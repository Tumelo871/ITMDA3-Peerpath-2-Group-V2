package com.example.peerpath;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;
import org.json.JSONArray;
import org.json.JSONObject;

public class AnnounceActivity extends AppCompatActivity {
    private EditText etAnnouncement;
    private TextView tvRecipients, tvHistoryStatus;
    private MaterialButton btnSend;
    private LinearLayout announcementList;
    private boolean sending = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announce);
        etAnnouncement = findViewById(R.id.etAnnouncement);
        tvRecipients = findViewById(R.id.tvRecipients);
        btnSend = findViewById(R.id.btnSendAnnouncement);
        View back = findViewById(R.id.btnBack);
        announcementList = findViewById(R.id.announcementList);
        tvHistoryStatus = findViewById(R.id.tvHistoryStatus);
        if (etAnnouncement == null || tvRecipients == null || btnSend == null || back == null
                || announcementList == null || tvHistoryStatus == null) {
            Toast.makeText(this, "Announcement layout is missing a required view.", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        back.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendAnnouncement());
        tvRecipients.setText("Recipients are selected from your accepted or completed sessions.");
        loadHistory();
    }

    private void sendAnnouncement() {
        if (sending) return;
        String message = etAnnouncement.getText().toString().trim();
        if (message.isEmpty()) { etAnnouncement.setError("Write your announcement."); etAnnouncement.requestFocus(); return; }
        if (message.length() > 4000) { etAnnouncement.setError("Use 4,000 characters or fewer."); return; }
        sending = true; btnSend.setEnabled(false); btnSend.setText("Sending...");
        SupabaseApi.sendTutorAnnouncement(message, new SupabaseApi.ObjectCallback() {
            @Override public void onSuccess(JSONObject result) {
                runOnUiThread(() -> {
                    sending = false; btnSend.setEnabled(true); btnSend.setText("Send announcement");
                    etAnnouncement.setText("");
                    int count = result == null ? 0 : result.optInt("recipients", 0);
                    Toast.makeText(AnnounceActivity.this, "Announcement sent to " + count + (count == 1 ? " learner." : " learners."), Toast.LENGTH_LONG).show();
                    loadHistory();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    sending = false; btnSend.setEnabled(true); btnSend.setText("Send announcement");
                    Toast.makeText(AnnounceActivity.this, message == null ? "Announcement could not be sent." : message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadHistory() {
        tvHistoryStatus.setText("Loading announcement history..."); announcementList.removeAllViews();
        SupabaseApi.getMyAnnouncements(new SupabaseApi.JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    announcementList.removeAllViews();
                    if (data == null || data.length() == 0) { tvHistoryStatus.setText("No announcements sent yet."); return; }
                    tvHistoryStatus.setText("Your announcements");
                    for (int i = 0; i < data.length(); i++) { JSONObject item = data.optJSONObject(i); if (item != null) addAnnouncementCard(item); }
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> tvHistoryStatus.setText("Could not load history: " + (message == null ? "Unknown error" : message)));
            }
        });
    }

    private void addAnnouncementCard(JSONObject item) {
        String id = item.optString("id", "");
        String message = item.optString("message", "");
        String created = item.optString("created_at", "").replace("T", " ").replace("Z", "");
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); int pad = dp(14); card.setPadding(pad,pad,pad,pad); card.setBackgroundResource(R.drawable.bg_input);
        TextView text = new TextView(this); text.setText(message); text.setTextColor(getColor(R.color.text_primary)); text.setTextSize(15); card.addView(text);
        TextView date = new TextView(this); date.setText(created); date.setTextColor(getColor(R.color.text_secondary)); date.setTextSize(12);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1,-2); dateParams.topMargin=dp(6); card.addView(date,dateParams);
        MaterialButton delete = new MaterialButton(this); delete.setText("Delete announcement"); delete.setTextColor(getColor(R.color.white)); delete.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.peer_blue)));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1,dp(46)); buttonParams.topMargin=dp(8); card.addView(delete,buttonParams);
        delete.setOnClickListener(v -> confirmDelete(id,message));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1,-2); cardParams.bottomMargin=dp(12); announcementList.addView(card,cardParams);
    }

    private void confirmDelete(String id, String message) {
        new AlertDialog.Builder(this).setTitle("Delete announcement?")
                .setMessage("This removes the announcement and its learner notifications.\n\n" + message)
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
                    SupabaseApi.deleteTutorAnnouncement(id, new SupabaseApi.EmptyCallback() {
                        @Override public void onSuccess() { runOnUiThread(() -> { Toast.makeText(AnnounceActivity.this, "Announcement and learner notifications removed.", Toast.LENGTH_LONG).show(); loadHistory(); }); }
                        @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(AnnounceActivity.this, "Delete failed: " + error, Toast.LENGTH_LONG).show()); }
                    });
                }).show();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
