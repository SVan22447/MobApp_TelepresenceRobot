package com.example.telepresencerobot;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;

import com.example.telepresencerobot.base.BaseWebRTCActivity;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseWebRTCActivity {
    private ImageButton buttonStartStop;
    private TextView server_name;
    private boolean isConnected = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_main);
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        buttonStartStop = findViewById(R.id.button_start_stop);
        server_name = findViewById(R.id.textView);
        initializeVideoViews();
        checkAndRequestPermissions();
        buttonStartStop.setOnClickListener(v -> toggleConnection());
    }
    @Override
    protected void onPermissionsGranted() {
        initializePeerConnection();
        connectToSignalingServer();
    }
    @Override
    protected boolean isFrontCameraPreferred() {
        return true;
    }
    @SuppressLint("SetTextI18n")
    @Override
    protected List<PeerConnection.IceServer> getIceServers() {
        server_name.setText("stun:stun.l.google.com:19302");
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.tagan.ru:3478").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.kanet.ru:3478").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.demos.ru:3478").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.mgn.ru:3478").createIceServer());
        return iceServers;
    }
    @Override
    protected String getSocketServerUrl() {
        return getResources().getString(R.string.test_link);
    }
    @Override
    protected String getRoomName() {
        return "default-room"; // или ваша логика выбора комнаты
    }
    @Override
    protected boolean isOfferer() {
        return true; // MainActivity создает offer
    }
    private void toggleConnection() {
        if (isConnected) {
            if (peerConnectionManager != null) {
                peerConnectionManager.close();
            }
            disconnectFromSignalingServer();
            updateButtonState(false);
        } else {
            connectToSignalingServer();
            updateButtonState(true);
        }
    }
    private void updateButtonState(boolean connecting) {
        runOnUiThread(() -> {
            if (buttonStartStop != null) {
                //                    buttonStartStop.setText("Connecting...");
                //                    buttonStartStop.setText("Start");
                buttonStartStop.setEnabled(!connecting);
            }
        });
    }
    @Override
    public void onConnected() {
        super.onConnected();
        runOnUiThread(() -> {
            isConnected = true;
            if (buttonStartStop != null) {
//                buttonStartStop.setText("Stop");
                buttonStartStop.setEnabled(true);
            }
            Toast.makeText(this, "WebRTC connected successfully", Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public void onDisconnected(String reason) {
        super.onDisconnected(reason);
        runOnUiThread(() -> {
            isConnected = false;
            if (buttonStartStop != null) {
//                buttonStartStop.setText("Start");
                buttonStartStop.setEnabled(true);
            }
            Toast.makeText(this, "Disconnected: " + reason, Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public void onPeerJoined(String peerId) {
        super.onPeerJoined(peerId);
        runOnUiThread(() -> Toast.makeText(this, "Peer joined: " + peerId, Toast.LENGTH_SHORT).show());
    }
    @Override
    public void onOffer(String from, String sdp) {
        super.onOffer(from, sdp);
        runOnUiThread(() -> Toast.makeText(this, "Received offer from: " + from, Toast.LENGTH_SHORT).show());
    }
    @Override
    public void onAnswer(String from, String sdp) {
        super.onAnswer(from, sdp);
        runOnUiThread(() -> Toast.makeText(this, "Received answer from: " + from, Toast.LENGTH_SHORT).show());
    }
    @Override
    public void onClosed(String reason) {
        super.onClosed(reason);
        runOnUiThread(() -> {
            isConnected = false;
            if (buttonStartStop != null) {
//                buttonStartStop.setText("Start");
                buttonStartStop.setEnabled(true);
            }
            Toast.makeText(this, "Signaling closed: " + reason, Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public void onRemoteVideoTrack(org.webrtc.VideoTrack videoTrack) {
        super.onRemoteVideoTrack(videoTrack);
        runOnUiThread(() -> Toast.makeText(this, "Remote video track received", Toast.LENGTH_SHORT).show());
    }
}