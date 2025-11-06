package com.example.telepresencerobot.base;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.telepresencerobot.webrtc.PeerConnectionManager;
import com.example.telepresencerobot.websocket.SignalingClient;

import org.json.JSONObject;
import org.webrtc.*;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseWebRTCActivity extends AppCompatActivity
        implements SignalingClient.Listener,
        PeerConnectionManager.PeerConnectionListener,
        PeerConnectionManager.DataChannelListener {
    protected PeerConnectionManager peerConnectionManager;
    protected SignalingClient signalingClient;
    protected SurfaceViewRenderer localVideoView;
    protected SurfaceViewRenderer remoteVideoView;
    protected EglBase eglBase;
    protected PeerConnectionFactory factory;

    private final ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        if (Boolean.TRUE.equals(permissions.get(Manifest.permission.CAMERA)) &&
                                Boolean.TRUE.equals(permissions.get(Manifest.permission.RECORD_AUDIO))) {
                            onPermissionsGranted();
                        } else {
                            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show();
                        }
                    });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            PeerConnectionFactory.InitializationOptions initializationOptions =
                    PeerConnectionFactory.InitializationOptions.builder(this)
                            .setEnableInternalTracer(true)
                            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                            .createInitializationOptions();
            PeerConnectionFactory.initialize(initializationOptions);
            eglBase = EglBase.create();
            DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                    eglBase.getEglBaseContext(),
                    true,
                    true
            );
            DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());
            PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
            factory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory();
            if (factory == null) {
                throw new RuntimeException("Failed to create PeerConnectionFactory");
            }
            signalingClient = new SignalingClient(getSocketServerUrl(), this);
        } catch (Exception e) {
            Log.e("BaseWebRTCActivity", "Failed to initialize WebRTC", e);
            Toast.makeText(this, "Failed to initialize video: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    protected void initializeVideoViews() {
        if (localVideoView != null) {
            localVideoView.init(eglBase.getEglBaseContext(), null);
            localVideoView.setMirror(true);
            Log.d("BaseWebRTCActivity", "Local video view initialized");
        }
        if (remoteVideoView != null) {
            remoteVideoView.init(eglBase.getEglBaseContext(), null);
            Log.d("BaseWebRTCActivity", "Remote video view initialized");
        }
    }
    protected void initializePeerConnection() {
        VideoSink localSink = localVideoView;
        VideoSink remoteSink = remoteVideoView;
        Log.d("BaseWebRTCActivity", "Initializing PeerConnection - Local video: " + (localVideoView != null) +
                ", Remote video: " + (remoteVideoView != null));
        peerConnectionManager = new PeerConnectionManager(
                this,
                factory,
                getIceServers(),
                eglBase.getEglBaseContext(),
                remoteSink,
                localSink,  // Теперь передаем null если нет локального view
                hasLocalVideo(),
                false
        );
        peerConnectionManager.createPeer(this, this);
    }
    @Override
    public void onDataChannelMessage(String message) {
        runOnUiThread(() -> {
            Log.d("BaseWebRTCActivity", "Received data channel message: " + message);
            handleRobotData(message);
        });
    }
    @Override
    public void onDataChannelStateChange(DataChannel.State state) {
        runOnUiThread(() -> {
            Log.d("BaseWebRTCActivity", "DataChannel state: " + state);
            if (state == DataChannel.State.OPEN) {
                onDataChannelConnected();
            } else if (state == DataChannel.State.CLOSED) {
                onDataChannelDisconnected();
            }
        });
    }
    protected void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (hasLocalVideo() && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }
        if (hasLocalAudio() && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!permissionsToRequest.isEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            onPermissionsGranted();
        }
    }
    protected abstract void onPermissionsGranted();
    protected abstract boolean isFrontCameraPreferred();
    protected abstract List<PeerConnection.IceServer> getIceServers();
    protected abstract String getSocketServerUrl();
    protected abstract String getRoomName();
    protected abstract boolean isOfferer();
    protected abstract void handleRobotData(String data);
    protected abstract void onDataChannelConnected();
    protected abstract void onDataChannelDisconnected();

    protected boolean hasLocalVideo() {
        return localVideoView != null;
    }
    protected boolean hasLocalAudio() {
        return true; // По умолчанию включаем аудио, можно переопределить
    }

    @Override
    public void onPeerJoined(String peerId) {
        runOnUiThread(() -> {
            Log.d("BaseWebRTCActivity", "Peer joined: " + peerId);
            Toast.makeText(this, "Peer joined: " + peerId, Toast.LENGTH_SHORT).show();
            if (isOfferer()) {
                new android.os.Handler().postDelayed(this::createOffer, 1000);
            }
        });
    }
    @Override
    public void onOffer(String from, String sdp) {
        runOnUiThread(() -> {
            Log.d("BaseWebRTCActivity", "Received offer from: " + from);
            Toast.makeText(this, "Received offer from: " + from, Toast.LENGTH_SHORT).show();
            if (peerConnectionManager != null) {
                SessionDescription remoteSdp = new SessionDescription(
                        SessionDescription.Type.OFFER, sdp
                );
                peerConnectionManager.setRemoteDescription(remoteSdp);
                createAnswer();
            }
        });
    }
    @Override
    public void onAnswer(String from, String sdp) {
        runOnUiThread(() -> {
            Log.d("BaseWebRTCActivity", "Received answer from: " + from);
            Toast.makeText(this, "Received answer from: " + from, Toast.LENGTH_SHORT).show();
            if (peerConnectionManager != null) {
                SessionDescription remoteSdp = new SessionDescription(
                        SessionDescription.Type.ANSWER, sdp
                );
                peerConnectionManager.setRemoteDescription(remoteSdp);
            }
        });
    }
    @Override
    public void onIce(String from, String mid, int index, String cand) {
        Log.d("BaseWebRTCActivity", "Received ICE candidate from: " + from);
        if (peerConnectionManager != null) {
            IceCandidate candidate = new IceCandidate(mid, index, cand);
            peerConnectionManager.addIceCandidate(candidate);
        }
    }
    @Override
    public void onClosed(String reason) {
        runOnUiThread(() -> {
            Log.d("BaseWebRTCActivity", "Connection closed: " + reason);
            Toast.makeText(this, "Connection closed: " + reason, Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public void onIceCandidate(IceCandidate candidate) {
        Log.d("BaseWebRTCActivity", "Sending ICE candidate: " + candidate.sdpMid + ":" + candidate.sdpMLineIndex);
        signalingClient.sendIce(
                candidate.sdpMid,
                candidate.sdpMLineIndex,
                candidate.sdp
        );
    }
    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            Log.d("BaseWebRTCActivity", "WebRTC connected");
            Toast.makeText(this, "WebRTC connected", Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public void onDisconnected(String reason) {
        runOnUiThread(() -> {
            Log.d("BaseWebRTCActivity", "WebRTC disconnected: " + reason);
            Toast.makeText(this, "WebRTC disconnected: " + reason, Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public void onRemoteVideoTrack(VideoTrack videoTrack) {
        runOnUiThread(() -> {
            Log.d("BaseWebRTCActivity", "Remote video track received");
            Toast.makeText(this, "Remote video track received", Toast.LENGTH_SHORT).show();
            if (remoteVideoView != null) {
                videoTrack.addSink(remoteVideoView);
            }
        });
    }
    protected void connectToSignalingServer() {
        Log.d("BaseWebRTCActivity", "Connecting to signaling server");
        signalingClient.connect(getRoomName());
    }

    protected void disconnectFromSignalingServer() {
        Log.d("BaseWebRTCActivity", "Disconnecting from signaling server");
        signalingClient.leave();
    }
    protected void createOffer() {
        Log.d("BaseWebRTCActivity", "Creating offer");
        if (peerConnectionManager != null) {
            peerConnectionManager.createOffer(sdp -> {
                Log.d("BaseWebRTCActivity", "Offer created, sending to signaling server");
                signalingClient.sendOffer(sdp.description);
            });
        }
    }
    protected void createAnswer() {
        Log.d("BaseWebRTCActivity", "Creating answer");
        if (peerConnectionManager != null) {
            peerConnectionManager.createAnswer(sdp -> {
                Log.d("BaseWebRTCActivity", "Answer created, sending to signaling server");
                signalingClient.sendAnswer(sdp.description);
            });
        }
    }
    public void setMicrophoneEnabled(boolean enabled) {
        if (peerConnectionManager != null) {
            peerConnectionManager.setAudioEnabled(enabled);
            updateMicButtonState(enabled);
        }
    }
    public void toggleMicrophone() {
        if (peerConnectionManager != null) {
            peerConnectionManager.toggleAudio();
            updateMicButtonState(peerConnectionManager.isAudioEnabled());
        }
    }
    public void sendRobotCommand(String command) {
        if (peerConnectionManager != null) {
            peerConnectionManager.sendData(command);
        }
    }

    public void sendRobotCommand(JSONObject jsonCommand) {
        if (peerConnectionManager != null) {
            peerConnectionManager.sendData(jsonCommand.toString());
        }
    }
    protected void updateMicButtonState(boolean isEnabled) {}
    public boolean isMicrophoneEnabled() {
        return peerConnectionManager != null && peerConnectionManager.isAudioEnabled();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("BaseWebRTCActivity", "Destroying activity");
        if (peerConnectionManager != null) {
            peerConnectionManager.dispose();
            peerConnectionManager.close();
        }
        if (signalingClient != null) {
            signalingClient.leave();
        }
        if (localVideoView != null) {
            localVideoView.release();
        }
        if (remoteVideoView != null) {
            remoteVideoView.release();
        }
        if (eglBase != null) {
            eglBase.release();
        }
        if (factory != null) {
            factory.dispose();
        }
        finish();
    }
}