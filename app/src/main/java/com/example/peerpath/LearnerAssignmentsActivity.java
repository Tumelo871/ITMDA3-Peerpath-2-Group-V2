
package com.example.peerpath;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class LearnerAssignmentsActivity extends AppCompatActivity {

    private LinearLayout assignmentContainer;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseApi.init(getApplicationContext());

        setContentView(R.layout.activity_learner_assignments);

        assignmentContainer = findViewById(R.id.assignmentContainer);
        progressBar = findViewById(R.id.progressAssignments);
        tvEmpty = findViewById(R.id.tvEmptyAssignments);

        findViewById(R.id.btnRefreshAssignments)
                .setOnClickListener(v -> loadAssignments());

        findViewById(R.id.btnBackAssignments)
                .setOnClickListener(v -> finish());

        loadAssignments();
    }

    // Refresh assignments when returning to this screen.
    @Override
    protected void onResume() {
        super.onResume();

        if (assignmentContainer != null) {
            loadAssignments();
        }
    }

    private void loadAssignments() {

        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        assignmentContainer.removeAllViews();

        if (!SupabaseApi.isLoggedIn()) {

            progressBar.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);

            tvEmpty.setText(
                    "Your session has expired. Please sign in again."
            );

            return;
        }

        SupabaseApi.getMyAssignments(
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray data) {

                        runOnUiThread(() -> {

                            if (isFinishing() || isDestroyed()) return;

                            progressBar.setVisibility(View.GONE);
                            assignmentContainer.removeAllViews();

                            if (data == null || data.length() == 0) {

                                tvEmpty.setVisibility(View.VISIBLE);

                                tvEmpty.setText(
                                        "You don't have any assignments yet.\n"
                                                + "Your tutor's published assessments "
                                                + "will appear here."
                                );

                                return;
                            }

                            tvEmpty.setVisibility(View.GONE);

                            for (int i = 0; i < data.length(); i++) {

                                JSONObject assignment =
                                        data.optJSONObject(i);

                                if (assignment != null) {
                                    addAssignmentCard(assignment);
                                }
                            }

                            if (assignmentContainer.getChildCount() == 0) {

                                tvEmpty.setVisibility(View.VISIBLE);

                                tvEmpty.setText(
                                        "No assignments are currently available."
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            if (isFinishing() || isDestroyed()) return;

                            progressBar.setVisibility(View.GONE);
                            tvEmpty.setVisibility(View.VISIBLE);

                            tvEmpty.setText(
                                    "Could not load your assignments.\n"
                                            + message
                            );
                        });
                    }
                }
        );
    }

    private void addAssignmentCard(JSONObject assignment) {

        String assignmentId =
                assignment.optString("id", "").trim();

        String title =
                assignment.optString("title", "Untitled assessment");

        String subject =
                assignment.optString("subject", "Subject not specified");

        String instructions =
                assignment.optString("instructions", "");

        String dueAt =
                assignment.optString("due_at", "No deadline specified");

        String assessmentType =
                assignment.optString("assessment_type", "assessment");

        String maxScore =
                assignment.optString("max_score", "0");

        boolean onlineAllowed =
                assignment.optBoolean("allow_online_answers", false);

        boolean fileAllowed =
                assignment.optBoolean("allow_file_submission", false);

        // Find the learner's latest submission.
        JSONObject submission = getLatestSubmission(assignment);

        boolean hasSubmitted = submission != null;

        String status = hasSubmitted
                ? submission.optString("status", "submitted")
                : "";

        boolean isMarked =
                "marked".equalsIgnoreCase(status);

        boolean resubmitRequested =
                "resubmit_requested".equalsIgnoreCase(status);

        // Create assignment card.
        LinearLayout card = new LinearLayout(this);

        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundColor(0xFFFFFFFF);
        card.setElevation(dp(3));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(cardParams);

        // Assignment information.
        card.addView(makeText(
                title, 19, 0xFF082D68, true
        ));

        card.addView(makeText(
                subject, 14, 0xFF536783, true
        ));

        card.addView(makeText(
                "Assessment type: "
                        + assessmentType.replace("_", " "),
                13, 0xFF536783, false
        ));

        card.addView(makeText(
                "Total marks: " + maxScore,
                14, 0xFF075CE5, true
        ));

        card.addView(makeText(
                "Deadline: " + dueAt,
                13, 0xFF536783, false
        ));

        String methods =
                (onlineAllowed ? "In-app answers" : "")
                        + (onlineAllowed && fileAllowed ? " and " : "")
                        + (fileAllowed ? "PDF / Word upload" : "");

        if (methods.isEmpty()) {
            methods = "Contact your tutor";
        }

        card.addView(makeText(
                "Submission: " + methods,
                13, 0xFF536783, false
        ));

        if (!instructions.trim().isEmpty()) {

            TextView instructionView = makeText(
                    "Instructions: " + instructions,
                    13, 0xFF344054, false
            );

            instructionView.setMaxLines(3);
            card.addView(instructionView);
        }

        // Submission status.
        TextView statusView = makeText(
                "",
                14,
                0xFF536783,
                true
        );

        statusView.setPadding(0, dp(12), 0, dp(6));

        Button actionButton = new Button(this);

        actionButton.setAllCaps(false);
        actionButton.setTextColor(0xFFFFFFFF);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                );

        buttonParams.setMargins(0, dp(12), 0, 0);

        // CASE 1: Assessment not submitted.
        if (!hasSubmitted) {

            statusView.setText("Not submitted yet");

            actionButton.setText(
                    onlineAllowed
                            ? "Open assessment"
                            : fileAllowed
                              ? "Document submission"
                              : "Unavailable"
            );

            actionButton.setBackgroundTintList(
                    ColorStateList.valueOf(0xFF075CE5)
            );

            boolean canOpen =
                    onlineAllowed && !assignmentId.isEmpty();

            actionButton.setEnabled(canOpen);

            actionButton.setOnClickListener(v -> {

                if (!canOpen) {

                    Toast.makeText(
                            this,
                            "This assessment cannot be opened here.",
                            Toast.LENGTH_LONG
                    ).show();

                    return;
                }

                openAssessment(assignmentId);
            });
        }

        // CASE 2: Assessment has been marked.
        else if (isMarked) {

            statusView.setText("✓ Assessment marked");
            statusView.setTextColor(0xFF16803C);

            double total = getMark(
                    submission,
                    "graded_total",
                    "score"
            );

            double maximum = getMark(
                    submission,
                    "graded_max_score",
                    "max_score"
            );

            actionButton.setText("View result");

            actionButton.setBackgroundTintList(
                    ColorStateList.valueOf(0xFF16803C)
            );

            actionButton.setOnClickListener(v ->
                    showResult(title, submission, total, maximum)
            );
        }

        // CASE 3: Tutor requested another submission.
        else if (resubmitRequested) {

            statusView.setText(
                    "Tutor has requested a resubmission."
            );

            statusView.setTextColor(0xFFB7791F);

            actionButton.setText("Contact your tutor");

            actionButton.setEnabled(false);
        }

        // CASE 4: Submitted but awaiting marking.
        else {

            statusView.setText(
                    "✓ Submitted successfully\n"
                            + "Awaiting tutor marking"
            );

            statusView.setTextColor(0xFFB7791F);

            actionButton.setText(
                    "Submitted — awaiting marking"
            );

            actionButton.setEnabled(false);

            actionButton.setBackgroundTintList(
                    ColorStateList.valueOf(0xFF8795A8)
            );
        }

        card.addView(statusView);
        card.addView(actionButton, buttonParams);

        assignmentContainer.addView(card);
    }

    // Get the latest submission associated with an assignment.
    private JSONObject getLatestSubmission(JSONObject assignment) {

        JSONArray submissions = assignment.optJSONArray(
                "peerpath_assignment_submissions"
        );

        if (submissions == null || submissions.length() == 0) {
            return null;
        }

        JSONObject latest = null;
        String latestDate = "";

        for (int i = 0; i < submissions.length(); i++) {

            JSONObject item = submissions.optJSONObject(i);

            if (item == null) continue;

            String date = item.optString("submitted_at", "");

            if (latest == null || date.compareTo(latestDate) > 0) {

                latest = item;
                latestDate = date;
            }
        }

        return latest;
    }

    // Open an assessment that has not been submitted.
    private void openAssessment(String assignmentId) {

        Intent intent = new Intent(
                LearnerAssignmentsActivity.this,
                AssessmentDetailsActivity.class
        );

        intent.putExtra("assignment_id", assignmentId);

        startActivity(intent);
    }

    // Display the learner's result.
    private void showResult(
            String title,
            JSONObject submission,
            double total,
            double maximum) {

        String feedback = submission.optString(
                "tutor_feedback", ""
        );

        String submittedAt = submission.optString(
                "submitted_at", "Not available"
        );

        String percentage = maximum > 0
                ? formatNumber((total / maximum) * 100) + "%"
                : "Not available";

        String message =
                "Assessment: " + title
                        + "\n\nYour result: "
                        + formatNumber(total)
                        + " / "
                        + formatNumber(maximum)
                        + "\nPercentage: " + percentage
                        + "\n\nSubmitted: " + submittedAt
                        + "\n\nTutor feedback: "
                        + (feedback.trim().isEmpty()
                        ? "No additional feedback provided."
                        : feedback);

        new AlertDialog.Builder(this)
                .setTitle("Assessment Result")
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    private double getMark(
            JSONObject submission,
            String primary,
            String fallback) {

        Object value = submission.opt(primary);

        if (value != null && !JSONObject.NULL.equals(value)) {
            return submission.optDouble(primary, 0);
        }

        return submission.optDouble(fallback, 0);
    }

    private String formatNumber(double value) {

        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }

        return String.format(
                Locale.getDefault(),
                "%.2f",
                value
        );
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
        text.setPadding(0, dp(3), 0, dp(5));

        if (bold) {
            text.setTypeface(null, Typeface.BOLD);
        }

        return text;
    }

    private int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }
}
