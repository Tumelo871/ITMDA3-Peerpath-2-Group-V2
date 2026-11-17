package com.example.peerpath;

import android.graphics.Typeface;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class LeaderboardActivity extends AppCompatActivity {

    private LinearLayout leaderboardContainer;
    private LinearLayout podiumContainer;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvMyRank;
    private TextView btnRefresh;
    private View btnBack;
    private boolean requestInProgress = false;

    private static final String[] MEDALS = {
            "\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        leaderboardContainer = findViewById(R.id.leaderboardContainer);
        podiumContainer = findViewById(R.id.podiumContainer);
        progressBar = findViewById(R.id.progressLeaderboard);
        tvStatus = findViewById(R.id.tvLeaderboardStatus);
        tvMyRank = findViewById(R.id.tvMyRank);
        btnRefresh = findViewById(R.id.btnRefreshLeaderboard);
        btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> loadLeaderboard());

        if (!SupabaseApi.isLoggedIn()) {
            Toast.makeText(this, "Please sign in to view the leaderboard.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        loadLeaderboard();
    }

    private void loadLeaderboard() {
        if (requestInProgress || isFinishing() || isDestroyed()) return;
        requestInProgress = true;
        showLoading();

        // Use the existing restricted RPC; never fetch full profiles for rankings.
        SupabaseApi.getLeaderboard(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    requestInProgress = false;
                    if (!isFinishing() && !isDestroyed()) renderLeaderboard(data);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    requestInProgress = false;
                    if (!isFinishing() && !isDestroyed()) showError(message);
                });
            }
        });
    }

    private void showLoading() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (tvStatus != null) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText("Loading leaderboard...");
        }
        if (leaderboardContainer != null) leaderboardContainer.removeAllViews();
        if (podiumContainer != null) {
            podiumContainer.removeAllViews();
            podiumContainer.setVisibility(View.GONE);
        }
        if (tvMyRank != null) tvMyRank.setVisibility(View.GONE);
        if (btnRefresh != null) btnRefresh.setEnabled(false);
    }

    private void showError(String message) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (btnRefresh != null) btnRefresh.setEnabled(true);
        if (tvStatus != null) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText("We couldn't load the leaderboard.\nCheck your connection and try again.");
        }
        if (podiumContainer != null) podiumContainer.setVisibility(View.GONE);
        if (leaderboardContainer != null) {
            leaderboardContainer.removeAllViews();
            TextView retry = createText("Tap here to retry", 15,
                    R.color.peer_blue, true);
            retry.setGravity(Gravity.CENTER);
            retry.setPadding(dp(16), dp(20), dp(16), dp(20));
            retry.setOnClickListener(v -> loadLeaderboard());
            leaderboardContainer.addView(retry);
        }
        android.util.Log.e("PeerPathLeaderboard", "Leaderboard error: " +
                (message == null ? "Unknown error" : message));
    }

    private void renderLeaderboard(JSONArray data) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (btnRefresh != null) btnRefresh.setEnabled(true);
        if (leaderboardContainer != null) leaderboardContainer.removeAllViews();
        if (podiumContainer != null) podiumContainer.removeAllViews();

        if (data == null || data.length() == 0) {
            if (tvStatus != null) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText("No rankings yet.\nEarn XP by learning and completing sessions!");
            }
            if (tvMyRank != null) tvMyRank.setVisibility(View.GONE);
            if (podiumContainer != null) podiumContainer.setVisibility(View.GONE);
            return;
        }

        if (tvStatus != null) tvStatus.setVisibility(View.GONE);

        String myUserId = SupabaseApi.userId();
        if (myUserId == null) myUserId = "";
        int myRank = -1;
        int rank = 0;

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            rank++;
            if (!myUserId.isEmpty() && myUserId.equals(item.optString("id", ""))) {
                myRank = rank;
                break;
            }
        }

        if (tvMyRank != null) {
            tvMyRank.setVisibility(View.VISIBLE);
            tvMyRank.setText(myRank > 0
                    ? "Your position: #" + myRank
                    : "Keep earning XP to climb the leaderboard!");
        }

        renderPodium(data);

        rank = 0;
        for (int i = 0; i < data.length(); i++) {
            JSONObject profile = data.optJSONObject(i);
            if (profile == null) continue;
            rank++;
            if (rank <= 3) continue; // Already shown in the podium.

            String id = profile.optString("id", "");
            String name = safeName(profile.optString("full_name", ""));
            String role = profile.optString("role", "learner");
            long xp = Math.max(0L, profile.optLong("xp", 0L));
            long level = Math.max(1L, profile.optLong("level",
                    Gamification.levelFor(xp)));
            boolean isMe = !myUserId.isEmpty() && myUserId.equals(id);

            leaderboardContainer.addView(createLeaderboardRow(
                    rank, name, role, xp, level, isMe));
        }

        if (leaderboardContainer.getChildCount() == 0) {
            TextView message = createText("You are viewing the top three PeerPath members.",
                    13, R.color.text_secondary, false);
            message.setGravity(Gravity.CENTER);
            message.setPadding(0, dp(12), 0, dp(12));
            leaderboardContainer.addView(message);
        }
    }

    private void renderPodium(JSONArray data) {
        if (podiumContainer == null) return;
        podiumContainer.setVisibility(View.VISIBLE);
        int count = Math.min(3, data.length());
        for (int i = 0; i < count; i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            String myId = SupabaseApi.userId();
            boolean isMe = myId != null && !myId.isEmpty() && myId.equals(id);
            String name = safeName(item.optString("full_name", ""));
            long xp = Math.max(0L, item.optLong("xp", 0L));
            long level = Math.max(1L, item.optLong("level",
                    Gamification.levelFor(xp)));
            podiumContainer.addView(createPodiumCard(i + 1, name, xp, level, isMe));
        }
    }

    private View createPodiumCard(int rank, String name, long xp,
                                  long level, boolean isMe) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        int pad = dp(rank == 1 ? 14 : 10);
        card.setPadding(dp(8), pad, dp(8), pad);
        card.setBackgroundResource(R.drawable.bg_quote);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), dp(rank == 1 ? 0 : 12), dp(3), 0);
        card.setLayoutParams(params);

        TextView medal = createText(MEDALS[rank - 1], rank == 1 ? 28 : 23,
                R.color.text_primary, true);
        medal.setGravity(Gravity.CENTER);
        TextView position = createText(rank == 1 ? "1ST" : rank == 2 ? "2ND" : "3RD",
                11, R.color.peer_blue, true);
        position.setGravity(Gravity.CENTER);
        TextView person = createText(name + (isMe ? " (you)" : ""),
                13, R.color.text_primary, true);
        person.setGravity(Gravity.CENTER);
        person.setMaxLines(2);
        TextView score = createText(String.format(Locale.getDefault(), "%,d XP", xp),
                12, R.color.peer_blue, true);
        score.setGravity(Gravity.CENTER);
        TextView levelView = createText("Level " + level, 11,
                R.color.text_secondary, false);
        levelView.setGravity(Gravity.CENTER);

        card.addView(medal);
        card.addView(position);
        card.addView(person);
        card.addView(score);
        card.addView(levelView);
        return card;
    }

    private View createLeaderboardRow(int rank, String name, String role,
                                      long xp, long level, boolean isMe) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int padding = dp(14);
        card.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(5));
        card.setLayoutParams(params);
        card.setBackgroundResource(R.drawable.bg_quote);

        TextView position = createText("#" + rank, 16,
                R.color.text_primary, true);
        position.setGravity(Gravity.CENTER);
        position.setMinWidth(dp(48));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = createText(name + (isMe ? " (you)" : ""),
                15, R.color.text_primary, true);
        TextView detailsView = createText(
                ("tutor".equalsIgnoreCase(role) ? "Tutor" : "Learner") +
                        " · Level " + level,
                12, R.color.text_secondary, false);
        info.addView(nameView);
        info.addView(detailsView);

        TextView xpView = createText(String.format(Locale.getDefault(),
                "%,d XP", xp), 14, R.color.peer_blue, true);
        card.addView(position);
        card.addView(info);
        card.addView(xpView);
        return card;
    }

    private String safeName(String value) {
        if (value == null || value.trim().isEmpty() ||
                "null".equalsIgnoreCase(value.trim())) return "PeerPath member";
        return value.trim();
    }

    private TextView createText(String value, int sizeSp, int colorRes,
                                boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(ContextCompat.getColor(this, colorRes));
        text.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
