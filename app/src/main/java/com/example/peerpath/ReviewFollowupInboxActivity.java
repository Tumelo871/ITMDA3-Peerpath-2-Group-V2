package com.example.peerpath;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.peerpath.supabase.SupabaseApi;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReviewFollowupInboxActivity extends Activity {
    private LinearLayout list;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SupabaseApi.init(getApplicationContext());
        buildUi();
        loadInbox();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(12));
        root.setBackgroundColor(0xFFF5F8FF);
        TextView title = text("Review follow-ups", 24, 0xFF082B6F, true);
        root.addView(title);
        root.addView(text("Private conversations with PeerPath administrators.", 14, 0xFF52627A, false));
        status = text("Loading conversations…", 13, 0xFF52627A, false);
        root.addView(status);
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button refresh = button("Refresh inbox");
        refresh.setOnClickListener(v -> loadInbox());
        root.addView(refresh);
        Button back = button("Back to dashboard");
        back.setOnClickListener(v -> finish());
        root.addView(back);
        setContentView(root);
    }

    private void loadInbox() {
        status.setText("Loading conversations…");
        SupabaseApi.getReviewFollowupInbox(new SupabaseApi.JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    list.removeAllViews();
                    int count = 0;
                    for (int i = 0; data != null && i < data.length(); i++) {
                        JSONObject row = data.optJSONObject(i);
                        if (row == null) continue;
                        String audience = row.optString("audience", "");
                        if (!"learner".equals(audience) && !"tutor".equals(audience)) continue;
                        addCard(row, audience);
                        count++;
                    }
                    status.setText(count == 0
                            ? "No follow-up messages yet. When an admin messages you, the conversation appears here."
                            : count + (count == 1 ? " conversation" : " conversations"));
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    status.setText("Inbox could not be loaded.");
                    new AlertDialog.Builder(ReviewFollowupInboxActivity.this)
                            .setTitle("Inbox error").setMessage(message)
                            .setPositiveButton("OK", null).show();
                });
            }
        });
    }

    private void addCard(JSONObject row, String audience) {
        String reviewId = row.optString("review_id", "");
        String tutor = row.optString("tutor_name", "Tutor");
        String reviewer = row.optString("reviewer_name", "Learner");
        String comment = row.optString("review_comment", "");
        String latest = row.optString("last_message", "");
        int rating = row.optInt("rating", 0);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(10);
        card.setLayoutParams(cp);
        card.addView(text("Tutor: " + tutor, 16, 0xFF082B6F, true));
        card.addView(text("Rating: " + rating + "/5", 14, 0xFF0D5BE1, true));
        if ("learner".equals(audience)) {
            card.addView(text("Your review: " + (comment.isEmpty() ? "No written comment" : comment), 13, 0xFF52627A, false));
        } else {
            card.addView(text("Reviewer: " + reviewer, 13, 0xFF52627A, false));
            card.addView(text("Review: " + (comment.isEmpty() ? "No written comment" : comment), 13, 0xFF52627A, false));
        }
        card.addView(text("Latest message: " + (latest.isEmpty() ? "No message text" : latest), 14, 0xFF17243A, false));
        Button open = button("Open conversation");
        open.setOnClickListener(v -> {
            if (reviewId.trim().isEmpty()) {
                Toast.makeText(this, "Review ID missing.", Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(this, ReviewFollowupChatActivity.class);
            intent.putExtra("review_id", reviewId);
            intent.putExtra("audience", audience);
            intent.putExtra("tutor_name", tutor);
            intent.putExtra("rating", String.valueOf(rating));
            intent.putExtra("review_comment", comment);
            startActivity(intent);
        });
        card.addView(open);
        list.addView(card);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value == null ? "" : value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }
    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0D5BE1));
        return b;
    }
    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
