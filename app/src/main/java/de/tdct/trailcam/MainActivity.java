package de.tdct.trailcam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends Activity implements CameraSurfaceView.Listener {

    private static final int REQ_PERMISSION = 100;

    private CameraSurfaceView surfaceView;
    private Button btnMode;
    private Button btnPickColor;
    private android.view.View colorPreview;
    private SeekBar thresholdSlider;
    private TextView thresholdValue;

    private CameraDevice camera;
    private CameraCaptureSession session;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private SurfaceTexture glSurfaceTexture;
    private boolean markMode = false;

    private float pickedR = 1f, pickedG = 0f, pickedB = 0f;

    private static final float[][] PRESET_COLORS = {
            {1f, 0f, 0f},
            {1f, 0.5f, 0f},
            {1f, 1f, 0f},
            {0f, 1f, 0f},
            {0f, 1f, 1f},
            {0f, 0f, 1f},
            {1f, 0f, 1f},
            {1f, 1f, 1f},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surface_view);
        btnMode = findViewById(R.id.btn_mode);
        btnPickColor = findViewById(R.id.btn_pick_color);
        colorPreview = findViewById(R.id.color_preview);
        thresholdSlider = findViewById(R.id.threshold_slider);
        thresholdValue = findViewById(R.id.threshold_value);

        surfaceView.setListener(this);

        btnMode.setOnClickListener(v -> {
            markMode = !markMode;
            surfaceView.getRenderer().setMarkMode(markMode);
            updateControls();
        });

        btnPickColor.setOnClickListener(v -> showColorPicker());

        thresholdSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float t = progress / 100f;
                thresholdValue.setText(String.valueOf(progress));
                surfaceView.getRenderer().setThreshold(t);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        updateControls();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_PERMISSION);
        }
    }

    private void updateControls() {
        btnMode.setText(markMode ? R.string.btn_mode_normal : R.string.btn_mode_mark);
        btnPickColor.setEnabled(markMode);
        thresholdSlider.setEnabled(markMode);
        surfaceView.setPinchEnabled(true);
    }

    private void showColorPicker() {
        String[] names = {"Rot", "Orange", "Gelb", "Grün", "Cyan", "Blau", "Magenta", "Weiß"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_color)
                .setItems(names, (d, which) -> {
                    float[] c = PRESET_COLORS[which];
                    pickedR = c[0]; pickedG = c[1]; pickedB = c[2];
                    surfaceView.getRenderer().setTargetColor(pickedR, pickedG, pickedB);
                    int color = android.graphics.Color.rgb(
                            (int) (pickedR * 255), (int) (pickedG * 255), (int) (pickedB * 255));
                    colorPreview.setBackgroundColor(color);
                })
                .show();
    }

    @Override
    public void onSurfaceCreated(SurfaceTexture texture, int texId) {
        glSurfaceTexture = texture;
        startCamera(texture);
    }

    @Override
    public void onFrameAvailable() {
        surfaceView.requestRender();
    }

    @SuppressLint("MissingPermission")
    private void startCamera(SurfaceTexture texture) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        CameraManager mgr = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = null;
            for (String id : mgr.getCameraIdList()) {
                CameraCharacteristics ch = mgr.getCameraCharacteristics(id);
                if (ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null && mgr.getCameraIdList().length > 0) {
                cameraId = mgr.getCameraIdList()[0];
            }
            if (cameraId == null) {
                Toast.makeText(this, "Keine Kamera gefunden", Toast.LENGTH_LONG).show();
                return;
            }

            CameraCharacteristics ch = mgr.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
            Size best = sizes[0];
            for (Size s : sizes) {
                if (s.getWidth() * s.getHeight() > best.getWidth() * best.getHeight()) {
                    best = s;
                }
            }
            texture.setDefaultBufferSize(best.getWidth(), best.getHeight());

            final String id = cameraId;
            Range<Integer>[] fpsRanges = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            Range<Integer> fps = null;
            if (fpsRanges != null && fpsRanges.length > 0) {
                fps = fpsRanges[0];
                for (Range<Integer> r : fpsRanges) {
                    if (r.getUpper() > fps.getUpper()) fps = r;
                }
            }

            final Range<Integer> finalFps = fps;
            mgr.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    MainActivity.this.camera = camera;
                    try {
                        Surface surface = new Surface(texture);
                        camera.createCaptureSession(Collections.singletonList(surface),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(@NonNull CameraCaptureSession session) {
                                        MainActivity.this.session = session;
                                        try {
                                            CaptureRequest.Builder builder =
                                                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                            builder.addTarget(surface);
                                            if (finalFps != null) {
                                                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, finalFps);
                                            }
                                            session.setRepeatingRequest(builder.build(), null, bgHandler);
                                        } catch (CameraAccessException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                        Toast.makeText(MainActivity.this, "Kamera-Session fehlgeschlagen",
                                                Toast.LENGTH_LONG).show();
                                    }
                                }, bgHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Toast.makeText(MainActivity.this, "Kameraproblem: " + error, Toast.LENGTH_LONG).show();
                    camera.close();
                }
            }, bgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bgThread = new HandlerThread("cam");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
        surfaceView.onResume();
        if (glSurfaceTexture != null
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                && session == null) {
            startCamera(glSurfaceTexture);
        }
    }

    @Override
    protected void onPause() {
        if (session != null) {
            try { session.stopRepeating(); } catch (CameraAccessException ignored) { }
            session.close();
            session = null;
        }
        if (camera != null) {
            camera.close();
            camera = null;
        }
        surfaceView.onPause();
        if (bgThread != null) {
            bgThread.quitSafely();
            try { bgThread.join(); } catch (InterruptedException ignored) { }
            bgThread = null;
            bgHandler = null;
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQ_PERMISSION
                && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && glSurfaceTexture != null) {
            startCamera(glSurfaceTexture);
        } else if (requestCode == REQ_PERMISSION) {
            Toast.makeText(this, "Kameraberechtigung benötigt", Toast.LENGTH_LONG).show();
        }
    }
}
