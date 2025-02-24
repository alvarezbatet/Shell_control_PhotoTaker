package com.example.photo_to_server;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
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

import android.os.PersistableBundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    String SERVER_IP;
    String SERVER_PORT;
    TextView text_view;

    private static String SERVER_URL; //your server address

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
            }
        });
        Button myButton2 = findViewById(R.id.Delete_Photos);
        myButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
        text_view = findViewById(R.id.text_view);

        Button connectButton = findViewById(R.id.connectButton);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(() -> {
                    try {
                        EditText ip = findViewById(R.id.ip);
                        Editable editableText = ip.getText();
                        SERVER_IP = editableText.toString();

                        EditText port = findViewById(R.id.port);
                        editableText = port.getText();
                        SERVER_PORT = editableText.toString();

                        Intent serviceIntent = new Intent(MainActivity.this, backgroundService.class);
                        serviceIntent.putExtra("key_IP", SERVER_IP);
                        serviceIntent.putExtra("key_PORT", SERVER_PORT);
                        startForegroundService(serviceIntent); // Start the background service
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

            }
        });
    }
}
