package com.example.peerpath;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

public class MessagesActivity extends AppCompatActivity {

    private LinearLayout messageList;
    private TextView emptyMessage;
    private ProgressBar progress;
    private Button refreshButton;

    private boolean loading = false;
    private int requestNumber = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        messageList = findViewById(R.id.messageListContainer);
        emptyMessage = findViewById(R.id.tvEmptyMessages);
        progress = findViewById(R.id.messagesProgress);
        refreshButton = findViewById(R.id.btnRefreshMessages);

        View backButton = findViewById(R.id.btnBack);

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        refreshButton.setOnClickListener(v -> loadConversations());

        loadConversations();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reload the list after returning from a conversation.
        if (messageList != null && !loading) {
            loadConversations();
        }
    }

    private void loadConversations() {
        if (loading) {
            return;
        }

        if (!SupabaseApi.isLoggedIn()) {
            showEmpty("Your session has expired. Please sign in again.");
            return;
        }

        loading = true;
        final int requestId = ++requestNumber;

        progress.setVisibility(View.VISIBLE);
        refreshButton.setEnabled(false);
        emptyMessage.setVisibility(View.GONE);
        messageList.removeAllViews();

        SupabaseApi.getAcceptedSessions(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray sessions) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()
                            || requestId != requestNumber) {
                        return;
                    }

                    if (sessions == null || sessions.length() == 0) {
                        finishLoading();
                        showEmpty(
                                "No conversations yet.\n\n"
                                        + "An accepted tutoring session will appear here."
                        );
                        return;
                    }

                    AtomicInteger remaining =
                            new AtomicInteger(sessions.length());

                    for (int i = 0; i < sessions.length(); i++) {
                        JSONObject session = sessions.optJSONObject(i);

                        if (session == null) {
                            finishOne(remaining, requestId);
                            continue;
                        }

                        String sessionId = session.optString("id", "");
                        String learnerId = session.optString("learner_id", "");
                        String tutorId = session.optString("tutor_id", "");
                        String currentUserId = SupabaseApi.userId();

                        String otherUserId;

                        if (currentUserId != null
                                && currentUserId.equals(learnerId)) {
                            otherUserId = tutorId;
                        } else if (currentUserId != null
                                && currentUserId.equals(tutorId)) {
                            otherUserId = learnerId;
                        } else {
                            finishOne(remaining, requestId);
                            continue;
                        }

                        String subject =
                                session.optString("subject", "Tutoring");

                        if (sessionId.trim().isEmpty()
                                || otherUserId.trim().isEmpty()) {
                            finishOne(remaining, requestId);
                            continue;
                        }

                        loadParticipant(
                                sessionId,
                                otherUserId,
                                subject,
                                requestId,
                                remaining
                        );
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()
                            || requestId != requestNumber) {
                        return;
                    }

                    finishLoading();
                    showEmpty("Couldn't load conversations: " + message);
                });
            }
        });
    }

    private void loadParticipant(
            String sessionId,
            String otherUserId,
            String subject,
            int requestId,
            AtomicInteger remaining
    ) {
        // Use the conversation-specific profile lookup. Do not silently label
        // every failed profile request as "PeerPath User"; that hides the cause.
        SupabaseApi.getConversationProfile(otherUserId,
                new SupabaseApi.ObjectCallback() {

                    @Override
                    public void onSuccess(JSONObject profile) {
                        String name = profile.optString("full_name", "").trim();
                        if (name.isEmpty()) {
                            name = profile.optString("display_name", "").trim();
                        }
                        if (name.isEmpty()) {
                            name = profile.optString("name", "").trim();
                        }
                        final String participantName = name.isEmpty()
                                ? "Name unavailable" : name;

                        loadLatestMessage(sessionId, (preview, time) ->
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()
                                            || requestId != requestNumber) return;

                                    addConversation(sessionId, otherUserId, subject,
                                            participantName, preview, time);
                                    finishOne(remaining, requestId);
                                })
                        );
                    }

                    @Override
                    public void onError(String message) {
                        loadLatestMessage(sessionId, (preview, time) ->
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()
                                            || requestId != requestNumber) return;

                                    addConversation(sessionId, otherUserId, subject,
                                            "Name unavailable", preview, time);
                                    finishOne(remaining, requestId);
                                    Toast.makeText(MessagesActivity.this,
                                            "Couldn't load a participant profile: "
                                                    + (message == null || message.trim().isEmpty()
                                                    ? "Unknown profile error" : message),
                                            Toast.LENGTH_LONG).show();
                                })
                        );
                    }
                });
    }

    private interface LatestMessageCallback {
        void onComplete(String preview, String time);
    }

    private void loadLatestMessage(
            String sessionId,
            LatestMessageCallback callback
    ) {
        SupabaseApi.getMessages(sessionId,
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray messages) {
                        String preview = "No messages yet — say hello!";
                        String time = "";

                        if (messages != null && messages.length() > 0) {
                            JSONObject latest =
                                    messages.optJSONObject(messages.length() - 1);

                            if (latest != null) {
                                preview = latest.optString("body", "Message");

                                if (preview.trim().isEmpty()) {
                                    preview = "Message";
                                }

                                time = formatTime(
                                        latest.optString("created_at", ""));
                            }
                        }

                        callback.onComplete(preview, time);
                    }

                    @Override
                    public void onError(String message) {
                        // Still allow the user to open the conversation.
                        callback.onComplete("Open conversation", "");
                    }
                });
    }

    private void finishOne(AtomicInteger remaining, int requestId) {
        if (remaining.decrementAndGet() != 0) {
            return;
        }

        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()
                    || requestId != requestNumber) {
                return;
            }

            finishLoading();

            if (messageList.getChildCount() == 0) {
                showEmpty(
                        "No conversations could be displayed. "
                                + "Check your session data and Supabase permissions."
                );
            } else {
                emptyMessage.setVisibility(View.GONE);
            }
        });
    }

    private void finishLoading() {
        loading = false;
        progress.setVisibility(View.GONE);
        refreshButton.setEnabled(true);
    }

    private void addConversation(
            String sessionId,
            String otherUserId,
            String subject,
            String participantName,
            String preview,
            String time
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(Color.WHITE);
        cardBackground.setCornerRadius(dp(18));
        cardBackground.setStroke(dp(1), Color.rgb(225, 235, 247));
        card.setBackground(cardBackground);
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(dp(6), dp(6), dp(6), dp(6));
        messageList.addView(card, cardParams);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);

        TextView avatar = new TextView(this);
        avatar.setText(getInitials(participantName));
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(15);
        avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);

        GradientDrawable avatarBackground = new GradientDrawable();
        avatarBackground.setColor(Color.rgb(13, 91, 225));
        avatarBackground.setShape(GradientDrawable.OVAL);
        avatar.setBackground(avatarBackground);

        top.addView(avatar, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                );

        detailsParams.setMargins(dp(12), 0, dp(8), 0);
        top.addView(details, detailsParams);

        TextView name = new TextView(this);
        name.setText(participantName);
        name.setTextColor(Color.rgb(20, 37, 65));
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        details.addView(name);

        TextView subjectText = new TextView(this);
        subjectText.setText(subject);
        subjectText.setTextColor(Color.rgb(83, 105, 135));
        subjectText.setTextSize(12);
        details.addView(subjectText);

        TextView timeText = new TextView(this);
        timeText.setText(time);
        timeText.setTextColor(Color.rgb(83, 105, 135));
        timeText.setTextSize(11);
        top.addView(timeText);

        TextView previewText = new TextView(this);
        previewText.setText(preview);
        previewText.setTextColor(Color.rgb(65, 81, 105));
        previewText.setTextSize(14);
        previewText.setMaxLines(2);
        previewText.setPadding(dp(58), dp(8), 0, 0);
        card.addView(previewText);

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MessagesActivity.this,
                    ChatActivity.class
            );

            intent.putExtra("sessionId", sessionId);
            intent.putExtra("receiverId", otherUserId);
            intent.putExtra("title", participantName);

            startActivity(intent);
        });
    }

    private void showEmpty(String message) {
        messageList.removeAllViews();
        emptyMessage.setText(message);
        emptyMessage.setVisibility(View.VISIBLE);
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "P";
        }

        String[] parts = name.trim().split("\\s+");

        if (parts.length == 1) {
            return parts[0].substring(0, 1)
                    .toUpperCase(Locale.getDefault());
        }

        return (
                parts[0].substring(0, 1)
                        + parts[parts.length - 1].substring(0, 1)
        ).toUpperCase(Locale.getDefault());
    }

    private String formatTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser =
                        new SimpleDateFormat(pattern, Locale.US);

                parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = parser.parse(value);

                if (date != null) {
                    return new SimpleDateFormat(
                            "HH:mm",
                            Locale.getDefault()
                    ).format(date);
                }
            } catch (ParseException ignored) {
                // Try the next timestamp format.
            }
        }

        return "";
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }
}
