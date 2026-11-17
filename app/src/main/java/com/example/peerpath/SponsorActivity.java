package com.example.peerpath;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * PeerPath sponsor dashboard.
 *
 * Uses the existing sponsor RPCs in SupabaseApi. The database remains the
 * authority for access control and learner consent.
 */
public class SponsorActivity extends Activity {

    private static final int NAVY = Color.rgb(15, 43, 76);
    private static final int BLUE = Color.rgb(35, 105, 190);
    private static final int TEAL = Color.rgb(34, 145, 125);
    private static final int BG = Color.rgb(244, 247, 251);
    private static final int TEXT = Color.rgb(32, 45, 62);
    private static final int MUTED = Color.rgb(105, 119, 138);
    private static final int BORDER = Color.rgb(225, 232, 240);
    private static final int WHITE = Color.WHITE;
    private static final int AMBER = Color.rgb(178, 117, 20);
    private static final int RED = Color.rgb(184, 55, 65);

    private LinearLayout root;
    private LinearLayout requestSection;
    private LinearLayout requestList;
    private TextView greeting;
    private TextView summaryActive;
    private TextView summaryPending;
    private TextView summaryHistory;
    private TextView listStatus;
    private LinearLayout resultsList;
    private TextView resultsStatus;
    private Spinner learnerSpinner;
    private Button sendRequestButton;
    private Button allFilter;
    private Button activeFilter;
    private Button pendingFilter;
    private Button historyFilter;

    private final List<JSONObject> learners = new ArrayList<>();
    private JSONArray allRequests = new JSONArray();
    private JSONArray releasedResults = new JSONArray();

