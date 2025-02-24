package com.example.photo_to_server;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;

import android.hardware.camera2.*;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.OutputConfiguration;

import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;

import android.os.Bundle;
import android.os.Environment;

import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.CharArrayWriter;
import java.io.DataOutputStream;
import java.io.File;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.widget.RelativeLayout;
import android.widget.TextView;

import android.widget.EditText;
import android.text.Editable;

import java.io.BufferedReader;
import java.io.FileInputStream;

import java.net.HttpURLConnection;

import java.net.URL;
import java.util.Objects;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    String TAG  = "MMMMMMMMMMMMM";

    private int count = 0;
    ClientThread clientThread;
    String SERVER_IP;
    int SERVER_PORT;
    TextView text_view;
    RelativeLayout layout;

    private static String SERVER_URL; //your server address

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE}, 1);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 1);
        }

        Button myButton = findViewById(R.id.TakePhoto);
        myButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });
        Button myButton2 = findViewById(R.id.Delete_Photos);
        myButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteImages();
            }
        });
        text_view = findViewById(R.id.text_view);

        Button connectButton = findViewById(R.id.connectButton);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(() -> {
                    try {
                        getMessagesFromServer();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        });

        Button sendButton = findViewById(R.id.sendButton);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                new Thread(() -> {
                    try {
                        String msg = uploadFile();
                        Log.d(TAG, msg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        });
    }
    private void getMessagesFromServer() {
        try {
            while (true) {
                // Send GET request to receive the server message
                EditText ip = findViewById(R.id.ip);
                Editable editableText = ip.getText();
                SERVER_IP = editableText.toString();

                EditText port = findViewById(R.id.port);
                editableText = port.getText();
                SERVER_PORT = Integer.parseInt(editableText.toString());
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
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            openCamera();
                        }
                    });
                } else if (Objects.equals(response, "3")) {
                    Log.d(TAG, "message from server == 3");
                    new Thread(() -> {
                        deleteImages();
                    }).start();
                    // Wait 2 seconds before fetching again
                    Thread.sleep(2000);
                }
            }
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
        // Upload image to server
    private String uploadFile() {
        new Thread(() -> {
            try {
                EditText ip = findViewById(R.id.ip);
                EditText port = findViewById(R.id.port);
                SERVER_IP = ip.getText().toString();
                SERVER_PORT = Integer.parseInt(port.getText().toString());

                if (SERVER_IP.isEmpty() || SERVER_PORT <= 0) {
                    Log.e(TAG, "Invalid IP or Port: " + SERVER_IP + ":" + SERVER_PORT);
                    return;
                }

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

    @NonNull
    public Size getResolution(@NonNull final CameraManager cameraManager, @NonNull final String cameraId) throws CameraAccessException
    {
        final CameraCharacteristics  characteristics = cameraManager.getCameraCharacteristics(cameraId);
        final StreamConfigurationMap map             = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null)
        {
            throw new IllegalStateException("Failed to get configuration map.");
        }

        final Size[] choices = map.getOutputSizes(ImageFormat.JPEG);

        Arrays.sort(choices, Collections.reverseOrder(new Comparator<Size>()
        {
            @Override
            public int compare(@NonNull final Size lhs, @NonNull final Size rhs)
            {
                // Cast to ensure the multiplications won't overflow
                return Long.signum((lhs.getWidth() * (long)lhs.getHeight()) - (rhs.getWidth() * (long)rhs.getHeight()));
            }
        }));

        return choices[0];
    }
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

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            }
            cameraManager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void createCaptureSession() throws CameraAccessException {
        List<OutputConfiguration> outputConfig = Collections.singletonList(new OutputConfiguration(imageReader.getSurface()));
        cameraDevice.createCaptureSessionByOutputConfigurations(outputConfig, captureSessionCallback, null);

    }
}
