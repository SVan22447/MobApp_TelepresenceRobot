package com.example.telepresencerobot;

import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;

public class TestUart2 extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uart_test);
        Toolbar toolbar = findViewById(R.id.toolbar);
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }
//        // Open a connection to the first available driver.
//        UsbSerialDriver driver = availableDrivers.get(0);
//        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
//        if (connection == null) {
//            // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
//            return;
//        }
//        UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)
//        try {
//            port.open(connection);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        try {
//            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, (SerialInputOutputManager.Listener) this);
//        usbIoManager.start();
//        try {
//            port.write("hello".getBytes(), WRITE_WAIT_MILLIS);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }
//        @Override
//        public void onNewData(byte[] data) {
//            runOnUiThread(() -> { textView.append(new String(data)); });
//        }

}
