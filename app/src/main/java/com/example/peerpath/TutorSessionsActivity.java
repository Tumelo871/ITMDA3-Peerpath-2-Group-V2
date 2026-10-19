package com.example.peerpath;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

public class TutorSessionsActivity extends AppCompatActivity {

    private LinearLayout list;
    private TextView empty;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_tutor_sessions);

        list = findViewById(R.id.tutorSessionsContainer);
        empty = findViewById(R.id.tvEmptyTutorSessions);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh the session list when returning to this screen.
        if (list != null && !loading) {
            load();
        }
    }

    private void load() {
        if (loading) {
            return;
        }

        loading = true;
        list.removeAllViews();

        empty.setVisibility(View.VISIBLE);
        empty.setText("Loading your sessions...");

        SupabaseApi.getTutorAcceptedSessions(
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray sessions) {
                        runOnUiThread(() -> {
                            loading = false;

                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            render(sessions);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            loading = false;

                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            list.removeAllViews();
                            empty.setVisibility(View.VISIBLE);
                            empty.setText(
                                    message == null
                                            ? "Could not load sessions. Please try again."
                                            : message
                            );

                            Toast.makeText(
                                    TutorSessionsActivity.this,
                                    "Unable to load sessions.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }
                }
        );
    }

    private void render(JSONArray sessions) {
        list.removeAllViews();

        if (sessions == null || sessions.length() == 0) {
            empty.setVisibility(View.VISIBLE);
            empty.setText(
                    "No accepted sessions yet.\n\n"
                            + "When you accept a learner's request, "
                            + "your session will appear here."
            );
            return;
        }

        empty.setVisibility(View.GONE);

        for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);

            if (session == null) {
                continue;
            }

            addSessionCard(session);
        }

        if (list.getChildCount() == 0) {
            empty.setVisibility(View.VISIBLE);
            empty.setText("No sessions could be displayed.");
        }
    }

    private void addSessionCard(JSONObject session) {
        LinearLayout card = Ui.card(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                Ui.dp(this, 16),
                Ui.dp(this, 16),
                Ui.dp(this, 16),
                Ui.dp(this, 16)
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                0,
                0,
                Ui.dp(this, 14)
        );

        card.setLayoutParams(cardParams);

        String sessionId = session.optString("id", "");
        String subject = session.optString("subject", "Tutoring session");
        String date = session.optString("session_date", "Date not set");
        String time = session.optString("session_time", "Time not set");
        String status = session.optString("status", "accepted");

        card.addView(
                Ui.text(
                        this,
                        subject,
                        18,
                        R.color.text_primary,
                        true
                )
        );

        addDetail(
                card,
                "Date: " + date
        );

        addDetail(
                card,
                "Time: " + time
        );

        addDetail(
                card,
                "Status: " + status
        );

        MaterialButton completeButton =
                new MaterialButton(this);

        completeButton.setText("Mark Complete");
        completeButton.setAllCaps(false);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        buttonParams.topMargin = Ui.dp(this, 12);
        completeButton.setLayoutParams(buttonParams);

        boolean completed = "completed".equalsIgnoreCase(status);

        completeButton.setEnabled(
                !completed && !sessionId.trim().isEmpty()
        );

        if (completed) {
            completeButton.setText("Session Completed");
        }

        card.addView(completeButton);

        completeButton.setOnClickListener(v -> {
            if (sessionId.trim().isEmpty()) {
                Toast.makeText(
                        this,
                        "The session ID is missing.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Complete session?")
                    .setMessage(
                            "Are you sure you want to mark this "
                                    + "tutoring session as completed?"
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Confirm", (dialog, which) ->
                            markCompleted(sessionId, completeButton))
                    .show();
        });

        list.addView(card);
    }

    private void addDetail(LinearLayout card, String text) {
        TextView detail = Ui.text(
                this,
                text,
                14,
                R.color.text_secondary,
                false
        );

        Ui.add(card, detail, 6);
    }

    private void markCompleted(
            String sessionId,
            MaterialButton button) {

        button.setEnabled(false);
        button.setText("Updating...");

        SupabaseApi.updateSessionStatus(
                sessionId,
                "completed",
                new SupabaseApi.EmptyCallback() {

                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            Toast.makeText(
                                    TutorSessionsActivity.this,
                                    "Session marked as completed.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            load();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            button.setEnabled(true);
                            button.setText("Mark Complete");

                            Toast.makeText(
                                    TutorSessionsActivity.this,
                                    "Could not update session: "
                                            + (message == null
                                            ? "Please try again."
                                            : message),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }
}
