package com.example.telepresencerobot.webrtc;

import static com.example.telepresencerobot.webrtc.CameraCapturerFactory.createCameraCapturer;

import android.content.Context;

import org.webrtc.*;

import java.util.List;

public class WebRTCManager {
    private final Context context;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private CameraVideoCapturer videoCapturer;
    private final WebRTCListener listener;
    private final boolean isFrontCamera;

    public interface WebRTCListener {
        void onLocalVideoTrackCreated(VideoTrack videoTrack);
        void onRemoteVideoTrackReceived(VideoTrack videoTrack);
        void onConnectionStateChanged(PeerConnection.PeerConnectionState state);
        void onIceConnectionStateChanged(PeerConnection.IceConnectionState state);
        void onError(String error);
    }
    public WebRTCManager(Context context, WebRTCListener listener, boolean isFrontCamera) {
        this.context = context;
        this.listener = listener;
        this.isFrontCamera = isFrontCamera;
    }
    public void initialize(List<PeerConnection.IceServer> iceServers) {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                        .builder(context)
                        .createInitializationOptions()
        );

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnectionObserver());
    }
    public void setupLocalMedia(EglBase.Context eglContext) {
        VideoSource videoSource = factory.createVideoSource(false);
        videoCapturer = createCameraCapturer(context, isFrontCamera);
        if (videoCapturer == null) {
            listener.onError("Cannot create camera capturer");
            return;
        }
        SurfaceTextureHelper surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", eglContext);
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(1280, 720, 30);
        VideoTrack localVideoTrack = factory.createVideoTrack("local_video", videoSource);
        listener.onLocalVideoTrackCreated(localVideoTrack);
        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        AudioTrack localAudioTrack = factory.createAudioTrack("local_audio", audioSource);
        if (peerConnection != null) {
            peerConnection.addTrack(localVideoTrack);
            peerConnection.addTrack(localAudioTrack);
        }
    }
    public void createOffer() {
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver() {
                }, sdp);
            }
        }, sdpConstraints);
    }

    public void createAnswer() {
        MediaConstraints sdpConstraints = new MediaConstraints();
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sdp);
            }
        }, sdpConstraints);
    }
    public void setRemoteDescription(SessionDescription sdp) {
        peerConnection.setRemoteDescription(new SimpleSdpObserver(), sdp);
    }
    public void addIceCandidate(IceCandidate candidate) {
        peerConnection.addIceCandidate(candidate);
    }
    public void switchCamera(CameraVideoCapturer.CameraSwitchHandler handler) {
        if (videoCapturer != null) {
            videoCapturer.switchCamera(handler);
        }
    }
    public void stopCapture() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                listener.onError("Error stopping capture: " + e.getMessage());
            }
        }
    }
    public void close() {
        if (peerConnection != null) {
            peerConnection.close();
        }
    }
    public void dispose() {
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
    }
    public PeerConnection.PeerConnectionState getConnectionState() {
        return peerConnection != null ? peerConnection.connectionState() :
                PeerConnection.PeerConnectionState.CLOSED;
    }
    private class PeerConnectionObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            listener.onIceConnectionStateChanged(iceConnectionState);
        }
        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {}
        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
        }
        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
        @Override
        public void onAddStream(MediaStream mediaStream) {
            if (!mediaStream.videoTracks.isEmpty()) {
                VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                listener.onRemoteVideoTrackReceived(remoteVideoTrack);
            }
        }
        @Override
        public void onRemoveStream(MediaStream mediaStream) {}
        @Override
        public void onDataChannel(DataChannel dataChannel) {}
        @Override
        public void onRenegotiationNeeded() {}
        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            listener.onConnectionStateChanged(newState);
        }
    }
}
