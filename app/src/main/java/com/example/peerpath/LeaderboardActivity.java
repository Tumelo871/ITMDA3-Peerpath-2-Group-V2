
package com.example.peerpath;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class LeaderboardActivity extends AppCompatActivity {

    private LinearLayout leaderboardContainer;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvMyRank;
    private View btnBack;

    private boolean requestInProgress = false;

    private static final int LIMIT = 50;

    private static final String[] MEDALS = {
            "\uD83E\uDD47",
            "\uD83E\uDD48",
            "\uD83E\uDD49"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        leaderboardContainer = findViewById(R.id.leaderboardContainer);
        progressBar = findViewById(R.id.progressLeaderboard);
        tvStatus = findViewById(R.id.tvLeaderboardStatus);
        tvMyRank = findViewById(R.id.tvMyRank);
        btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (!SupabaseApi.isLoggedIn()) {
            Toast.makeText(
                    this,
                    "Please sign in to view the leaderboard.",
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        loadLeaderboard();
    }

    private void loadLeaderboard() {
        if (requestInProgress || isFinishing() || isDestroyed()) {
            return;
        }

        requestInProgress = true;
        showLoading();

        /*
         * This method expects a restricted leaderboard endpoint/RPC.
         * It must return only public leaderboard fields, not full profiles.
         *
         * Expected fields:
         * id, full_name, role, xp, level
         */
        SupabaseApi.getLeaderboard(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    requestInProgress = false;

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    renderLeaderboard(data);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    requestInProgress = false;

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    showError(message);
                });
            }
        });
    }

    private void showLoading() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        if (tvStatus != null) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText("Loading leaderboard...");
        }

        if (leaderboardContainer != null) {
            leaderboardContainer.removeAllViews();
        }

        if (tvMyRank != null) {
            tvMyRank.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        if (tvStatus != null) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText(
                    "We couldn't load the leaderboard.\n" +
                            "Check your connection and try again."
            );
        }

        if (leaderboardContainer != null) {
            leaderboardContainer.removeAllViews();

            TextView retry = createText(
                    "Tap here to retry",
                    15,
                    R.color.peer_blue,
                    true
            );
            retry.setGravity(Gravity.CENTER);
            retry.setPadding(16, 20, 16, 20);
            retry.setOnClickListener(v -> loadLeaderboard());
            leaderboardContainer.addView(retry);
        }

        if (message != null && !message.trim().isEmpty()) {
            android.util.Log.e(
                    "PeerPathLeaderboard",
                    "Leaderboard error: " + message
            );
        }
    }

    private void renderLeaderboard(JSONArray data) {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        leaderboardContainer.removeAllViews();

        if (data == null || data.length() == 0) {
            if (tvStatus != null) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText(
                        "No rankings yet.\nEarn XP by learning and completing sessions!"
                );
            }

            if (tvMyRank != null) {
                tvMyRank.setVisibility(View.GONE);
            }
            return;
        }

        if (tvStatus != null) {
            tvStatus.setVisibility(View.GONE);
        }

        String myUserId = SupabaseApi.userId();
        int myRank = -1;

        for (int i = 0; i < data.length(); i++) {
            JSONObject profile = data.optJSONObject(i);

            if (profile == null) {
                continue;
            }

            String id = profile.optString("id", "");
            if (!myUserId.isEmpty() && myUserId.equals(id)) {
                myRank = i + 1;
                break;
            }
        }

        if (tvMyRank != null) {
            tvMyRank.setVisibility(View.VISIBLE);

            if (myRank > 0) {
                tvMyRank.setText("Your position: #" + myRank);
            } else {
                tvMyRank.setText(
                        "Keep earning XP to climb the leaderboard!"
                );
            }
        }

        int rank = 1;

        for (int i = 0; i < data.length(); i++) {
            JSONObject profile = data.optJSONObject(i);

            if (profile == null) {
                continue;
            }

            String id = profile.optString("id", "");
            String name = profile.optString("full_name", "Learner");

            if (name.trim().isEmpty() || "null".equalsIgnoreCase(name)) {
                name = "Learner";
            }

            String role = profile.optString("role", "learner");
            long xp = Math.max(0L, profile.optLong("xp", 0L));
            long level = profile.optLong(
                    "level",
                    Gamification.levelFor(xp)
            );

            boolean isMe = !myUserId.isEmpty() && myUserId.equals(id);

            leaderboardContainer.addView(
                    createLeaderboardRow(
                            rank,
                            name,
                            role,
                            xp,
                            level,
                            isMe
                    )
            );

            rank++;
        }

        if (rank == 1 && tvStatus != null) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText("No ranking information is available.");
        }
    }

    private View createLeaderboardRow(
            int rank,
            String name,
            String role,
            long xp,
            long level,
            boolean isMe
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        int padding = dp(14);
        card.setPadding(padding, padding, padding, padding);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        cardParams.setMargins(0, dp(5), 0, dp(5));
        card.setLayoutParams(cardParams);

        card.setBackgroundResource(
                isMe ? R.drawable.bg_quote : R.drawable.bg_quote
        );

        TextView position = createText(
                rank <= 3 ? MEDALS[rank - 1] : "#" + rank,
                18,
                R.color.text_primary,
                true
        );

        position.setGravity(Gravity.CENTER);
        position.setMinWidth(dp(48));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );
        infoParams.setMargins(dp(8), 0, dp(8), 0);
        info.setLayoutParams(infoParams);

        TextView nameView = createText(
                name + (isMe ? " (you)" : ""),
                15,
                R.color.text_primary,
                true
        );

        TextView detailsView = createText(
                ("tutor".equalsIgnoreCase(role) ? "Tutor" : "Learner")
                        + " \u00B7 Level " + level,
                12,
                R.color.text_secondary,
                false
        );

        info.addView(nameView);
        info.addView(detailsView);

        TextView xpView = createText(
                String.format(Locale.getDefault(), "%,d XP", xp),
                14,
                R.color.peer_blue,
                true
        );
        xpView.setGravity(Gravity.CENTER_VERTICAL);

        card.addView(position);
        card.addView(info);
        card.addView(xpView);

        return card;
    }

    private TextView createText(
            String value,
            int sizeSp,
            int colorRes,
            boolean bold
    ) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(ContextCompat.getColor(this, colorRes));
        text.setGravity(Gravity.CENTER_VERTICAL);

        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return text;
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
