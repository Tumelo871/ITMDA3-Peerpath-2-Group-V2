package com.example.peerpath;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

/** Stable splash/entry point for the Supabase-backed PeerPath app. */
public class MainActivity extends AppCompatActivity {
    private static final int SPLASH_DURATION = 1500;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator animator;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_main);
        ProgressBar bar = findViewById(R.id.splashProgress);
        animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(SPLASH_DURATION);
        animator.addUpdateListener(a -> bar.setProgress((int) a.getAnimatedValue()));
        animator.start();
        handler.postDelayed(() -> {
            if (!SupabaseApi.isConfigured()) {
                startActivity(new Intent(this, LoginActivity.class));
            } else {
                startActivity(new Intent(this, SupabaseApi.isLoggedIn() ? DashboardActivity.class : LoginActivity.class));
            }
            finish();
        }, SPLASH_DURATION);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (animator != null) animator.cancel();
    }
}
