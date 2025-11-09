package com.example.telepresencerobot;


import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;

import com.example.telepresencerobot.base.BaseWebRTCActivity;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.PeerConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RobotActivity extends BaseWebRTCActivity {
//    private enum UsbPermission { Unknown, Requested, Granted, Denied }
//    private static final int WRITE_WAIT_MILLIS = 2000;
//    private static final int READ_WAIT_MILLIS = 2000;
//    private UsbSerialPort usbSerialPort;
//    private SerialInputOutputManager usbIoManager;
//    private BroadcastReceiver broadcastReceiver;
    private boolean connected = false;
    private Button TestBut;
    private ImageView Mic;
    private UsbSerialPort port;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
//        ContextCompat.registerReceiver(this, broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB), ContextCompat.RECEIVER_NOT_EXPORTED);
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
        TestBut = findViewById(R.id.test_);
        remoteVideoView = findViewById(R.id.remote_video_view);
        try {
            initUsbService();
            initializeVideoViews();
            checkAndRequestPermissions();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void initUsbService() throws IOException {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            return;
        }
        port = driver.getPorts().get(0);
        try {
            port.open(connection);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
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
            port.write(Move.toString().getBytes(),1000);
        }catch(JSONException | IOException e){
            Log.e("RobotControl", "Error getting command", e);
        }
    }

    @Override
    public void onDisconnected(String reason) {
        super.onDisconnected(reason);
        try {
            port.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    protected void onDataChannelDisconnected() {
        runOnUiThread(() -> Toast.makeText(this, "DataChannel disconnected", Toast.LENGTH_SHORT).show());
    }


}