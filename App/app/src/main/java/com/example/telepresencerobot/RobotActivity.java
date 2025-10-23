package com.example.telepresencerobot;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.telepresencerobot.base.BaseWebRTCActivity;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.*;
import org.java_websocket.*;

import java.util.Collections;
import java.util.List;

public class RobotActivity extends BaseWebRTCActivity {
    static final Logger logger = LoggerFactory.getLogger(RobotActivity.class);
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        remoteVideoView = findViewById(R.id.remote_video_view);
    }
    @Override
    protected void onPermissionsGranted() {
        webRTCManager.initialize(getIceServers());
        webRTCManager.setupLocalMedia(eglBase.getEglBaseContext());
    }
    @Override
    protected boolean isFrontCameraPreferred() {
        return false;
    }
    @Override
    protected List<PeerConnection.IceServer> getIceServers() {
        return List.of(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        );
    }
    @Override
    public void onConnected() {
    }
    @Override
    public void onDisconnected() {
    }
    @Override
    public void onMessage(JSONObject message) {
    }
}
