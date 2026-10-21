package com.example.peerpath;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.peerpath.supabase.SupabaseApi;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Sponsor request screen backed by the existing Supabase sponsor RPCs. */
public class SponsorActivity extends Activity {
    private static final int NAVY = 0xFF082B6F;
    private static final int BLUE = 0xFF0D5BE1;
    private static final int BACKGROUND = 0xFFF5F8FF;
    private static final int TEXT = 0xFF17243A;
    private static final int MUTED = 0xFF52627A;

    private LinearLayout requestSection;
    private LinearLayout requestList;
    private TextView status;
    private Spinner learnerSpinner;
    private final List<JSONObject> learners = new ArrayList<>();
    private boolean isAdmin = false;
    private String myUserId = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        if (!SupabaseApi.isConfigured()) {
            showFatal("Supabase is not configured. Check SupabaseConfig.URL and PUBLISHABLE_KEY.");
            return;
        }
        if (!SupabaseApi.isLoggedIn()) {
            showFatal("Please sign in to PeerPath before opening Sponsor.");
            return;
        }
        myUserId = SupabaseApi.userId();
        buildUi();
        loadMyRoleThenData();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        root.setBackgroundColor(BACKGROUND);

        TextView title = text("PeerPath Sponsorship", 26, NAVY, true);
        root.addView(title);
        root.addView(text("Support a learner by requesting a sponsorship connection. Learners decide whether to approve.", 15, MUTED, false));

        requestSection = card();
        requestSection.addView(text("Request to sponsor a learner", 19, NAVY, true));
        requestSection.addView(text("Choose a learner and send a request. You cannot approve your own request.", 14, MUTED, false));
        learnerSpinner = new Spinner(this);
        requestSection.addView(learnerSpinner, new LinearLayout.LayoutParams(-1, dp(52)));
        Button send = button("Send sponsorship request");
        send.setOnClickListener(v -> submitRequest());
        requestSection.addView(send, fullWidth());
        root.addView(requestSection);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView requestsTitle = text("Sponsorship requests", 20, NAVY, true);
        heading.addView(requestsTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = button("Refresh");
        refresh.setOnClickListener(v -> loadRequests());
        heading.addView(refresh);
        root.addView(heading);
        status = text("Loading your requests…", 14, MUTED, false);
        root.addView(status);
        requestList = new LinearLayout(this);
        requestList.setOrientation(LinearLayout.VERTICAL);
        root.addView(requestList);
        Button logout = button("Log out");
        logout.setOnClickListener(v -> confirmLogout());
        root.addView(logout, fullWidth());

        Button close = button("Back");
        close.setOnClickListener(v -> finish());
        root.addView(close, fullWidth());
        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadMyRoleThenData() {
        SupabaseApi.getMyProfile(new SupabaseApi.ObjectCallback() {
            @Override public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> {
                    isAdmin = "admin".equalsIgnoreCase(profile.optString("role", ""));
                    loadLearners();
                    loadRequests();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> showError("Could not verify your PeerPath profile: " + message));
            }
        });
    }

