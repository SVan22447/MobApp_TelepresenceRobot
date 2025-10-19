package com.example.telepresencerobot;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.pedro.common.ConnectChecker;
import com.pedro.library.rtmp.RtmpCamera2;
import com.pedro.library.view.OpenGlView;


public class MainActivity extends AppCompatActivity {
    private OpenGlView openGlView;
    private RtmpCamera2 rtmpCamera;
    private Button buttonStartStop;
    private Spinner cameraSpinner;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private final ConnectChecker connectCheckerRtmp = new ConnectChecker() {
        @Override
        public void onAuthSuccess() {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Auth success", Toast.LENGTH_SHORT).show());
        }
        @Override
        public void onAuthError() {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Auth error", Toast.LENGTH_SHORT).show());
        }
        @Override
        public void onDisconnect() {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                buttonStartStop.setText("Начать стрим");
            });
        }
        @Override
        public void onConnectionFailed(@NonNull String s) {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Connection failed: " + s, Toast.LENGTH_SHORT).show());
        }
        @Override
        public void onConnectionSuccess() {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Connection success", Toast.LENGTH_SHORT).show());
        }
        @Override
        public void onConnectionStarted(@NonNull String s) {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Connection started: " + s, Toast.LENGTH_SHORT).show());
        }

    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        openGlView = findViewById(R.id.view3);
        buttonStartStop = findViewById(R.id.button_start_stop);
        cameraSpinner = findViewById(R.id.cameraSpinner);
        if (openGlView == null) {
            Toast.makeText(this, "OpenGlView not found!", Toast.LENGTH_LONG).show();
        }
        setSupportActionBar(findViewById(R.id.toolbar)); //включаем toolbar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {// Разрешение получено

                    } else {
                        Toast.makeText(
                                MainActivity.this,
                                "Camera permission is required to use this feature.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );


        buttonStartStop.setOnClickListener(v -> toggleStream());;
        checkAndRequestCameraPermission();

    }
    private void initializeCamera() {
        try {
            if (openGlView != null) {
                rtmpCamera = new RtmpCamera2(openGlView, connectCheckerRtmp);
//                startPreview();
            } else {
                Toast.makeText(this, "OpenGlView is not initialized", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error initializing camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    private void checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initializeCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }



//    private void startPreview() {
//        if (!rtmpCamera.isOnPreview()) {
//            rtmpCamera.startPreview();
//        }
//    }
    private void toggleStream() {
        if (!rtmpCamera.isStreaming()) {
            startStream();
        } else {
            stopStream();
        }
    }
    private void startStream() {
        if (rtmpCamera == null) {
            Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        String rtmpUrl = "rtmp://";//will be later
        if (rtmpCamera.prepareAudio() && rtmpCamera.prepareVideo()) {
            rtmpCamera.startStream(rtmpUrl);
            buttonStartStop.setText("Остановить стрим");
        } else {
            Toast.makeText(this, "Ошибка подготовки стрима", Toast.LENGTH_SHORT).show();
        }
    }
    private void stopStream() {
        if (rtmpCamera.isStreaming()) {
            rtmpCamera.stopStream();
        }
        buttonStartStop.setText("Начать стрим");
    }
//    @Override
//    protected void onResume() {
//        super.onResume();
//        if (!rtmpCamera.isOnPreview()) {
//            startPreview();
//        }
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        if (rtmpCamera.isStreaming()) {
//            rtmpCamera.stopStream();
//        }
//        if (rtmpCamera.isOnPreview()) {
//            rtmpCamera.stopPreview();
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (rtmpCamera != null) {
//            rtmpCamera.stopStream();
//            rtmpCamera.stopPreview();
//        }
//    }

}
