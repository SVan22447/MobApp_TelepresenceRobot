package com.example.telepresencerobot;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;

import com.example.telepresencerobot.base.BaseWebRTCActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseWebRTCActivity {
    private ImageButton buttonStartStop;
    private ImageButton MicButton;
    private ImageButton btn_up;
    private ImageButton btn_down;
    private ImageButton btn_left;
    private ImageButton btn_right;
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
        btn_up= findViewById(R.id.upButton_);
        btn_down= findViewById(R.id.downButton_);
        btn_left= findViewById(R.id.leftButton_);
        btn_right= findViewById(R.id.rightButton_);
        server_name = findViewById(R.id.textView);
        MicButton= findViewById(R.id.MicControl);

        initializeVideoViews();
        checkAndRequestPermissions();
        setupMovementButton(btn_up, "FORWARD");
        setupMovementButton(btn_down, "BACKWARD");
        setupMovementButton(btn_left, "LEFT");
        setupMovementButton(btn_right, "RIGHT");
        buttonStartStop.setOnClickListener(v -> toggleConnection());
        MicButton.setOnClickListener(v -> toggleMicrophone());
    }
    @Override
    protected void onPermissionsGranted() {
        initializePeerConnection();
        connectToSignalingServer();
        updateMicButtonState(isMicrophoneEnabled());
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
        return "default-room";
    }
    @SuppressLint("ClickableViewAccessibility")
    private void setupMovementButton(ImageButton button, String direction) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sendMovementCommand(direction, "press");
                    return true;
                case MotionEvent.ACTION_UP:
                    sendMovementCommand(direction, "release");
                    return true;
            }
            return false;
        });
    }
    @Override
    protected boolean isOfferer() {return true;}

    @Override
    protected void handleRobotData(String data) {}
    @Override
    protected void onDataChannelConnected() {
        runOnUiThread(() -> {
            enableControls(true);
            sendMicStatus(true);
        });
    }
    private void sendMicStatus(boolean status){
        try {
            JSONObject command = new JSONObject();
            command.put("type", "mic");
            command.put("status", status);
            sendRobotCommand(command);
        } catch (JSONException e) {
            Log.e("RobotControl", "Error creating command", e);
        }
    }
    private void sendMovementCommand(String direction,String action) {
        try {
            JSONObject command = new JSONObject();
            Toast.makeText(this, "Command send " + direction, Toast.LENGTH_SHORT).show();
            command.put("type", "movement");
            command.put("direction", direction);
            command.put("action", action);
            sendRobotCommand(command);
        } catch (JSONException e) {
            Log.e("RobotControl", "Error creating command", e);
        }
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
    @Override
    protected void updateMicButtonState(boolean isEnabled) {
        runOnUiThread(() -> {
            sendMicStatus(isEnabled);
//            if (MicButton != null) {
//                MicButton.setImageAlpha(isEnabled ?
//                        0 : 100);
////                GradientDrawable background = (GradientDrawable) MicButton.getBackground();
//            }
        });
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
            if (MicButton != null) {
                MicButton.setEnabled(true);
            }
        });
    }
    @Override
    public void onDisconnected(String reason) {
        super.onDisconnected(reason);
        runOnUiThread(() -> {
            isConnected = false;
            if (MicButton != null) {
                MicButton.setEnabled(false);
            }
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
    }
    @Override
    public void onOffer(String from, String sdp) {
        super.onOffer(from, sdp);
    }
    @Override
    public void onAnswer(String from, String sdp) {
        super.onAnswer(from, sdp);
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
    private void enableControls(boolean enabled) {
        btn_up.setEnabled(enabled);
        btn_down.setEnabled(enabled);
        btn_left.setEnabled(enabled);
        btn_right.setEnabled(enabled);
    }
    @Override
    protected void onDataChannelDisconnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "DataChannel disconnected", Toast.LENGTH_SHORT).show();
            enableControls(false);
        });
    }
}