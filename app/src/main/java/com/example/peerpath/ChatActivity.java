package com.example.peerpath;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

public class ChatActivity extends AppCompatActivity {

    private String sessionId;
    private String receiverId;

    private TextView title;
    private LinearLayout messages;
    private EditText input;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    /*
     * Do NOT initialize this using:
     *
     * private final Runnable poll = () -> {
     *     handler.postDelayed(poll, 3000);
     * };
     *
     * because that causes a self-reference initialization error.
     */
    private final Runnable poll = new Runnable() {

        @Override
        public void run() {
            loadMessages();

            handler.postDelayed(this, 3000);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_chat);

        sessionId = getIntent().getStringExtra("sessionId");
        receiverId = getIntent().getStringExtra("receiverId");

        title = findViewById(R.id.tvChatTitle);
        messages = findViewById(R.id.messageContainer);
        input = findViewById(R.id.etMessage);

        String chatTitle =
                getIntent().getStringExtra("title");

        if (chatTitle == null || chatTitle.isEmpty()) {
            title.setText("Messages");
        } else {
            title.setText(chatTitle);
        }


        // Back button
        findViewById(R.id.btnBack)
                .setOnClickListener(v -> finish());


        // Send button
        findViewById(R.id.btnSend)
                .setOnClickListener(v -> send());


        // Attachment button
        findViewById(R.id.btnAttach)
                .setOnClickListener(v ->
                        Toast.makeText(
                                ChatActivity.this,
                                "Attachments will be added through Supabase Storage.",
                                Toast.LENGTH_SHORT
                        ).show()
                );


        // Initial message load
        loadMessages();
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Start polling
        handler.removeCallbacks(poll);
        handler.post(poll);
    }


    @Override
    protected void onPause() {
        super.onPause();

        // Stop polling when activity isn't visible
        handler.removeCallbacks(poll);
    }


    private void loadMessages() {

        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }

        SupabaseApi.getMessages(
                sessionId,
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray data) {

                        runOnUiThread(() ->
                                render(data)
                        );
                    }

                    @Override
                    public void onError(String message) {

                        // Don't show an error every 3 seconds
                        // because this method is being polled.
                    }
                }
        );
    }


    private void render(JSONArray data) {

        if (messages == null) {
            return;
        }

        messages.removeAllViews();

        String currentUserId =
                SupabaseApi.userId();

        for (int i = 0; i < data.length(); i++) {

            JSONObject message =
                    data.optJSONObject(i);

            if (message == null) {
                continue;
            }

            String body =
                    message.optString("body", "");

            String senderId =
                    message.optString("sender_id", "");

            TextView textView =
                    new TextView(this);

            textView.setText(body);
            textView.setTextSize(15);

            textView.setPadding(
                    18,
                    12,
                    18,
                    12
            );

            /*
             * Messages sent by the current user
             * appear on the right.
             *
             * Messages received from the tutor/learner
             * appear on the left.
             */
            if (senderId.equals(currentUserId)) {

                textView.setGravity(
                        Gravity.END
                );

            } else {

                textView.setGravity(
                        Gravity.START
                );
            }

            messages.addView(textView);
        }
    }


    private void send() {

        String body =
                input.getText()
                        .toString()
                        .trim();

        if (TextUtils.isEmpty(body)) {
            return;
        }

        if (sessionId == null ||
                sessionId.isEmpty()) {

            Toast.makeText(
                    this,
                    "This session is invalid.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (receiverId == null ||
                receiverId.isEmpty()) {

            Toast.makeText(
                    this,
                    "The recipient could not be found.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        input.setEnabled(false);


        SupabaseApi.sendMessage(
                sessionId,
                receiverId,
                body,
                new SupabaseApi.EmptyCallback() {

                    @Override
                    public void onSuccess() {

                        runOnUiThread(() -> {

                            input.setText("");

                            input.setEnabled(true);

                            loadMessages();
                        });
                    }


                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            input.setEnabled(true);

                            Toast.makeText(
                                    ChatActivity.this,
                                    "Message failed: " + message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }
}