//package com.example.telepresencerobot;
//
//
//import android.content.Intent;
//import android.os.Bundle;
//
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.widget.Toolbar;
//import androidx.fragment.app.FragmentManager;
//
//import com.example.telepresencerobot.Serial.TerminalFragment;
//
//public class TestUart extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.uart_test);
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        getSupportFragmentManager().addOnBackStackChangedListener(this);
////        if (savedInstanceState == null)
////            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
////        else
////            onBackStackChanged();
//    }
//    @Override
//    public boolean onSupportNavigateUp() {
//        onBackPressed();
//        return true;
//    }
//    @Override
//    protected void onNewIntent(Intent intent) {
//        if("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
//            TerminalFragment terminal = (TerminalFragment)getSupportFragmentManager().findFragmentByTag("terminal");
//            if (terminal != null)
//                terminal.status("USB device detected");
//        }
//        super.onNewIntent(intent);
//    }
//
//    @Override
//    public void onBackStackChanged() {
//
//    }
//}
