package com.example.peerpath;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

public class TutorRequestsActivity extends AppCompatActivity {

    private static final String TAG = "TutorRequests";

    private LinearLayout list;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_tutor_requests);

        list = findViewById(R.id.requestContainer);
        empty = findViewById(R.id.tvEmptyRequests);
        View back = findViewById(R.id.btnBack);

        if (list == null || empty == null || back == null) {
            Toast.makeText(this,
                    "Student Requests layout is missing a required view.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        back.setOnClickListener(v -> finish());
        load();
    }

    private void load() {
        empty.setVisibility(View.VISIBLE);
        empty.setText("Loading student requests...");
        list.removeAllViews();

        SupabaseApi.getPendingTutorRequests(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) render(data);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    list.removeAllViews();
                    empty.setVisibility(View.VISIBLE);
                    empty.setText("Could not load requests: " + safe(message));
                });
            }
        });
    }

    private void render(JSONArray data) {
        list.removeAllViews();

        if (data == null || data.length() == 0) {
            empty.setVisibility(View.VISIBLE);
            empty.setText("No pending requests.");
            return;
        }

        empty.setVisibility(View.GONE);

        for (int i = 0; i < data.length(); i++) {
            JSONObject session = data.optJSONObject(i);
            if (session == null) continue;

            String sessionId = session.optString("id", "").trim();
            String subject = session.optString("subject", "Tutoring");
            String date = session.optString("session_date", "");
            String time = session.optString("session_time", "");
            String message = session.optString("learner_message", "No message provided.");

            LinearLayout card = Ui.card(this);
            card.setOrientation(LinearLayout.VERTICAL);
            int padding = Ui.dp(this, 16);
            card.setPadding(padding, padding, padding, padding);

            TextView learnerName = Ui.text(this, "Loading learner name...",
                    17, R.color.text_primary, true);
            Ui.add(card, learnerName, 0);

            if (sessionId.isEmpty()) {
                learnerName.setText("Learner name unavailable (missing session ID)");
            } else {
                loadLearnerName(sessionId, learnerName);
            }

            Ui.add(card, Ui.text(this, "📚 " + subject,
                    18, R.color.text_primary, true), 8);
            Ui.add(card, Ui.text(this, date + " at " + time,
                    14, R.color.text_secondary, false), 5);
            Ui.add(card, Ui.text(this, message,
                    14, R.color.text_secondary, false), 5);

            MaterialButton accept = new MaterialButton(this);
            accept.setText("Accept");
            card.addView(accept);

            MaterialButton reject = new MaterialButton(this);
            reject.setText("Reject");
            card.addView(reject);

            accept.setOnClickListener(v -> {
                accept.setEnabled(false);
                reject.setEnabled(false);
                update(sessionId, "accepted", accept, reject);
            });
            reject.setOnClickListener(v -> {
                accept.setEnabled(false);
                reject.setEnabled(false);
                update(sessionId, "rejected", accept, reject);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = Ui.dp(this, 12);
            list.addView(card, params);
        }

        if (list.getChildCount() == 0) {
            empty.setVisibility(View.VISIBLE);
            empty.setText("No pending requests.");
        }
    }

    private void loadLearnerName(String sessionId, TextView nameView) {
        SupabaseApi.getTutorRequesterName(sessionId, new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray result) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    JSONObject profile = result == null ? null : result.optJSONObject(0);
                    String name = profile == null ? ""
                            : profile.optString("full_name", "").trim();

                    if (name.isEmpty() || "null".equalsIgnoreCase(name)) {
                        nameView.setText("Learner name unavailable");
                    } else {
                        nameView.setText("Student: " + name);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Learner lookup failed for session " + sessionId + ": " + error);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    nameView.setText("Could not load learner name");
                });
            }
        });
    }

    private void update(String sessionId, String status,
                        MaterialButton accept, MaterialButton reject) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            accept.setEnabled(true);
            reject.setEnabled(true);
            Toast.makeText(this, "Session ID is missing.", Toast.LENGTH_LONG).show();
            return;
        }

        SupabaseApi.updateSessionStatus(sessionId, status, new SupabaseApi.EmptyCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(TutorRequestsActivity.this,
                            status.equals("accepted") ? "Session accepted." : "Session rejected.",
                            Toast.LENGTH_SHORT).show();
                    load();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    accept.setEnabled(true);
                    reject.setEnabled(true);
                    Toast.makeText(TutorRequestsActivity.this,
                            "Could not update session: " + safe(message),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String safe(String message) {
        return message == null || message.trim().isEmpty()
                ? "Please try again." : message.trim();
    }
}
