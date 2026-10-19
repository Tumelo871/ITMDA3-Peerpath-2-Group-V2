
package com.example.peerpath;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

public class TutorSearchActivity extends AppCompatActivity {

    private EditText etSearch;
    private TextView tvSearchStatus;
    private LinearLayout tutorContainer;
    private MaterialButton btnBack;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int requestNumber = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutor_search);

        SupabaseApi.init(getApplicationContext());

        etSearch = findViewById(R.id.etSearch);
        tvSearchStatus = findViewById(R.id.tvSearchStatus);
        tutorContainer = findViewById(R.id.tutorContainer);

        // IMPORTANT: this must match the type in activity_tutor_search.xml.
        View backView = findViewById(R.id.btnBack);

        if (etSearch == null || tvSearchStatus == null
                || tutorContainer == null) {
            Toast.makeText(this,
                    "Tutor search layout is missing a required view.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (backView != null) {
            backView.setOnClickListener(v -> finish());
        }

        if (!SupabaseApi.isConfigured()) {
            tvSearchStatus.setText(
                    "Supabase is not configured. Check your connection settings.");
            return;
        }

        if (!SupabaseApi.isLoggedIn()) {
            tvSearchStatus.setText("Please sign in to find a tutor.");
            return;
        }

        etSearch.setSingleLine(true);
        etSearch.setHint("Search tutor name or module code");

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                scheduleSearch(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        loadTutors("");
    }

    private void scheduleSearch(String query) {
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }

        String searchText = query == null ? "" : query.trim();

        pendingSearch = () -> loadTutors(searchText);
        handler.postDelayed(pendingSearch, 350);
    }

    private void loadTutors(String query) {
        final int currentRequest = ++requestNumber;

        tvSearchStatus.setText("Loading verified tutors...");
        tutorContainer.removeAllViews();

        SupabaseApi.getVerifiedTutors(
                query == null || query.trim().isEmpty()
                        ? null : query.trim(),
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray data) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (currentRequest != requestNumber) return;

                            displayTutors(data);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (currentRequest != requestNumber) return;

                            tutorContainer.removeAllViews();
                            tvSearchStatus.setText(
                                    "Could not load tutors: "
                                            + clean(message));
                        });
                    }
                }
        );
    }

    private void displayTutors(JSONArray tutors) {
        tutorContainer.removeAllViews();

        if (tutors == null || tutors.length() == 0) {
            tvSearchStatus.setText(
                    "No verified tutors found. Try another name or module code.");
            return;
        }

        int displayed = 0;

        for (int i = 0; i < tutors.length(); i++) {
            JSONObject tutor = tutors.optJSONObject(i);

            if (tutor == null) continue;

            String tutorId = clean(tutor.optString("id", ""));
            String name = clean(tutor.optString("full_name", ""));
            String modules = clean(tutor.optString("module_codes", ""));
            String subject = clean(tutor.optString("tutor_subject", ""));
            String bio = clean(tutor.optString("tutor_bio", ""));
            String experience = clean(
                    tutor.optString("tutor_experience", ""));

            if (name.isEmpty()) {
                name = "PeerPath Tutor";
            }

            if (tutorId.isEmpty()) {
                // Do not show a card that cannot open a real profile.
                continue;
            }

            if (modules.isEmpty()) {
                modules = "Modules not added yet";
            }

            addTutorCard(
                    tutorId,
                    name,
                    subject,
                    modules,
                    bio,
                    experience
            );

            displayed++;
        }

        if (displayed == 0) {
            tvSearchStatus.setText(
                    "No usable tutor profiles were returned.");
        } else if (displayed == 1) {
            tvSearchStatus.setText("1 verified tutor found");
        } else {
            tvSearchStatus.setText(
                    displayed + " verified tutors found");
        }
    }

    private void addTutorCard(
            String tutorId,
            String name,
            String subject,
            String modules,
            String bio,
            String experience) {

        MaterialCardView card = new MaterialCardView(this);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);

        cardParams.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(cardParams);
        card.setRadius(dp(16));
        card.setCardElevation(dp(3));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.rgb(225, 234, 246));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView nameView = createText(name, 19, true);
        nameView.setTextColor(Color.rgb(8, 43, 111));
        content.addView(nameView);

        TextView verified = createText(
                "✓ Verified tutor", 12, true);
        verified.setTextColor(Color.rgb(20, 125, 86));

        LinearLayout.LayoutParams verifiedParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        verifiedParams.topMargin = dp(5);
        content.addView(verified, verifiedParams);

        if (!subject.isEmpty()) {
            addDetail(content, "Subject: " + subject, 14, true);
        }

        addDetail(content, "Modules: " + modules, 14, true);

        if (!bio.isEmpty()) {
            addDetail(content, bio, 14, false);
        }

        if (!experience.isEmpty()) {
            addDetail(
                    content,
                    "Experience: " + experience,
                    13,
                    false);
        }

        TextView viewProfile = createText(
                "View profile  →", 14, true);
        viewProfile.setTextColor(Color.rgb(13, 91, 225));

        LinearLayout.LayoutParams linkParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        linkParams.topMargin = dp(14);
        content.addView(viewProfile, linkParams);

        card.addView(content);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(
                    TutorSearchActivity.this,
                    TutorProfileActivity.class);

            intent.putExtra("tutorId", tutorId);
            intent.putExtra("tutorName", name);
            intent.putExtra("subject", subject);

            startActivity(intent);
        });

        tutorContainer.addView(card);
    }

    private void addDetail(
            LinearLayout container,
            String text,
            int size,
            boolean bold) {

        TextView view = createText(text, size, bold);
        view.setTextColor(Color.rgb(80, 95, 115));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);

        params.topMargin = dp(10);
        container.addView(view, params);
    }

    private TextView createText(
            String text,
            int sizeSp,
            boolean bold) {

        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setGravity(Gravity.START);
        view.setTextColor(Color.rgb(45, 55, 72));
        view.setLineSpacing(dp(2), 1.0f);

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        return view;
    }

    private String clean(String value) {
        if (value == null
                || "null".equalsIgnoreCase(value.trim())) {
            return "";
        }

        return value.trim();
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        requestNumber++;
        super.onDestroy();
    }
}
