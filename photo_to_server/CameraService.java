package com.example.photo_to_server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.photo_to_server.R;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CameraService extends Service {
    private static final String CHANNEL_ID = "CameraServiceChannel";
    private static final String TAG = "BackgroundCamera";

    private CameraManager cameraManager;
    private String cameraId;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ImageReader imageReader;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    int count;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundService();
        startBackgroundThread();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            count = Integer.parseInt(intent.getStringExtra("key_count"));
            Log.d(TAG, "Camera Service Started");
            initializeCamera();
        }
        return Service.START_NOT_STICKY;
    }

    private void startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Camera Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Background Camera Running")
                .setContentText("Capturing images in the background")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void initializeCamera() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            cameraId = cameraManager.getCameraIdList()[0]; // Get the first camera (usually rear)
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 3);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    saveImage(image);
                    image.close();
                }
            }, backgroundHandler);
            openCamera();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            if (cameraManager != null) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        cameraDevice = camera;
                        createCameraCaptureSession();
                    }

                    @Override
                    public void onDisconnected(CameraDevice camera) {
                        camera.close();
                    }

                    @Override
                    public void onError(CameraDevice camera, int error) {
                        camera.close();
                    }
                }, backgroundHandler);
            }
        } catch (SecurityException | CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCameraCaptureSession() {
        try {
            List<Surface> outputSurfaces = new ArrayList<>();
            outputSurfaces.add(imageReader.getSurface());

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    new Handler().postDelayed(() -> captureImage(), 500);  // Small delay to ensure session is ready
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Camera Capture Session Failed");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureImage() {
        try {
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {}, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void saveImage(Image image) {
        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BackgroundCamera");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File file = new File("/storage/emulated/0/Pictures/" + "take-photo" + Integer.toString(count) + ".jpg");

        try (FileOutputStream fos = new FileOutputStream(file)) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer(); // Move this outside the try
            buffer.rewind();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            fos.write(bytes);
            fos.close();
            buffer.clear();
            Log.d(TAG, "Image Saved: " + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

