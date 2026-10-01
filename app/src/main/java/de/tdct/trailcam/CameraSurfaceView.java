package de.tdct.trailcam;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraSurfaceView extends GLSurfaceView implements ScaleGestureDetector.OnScaleGestureListener {

    public interface Listener {
        void onSurfaceCreated(SurfaceTexture texture, int texId);
        void onFrameAvailable();
    }

    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 5.0f;

    private Listener listener;
    private ScaleGestureDetector scaleDetector;
    private float zoom = 1.0f;
    private boolean pinchEnabled = true;
    private CameraRenderer renderer;

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

    public float getZoom() {
        return zoom;
    }

    public void setPinchEnabled(boolean enabled) {
        this.pinchEnabled = enabled;
    }

    void fireSurfaceCreated(SurfaceTexture texture, int texId) {
        if (listener != null) listener.onSurfaceCreated(texture, texId);
    }

    void fireFrameAvailable() {
        if (listener != null) listener.onFrameAvailable();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!pinchEnabled) return false;
        scaleDetector.onTouchEvent(event);
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
                "uniform float uTexW;\n" +
                "uniform float uTexH;\n" +
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
                "uniform float uThreshold;\n" +
                "uniform float uAspect;\n" +
                "\n" +
                "vec3 rgb2hsv(vec3 c) {\n" +
                "  vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);\n" +
                "  vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));\n" +
                "  vec4 q = mix(vec4(p.xyw, c.r), vec4(c.rz, p.yz), step(p.x, c.r));\n" +
                "  float d = q.x - min(q.w, q.y);\n" +
                "  float e = 1.0e-10;\n" +
                "  return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);\n" +
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
                "  vec3 target = rgb2hsv(uTargetColor);\n" +
                "  float hueDiff = abs(hsv.x - target.x);\n" +
                "  if (hueDiff > 0.5) hueDiff = 1.0 - hueDiff;\n" +
                "  float satDiff = abs(hsv.y - target.y);\n" +
                "  float valDiff = abs(hsv.z - target.z);\n" +
                "  float dist = hueDiff + 0.5 * satDiff + 0.5 * valDiff;\n" +
                "  float match = step(dist, uThreshold);\n" +
                "  vec3 faded = mix(vec3(dot(col, vec3(0.299, 0.587, 0.114))), col, 0.35);\n" +
                "  faded *= 0.75;\n" +
                "  vec3 glow = uTargetColor * (0.5 + 1.6 * hsv.z);\n" +
                "  vec3 result = mix(faded, glow, match);\n" +
                "  gl_FragColor = vec4(result, 1.0);\n" +
                "}\n";

        private final CameraSurfaceView view;
        private int programNormal;
        private int programMark;
        private int aPosLoc, aTexLoc;
        private int mvpLocN, stLocN, zoomLocN;
        private int mvpLocM, stLocM, zoomLocM, colorLocM, threshLocM;
        private int texId;
        private SurfaceTexture surfaceTexture;
        private float[] mvp = new float[16];
        private float[] stMatrix = new float[16];

        private volatile boolean markMode = false;
        private volatile float zoom = 1.0f;
        private volatile float[] targetColor = {1f, 0f, 0f};
        private volatile float threshold = 0.30f;

        CameraRenderer(CameraSurfaceView view) {
            this.view = view;
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

        public void setThreshold(float t01) {
            threshold = t01;
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
            threshLocM = GLES20.glGetUniformLocation(programMark, "uThreshold");

            view.fireSurfaceCreated(surfaceTexture, texId);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            float aspect = (float) width / Math.max(1, height);
            android.opengl.Matrix.orthoM(mvp, 0, -aspect, aspect, -1, 1, -1, 1);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (surfaceTexture == null) return;
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(stMatrix);

            float[] verts = new float[]{
                    -1f, -1f, 0f, 0f, 0f,
                    1f, -1f, 0f, 1f, 0f,
                    -1f, 1f, 0f, 0f, 1f,
                    1f, 1f, 0f, 1f, 1f,
            };
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(verts.length * 4);
            bb.order(java.nio.ByteOrder.nativeOrder());
            java.nio.FloatBuffer fb = bb.asFloatBuffer();
            fb.put(verts);
            fb.position(0);

            int program = markMode ? programMark : programNormal;
            GLES20.glUseProgram(program);
            aPosLoc = GLES20.glGetAttribLocation(program, "aPosition");
            aTexLoc = GLES20.glGetAttribLocation(program, "aTexCoord");
            GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 20, fb);
            GLES20.glEnableVertexAttribArray(aPosLoc);
            GLES20.glVertexAttribPointer(aTexLoc, 3, GLES20.GL_FLOAT, false, 20, fb.position(3));
            GLES20.glEnableVertexAttribArray(aTexLoc);

            GLES20.glUniformMatrix4fv(markMode ? mvpLocM : mvpLocN, 1, false, mvp, 0);
            GLES20.glUniformMatrix4fv(markMode ? stLocM : stLocN, 1, false, stMatrix, 0);
            GLES20.glUniform1f(markMode ? zoomLocM : zoomLocN, zoom);
            if (markMode) {
                GLES20.glUniform3f(colorLocM, targetColor[0], targetColor[1], targetColor[2]);
                GLES20.glUniform1f(threshLocM, threshold);
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
