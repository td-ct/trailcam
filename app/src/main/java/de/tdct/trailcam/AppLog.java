package de.tdct.trailcam;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

public final class AppLog {

    public static final String TAG = "TrailCam";
    private static final int MAX_LINES = 200;
    private static final Deque<String> buffer = new ArrayDeque<>();
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static File logDir;
    private static Listener listener;

    public interface Listener {
        void onLog(String line);
    }

    private AppLog() {
    }

    public static void init(Context ctx) {
        try {
            logDir = ctx.getExternalFilesDir(null);
        } catch (Exception e) {
            logDir = ctx.getFilesDir();
        }
        d("=== TrailCam Start, Android " + Build.VERSION.RELEASE
                + " (" + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER
                + " " + Build.MODEL + " ===");
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e("UNCAUGHT EXCEPTION on thread " + t.getName(), e);
            writeCrashFile(e);
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    public static synchronized void d(String msg) {
        Log.d(TAG, msg);
        add("D " + TS.format(new Date()) + " " + msg);
    }

    public static synchronized void i(String msg) {
        Log.i(TAG, msg);
        add("I " + TS.format(new Date()) + " " + msg);
    }

    public static synchronized void w(String msg) {
        Log.w(TAG, msg);
        add("W " + TS.format(new Date()) + " " + msg);
    }

    public static synchronized void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        add("E " + TS.format(new Date()) + " " + msg
                + (t == null ? "" : "\n" + stackTraceOf(t)));
        appendToFile("crash.log", msg + "\n" + (t == null ? "" : stackTraceOf(t)) + "\n\n");
    }

    private static void add(String line) {
        synchronized (buffer) {
            buffer.addLast(line);
            while (buffer.size() > MAX_LINES) {
                buffer.removeFirst();
            }
        }
        appendToFile("trailcam.log", line + "\n");
        if (listener != null) {
            try {
                listener.onLog(line);
            } catch (Exception ignored) {
            }
        }
    }

    public static synchronized String getLog() {
        StringBuilder sb = new StringBuilder();
        synchronized (buffer) {
            for (String line : buffer) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    public static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static void writeCrashFile(Throwable t) {
        appendToFile("crash.log", "=== CRASH "
                + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                + " ===\n" + stackTraceOf(t) + "\n\n");
    }

    private static synchronized void appendToFile(String name, String text) {
        if (logDir == null) {
            return;
        }
        try {
            File f = new File(logDir, name);
            FileWriter w = new FileWriter(f, true);
            w.write(text);
            w.close();
        } catch (Exception ignored) {
        }
    }

    public static String getLogFilePath() {
        if (logDir == null) {
            return null;
        }
        return new File(logDir, "trailcam.log").getAbsolutePath();
    }
}
