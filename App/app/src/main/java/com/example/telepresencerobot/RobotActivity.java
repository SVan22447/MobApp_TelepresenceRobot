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
    private SurfaceViewRenderer remoteVideoView;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    @Override
    protected void onPermissionsGranted() {
    }
    @Override
    protected boolean isFrontCameraPreferred() {
        return false;
    }
    @Override
    protected List<PeerConnection.IceServer> getIceServers() {
        return Collections.emptyList();
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
