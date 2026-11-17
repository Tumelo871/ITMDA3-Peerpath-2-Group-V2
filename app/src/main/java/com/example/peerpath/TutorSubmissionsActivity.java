
package com.example.peerpath;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class TutorSubmissionsActivity extends Activity {

    private LinearLayout submissionsContainer;
    private TextView txtStatus;
    private Button btnRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutor_submissions);

        SupabaseApi.init(getApplicationContext());

        submissionsContainer = findViewById(R.id.submissionsContainer);
        txtStatus = findViewById(R.id.txtSubmissionStatus);
        btnRefresh = findViewById(R.id.btnRefreshSubmissions);

        btnRefresh.setOnClickListener(v -> loadResults());

        loadResults();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (submissionsContainer != null) {
            loadResults();
        }
    }

    private void loadResults() {

        txtStatus.setText("Loading learner results...");
        submissionsContainer.removeAllViews();

        SupabaseApi.getTutorAssessmentSubmissions(
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray data) {
                        runOnUiThread(() -> displayResults(data));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            txtStatus.setText(
                                    "Could not load results:\n" + message
                            );
                        });
                    }
                }
        );
    }

    private void displayResults(JSONArray data) {

        submissionsContainer.removeAllViews();

        if (data == null || data.length() == 0) {
            txtStatus.setText(
                    "No learner submissions have been received yet."
            );
            return;
        }

        int marked = 0;
        int pending = 0;

        for (int i = 0; i < data.length(); i++) {

            JSONObject item = data.optJSONObject(i);

            if (item == null) continue;

            String status = item.optString("status", "submitted");

            if ("marked".equalsIgnoreCase(status)) {
                marked++;
            } else {
                pending++;
            }

            addResultCard(item);
        }

        txtStatus.setText(
                "Total submissions: " + data.length()
                        + "\nMarked: " + marked
                        + " | Awaiting marking: " + pending
        );
    }

    private void addResultCard(JSONObject item) {

        JSONObject assignment =
                item.optJSONObject("peerpath_assignments");

        JSONObject learner =
                item.optJSONObject("learner");

        String learnerName = learner != null
                ? learner.optString("full_name", "Unknown learner")
                : "Unknown learner";

        String title = assignment != null
                ? assignment.optString("title", "Assessment")
                : "Assessment";

        String subject = assignment != null
                ? assignment.optString("subject", "Subject not specified")
                : "Subject not specified";

        String status = item.optString("status", "submitted");

        double total = getNumber(item, "graded_total", "score");

        double maximum = getNumber(
                item,
                "graded_max_score",
                assignment != null
                        ? assignment.optDouble("max_score", 0)
                        : 0
        );

        String feedback = item.optString("tutor_feedback", "");

        boolean isMarked = "marked".equalsIgnoreCase(status);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundColor(Color.WHITE);
        card.setElevation(dp(3));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(params);

        card.addView(makeText(
                learnerName, 18, 0xFF082D68, true
        ));

        card.addView(makeText(
                "Assessment: " + title, 15, 0xFF344054, true
        ));

        card.addView(makeText(
                "Subject: " + subject, 13, 0xFF64748B, false
        ));

        card.addView(makeText(
                "Attempt: " + item.optInt("attempt_number", 1),
                13, 0xFF64748B, false
        ));

        if (isMarked) {

            double percentage = maximum > 0
                    ? (total / maximum) * 100
                    : 0;

            card.addView(makeText(
                    "MARK: " + format(total) + " / " + format(maximum),
                    20, 0xFF16803C, true
            ));

            card.addView(makeText(
                    "Percentage: " + format(percentage) + "%",
                    15, 0xFF16803C, true
            ));

            card.addView(makeText(
                    "Status: Marked",
                    13, 0xFF16803C, true
            ));

            card.addView(makeText(
                    "Feedback: " +
                            (feedback == null || feedback.trim().isEmpty()
                                    || "null".equalsIgnoreCase(feedback)
                                    ? "No feedback provided."
                                    : feedback),
                    14, 0xFF344054, false
            ));

        } else {

            card.addView(makeText(
                    "Status: Awaiting tutor marking",
                    15, 0xFFB7791F, true
            ));

            if (item.has("graded_total")
                    && !item.isNull("graded_total")) {

                card.addView(makeText(
                        "Automatic marks so far: "
                                + format(total) + " / " + format(maximum),
                        14, 0xFF075CE5, true
                ));
            }
        }

        card.addView(makeText(
                "Submitted: "
                        + item.optString("submitted_at", "Not available"),
                12, 0xFF64748B, false
        ));

        submissionsContainer.addView(card);
    }

    private double getNumber(
            JSONObject object,
            String primary,
            double fallback) {

        if (object.has(primary) && !object.isNull(primary)) {
            return object.optDouble(primary, fallback);
        }

        return fallback;
    }

    private double getNumber(
            JSONObject object,
            String primary,
            String fallback) {

        if (object.has(primary) && !object.isNull(primary)) {
            return object.optDouble(primary, 0);
        }

        return object.optDouble(fallback, 0);
    }

    private String format(double value) {

        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }

        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private TextView makeText(
            String value,
            float size,
            int color,
            boolean bold) {

        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setPadding(0, dp(4), 0, dp(6));

        if (bold) {
            text.setTypeface(null, Typeface.BOLD);
        }

        return text;
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }
}
