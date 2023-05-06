package com.authenticator.otp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.Manifest;

import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ScannedBarcodeActivity extends AppCompatActivity {
    SurfaceView surfaceView;
    TextView textBarcodeValue;
    private BarcodeDetector barcodeDetector;
    private CameraSource cameraSource;
    private static final int REQUEST_CAMERA_PERMISSION = 201;
    Button btnAction;
    String intentData = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_barcode);
        initViews();
    }

    public void initViews() {
        textBarcodeValue = findViewById(R.id.txtBarcodeValue);
        surfaceView = findViewById(R.id.surfaceView);
        btnAction = findViewById(R.id.btnAction);

        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(intentData.length() > 0) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(intentData)));
                }
            }
        });
    }

    public void initialiseDetectorsAndSources() {
        Toast.makeText(getApplicationContext(), "Scanning started", Toast.LENGTH_SHORT).show();

        barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.ALL_FORMATS)
                .build();
        cameraSource = new CameraSource.Builder(this, barcodeDetector)
                .setRequestedPreviewSize(1920, 1080)
                .setAutoFocusEnabled(true)
                .build();

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                try {
                    if (ActivityCompat.checkSelfPermission(ScannedBarcodeActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraSource.start(surfaceView.getHolder());
                    } else {
                        ActivityCompat.requestPermissions(ScannedBarcodeActivity.this, new
                                String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
                cameraSource.stop();
            }
        });

        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {
                Toast.makeText(getApplicationContext(), "To prevent memory leaks barcode scanner has been stopped", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {
                final SparseArray<Barcode> barcodes = detections.getDetectedItems();
                if (barcodes.size() != 0) {
                    textBarcodeValue.post(new Runnable() {
                        @Override
                        public void run() {
                            btnAction.setText("LAUNCH URL");
                            intentData = barcodes.valueAt(0).displayValue;
                            // otpauth://totp/?secret=&algorithm=SHA1&digits=6&period=30
                            Uri uri = Uri.parse(intentData);
                            String key = uri.getQueryParameter("secret");
                            // TODO: use all query params from uri as variables with error checking
//                            int period = Integer.parseInt(uri.getQueryParameter("period"));
                            long counter = 0;
                            int period = 30;
                            long currentUnixTime;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                currentUnixTime = Instant.now().getEpochSecond();
                                counter = currentUnixTime/period;
                            }
                            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                            buffer.putLong(counter);
                            byte[] counterInBytes = buffer.array();
                            String algorithm = "HmacSHA1";
                            try {
                                byte[] keyBytes = key.getBytes();
                                if (keyBytes.length > 0) {
                                    SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, algorithm);
                                    Mac mac = Mac.getInstance(algorithm);
                                    mac.init(secretKeySpec);
                                    byte[] bytes = mac.doFinal(counterInBytes);
                                    String base64HmacSha1 = null;
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        base64HmacSha1 = Base64.getEncoder().encodeToString(bytes);
                                    }
                                    int offset = bytes[19] & 0xf;
                                    int truncatedHash = (bytes[offset] & 0x7f) << 24 | (bytes[offset + 1] & 0xff) << 16 | (bytes[offset + 2] & 0xff) << 8 | (bytes[offset + 3] & 0xff);
                                    int finalOTP = (int) (truncatedHash % (Math.pow(10,6)));
                                    textBarcodeValue.setText(Integer.toString(finalOTP));
                                }

                            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                                throw new RuntimeException(e);
                            }

                        }
                    });
                }
            }
        });
    }
public String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();

    for (byte b : bytes)
    {
        sb.append(String.format("%02X ", b));
    }
    return sb.toString();
}
    @Override
    protected void onPause() {
        super.onPause();
        cameraSource.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initialiseDetectorsAndSources();
    }
}
