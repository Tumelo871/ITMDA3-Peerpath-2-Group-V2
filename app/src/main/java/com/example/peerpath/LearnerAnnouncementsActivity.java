package com.example.peerpath;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Read-only learner announcements feed.
 * Supabase filters announcements to tutors with whom the current learner has
 * an accepted or completed session.
 */
public class LearnerAnnouncementsActivity extends Activity {
    private static final int NAVY = 0xFF082B6F;
    private static final int BLUE = 0xFF0D5BE1;
    private static final int BACKGROUND = 0xFFF5F8FF;
    private static final int TEXT = 0xFF17243A;
    private static final int MUTED = 0xFF52627A;

    private LinearLayout announcementList;
    private TextView statusText;
    private Button refreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseApi.init(getApplicationContext());

        if (!SupabaseApi.isLoggedIn()) {
            Toast.makeText(this, "Please sign in to view announcements.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildUi();
        loadAnnouncements();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));
        root.setBackgroundColor(BACKGROUND);

        TextView title = makeText("Tutor Announcements", 26, NAVY, true);
        root.addView(title);

        TextView subtitle = makeText(
                "Announcements from tutors you have an accepted or completed session with.",
                14, MUTED, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(4);
        root.addView(subtitle, subtitleParams);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams toolbarParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        toolbarParams.topMargin = dp(14);

        statusText = makeText("Loading announcements...", 14, MUTED, false);
        toolbar.addView(statusText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        refreshButton = makeButton("Refresh");
        refreshButton.setOnClickListener(v -> loadAnnouncements());
        toolbar.addView(refreshButton);
        root.addView(toolbar, toolbarParams);

        announcementList = new LinearLayout(this);
        announcementList.setOrientation(LinearLayout.VERTICAL);
        root.addView(announcementList);

        Button backButton = makeButton("Back to dashboard");
        backButton.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        backParams.topMargin = dp(18);
        root.addView(backButton, backParams);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void loadAnnouncements() {
        if (announcementList == null || statusText == null) return;

        refreshButton.setEnabled(false);
        statusText.setText("Loading announcements...");
        announcementList.removeAllViews();

        SupabaseApi.getLearnerAnnouncements(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    refreshButton.setEnabled(true);
                    announcementList.removeAllViews();

                    if (data == null || data.length() == 0) {
                        statusText.setText("No announcements yet.");
                        announcementList.addView(makeText(
                                "Announcements from your eligible tutors will appear here.",
                                15, MUTED, false));
                        return;
                    }

                    statusText.setText(data.length() + " announcement(s)");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.optJSONObject(i);
                        if (item != null) addAnnouncementCard(item);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    refreshButton.setEnabled(true);
                    statusText.setText("Could not load announcements.");
                    announcementList.removeAllViews();
                    announcementList.addView(makeText(
                            "Please check your connection and try again.",
                            15, MUTED, false));
                    Toast.makeText(LearnerAnnouncementsActivity.this,
                            message == null ? "Unknown error" : message,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void addAnnouncementCard(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(10);
        card.setLayoutParams(cardParams);

        String tutorName = item.optString("tutor_name", "PeerPath Tutor");
        String message = item.optString("message", "");
        String createdAt = item.optString("created_at", "");

        TextView author = makeText(tutorName, 17, NAVY, true);
        card.addView(author);

        TextView date = makeText(formatDate(createdAt), 12, MUTED, false);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(2);
        card.addView(date, dateParams);

        TextView body = makeText(message, 15, TEXT, false);
        body.setPadding(0, dp(10), 0, dp(2));
        card.addView(body);

        announcementList.addView(card);
    }

    private String formatDate(String value) {
        if (value == null || value.trim().isEmpty()) return "";

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ"
        };

        for (String pattern : patterns) {
            try {
                Date date = new SimpleDateFormat(pattern, Locale.getDefault()).parse(value);
                if (date != null) {
                    return DateFormat.getDateTimeInstance(
                            DateFormat.MEDIUM, DateFormat.SHORT).format(date);
                }
            } catch (ParseException ignored) {
                // Try the next supported timestamp format.
            }
        }
        return value;
    }

    private TextView makeText(String value, int size, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(size);
        textView.setTextColor(color);
        textView.setPadding(0, dp(3), 0, dp(4));
        if (bold) textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return textView;
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(BLUE));
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
