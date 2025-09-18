package com.example.udpstreamingclient;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private EditText etServerAddress, etServerPort, etImei;
    private Button btnReset, btnRandomize, btnStart;

    private boolean isStreaming = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServerAddress = findViewById(R.id.etServerAddress);
        etServerPort = findViewById(R.id.etServerPort);
        etImei = findViewById(R.id.etImei);
        btnReset = findViewById(R.id.btnReset);
        btnRandomize = findViewById(R.id.btnRandomize);
        btnStart = findViewById(R.id.btnStart);

        generateRandomImei();

        btnReset.setOnClickListener(v -> etServerAddress.setText(R.string.default_server_address));

        btnRandomize.setOnClickListener(v -> generateRandomImei());

        btnStart.setOnClickListener(v -> {
            if (isStreaming) {
                stopStreaming();
            } else {
                startStreaming();
            }
        });
    }

    private void startStreaming() {
        String serverAddress = etServerAddress.getText().toString().trim();
        String serverPortStr = etServerPort.getText().toString().trim();
        String imei = etImei.getText().toString().trim();

        if (TextUtils.isEmpty(serverAddress) || TextUtils.isEmpty(serverPortStr) || TextUtils.isEmpty(imei)) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        int serverPort;
        try {
            serverPort = Integer.parseInt(serverPortStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, StreamingService.class);
        serviceIntent.putExtra("serverAddress", serverAddress);
        serviceIntent.putExtra("serverPort", serverPort);
        serviceIntent.putExtra("imei", imei);
        startService(serviceIntent);

        isStreaming = true;
        updateUI();
    }

    private void stopStreaming() {
        Intent serviceIntent = new Intent(this, StreamingService.class);
        stopService(serviceIntent);

        isStreaming = false;
        updateUI();
    }

    private void updateUI() {
        if (isStreaming) {
            btnStart.setText(R.string.stop);
            etServerAddress.setEnabled(false);
            etServerPort.setEnabled(false);
            etImei.setEnabled(false);
            btnRandomize.setEnabled(false);
            btnReset.setEnabled(false);
        } else {
            btnStart.setText(R.string.start);
            etServerAddress.setEnabled(true);
            etServerPort.setEnabled(true);
            etImei.setEnabled(true);
            btnRandomize.setEnabled(true);
            btnReset.setEnabled(true);
        }
    }

    private void generateRandomImei() {
        Random random = new Random();
        StringBuilder imeiBuilder = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            imeiBuilder.append(random.nextInt(10));
        }
        etImei.setText(imeiBuilder.toString());
    }
}