    private void loadLearners() {
        SupabaseApi.getSponsorLearners(new SupabaseApi.JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    learners.clear();
                    List<String> labels = new ArrayList<>();
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject row = data.optJSONObject(i);
                        if (row == null) continue;
                        String id = row.optString("learner_id", "");
                        if (id.isEmpty() || id.equals(myUserId)) continue;
                        learners.add(row);
                        String name = row.optString("learner_name", "Learner");
                        if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name)) name = "Learner";
                        labels.add(name + "  ·  " + shortId(id));
                    }
                    if (labels.isEmpty()) labels.add("No learners available");
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(SponsorActivity.this,
                            android.R.layout.simple_spinner_item, labels);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    learnerSpinner.setAdapter(adapter);
                    learnerSpinner.setEnabled(!learners.isEmpty());
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    learnerSpinner.setEnabled(false);
                    showError("Could not load learners: " + message + ". Check that the sponsor read-functions SQL was run.");
                });
            }
        });
    }

    private void submitRequest() {
        int position = learnerSpinner.getSelectedItemPosition();
        if (position < 0 || position >= learners.size()) {
            showError("No learner is available to select yet.");
            return;
        }
        JSONObject selected = learners.get(position);
        String learnerId = selected.optString("learner_id", "");
        String name = selected.optString("learner_name", "this learner");
        if (name == null || name.trim().isEmpty() || "null".equalsIgnoreCase(name)) name = "this learner";
        new AlertDialog.Builder(this)
                .setTitle("Send sponsorship request?")
                .setMessage("Send a request to sponsor " + name + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send request", (dialog, which) -> {
                    setBusy(true, "Sending request…");
                    SupabaseApi.requestSponsorLearner(learnerId, new SupabaseApi.EmptyCallback() {
                        @Override public void onSuccess() {
                            runOnUiThread(() -> {
                                setBusy(false, "Request sent.");
                                toast("Sponsorship request sent.");
                                loadRequests();
                            });
                        }
                        @Override public void onError(String message) {
                            runOnUiThread(() -> { setBusy(false, "Could not send request."); showError(message); });
                        }
                    });
                }).show();
    }

    private void loadRequests() {
        if (status == null || requestList == null) return;
        status.setText("Loading sponsorship requests…");
        SupabaseApi.getSponsorRequests(new SupabaseApi.JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    requestList.removeAllViews();
                    int count = 0;
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject row = data.optJSONObject(i);
                        if (row == null) continue;
                        addRequestCard(row);
                        count++;
                    }
                    status.setText(count == 0 ? "No sponsorship requests yet." : "Requests shown: " + count);
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    status.setText("Could not load requests.");
                    showError("Could not load sponsorship requests: " + message);
                });
            }
        });
    }

    private void addRequestCard(JSONObject row) {
        String requestId = row.optString("request_id", "");
        String sponsorId = row.optString("sponsor_id", "");
        String learnerId = row.optString("learner_id", "");
        String requestStatus = row.optString("request_status", "pending");
        if (requestStatus == null || requestStatus.trim().isEmpty() || "null".equalsIgnoreCase(requestStatus)) requestStatus = "pending";
        String linkId = row.optString("active_link_id", "");
        boolean pending = "pending".equalsIgnoreCase(requestStatus);
        boolean approved = "approved".equalsIgnoreCase(requestStatus) && !linkId.isEmpty();
        boolean learnerCanApprove = pending && myUserId.equals(learnerId);
        boolean adminCanApproveOrReject = pending && isAdmin;

        LinearLayout card = card();
        card.addView(text("Status: " + requestStatus.toUpperCase(), 17, NAVY, true));
        card.addView(text("Sponsor: " + row.optString("sponsor_name", "User"), 15, TEXT, false));
        card.addView(text("Learner: " + row.optString("learner_name", "Learner"), 15, TEXT, false));
        card.addView(text("Requested: " + row.optString("requested_at", ""), 12, MUTED, false));
        String reason = row.optString("review_reason", "");
        if (!reason.isEmpty() && !"null".equals(reason))
            card.addView(text("Review note: " + reason, 13, MUTED, false));

        if (learnerCanApprove || adminCanApproveOrReject) {
            LinearLayout actions = new LinearLayout(this);
            Button approve = button("Approve");
            approve.setOnClickListener(v -> confirmApprove(requestId));
            actions.addView(approve, new LinearLayout.LayoutParams(0, dp(48), 1));
            if (isAdmin) {
                Button reject = button("Reject");
                reject.setOnClickListener(v -> promptReason("Reject sponsorship request", reasonText -> reject(requestId, reasonText)));
                actions.addView(reject, new LinearLayout.LayoutParams(0, dp(48), 1));
            }
            card.addView(actions);
        }
        if (isAdmin && approved) {
            Button revoke = button("Revoke sponsorship");
            revoke.setOnClickListener(v -> promptReason("Revoke sponsorship", reasonText -> revoke(linkId, reasonText)));
            card.addView(revoke, fullWidth());
        }
        requestList.addView(card);
    }

    private void confirmApprove(String requestId) {
        new AlertDialog.Builder(this).setTitle("Approve sponsorship?")
                .setMessage("This will create an active sponsorship link.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Approve", (d, w) -> {
                    setBusy(true, "Approving…");
                    SupabaseApi.approveSponsorRequest(requestId, new SupabaseApi.EmptyCallback() {
                        @Override public void onSuccess() { runOnUiThread(() -> { setBusy(false, "Approved."); toast("Sponsorship approved."); loadRequests(); }); }
                        @Override public void onError(String message) { runOnUiThread(() -> { setBusy(false, "Approval failed."); showError(message); }); }
                    });
                }).show();
    }

    private void reject(String requestId, String reason) {
        setBusy(true, "Rejecting request…");
        SupabaseApi.rejectSponsorRequest(requestId, reason, new SupabaseApi.EmptyCallback() {
            @Override public void onSuccess() { runOnUiThread(() -> { setBusy(false, "Request rejected."); toast("Request rejected."); loadRequests(); }); }
            @Override public void onError(String message) { runOnUiThread(() -> { setBusy(false, "Rejection failed."); showError(message); }); }
        });
    }

    private void revoke(String linkId, String reason) {
        new AlertDialog.Builder(this).setTitle("Confirm revocation")
                .setMessage("This will end the active sponsorship.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Revoke", (d, w) -> {
                    setBusy(true, "Revoking sponsorship…");
                    SupabaseApi.revokeSponsorLink(linkId, reason, new SupabaseApi.EmptyCallback() {
                        @Override public void onSuccess() { runOnUiThread(() -> { setBusy(false, "Sponsorship revoked."); toast("Sponsorship revoked."); loadRequests(); }); }
                        @Override public void onError(String message) { runOnUiThread(() -> { setBusy(false, "Revocation failed."); showError(message); }); }
                    });
                }).show();
    }

    private interface ReasonCallback { void onReason(String reason); }
    private void promptReason(String title, ReasonCallback callback) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Reason (required)");
        input.setMinLines(2);
        new AlertDialog.Builder(this).setTitle(title).setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) { showError("A reason is required."); return; }
                    callback.onReason(reason);
                }).show();
    }

    private void setBusy(boolean busy, String message) {
        status.setText(message);
        if (learnerSpinner != null) learnerSpinner.setEnabled(!busy && !learners.isEmpty());
        if (requestSection != null) {
            for (int i = 0; i < requestSection.getChildCount(); i++) {
                View child = requestSection.getChildAt(i);
                if (child instanceof Button) child.setEnabled(!busy);
            }
        }
    }

    private void showFatal(String message) {
        new AlertDialog.Builder(this).setTitle("PeerPath Sponsor")
                .setMessage(message).setPositiveButton("Close", (d, w) -> finish()).show();
    }
    private void showError(String message) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this).setTitle("Sponsor workflow")
                .setMessage(message).setPositiveButton("OK", null).show();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, dp(14));
        box.setLayoutParams(params);
        return box;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(4), 0, dp(5));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value); b.setAllCaps(false); b.setTextColor(0xFFFFFFFF);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));
        return b;
    }
    private LinearLayout.LayoutParams fullWidth() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48));
        p.setMargins(0, dp(8), 0, dp(8)); return p;
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private String shortId(String id) { return id.length() > 8 ? id.substring(0, 8) + "…" : id; }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log out of PeerPath?")
                .setMessage("Are you sure you want to log out?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (dialog, which) -> {
                    // This clears the locally stored access/refresh tokens.
                    SupabaseApi.clearSession();

                    Intent intent = new Intent(SponsorActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

}
