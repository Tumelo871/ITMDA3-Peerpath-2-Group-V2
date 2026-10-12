
package com.example.peerpath;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
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
            Toast.makeText(
                    this,
                    "The badges layout is missing required views.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        badgeSummary.setText("Loading your badges...");
        loadBadges();
    }

    private void loadBadges() {
        if (loading || isFinishing() || isDestroyed()) {
            return;
        }

        String userId = SupabaseApi.userId();

        if (userId == null || userId.trim().isEmpty()
                || !SupabaseApi.isLoggedIn()) {
            badgeSummary.setText("Please sign in to view your badges.");
            Toast.makeText(
                    this,
                    "Your session is missing or has expired. Please sign in again.",
                    Toast.LENGTH_LONG
            ).show();
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

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (profile == null) {
                        showLoadError("Your profile was empty.");
                        return;
                    }

                    displayBadges(profile);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    loading = false;

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    showLoadError(message);
                });
            }
        });
    }

    private void showLoadError(String message) {
        badgeContainer.removeAllViews();
        badgeSummary.setText(
                "We couldn't load your badges. Check your connection and try again."
        );

        TextView retry = new TextView(this);
        retry.setText("Tap here to retry");
        retry.setTextSize(15);
        retry.setTextColor(
                ContextCompat.getColor(this, R.color.peer_blue)
        );
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(dp(16), dp(20), dp(16), dp(20));
        retry.setOnClickListener(v -> loadBadges());

        badgeContainer.addView(retry);

        android.util.Log.e(
                "PeerPathBadges",
                "Failed to load badges: "
                        + (message == null ? "Unknown error" : message)
        );
    }

    private void displayBadges(JSONObject profile) {
        badgeContainer.removeAllViews();

        List<Gamification.Badge> badges;

        try {
            badges = Gamification.badgesFor(profile);
        } catch (Exception e) {
            android.util.Log.e(
                    "PeerPathBadges",
                    "Failed to calculate badges",
                    e
            );
            showLoadError("Badge calculation failed.");
            return;
        }

        if (badges == null || badges.isEmpty()) {
            badgeSummary.setText(
                    "No badges are configured yet. Complete learning activities to earn achievements."
            );
            return;
        }

        int unlocked = Gamification.unlockedCount(profile);
        int total = badges.size();

        badgeSummary.setText(
                unlocked + " of " + total + " badges unlocked"
                        + "\nKeep learning to unlock more achievements!"
        );

        for (Gamification.Badge badge : badges) {
            if (badge != null) {
                addBadgeRow(badge);
            }
        }
    }

    private void addBadgeRow(Gamification.Badge badge) {
        LinearLayout row = Ui.card(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));

        row.setAlpha(badge.unlocked ? 1.0f : 0.65f);

        TextView icon = Ui.text(
                this,
                badge.unlocked ? badge.emoji : "\uD83D\uDD12",
                30,
                R.color.text_primary,
                false
        );

        icon.setGravity(Gravity.CENTER);
        icon.setMinWidth(dp(52));
        icon.setPadding(0, 0, dp(12), 0);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );
        info.setLayoutParams(infoParams);

        TextView title = Ui.text(
                this,
                safeText(badge.title, "Achievement"),
                16,
                R.color.text_primary,
                true
        );

        TextView description = Ui.text(
                this,
                safeText(
                        badge.description,
                        "Continue learning to unlock this badge."
                ),
                13,
                R.color.text_secondary,
                false
        );

        TextView status = Ui.text(
                this,
                badge.unlocked ? "UNLOCKED" : "LOCKED",
                11,
                badge.unlocked ? R.color.peer_blue : R.color.text_secondary,
                true
        );

        status.setPadding(0, dp(5), 0, 0);

        info.addView(title);
        info.addView(description);
        info.addView(status);

        row.addView(icon);
        row.addView(info);

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        rowParams.setMargins(0, dp(5), 0, dp(5));
        row.setLayoutParams(rowParams);

        Ui.addToList(badgeContainer, row);
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()
                || "null".equalsIgnoreCase(value.trim())) {
            return fallback;
        }
        return value;
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
