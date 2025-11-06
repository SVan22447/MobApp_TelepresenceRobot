package com.example.telepresencerobot.webrtc;

import android.content.Context;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.nio.ByteBuffer;

public class PeerConnectionManager {
    private final Context appContext;
    private final PeerConnectionFactory factory;
    private final java.util.List<PeerConnection.IceServer> iceServers;
    private final EglBase.Context eglCtx;
    private final VideoSink localSink;
    private final boolean enableVideo;
    private final boolean relayOnly;
    private final VideoSink remoteSink;
    private PeerConnection pc;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceHelper;
    private boolean isAudioEnabled = true;
    private DataChannel dataChannel;

    public interface PeerConnectionListener {
        void onIceCandidate(IceCandidate candidate);
        void onConnected();
        void onDisconnected(String reason);
        void onRemoteVideoTrack(VideoTrack videoTrack);
    }
    public interface DataChannelListener {
        void onDataChannelMessage(String message);
        void onDataChannelStateChange(DataChannel.State state);
    }
    public PeerConnectionManager(
            Context appContext,
            PeerConnectionFactory factory,
            java.util.List<PeerConnection.IceServer> iceServers,
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
            Log.d(tag, "onCreateSuccess: " + desc.type);
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
    public void createPeer(final PeerConnectionListener listener, final DataChannelListener dcListener) {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        if (relayOnly) {
            rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
        }
        pc = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState newState) {
                Log.d("PeerConnection", "Signaling state: " + newState);
            }
            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
                switch (newState) {
                    case CONNECTED:
                    case COMPLETED:
                        listener.onConnected();
                        break;
                    case DISCONNECTED:
                    case FAILED:
                        listener.onDisconnected(newState.name());
                        break;
                    default:
                        break;
                }
            }
            @Override
            public void onIceConnectionReceivingChange(boolean receiving) {}
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                Log.d("PeerConnection", "Candidate: " + candidate.sdp);
                listener.onIceCandidate(candidate);
            }
            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    Log.d("PeerConnection", "ICE gathering COMPLETE");
                }
            }
            @Override
            public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
            @Override
            public void onAddStream(MediaStream stream) {
                if (!stream.videoTracks.isEmpty()) {
                    VideoTrack remoteVideoTrack = stream.videoTracks.get(0);
                    listener.onRemoteVideoTrack(remoteVideoTrack);
                }
            }
            @Override
            public void onRemoveStream(MediaStream stream) {}
            @Override
            public void onDataChannel(DataChannel dc) {}

            @Override
            public void onRenegotiationNeeded() {
                Log.d("PeerConnection", "Renegotiation needed");
            }
            @Override
            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                MediaStreamTrack track = receiver.track();
                if (track instanceof VideoTrack) {
                    VideoTrack videoTrack = (VideoTrack) track;
                    listener.onRemoteVideoTrack(videoTrack);
                    if (remoteSink != null) {
                        videoTrack.addSink(remoteSink);
                    }
                }
            }
            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                Log.d("PeerConnection", "Connection state: " + newState);
            }
        });
        if (pc == null) {
            throw new IllegalStateException("PeerConnection create failed.");
        }
        initLocalAudioTrack();
        if (enableVideo) {
            initLocalVideoTrack();
        }
        DataChannel.Init init = new DataChannel.Init();
        init.ordered = true;
        init.negotiated = false;
        dataChannel = pc.createDataChannel("robotControl", init);
        dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long amount) {}
            @Override
            public void onStateChange() {
                Log.d("DataChannel", "State: " + dataChannel.state());
                if (dcListener != null) {
                    dcListener.onDataChannelStateChange(dataChannel.state());
                }
            }
            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                if (buffer.binary) {
                    ByteBuffer data = buffer.data;
                    byte[] bytes = new byte[data.remaining()];
                    data.get(bytes);
                } else {
                    byte[] bytes = new byte[buffer.data.remaining()];
                    buffer.data.get(bytes);
                    String message = new String(bytes);
                    if (dcListener != null) {
                        dcListener.onDataChannelMessage(message);
                    }
                }
            }
        });
    }
    public void sendData(String data) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            DataChannel.Buffer buffer = new DataChannel.Buffer(
                    ByteBuffer.wrap(data.getBytes()), false // false = текст
            );
            dataChannel.send(buffer);
        } else {
            Log.w("PeerConnectionManager", "DataChannel not ready for sending");
        }
    }
    public void sendBinaryData(byte[] data) {
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            DataChannel.Buffer buffer = new DataChannel.Buffer(
                    ByteBuffer.wrap(data), true // true = бинарные данные
            );
            dataChannel.send(buffer);
        }
    }

    private void initLocalAudioTrack() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("audio0", audioSource);
        if (pc != null) {
            pc.addTrack(localAudioTrack);
            Log.d("PeerConnectionManager", "Audio track added");
        }
    }
    private void initLocalVideoTrack() {
        surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglCtx);
        videoCapturer = createBestCapturer();
        if (videoCapturer != null) {
            videoSource = factory.createVideoSource(false);
            videoCapturer.initialize(surfaceHelper, appContext, videoSource.getCapturerObserver());
            videoCapturer.startCapture(640, 480, 30);
            localVideoTrack = factory.createVideoTrack("video0", videoSource);
            if (localSink != null) {
                localVideoTrack.addSink(localSink);
            }
            if (pc != null) {
                pc.addTrack(localVideoTrack);
            }
        } else {
            Log.w("PeerConnectionManager", "Video capturer could not be created");
        }
    }
    public void setAudioEnabled(boolean enabled) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enabled);
            isAudioEnabled = enabled;
        } else {
            Log.w("PeerConnectionManager", "Audio track is null, cannot set enabled to " + enabled);
        }
    }
    public void toggleAudio() {
        setAudioEnabled(!isAudioEnabled);
    }
    public boolean isAudioEnabled() {
        return isAudioEnabled && localAudioTrack != null && localAudioTrack.enabled();
    }
    public void createOffer(final SdpCreateListener listener) {
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        pc.createOffer(new LoggingSdpObserver("createOffer") {
            @Override
            public void onCreateSuccess(SessionDescription desc) {
                pc.setLocalDescription(new LoggingSdpObserver("setLocalOffer"), desc);
                listener.onSdpReady(desc);
            }
            @Override
            public void onCreateFailure(String error) {
                Log.e("PeerConnectionManager", "Create offer failed: " + error);
            }
        }, sdpConstraints);
    }
    public void createAnswer(final SdpCreateListener listener) {
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        pc.createAnswer(new LoggingSdpObserver("createAnswer") {
            @Override
            public void onCreateSuccess(SessionDescription desc) {
                pc.setLocalDescription(new LoggingSdpObserver("setLocalAnswer"), desc);
                listener.onSdpReady(desc);
            }
            @Override
            public void onCreateFailure(String error) {
                Log.e("PeerConnectionManager", "Create answer failed: " + error);
            }
        }, sdpConstraints);
    }
    public void setRemoteDescription(SessionDescription desc) {
        pc.setRemoteDescription(new LoggingSdpObserver("setRemote") {
            @Override
            public void onSetSuccess() {}
            @Override
            public void onSetFailure(String error) {
                setRemoteDescriptionWithRelaxedConstraints(desc);
            }
        }, desc);
    }
    private void setRemoteDescriptionWithRelaxedConstraints(SessionDescription desc) {
        try {
            String sdp = desc.description;
            sdp = sdp.replace("UDP/TLS/RTP/SAVPF", "RTP/SAVPF");
            SessionDescription modifiedDesc = new SessionDescription(desc.type, sdp);
            pc.setRemoteDescription(new LoggingSdpObserver("setRemoteRelaxed"), modifiedDesc);
        } catch (Exception e) {
            Log.e("PeerConnectionManager", "Failed to set remote description with relaxed constraints", e);
        }
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
    public void stopCapture() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e("PeerConnectionManager", "Error stopping capture", e);
            }
        }
    }
    public void close() {
        if (pc != null) {
            pc.close();
        }
    }
    public void dispose() {
        try {
            if (dataChannel != null) {
                dataChannel.close();
                dataChannel.dispose();
                dataChannel = null;
            }
            if (videoCapturer != null) {
                videoCapturer.stopCapture();
                videoCapturer.dispose();
                videoCapturer = null;
            }
            if (localVideoTrack != null) {
                if (localSink != null) {
                    localVideoTrack.removeSink(localSink);
                }
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
                pc.dispose();
                pc = null;
            }
        } catch (Exception e) {
            Log.e("PeerConnectionManager", "Error during dispose", e);
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
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    return capturer;
                }
            }
        }
        for (String deviceName : deviceNames) {
            VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
            if (capturer != null) {
                return capturer;
            }
        }
        return null;
    }
    public interface SdpCreateListener {
        void onSdpReady(SessionDescription sdp);
    }
}