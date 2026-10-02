package de.tdct.trailcam;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SplashActivity extends Activity {

    private static final long MIN_SPLASH_MS = 1200;
    private static final String GITHUB_URL = "https://github.com/td-ct";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView version = findViewById(R.id.splash_version);
        String vName;
        try {
            vName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            vName = "?";
        }
        version.setText("v" + vName);

        Button github = findViewById(R.id.splash_github);
        github.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)));
            } catch (Exception e) {
                AppLog.w("GitHub-Link konnte nicht ge\u00f6ffnet werden");
            }
        });

        findViewById(R.id.splash_root).setOnClickListener(v -> startMain());

        new Thread(() -> {
            long start = System.currentTimeMillis();
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed < MIN_SPLASH_MS) {
                try {
                    Thread.sleep(MIN_SPLASH_MS - elapsed);
                } catch (InterruptedException ignored) {
                }
            }
            runOnUiThread(this::startMain);
        }).start();
    }

    private boolean started = false;

    private void startMain() {
        if (started) {
            return;
        }
        started = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
