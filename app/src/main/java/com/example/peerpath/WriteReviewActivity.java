
package com.example.peerpath;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;

public class WriteReviewActivity extends Activity {

    private String sessionId;
    private RatingBar ratingBar;
    private EditText commentInput;
    private CheckBox anonymousCheck;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseApi.init(getApplicationContext());

        sessionId = getIntent().getStringExtra("session_id");

        if (sessionId == null || sessionId.trim().isEmpty()) {
            Toast.makeText(this,
                    "The tutoring session ID is missing.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildScreen();
    }

    private void buildScreen() {
        int padding = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(0xFFF5F8FF);

        TextView title = text("Review your tutoring session",
                24, 0xFF082B6F, true);
        root.addView(title);

        TextView description = text(
                "Share honest feedback about your tutoring experience.",
                15, 0xFF52627A, false);
        root.addView(description);

        root.addView(text("Your rating", 18, 0xFF17243A, true));

        ratingBar = new RatingBar(this);
        ratingBar.setNumStars(5);
        ratingBar.setStepSize(1f);
        ratingBar.setRating(5f);
        root.addView(ratingBar);

        root.addView(text("Your comments (optional)",
                17, 0xFF17243A, true));

        commentInput = new EditText(this);
        commentInput.setHint(
                "What went well? What could be improved?");
        commentInput.setGravity(Gravity.TOP);
        commentInput.setMinLines(4);
        commentInput.setMaxLines(8);
        commentInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(commentInput);

        anonymousCheck = new CheckBox(this);
        anonymousCheck.setText(
                "Post anonymously (hide my name from other users)");
        anonymousCheck.setTextColor(0xFF17243A);
        root.addView(anonymousCheck);

        TextView privacy = text(
                "Your choice applies to this review only. "
                        + "Authorised administrators may access your identity "
                        + "when investigating a report.",
                13, 0xFF52627A, false);
        root.addView(privacy);

        submitButton = new Button(this);
        submitButton.setText("Submit review");
        root.addView(submitButton);

        Button cancelButton = new Button(this);
        cancelButton.setText("Cancel");
        root.addView(cancelButton);

        cancelButton.setOnClickListener(v -> finish());
        submitButton.setOnClickListener(v -> submit());

        setContentView(root);
    }

    private void submit() {
        if (!SupabaseApi.isLoggedIn()) {
            Toast.makeText(this,
                    "Please sign in again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        float rating = ratingBar.getRating();

        if (rating < 1 || rating > 5) {
            Toast.makeText(this,
                    "Please select a rating from 1 to 5.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String comment = commentInput.getText()
                .toString().trim();

        if (comment.length() > 3000) {
            commentInput.setError(
                    "Keep your comment under 3,000 characters.");
            return;
        }

        submitButton.setEnabled(false);
        submitButton.setText("Submitting...");

        SupabaseApi.submitReview(
                sessionId,
                rating,
                comment,
                anonymousCheck.isChecked(),
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray data) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            Toast.makeText(
                                    WriteReviewActivity.this,
                                    "Thank you! Your review was submitted.",
                                    Toast.LENGTH_LONG
                            ).show();

                            setResult(Activity.RESULT_OK);
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            submitButton.setEnabled(true);
                            submitButton.setText("Submit review");

                            Toast.makeText(
                                    WriteReviewActivity.this,
                                    message == null
                                            ? "Could not submit your review."
                                            : message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                });
    }

    private TextView text(
            String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        view.setPadding(0, dp(10), 0, dp(10));
        return view;
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density);
    }
}
