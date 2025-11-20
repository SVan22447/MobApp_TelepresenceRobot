package com.example.telepresence_rtsprtmp;

import static androidx.core.content.ContextCompat.getSystemService;
import static androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;

import com.example.myapplication.R;
import com.example.telepresencerobot.base.BaseWebRTCActivity;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RobotActivity extends BaseWebRTCActivity {
    private ImageView Mic;
    private UsbSerialPort port;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        this.getWindow().getDecorView().setSystemUiVisibility(
                ImageView.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | ImageView.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | ImageView.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_robot);
        Mic = findViewById(R.id.Mic);
        Mic.setImageAlpha(0);
        remoteVideoView = findViewById(R.id.remote_video_view);
        initializeVideoViews();
        checkAndRequestPermissions();
        try {
            if (!initUsbService()) {
                Toast.makeText(this, "USB устройство не найдено", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private boolean initUsbService() throws IOException {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            Log.e("USB", "No USB devices found");
            return false;
        }
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            return false;
        }
        port = driver.getPorts().get(0);
        try {
            port.open(connection);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        return true;
    }
    @Override
    protected void onPermissionsGranted() {
        Log.d("RobotActivity", "Permissions granted, initializing connection");
        initializePeerConnection();
        connectToSignalingServer();
    }
    @Override
    protected boolean isFrontCameraPreferred() {
        return true;
    }
    @Override
    protected List<PeerConnection.IceServer> getIceServers() {
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
        return "default-room"; // Такая же комната как в MainActivity
    }
    @Override
    protected boolean isOfferer() {return false;}

    @Override
    protected void handleRobotData(String data) {
        try {
            JSONObject robotData = new JSONObject(data);
            String type = robotData.getString("type");
            if ("movement".equals(type)) {
                getMove(robotData);
            }else if("mic".equals(type)){
                updateMicButtonState("true".equals(robotData.getString("status")));
            }
        } catch (JSONException e) {
            Log.e("RobotControl", "Error parsing robot data", e);
        }
    }

    @Override
    protected void onDataChannelConnected() {
        runOnUiThread(() -> Toast.makeText(this, "DataChannel connected", Toast.LENGTH_SHORT).show());
    }
    @Override
    protected boolean hasLocalVideo() {
        return true;
    }
    @Override
    protected void updateMicButtonState(boolean isEnabled) {
        runOnUiThread(() -> {
            if(Mic != null){
                Mic.setImageAlpha(isEnabled ? 0 : 255);
            }
        });
    }
    @Override
    public void onConnected() {
        super.onConnected();
        runOnUiThread(() -> Toast.makeText(this, "Robot connected - waiting for offer", Toast.LENGTH_SHORT).show());
    }
    @Override
    public void onPeerJoined(String peerId) {
        super.onPeerJoined(peerId);
    }
    @Override
    public void onOffer(String from, String sdp) {
        super.onOffer(from, sdp);
    }
    private void getMove(JSONObject Move){
        try {
            Toast.makeText(this, "Get the "+Move.getString("direction")
                    +"_"+Move.getString("action"), Toast.LENGTH_SHORT).show();
            if (port != null) {
                port.write(Move.toString().getBytes(), 1000);
            } else {
                Log.w("USB", "Attempted to send command but USB port is not available");
            }
        }catch(JSONException | IOException e){
            Log.e("RobotControl", "Error getting command", e);
        }
    }

    @Override
    public void onDisconnected(String reason) {
        super.onDisconnected(reason);
        if (port != null) {
            try {
                port.close();
            } catch (IOException e) {
                Log.e("USB", "Error closing USB port", e);
            }
        }
    }
    @Override
    protected void onDataChannelDisconnected() {
        runOnUiThread(() -> Toast.makeText(this, "DataChannel disconnected", Toast.LENGTH_SHORT).show());
    }


}