
package com.example.peerpath;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MySessionsActivity extends AppCompatActivity {

    private RecyclerView rv;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);

        TextView header = findViewById(R.id.tvHeader);
        header.setText("My Sessions");

        empty = findViewById(R.id.tvEmptyState);
        empty.setText("Loading your sessions...");
        empty.setVisibility(View.VISIBLE);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.etSearchModule).setVisibility(View.GONE);
        findViewById(R.id.btnPrimaryAction).setVisibility(View.GONE);

        rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));

        load();
    }

    private void load() {
        SupabaseApi.getMySessions(new SupabaseApi.JsonCallback() {
            @Override
            public void onSuccess(JSONArray a) {
                runOnUiThread(() -> render(a));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    empty.setText("Could not load sessions. Please try again.");
                    empty.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void render(JSONArray data) {
        List<JSONObject> rows = new ArrayList<>();

        for (int i = 0; i < data.length(); i++) {
            JSONObject session = data.optJSONObject(i);
            if (session != null) {
                rows.add(session);
            }
        }

        rv.setAdapter(new RecyclerView.Adapter<Holder>() {

            @Override
            public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
                LinearLayout card = Ui.card(MySessionsActivity.this);

                int padding = Ui.dp(MySessionsActivity.this, 16);
                card.setPadding(padding, padding, padding, padding);
                card.setOrientation(LinearLayout.VERTICAL);

                RecyclerView.LayoutParams params =
                        new RecyclerView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        );

                params.setMargins(
                        0,
                        0,
                        0,
                        Ui.dp(MySessionsActivity.this, 12)
                );

                card.setLayoutParams(params);
                card.setElevation(Ui.dp(MySessionsActivity.this, 2));

                return new Holder(card);
            }

            @Override
            public void onBindViewHolder(Holder holder, int position) {
                JSONObject session = rows.get(position);

                String status = session.optString("status", "pending")
                        .trim()
                        .toLowerCase(Locale.ROOT);

                holder.card.removeAllViews();

                int backgroundColor;
                int accentColor;
                int borderColor;

                switch (status) {
                    case "accepted":
                        backgroundColor = Color.parseColor("#DCFCE7");
                        accentColor = Color.parseColor("#15803D");
                        borderColor = Color.parseColor("#86EFAC");
                        break;

                    case "completed":
                        backgroundColor = Color.parseColor("#DBEAFE");
                        accentColor = Color.parseColor("#1D4ED8");
                        borderColor = Color.parseColor("#93C5FD");
                        break;

                    case "rejected":
                    case "declined":
                        backgroundColor = Color.parseColor("#FEE2E2");
                        accentColor = Color.parseColor("#B91C1C");
                        borderColor = Color.parseColor("#FCA5A5");
                        break;

                    case "cancelled":
                    case "canceled":
                        backgroundColor = Color.parseColor("#F3F4F6");
                        accentColor = Color.parseColor("#4B5563");
                        borderColor = Color.parseColor("#D1D5DB");
                        break;

                    default:
                        // Pending or other statuses
                        backgroundColor = Color.parseColor("#FEF3C7");
                        accentColor = Color.parseColor("#92400E");
                        borderColor = Color.parseColor("#FCD34D");
                        break;
                }

                GradientDrawable cardBackground = new GradientDrawable();
                cardBackground.setColor(backgroundColor);
                cardBackground.setCornerRadius(
                        Ui.dp(MySessionsActivity.this, 14)
                );
                cardBackground.setStroke(
                        Ui.dp(MySessionsActivity.this, 1),
                        borderColor
                );

                holder.card.setBackground(cardBackground);

                TextView subject = Ui.text(
                        MySessionsActivity.this,
                        session.optString("subject", "Tutoring"),
                        18,
                        R.color.text_primary,
                        true
                );

                Ui.add(holder.card, subject, 0);

                String date = session.optString("session_date", "");
                String time = session.optString("session_time", "");

                String dateTime = date;
                if (!time.isEmpty()) {
                    dateTime = date.isEmpty() ? time : date + " at " + time;
                }

                TextView dateText = Ui.text(
                        MySessionsActivity.this,
                        dateTime,
                        14,
                        R.color.text_secondary,
                        false
                );

                Ui.add(holder.card, dateText, 6);

                TextView statusText = Ui.text(
                        MySessionsActivity.this,
                        "Status: " + formatStatus(status),
                        14,
                        R.color.text_primary,
                        true
                );

                statusText.setTextColor(accentColor);
                Ui.add(holder.card, statusText, 8);

                boolean accepted = "accepted".equals(status);

                holder.card.setClickable(accepted);
                holder.card.setFocusable(accepted);

                if (accepted) {
                    holder.card.setOnClickListener(v -> {
                        Intent intent = new Intent(
                                MySessionsActivity.this,
                                ChatActivity.class
                        );

                        intent.putExtra("sessionId", session.optString("id"));

                        String currentUserId = SupabaseApi.userId();
                        String learnerId = session.optString("learner_id");
                        String tutorId = session.optString("tutor_id");

                        String otherUserId = currentUserId.equals(learnerId)
                                ? tutorId
                                : learnerId;

                        intent.putExtra("receiverId", otherUserId);
                        startActivity(intent);
                    });
                } else {
                    holder.card.setOnClickListener(null);
                }
            }

            @Override
            public int getItemCount() {
                return rows.size();
            }
        });

        boolean hasSessions = !rows.isEmpty();

        empty.setText("You have no sessions yet.");
        empty.setVisibility(hasSessions ? View.GONE : View.VISIBLE);
    }

    private String formatStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "Pending";
        }

        switch (status) {
            case "accepted":
                return "Accepted";
            case "completed":
                return "Completed";
            case "rejected":
            case "declined":
                return "Rejected";
            case "cancelled":
            case "canceled":
                return "Cancelled";
            case "pending":
                return "Pending";
            default:
                return status.substring(0, 1).toUpperCase(Locale.ROOT)
                        + status.substring(1);
        }
    }

    static class Holder extends RecyclerView.ViewHolder {

        LinearLayout card;

        Holder(LinearLayout card) {
            super(card);
            this.card = card;
        }
    }
}
