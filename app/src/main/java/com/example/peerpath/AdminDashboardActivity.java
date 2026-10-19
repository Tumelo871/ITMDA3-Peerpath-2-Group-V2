package com.example.peerpath;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.peerpath.supabase.SupabaseApi;
import org.json.JSONArray;
import org.json.JSONObject;

/** One admin dashboard. Applications and users render inside this Activity; no second admin screen. */
public class AdminDashboardActivity extends RoleDashboardBaseActivity {
    public static final String SCREEN_APPLICATIONS = "applications";
    public static final String SCREEN_USERS = "users";

    private LinearLayout content;
    private TextView title, status;
    private ProgressBar progress;
    private Button applicationsButton, usersButton;
    private boolean requestInProgress = false;
    private String currentScreen = SCREEN_APPLICATIONS;

    @Override protected int layoutId() { return R.layout.activity_admin_dashboard; }
    @Override protected String requiredRole() { return "admin"; }
    @Override protected boolean requiresVerifiedTutor() { return false; }

    @Override protected void wireRoleActions() {
        SupabaseApi.init(getApplicationContext());
        applicationsButton = findViewById(R.id.btnAdminApplications);
        usersButton = findViewById(R.id.btnAdminUsers);
        status = findViewById(R.id.txtAdminStatus);
        progress = findViewById(R.id.adminProgress);
        content = findViewById(R.id.adminContent);
        title = findViewById(R.id.txtAdminSectionTitle);
        View logout = findViewById(R.id.btnAdminLogout);

        if (applicationsButton != null) applicationsButton.setOnClickListener(v -> loadApplications());
        if (usersButton != null) usersButton.setOnClickListener(v -> loadUsers());
        if (logout != null) logout.setOnClickListener(v -> confirmLogout());

        if (!SupabaseApi.isLoggedIn()) {
            goToLogin();
            return;
        }
        loadApplications();
    }

    @Override protected void onResume() {
        super.onResume();
        // Keep the current dashboard section in sync when returning from another app flow.
        if (content != null && !requestInProgress) {
            if (SCREEN_USERS.equals(currentScreen)) loadUsers(); else loadApplications();
        }
    }

