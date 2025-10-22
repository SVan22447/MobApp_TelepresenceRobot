package com.example.telepresencerobot.base;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.webrtc.*;

import com.example.telepresencerobot.webrtc.WebRTCManager;
import com.example.telepresencerobot.websocket.WebSocketManager;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseWebRTCActivity extends AppCompatActivity
        implements WebRTCManager.WebRTCListener, WebSocketManager.WebSocketListener {
    protected WebRTCManager webRTCManager;
    protected WebSocketManager webSocketManager;
    protected SurfaceViewRenderer localVideoView;
    protected SurfaceViewRenderer remoteVideoView;
    protected EglBase eglBase;
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
        eglBase = EglBase.create();
        webRTCManager = new WebRTCManager(this, this, isFrontCameraPreferred());
        webSocketManager = new WebSocketManager(this);
    }
    protected void initializeVideoViews() {
        if (localVideoView != null) {
            localVideoView.init(eglBase.getEglBaseContext(), null);
        }
        if (remoteVideoView != null) {
            remoteVideoView.init(eglBase.getEglBaseContext(), null);
        }
    }
    protected void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
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

    // WebRTCManager.WebRTCListener implementations
    @Override
    public void onLocalVideoTrackCreated(VideoTrack videoTrack) {
        runOnUiThread(() -> {
            if (localVideoView != null) {
                videoTrack.addSink(localVideoView);
            }
        });
    }
    @Override
    public void onRemoteVideoTrackReceived(VideoTrack videoTrack) {
        runOnUiThread(() -> {
            if (remoteVideoView != null) {
                videoTrack.addSink(remoteVideoView);
            }
        });
    }
    @Override
    public void onConnectionStateChanged(PeerConnection.PeerConnectionState state) {
        runOnUiThread(() -> {
            String message = "Connection: " + state.name();
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public void onIceConnectionStateChanged(PeerConnection.IceConnectionState state) {
        runOnUiThread(() -> {
            String message = "ICE: " + state.name();
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public void onError(String error) {
        runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webRTCManager != null) {
            webRTCManager.stopCapture();
            webRTCManager.close();
            webRTCManager.dispose();
        }
        if (webSocketManager != null) {
            webSocketManager.disconnect();
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
    }
}
