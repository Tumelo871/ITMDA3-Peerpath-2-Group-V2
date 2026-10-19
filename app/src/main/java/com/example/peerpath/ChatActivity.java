package com.example.peerpath;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * PeerPath chat screen. Uses the existing Supabase REST helper and refreshes
 * while visible. This is near-real-time polling, not a Supabase Realtime socket.
 */
public class ChatActivity extends AppCompatActivity {
    private static final long POLL_INTERVAL_MS = 2500L;

    private String sessionId;
    private String receiverId;
    private String lastRenderedSignature = "";
    private boolean requestInFlight = false;
    private boolean screenVisible = false;

    private TextView tvTitle;
    private TextView tvStatus;
    private LinearLayout messageContainer;
    private ScrollView messageScroll;
    private EditText etMessage;
    private View btnSend;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (!screenVisible || isFinishing()) return;
            loadMessages(false);
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_chat);

        sessionId = getIntent().getStringExtra("sessionId");
        receiverId = getIntent().getStringExtra("receiverId");
        String chatTitle = getIntent().getStringExtra("title");

        tvTitle = findViewById(R.id.tvChatTitle);
        tvStatus = findViewById(R.id.tvChatStatus);
        messageContainer = findViewById(R.id.messageContainer);
        messageScroll = findViewById(R.id.messageScroll);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        tvTitle.setText(TextUtils.isEmpty(chatTitle) ? "PeerPath chat" : chatTitle);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> send());
        findViewById(R.id.btnAttach).setOnClickListener(v ->
                Toast.makeText(this, "Resource sharing can be connected next; messages are available now.", Toast.LENGTH_SHORT).show());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });

        if (!SupabaseApi.isLoggedIn()) {
            Toast.makeText(this, "Please sign in again to use chat.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (TextUtils.isEmpty(sessionId)) {
            tvStatus.setText("No session selected");
            Toast.makeText(this, "This chat needs a valid session ID.", Toast.LENGTH_LONG).show();
            btnSend.setEnabled(false);
            etMessage.setEnabled(false);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        screenVisible = true;
        handler.removeCallbacks(pollRunnable);
        loadMessages(true);
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    @Override protected void onPause() {
        screenVisible = false;
        handler.removeCallbacks(pollRunnable);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void loadMessages(boolean showLoading) {
        if (!screenVisible && !showLoading) return;
        if (TextUtils.isEmpty(sessionId) || requestInFlight) return;
        requestInFlight = true;
        if (showLoading && messageContainer.getChildCount() == 0) {
            tvStatus.setText("Connecting…");
        }
        SupabaseApi.getMessages(sessionId, new SupabaseApi.JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    requestInFlight = false;
                    if (isFinishing() || isDestroyed()) return;
                    tvStatus.setText("Messages refresh automatically");
                    String signature = signatureOf(data);
                    if (!signature.equals(lastRenderedSignature)) {
                        boolean nearBottom = isNearBottom();
                        renderMessages(data);
                        lastRenderedSignature = signature;
                        if (nearBottom || lastRenderedSignature.isEmpty()) scrollToBottom();
                        else scrollToBottom(); // keep the latest messages visible, WhatsApp-style
                    }
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    requestInFlight = false;
                    if (isFinishing() || isDestroyed()) return;
                    tvStatus.setText("Connection issue — retrying automatically");
                    // Avoid toast spam during background polling. Initial failure is visible in status.
                });
            }
        });
    }

    private String signatureOf(JSONArray data) {
        if (data == null) return "null";
        StringBuilder b = new StringBuilder().append(data.length());
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o != null) {
                b.append('|').append(o.optString("id", ""))
                        .append(':').append(o.optString("created_at", ""))
                        .append(':').append(o.optString("body", ""));
            }
        }
        return b.toString();
    }

    private void renderMessages(JSONArray data) {
        messageContainer.removeAllViews();
        String currentUserId = SupabaseApi.userId();
        if (data == null || data.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No messages yet. Say hello 👋");
            empty.setTextColor(Color.rgb(103, 119, 143));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(20, 28, 20, 28);
            messageContainer.addView(empty);
            return;
        }

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            String body = item.optString("body", "");
            if (body.trim().isEmpty()) continue;
            boolean outgoing = currentUserId != null && currentUserId.equals(item.optString("sender_id", ""));

            LinearLayout line = new LinearLayout(this);
            line.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            line.setGravity(outgoing ? Gravity.END : Gravity.START);
            line.setPadding(0, 3, 0, 3);

            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(13), dp(9), dp(13), dp(7));
            bubble.setBackgroundResource(outgoing ? R.drawable.bg_chat_bubble_out : R.drawable.bg_chat_bubble_in);
            LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT);
            bubbleParams.width = getResources().getDisplayMetrics().widthPixels * 4 / 5;
            bubbleParams.gravity = outgoing ? Gravity.END : Gravity.START;
            bubble.setLayoutParams(bubbleParams);

            TextView bodyView = new TextView(this);
            bodyView.setText(body);
            bodyView.setTextSize(15);
            bodyView.setTextColor(Color.rgb(22, 35, 55));
            bodyView.setTextIsSelectable(true);
            bubble.addView(bodyView);

            String time = formatTime(item.optString("created_at", ""));
            TextView timeView = new TextView(this);
            timeView.setText(time);
            timeView.setTextSize(10);
            timeView.setTextColor(Color.rgb(100, 117, 139));
            timeView.setGravity(Gravity.END);
            timeView.setPadding(0, dp(4), 0, 0);
            bubble.addView(timeView);
            line.addView(bubble);
            messageContainer.addView(line);
        }
    }

    private String formatTime(String iso) {
        if (TextUtils.isEmpty(iso)) return "";
        try {
            // ISO values from Postgres are normally UTC and end in Z.
            String value = iso;
            if (value.length() >= 19) {
                String core = value.substring(0, 19);
                SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                input.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = input.parse(core);
                if (date != null) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
            }
        } catch (Exception ignored) { }
        return iso.length() >= 16 ? iso.substring(11, 16) : "";
    }

    private boolean isNearBottom() {
        if (messageScroll == null) return true;
        int diff = messageContainer.getBottom() - (messageScroll.getScrollY() + messageScroll.getHeight());
        return diff < dp(100);
    }

    private void scrollToBottom() {
        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void send() {
        String body = etMessage.getText() == null ? "" : etMessage.getText().toString().trim();
        if (body.isEmpty()) return;
        if (body.length() > 4000) {
            etMessage.setError("Please keep messages under 4,000 characters.");
            return;
        }
        if (TextUtils.isEmpty(sessionId)) {
            Toast.makeText(this, "This session is invalid.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(receiverId)) {
            Toast.makeText(this, "The recipient could not be found. Open chat from a session or tutor conversation.", Toast.LENGTH_LONG).show();
            return;
        }
        btnSend.setEnabled(false);
        etMessage.setEnabled(false);
        SupabaseApi.sendMessage(sessionId, receiverId, body, new SupabaseApi.EmptyCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> {
                    btnSend.setEnabled(true);
                    etMessage.setEnabled(true);
                    etMessage.setText("");
                    etMessage.requestFocus();
                    loadMessages(true);
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    btnSend.setEnabled(true);
                    etMessage.setEnabled(true);
                    Toast.makeText(ChatActivity.this, "Message not sent: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
