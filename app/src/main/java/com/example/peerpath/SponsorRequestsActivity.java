package com.example.peerpath;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Learner-only sponsor request inbox.
 *
 * The learner can approve or decline only requests addressed to their own
 * account. Supabase RPCs independently enforce that permission.
 */
public class SponsorRequestsActivity extends Activity {

    private LinearLayout list;
    private TextView status;
    private ProgressBar progress;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());

        if (!SupabaseApi.isLoggedIn()) {
            Toast.makeText(this, "Please sign in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildScreen();
        loadRequests();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null && !loading) loadRequests();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 247, 255));
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView heading = new TextView(this);
        heading.setText("Sponsor Requests");
        heading.setTextSize(23);
        heading.setTextColor(Color.rgb(8, 43, 111));
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(heading);

        TextView explanation = new TextView(this);
        explanation.setText("Review requests from approved sponsors. You decide whether to approve or decline each request.");
        explanation.setTextSize(14);
        explanation.setTextColor(Color.rgb(82, 98, 122));
        explanation.setPadding(0, dp(6), 0, dp(10));
        root.addView(explanation);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(Color.rgb(60, 70, 90));
        root.addView(status);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);

        Button refresh = makeButton("Refresh requests");
        refresh.setOnClickListener(v -> loadRequests());
        root.addView(refresh);

        Button back = makeButton("Back to dashboard");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        setContentView(root);
    }

    private void loadRequests() {
        if (loading) return;
        loading = true;
        progress.setVisibility(View.VISIBLE);
        status.setText("Loading sponsor requests…");
        list.removeAllViews();

        SupabaseApi.getSponsorRequests(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    list.removeAllViews();

                    String myId = SupabaseApi.userId();
                    int pending = 0;
                    int total = 0;

                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject request = data.optJSONObject(i);
                            if (request == null) continue;

                            // The list RPC returns requests visible to the
                            // signed-in user. Filter again to learner-owned rows.
                            String learnerId = request.optString("learner_id", "");
                            if (!myId.equals(learnerId)) continue;

                            total++;
                            String requestStatus = request.optString(
                                    "request_status",
                                    request.optString("status", "")
                            );

                            if ("pending".equalsIgnoreCase(requestStatus)) {
                                pending++;
                                addRequestCard(request, true);
                            } else {
                                addRequestCard(request, false);
                            }
                        }
                    }

                    status.setText("Your sponsor requests: " + total
                            + " | Awaiting your decision: " + pending);

                    if (total == 0) {
                        addMessage("You do not have any sponsor requests at the moment.");
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    status.setText("Could not load sponsor requests.");
                    addMessage(message == null ? "Unknown error." : message);
                });
            }
        });
    }

    private void addRequestCard(JSONObject request, boolean pending) {
        String requestId = request.optString(
                "request_id",
                request.optString("id", "")
        );
        String sponsorName = request.optString("sponsor_name", "Sponsor");
        String requestStatus = request.optString(
                "request_status",
                request.optString("status", "unknown")
        );
        String requestedAt = request.optString("requested_at", "");
        String reason = request.optString("review_reason", "");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(10);
        card.setLayoutParams(cardParams);

        TextView title = makeText(
                pending ? "Pending Sponsor Request" : "Sponsor Request",
                18, Color.rgb(8, 43, 111), true);
        card.addView(title);

        card.addView(makeText("Sponsor: " + sponsorName, 15,
                Color.rgb(82, 98, 122), false));
        card.addView(makeText("Status: " + requestStatus, 14,
                Color.rgb(82, 98, 122), false));

        if (!requestedAt.isEmpty()) {
            card.addView(makeText("Request date: " + requestedAt,
                    13, Color.rgb(82, 98, 122), false));
        }

        if (!reason.isEmpty()) {
            card.addView(makeText("Review note: " + reason,
                    13, Color.rgb(82, 98, 122), false));
        }

        if (pending && !requestId.trim().isEmpty()) {
            TextView consent = makeText(
                    "Only you can approve this sponsorship. If you approve, the sponsor will be linked to your learner account.",
                    13, Color.rgb(82, 98, 122), false);
            card.addView(consent);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button approve = makeButton("Approve");
            Button decline = makeButton("Reject");

            approve.setOnClickListener(v -> confirmDecision(
                    requestId, true));
            decline.setOnClickListener(v -> confirmDecision(
                    requestId, false));

            actions.addView(approve, weightedParams());
            actions.addView(decline, weightedParams());
            card.addView(actions);
        }

        list.addView(card);
    }

    private void confirmDecision(String requestId, boolean approve) {
        String title = approve ? "Approve sponsor?" : "Reject sponsor request?";
        String message = approve
                ? "Do you consent to this sponsor being linked to your learner account?"
                : "Are you sure you want to decline this sponsor request?";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(approve ? "Approve" : "Reject",
                        (dialog, which) -> submitDecision(requestId, approve))
                .show();
    }

    private void submitDecision(String requestId, boolean approve) {
        setBusy(true);
        SupabaseApi.EmptyCallback callback = new SupabaseApi.EmptyCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(
                            SponsorRequestsActivity.this,
                            approve ? "Sponsor request approved."
                                    : "Sponsor request rejected.",
                            Toast.LENGTH_LONG
                    ).show();
                    loadRequests();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setBusy(false);
                    new AlertDialog.Builder(SponsorRequestsActivity.this)
                            .setTitle("Could not update request")
                            .setMessage(message == null ? "Unknown error." : message)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        };

        if (approve) {
            SupabaseApi.approveSponsorRequest(requestId, callback);
        } else {
            SupabaseApi.declineSponsorRequest(
                    requestId, "Declined by learner", callback);
        }
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        status.setText(busy ? "Saving your decision…" : "Updating requests…");
    }

    private void addMessage(String value) {
        LinearLayout card = new LinearLayout(this);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundColor(Color.WHITE);
        card.addView(makeText(value, 14,
                Color.rgb(82, 98, 122), false));
        list.addView(card);
    }

    private TextView makeText(String value, int size, int color,
                              boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setPadding(0, dp(5), 0, dp(5));
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(13, 91, 225)));
        return button;
    }

    private LinearLayout.LayoutParams weightedParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), dp(4), dp(3), dp(4));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
