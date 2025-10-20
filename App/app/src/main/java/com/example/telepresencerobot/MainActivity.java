package com.example.telepresencerobot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.*;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final Logger logger = LoggerFactory.getLogger(MainActivity.class);
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private Button buttonStartStop;
    private CameraVideoCapturer videoCapturer;
    private final List<PeerConnection.IceServer> iceServers = List.of(
            PeerConnection.IceServer.builder("stun://").createIceServer()
    );
    private final PeerConnection.RTCConfiguration rtcConfig =
            new PeerConnection.RTCConfiguration(iceServers);
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        initializeViews();
        checkAndRequestPermissions();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    private void initializeViews() {
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        buttonStartStop = findViewById(R.id.button_start_stop);
        Button buttonCamToggle = findViewById(R.id.button_tog_cam);
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                        .builder(this)
                        .createInitializationOptions()
        );
        buttonStartStop.setOnClickListener(v -> toggleStream());
        buttonCamToggle.setOnClickListener(v -> toggleCamera());
    }
    private void initializeWebRTC() {
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();
        VideoSource videoSource = factory.createVideoSource(false);
        videoCapturer = createCameraCapturer();
        if (videoCapturer == null) {
            Toast.makeText(this, "Cannot create camera capturer", Toast.LENGTH_SHORT).show();
            return;
        }
        SurfaceTextureHelper surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread",
                        EglBase.create().getEglBaseContext());
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        videoCapturer.startCapture(1280, 720, 30);
        VideoTrack localVideoTrack = factory.createVideoTrack("local_video", videoSource);
        localVideoTrack.addSink(localVideoView);
        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        AudioTrack localAudioTrack = factory.createAudioTrack("local_audio", audioSource);
        localVideoView.init(EglBase.create().getEglBaseContext(), null);
        remoteVideoView.init(EglBase.create().getEglBaseContext(), null);
        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnectionObserver());
        if (peerConnection != null) {
            peerConnection.addTrack(localVideoTrack);
            peerConnection.addTrack(localAudioTrack);
        }
    }
    private CameraVideoCapturer createCameraCapturer() {
        Camera1Enumerator enumerator = new Camera1Enumerator(false);
        String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, new CameraEventsHandler());
            }
        }
        for (String deviceName : deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, new CameraEventsHandler());
            }
        }
        return null;
    }
    private class CameraEventsHandler implements CameraVideoCapturer.CameraEventsHandler {
        @Override
        public void onCameraError(String errorDescription) {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Camera error: " + errorDescription, Toast.LENGTH_SHORT).show());
        }
        @Override
        public void onCameraDisconnected() {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Camera disconnected", Toast.LENGTH_SHORT).show());
        }
        @Override
        public void onCameraFreezed(String errorDescription) {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Camera frozen: " + errorDescription, Toast.LENGTH_SHORT).show());
        }
        @Override
        public void onCameraOpening(String cameraName) {
        }
        @Override
        public void onFirstFrameAvailable() {
        }
        @Override
        public void onCameraClosed() {
        }
    }
    private class PeerConnectionObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }
        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            runOnUiThread(() -> {
                switch (iceConnectionState) {
                    case CONNECTED:
                        Toast.makeText(MainActivity.this, "ICE Connected", Toast.LENGTH_SHORT).show();
                        break;
                    case DISCONNECTED:
                        Toast.makeText(MainActivity.this, "ICE Disconnected", Toast.LENGTH_SHORT).show();
                        break;
                    case FAILED:
                        Toast.makeText(MainActivity.this, "ICE Failed", Toast.LENGTH_SHORT).show();
                        break;
                }
            });
        }
        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
        }
        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        }
        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
        }
        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        }
        @Override
        public void onAddStream(MediaStream mediaStream) {
            runOnUiThread(() -> {
                if (!mediaStream.videoTracks.isEmpty()) {
                    VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                    remoteVideoTrack.addSink(remoteVideoView);
                }
            });
        }
        @Override
        public void onRemoveStream(MediaStream mediaStream) {
        }
        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }
        @Override
        public void onRenegotiationNeeded() {
        }
        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        }
        @SuppressLint("SetTextI18n")
        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            runOnUiThread(() -> {
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                    Toast.makeText(MainActivity.this, "WebRTC Connected", Toast.LENGTH_SHORT).show();
                    buttonStartStop.setText("Stop Connection");
                } else if (newState == PeerConnection.PeerConnectionState.DISCONNECTED ||
                        newState == PeerConnection.PeerConnectionState.FAILED) {
                    Toast.makeText(MainActivity.this, "WebRTC Disconnected", Toast.LENGTH_SHORT).show();
                    buttonStartStop.setText("Start Connection");
                }
            });
        }
    }
    private final ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        if (Boolean.TRUE.equals(permissions.get(Manifest.permission.CAMERA)) &&
                                Boolean.TRUE.equals(permissions.get(Manifest.permission.RECORD_AUDIO))) {
                            initializeWebRTC();
                        } else {
                            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show();
                        }
                    });

    private void checkAndRequestPermissions() {
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
            initializeWebRTC();
        }
    }
    private void toggleCamera() {
        if (videoCapturer != null) {
            videoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean isFrontCamera) {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                    "Switched to " + (isFrontCamera ? "front" : "back") + " camera",
                                    Toast.LENGTH_SHORT).show());
                }
                @Override
                public void onCameraSwitchError(String errorDescription) {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                    "Camera switch error: " + errorDescription,
                                    Toast.LENGTH_SHORT).show());
                }
            });
        }
    }
    private void toggleStream() {
        if (peerConnection != null) {
            if (peerConnection.connectionState() ==
                    PeerConnection.PeerConnectionState.CONNECTED) {
                stopConnection();
            } else {
                startConnection();
            }
        }
    }
    private void startConnection() {
        // Create SDP offer and send through signaling server
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));

        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sdp) {}

                    @Override
                    public void onSetSuccess() {
                        // Send SDP offer through your signaling server
                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this, "Local description set", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onCreateFailure(String error) {
                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this, "Create failure: " + error, Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onSetFailure(String error) {
                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this, "Set failure: " + error, Toast.LENGTH_SHORT).show());
                    }
                }, sdp);
            }

            @Override
            public void onSetSuccess() {}

            @Override
            public void onCreateFailure(String error) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Create offer failure: " + error, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onSetFailure(String error) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Set offer failure: " + error, Toast.LENGTH_SHORT).show());
            }
        }, sdpConstraints);
    }

    @SuppressLint("SetTextI18n")
    private void stopConnection() {
        if (peerConnection != null) {
            peerConnection.close();
        }
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                logger.error("An error occurred: ", e);
            }
        }
        buttonStartStop.setText("Start Connection");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
        }
        if (factory != null) {
            factory.dispose();
        }
        if (localVideoView != null) {
            localVideoView.release();
        }
        if (remoteVideoView != null) {
            remoteVideoView.release();
        }
    }

}
