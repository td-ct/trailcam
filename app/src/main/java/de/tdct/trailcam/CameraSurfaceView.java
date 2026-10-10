package de.tdct.trailcam;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraSurfaceView extends GLSurfaceView implements ScaleGestureDetector.OnScaleGestureListener {

    public interface Listener {
        void onSurfaceCreated(SurfaceTexture texture, int texId);
        void onFrameAvailable();
        default void onZoomChanged(float zoom) { }
        void onColorPicked(float r, float g, float b);
        default void onPickPointer(float x, float y) { }
        default void onPickPointerGone() { }
        default void onPickPreviewColor(float r, float g, float b) { }
    }

    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 5.0f;

    private Listener listener;
    private ScaleGestureDetector scaleDetector;
    private float zoom = 1.0f;
    private float minZoom = MIN_ZOOM;
    private float maxZoom = MAX_ZOOM;
    private CameraRenderer renderer;

    private volatile boolean pickMode = false;
    private final AtomicReference<float[]> pendingPick = new AtomicReference<>();
    private boolean multiTouch = false;

    public CameraSurfaceView(Context context) {
        super(context);
        init(context);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        renderer = new CameraRenderer(this);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        scaleDetector = new ScaleGestureDetector(context, this);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPickMode(boolean enabled) {
        this.pickMode = enabled;
    }

    public void setZoomRange(float min, float max) {
        minZoom = Math.min(min, 1f);
        maxZoom = Math.max(max, 1f);
        zoom = Math.max(minZoom, Math.min(maxZoom, zoom));
        renderer.setZoom(Math.max(1f, zoom));
    }

    public float getZoom() {
        return zoom;
    }

    void fireSurfaceCreated(SurfaceTexture texture, int texId) {
        if (listener != null) listener.onSurfaceCreated(texture, texId);
    }

    void fireFrameAvailable() {
        if (listener != null) listener.onFrameAvailable();
    }

    void fireColorPicked(float r, float g, float b) {
        if (listener != null) listener.onColorPicked(r, g, b);
    }

    void firePickPointer(float x, float y) {
        if (listener != null) listener.onPickPointer(x, y);
    }

    void firePickPointerGone() {
        if (listener != null) listener.onPickPointerGone();
    }

    void firePickPreviewColor(float r, float g, float b) {
        if (listener != null) listener.onPickPreviewColor(r, g, b);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() >= 2) {
            multiTouch = true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            multiTouch = false;
        }
        scaleDetector.onTouchEvent(event);
        if (pickMode && !multiTouch) {
            float offsetY = 0.10f * getHeight();
            float pickY = Math.max(0f, Math.min(getHeight() - 1f, event.getY() - offsetY));
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    firePickPointer(event.getX(), pickY);
                    requestPick(event.getX(), pickY, false);
                    break;
                case MotionEvent.ACTION_MOVE:
                    firePickPointer(event.getX(), pickY);
                    requestPick(event.getX(), pickY, false);
                    break;
                case MotionEvent.ACTION_UP:
                    firePickPointerGone();
                    requestPick(event.getX(), pickY, true);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    firePickPointerGone();
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    private void requestPick(float x, float y, boolean isFinal) {
        pendingPick.set(new float[]{
                x / Math.max(1f, getWidth()),
                y / Math.max(1f, getHeight()),
                isFinal ? 1f : 0f});
        queueEvent(() -> renderer.requestPick(pendingPick));
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        zoom *= detector.getScaleFactor();
        if (zoom < minZoom) zoom = minZoom;
        if (zoom > maxZoom) zoom = maxZoom;
        renderer.setZoom(1.0f);
        if (listener != null) listener.onZoomChanged(zoom);
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
    }

    public CameraRenderer getRenderer() {
        return renderer;
    }

    public static class CameraRenderer implements GLSurfaceView.Renderer {

        private static final String VERTEX_SHADER =
                "uniform mat4 uMVP;\n" +
                "uniform mat4 uSTMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTexCoord;\n" +
                "varying vec2 vTexCoord;\n" +
                "void main() {\n" +
                "  gl_Position = uMVP * aPosition;\n" +
                "  vTexCoord = (uSTMatrix * aTexCoord).xy;\n" +
                "}\n";

        private static final String FRAGMENT_SHADER_NORMAL =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vTexCoord;\n" +
                "uniform samplerExternalOES uTexture;\n" +
                "uniform float uZoom;\n" +
                "uniform float uAspect;\n" +
                "void main() {\n" +
                "  vec2 c = vTexCoord - vec2(0.5);\n" +
                "  c = c / uZoom;\n" +
                "  c.x *= min(uAspect, 1.0);\n" +
                "  c.y *= 1.0 / max(uAspect, 1.0);\n" +
                "  vec2 tc = c + vec2(0.5);\n" +
                "  if (tc.x < 0.0 || tc.x > 1.0 || tc.y < 0.0 || tc.y > 1.0) {\n" +
                "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
                "    return;\n" +
                "  }\n" +
                "  gl_FragColor = texture2D(uTexture, tc);\n" +
                "}\n";

        private static final String FRAGMENT_SHADER_MARK =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vTexCoord;\n" +
                "uniform samplerExternalOES uTexture;\n" +
                "uniform float uZoom;\n" +
                "uniform float uAspect;\n" +
                "uniform vec3 uTargetHSV;\n" +
                "uniform float uHueTol;\n" +
                "uniform float uSatMin;\n" +
                "uniform float uSatMax;\n" +
                "uniform float uValMin;\n" +
                "uniform float uValMax;\n" +
                "uniform float uTime;\n" +
                "uniform float uBrightMode;\n" +
                "uniform float uInvert;\n" +
                "\n" +
                "vec3 hsv2rgb(vec3 c) {\n" +
                "  vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);\n" +
                "  vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);\n" +
                "  return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);\n" +
                "}\n" +
                "\n" +
                "vec3 rgb2hsv(vec3 c) {\n" +
                "  vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);\n" +
                "  vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));\n" +
                "  vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));\n" +
                "  float d = q.x - min(q.w, q.y);\n" +
                "  float e = 1.0e-10;\n" +
                "  return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);\n" +
                "}\n" +
                "\n" +
                "void main() {\n" +
                "  vec2 c = vTexCoord - vec2(0.5);\n" +
                "  c = c / uZoom;\n" +
                "  c.x *= min(uAspect, 1.0);\n" +
                "  c.y *= 1.0 / max(uAspect, 1.0);\n" +
                "  vec2 tc = c + vec2(0.5);\n" +
                "  if (tc.x < 0.0 || tc.x > 1.0 || tc.y < 0.0 || tc.y > 1.0) {\n" +
                "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
                "    return;\n" +
                "  }\n" +
                "  vec3 col = texture2D(uTexture, tc).rgb;\n" +
                "  vec3 hsv = rgb2hsv(col);\n" +
                "  float hueDiff = abs(hsv.x - uTargetHSV.x);\n" +
                "  if (hueDiff > 0.5) hueDiff = 1.0 - hueDiff;\n" +
                "  float match = step(hueDiff, uHueTol)\n" +
                "      * step(uSatMin, hsv.y) * step(hsv.y, uSatMax)\n" +
                "      * step(uValMin, hsv.z) * step(hsv.z, uValMax);\n" +
                "  match = mix(match, 1.0 - match, uInvert);\n" +
                "  vec3 faded = mix(vec3(dot(col, vec3(0.299, 0.587, 0.114))), col, 0.15);\n" +
                "  vec3 sepia = vec3(1.20, 1.05, 0.80);\n" +
                "  faded = faded * sepia;\n" +
                "  faded = clamp(faded * 0.85 + 0.30, 0.0, 1.0);\n" +
                "  faded = mix(faded * 0.35 + vec3(0.02), faded, uBrightMode);\n" +
                "  vec3 neonHue = mix(vec3(uTargetHSV.x), vec3(hsv.x), uInvert);\n" +
                "  vec3 neon = hsv2rgb(vec3(neonHue.x, 1.0, 1.0));\n" +
                "  neon = mix(neon, vec3(1.0), 0.15);\n" +
                "  float pulse = 0.75 + 0.25 * sin(uTime * 2.0 * 3.14159265 * 4.0);\n" +
                "  neon = neon * pulse;\n" +
                "  float edge = smoothstep(uHueTol, uHueTol * 0.35, hueDiff)\n" +
                "      * smoothstep(uSatMin, uSatMin + 0.08, hsv.y)\n" +
                "      * smoothstep(uValMin, uValMin + 0.08, hsv.z)\n" +
                "      * (1.0 - smoothstep(uValMax - 0.08, uValMax, hsv.z));\n" +
                "  edge = mix(edge, (1.0 - edge) * step(hueDiff, uHueTol * 2.0), uInvert);\n" +
                "  vec3 core = mix(neon * 1.6, vec3(1.0), 0.55);\n" +
                "  vec3 glowCol = mix(neon, vec3(1.0), 0.25) * 1.15;\n" +
                "  vec3 halo = neon * 0.65;\n" +
                "  vec3 result = mix(faded, halo, clamp(edge * 0.45, 0.0, 1.0));\n" +
                "  result = mix(result, glowCol, clamp(edge - 0.35, 0.0, 1.0));\n" +
                "  result = mix(result, core, match);\n" +
                "  gl_FragColor = vec4(clamp(result, 0.0, 1.0), 1.0);\n" +
                "}\n";

        private final CameraSurfaceView view;
        private int programNormal;
        private int programMark;
        private int aPosLoc, aTexLoc;
        private int mvpLocN, stLocN, zoomLocN, aspectLocN;
        private int mvpLocM, stLocM, zoomLocM, aspectLocM, hsvLocM, tolLocM;
        private int satMinLocM, satMaxLocM, valMinLocM, valMaxLocM;
        private int timeLocM, brightLocM, invertLocM;
        private int texId;
        private SurfaceTexture surfaceTexture;
        private float[] mvp = new float[16];
        private float[] stMatrix = new float[16];
        private final FloatBuffer vertexBuffer;

        private volatile boolean markMode = false;
        private volatile boolean pickPreview = false;
        private volatile float zoom = 1.0f;
        private volatile float aspect = 1.0f;

        public void setAspect(float a) {
            aspect = Math.max(0.1f, Math.min(10f, a));
        }
        private final float[] targetHsv = {0f, 1f, 1f};
        private volatile float hueTol = 0.083f;
        private volatile float satMin = 0f, satMax = 1f;
        private volatile float valMin = 0f, valMax = 1f;
        private volatile float brightMode = 1f;
        private volatile boolean invertMode = false;

        public void setInvertMode(boolean i) {
            invertMode = i;
        }
        private long startTime = android.os.SystemClock.elapsedRealtime();
        private AtomicReference<float[]> pickRequest;
        private int surfaceWidth = 1;
        private int surfaceHeight = 1;

        CameraRenderer(CameraSurfaceView view) {
            this.view = view;
            float[] verts = new float[]{
                    -1f, -1f, 0f, 0f, 0f,
                    1f, -1f, 0f, 1f, 0f,
                    -1f, 1f, 0f, 0f, 1f,
                    1f, 1f, 0f, 1f, 1f,
            };
            ByteBuffer bb = ByteBuffer.allocateDirect(verts.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(verts);
            vertexBuffer.position(0);
        }

        public void setZoom(float z) {
            zoom = Math.max(1f, Math.min(5f, z));
        }

        public void setMarkMode(boolean m) {
            markMode = m;
        }

        public void setPickPreview(boolean p) {
            pickPreview = p;
        }

        public void setTargetColor(float r, float g, float b) {
            int color = android.graphics.Color.rgb(
                    Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(color, hsv);
            synchronized (targetHsv) {
                targetHsv[0] = hsv[0] / 360f;
                targetHsv[1] = hsv[1];
                targetHsv[2] = hsv[2];
            }
        }

        public void setHueToleranceDeg(float deg) {
            hueTol = Math.max(0f, Math.min(180f, deg)) / 360f;
        }

        public void setSatRange(float min01, float max01) {
            satMin = clamp01(min01);
            satMax = clamp01(max01);
        }

        public void setBrightMode(float b) {
            brightMode = Math.max(0f, Math.min(1f, b));
        }

        public void setValRange(float min01, float max01) {
            valMin = clamp01(min01);
            valMax = clamp01(max01);
        }

        private static float clamp01(float v) {
            return Math.max(0f, Math.min(1f, v));
        }

        public void requestPick(AtomicReference<float[]> request) {
            pickRequest = request;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            int extTex[] = new int[1];
            GLES20.glGenTextures(1, extTex, 0);
            texId = extTex[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            surfaceTexture = new SurfaceTexture(texId);
            surfaceTexture.setOnFrameAvailableListener(st -> view.fireFrameAvailable());

            programNormal = buildProgramSafe(VERTEX_SHADER, FRAGMENT_SHADER_NORMAL);
            programMark = buildProgramSafe(VERTEX_SHADER, FRAGMENT_SHADER_MARK);
            if (programMark == 0) {
                AppLog.e("Markierungs-Shader ungueltig, falle auf Normal-Modus zurueck", null);
                programMark = programNormal;
            }

            mvpLocN = GLES20.glGetUniformLocation(programNormal, "uMVP");
            stLocN = GLES20.glGetUniformLocation(programNormal, "uSTMatrix");
            zoomLocN = GLES20.glGetUniformLocation(programNormal, "uZoom");
            aspectLocN = GLES20.glGetUniformLocation(programNormal, "uAspect");

            mvpLocM = GLES20.glGetUniformLocation(programMark, "uMVP");
            stLocM = GLES20.glGetUniformLocation(programMark, "uSTMatrix");
            zoomLocM = GLES20.glGetUniformLocation(programMark, "uZoom");
            aspectLocM = GLES20.glGetUniformLocation(programMark, "uAspect");
            hsvLocM = GLES20.glGetUniformLocation(programMark, "uTargetHSV");
            tolLocM = GLES20.glGetUniformLocation(programMark, "uHueTol");
            satMinLocM = GLES20.glGetUniformLocation(programMark, "uSatMin");
            satMaxLocM = GLES20.glGetUniformLocation(programMark, "uSatMax");
            valMinLocM = GLES20.glGetUniformLocation(programMark, "uValMin");
            valMaxLocM = GLES20.glGetUniformLocation(programMark, "uValMax");
            timeLocM = GLES20.glGetUniformLocation(programMark, "uTime");
            brightLocM = GLES20.glGetUniformLocation(programMark, "uBrightMode");
            invertLocM = GLES20.glGetUniformLocation(programMark, "uInvert");

            view.fireSurfaceCreated(surfaceTexture, texId);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            surfaceWidth = Math.max(1, width);
            surfaceHeight = Math.max(1, height);
            float aspect = (float) width / Math.max(1, height);
            android.opengl.Matrix.orthoM(mvp, 0, -aspect, aspect, -1, 1, -1, 1);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (surfaceTexture == null) return;
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(stMatrix);

            draw(markMode && !pickPreview);

            AtomicReference<float[]> req = pickRequest;
            if (req != null) {
                float[] pos = req.getAndSet(null);
                pickRequest = null;
                if (pos != null) {
                    int px = Math.min(surfaceWidth - 1, Math.max(0, (int) (pos[0] * surfaceWidth)));
                    int pyFlipped = Math.min(surfaceHeight - 1,
                            Math.max(0, (int) (pos[1] * surfaceHeight)));
                    int py = surfaceHeight - 1 - pyFlipped;
                    int rw = 5, rh = 5;
                    int x0 = Math.max(0, Math.min(surfaceWidth - rw, px - rw / 2));
                    int y0 = Math.max(0, Math.min(surfaceHeight - rh, py - rh / 2));
                    ByteBuffer pb = ByteBuffer.allocateDirect(rw * rh * 4);
                    pb.order(ByteOrder.nativeOrder());
                    GLES20.glReadPixels(x0, y0, rw, rh,
                            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pb);
                    int n = rw * rh;
                    double sinSum = 0, cosSum = 0, satSum = 0, valSum = 0;
                    float[] hsvPx = new float[3];
                    for (int i = 0; i < n; i++) {
                        int r8 = pb.get(i * 4) & 0xFF;
                        int g8 = pb.get(i * 4 + 1) & 0xFF;
                        int b8 = pb.get(i * 4 + 2) & 0xFF;
                        android.graphics.Color.colorToHSV(
                                android.graphics.Color.rgb(r8, g8, b8), hsvPx);
                        double ang = hsvPx[0] / 360f * 2f * Math.PI;
                        sinSum += Math.sin(ang);
                        cosSum += Math.cos(ang);
                        satSum += hsvPx[1];
                        valSum += hsvPx[2];
                    }
                    double meanAng = Math.atan2(sinSum / n, cosSum / n);
                    if (meanAng < 0) meanAng += 2f * Math.PI;
                    float meanHue = (float) (meanAng / (2f * Math.PI) * 360f);
                    float meanSat = (float) (satSum / n);
                    float meanVal = (float) (valSum / n);
                    int picked = android.graphics.Color.HSVToColor(
                            new float[]{meanHue, meanSat, meanVal});
                    float pr = android.graphics.Color.red(picked) / 255f;
                    float pg = android.graphics.Color.green(picked) / 255f;
                    float pb2 = android.graphics.Color.blue(picked) / 255f;
                    if (pos.length > 2 && pos[2] > 0.5f) {
                        view.fireColorPicked(pr, pg, pb2);
                    } else {
                        view.firePickPreviewColor(pr, pg, pb2);
                    }
                }
            }
        }

        private void draw(boolean mark) {
            int program = mark ? programMark : programNormal;
            GLES20.glUseProgram(program);
            aPosLoc = GLES20.glGetAttribLocation(program, "aPosition");
            aTexLoc = GLES20.glGetAttribLocation(program, "aTexCoord");
            vertexBuffer.position(0);
            GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 20, vertexBuffer);
            GLES20.glEnableVertexAttribArray(aPosLoc);
            vertexBuffer.position(3);
            GLES20.glVertexAttribPointer(aTexLoc, 3, GLES20.GL_FLOAT, false, 20, vertexBuffer);
            GLES20.glEnableVertexAttribArray(aTexLoc);
            vertexBuffer.position(0);

            GLES20.glUniformMatrix4fv(mark ? mvpLocM : mvpLocN, 1, false, mvp, 0);
            GLES20.glUniformMatrix4fv(mark ? stLocM : stLocN, 1, false, stMatrix, 0);
            GLES20.glUniform1f(mark ? zoomLocM : zoomLocN, zoom);
            GLES20.glUniform1f(mark ? aspectLocM : aspectLocN, aspect);
            if (mark) {
                float[] hsv;
                synchronized (targetHsv) {
                    hsv = new float[]{targetHsv[0], targetHsv[1], targetHsv[2]};
                }
                GLES20.glUniform3f(hsvLocM, hsv[0], hsv[1], hsv[2]);
                GLES20.glUniform1f(tolLocM, hueTol);
                GLES20.glUniform1f(satMinLocM, satMin);
                GLES20.glUniform1f(satMaxLocM, satMax);
                GLES20.glUniform1f(valMinLocM, valMin);
                GLES20.glUniform1f(valMaxLocM, valMax);
                GLES20.glUniform1f(timeLocM, (android.os.SystemClock.elapsedRealtime() - startTime) / 1000f);
                GLES20.glUniform1f(brightLocM, brightMode);
                GLES20.glUniform1f(invertLocM, invertMode ? 1f : 0f);
            }

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(aPosLoc);
            GLES20.glDisableVertexAttribArray(aTexLoc);
        }

        private static int buildProgramSafe(String vsSrc, String fsSrc) {
            try {
                return buildProgram(vsSrc, fsSrc);
            } catch (Throwable t) {
                AppLog.e("Shader-Build fehlgeschlagen: " + t.getMessage(), t);
                return 0;
            }
        }

        private static int buildProgram(String vsSrc, String fsSrc) {
            int vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc);
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc);
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, vs);
            GLES20.glAttachShader(p, fs);
            GLES20.glLinkProgram(p);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                throw new RuntimeException("link error: " + GLES20.glGetProgramInfoLog(p));
            }
            return p;
        }

        private static int compile(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                throw new RuntimeException("compile error: " + GLES20.glGetShaderInfoLog(s));
            }
            return s;
        }
    }
}