    private void loadApplications() {
        currentScreen = SCREEN_APPLICATIONS;
        setSection("Pending tutor applications");
        if (requestInProgress) return;
        setLoading(true, "Loading tutor applications…");
        content.removeAllViews();
        SupabaseApi.getTutorApplications(new SupabaseApi.JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    setLoading(false, "");
                    content.removeAllViews();
                    int count = 0;
                    int returned = data == null ? 0 : data.length();
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject app = data.optJSONObject(i);
                            if (app != null && isPending(app.optString("status", ""))) {
                                addApplicationCard(app);
                                count++;
                            }
                        }
                    }
                    if (count == 0) {
                        status.setText("No pending applications found (RPC returned " + returned + " rows).");
                        if (returned == 0) {
                            addMessage("The admin RPC returned no rows. Check peerpath_admin_list_tutor_applications and its admin permission check in Supabase.");
                        } else {
                            addMessage("Applications were returned, but none has a pending status. Check the Status values in Supabase; approved/verified/rejected applications are not pending.");
                            addReturnedStatuses(data);
                        }
                    } else {
                        status.setText("Pending applications: " + count);
                    }
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> { setLoading(false, "Could not load applications."); showError(message); });
            }
        });
    }

    private boolean isPending(String value) {
        String s = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        return s.equals("pending") || s.equals("pending_verification") || s.equals("pending_review")
                || s.equals("submitted") || s.equals("awaiting_review") || s.equals("awaiting_approval");
    }

    private void addReturnedStatuses(JSONArray data) {
        if (data == null) return;
        for (int i = 0; i < data.length(); i++) {
            JSONObject app = data.optJSONObject(i);
            if (app != null) addLine(content, "Application status returned: " + app.optString("status", "(missing status)"));
        }
    }

    private void addApplicationCard(JSONObject app) {
        String id = first(app, "id", "application_id");
        String tutorId = first(app, "tutor_id", "user_id");
        LinearLayout card = card();
        card.addView(text("Tutor application", 18, 0xFF082B6F, true));
        String name = first(app, "tutor_name", "full_name", "name", "display_name");
        String email = first(app, "tutor_email", "email");
        String modules = first(app, "module_codes", "modules");
        String evidence = first(app, "evidence_path", "evidence_url");
        addLine(card, "Tutor: " + (name.isEmpty() ? "Name not supplied" : name));
        if (!email.isEmpty()) addLine(card, "Email: " + email);
        addLine(card, "Modules: " + (modules.isEmpty() ? "Not supplied" : modules));
        addLine(card, "Status: " + first(app, "status"));
        if (!evidence.isEmpty()) addLine(card, "Evidence: " + evidence);
        if (id.isEmpty() || tutorId.isEmpty()) {
            addLine(card, "Cannot review: the RPC response is missing application ID or tutor ID.");
        } else {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button approve = button("Approve");
            Button reject = button("Reject");
            approve.setOnClickListener(v -> review(app, true));
            reject.setOnClickListener(v -> review(app, false));
            actions.addView(approve, weightParams());
            actions.addView(reject, weightParams());
            card.addView(actions);
        }
        content.addView(card);
    }

    private void review(JSONObject app, boolean approve) {
        String id = first(app, "id", "application_id");
        String tutorId = first(app, "tutor_id", "user_id");
        if (id.isEmpty() || tutorId.isEmpty()) { showError("Application ID or tutor ID is missing from the RPC response."); return; }
        new AlertDialog.Builder(this)
                .setTitle(approve ? "Approve tutor?" : "Reject application?")
                .setMessage(approve ? "Approve this tutor application?" : "Reject this tutor application?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(approve ? "Approve" : "Reject", (dialog, which) -> {
                    setLoading(true, "Submitting review…");
                    SupabaseApi.adminReviewApplication(id, tutorId, approve,
                            approve ? "Approved by administrator" : "Application rejected",
                            new SupabaseApi.EmptyCallback() {
                                @Override public void onSuccess() {
                                    runOnUiThread(() -> {
                                        setLoading(false, approve ? "Tutor approved." : "Application rejected.");
                                        Toast.makeText(AdminDashboardActivity.this, approve ? "Tutor approved." : "Application rejected.", Toast.LENGTH_LONG).show();
                                        loadApplications();
                                    });
                                }
                                @Override public void onError(String message) {
                                    runOnUiThread(() -> { setLoading(false, "Review failed."); showError(message); });
                                }
                            });
                }).show();
    }

    private void loadUsers() {
        currentScreen = SCREEN_USERS;
        setSection("Manage users");
        if (requestInProgress) return;
        setLoading(true, "Loading registered users…");
        content.removeAllViews();
        SupabaseApi.adminListUsers(new SupabaseApi.JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    setLoading(false, "");
                    content.removeAllViews();
                    if (data == null || data.length() == 0) {
                        status.setText("No users were returned.");
                        addMessage("The admin users RPC returned an empty list. Check peerpath_admin_list_users in Supabase.");
                        return;
                    }
                    status.setText("Registered users: " + data.length());
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject user = data.optJSONObject(i);
                        if (user != null) addUserCard(user);
                    }
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> { setLoading(false, "Could not load users."); showError(message); });
            }
        });
    }

    private void addUserCard(JSONObject user) {
        LinearLayout card = card();
        String name = first(user, "full_name", "name", "display_name", "username");
        String email = first(user, "email", "user_email");
        String id = first(user, "id", "user_id");
        String role = first(user, "role", "user_role");
        String suspended = first(user, "is_suspended", "suspended", "status");
        card.addView(text(name.isEmpty() ? "Registered user" : name, 17, 0xFF082B6F, true));
        if (!email.isEmpty()) addLine(card, "Email: " + email);
        if (!id.isEmpty()) addLine(card, "User ID: " + id);
        if (!role.isEmpty()) addLine(card, "Role: " + role);
        if (!suspended.isEmpty()) addLine(card, "Status: " + suspended);
        content.addView(card);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this).setTitle("Log out?")
                .setMessage("You will be signed out of PeerPath.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (dialog, which) -> {
                    SupabaseApi.clearSession();
                    goToLogin();
                }).show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setSection(String value) { if (title != null) title.setText(value); }
    private void setLoading(boolean loading, String message) {
        requestInProgress = loading;
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (status != null && message != null && !message.isEmpty()) status.setText(message);
        if (applicationsButton != null) applicationsButton.setEnabled(!loading);
        if (usersButton != null) usersButton.setEnabled(!loading);
    }
    private String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }
    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("Admin request failed")
                .setMessage(message == null || message.trim().isEmpty() ? "Unknown error" : message)
                .setPositiveButton("OK", null).show();
    }
    private void addLine(LinearLayout parent, String value) { parent.addView(text(value, 14, 0xFF52627A, false)); }
    private void addMessage(String value) { LinearLayout message = card(); addLine(message, value); content.addView(message); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(6)); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private Button button(String value) { Button button = new Button(this); button.setText(value); button.setAllCaps(false); return button; }
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12)); card.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(10); card.setLayoutParams(params); return card;
    }
    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(dp(3), dp(4), dp(3), dp(4)); return params;
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
