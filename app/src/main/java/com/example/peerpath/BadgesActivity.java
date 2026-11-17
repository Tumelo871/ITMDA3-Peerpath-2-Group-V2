package com.example.peerpath;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONObject;

import java.util.List;

public class BadgesActivity extends AppCompatActivity {

    private LinearLayout badgeContainer;
    private TextView badgeSummary;
    private View backButton;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_badges);

        badgeContainer = findViewById(R.id.badgeContainer);
        badgeSummary = findViewById(R.id.tvBadgeSummary);
        backButton = findViewById(R.id.btnBack);

        if (badgeContainer == null || badgeSummary == null) {
            Toast.makeText(this, "The badges layout is missing required views.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (backButton != null) backButton.setOnClickListener(v -> finish());
        badgeSummary.setText("Loading your badges...");
        loadBadges();
    }

    private void loadBadges() {
        if (loading || isFinishing() || isDestroyed()) return;

        String userId = SupabaseApi.userId();
        if (!SupabaseApi.isLoggedIn() || userId == null || userId.trim().isEmpty()) {
            badgeSummary.setText("Please sign in to view your badges.");
            return;
        }

        loading = true;
        badgeSummary.setText("Loading your badges...");
        badgeContainer.removeAllViews();

        SupabaseApi.getProfile(userId, new SupabaseApi.ObjectCallback() {
            @Override
            public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> {
                    loading = false;
                    if (isFinishing() || isDestroyed()) return;
                    if (profile == null) {
                        showLoadError("Your profile was empty.");
                    } else {
                        displayBadges(profile);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    loading = false;
                    if (!isFinishing() && !isDestroyed()) showLoadError(message);
                });
            }
        });
    }

    private void showLoadError(String message) {
        badgeContainer.removeAllViews();
        badgeSummary.setText("We couldn't load your badges. Check your connection and try again.");

        TextView retry = new TextView(this);
        retry.setText("Tap here to retry");
        retry.setTextSize(15);
        retry.setTextColor(ContextCompat.getColor(this, R.color.peer_blue));
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(dp(16), dp(20), dp(16), dp(20));
        retry.setOnClickListener(v -> loadBadges());
        badgeContainer.addView(retry);

        android.util.Log.e("PeerPathBadges", "Failed to load badges: " +
                (message == null ? "Unknown error" : message));
    }

    private void displayBadges(JSONObject profile) {
        badgeContainer.removeAllViews();
        List<Gamification.Badge> badges;
        try {
            badges = Gamification.badgesFor(profile);
        } catch (Exception e) {
            android.util.Log.e("PeerPathBadges", "Failed to calculate badges", e);
            showLoadError("Badge calculation failed.");
            return;
        }

        if (badges == null || badges.isEmpty()) {
            badgeSummary.setText("No achievements are available yet.");
            return;
        }

        int unlocked = Gamification.unlockedCount(profile);
        int total = badges.size();
        badgeSummary.setText(unlocked + " of " + total + " badges unlocked\n" +
                Gamification.badgeProgressPercent(profile) + "% complete");

        for (Gamification.Badge badge : badges) {
            if (badge != null) addBadgeRow(badge);
        }
    }

    private void addBadgeRow(Gamification.Badge badge) {
        LinearLayout card = Ui.card(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setAlpha(badge.unlocked ? 1.0f : 0.82f);

        TextView icon = Ui.text(this, badge.unlocked ? badge.emoji : "🔒",
                28, R.color.text_primary, false);
        icon.setGravity(Gravity.CENTER);
        icon.setMinWidth(dp(52));
        icon.setPadding(0, 0, dp(12), 0);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = Ui.text(this, safeText(badge.title, "Achievement"),
                16, R.color.text_primary, true);
        TextView description = Ui.text(this,
                safeText(badge.description, "Continue learning to unlock this badge."),
                13, R.color.text_secondary, false);
        TextView progressText = Ui.text(this,
                badge.unlocked ? "UNLOCKED" : safeText(badge.progressText, "Keep going"),
                12, badge.unlocked ? R.color.peer_blue : R.color.text_secondary, true);
        progressText.setPadding(0, dp(6), 0, dp(4));

        ProgressBar progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(badge.progressPercent());
        progress.setIndeterminate(false);

        info.addView(title);
        info.addView(description);
        info.addView(progressText);
        info.addView(progress);

        card.addView(icon);
        card.addView(info);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(5));
        card.setLayoutParams(params);
        Ui.addToList(badgeContainer, card);
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty() ||
                "null".equalsIgnoreCase(value.trim())) return fallback;
        return value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
