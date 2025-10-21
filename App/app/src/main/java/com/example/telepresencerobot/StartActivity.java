package com.example.telepresencerobot;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class StartActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_start);
        Button modeOne = findViewById(R.id.modeOne);
        Button modeTwo = findViewById(R.id.modeTwo);
        modeOne.setOnClickListener(view -> {
            Intent mainActivityIntent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(mainActivityIntent);
        });
        modeTwo.setOnClickListener(view -> {
            Intent mainActivityIntent = new Intent(getApplicationContext(), RobotActivity.class);
            startActivity(mainActivityIntent);
        });
    }
}
