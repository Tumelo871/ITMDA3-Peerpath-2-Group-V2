package com.example.peerpath;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AdminReviewActivity extends Activity {
    private LinearLayout root;
    private LinearLayout content;
    private TextView status;

    private static final String EXTRA_ADMIN_SCREEN = "admin_screen";
    private static final String SCREEN_USERS = "users";
    private static final String EVIDENCE_BUCKET = "tutor-evidence";
    private static final int SIGNED_URL_SECONDS = 120;

    private final OkHttpClient proofHttp = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        buildUi();

        String screen = getIntent().getStringExtra(EXTRA_ADMIN_SCREEN);
        if (SCREEN_USERS.equals(screen)) {
            loadUsers();
        } else {
            loadApplications();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        root.setBackgroundColor(0xFFF5F8FF);

        TextView title = text("PeerPath Admin", 25, 0xFF082B6F, true);
        root.addView(title);
        root.addView(text("Tutor applications and user access", 14, 0xFF52627A, false));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button applications = button("Tutor applications");
        applications.setOnClickListener(v -> loadApplications());

        Button users = button("Manage users");
        users.setOnClickListener(v -> loadUsers());

        actions.addView(applications, weightParams());
        actions.addView(users, weightParams());
        root.addView(actions);

        status = text("Ready", 14, 0xFF52627A, false);
        root.addView(status);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadApplications() {
        setStatus("Loading tutor applications…");
        content.removeAllViews();

        SupabaseApi.getTutorApplications(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    content.removeAllViews();
                    int pendingCount = 0;

                    for (int i = 0; i < data.length(); i++) {
                        JSONObject app = data.optJSONObject(i);
                        if (app == null) continue;

                        String appStatus = app.optString("status", "unknown");
                        if ("pending".equalsIgnoreCase(appStatus)
                                || "pending_verification".equalsIgnoreCase(appStatus)) {
                            addApplicationCard(app);
                            pendingCount++;
                        }
                    }

                    status.setText(pendingCount == 0
                            ? "No pending tutor applications were returned."
                            : "Pending applications: " + pendingCount);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> showError("Could not load applications: " + safe(message)));
            }
        });
    }

    private void addApplicationCard(JSONObject app) {
        String applicationId = app.optString("id", "");
        String tutorId = app.optString("tutor_id", "");
        String modules = app.optString("module_codes", "Not supplied");
        String evidence = app.optString("evidence_path", "").trim();
        String tutorName = app.optString("tutor_name", "Unknown tutor");
        String tutorEmail = app.optString("tutor_email", "");
        String appStatus = app.optString("status", "pending");

        LinearLayout card = card();
        card.addView(text("Tutor application", 18, 0xFF082B6F, true));
        card.addView(text("Tutor: " + tutorName, 15, 0xFF17243A, false));
        if (!tutorEmail.trim().isEmpty()) {
            card.addView(text("Email: " + tutorEmail, 13, 0xFF52627A, false));
        }
        card.addView(text("Status: " + appStatus, 14, 0xFF082B6F, true));
        card.addView(text("Modules: " + modules, 15, 0xFF17243A, false));

        boolean hasEvidencePath = !evidence.isEmpty();
        if (hasEvidencePath) {
            Button viewProof = button("View Academic Proof");
            viewProof.setOnClickListener(v -> openAcademicProof(evidence));
            card.addView(viewProof);
            card.addView(text("Evidence file is attached. Open it before making a decision.",
                    13, 0xFF52627A, false));
        } else {
            card.addView(text(
                    "ACADEMIC PROOF MISSING — do not approve this application. The server must also reject approval.",
                    13, 0xFFB00020, true));
        }

        if (applicationId.isEmpty() || tutorId.isEmpty()) {
            card.addView(text(
                    "This record is missing its application ID or tutor ID, so it cannot be reviewed.",
                    13, 0xFFB00020, false));
        } else {
            LinearLayout buttons = new LinearLayout(this);
            Button approve = button("Approve");
            Button reject = button("Reject");

            // UI-level safeguard only; the database review RPC must enforce this too.
            approve.setEnabled(hasEvidencePath);
            reject.setEnabled(hasEvidencePath);
            approve.setOnClickListener(v -> {
                if (!hasEvidencePath) {
                    showError("Cannot review: no academic proof was submitted.");
                } else {
                    confirmReview(applicationId, tutorId, true);
                }
            });
            reject.setOnClickListener(v -> {
                if (!hasEvidencePath) {
                    showError("This application has no academic proof. Resolve the missing evidence first.");
                } else {
                    confirmReview(applicationId, tutorId, false);
                }
            });

            buttons.addView(approve, weightParams());
            buttons.addView(reject, weightParams());
            card.addView(buttons);
        }

        content.addView(card);
    }

    /**
     * Creates a short-lived signed URL for the private tutor-evidence bucket.
     * The current signed-in admin must have Storage SELECT permission.
     */
    private void openAcademicProof(String evidencePath) {
        if (evidencePath == null || evidencePath.trim().isEmpty()) {
            showError("No academic proof was submitted.");
            return;
        }

        String accessToken = SupabaseApi.token();
        if (accessToken == null || accessToken.trim().isEmpty()) {
            showError("Your admin session has expired. Please sign in again.");
            return;
        }

        setStatus("Preparing secure academic proof link…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("expiresIn", SIGNED_URL_SECONDS);

                String url = SupabaseConfig.URL.trim()
                        + "/storage/v1/object/sign/"
                        + EVIDENCE_BUCKET + "/" + encodeStoragePath(evidencePath);

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY.trim())
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(),
                                MediaType.parse("application/json; charset=utf-8")))
                        .build();

                String responseText;
                try (Response response = proofHttp.newCall(request).execute()) {
                    responseText = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) {
                        throw new IOException("Storage returned HTTP " + response.code()
                                + ". Check that the bucket is private and admins have SELECT access.");
                    }
                }

                JSONObject responseJson = new JSONObject(responseText);
                String signed = responseJson.optString("signedURL",
                        responseJson.optString("signedUrl", ""));
                if (signed.trim().isEmpty()) {
                    throw new IOException("Supabase did not return a signed URL.");
                }

                final String finalUrl;
                if (signed.startsWith("https://") || signed.startsWith("http://")) {
                    finalUrl = signed;
                } else if (signed.startsWith("/storage/v1/")) {
                    finalUrl = SupabaseConfig.URL.trim() + signed;
                } else if (signed.startsWith("/")) {
                    finalUrl = SupabaseConfig.URL.trim() + "/storage/v1" + signed;
                } else {
                    finalUrl = SupabaseConfig.URL.trim() + "/storage/v1/" + signed;
                }

                runOnUiThread(() -> {
                    setStatus("Opening academic proof…");
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl));
                        startActivity(Intent.createChooser(intent, "Open academic proof"));
                    } catch (ActivityNotFoundException e) {
                        showError("No app can open this file. Install a PDF or office document viewer.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("Could not open academic proof: "
                        + (e.getMessage() == null ? "Unknown Storage error." : e.getMessage())));
            }
        }).start();
    }

    private String encodeStoragePath(String path) throws Exception {
        String[] segments = path.split("/");
        StringBuilder result = new StringBuilder();
        for (String segment : segments) {
            if (result.length() > 0) result.append("/");
            result.append(URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
                    .replace("+", "%20"));
        }
        return result.toString();
    }

    private void confirmReview(String applicationId, String tutorId, boolean approve) {
        String action = approve ? "approve" : "reject";

        new AlertDialog.Builder(this)
                .setTitle((approve ? "Approve" : "Reject") + " tutor?")
                .setMessage("Are you sure you want to " + action + " this tutor application?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(approve ? "Approve" : "Reject", (dialog, which) -> {
                    setStatus("Submitting review…");

                    SupabaseApi.adminReviewApplication(
                            applicationId,
                            tutorId,
                            approve,
                            "",
                            new SupabaseApi.EmptyCallback() {
                                @Override
                                public void onSuccess() {
                                    runOnUiThread(() -> {
                                        Toast.makeText(
                                                AdminReviewActivity.this,
                                                "Application " + (approve ? "approved." : "rejected."),
                                                Toast.LENGTH_LONG
                                        ).show();
                                        loadApplications();
                                    });
                                }

                                @Override
                                public void onError(String message) {
                                    runOnUiThread(() -> showError("Review failed: " + safe(message)));
                                }
                            });
                })
                .show();
    }

    private void loadUsers() {
        setStatus("Loading registered users…");
        content.removeAllViews();

        SupabaseApi.adminListUsers(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    content.removeAllViews();
                    status.setText("Registered profiles: " + data.length());

                    if (data.length() == 0) {
                        content.addView(text(
                                "No profiles were returned by the admin users function.",
                                15, 0xFF52627A, false));
                        return;
                    }

                    for (int i = 0; i < data.length(); i++) {
                        JSONObject user = data.optJSONObject(i);
                        if (user != null) addUserCard(user);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> showError("Could not load users: " + safe(message)));
            }
        });
    }

    private void addUserCard(JSONObject user) {
        String id = user.optString("id", user.optString("user_id", ""));
        String email = user.optString("email", "");
        String name = user.optString("full_name", "User");
        String role = user.optString("role", "unknown");
        String verification = user.optString("verification_status", "unknown");
        boolean suspended = user.optBoolean("is_suspended", false);

        LinearLayout card = card();
        card.addView(text(name + " (" + role + ")", 17, 0xFF082B6F, true));
        if (!email.trim().isEmpty()) {
            card.addView(text(email, 14, 0xFF17243A, false));
        }
        card.addView(text("Verification: " + verification, 13, 0xFF52627A, false));

        if (!"admin".equalsIgnoreCase(role) && !id.trim().isEmpty()) {
            Button suspend = button(suspended ? "Restore access" : "Suspend user");
            suspend.setOnClickListener(v -> confirmSuspend(id, name, !suspended));
            card.addView(suspend);
        } else if ("admin".equalsIgnoreCase(role)) {
            card.addView(text("Administrator account", 13, 0xFF52627A, false));
        }

        content.addView(card);
    }

    private void confirmSuspend(String userId, String name, boolean suspend) {
        new AlertDialog.Builder(this)
                .setTitle(suspend ? "Suspend user?" : "Restore access?")
                .setMessage((suspend ? "Temporarily suspend " : "Restore access for ") + name + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) ->
                        SupabaseApi.adminSetSuspended(
                                userId,
                                suspend,
                                new SupabaseApi.EmptyCallback() {
                                    @Override
                                    public void onSuccess() {
                                        runOnUiThread(() -> {
                                            Toast.makeText(
                                                    AdminReviewActivity.this,
                                                    suspend ? "User suspended." : "Access restored.",
                                                    Toast.LENGTH_LONG
                                            ).show();
                                            loadUsers();
                                        });
                                    }

                                    @Override
                                    public void onError(String message) {
                                        runOnUiThread(() -> showError("Action failed: " + safe(message)));
                                    }
                                }))
                .show();
    }

    private void setStatus(String value) {
        if (status != null) status.setText(value);
    }

    private void showError(String value) {
        setStatus(value);
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, dp(4));
        box.setLayoutParams(params);
        return box;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty()
                ? "Please try again." : value.trim();
    }
}
