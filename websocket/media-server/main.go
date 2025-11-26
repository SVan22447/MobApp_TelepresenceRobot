package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/gorilla/websocket"
	"github.com/pion/webrtc/v3"
)

// Конфигурация
type Config struct {
	SignalingAddr string `json:"signaling_addr"`
	MediaAddr     string `json:"media_addr"`
	RTSPPort      int    `json:"rtsp_port"`
	STUNServer    string `json:"stun_server"`
}

// Состояние трансляции
type StreamSession struct {
	RoomID       string
	PeerID       string
	RTSPEndpoint string
	PC           *webrtc.PeerConnection
	FFmpegCmd    *exec.Cmd
	CreatedAt    time.Time
}

// Медиа-сервер
type MediaServer struct {
	config        *Config
	upgrader      websocket.Upgrader
	sessions      map[string]*StreamSession // roomID -> session
	sessionsMutex sync.RWMutex
	rtspPortCounter int
}

// WebSocket сообщения
type WSMessage struct {
	Type      string          `json:"type"`
	Room      string          `json:"room"`
	PeerID    string          `json:"peerId"`
	From      string          `json:"from"`
	SDP       string          `json:"sdp,omitempty"`
	Candidate json.RawMessage `json:"candidate,omitempty"`
	Payload   interface{}     `json:"payload,omitempty"`
}

func NewMediaServer(config *Config) *MediaServer {
	return &MediaServer{
		config: config,
		upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool { return true },
		},
		sessions:      make(map[string]*StreamSession),
		rtspPortCounter: 8554,
	}
}

func (ms *MediaServer) Start() error {
	// Запуск HTTP сервера для health checks
	http.HandleFunc("/health", ms.healthHandler)
	http.HandleFunc("/sessions", ms.sessionsHandler)
	
	go func() {
		log.Printf("Starting media server on %s", ms.config.MediaAddr)
		if err := http.ListenAndServe(ms.config.MediaAddr, nil); err != nil {
			log.Fatalf("Failed to start media server: %v", err)
		}
	}()
	go ms.connectToSignalingServer()
	return nil
}

func (ms *MediaServer) connectToSignalingServer() {
	url := fmt.Sprintf("ws://%s/ws?room=media-server&peer=media-server", ms.config.SignalingAddr)
	
	for {
		conn, _, err := websocket.DefaultDialer.Dial(url, nil)
		if err != nil {
			log.Printf("Failed to connect to signaling server: %v. Retrying in 5s...", err)
			time.Sleep(5 * time.Second)
			continue
		}
		log.Printf("Connected to signaling server at %s", ms.config.SignalingAddr)
		ms.handleSignalingConnection(conn)
		time.Sleep(5 * time.Second)
	}
}

func (ms *MediaServer) handleSignalingConnection(conn *websocket.Conn) {
	defer conn.Close()

	for {
		var msg WSMessage
		err := conn.ReadJSON(&msg)
		if err != nil {
			log.Printf("WebSocket read error: %v", err)
			return
		}

		switch msg.Type {
		case "offer":
			go ms.handleOffer(msg)
		case "ice-candidate":
			go ms.handleICECandidate(msg)
		case "peer-joined":
			log.Printf("Peer joined: %s in room %s", msg.PeerID, msg.Room)
		case "peer-left":
			go ms.cleanupSession(msg.Room)
		}
	}
}

func (ms *MediaServer) handleOffer(msg WSMessage) {
	log.Printf("Received offer from %s for room %s", msg.From, msg.Room)
	session, err := ms.createStreamSession(msg.Room, msg.From, msg.SDP)
	if err != nil {
		log.Printf("Failed to create stream session: %v", err)
		return
	}
	answer, err := ms.createAnswer(session, msg.SDP)
	if err != nil {
		log.Printf("Failed to create answer: %v", err)
		return
	}

	answerMsg := WSMessage{
		Type:   "answer",
		Room:   msg.Room,
		PeerID: "media-server",
		From:   "media-server",
		SDP:    answer,
	}
	ms.sendToSignalingServer(answerMsg)
}

