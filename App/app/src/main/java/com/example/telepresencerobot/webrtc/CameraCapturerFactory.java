package com.example.telepresencerobot.webrtc;

import android.content.Context;
import org.webrtc.*;

public class CameraCapturerFactory {
    public static CameraVideoCapturer createCameraCapturer(Context context, boolean preferFrontCamera) {
        Camera1Enumerator enumerator = new Camera1Enumerator(false);
        String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (preferFrontCamera && enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, new CameraEventsHandler(context));
            } else if (!preferFrontCamera && enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, new CameraEventsHandler(context));
            }
        }
        for (String deviceName : deviceNames) {
            return enumerator.createCapturer(deviceName, new CameraEventsHandler(context));
        }
        return null;
    }
    private static class CameraEventsHandler implements CameraVideoCapturer.CameraEventsHandler {
        CameraEventsHandler(Context context) {
        }
        @Override
        public void onCameraError(String errorDescription) {}
        @Override
        public void onCameraDisconnected() {}
        @Override
        public void onCameraFreezed(String errorDescription) {}
        @Override
        public void onCameraOpening(String cameraName) {}
        @Override
        public void onFirstFrameAvailable() {}
        @Override
        public void onCameraClosed() {}
    }
}
