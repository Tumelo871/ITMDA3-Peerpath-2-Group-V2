package com.example.peerpath;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TutorProfileActivity extends AppCompatActivity {

    private String tutorId;
    private TextView name, subject, rating, level, bio, experience, noReviews;
    private LinearLayout reviews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_tutor_profile);

        tutorId = getIntent().getStringExtra("tutorId");

        name = findViewById(R.id.tvTutorName);
        subject = findViewById(R.id.tvTutorSubject);
        rating = findViewById(R.id.tvTutorRating);
        level = findViewById(R.id.tvTutorLevel);
        bio = findViewById(R.id.tvTutorBio);
        experience = findViewById(R.id.tvTutorExperience);
        noReviews = findViewById(R.id.tvNoReviews);
        reviews = findViewById(R.id.reviewContainer);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnBookSession).setOnClickListener(v -> {
            if (TextUtils.isEmpty(tutorId)) {
                Toast.makeText(this, "Tutor could not be identified.", Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(this, SessionRequestActivity.class);
            intent.putExtra("tutorId", tutorId);
            intent.putExtra("subject", subject.getTag() instanceof String ? (String) subject.getTag() : "");
            intent.putExtra("tutorName", name.getText().toString());
            startActivity(intent);
        });

        if (TextUtils.isEmpty(tutorId)) {
            Toast.makeText(this, "Please reopen this tutor from search.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadTutor();
        loadReviews();
    }

    private void loadTutor() {
        rating.setText("Loading tutor profile...");
        SupabaseApi.getTutorProfile(tutorId, new SupabaseApi.ObjectCallback() {
            @Override
            public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    String tutorName = clean(profile.optString("full_name", ""), "PeerPath Tutor");
                    String modules = firstNonBlank(
                            profile.optString("module_codes", ""),
                            profile.optString("tutor_subject", ""),
                            profile.optString("modules", ""));
                    name.setText(tutorName);
                    subject.setText(modules.isEmpty() ? "Modules not added yet" : modules);
                    subject.setTag(modules);

                    double average = profile.optDouble("rating_average", 0.0);
                    int count = profile.optInt("rating_count", 0);
                    rating.setText(count > 0
                            ? String.format(Locale.US, "⭐ %.1f (%d reviews) · ✓ Verified", average, count)
                            : "✓ Verified tutor · No reviews yet");
                    level.setText("Level " + Math.max(1, profile.optInt("level", 1)));
                    bio.setText(clean(profile.optString("tutor_bio", ""), "This tutor hasn't added a bio yet."));
                    experience.setText(clean(profile.optString("tutor_experience", ""), "Experience not provided."));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    rating.setText("Unable to load tutor profile.");
                    Toast.makeText(TutorProfileActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadReviews() {
        if (reviews == null) return;
        reviews.removeAllViews();
        addReviewText("Loading learner reviews...", 14, false);

        SupabaseApi.getTutorReviews(tutorId, new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray result) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || reviews == null) return;
                    reviews.removeAllViews();
                    int count = result == null ? 0 : result.length();
                    if (noReviews != null) noReviews.setText(count == 0 ? "No reviews yet" : count + (count == 1 ? " review" : " reviews"));
                    if (count == 0) {
                        addReviewText("No reviews yet", 15, true);
                        addReviewText("This tutor hasn't received reviews yet.", 13, false);
                        return;
                    }
                    for (int i = 0; i < count; i++) {
                        JSONObject item = result.optJSONObject(i);
                        if (item == null) continue;
                        addReviewCard(item);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || reviews == null) return;
                    reviews.removeAllViews();
                    addReviewText("Reviews couldn't be loaded.", 14, true);
                    addReviewText("Please check your connection and try again.", 13, false);
                    android.util.Log.e("TutorProfileActivity", "Loading tutor reviews failed: " + message);
                });
            }
        });
    }

    private void addReviewCard(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.bg_subject_card);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        reviews.addView(card, cardParams);

        double score = item.optDouble("rating", 0.0);
        String reviewer = clean(item.optString("reviewer_name", ""), "Anonymous learner");
        String comment = clean(item.optString("comment", ""), "No written comment was provided.");
        String date = formatDate(item.optString("created_at", ""));

        TextView stars = new TextView(this);
        stars.setText(starText(score) + "  " + String.format(Locale.US, "%.0f/5", score));
        stars.setTextColor(getColor(R.color.text_primary));
        stars.setTextSize(15);
        stars.setTypeface(null, Typeface.BOLD);
        card.addView(stars);

        TextView reviewerView = new TextView(this);
        reviewerView.setText(reviewer);
        reviewerView.setTextColor(getColor(R.color.peer_blue));
        reviewerView.setTextSize(13);
        reviewerView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams reviewerParams = new LinearLayout.LayoutParams(-1, -2);
        reviewerParams.topMargin = dp(5);
        card.addView(reviewerView, reviewerParams);

        TextView commentView = new TextView(this);
        commentView.setText(comment);
        commentView.setTextColor(getColor(R.color.text_primary));
        commentView.setTextSize(14);
        LinearLayout.LayoutParams commentParams = new LinearLayout.LayoutParams(-1, -2);
        commentParams.topMargin = dp(7);
        card.addView(commentView, commentParams);

        if (!date.isEmpty()) {
            TextView dateView = new TextView(this);
            dateView.setText(date);
            dateView.setTextColor(getColor(R.color.text_secondary));
            dateView.setTextSize(12);
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1, -2);
            dateParams.topMargin = dp(7);
            card.addView(dateView, dateParams);
        }
    }

    private void addReviewText(String text, int sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(bold ? R.color.text_primary : R.color.text_secondary));
        view.setTextSize(sizeSp);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        view.setPadding(dp(2), dp(4), dp(2), dp(4));
        reviews.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    private String starText(double score) {
        int filled = Math.max(0, Math.min(5, (int) Math.round(score)));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < filled; i++) out.append("★");
        for (int i = filled; i < 5; i++) out.append("☆");
        return out.toString();
    }

    private String formatDate(String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) return "";
        try {
            String normalized = value.trim();
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed;
            try {
                parsed = input.parse(normalized);
            } catch (Exception first) {
                input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US);
                input.setTimeZone(TimeZone.getTimeZone("UTC"));
                parsed = input.parse(normalized);
            }
            if (parsed == null) return normalized;
            return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(parsed);
        } catch (Exception ignored) {
            return value;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) return value.trim();
            }
        }
        return "";
    }

    private String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) return fallback;
        return value.trim();
    }
}
