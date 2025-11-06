package com.example.telepresencerobot;


import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;

import com.example.telepresencerobot.base.BaseWebRTCActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.PeerConnection;

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
//        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
//        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
//        if (availableDrivers.isEmpty()) {
//            return;
//        }
//        UsbSerialDriver driver = availableDrivers.get(0);
//        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
//        if (connection == null) {
//            return;
//        }
        Mic = findViewById(R.id.Mic);
        Mic.setImageAlpha(0);
        TestBut = findViewById(R.id.test_);
        remoteVideoView = findViewById(R.id.remote_video_view);
        initializeVideoViews();
        checkAndRequestPermissions();
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
        }catch(JSONException e){
            Log.e("RobotControl", "Error getting command", e);
        }
    }
//    private void connect() {
//        UsbDevice device = null;
//        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
//        for(UsbDevice v : usbManager.getDeviceList().values())
//            if(v.getDeviceId() == deviceId)
//                device = v;
//        if(device == null) {
//            status("connection failed: device not found");
//            return;
//        }
//        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
//        if(driver == null) {
//            driver = CustomProber.getCustomProber().probeDevice(device);
//        }
//        if(driver == null) {
//            status("connection failed: no driver for device");
//            return;
//        }
//        if(portNum >= driver.getPorts().size()) {
//            status("connection failed: not enough ports at device");
//            return;
//        }
//        usbSerialPort = driver.getPorts().get(portNum);
//        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
//        if(usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
//            usbPermission = UsbPermission.Requested;
//            int flags = PendingIntent.FLAG_MUTABLE;
//            Intent intent = new Intent(INTENT_ACTION_GRANT_USB);
//            intent.setPackage(this.getPackageName());
//            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags);
//            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
//            return;
//        }
//        if(usbConnection == null) {
//            if (!usbManager.hasPermission(driver.getDevice()))
//                status("connection failed: permission denied");
//            else
//                status("connection failed: open failed");
//            return;
//        }
//
//        try {
//            usbSerialPort.open(usbConnection);
//            try{
//                usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
//            }catch (UnsupportedOperationException e){
//                status("unsupport setparameters");
//            }
//            if(withIoManager) {
//                usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
//                usbIoManager.start();
//            }
//            status("connected");
//            connected = true;
//            controlLines.start();
//        } catch (Exception e) {
//            status("connection failed: " + e.getMessage());
//            disconnect();
//        }
//    }
//    @Override
//    public void onNewData(byte[] data) {
//        mainLooper.post(() -> receive(data));
//    }
//
//    @Override
//    public void onRunError(Exception e) {
//        mainLooper.post(() -> {
//            status("connection lost: " + e.getMessage());
//            disconnect();
//        });
//    }
//    private void send(String str) {
//        if(!connected) {
//            Toast.makeText(this, "not connected", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        try {
//            byte[] data = (str + '\n').getBytes();
////            SpannableStringBuilder spn = new SpannableStringBuilder();
////            spn.append("send " + data.length + " bytes\n");
////            spn.append(HexDump.dumpHexString(data)).append("\n");
////            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
////            receiveText.append(spn);
//            usbSerialPort.write(data, WRITE_WAIT_MILLIS);
//        } catch (Exception e) {
//            onRunError(e);
//        }
//    }
    @Override
    protected void onDataChannelDisconnected() {
        runOnUiThread(() -> Toast.makeText(this, "DataChannel disconnected", Toast.LENGTH_SHORT).show());
    }


}