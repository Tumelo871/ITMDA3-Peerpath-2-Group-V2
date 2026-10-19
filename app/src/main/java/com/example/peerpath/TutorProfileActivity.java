
package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONObject;

import java.util.Locale;

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
                Toast.makeText(this,
                        "Tutor could not be identified.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            Intent intent = new Intent(
                    this, SessionRequestActivity.class);

            intent.putExtra("tutorId", tutorId);
            intent.putExtra("subject",
                    subject.getTag() instanceof String
                            ? (String) subject.getTag() : "");
            intent.putExtra("tutorName", name.getText().toString());

            startActivity(intent);
        });

        if (TextUtils.isEmpty(tutorId)) {
            Toast.makeText(this,
                    "Please reopen this tutor from search.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        loadTutor();
    }

    private void loadTutor() {
        rating.setText("Loading tutor profile...");

        SupabaseApi.getTutorProfile(tutorId,
                new SupabaseApi.ObjectCallback() {

                    @Override
                    public void onSuccess(JSONObject profile) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;

                            String tutorName = clean(
                                    profile.optString("full_name", ""),
                                    "PeerPath Tutor");

                            String modules = firstNonBlank(
                                    profile.optString("module_codes", ""),
                                    profile.optString("tutor_subject", ""),
                                    profile.optString("modules", ""));

                            name.setText(tutorName);

                            subject.setText(modules.isEmpty()
                                    ? "Modules not added yet"
                                    : modules);

                            // Keep fallback display text out of the booking form.
                            subject.setTag(modules);

                            double average =
                                    profile.optDouble("rating_average", 0.0);

                            int count = profile.optInt("rating_count", 0);

                            rating.setText(count > 0
                                    ? String.format(Locale.US,
                                    "⭐ %.1f (%d reviews) · ✓ Verified",
                                    average, count)
                                    : "✓ Verified tutor · No reviews yet");

                            level.setText("Level " +
                                    Math.max(1, profile.optInt("level", 1)));

                            bio.setText(clean(
                                    profile.optString("tutor_bio", ""),
                                    "This tutor hasn't added a bio yet."));

                            experience.setText(clean(
                                    profile.optString("tutor_experience", ""),
                                    "Experience not provided."));

                            if (noReviews != null) {
                                noReviews.setText(count == 0
                                        ? "No reviews yet"
                                        : count + " review(s)");
                            }

                            if (reviews != null) {
                                reviews.removeAllViews();

                                TextView info = new TextView(
                                        TutorProfileActivity.this);

                                info.setText(count == 0
                                        ? "This tutor hasn't received reviews yet."
                                        : "Review details are not available here yet.");

                                reviews.addView(info);
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;

                            rating.setText("Unable to load tutor profile.");

                            Toast.makeText(
                                    TutorProfileActivity.this,
                                    message,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null
                        && !value.trim().isEmpty()
                        && !"null".equalsIgnoreCase(value.trim())) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    private String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()
                || "null".equalsIgnoreCase(value.trim())) {
            return fallback;
        }
        return value.trim();
    }
}