    private boolean isAdmin = false;
    private boolean busy = false;
    private String myUserId = "";
    private String currentFilter = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());

        if (!SupabaseApi.isConfigured()) {
            showFatal("Supabase is not configured. Check SupabaseConfig.URL and PUBLISHABLE_KEY.");
            return;
        }
        if (!SupabaseApi.isLoggedIn()) {
            showFatal("Please sign in to PeerPath before opening the sponsor dashboard.");
            return;
        }

        myUserId = SupabaseApi.userId();
        buildDashboard();
        loadMyRoleThenData();
    }

    private void buildDashboard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setBackgroundColor(BG);

        // Dashboard hero
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(round(NAVY, 20));
        TextView brand = text("PEERPATH  •  SPONSOR PORTAL", 11, 0xFFD5E5F5, true);
        hero.addView(brand);
        greeting = text("Welcome back", 27, WHITE, true);
        LinearLayout.LayoutParams greetParams = new LinearLayout.LayoutParams(-1, -2);
        greetParams.topMargin = dp(12);
        hero.addView(greeting, greetParams);
        hero.addView(text("Your support can make a real difference in a learner's journey.",
                14, 0xFFE0EAF5, false));
        root.addView(hero, fullWidthParams());

        TextView overviewTitle = sectionTitle("Your sponsorship overview");
        addTopSpace(root, overviewTitle, 20);

        LinearLayout statsRow1 = new LinearLayout(this);
        statsRow1.setOrientation(LinearLayout.HORIZONTAL);
        summaryActive = addStatCard(statsRow1, "0", "Active links", TEAL);
        summaryPending = addStatCard(statsRow1, "0", "Pending requests", AMBER);
        root.addView(statsRow1, fullWidthParams());

        LinearLayout statsRow2 = new LinearLayout(this);
        statsRow2.setOrientation(LinearLayout.HORIZONTAL);
        summaryHistory = addStatCard(statsRow2, "0", "Other requests", BLUE);
        TextView infoStat = addStatCard(statsRow2, "Live", "Supabase data", NAVY);
        root.addView(statsRow2, fullWidthParams());

        // Request a learner connection
        requestSection = card();
        requestSection.addView(sectionTitle("Support a learner"));
        requestSection.addView(text(
                "Choose a learner and send a sponsorship request. The learner must approve the connection before it becomes active.",
                13, MUTED, false));
        learnerSpinner = new Spinner(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(-1, dp(52));
        spinnerParams.topMargin = dp(10);
        requestSection.addView(learnerSpinner, spinnerParams);

        sendRequestButton = primaryButton("Send sponsorship request");
        sendRequestButton.setOnClickListener(v -> submitRequest());
        requestSection.addView(sendRequestButton, fullWidthParams());
        root.addView(requestSection, fullWidthParams());

        // Requests / sponsorship list
        LinearLayout listHeading = new LinearLayout(this);
        listHeading.setOrientation(LinearLayout.HORIZONTAL);
        listHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView listTitle = sectionTitle("Sponsorship management");
        listHeading.addView(listTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = smallButton("Refresh");
        refresh.setOnClickListener(v -> refreshAll());
        listHeading.addView(refresh);
        LinearLayout.LayoutParams headingParams = fullWidthParams();
        headingParams.topMargin = dp(18);
        root.addView(listHeading, headingParams);

        TextView helper = text(
                isAdmin
                        ? "Review requests and manage active sponsorship links."
                        : "Track your requests and the learners connected to your sponsorship.",
                13, MUTED, false);
        root.addView(helper);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(0, dp(10), 0, dp(6));
        allFilter = filterButton("All", "all");
        activeFilter = filterButton("Active", "active");
        pendingFilter = filterButton("Pending", "pending");
        historyFilter = filterButton("History", "history");
        filters.addView(allFilter, filterParams());
        filters.addView(activeFilter, filterParams());
        filters.addView(pendingFilter, filterParams());
        filters.addView(historyFilter, filterParams());
        root.addView(filters, fullWidthParams());

        listStatus = text("Loading sponsorship information…", 13, MUTED, false);
        root.addView(listStatus);
        requestList = new LinearLayout(this);
        requestList.setOrientation(LinearLayout.VERTICAL);
        root.addView(requestList, fullWidthParams());

        // Released academic results (sponsors only; Supabase RLS controls visibility).
        LinearLayout resultsHeading = new LinearLayout(this);
        resultsHeading.setOrientation(LinearLayout.HORIZONTAL);
        resultsHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView resultsTitle = sectionTitle("Released learner results");
        resultsHeading.addView(resultsTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button refreshResults = smallButton("Refresh");
        refreshResults.setOnClickListener(v -> loadSponsorResults());
        resultsHeading.addView(refreshResults);
        LinearLayout.LayoutParams resultsHeadingParams = fullWidthParams();
        resultsHeadingParams.topMargin = dp(22);
        root.addView(resultsHeading, resultsHeadingParams);
        root.addView(text("Only marks and feedback that the learner's tutor has released to an active sponsor are shown.",
                13, MUTED, false));
        resultsStatus = text("Loading released results…", 13, MUTED, false);
        LinearLayout.LayoutParams resultsStatusParams = fullWidthParams();
        resultsStatusParams.topMargin = dp(8);
        root.addView(resultsStatus, resultsStatusParams);
        resultsList = new LinearLayout(this);
        resultsList.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultsList, fullWidthParams());

        TextView privacyNote = text(
                "Privacy: sponsors can only view sponsorship information returned for their own approved connections. Learners remain in control of approving or rejecting requests.",
                12, MUTED, false);
        LinearLayout.LayoutParams noteParams = fullWidthParams();
        noteParams.topMargin = dp(12);
        root.addView(privacyNote, noteParams);

        Button logout = secondaryButton("Log out");
        logout.setOnClickListener(v -> confirmLogout());
        LinearLayout.LayoutParams actionParams = fullWidthParams();
        actionParams.topMargin = dp(18);
        root.addView(logout, actionParams);

        Button back = secondaryButton("Back");
        back.setOnClickListener(v -> finish());
        root.addView(back, fullWidthParams());

        scroll.addView(root);
        setContentView(scroll);
        updateFilterStyles();
    }

    private void loadMyRoleThenData() {
        SupabaseApi.getMyProfile(new SupabaseApi.ObjectCallback() {
            @Override
            public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> {
                    String role = profile.optString("role", "").trim();
                    String verification = profile.optString("verification_status", "").trim();
                    isAdmin = "admin".equalsIgnoreCase(role);

                    if (!isAdmin && !"sponsor".equalsIgnoreCase(role)) {
                        showFatal("This dashboard is only available to approved sponsors and administrators.");
                        return;
                    }
                    if (!isAdmin && !"verified".equalsIgnoreCase(verification)) {
                        String state = verification.isEmpty() ? "not approved" : verification.replace('_', ' ');
                        showFatal("Your sponsor application is " + state
                                + ". Your dashboard will unlock after administrator approval.");
                        return;
                    }

                    String name = profile.optString("full_name", "").trim();
                    if (name.isEmpty() || "null".equalsIgnoreCase(name)) {
                        name = profile.optString("first_name", "").trim();
                    }
                    if (!name.isEmpty() && !"null".equalsIgnoreCase(name)) {
                        greeting.setText("Welcome, " + firstName(name));
                    } else {
                        greeting.setText(isAdmin ? "Administrator dashboard" : "Welcome back");
                    }
                    refreshAll();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> showError("Could not verify your PeerPath profile: " + message));
            }
        });
    }

    private void refreshAll() {
        if (busy) return;
        loadLearners();
        loadRequests();
        if (!isAdmin) loadSponsorResults();
        else if (resultsStatus != null) {
            resultsStatus.setText("Academic results are available to approved sponsors, not administrator accounts.");
            resultsList.removeAllViews();
        }
    }

    private void loadSponsorResults() {
        if (isAdmin || resultsList == null || resultsStatus == null) return;
        resultsStatus.setText("Loading released results…");
        SupabaseApi.getSponsorReleasedResults(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> renderSponsorResults(data == null ? new JSONArray() : data));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    resultsStatus.setText("Could not load released results.");
                    resultsList.removeAllViews();
                    showError("Could not load released results: " + message);
                });
            }
        });
    }

    private void renderSponsorResults(JSONArray data) {
        releasedResults = data == null ? new JSONArray() : data;
        data = releasedResults;
        resultsList.removeAllViews();
        if (data.length() == 0) {
            resultsStatus.setText("No results have been released to your sponsorships yet.");
            addResultsEmptyState();
            return;
        }
        resultsStatus.setText(data.length() + " released result(s)");
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.optJSONObject(i);
            if (row == null) continue;
            JSONObject assignment = row.optJSONObject("peerpath_assignments");
            if (assignment == null) assignment = new JSONObject();
            String learnerName = findLinkedLearnerName(row.optString("learner_id", ""));
            String title = safe(assignment.optString("title", ""), "Assessment");
            String subject = safe(assignment.optString("subject", ""), "Subject not specified");
            String total = row.optString("graded_total", "");
            String maximum = row.optString("graded_max_score", "");
            if (total.isEmpty() || "null".equalsIgnoreCase(total)) total = row.optString("score", "Not provided");
            if (maximum.isEmpty() || "null".equalsIgnoreCase(maximum)) maximum = "—";
            String feedback = safe(row.optString("tutor_feedback", ""), "No tutor feedback was included.");

            LinearLayout resultCard = card();
            resultCard.addView(text(title, 16, NAVY, true));
            resultCard.addView(detailLine("Learner", learnerName));
            resultCard.addView(detailLine("Subject", subject));
            resultCard.addView(detailLine("Mark", total + " / " + maximum));
            resultCard.addView(detailLine("Marked", safe(row.optString("marked_at", ""), "Date not available")));
            TextView feedbackText = text("Tutor feedback: " + feedback, 13, TEXT, false);
            feedbackText.setPadding(0, dp(7), 0, 0);
            resultCard.addView(feedbackText);
            resultsList.addView(resultCard, fullWidthParams());
        }
    }

    private String findLinkedLearnerName(String learnerId) {
        if (learnerId == null || learnerId.trim().isEmpty()) return "Linked learner";
        for (int i = 0; i < allRequests.length(); i++) {
            JSONObject request = allRequests.optJSONObject(i);
            if (request == null) continue;
            if (learnerId.equals(request.optString("learner_id", ""))) {
                return safe(request.optString("learner_name", ""), "Linked learner");
            }
        }
        return "Linked learner";
    }

    private void addResultsEmptyState() {
        LinearLayout empty = card();
        empty.addView(text("No released results yet", 15, NAVY, true));
        empty.addView(text("When a tutor releases a marked assessment to you, it will appear here. Unreleased marks and learner answers remain private.",
                13, MUTED, false));
        resultsList.addView(empty, fullWidthParams());
    }

    private void loadLearners() {
        if (learnerSpinner == null || isAdmin) {
            if (requestSection != null) requestSection.setVisibility(isAdmin ? View.GONE : View.VISIBLE);
            return;
        }

        SupabaseApi.getSponsorLearners(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    learners.clear();
                    List<String> labels = new ArrayList<>();
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject row = data.optJSONObject(i);
                            if (row == null) continue;
                            String id = row.optString("learner_id", "");
                            if (id.isEmpty() || id.equals(myUserId)) continue;
                            learners.add(row);
                            String name = safe(row.optString("learner_name", ""), "Learner");
                            labels.add(name + "  ·  " + shortId(id));
                        }
                    }
                    if (labels.isEmpty()) labels.add("No learners currently available");
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                            SponsorActivity.this,
                            android.R.layout.simple_spinner_item, labels);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    learnerSpinner.setAdapter(adapter);
                    learnerSpinner.setEnabled(!learners.isEmpty() && !busy);
                    sendRequestButton.setEnabled(!learners.isEmpty() && !busy);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    learnerSpinner.setEnabled(false);
                    sendRequestButton.setEnabled(false);
                    showError("Could not load learners: " + message);
                });
            }
        });
    }

    private void submitRequest() {
        int position = learnerSpinner.getSelectedItemPosition();
        if (position < 0 || position >= learners.size()) {
            showError("There are no learners available to select.");
            return;
        }

        JSONObject selected = learners.get(position);
        String learnerId = selected.optString("learner_id", "");
        String learnerName = safe(selected.optString("learner_name", ""), "this learner");

        new AlertDialog.Builder(this)
                .setTitle("Send sponsorship request?")
                .setMessage("A request will be sent to " + learnerName
                        + ". The learner must approve it before the sponsorship becomes active.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send request", (dialog, which) -> {
                    setBusy(true, "Sending your request…");
                    SupabaseApi.requestSponsorLearner(learnerId, new SupabaseApi.EmptyCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                setBusy(false, "Request sent successfully.");
                                toast("Sponsorship request sent.");
                                loadRequests();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                setBusy(false, "Could not send request.");
                                showError(message);
                            });
                        }
                    });
                }).show();
    }

    private void loadRequests() {
        if (listStatus == null || requestList == null) return;
        listStatus.setText("Loading sponsorship information…");

        SupabaseApi.getSponsorRequests(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    allRequests = data == null ? new JSONArray() : data;
                    renderRequests();
                    if (releasedResults.length() > 0) renderSponsorResults(releasedResults);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    listStatus.setText("Could not load sponsorship information.");
                    requestList.removeAllViews();
                    showError("Could not load sponsorship information: " + message);
                });
            }
        });
    }

    private void renderRequests() {
        requestList.removeAllViews();

        int active = 0;
        int pending = 0;
        int other = 0;

        for (int i = 0; i < allRequests.length(); i++) {
            JSONObject row = allRequests.optJSONObject(i);
            if (row == null) continue;
            String state = requestStatus(row);
            boolean hasActiveLink = "approved".equals(state)
                    && !row.optString("active_link_id", "").trim().isEmpty();
            if (hasActiveLink) active++;
            else if ("pending".equals(state)) pending++;
            else other++;
        }

        summaryActive.setText(String.valueOf(active));
        summaryPending.setText(String.valueOf(pending));
        summaryHistory.setText(String.valueOf(other));

        int shown = 0;
        for (int i = 0; i < allRequests.length(); i++) {
            JSONObject row = allRequests.optJSONObject(i);
            if (row == null || !matchesFilter(row)) continue;
            addSponsorshipCard(row);
            shown++;
        }

        if (allRequests.length() == 0) {
            listStatus.setText("No sponsorship requests yet.");
            addEmptyState("No sponsorship activity yet",
                    "When you send or receive an eligible sponsorship request, it will appear here.");
        } else if (shown == 0) {
            listStatus.setText("No items match this filter.");
            addEmptyState("Nothing to show",
                    "Try another filter to see your sponsorship activity.");
        } else {
            listStatus.setText("Showing " + shown + " of " + allRequests.length() + " records");
        }
        updateFilterStyles();
    }

    private boolean matchesFilter(JSONObject row) {
        String state = requestStatus(row);
        boolean active = "approved".equals(state)
                && !row.optString("active_link_id", "").trim().isEmpty();
        boolean pending = "pending".equals(state);

        if ("active".equals(currentFilter)) return active;
        if ("pending".equals(currentFilter)) return pending;
        if ("history".equals(currentFilter)) return !active && !pending;
        return true;
    }

    private void addSponsorshipCard(JSONObject row) {
        String requestId = row.optString("request_id", "");
        String sponsorId = row.optString("sponsor_id", "");
        String learnerId = row.optString("learner_id", "");
        String state = requestStatus(row);
        String linkId = row.optString("active_link_id", "");
        boolean active = "approved".equals(state) && !linkId.trim().isEmpty();
        boolean pending = "pending".equals(state);

        LinearLayout item = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = text(
                safe(row.optString("learner_name", ""), "Learner"),
                17, NAVY, true);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(statusLabel(state, active), 11,
                active ? TEAL : (pending ? AMBER : MUTED), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(6), dp(9), dp(6));
        badge.setBackground(round(active ? 0xFFE6F5F0 : (pending ? 0xFFFFF3DD : 0xFFEFF2F6), 30));
        top.addView(badge);
        item.addView(top);

        if (isAdmin) {
            item.addView(detailLine("Sponsor", safe(row.optString("sponsor_name", ""), "User")));
            item.addView(detailLine("Learner ID", shortId(learnerId)));
            item.addView(detailLine("Sponsor ID", shortId(sponsorId)));
        } else {
            item.addView(detailLine("Sponsorship", active ? "Active learner connection" :
                    (pending ? "Awaiting learner decision" : "Request history")));
        }

        item.addView(detailLine("Requested", formatDate(row.optString("requested_at", ""))));
        String reviewed = row.optString("reviewed_at", "");
        if (!reviewed.isEmpty() && !"null".equalsIgnoreCase(reviewed)) {
            item.addView(detailLine("Reviewed", formatDate(reviewed)));
        }

        String reason = row.optString("review_reason", "");
        if (!reason.trim().isEmpty() && !"null".equalsIgnoreCase(reason)) {
            TextView note = text("Review note: " + reason, 12, MUTED, false);
            note.setPadding(0, dp(7), 0, dp(2));
            item.addView(note);
        }

        // Learners, not sponsors or administrators, approve their own incoming requests.
        if (isAdmin && pending && !requestId.isEmpty()) {
            Button reject = dangerButton("Reject request");
            reject.setOnClickListener(v ->
                    promptReason("Reject sponsorship request",
                            reasonText -> rejectRequest(requestId, reasonText)));
            item.addView(reject, fullWidthParams());
        }

        // Only administrators can revoke an active link; the sponsor cannot remove a learner.
        if (isAdmin && active && !linkId.trim().isEmpty()) {
            Button revoke = dangerButton("Revoke active sponsorship");
            revoke.setOnClickListener(v ->
                    promptReason("Revoke sponsorship",
                            reasonText -> revokeLink(linkId, reasonText)));
            item.addView(revoke, fullWidthParams());
        }

        requestList.addView(item, fullWidthParams());
    }

    private void rejectRequest(String requestId, String reason) {
        setBusy(true, "Rejecting request…");
        SupabaseApi.rejectSponsorRequest(requestId, reason, new SupabaseApi.EmptyCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    setBusy(false, "Request rejected.");
                    toast("Sponsorship request rejected.");
                    loadRequests();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setBusy(false, "Rejection failed.");
                    showError(message);
                });
            }
        });
    }

    private void revokeLink(String linkId, String reason) {
        new AlertDialog.Builder(this)
                .setTitle("Revoke active sponsorship?")
                .setMessage("This ends the active sponsorship link. The action will be recorded.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Revoke", (dialog, which) -> {
                    setBusy(true, "Revoking sponsorship…");
                    SupabaseApi.revokeSponsorLink(linkId, reason, new SupabaseApi.EmptyCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                setBusy(false, "Sponsorship revoked.");
                                toast("Sponsorship link revoked.");
                                loadRequests();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                setBusy(false, "Revocation failed.");
                                showError(message);
                            });
                        }
                    });
                }).show();
    }

    private interface ReasonCallback {
        void onReason(String reason);
    }

    private void promptReason(String title, ReasonCallback callback) {
        EditText input = new EditText(this);
        input.setHint("Enter a reason (required)");
        input.setMinLines(2);
        input.setGravity(Gravity.TOP | Gravity.START);
        int pad = dp(12);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        showError("A reason is required.");
                        return;
                    }
                    callback.onReason(reason);
                }).show();
    }

    private Button filterButton(String label, String filter) {
        Button b = smallButton(label);
        b.setOnClickListener(v -> {
            currentFilter = filter;
            renderRequests();
        });
        return b;
    }

    private void updateFilterStyles() {
        if (allFilter == null) return;
        styleFilter(allFilter, "all".equals(currentFilter));
        styleFilter(activeFilter, "active".equals(currentFilter));
        styleFilter(pendingFilter, "pending".equals(currentFilter));
        styleFilter(historyFilter, "history".equals(currentFilter));
    }

    private void styleFilter(Button button, boolean selected) {
        if (button == null) return;
        button.setTextColor(selected ? WHITE : NAVY);
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? BLUE : 0xFFE7EDF5));
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        if (listStatus != null) listStatus.setText(message);
        if (learnerSpinner != null) learnerSpinner.setEnabled(!value && !learners.isEmpty());
        if (sendRequestButton != null) {
            sendRequestButton.setEnabled(!value && !learners.isEmpty());
        }
    }

    private void addEmptyState(String title, String message) {
        LinearLayout empty = card();
        empty.setGravity(Gravity.CENTER);
        TextView icon = text("◎", 30, BLUE, true);
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon);
        TextView heading = text(title, 16, NAVY, true);
        heading.setGravity(Gravity.CENTER);
        empty.addView(heading);
        TextView body = text(message, 13, MUTED, false);
        body.setGravity(Gravity.CENTER);
        empty.addView(body);
        requestList.addView(empty, fullWidthParams());
    }

    private TextView addStatCard(LinearLayout row, String value, String label, int accent) {
        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setPadding(dp(14), dp(13), dp(14), dp(13));
        stat.setBackground(round(WHITE, 16));
        TextView number = text(value, 25, accent, true);
        stat.addView(number);
        stat.addView(text(label, 12, MUTED, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(0, dp(5), dp(8), dp(5));
        row.addView(stat, params);
        return number;
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(15), dp(14), dp(15), dp(14));
        box.setBackground(round(WHITE, 17));
        box.setElevation(dp(1));
        LinearLayout.LayoutParams p = fullWidthParams();
        p.setMargins(0, dp(7), 0, dp(9));
        box.setLayoutParams(p);
        return box;
    }

    private TextView sectionTitle(String value) {
        return text(value, 19, NAVY, true);
    }

    private TextView detailLine(String label, String value) {
        TextView t = text(label + ": " + value, 13, TEXT, false);
        t.setPadding(0, dp(5), 0, dp(2));
        return t;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button primaryButton(String label) {
        Button b = baseButton(label);
        b.setTextColor(WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = baseButton(label);
        b.setTextColor(NAVY);
        b.setBackgroundTintList(ColorStateList.valueOf(0xFFE5ECF4));
        return b;
    }

    private Button dangerButton(String label) {
        Button b = baseButton(label);
        b.setTextColor(RED);
        b.setBackgroundTintList(ColorStateList.valueOf(0xFFFFEEEE));
        return b;
    }

    private Button smallButton(String label) {
        Button b = baseButton(label);
        b.setTextSize(12);
        b.setMinHeight(dp(38));
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setTextColor(NAVY);
        b.setBackgroundTintList(ColorStateList.valueOf(0xFFE7EDF5));
        return b;
    }

    private Button baseButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(46));
        b.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        return b;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams filterParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(40), 1);
        p.setMargins(0, 0, dp(5), 0);
        return p;
    }

    private void addTopSpace(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams p = fullWidthParams();
        p.topMargin = dp(top);
        parent.addView(child, p);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String requestStatus(JSONObject row) {
        String state = row.optString("request_status", "pending");
        if (state == null || state.trim().isEmpty() || "null".equalsIgnoreCase(state)) {
            return "pending";
        }
        return state.trim().toLowerCase(Locale.ROOT);
    }

    private String statusLabel(String state, boolean active) {
        if (active) return "ACTIVE";
        if ("pending".equals(state)) return "PENDING";
        return state.replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    private String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return fallback;
        }
        return value.trim();
    }

    private String firstName(String name) {
        String clean = safe(name, "there");
        int space = clean.indexOf(' ');
        return space > 0 ? clean.substring(0, space) : clean;
    }

    private String shortId(String id) {
        if (id == null || id.trim().isEmpty()) return "Not available";
        return id.length() > 8 ? id.substring(0, 8) + "…" : id;
    }

    private String formatDate(String raw) {
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw)) {
            return "Not available";
        }
        try {
            String clean = raw.trim();
            SimpleDateFormat input;
            if (clean.length() >= 19) {
                input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            } else if (clean.length() >= 10) {
                input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            } else {
                return clean;
            }
            Date date = input.parse(clean.substring(0, Math.min(clean.length(), 19)));
            return date == null ? clean : DateFormat.getDateInstance(DateFormat.MEDIUM).format(date);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private void showFatal(String message) {
        new AlertDialog.Builder(this)
                .setTitle("PeerPath Sponsor")
                .setMessage(message)
                .setPositiveButton("Close", (dialog, which) -> finish())
                .show();
    }

    private void showError(String message) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Sponsor dashboard")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Log out of PeerPath?")
                .setMessage("Are you sure you want to log out?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (dialog, which) -> {
                    SupabaseApi.clearSession();
                    Intent intent = new Intent(SponsorActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }).show();
    }
}