func (ms *MediaServer) createStreamSession(roomID, peerID, offerSDP string) (*StreamSession, error) {
	ms.sessionsMutex.Lock()
	defer ms.sessionsMutex.Unlock()
	if existing, exists := ms.sessions[roomID]; exists {
		ms.cleanupSessionInternal(existing)
	}
	config := webrtc.Configuration{
		ICEServers: []webrtc.ICEServer{
			{URLs: []string{ms.config.STUNServer}},
		},
	}

	peerConnection, err := webrtc.NewPeerConnection(config)
	if err != nil {
		return nil, fmt.Errorf("failed to create peer connection: %w", err)
	}
	rtspPort := ms.rtspPortCounter
	ms.rtspPortCounter++
	rtspEndpoint := fmt.Sprintf("rtsp://localhost:%d/live/%s", rtspPort, roomID)
	ffmpegCmd, err := ms.startFFmpegTranscoder(rtspPort, roomID)
	if err != nil {
		peerConnection.Close()
		return nil, fmt.Errorf("failed to start ffmpeg: %w", err)
	}
	session := &StreamSession{
		RoomID:       roomID,
		PeerID:       peerID,
		RTSPEndpoint: rtspEndpoint,
		PC:           peerConnection,
		FFmpegCmd:    ffmpegCmd,
		CreatedAt:    time.Now(),
	}
	peerConnection.OnTrack(func(track *webrtc.TrackRemote, receiver *webrtc.RTPReceiver) {
		log.Printf("Track received: %s, kind: %s", track.ID(), track.Kind().String())
		ms.handleTrack(session, track)
	})
	peerConnection.OnICEConnectionStateChange(func(state webrtc.ICEConnectionState) {
		log.Printf("ICE Connection State for %s: %s", roomID, state.String())
		if state == webrtc.ICEConnectionStateFailed {
			ms.cleanupSession(roomID)
		}
	})
	offer := webrtc.SessionDescription{
		Type: webrtc.SDPTypeOffer,
		SDP:  offerSDP,
	}

	if err := peerConnection.SetRemoteDescription(offer); err != nil {
		ms.cleanupSessionInternal(session)
		return nil, fmt.Errorf("failed to set remote description: %w", err)
	}

	ms.sessions[roomID] = session
	log.Printf("Created stream session for room %s, RTSP: %s", roomID, rtspEndpoint)
	return session, nil
}

func (ms *MediaServer) createAnswer(session *StreamSession, offerSDP string) (string, error) {
	answer, err := session.PC.CreateAnswer(nil)
	if err != nil {
		return "", fmt.Errorf("failed to create answer: %w", err)
	}
	err = session.PC.SetLocalDescription(answer)
	if err != nil {
		return "", fmt.Errorf("failed to set local description: %w", err)
	}
	return answer.SDP, nil
}

func (ms *MediaServer) startFFmpegTranscoder(port int, roomID string) (*exec.Cmd, error) {
	cmd := exec.Command("ffmpeg",
		"-f", "rtp",          // формат ввода RTP
		"-i", fmt.Sprintf("rtp://127.0.0.1:%d", port), // входной RTP поток
		"-c", "copy",         // без перекодирования
		"-f", "rtsp",         // формат вывода RTSP
		fmt.Sprintf("rtsp://127.0.0.1:8554/live/%s", roomID), // выходной RTSP
	)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("failed to start ffmpeg: %w", err)
	}
	log.Printf("Started FFmpeg transcoder for room %s on port %d", roomID, port)
	return cmd, nil
}

func (ms *MediaServer) handleTrack(session *StreamSession, track *webrtc.TrackRemote) {
	// Здесь будет логика обработки медиа-треков
	
	log.Printf("Handling track %s for room %s", track.ID(), session.RoomID)
}

