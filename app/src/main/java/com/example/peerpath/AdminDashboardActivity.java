package com.example.peerpath;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    public static final String SCREEN_REVIEWS = "reviews";

    private LinearLayout content;
    private TextView title, status;
    private ProgressBar progress;
    private Button applicationsButton, usersButton, reviewsButton, materialsButton;
    private boolean requestInProgress = false;
    private String currentScreen = SCREEN_APPLICATIONS;

    @Override protected int layoutId() { return R.layout.activity_admin_dashboard; }
    @Override protected String requiredRole() { return "admin"; }
    @Override protected boolean requiresVerifiedTutor() { return false; }

    @Override protected void wireRoleActions() {
        SupabaseApi.init(getApplicationContext());
        applicationsButton = findViewById(R.id.btnAdminApplications);
        usersButton = findViewById(R.id.btnAdminUsers);
        reviewsButton = findViewById(R.id.btnAdminReviewFollowups);
        materialsButton = findViewById(R.id.btnAdminTutorMaterials);
        status = findViewById(R.id.txtAdminStatus);
        progress = findViewById(R.id.adminProgress);
        content = findViewById(R.id.adminContent);
        title = findViewById(R.id.txtAdminSectionTitle);
        View logout = findViewById(R.id.btnAdminLogout);

        styleButton(applicationsButton);
        styleButton(usersButton);
        styleButton(reviewsButton);
        styleButton(materialsButton);
        if (logout instanceof Button) styleButton((Button) logout);

        if (applicationsButton != null) applicationsButton.setOnClickListener(v -> loadApplications());
        if (usersButton != null) usersButton.setOnClickListener(v -> loadUsers());
        if (reviewsButton != null) reviewsButton.setOnClickListener(v -> loadNegativeReviews());
        if (materialsButton != null) materialsButton.setOnClickListener(v -> openTutorMaterials());
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
            if (SCREEN_USERS.equals(currentScreen)) loadUsers();
            else if (SCREEN_REVIEWS.equals(currentScreen)) loadNegativeReviews();
            else loadApplications();
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

    private void openTutorMaterials() {
        Intent intent = new Intent(this, MaterialsActivity.class);
        intent.putExtra("mine", false); // Admin can inspect the shared repository.
        intent.putExtra("admin_review_mode", true);
        startActivity(intent);
    }

    private void loadNegativeReviews() {
        currentScreen = SCREEN_REVIEWS;
        setSection("Negative review follow-ups");
        if (requestInProgress) return;
        setLoading(true, "Loading reviews rated 1–2 stars…");
        content.removeAllViews();
        SupabaseApi.getAdminNegativeReviews(new SupabaseApi.JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLoading(false, "");
                    content.removeAllViews();
                    int count = data == null ? 0 : data.length();
                    status.setText("Negative reviews requiring follow-up: " + count);
                    if (count == 0) {
                        addMessage("No visible reviews rated 1 or 2 stars were returned.");
                        return;
                    }
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject review = data.optJSONObject(i);
                        if (review != null) addNegativeReviewCard(review);
                    }
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    setLoading(false, "Could not load negative reviews.");
                    showError(message);
                });
            }
        });
    }

    private void addNegativeReviewCard(JSONObject review) {
        LinearLayout item = card();
        int stars = review.optInt("rating", 0);
        String tutor = first(review, "tutor_name");
        String comment = first(review, "comment");
        String reviewer = Boolean.TRUE.equals(review.opt("is_anonymous"))
                ? "Anonymous learner" : first(review, "reviewer_name");
        String caseStatus = first(review, "case_status");
        if (caseStatus.isEmpty()) caseStatus = "open (not yet saved)";
        final String displayCaseStatus = caseStatus;

        item.addView(text("★ Rating: " + stars + "/5", 18, 0xFF082B6F, true));
        addLine(item, "Tutor: " + (tutor.isEmpty() ? "Tutor" : tutor));
        addLine(item, "Reviewer: " + (reviewer.isEmpty() ? "Learner" : reviewer));
        addLine(item, "Comment: " + (comment.isEmpty() ? "No written comment." : comment));
        addLine(item, "Follow-up status: " + displayCaseStatus.replace('_', ' '));
        Button details = button("View follow-up details");
        details.setOnClickListener(v -> showReviewFollowupDialog(
                review, tutor, reviewer, stars, comment, displayCaseStatus));
        item.addView(details);

        // Direct messaging actions remain visible on each negative-review card.
        String reviewId = first(review, "review_id", "id");
        if (reviewId.isEmpty()) {
            addLine(item, "Messaging unavailable: review ID was not returned by Supabase.");
        } else {
            Button messageLearner = button("Message learner");
            messageLearner.setOnClickListener(v ->
                    openReviewChat(reviewId, "learner", tutor, stars, comment));
            Button messageTutor = button("Message tutor");
            messageTutor.setOnClickListener(v ->
                    openReviewChat(reviewId, "tutor", tutor, stars, comment));
            item.addView(messageLearner, new LinearLayout.LayoutParams(-1, -2));
            item.addView(messageTutor, new LinearLayout.LayoutParams(-1, -2));
        }
        content.addView(item);
    }

    private void showReviewFollowupDialog(JSONObject review, String tutor, String reviewer,
                                          int stars, String comment, String caseStatus) {
        String reviewId = first(review, "review_id", "id");
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        panel.setPadding(pad, dp(8), pad, dp(8));

        addLine(panel, "Tutor: " + (tutor.isEmpty() ? "Tutor" : tutor));
        addLine(panel, "Rating: " + stars + "/5");
        addLine(panel, "Reviewer: " + (reviewer.isEmpty() ? "Learner" : reviewer));
        addLine(panel, "Comment: " + (comment.isEmpty() ? "No written comment." : comment));
        addLine(panel, "Follow-up status: " + caseStatus.replace('_', ' '));
        addLine(panel, "Choose who to contact. Learner and tutor conversations are separate.");

        Button messageLearner = button("Message learner");
        messageLearner.setOnClickListener(v -> {
            if (reviewId.isEmpty()) {
                showError("The review ID is missing from the admin review response.");
                return;
            }
            openReviewChat(reviewId, "learner", tutor, stars, comment);
        });
        Button messageTutor = button("Message tutor");
        messageTutor.setOnClickListener(v -> {
            if (reviewId.isEmpty()) {
                showError("The review ID is missing from the admin review response.");
                return;
            }
            openReviewChat(reviewId, "tutor", tutor, stars, comment);
        });
        Button materials = button("Open tutor materials");
        materials.setOnClickListener(v -> openTutorMaterials());

        panel.addView(messageLearner, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(messageTutor, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(materials, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Review follow-up")
                .setView(scroll)
                .setNegativeButton("Close", null)
                .create();
        dialog.show();
        Button close = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (close != null) close.setTextColor(0xFF0D5BE1);
    }

    private void openReviewChat(String reviewId, String audience, String tutorName,
                                int rating, String comment) {
        if (reviewId == null || reviewId.trim().isEmpty()) {
            showError("The review ID is missing from the admin review response.");
            return;
        }
        Intent intent = new Intent(this, ReviewFollowupChatActivity.class);
        intent.putExtra("review_id", reviewId);
        intent.putExtra("audience", audience);
        intent.putExtra("tutor_name", tutorName == null ? "" : tutorName);
        intent.putExtra("rating", String.valueOf(rating));
        intent.putExtra("review_comment", comment == null ? "" : comment);
        startActivity(intent);
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
        String studentNumber = first(user, "student_number");
        String faculty = first(user, "faculty");
        String verification = first(user, "verification_status");
        String subject = first(user, "tutor_subject");

        card.addView(text(name.isEmpty() ? "Registered user" : name, 17, 0xFF082B6F, true));
        if (!email.isEmpty()) addLine(card, "Email: " + email);
        if (!id.isEmpty()) addLine(card, "User ID: " + id);
        addLine(card, "Role: " + (role.isEmpty() ? "Not set" : role));
        if (!studentNumber.isEmpty()) addLine(card, "Student number: " + studentNumber);
        if (!faculty.isEmpty()) addLine(card, "Faculty: " + faculty);
        if (!verification.isEmpty()) addLine(card, "Verification: " + verification);
        if (!subject.isEmpty()) addLine(card, "Tutor subject: " + subject);

        boolean isAdmin = "admin".equalsIgnoreCase(role);
        boolean isSelf = !id.isEmpty() && id.equals(SupabaseApi.userId());
        if (isAdmin || isSelf || id.isEmpty()) {
            addLine(card, isAdmin ? "Administrator account protected." :
                    (isSelf ? "Your own account cannot be edited or deleted here." : "User ID missing; actions unavailable."));
        } else {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button edit = button("Edit user");
            Button delete = button("Delete user");
            edit.setOnClickListener(v -> showEditUserDialog(user));
            delete.setOnClickListener(v -> confirmDeleteUser(user));
            actions.addView(edit, weightParams());
            actions.addView(delete, weightParams());
            card.addView(actions);
        }
        content.addView(card);
    }

    private void showEditUserDialog(JSONObject user) {
        String id = first(user, "id", "user_id");
        if (id.isEmpty()) { showError("User ID is missing."); return; }
        if ("admin".equalsIgnoreCase(first(user, "role")) || id.equals(SupabaseApi.userId())) {
            showError("Administrator accounts and your own account cannot be edited here.");
            return;
        }

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        fields.setPadding(pad, dp(8), pad, dp(4));

        EditText fullName = editField("Full name", first(user, "full_name"));
        EditText studentNumber = editField("Student number", first(user, "student_number"));
        EditText faculty = editField("Faculty", first(user, "faculty"));
        fields.addView(fullName);
        fields.addView(studentNumber);
        fields.addView(faculty);

        fields.addView(text("Role", 14, 0xFF52627A, true));
        Spinner roleSpinner = spinnerWithOptions(
                new String[]{"learner", "tutor"}, first(user, "role").toLowerCase(java.util.Locale.ROOT));
        fields.addView(roleSpinner);

        fields.addView(text("Verification status", 14, 0xFF52627A, true));
        String currentStatus = first(user, "verification_status");
        java.util.ArrayList<String> statuses = new java.util.ArrayList<>();
        statuses.add("pending");
        statuses.add("verified");
        statuses.add("unverified");
        statuses.add("rejected");
        if (!currentStatus.isEmpty() && !statuses.contains(currentStatus)) statuses.add(currentStatus);
        Spinner statusSpinner = spinnerWithOptions(statuses.toArray(new String[0]), currentStatus);
        fields.addView(statusSpinner);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(fields);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit user profile")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save changes", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            styleButton(save);
            styleButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
            save.setOnClickListener(v -> {
                String newName = fullName.getText().toString().trim();
                if (newName.isEmpty()) {
                    fullName.setError("Full name is required.");
                    return;
                }
                setLoading(true, "Saving user changes…");
                SupabaseApi.adminUpdateUser(
                        id,
                        newName,
                        studentNumber.getText().toString(),
                        faculty.getText().toString(),
                        String.valueOf(roleSpinner.getSelectedItem()),
                        String.valueOf(statusSpinner.getSelectedItem()),
                        new SupabaseApi.EmptyCallback() {
                            @Override public void onSuccess() {
                                runOnUiThread(() -> {
                                    dialog.dismiss();
                                    setLoading(false, "User updated successfully.");
                                    Toast.makeText(AdminDashboardActivity.this, "User updated.", Toast.LENGTH_SHORT).show();
                                    loadUsers();
                                });
                            }
                            @Override public void onError(String message) {
                                runOnUiThread(() -> { setLoading(false, "Could not update user."); showError(message); });
                            }
                        });
            });
        });
        dialog.show();
    }

    private void confirmDeleteUser(JSONObject user) {
        String id = first(user, "id", "user_id");
        String name = first(user, "full_name", "email");
        String role = first(user, "role");
        if (id.isEmpty()) { showError("User ID is missing."); return; }
        if (id.equals(SupabaseApi.userId()) || "admin".equalsIgnoreCase(role)) {
            showError("You cannot delete your own account or an administrator account.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete user profile?")
                .setMessage("Remove the profile for " + (name.isEmpty() ? "this user" : name)
                        + "? This action cannot be undone. This currently deletes the public profile row only; the Supabase Authentication account will remain until full account deletion is implemented.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete profile", (dialog, which) -> {
                    setLoading(true, "Deleting user profile…");
                    SupabaseApi.adminDeleteUser(id, new SupabaseApi.EmptyCallback() {
                        @Override public void onSuccess() {
                            runOnUiThread(() -> {
                                setLoading(false, "User profile deleted.");
                                Toast.makeText(AdminDashboardActivity.this, "User profile deleted.", Toast.LENGTH_SHORT).show();
                                loadUsers();
                            });
                        }
                        @Override public void onError(String message) {
                            runOnUiThread(() -> { setLoading(false, "Could not delete user."); showError(message); });
                        }
                    });
                }).show();
    }

    private EditText editField(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(16);
        return edit;
    }

    private Spinner spinnerWithOptions(String[] options, String selected) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(selected == null ? "" : selected)) {
                spinner.setSelection(i);
                break;
            }
        }
        return spinner;
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
        if (reviewsButton != null) reviewsButton.setEnabled(!loading);
        if (materialsButton != null) materialsButton.setEnabled(!loading);
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
    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        styleButton(button);
        return button;
    }
    private void styleButton(Button button) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(0xFF0D5BE1));
    }
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
