
package com.example.peerpath;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

public class ChatActivity extends AppCompatActivity {

    private static final long REFRESH_INTERVAL_MS = 3000L;

    private String sessionId;
    private String receiverId;
    private String initialTitle = "PeerPath chat";
    private String lastMessageSignature = "";

    private boolean requestInFlight = false;
    private boolean screenVisible = false;
    private boolean initialLoadFinished = false;
    private boolean sending = false;

    private TextView tvTitle;
    private TextView tvStatus;
    private TextView btnSend;
    private EditText etMessage;
    private LinearLayout messageContainer;
    private ScrollView messageScroll;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!screenVisible || isFinishing() || isDestroyed()) {
                return;
            }

            loadMessages(false);
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_chat);

        sessionId = getIntent().getStringExtra("sessionId");
        receiverId = getIntent().getStringExtra("receiverId");

        String suppliedTitle = getIntent().getStringExtra("title");

        if (!TextUtils.isEmpty(suppliedTitle)) {
            initialTitle = suppliedTitle.trim();
        }

        tvTitle = findViewById(R.id.tvChatTitle);
        tvStatus = findViewById(R.id.tvChatStatus);
        messageContainer = findViewById(R.id.messageContainer);
        messageScroll = findViewById(R.id.messageScroll);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        tvTitle.setText(initialTitle);
        tvStatus.setText("Connecting...");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> sendMessage());

        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        findViewById(R.id.btnAttach).setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "File sharing is not connected yet.",
                        Toast.LENGTH_SHORT
                ).show()
        );

        if (!SupabaseApi.isLoggedIn()) {
            Toast.makeText(
                    this,
                    "Please sign in again to use chat.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        if (TextUtils.isEmpty(sessionId)) {
            tvStatus.setText("No session selected");
            btnSend.setEnabled(false);
            etMessage.setEnabled(false);

            Toast.makeText(
                    this,
                    "This conversation has no session ID.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (TextUtils.isEmpty(receiverId)) {
            tvStatus.setText("Recipient unavailable");
            btnSend.setEnabled(false);
            etMessage.setEnabled(false);

            Toast.makeText(
                    this,
                    "The other participant could not be identified. Please open the chat from your session or messages list.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        loadParticipantName();
    }

    // ---------------------------------------------------------
    // PARTICIPANT NAME
    // ---------------------------------------------------------

    private void loadParticipantName() {
        SupabaseApi.getConversationProfile(
                receiverId,
                new SupabaseApi.ObjectCallback() {

                    @Override
                    public void onSuccess(JSONObject profile) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            String name = profile.optString(
                                    "full_name", ""
                            ).trim();

                            if (!name.isEmpty()) {
                                tvTitle.setText(name);
                            } else {
                                tvTitle.setText(initialTitle);
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            // Keep the supplied title if the profile
                            // cannot be read under the current RLS rules.
                            tvTitle.setText(initialTitle);
                            tvStatus.setText(
                                    "Name unavailable; messages can still refresh"
                            );
                        });
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // LIFECYCLE AND AUTOMATIC REFRESH
    // ---------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();

        screenVisible = true;

        handler.removeCallbacks(refreshRunnable);

        loadMessages(true);

        handler.postDelayed(
                refreshRunnable,
                REFRESH_INTERVAL_MS
        );
    }

    @Override
    protected void onPause() {
        screenVisible = false;

        handler.removeCallbacks(refreshRunnable);

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------------------------------------------------------
    // LOAD MESSAGES
    // ---------------------------------------------------------

    private void loadMessages(boolean showLoading) {
        if (TextUtils.isEmpty(sessionId)) {
            return;
        }

        if (requestInFlight) {
            return;
        }

        if (!screenVisible && !showLoading) {
            return;
        }

        requestInFlight = true;

        if (showLoading && messageContainer.getChildCount() == 0) {
            tvStatus.setText("Loading messages...");
        }

        SupabaseApi.getMessages(
                sessionId,
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray data) {
                        runOnUiThread(() -> {
                            requestInFlight = false;

                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            tvStatus.setText(
                                    "Messages refresh automatically"
                            );

                            String signature = createSignature(data);

                            if (!signature.equals(lastMessageSignature)) {
                                boolean wasNearBottom = isNearBottom();

                                renderMessages(data);

                                lastMessageSignature = signature;

                                if (wasNearBottom) {
                                    scrollToBottom();
                                }
                            }

                            initialLoadFinished = true;
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            requestInFlight = false;

                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            if (!initialLoadFinished) {
                                tvStatus.setText(
                                        "Could not load messages. Retrying..."
                                );
                            } else {
                                tvStatus.setText(
                                        "Connection issue — retrying..."
                                );
                            }
                        });
                    }
                }
        );
    }

    private String createSignature(JSONArray data) {
        if (data == null) {
            return "null";
        }

        StringBuilder signature = new StringBuilder();

        for (int i = 0; i < data.length(); i++) {
            JSONObject message = data.optJSONObject(i);

            if (message == null) {
                continue;
            }

            signature.append(message.optString("id", ""))
                    .append('|')
                    .append(message.optString("sender_id", ""))
                    .append('|')
                    .append(message.optString("created_at", ""))
                    .append('|')
                    .append(message.optString("body", ""))
                    .append('\n');
        }

        return signature.toString();
    }

    // ---------------------------------------------------------
    // RENDER CHAT BUBBLES
    // ---------------------------------------------------------

    private void renderMessages(JSONArray data) {
        messageContainer.removeAllViews();

        String currentUserId = SupabaseApi.userId();

        if (data == null || data.length() == 0) {
            TextView empty = new TextView(this);

            empty.setText("No messages yet. Say hello!");
            empty.setTextColor(Color.rgb(103, 119, 143));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(
                    dp(20), dp(28), dp(20), dp(28)
            );

            messageContainer.addView(empty);
            return;
        }

        for (int i = 0; i < data.length(); i++) {
            JSONObject message = data.optJSONObject(i);

            if (message == null) {
                continue;
            }

            String body = message.optString("body", "").trim();

            if (body.isEmpty()) {
                continue;
            }

            String senderId = message.optString("sender_id", "");

            boolean outgoing = !TextUtils.isEmpty(currentUserId)
                    && currentUserId.equals(senderId);

            LinearLayout row = new LinearLayout(this);

            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(
                    outgoing ? Gravity.END : Gravity.START
            );

            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            rowParams.bottomMargin = dp(4);
            row.setLayoutParams(rowParams);

            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(
                    dp(13), dp(9), dp(13), dp(7)
            );

            bubble.setBackgroundResource(
                    outgoing
                            ? R.drawable.bg_chat_bubble_out
                            : R.drawable.bg_chat_bubble_in
            );

            LinearLayout.LayoutParams bubbleParams =
                    new LinearLayout.LayoutParams(
                            getResources().getDisplayMetrics().widthPixels
                                    * 4 / 5,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            bubbleParams.gravity = outgoing
                    ? Gravity.END
                    : Gravity.START;

            bubble.setLayoutParams(bubbleParams);

            TextView bodyView = new TextView(this);
            bodyView.setText(body);
            bodyView.setTextSize(15);
            bodyView.setTextColor(Color.rgb(22, 35, 55));
            bodyView.setTextIsSelectable(true);

            bubble.addView(bodyView);

            TextView timeView = new TextView(this);
            timeView.setText(
                    formatTime(message.optString("created_at", ""))
            );
            timeView.setTextSize(10);
            timeView.setTextColor(Color.rgb(100, 117, 139));
            timeView.setGravity(Gravity.END);
            timeView.setPadding(0, dp(4), 0, 0);

            bubble.addView(timeView);
            row.addView(bubble);
            messageContainer.addView(row);
        }
    }

    // ---------------------------------------------------------
    // SEND MESSAGE
    // ---------------------------------------------------------

    private void sendMessage() {
        if (sending) {
            return;
        }

        String body = etMessage.getText() == null
                ? ""
                : etMessage.getText().toString().trim();

        if (body.isEmpty()) {
            etMessage.setError("Enter a message.");
            return;
        }

        if (body.length() > 4000) {
            etMessage.setError(
                    "Please keep messages under 4,000 characters."
            );
            return;
        }

        if (TextUtils.isEmpty(sessionId)
                || TextUtils.isEmpty(receiverId)) {
            Toast.makeText(
                    this,
                    "The session or recipient is missing.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        sending = true;
        btnSend.setEnabled(false);
        etMessage.setEnabled(false);
        tvStatus.setText("Sending message...");

        SupabaseApi.sendMessage(
                sessionId,
                receiverId,
                body,
                new SupabaseApi.EmptyCallback() {

                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            sending = false;
                            btnSend.setEnabled(true);
                            etMessage.setEnabled(true);

                            etMessage.setText("");
                            tvStatus.setText(
                                    "Messages refresh automatically"
                            );

                            // Force a fresh render after sending.
                            lastMessageSignature = "";
                            loadMessages(true);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            sending = false;
                            btnSend.setEnabled(true);
                            etMessage.setEnabled(true);

                            tvStatus.setText("Message not sent");

                            Toast.makeText(
                                    ChatActivity.this,
                                    "Message not sent: " + error,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // SCROLLING AND TIME FORMATTING
    // ---------------------------------------------------------

    private boolean isNearBottom() {
        if (messageScroll == null) {
            return true;
        }

        int difference = messageContainer.getBottom()
                - (messageScroll.getScrollY()
                + messageScroll.getHeight());

        return difference < dp(100);
    }

    private void scrollToBottom() {
        messageScroll.post(
                () -> messageScroll.fullScroll(View.FOCUS_DOWN)
        );
    }

    private String formatTime(String value) {
        if (TextUtils.isEmpty(value)) {
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
                SimpleDateFormat input =
                        new SimpleDateFormat(pattern, Locale.US);

                input.setTimeZone(TimeZone.getTimeZone("UTC"));

                Date date = input.parse(value);

                if (date != null) {
                    return new SimpleDateFormat(
                            "HH:mm",
                            Locale.getDefault()
                    ).format(date);
                }
            } catch (ParseException ignored) {
                // Try the next supported timestamp format.
            }
        }

        return value.length() >= 16
                ? value.substring(11, 16)
                : "";
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density
                        + 0.5f
        );
    }
}