func (ms *MediaServer) handleICECandidate(msg WSMessage) {
	ms.sessionsMutex.RLock()
	session, exists := ms.sessions[msg.Room]
	ms.sessionsMutex.RUnlock()
	if !exists {
		return
	}
	var candidate webrtc.ICECandidateInit
	if err := json.Unmarshal(msg.Candidate, &candidate); err != nil {
		log.Printf("Failed to parse ICE candidate: %v", err)
		return
	}
	if err := session.PC.AddICECandidate(candidate); err != nil {
		log.Printf("Failed to add ICE candidate: %v", err)
	}
}
func (ms *MediaServer) cleanupSession(roomID string) {
	ms.sessionsMutex.Lock()
	defer ms.sessionsMutex.Unlock()
	if session, exists := ms.sessions[roomID]; exists {
		ms.cleanupSessionInternal(session)
		delete(ms.sessions, roomID)
		log.Printf("Cleaned up session for room %s", roomID)
	}
}

func (ms *MediaServer) cleanupSessionInternal(session *StreamSession) {
	if session.PC != nil {
		session.PC.Close()
	}
	if session.FFmpegCmd != nil {
		session.FFmpegCmd.Process.Signal(os.Interrupt)
		session.FFmpegCmd.Wait()
	}
}

func (ms *MediaServer) sendToSignalingServer(msg WSMessage) {
	// Реализация отправки сообщений обратно в сигнальный сервер
	url := fmt.Sprintf("ws://%s/ws?room=%s&peer=media-server", ms.config.SignalingAddr, msg.Room)
	conn, _, err := websocket.DefaultDialer.Dial(url, nil)
	if err != nil {
		log.Printf("Failed to connect to signaling server for sending: %v", err)
		return
	}
	defer conn.Close()
	if err := conn.WriteJSON(msg); err != nil {
		log.Printf("Failed to send message to signaling server: %v", err)
	}
}

func (ms *MediaServer) healthHandler(w http.ResponseWriter, r *http.Request) {
	ms.sessionsMutex.RLock()
	defer ms.sessionsMutex.RUnlock()
	health := map[string]interface{}{
		"status":   "healthy",
		"sessions": len(ms.sessions),
		"uptime":   time.Since(startTime).String(),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(health)
}

func (ms *MediaServer) sessionsHandler(w http.ResponseWriter, r *http.Request) {
	ms.sessionsMutex.RLock()
	defer ms.sessionsMutex.RUnlock()
	sessions := make([]map[string]interface{}, 0)
	for roomID, session := range ms.sessions {
		sessions = append(sessions, map[string]interface{}{
			"room_id":    roomID,
			"peer_id":    session.PeerID,
			"rtsp_url":   session.RTSPEndpoint,
			"created_at": session.CreatedAt,
		})
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(sessions)
}

var startTime time.Time
func main() {
	startTime = time.Now()
	config := &Config{
		SignalingAddr: "localhost:8777",
		MediaAddr:     ":8888", 
		RTSPPort:      8554,
		STUNServer:    "stun:stun.l.google.com:19302",
	}
	if addr := os.Getenv("SIGNALING_ADDR"); addr != "" {
		config.SignalingAddr = addr
	}
	if addr := os.Getenv("MEDIA_ADDR"); addr != "" {
		config.MediaAddr = addr
	}
	if port := os.Getenv("RTSP_PORT"); port != "" {
		if p, err := strconv.Atoi(port); err == nil {
			config.RTSPPort = p
		}
	}
	if stun := os.Getenv("STUN_SERVER"); stun != "" {
		config.STUNServer = stun
	}
	server := NewMediaServer(config)
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigChan
		log.Println("Shutting down media server...")
		os.Exit(0)
	}()
	if err := server.Start(); err != nil {
		log.Fatalf("Failed to start media server: %v", err)
	}
	// Бесконечный цикл
	select {}
}