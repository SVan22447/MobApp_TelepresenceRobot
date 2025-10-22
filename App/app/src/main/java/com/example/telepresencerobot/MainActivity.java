package com.example.telepresencerobot;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import com.example.telepresencerobot.base.BaseWebRTCActivity;

import org.json.JSONObject;
import org.webrtc.PeerConnection;

import java.util.List;

public class MainActivity extends BaseWebRTCActivity {
    private ImageButton buttonStartStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        buttonStartStop = findViewById(R.id.button_start_stop);
        initializeVideoViews();
        checkAndRequestPermissions();
        buttonStartStop.setOnClickListener(v -> toggleConnection());
    }

    @Override
    protected void onPermissionsGranted() {
        webRTCManager.initialize(getIceServers());
        webRTCManager.setupLocalMedia(eglBase.getEglBaseContext());
    }
    @Override
    protected boolean isFrontCameraPreferred() {
        return true; // Пользователь предпочитает фронтальную камеру
    }
    @Override
    protected List<PeerConnection.IceServer> getIceServers() {
        return List.of(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        );
    }
    private void toggleConnection() {
        if (webRTCManager.getConnectionState() == PeerConnection.PeerConnectionState.CONNECTED) {
            webRTCManager.close();
        } else {
            webRTCManager.createOffer();
        }
    }
    @Override
    public void onConnected() {
        runOnUiThread(() -> Toast.makeText(this, "WebSocket Connected", Toast.LENGTH_SHORT).show());
    }
    @Override
    public void onDisconnected() {
        runOnUiThread(() -> Toast.makeText(this, "WebSocket Disconnected", Toast.LENGTH_SHORT).show());
    }
    @Override
    public void onMessage(JSONObject message) {
        // Обработка сообщений от сервера
    }
}
