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
import android.view.Surface;
import android.net.Uri;
import android.content.Intent;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.Collections;

public class MainActivity extends Activity implements CameraSurfaceView.Listener {

    private static final int REQ_PERMISSION = 100;
    private static final int MAX_PREVIEW_WIDTH = 1280;
    private static final int MAX_PREVIEW_HEIGHT = 720;

    private CameraSurfaceView surfaceView;
    private Button btnMode;
    private Button btnPickColor;
    private android.view.View colorPreview;
    private SeekBar hueSlider;
    private TextView hueValue;
    private RangeSeekBar satRange;
    private TextView satValue;
    private RangeSeekBar valRange;
    private TextView valValue;
    private TextView pickHint;
    private PickOverlayView pickOverlay;
    private Button btnPickEyedropper;

    private CameraDevice camera;
    private CameraCaptureSession session;
    private Surface previewSurface;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private SurfaceTexture glSurfaceTexture;
    private boolean markMode = false;
    private boolean pickMode = false;
    private final float[] hsvTmp = new float[3];

    private int cameraGeneration = 0;
    private boolean cameraStarting = false;
    private boolean sessionConfigured = false;
    private int sessionRetryCount = 0;
    private static final int MAX_SESSION_RETRIES = 3;
    private final java.lang.Runnable sessionTimeout = new java.lang.Runnable() {
        @Override
        public void run() {
            if (!sessionConfigured && sessionRetryCount < MAX_SESSION_RETRIES) {
                sessionRetryCount++;
                AppLog.w("Session-Timeout, Neustart der Kamera (" + sessionRetryCount + "/" + MAX_SESSION_RETRIES + ")");
                closeCamera();
                startCamera();
            }
        }
    };

    private void scheduleSessionTimeout(int gen) {
        android.os.Handler main = new android.os.Handler(getMainLooper());
        main.postDelayed(sessionTimeout, 4000);
    }

    private void cancelSessionTimeout() {
        android.os.Handler main = new android.os.Handler(getMainLooper());
        main.removeCallbacks(sessionTimeout);
    }

    private float pickedR = 1f, pickedG = 0f, pickedB = 0f;

