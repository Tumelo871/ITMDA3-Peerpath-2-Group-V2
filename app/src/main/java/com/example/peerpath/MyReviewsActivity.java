package com.example.peerpath;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class MyReviewsActivity extends Activity {
    private static final int BLUE = 0xFF0D5BE1;
    private static final int NAVY = 0xFF082B6F;
    private static final int MUTED = 0xFF52627A;
    private static final int BACKGROUND = 0xFFF5F8FF;
    private static final int BORDER = 0xFFE1EAF8;

    private LinearLayout cards;
    private TextView status;
    private Button refreshButton;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSessions();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        root.setBackgroundColor(BACKGROUND);

        TextView title = text("My Reviews", 28, NAVY, true);
        title.setPadding(0, dp(2), 0, dp(4));
        root.addView(title);

        TextView subtitle = text(
                "Review completed tutoring sessions, share feedback, and choose whether your name is shown.",
                14, MUTED, false);
        subtitle.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.bottomMargin = dp(16);
        root.addView(subtitle, subtitleParams);

        refreshButton = button("Refresh sessions", true);
        refreshButton.setOnClickListener(v -> loadSessions());
        LinearLayout.LayoutParams refreshParams = matchWrap();
        refreshParams.bottomMargin = dp(16);
        root.addView(refreshButton, refreshParams);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        statusCard.setBackground(background(0xFFFFFFFF, dp(12), BORDER));
        status = text("Loading your sessions...", 14, MUTED, false);
        status.setLineSpacing(dp(2), 1.0f);
        statusCard.addView(status);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.bottomMargin = dp(14);
        root.addView(statusCard, statusParams);

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        root.addView(cards, matchWrap());

        Button back = button("Back", false);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = matchWrap();
        backParams.topMargin = dp(6);
        root.addView(back, backParams);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadSessions() {
        if (loading) return;
        loading = true;
        status.setText("Loading your sessions...");
        cards.removeAllViews();
        if (refreshButton != null) {
            refreshButton.setEnabled(false);
            refreshButton.setText("Loading...");
        }

        SupabaseApi.getMySessions(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray sessions) {
                SupabaseApi.getMyReviews(new SupabaseApi.JsonCallback() {
                    @Override
                    public void onSuccess(JSONArray reviewRows) {
                        runOnUiThread(() -> {
                            loading = false;
                            setRefreshReady();
                            if (isFinishing() || isDestroyed()) return;

                            Set<String> reviewedSessions = new HashSet<>();
                            if (reviewRows != null) {
                                for (int i = 0; i < reviewRows.length(); i++) {
                                    JSONObject review = reviewRows.optJSONObject(i);
                                    if (review == null) continue;
                                    String id = review.optString("session_id", "").trim();
                                    if (!id.isEmpty() && !"null".equalsIgnoreCase(id)) {
                                        reviewedSessions.add(id);
                                    }
                                }
                            }
                            renderSessions(sessions, reviewedSessions);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            loading = false;
                            setRefreshReady();
                            if (isFinishing() || isDestroyed()) return;
                            status.setText("Your sessions loaded, but existing reviews couldn't be checked. " + safe(error));
                        });
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    loading = false;
                    setRefreshReady();
                    if (isFinishing() || isDestroyed()) return;
                    status.setText("Could not load your sessions: " + safe(error));
                });
            }
        });
    }

    private void setRefreshReady() {
        if (refreshButton != null) {
            refreshButton.setEnabled(true);
            refreshButton.setText("Refresh sessions");
        }
    }

    private void renderSessions(JSONArray sessions, Set<String> reviewedSessions) {
        cards.removeAllViews();
        int completedCount = 0;
        int displayedCount = 0;
        String currentUser = SupabaseApi.userId();

        if (sessions != null) {
            for (int i = 0; i < sessions.length(); i++) {
                JSONObject session = sessions.optJSONObject(i);
                if (session == null) continue;
                String learnerId = session.optString("learner_id", "").trim();
                if (currentUser == null || !currentUser.equals(learnerId)) continue;

                String sessionId = session.optString("id", "").trim();
                if (sessionId.isEmpty()) continue;

                String sessionStatus = session.optString("status", "unknown").trim();
                String subject = session.optString("subject", "Tutoring session");
                String date = session.optString("session_date", "Date not provided");
                String time = session.optString("session_time", "Time not provided");
                boolean completed = "completed".equalsIgnoreCase(sessionStatus);
                boolean alreadyReviewed = reviewedSessions.contains(sessionId);

                addSessionCard(sessionId, subject, date, time, sessionStatus, completed, alreadyReviewed);
                displayedCount++;
                if (completed) completedCount++;
            }
        }

        if (displayedCount == 0) {
            status.setText("No tutoring bookings were found for your account. Your sessions will appear here once you book a tutor.");
        } else if (completedCount == 0) {
            status.setText("You have " + displayedCount + " session(s). A session becomes eligible for a review when it is marked completed.");
        } else {
            status.setText("Completed sessions: " + completedCount + ". You can review each eligible session once.");
        }
    }

    private void addSessionCard(String sessionId, String subject, String date, String time,
                                String sessionStatus, boolean completed, boolean alreadyReviewed) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.setBackground(background(0xFFFFFFFF, dp(14), BORDER));
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(14);
        cards.addView(card, cardParams);

        TextView subjectView = text(clean(subject, "Tutoring session"), 18, NAVY, true);
        card.addView(subjectView);

        TextView dateView = text(clean(date, "Date not provided") + " at " + clean(time, "Time not provided"),
                13, MUTED, false);
        dateView.setPadding(0, dp(5), 0, dp(8));
        card.addView(dateView);

        TextView statusView = text("●  " + clean(sessionStatus, "unknown").toUpperCase(), 12,
                completed ? 0xFF16804A : 0xFF8A5A00, true);
        statusView.setPadding(0, 0, 0, dp(10));
        card.addView(statusView);

        Button reviewButton;
        if (completed && !alreadyReviewed) {
            reviewButton = button("★  Write a review", true);
            reviewButton.setOnClickListener(v -> {
                Intent intent = new Intent(MyReviewsActivity.this, WriteReviewActivity.class);
                intent.putExtra("session_id", sessionId);
                startActivity(intent);
            });
        } else if (alreadyReviewed) {
            reviewButton = button("✓  Review submitted", false);
            reviewButton.setEnabled(false);
            reviewButton.setAlpha(0.72f);
        } else {
            reviewButton = button("Available when completed", false);
            reviewButton.setEnabled(false);
            reviewButton.setAlpha(0.72f);
        }
        card.addView(reviewButton, matchWrap());
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(primary ? 0xFFFFFFFF : BLUE);
        b.setMinHeight(dp(48));
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setBackground(background(primary ? BLUE : 0xFFEAF2FF, dp(12), primary ? BLUE : 0xFFD5E4FF));
        b.setStateListAnimator(null);
        return b;
    }

    private GradientDrawable background(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(3), 0, dp(3));
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) return fallback;
        return value.trim();
    }

    private String safe(String message) {
        return message == null || message.trim().isEmpty() ? "Please try again." : message.trim();
    }
}
