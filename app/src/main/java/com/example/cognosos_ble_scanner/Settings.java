package com.example.cognosos_ble_scanner;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Settings extends AppCompatActivity {
    private TextView timerView;
    private Button applyButton;
    private SeekBar timeSlider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        timerView = findViewById(R.id.timerView);
        applyButton = findViewById(R.id.applyButton);
        timeSlider = findViewById(R.id.timeSlider);

        timeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                timerView.setText("Current Time: " + progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        applyButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("SCAN_TIME", timeSlider.getProgress());
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        // Initialize the TextView with the current progress
        timerView.setText("Current Time: " + timeSlider.getProgress());
    }
}