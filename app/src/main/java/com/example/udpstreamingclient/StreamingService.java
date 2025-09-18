package com.example.udpstreamingclient;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StreamingService extends Service {

    private static final String TAG = "StreamingService";

    private ExecutorService executorService;
    private volatile boolean isRunning = false;
    private DatagramSocket socket;
    private FFmpegSession ffmpegSession;
    private String pipePath;

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String serverAddress = intent.getStringExtra("serverAddress");
        int serverPort = intent.getIntExtra("serverPort", 0);
        String imei = intent.getStringExtra("imei");

        executorService.submit(() -> {
            try {
                isRunning = true;
                startStreaming(serverAddress, serverPort, imei);
            } catch (Exception e) {
                Log.e(TAG, "Streaming failed", e);
                stopSelf(); // Stop service if something goes wrong
            }
        });

        return START_NOT_STICKY;
    }

    private void startStreaming(String address, int port, String imei) throws IOException {
        // 1. Setup FFmpeg with a named pipe for input
        pipePath = FFmpegKit.registerNewFFmpegPipe(this);
        String command = String.format("-f amrwb -i %s -f opensles -", pipePath);

        ffmpegSession = FFmpegKit.executeAsync(command, session -> {
            Log.d(TAG, String.format("FFmpeg process exited with state %s and rc %s.%s", session.getState(), session.getReturnCode(), session.getFailStackTrace()));
            if (!ReturnCode.isSuccess(session.getReturnCode())) {
                // FFmpeg failed, stop the service
                stopSelf();
            }
        });

        Log.d(TAG, "FFmpeg session started");

        // 2. Setup UDP socket and send initiation message
        socket = new DatagramSocket();
        InetAddress serverAddress = InetAddress.getByName(address);
        String initMessage = String.format("[ZJ*%s*0001*0001*2]", imei);
        byte[] initData = initMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket initPacket = new DatagramPacket(initData, initData.length, serverAddress, port);
        socket.send(initPacket);

        Log.d(TAG, "Sent initiation message: " + initMessage);

        // 3. Start receiving audio data and pipe it to FFmpeg
        byte[] buffer = new byte[4096]; // Increased buffer size for safety
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);

        while (isRunning) {
            try {
                socket.receive(receivePacket); // This is a blocking call
                if (receivePacket.getLength() > 0) {
                    byte[] audioData = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
                    FFmpegKit.writeToPipe(pipePath, audioData);
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Socket receive error", e);
                }
                break; // Exit loop on error
            }
        }

        // 4. Cleanup
        cleanup();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        isRunning = false;
        cleanup();
        executorService.shutdownNow();
    }

    private void cleanup() {
        if (ffmpegSession != null) {
            FFmpegKit.cancel(ffmpegSession);
            ffmpegSession = null;
        }
        if (pipePath != null) {
            // There is no explicit unregister, closing the pipe is handled by FFmpegKit
            FFmpegKit.closePipe(pipePath);
            pipePath = null;
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
