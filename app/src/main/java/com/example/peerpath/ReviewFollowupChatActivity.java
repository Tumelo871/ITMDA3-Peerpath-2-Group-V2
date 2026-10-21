package com.example.peerpath;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

/**
 * Private conversation for one review. The database RPC enforces whether the
 * signed-in user is an administrator or an authorised conversation participant.
 */
public class ReviewFollowupChatActivity extends Activity {
    private String reviewId;
    private String audience;
    private String tutorName;
    private String ratingValue;
    private String reviewComment;
    private LinearLayout messageList;
    private EditText composer;
    private TextView status;
    private Button sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());

        reviewId = getIntent().getStringExtra("review_id");
        audience = getIntent().getStringExtra("audience");
        tutorName = safe(getIntent().getStringExtra("tutor_name"), "Tutor");
        ratingValue = safe(getIntent().getStringExtra("rating"), "—");
        reviewComment = safe(getIntent().getStringExtra("review_comment"), "");

        if (!"learner".equals(audience) && !"tutor".equals(audience)) {
            Toast.makeText(this, "Invalid conversation type.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (reviewId == null || reviewId.trim().isEmpty()) {
            Toast.makeText(this, "Review ID is missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildUi();
        loadMessages();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(12));
        root.setBackgroundColor(0xFFF5F8FF);

        TextView heading = text("Review follow-up", 23, 0xFF082B6F, true);
        root.addView(heading);

        TextView context = text(
                "Tutor: " + tutorName + "\nRating: " + ratingValue + "/5\nReview: " + reviewComment
                        + "\nConversation: " + ("learner".equals(audience)
                        ? "Private with learner" : "Private with tutor"),
                14, 0xFF52627A, false);
        context.setPadding(0, dp(8), 0, dp(10));
        root.addView(context);

        status = text("Loading messages…", 13, 0xFF52627A, false);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messageList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        composer = new EditText(this);
        composer.setHint("Write a respectful, constructive message…");
        composer.setMinLines(2);
        composer.setMaxLines(5);
        composer.setGravity(Gravity.TOP | Gravity.START);
        composer.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(composer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = button("Refresh");
        refresh.setOnClickListener(v -> loadMessages());
        sendButton = button("Send message");
        sendButton.setOnClickListener(v -> sendMessage());
        actions.addView(refresh, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(sendButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(actions);

        Button back = button("Back to admin");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        setContentView(root);
    }

    private void loadMessages() {
        status.setText("Loading messages…");
        SupabaseApi.getReviewFollowupMessages(reviewId, audience, new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    messageList.removeAllViews();
                    int count = data == null ? 0 : data.length();
                    if (count == 0) {
                        messageList.addView(text("No messages yet. Start the conversation below.",
                                14, 0xFF52627A, false));
                    } else {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.optJSONObject(i);
                            if (item != null) addMessageCard(item);
                        }
                    }
                    status.setText(count + (count == 1 ? " message" : " messages")
                            + " · " + ("learner".equals(audience)
                            ? "Learner conversation" : "Tutor conversation"));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    status.setText("Could not load messages.");
                    new AlertDialog.Builder(ReviewFollowupChatActivity.this)
                            .setTitle("Conversation unavailable")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private void addMessageCard(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        card.setLayoutParams(params);

        String sender = item.optString("sender_name", "PeerPath Admin");
        String body = item.optString("body", "");
        String created = item.optString("created_at", "");
        card.addView(text(sender, 14, 0xFF0D5BE1, true));
        card.addView(text(body, 15, 0xFF17243A, false));
        if (!created.isEmpty()) card.addView(text(created, 11, 0xFF718096, false));
        messageList.addView(card);
    }

    private void sendMessage() {
        String body = composer.getText().toString().trim();
        if (body.isEmpty()) {
            composer.setError("Write a message first.");
            return;
        }
        sendButton.setEnabled(false);
        status.setText("Sending message…");
        SupabaseApi.sendReviewFollowupMessage(reviewId, audience, body,
                new SupabaseApi.JsonCallback() {
                    @Override
                    public void onSuccess(JSONArray result) {
                        runOnUiThread(() -> {
                            composer.setText("");
                            sendButton.setEnabled(true);
                            Toast.makeText(ReviewFollowupChatActivity.this,
                                    "Message sent.", Toast.LENGTH_SHORT).show();
                            loadMessages();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            sendButton.setEnabled(true);
                            status.setText("Message was not sent.");
                            new AlertDialog.Builder(ReviewFollowupChatActivity.this)
                                    .setTitle("Could not send message")
                                    .setMessage(message)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }
                });
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(4));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF0D5BE1));
        return button;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
