package com.example.peerpath;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

/** Admin screen for reviewing tutor applications, listing users and logging out. */
public class AdminReviewActivity extends Activity {
    private LinearLayout content;
    private TextView title;
    private TextView status;
    private static final String SCREEN_APPLICATIONS = "applications";
    private static final String SCREEN_USERS = "users";
    private String currentScreen = SCREEN_APPLICATIONS;
    private boolean requestInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());

        if (!SupabaseApi.isLoggedIn()) {
            goToLogin();
            return;
        }

        String requested = getIntent().getStringExtra("admin_screen");
        if (SCREEN_USERS.equals(requested)) {
            currentScreen = SCREEN_USERS;
        }
        buildScreen();
        if (SCREEN_USERS.equals(currentScreen)) {
            loadUsers();
        } else {
            loadApplications();
        }
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(24));
        root.setBackgroundColor(0xFFF5F8FF);

        root.addView(makeText("PeerPath Admin", 25, 0xFF082B6F, true));
        title = makeText("Tutor applications", 19, 0xFF17243A, true);
        root.addView(title);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button applications = makeButton("Tutor applications");
        applications.setOnClickListener(v -> loadApplications());
        Button users = makeButton("Manage users");
        users.setOnClickListener(v -> loadUsers());
        nav.addView(applications, weightParams());
        nav.addView(users, weightParams());
        root.addView(nav);

        Button logout = makeButton("Log out");
        logout.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Log out?")
                .setMessage("You will be signed out of PeerPath.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (dialog, which) -> {
                    SupabaseApi.clearSession();
                    goToLogin();
                })
                .show());
        root.addView(logout);

        status = makeText("Ready", 14, 0xFF52627A, false);
        root.addView(status);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadApplications() {
        currentScreen = SCREEN_APPLICATIONS;
        title.setText("Pending tutor applications");
        if (requestInProgress) return;
        requestInProgress = true;
        status.setText("Loading tutor applications...");
        content.removeAllViews();

        SupabaseApi.getTutorApplications(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    requestInProgress = false;
                    content.removeAllViews();
                    int count = 0;
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject application = data.optJSONObject(i);
                            if (application == null) continue;
                            String applicationStatus = application.optString("status", "").trim();
                            if (isPending(applicationStatus)) {
                                addApplicationCard(application);
                                count++;
                            }
                        }
                    }
                    status.setText(count == 0
                            ? "No pending applications returned."
                            : "Pending applications: " + count);
                    if (count == 0) {
                        addMessage("No pending applications were returned by Supabase. Check the admin RPC and the status values in tutor_applications.");
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    requestInProgress = false;
                    status.setText("Could not load applications.");
                    showError(message);
                });
            }
        });
    }

    private boolean isPending(String value) {
        return "pending".equalsIgnoreCase(value)
                || "pending_verification".equalsIgnoreCase(value)
                || "pending verification".equalsIgnoreCase(value)
                || "submitted".equalsIgnoreCase(value);
    }

    private void addApplicationCard(JSONObject application) {
        String applicationId = first(application, "id", "application_id");
        String tutorId = first(application, "tutor_id", "user_id");
        String name = first(application, "tutor_name", "full_name", "name", "display_name");
        String email = first(application, "tutor_email", "email");
        String modules = first(application, "module_codes", "modules");
        String evidence = first(application, "evidence_path", "evidence_url");

        LinearLayout card = makeCard();
        card.addView(makeText("Tutor application", 18, 0xFF082B6F, true));
        addLine(card, "Tutor: " + (name.isEmpty() ? "Name not provided" : name));
        if (!email.isEmpty()) addLine(card, "Email: " + email);
        addLine(card, "Modules: " + (modules.isEmpty() ? "Not supplied" : modules));
        addLine(card, "Status: " + application.optString("status", "unknown"));
        if (!evidence.isEmpty()) addLine(card, "Evidence: " + evidence);

        if (applicationId.isEmpty() || tutorId.isEmpty()) {
            addLine(card, "Cannot review: the admin RPC must return both application id and tutor_id.");
        } else {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button approve = makeButton("Approve");
            Button reject = makeButton("Reject");
            approve.setOnClickListener(v -> confirmReview(application, true));
            reject.setOnClickListener(v -> confirmReview(application, false));
            actions.addView(approve, weightParams());
            actions.addView(reject, weightParams());
            card.addView(actions);
        }
        content.addView(card);
    }

    private void confirmReview(JSONObject application, boolean approve) {
        String applicationId = first(application, "id", "application_id");
        String tutorId = first(application, "tutor_id", "user_id");
        if (applicationId.isEmpty() || tutorId.isEmpty()) {
            showError("Application ID or tutor ID is missing from the Supabase response.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(approve ? "Approve tutor?" : "Reject application?")
                .setMessage(approve ? "Approve this tutor application?" : "Reject this tutor application?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(approve ? "Approve" : "Reject", (dialog, which) -> {
                    status.setText("Submitting review...");
                    // IMPORTANT: this API method requires all five arguments.
                    SupabaseApi.adminReviewApplication(
                            applicationId,
                            tutorId,
                            approve,
                            approve ? "Approved by administrator" : "Application rejected",
                            new SupabaseApi.EmptyCallback() {
                                @Override
                                public void onSuccess() {
                                    runOnUiThread(() -> {
                                        Toast.makeText(AdminReviewActivity.this,
                                                approve ? "Tutor approved." : "Application rejected.",
                                                Toast.LENGTH_LONG).show();
                                        requestInProgress = false;
                                        loadApplications();
                                    });
                                }

                                @Override
                                public void onError(String message) {
                                    runOnUiThread(() -> {
                                        status.setText("Review failed.");
                                        showError(message);
                                    });
                                }
                            });
                })
                .show();
    }

    private void loadUsers() {
        currentScreen = SCREEN_USERS;
        title.setText("Manage users");
        if (requestInProgress) return;
        requestInProgress = true;
        status.setText("Loading registered users...");
        content.removeAllViews();

        SupabaseApi.adminListUsers(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    requestInProgress = false;
                    content.removeAllViews();
                    if (data == null || data.length() == 0) {
                        status.setText("No users returned.");
                        addMessage("The admin users RPC returned an empty list.");
                        return;
                    }
                    status.setText("Registered users: " + data.length());
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject user = data.optJSONObject(i);
                        if (user != null) addUserCard(user);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    requestInProgress = false;
                    status.setText("Could not load users.");
                    showError(message);
                });
            }
        });
    }

    private void addUserCard(JSONObject user) {
        LinearLayout card = makeCard();
        String name = first(user, "full_name", "name", "display_name", "username");
        String email = first(user, "email", "user_email");
        String id = first(user, "id", "user_id");
        String role = first(user, "role", "user_role");
        String state = first(user, "is_suspended", "suspended", "status");

        card.addView(makeText(name.isEmpty() ? "Registered user" : name,
                17, 0xFF082B6F, true));
        if (!email.isEmpty()) addLine(card, "Email: " + email);
        if (!id.isEmpty()) addLine(card, "User ID: " + id);
        if (!role.isEmpty()) addLine(card, "Role: " + role);
        if (!state.isEmpty()) addLine(card, "Status: " + state);
        content.addView(card);
    }

    private String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private void addLine(LinearLayout parent, String value) {
        parent.addView(makeText(value, 14, 0xFF52627A, false));
    }

    private void addMessage(String value) {
        LinearLayout messageCard = makeCard();
        messageCard.addView(makeText(value, 15, 0xFF52627A, false));
        content.addView(messageCard);
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Admin request failed")
                .setMessage(message == null || message.trim().isEmpty() ? "Unknown error." : message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private TextView makeText(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(6));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), dp(4), dp(3), dp(4));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