    private static final float[][] PRESET_COLORS = {
            {1f, 0f, 0f},
            {1f, 0.5f, 0f},
            {1f, 1f, 0f},
            {0f, 1f, 0f},
            {0f, 1f, 1f},
            {0f, 0f, 1f},
            {1f, 0f, 1f},
            {0.72f, 0.52f, 0.36f},
            {0.80f, 0.66f, 0.50f},
            {0.62f, 0.45f, 0.32f},
            {0.87f, 0.72f, 0.53f},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppLog.init(getApplication());
        AppLog.d("onCreate");
        surfaceView = findViewById(R.id.surface_view);
        btnMode = findViewById(R.id.btn_mode);
        btnPickColor = findViewById(R.id.btn_pick_color);
        colorPreview = findViewById(R.id.color_preview);
        ImageButton btnMenu = findViewById(R.id.btn_menu);
        btnMenu.setOnClickListener(v -> showMenu());

        hueSlider = findViewById(R.id.hue_slider);
        hueValue = findViewById(R.id.hue_value);
        satRange = findViewById(R.id.sat_range);
        satValue = findViewById(R.id.sat_value);
        valRange = findViewById(R.id.val_range);
        valValue = findViewById(R.id.val_value);
        pickHint = findViewById(R.id.pick_hint);
        pickOverlay = findViewById(R.id.pick_overlay);

        surfaceView.setListener(this);

        btnMode.setOnClickListener(v -> {
            markMode = !markMode;
            surfaceView.getRenderer().setMarkMode(markMode);
            updateControls();
        });

        btnPickColor.setOnClickListener(v -> showColorPicker());
        btnPickEyedropper = findViewById(R.id.btn_pick_eyedropper);
        btnPickEyedropper.setOnClickListener(v -> {
            pickMode = !pickMode;
            surfaceView.setPickMode(pickMode);
            surfaceView.getRenderer().setPickPreview(pickMode && markMode);
            if (pickMode) pickOverlay.show(-1f, -1f);
            pickOverlay.setVisibility(pickMode ? android.view.View.VISIBLE : android.view.View.GONE);
            updateControls();
            updatePickHint();
        });

        hueSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                hueValue.setText(progress + "\u00b0");
                surfaceView.getRenderer().setHueToleranceDeg(progress);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        satRange.setOnRangeChangeListener((min, max) -> {
            satValue.setText(fmtPct(min) + "-" + fmtPct(max) + "%");
            surfaceView.getRenderer().setSatRange(min, max);
        });

        valRange.setOnRangeChangeListener((min, max) -> {
            valValue.setText(fmtPct(min) + "-" + fmtPct(max) + "%");
            surfaceView.getRenderer().setValRange(min, max);
        });

        surfaceView.getRenderer().setHueToleranceDeg(hueSlider.getProgress());
        satRange.setValues(0f, 1f);
        valRange.setValues(0f, 1f);
        surfaceView.getRenderer().setSatRange(0f, 1f);
        surfaceView.getRenderer().setValRange(0f, 1f);

        updateControls();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_PERMISSION);
        }
    }

    private static String fmtPct(float v) {
        return String.valueOf(Math.round(v * 100f));
    }

    private void applyColor(float r, float g, float b) {
        pickedR = r; pickedG = g; pickedB = b;
        surfaceView.getRenderer().setTargetColor(r, g, b);
        colorPreview.setBackgroundColor(android.graphics.Color.rgb(
                Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)));
    }

    private void updatePickHint() {
        if (pickHint != null) {
            pickHint.setVisibility(pickMode ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void updateControls() {
        updatePickHint();
        btnMode.setText(markMode ? R.string.btn_mode_normal : R.string.btn_mode_mark);
        btnPickColor.setEnabled(markMode && !pickMode);
        btnPickEyedropper.setEnabled(markMode);
        btnPickEyedropper.setText(pickMode ? "Pipette aktiv" : "Pipette");
        hueSlider.setEnabled(markMode);
        satRange.setRangeEnabled(markMode);
        valRange.setRangeEnabled(markMode);
        surfaceView.getRenderer().setPickPreview(pickMode && markMode);
    }

    @Override
    public void onColorPicked(float r, float g, float b) {
        runOnUiThread(() -> {
            pickMode = false;
            surfaceView.setPickMode(false);
            pickOverlay.hide();
            pickOverlay.setVisibility(android.view.View.GONE);
            applyColor(r, g, b);
            updateControls();
        });
    }

    @Override
    public void onPickPointer(float x, float y) {
        runOnUiThread(() -> pickOverlay.show(x, y));
    }

    @Override
    public void onPickPointerGone() {
        runOnUiThread(() -> pickOverlay.show(-1f, -1f));
    }

    private void showMenu() {
        PopupMenu popup = new PopupMenu(this, findViewById(R.id.btn_menu));
        popup.getMenu().add("Uber");
        popup.getMenu().add("Spenden");
        popup.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if ("Uber".equals(title)) {
                startActivity(new Intent(this, SplashActivity.class));
                return true;
            }
            if ("Spenden".equals(title)) {
                showDonate();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showDonate() {
        new AlertDialog.Builder(this)
                .setTitle("Spenden")
                .setMessage("Unterstuetze die Entwicklung von TrailCam mit einer kleinen Spende uber PayPal.")
                .setPositiveButton("Mit PayPal spenden", (d, w) -> {
                    try {
                        String url = "https://www.paypal.com/donate/?business=timodamm%40googlemail.com&no_recurring=0&item_name=TrailCam%20Spende&currency_code=EUR";
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception e) {
                        AppLog.w("PayPal-Link konnte nicht geoeffnet werden");
                        showToast("PayPal konnte nicht geoeffnet werden");
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void showColorPicker() {
        String[] names = {"Rot", "Orange", "Gelb", "Gr\u00fcn", "Cyan", "Blau", "Magenta",
                "Hellbraun", "Beige", "Dunkelbraun", "Sepia"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_color)
                .setItems(names, (d, which) -> {
                    float[] c = PRESET_COLORS[which];
                    applyColor(c[0], c[1], c[2]);
                })
                .show();
    }

    @Override
    public void onSurfaceCreated(SurfaceTexture texture, int texId) {
        AppLog.d("onSurfaceCreated texId=" + texId);
        runOnUiThread(() -> {
            boolean textureChanged = (texture != glSurfaceTexture);
            glSurfaceTexture = texture;
            if (textureChanged) {
                AppLog.d("GL texture changed -> restart camera");
                closeCamera();
                startCamera();
            }
        });
    }

    @Override
    public void onFrameAvailable() {
        surfaceView.requestRender();
    }

    private void ensureBgThread() {
        if (bgThread == null || !bgThread.isAlive()) {
            bgThread = new HandlerThread("cam");
            bgThread.start();
            bgHandler = new Handler(bgThread.getLooper());
        }
    }

    private void closeCamera() {
        cancelSessionTimeout();
        AppLog.d("closeCamera gen=" + cameraGeneration);
        cameraGeneration++;
        cameraStarting = false;
        CameraCaptureSession s = session;
        session = null;
        if (s != null) {
            try { s.stopRepeating(); } catch (Exception ignored) { }
            try { s.close(); } catch (Exception ignored) { }
        }
        CameraDevice c = camera;
        camera = null;
        if (c != null) {
            try { c.close(); } catch (Exception ignored) { }
        }
        Surface ps = previewSurface;
        previewSurface = null;
        if (ps != null) {
            try { ps.release(); } catch (Exception ignored) { }
        }
    }

    @SuppressLint("MissingPermission")
    private void startCamera() {
        sessionRetryCount = 0;
        AppLog.d("startCamera gen=" + cameraGeneration
                + " starting=" + cameraStarting
                + " camera=" + (camera != null)
                + " session=" + (session != null)
                + " texture=" + (glSurfaceTexture != null));
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        synchronized (this) {
            if (cameraStarting || camera != null || session != null) {
                return;
            }
            cameraStarting = true;
        }
        ensureBgThread();

        final SurfaceTexture texture = glSurfaceTexture;
        if (texture == null) {
            cameraStarting = false;
            return;
        }

        final CameraManager mgr = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        final int gen = cameraGeneration;
        try {
            String cameraId = null;
            for (String id : mgr.getCameraIdList()) {
                CameraCharacteristics ch = mgr.getCameraCharacteristics(id);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null && mgr.getCameraIdList().length > 0) {
                cameraId = mgr.getCameraIdList()[0];
            }
            if (cameraId == null) {
                cameraStarting = false;
                AppLog.w("keine Kamera gefunden");
                showToast("Keine Kamera gefunden");
                return;
            }

            CameraCharacteristics ch = mgr.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size best = null;
            Size largest = null;
            if (map != null) {
                for (Size s : map.getOutputSizes(SurfaceTexture.class)) {
                    if (largest == null
                            || s.getWidth() * s.getHeight() > largest.getWidth() * largest.getHeight()) {
                        largest = s;
                    }
                    if (s.getWidth() <= MAX_PREVIEW_WIDTH
                            && s.getHeight() <= MAX_PREVIEW_HEIGHT
                            && (best == null
                                || s.getWidth() * s.getHeight() > best.getWidth() * best.getHeight())) {
                        best = s;
                    }
                }
            }
            if (best == null) {
                best = largest;
            }
            if (best == null) {
                cameraStarting = false;
                showToast("Keine Kamera-Aufl\u00f6sung verf\u00fcgbar");
                return;
            }
            AppLog.d("preview size=" + best);
            try {
                texture.setDefaultBufferSize(best.getWidth(), best.getHeight());
            } catch (Exception e) {
                AppLog.w("setDefaultBufferSize failed: " + e);
            }

            AppLog.d("openCamera id=" + cameraId);

            mgr.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cam) {
                    AppLog.d("camera onOpened gen=" + gen);
                    if (gen != cameraGeneration) {
                        try { cam.close(); } catch (Exception ignored) { }
                        return;
                    }
                    camera = cam;
                    try {
                        Surface surface = new Surface(texture);
                        previewSurface = surface;
                        sessionConfigured = false;
                        scheduleSessionTimeout(gen);
                        AppLog.d("createCaptureSession");
                        cam.createCaptureSession(Collections.singletonList(surface),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(@NonNull CameraCaptureSession sess) {
                                        AppLog.d("session onConfigured gen=" + gen);
                                        if (gen != cameraGeneration || camera == null) {
                                            try { sess.close(); } catch (Exception ignored) { }
                                            return;
                                        }
                                        session = sess;
                                        cameraStarting = false;
                                        Handler h = bgHandler;
                                        if (h == null) {
                                            return;
                                        }
                                        sessionConfigured = true;
                                        try {
                                            CaptureRequest.Builder builder =
                                                    cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                            builder.addTarget(surface);
                                            sess.setRepeatingRequest(builder.build(), null, h);
                                            AppLog.i("Preview laeuft");
                                        } catch (Exception e) {
                                            AppLog.e("setRepeatingRequest failed", e);
                                            closeCameraQuietly();
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(@NonNull CameraCaptureSession sess) {
                                        AppLog.e("onConfigureFailed gen=" + gen, null);
                                        if (gen == cameraGeneration) {
                                            cameraStarting = false;
                                            showToast("Kamera-Session fehlgeschlagen");
                                        }
                                        try { sess.close(); } catch (Exception ignored) { }
                                    }
                                }, bgHandler);
                    } catch (Exception e) {
                        AppLog.e("createCaptureSession failed", e);
                        cameraStarting = false;
                        try { cam.close(); } catch (Exception ignored) { }
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cam) {
                    AppLog.w("camera onDisconnected");
                    try { cam.close(); } catch (Exception ignored) { }
                    if (gen == cameraGeneration) {
                        camera = null;
                        session = null;
                        cameraStarting = false;
                    }
                }

                @Override
                public void onError(@NonNull CameraDevice cam, int error) {
                    AppLog.e("camera onError code=" + error, null);
                    try { cam.close(); } catch (Exception ignored) { }
                    if (gen == cameraGeneration) {
                        camera = null;
                        session = null;
                        cameraStarting = false;
                        showToast("Kameraproblem: " + error);
                    }
                }
            }, bgHandler);
        } catch (Exception e) {
            cameraStarting = false;
            AppLog.e("openCamera failed", e);
            showToast("Kamera konnte nicht ge\u00f6ffnet werden");
        }
    }

    private void closeCameraQuietly() {
        try {
            closeCamera();
        } catch (Exception ignored) {
        }
    }

    private void showToast(String msg) {
        AppLog.w("Toast: " + msg);
        runOnUiThread(() ->
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppLog.d("onResume");
        ensureBgThread();
        surfaceView.onResume();
        if (glSurfaceTexture != null) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        AppLog.d("onPause");
        closeCamera();
        surfaceView.onPause();
        if (bgThread != null) {
            bgThread.quitSafely();
            try { bgThread.join(500); } catch (InterruptedException ignored) { }
            bgThread = null;
            bgHandler = null;
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        AppLog.d("permission result granted="
                + (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED));
        if (requestCode == REQ_PERMISSION
                && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (requestCode == REQ_PERMISSION) {
            showToast("Kameraberechtigung ben\u00f6tigt");
        }
    }
}
