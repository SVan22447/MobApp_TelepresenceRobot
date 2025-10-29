package com.example.telepresencerobot;

import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;

import com.example.telepresencerobot.base.BaseWebRTCActivity;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

public class RobotActivity extends BaseWebRTCActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_robot);
        remoteVideoView = findViewById(R.id.remote_video_view);
        initializeVideoViews();
        checkAndRequestPermissions();
    }
    @Override
    protected void onPermissionsGranted() {
        Log.d("RobotActivity", "Permissions granted, initializing connection");
        initializePeerConnection();
        connectToSignalingServer();
    }
    @Override
    protected boolean isFrontCameraPreferred() {
        return true;
    }
    @Override
    protected List<PeerConnection.IceServer> getIceServers() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.tagan.ru:3478").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.kanet.ru:3478").createIceServer());
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
        return "default-room"; // Такая же комната как в MainActivity
    }
    @Override
    protected boolean isOfferer() {return false;}
    @Override
    protected boolean hasLocalVideo() {
        return true;
    }
    @Override
    public void onConnected() {
        super.onConnected();
        runOnUiThread(() -> Toast.makeText(this, "Robot connected - waiting for offer", Toast.LENGTH_SHORT).show());
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

}