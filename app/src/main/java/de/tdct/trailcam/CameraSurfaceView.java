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
        void onColorPicked(float r, float g, float b);
    }

    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 5.0f;

    private Listener listener;
    private ScaleGestureDetector scaleDetector;
    private float zoom = 1.0f;
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() >= 2) {
            multiTouch = true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            multiTouch = false;
        }
        scaleDetector.onTouchEvent(event);
        if (pickMode
                && !multiTouch
                && event.getActionMasked() == MotionEvent.ACTION_UP
                && pendingPick.get() == null) {
            float xNorm = event.getX() / Math.max(1f, getWidth());
            float yNorm = event.getY() / Math.max(1f, getHeight());
            pendingPick.set(new float[]{xNorm, yNorm});
            queueEvent(() -> renderer.requestPick(pendingPick));
        }
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        zoom *= detector.getScaleFactor();
        if (zoom < MIN_ZOOM) zoom = MIN_ZOOM;
        if (zoom > MAX_ZOOM) zoom = MAX_ZOOM;
        renderer.setZoom(zoom);
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
                "void main() {\n" +
                "  vec2 c = vTexCoord - vec2(0.5);\n" +
                "  c = c / uZoom;\n" +
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
                "uniform vec3 uTargetColor;\n" +
                "uniform float uHueTol;\n" +
                "\n" +
                "vec3 rgb2hsv(vec3 c) {\n" +
                "  vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);\n" +
                "  vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);\n" +
                "  return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);\n" +
                "}\n" +
                "\n" +
                "void main() {\n" +
                "  vec2 c = vTexCoord - vec2(0.5);\n" +
                "  c = c / uZoom;\n" +
                "  vec2 tc = c + vec2(0.5);\n" +
                "  if (tc.x < 0.0 || tc.x > 1.0 || tc.y < 0.0 || tc.y > 1.0) {\n" +
                "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
                "    return;\n" +
                "  }\n" +
                "  vec3 col = texture2D(uTexture, tc).rgb;\n" +
                "  vec3 hsv = rgb2hsv(col);\n" +
                "  float targetHue = rgb2hsv(uTargetColor).x;\n" +
                "  float hueDiff = abs(hsv.x - targetHue);\n" +
                "  if (hueDiff > 0.5) hueDiff = 1.0 - hueDiff;\n" +
                "  float match = step(hueDiff, uHueTol) * step(0.25, hsv.y);\n" +
                "  vec3 faded = mix(vec3(dot(col, vec3(0.299, 0.587, 0.114))), col, 0.35);\n" +
                "  faded *= 0.75;\n" +
                "  vec3 hsvGlow = vec3(targetHue, 1.0, clamp(0.6 + 0.8 * hsv.z, 0.0, 1.0));\n" +
                "  vec3 K2 = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0).xxx;\n" +
                "  vec3 p2 = abs(fract(hsvGlow.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - vec3(3.0));\n" +
                "  vec3 glow = hsvGlow.z * mix(K2, clamp(p2 - K2, 0.0, 1.0), hsvGlow.y);\n" +
                "  vec3 result = mix(faded, glow, match);\n" +
                "  gl_FragColor = vec4(result, 1.0);\n" +
                "}\n";

        private final CameraSurfaceView view;
        private int programNormal;
        private int programMark;
        private int aPosLoc, aTexLoc;
        private int mvpLocN, stLocN, zoomLocN;
        private int mvpLocM, stLocM, zoomLocM, colorLocM, tolLocM;
        private int texId;
        private SurfaceTexture surfaceTexture;
        private float[] mvp = new float[16];
        private float[] stMatrix = new float[16];
        private final FloatBuffer vertexBuffer;

        private volatile boolean markMode = false;
        private volatile boolean picking = false;
        private volatile float zoom = 1.0f;
        private volatile float[] targetColor = {1f, 0f, 0f};
        private volatile float hueTol = 0.075f;
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

        public void setTargetColor(float r, float g, float b) {
            targetColor[0] = r; targetColor[1] = g; targetColor[2] = b;
        }

        public void setHueTolerance(float tol01) {
            hueTol = tol01;
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

            programNormal = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER_NORMAL);
            programMark = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER_MARK);

            mvpLocN = GLES20.glGetUniformLocation(programNormal, "uMVP");
            stLocN = GLES20.glGetUniformLocation(programNormal, "uSTMatrix");
            zoomLocN = GLES20.glGetUniformLocation(programNormal, "uZoom");

            mvpLocM = GLES20.glGetUniformLocation(programMark, "uMVP");
            stLocM = GLES20.glGetUniformLocation(programMark, "uSTMatrix");
            zoomLocM = GLES20.glGetUniformLocation(programMark, "uZoom");
            colorLocM = GLES20.glGetUniformLocation(programMark, "uTargetColor");
            tolLocM = GLES20.glGetUniformLocation(programMark, "uHueTol");

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

            AtomicReference<float[]> req = pickRequest;
            boolean pickNow = (req != null && req.get() != null);

            draw(markMode && !pickNow);

            if (pickNow) {
                float[] pos = req.getAndSet(null);
                pickRequest = null;
                if (pos != null) {
                    int px = Math.min(surfaceWidth - 1, Math.max(0, (int) (pos[0] * surfaceWidth)));
                    int py = Math.min(surfaceHeight - 1, Math.max(0, (int) (pos[1] * surfaceHeight)));
                    ByteBuffer pb = ByteBuffer.allocateDirect(4);
                    pb.order(ByteOrder.nativeOrder());
                    GLES20.glReadPixels(px, py, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pb);
                    float r = (pb.get(0) & 0xFF) / 255f;
                    float g = (pb.get(1) & 0xFF) / 255f;
                    float b = (pb.get(2) & 0xFF) / 255f;
                    view.fireColorPicked(r, g, b);
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
            if (mark) {
                GLES20.glUniform3f(colorLocM, targetColor[0], targetColor[1], targetColor[2]);
                GLES20.glUniform1f(tolLocM, hueTol);
            }

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(aPosLoc);
            GLES20.glDisableVertexAttribArray(aTexLoc);
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
