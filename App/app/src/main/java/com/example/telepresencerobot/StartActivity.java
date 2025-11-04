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
        Button test_button = findViewById(R.id.test_button);
        ButtonCreate(modeOne, MainActivity.class,false);
        ButtonCreate(modeTwo, RobotActivity.class,false);
        ButtonCreate(test_button, TestUart2.class,false);
    }
    private void ButtonCreate(Button _but,Class<?> ClassName,boolean Finish){
        _but.setOnClickListener(view -> {
            Intent mainActivityIntent = new Intent(getApplicationContext(), ClassName);
            startActivity(mainActivityIntent);
            if(Finish)finish();
        });
    }
}
