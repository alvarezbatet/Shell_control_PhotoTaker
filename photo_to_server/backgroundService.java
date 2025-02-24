package com.example.photo_to_server;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import android.app.Notification;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import android.os.Handler;
import android.os.Looper;
import android.util.Size;

public class backgroundService extends Service {
    private static final String TAG = "MMMMMMMMMMMMM";
    private static final String CHANNEL_ID = "ForegroundServiceChannel";
    private String SERVER_IP;
    private String SERVER_PORT;
    private int count = 0;
    final int REQUEST_CAMERA_PERMISSION = 200;
    CameraManager cameraManager;
    CameraDevice cameraDevice;
    ImageReader imageReader;
    private static final int CAMERA_REQUEST_CODE = 100;

    @Override
    public void onCreate() {
        super.onCreate();
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.d(TAG, "Service Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Foreground Service Started");

        // Get variables passed from Activity
        SERVER_IP = intent.getStringExtra("key_IP");
        SERVER_PORT = intent.getStringExtra("key_PORT");

        // Create Notification for Foreground Service
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service Running")
                .setContentText("Listening to server messages...")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Change icon
                .build();

        // Start foreground service with notification
        startForeground(1, notification);

        // Start background thread
        new Thread(() -> {
            try {
                getMessagesFromServer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return START_STICKY; // Keeps service running after killed
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }




    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened: ");
            cameraDevice = camera;
            try {
                createCaptureSession();
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
        }
    };
    private final CameraCaptureSession.StateCallback captureSessionCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "onConfigured: ");
            CaptureRequest.Builder captureRequestBuilder = null;
            try {
                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
            captureRequestBuilder.addTarget(imageReader.getSurface()); // Add the image reader surface
            try {
                session.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        Image image = imageReader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        buffer.rewind();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        image.close();

                        // Save the image bytes as a JPEG file
                        try {
                            File galleryDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                            String fileName = "take-photo";
                            String extension = ".jpg"; // Customize the file name as needed
                            fileName += Integer.toString(count);
                            count += 1;
                            File imageFile = new File(galleryDir, fileName + extension);
                            FileOutputStream fos = new FileOutputStream(imageFile);
                            fos.write(bytes);
                            fos.close();
                            buffer.clear();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }, null);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "onConfigureFailed: ");
        }
    };


    @NonNull
    public Size getResolution(@NonNull final CameraManager cameraManager, @NonNull final String cameraId) throws CameraAccessException {
        final CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        final StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
            throw new IllegalStateException("Failed to get configuration map.");
        }

        final Size[] choices = map.getOutputSizes(ImageFormat.JPEG);

        Arrays.sort(choices, Collections.reverseOrder(new Comparator<Size>() {
            @Override
            public int compare(@NonNull final Size lhs, @NonNull final Size rhs) {
                // Cast to ensure the multiplications won't overflow
                return Long.signum((lhs.getWidth() * (long) lhs.getHeight()) - (rhs.getWidth() * (long) rhs.getHeight()));
            }
        }));

        return choices[0];
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String id : cameraIds) {
                Log.d(TAG, id);
            }
            String cameraId = "0"; // Rear camera ID (adjust as needed)

            Size size = getResolution(cameraManager, cameraId);

            Size imageSize = new Size(size.getWidth(), size.getHeight()); // Example size
            imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(), ImageFormat.JPEG, 1);
            cameraManager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void createCaptureSession() throws CameraAccessException {
        List<OutputConfiguration> outputConfig = Collections.singletonList(new OutputConfiguration(imageReader.getSurface()));
        cameraDevice.createCaptureSessionByOutputConfigurations(outputConfig, captureSessionCallback, null);

    }
    public void getMessagesFromServer() {
        try {
            while (true) {
                // Send GET request to receive the server message
                URL url = new URL("http://" + SERVER_IP + ":" + SERVER_PORT + "/receive");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = reader.readLine();
                if (Objects.equals(response, "1")) {
                    Log.d(TAG, "message from server == 1");
                    uploadFile();
                } else if (Objects.equals(response, "2")) {
                    Log.d(TAG, "message from server == 2");
                    Intent serviceIntent = new Intent(this, CameraService.class);
                    serviceIntent.putExtra("key_count", Integer.toString(count));
                    startForegroundService(serviceIntent); // Ensures it runs as a foreground service
                } else if (Objects.equals(response, "3")) {
                    Log.d(TAG, "message from server == 3");
                    deleteImages();
                }
            }
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    // Upload image to server
    private String uploadFile() {
        new Thread(() -> {
            try {
                URL url = new URL("http://" + SERVER_IP + ":" + SERVER_PORT + "/send");

                // Check if file exists
                String galleryDir = "/storage/emulated/0/Pictures/";
                String fileName = galleryDir + "take-photo0.jpg";
                File file = new File(fileName);
                if (!file.exists()) {
                    Log.e(TAG, "File does not exist: " + file.getAbsolutePath());
                    return;
                }

                String boundary = "*****";
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                OutputStream os = conn.getOutputStream();
                DataOutputStream dos = new DataOutputStream(os);
                dos.writeBytes("--" + boundary + "\r\n");
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n");
                dos.writeBytes("\r\n");

                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) > 0) {
                    dos.write(buffer, 0, bytesRead);
                }

                dos.writeBytes("\r\n--" + boundary + "--\r\n");
                fis.close();
                dos.flush();
                dos.close();

                // Read the response
                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Server Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    Log.d(TAG, "Server Response: " + response.toString());
                } else {
                    Log.e(TAG, "Server Error: " + responseCode);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        return "end";
    }

    private void deleteImages() {
        String galleryDir = "/storage/emulated/0/Pictures/";
        String extension = ".jpg"; // Customize the file name as needed
        int i = 0;
        while(i < 100) {
            String fileName = "take-photo";
            fileName += Integer.toString(i);
            File imageFile = new File(galleryDir, fileName + extension);
            if (imageFile.exists()) {
                if (imageFile.delete()) {
                    Log.d(TAG, "deleted: " + fileName);

                }
            }
            i += 1;
        }
        count = 0;
    }
}
