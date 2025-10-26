package com.example.telepresencerobot.webrtc;

import android.content.Context;
import android.util.Log;

import org.webrtc.*;

import java.util.List;

public class PeerConnectionManager {
    private final Context appContext;
    private final PeerConnectionFactory factory;
    private final List<PeerConnection.IceServer> iceServers;
    private final EglBase.Context eglCtx;
    private final VideoSink remoteSink;
    private final VideoSink localSink;
    private final boolean enableVideo;
    private final boolean relayOnly;

    private PeerConnection pc;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceHelper;

    public interface PeerConnectionListener {
        void onIceCandidate(IceCandidate candidate);
        void onConnected();
        void onDisconnected(String reason);
        void onRemoteVideoTrack(VideoTrack videoTrack);
    }

    public PeerConnectionManager(
            Context appContext,
            PeerConnectionFactory factory,
            List<PeerConnection.IceServer> iceServers,
            EglBase.Context eglCtx,
            VideoSink remoteSink,
            VideoSink localSink,
            boolean enableVideo,
            boolean relayOnly) {
        this.appContext = appContext;
        this.factory = factory;
        this.iceServers = iceServers;
        this.eglCtx = eglCtx;
        this.remoteSink = remoteSink;
        this.localSink = localSink;
        this.enableVideo = enableVideo;
        this.relayOnly = relayOnly;
    }
    private static class LoggingSdpObserver implements SdpObserver {
        private final String tag;
        LoggingSdpObserver(String tag) {
            this.tag = tag;
        }
        @Override
        public void onCreateSuccess(SessionDescription desc) {
            Log.d(tag, "onCreateSuccess");
        }
        @Override
        public void onSetSuccess() {
            Log.d(tag, "onSetSuccess");
        }
        @Override
        public void onCreateFailure(String error) {
            Log.e(tag, "onCreateFailure: " + error);
        }
        @Override
        public void onSetFailure(String error) {
            Log.e(tag, "onSetFailure: " + error);
        }
    }
    public void createPeer(final PeerConnectionListener listener) {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        if (relayOnly) {
            rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
        }

        pc = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState newState) {
                // Do nothing
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
                Log.d("PeerConnection", "ICE state changed: " + newState);
                switch (newState) {
                    case CONNECTED:
                    case COMPLETED:
                        listener.onConnected();
                        break;
                    case DISCONNECTED:
                    case FAILED:
                    case CLOSED:
                        listener.onDisconnected(newState.name());
                        break;
                    default:
                        // Do nothing
                        break;
                }
            }
            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {
                // Do nothing
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
                // Do nothing
            }

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                listener.onIceCandidate(candidate);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {
                // Do nothing
            }

            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
                MediaStreamTrack track = receiver.track();
                if (track instanceof VideoTrack) {
                    VideoTrack videoTrack = (VideoTrack) track;
                    videoTrack.addSink(remoteSink);
                    listener.onRemoteVideoTrack(videoTrack);
                }
            }

            @Override
            @Deprecated
            public void onAddStream(MediaStream stream) {
                // Legacy, not used in Unified Plan
            }

            @Override
            @Deprecated
            public void onRemoveStream(MediaStream stream) {
                // Legacy, not used in Unified Plan
            }

            @Override
            public void onDataChannel(DataChannel dc) {
                // Do nothing
            }

            @Override
            public void onRenegotiationNeeded() {
                // Do nothing
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                Log.d("PeerConnection", "Connection state changed: " + newState);
            }
        });

        if (pc == null) {
            throw new IllegalStateException("PeerConnection create failed. Check logs for WebRTC errors.");
        }

        initLocalAudioTrack();
        if (enableVideo) {
            initLocalVideoTrack();
        }
    }
    private void initLocalAudioTrack() {
        audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack("audio0", audioSource);
        pc.addTrack(localAudioTrack);
    }
    private void initLocalVideoTrack() {
        surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglCtx);
        videoCapturer = createBestCapturer();

        if (videoCapturer != null) {
            videoSource = factory.createVideoSource(videoCapturer.isScreencast());
            videoCapturer.initialize(surfaceHelper, appContext, videoSource.getCapturerObserver());
            videoCapturer.startCapture(640, 480, 30);

            localVideoTrack = factory.createVideoTrack("video0", videoSource);
            localVideoTrack.addSink(localSink);
            pc.addTrack(localVideoTrack);
        } else {
            Log.w("PeerConnectionManager", "Video capturer could not be created. Video will not be sent.");
        }
    }
    public void createOffer(final SdpCreateListener listener) {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        pc.createOffer(new LoggingSdpObserver("createOffer") {
            @Override
            public void onCreateSuccess(SessionDescription desc) {
                pc.setLocalDescription(new LoggingSdpObserver("createOffer"), desc);
                listener.onSdpReady(desc);
            }
            @Override
            public void onCreateFailure(String error) {
                Log.e("createOffer", "Failed: " + error);
            }
        }, constraints);
    }
    public void createAnswer(final SdpCreateListener listener) {
        MediaConstraints constraints = new MediaConstraints();

        pc.createAnswer(new LoggingSdpObserver("createAnswer") {
            @Override
            public void onCreateSuccess(SessionDescription desc) {
                pc.setLocalDescription(new LoggingSdpObserver("createAnswer"), desc);
                listener.onSdpReady(desc);
            }
            @Override
            public void onCreateFailure(String error) {
                Log.e("createAnswer", "Failed: " + error);
            }
        }, constraints);
    }
    public void setRemoteDescription(SessionDescription desc) {
        pc.setRemoteDescription(new LoggingSdpObserver("setRemote"), desc);
    }
    public void addIceCandidate(IceCandidate candidate) {
        pc.addIceCandidate(candidate);
    }
    public void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraCapturer = (CameraVideoCapturer) videoCapturer;
            cameraCapturer.switchCamera(null);
        }
    }
    public void close() {
        try {
            if (videoCapturer != null) {
                videoCapturer.stopCapture();
                videoCapturer.dispose();
                videoCapturer = null;
            }

            if (localVideoTrack != null) {
                localVideoTrack.removeSink(localSink);
                localVideoTrack.dispose();
                localVideoTrack = null;
            }

            if (surfaceHelper != null) {
                surfaceHelper.dispose();
                surfaceHelper = null;
            }

            if (videoSource != null) {
                videoSource.dispose();
                videoSource = null;
            }

            if (localAudioTrack != null) {
                localAudioTrack.dispose();
                localAudioTrack = null;
            }

            if (audioSource != null) {
                audioSource.dispose();
                audioSource = null;
            }

            if (pc != null) {
                pc.close();
                pc = null;
            }
        } catch (Exception e) {
            Log.e("PeerConnectionManager", "Error during close", e);
        }
    }
    private VideoCapturer createBestCapturer() {
        if (Camera2Enumerator.isSupported(appContext)) {
            return createCapturer(new Camera2Enumerator(appContext));
        } else {
            return createCapturer(new Camera1Enumerator(true));
        }
    }
    private VideoCapturer createCapturer(CameraEnumerator enumerator) {
        String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null);
            }
        }
        if (deviceNames.length > 0) {
            return enumerator.createCapturer(deviceNames[0], null);
        }

        return null;
    }

    public interface SdpCreateListener {
        void onSdpReady(SessionDescription sdp);
    }
}