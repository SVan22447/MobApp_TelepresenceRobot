package com.example.telepresencerobot.websocket;

import android.util.Log;


import androidx.annotation.NonNull;

import com.google.gson.Gson;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SignalingClient {
    public interface Listener {
        void onPeerJoined(String peerId);
        void onOffer(String from, String sdp);
        void onAnswer(String from, String sdp);
        void onIce(String from, String mid, int index, String cand);
        void onClosed(String reason);
    }

    private final String baseWs;
    private final Listener listener;
    private final OkHttpClient client;
    private final Gson gson;
    private final String id;

    private WebSocket webSocket;
    private String currentRoomId;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    public SignalingClient(String baseWs, Listener listener) {
        this.baseWs = baseWs;
        this.listener = listener;
        this.gson = new Gson();
        this.id = UUID.randomUUID().toString();

        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    public void connect(String roomId) {
        if (!isConnecting.compareAndSet(false, true)) {
            Log.w("SignalingClient", "Connection attempt already in progress for room: " + roomId);
            return;
        }

        if (webSocket != null) {
            webSocket.close(1000, "Changing room");
            webSocket = null;
        }

        this.currentRoomId = roomId;

        String url = baseWs + "?room=" + roomId + "&peer=" + id;
        Request request = new Request.Builder().url(url).build();

        Log.d("SignalingClient", "Connecting to URL: " + url);
        client.newWebSocket(request, new SignalingWebSocketListener());
    }
    private class SignalingWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            SignalingClient.this.webSocket = webSocket;
            isConnecting.set(false);
            Log.i("SignalingClient", "WebSocket connection opened to room: " + currentRoomId);
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            try {
                Envelope envelope = gson.fromJson(text, Envelope.class);
                if (envelope.from != null && envelope.from.equals(id)) {
                    return; // Игнорируем свои же сообщения
                }

                Log.d("SignalingClient", "Received message: " + envelope.type + " from " + envelope.from);

                switch (envelope.type) {
                    case "peer-joined":
                        if (envelope.peerId != null) {
                            listener.onPeerJoined(envelope.peerId);
                        }
                        break;
                    case "offer":
                        if (envelope.sdp != null && envelope.from != null) {
                            listener.onOffer(envelope.from, envelope.sdp);
                        }
                        break;
                    case "answer":
                        if (envelope.sdp != null && envelope.from != null) {
                            listener.onAnswer(envelope.from, envelope.sdp);
                        }
                        break;
                    case "ice":
                        if (envelope.candidate != null && envelope.from != null) {
                            Candidate c = envelope.candidate;
                            if (c.sdpMid != null && c.sdpMLineIndex != null && c.candidate != null) {
                                listener.onIce(envelope.from, c.sdpMid, c.sdpMLineIndex, c.candidate);
                            }
                        }
                        break;
                    case "error":
                        Log.e("SignalingClient", "Server error: " + envelope.payload);
                        break;
                }
            } catch (Exception e) {
                Log.e("SignalingClient", "Failed to parse message: " + text, e);
            }
        }

        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Log.w("SignalingClient", "WebSocket closing: " + code + " / " + reason);
        }

        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            SignalingClient.this.webSocket = null;
            isConnecting.set(false);
            Log.w("SignalingClient", "WebSocket closed: " + code + " / " + reason);
            listener.onClosed("Connection closed: " + reason);
        }

        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
            SignalingClient.this.webSocket = null;
            isConnecting.set(false);
            Log.e("SignalingClient", "WebSocket failure", t);
            // В Java можно добавить переподключение через Handler если нужно
        }
    }

    public void sendOffer(String sdp) {
        send(new Envelope("offer", sdp));
    }

    public void sendAnswer(String sdp) {
        send(new Envelope("answer", sdp));
    }

    public void sendIce(String mid, int idx, String cand) {
        send(new Envelope("ice", new Candidate(mid, idx, cand)));
    }

    public void leave() {
        if (webSocket != null) {
            webSocket.close(1000, "User left");
            webSocket = null;
        }
        currentRoomId = null;
    }

    private void send(Envelope data) {
        if (webSocket == null) {
            Log.w("SignalingClient", "Cannot send message, WebSocket is not active.");
            return;
        }

        try {
            // Дополняем сообщение данными о комнате и отправителе
            data.room = currentRoomId;
            data.from = id;
            String message = gson.toJson(data);
            webSocket.send(message);
        } catch (Exception e) {
            Log.e("SignalingClient", "Failed to send message", e);
        }
    }

    public String getId() {
        return id;
    }

    public boolean isConnected() {
        return webSocket != null;
    }
}

class Envelope {
    String type;
    String room;
    String peerId;
    String from;
    String sdp;
    Candidate candidate;
    String payload;

    public Envelope(String type, String sdp) {
        this.type = type;
        this.sdp = sdp;
    }

    public Envelope(String type, Candidate candidate) {
        this.type = type;
        this.candidate = candidate;
    }
}

class Candidate {
    String sdpMid;
    Integer sdpMLineIndex;
    String candidate;

    public Candidate(String sdpMid, Integer sdpMLineIndex, String candidate) {
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
        this.candidate = candidate;
    }
}