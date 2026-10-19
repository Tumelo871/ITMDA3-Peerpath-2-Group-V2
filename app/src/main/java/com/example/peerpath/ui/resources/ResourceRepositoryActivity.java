package com.example.peerpath.ui.resources;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.R;
import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ResourceRepositoryActivity extends AppCompatActivity {
    private EditText etSearch;
    private Button btnAdd;
    private ProgressBar progress;
    private TextView tvStatus;
    private LinearLayout resourceList;
    private final List<JSONObject> resources = new ArrayList<>();
    private String currentUserId;
    private String role = "learner";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        if (!SupabaseApi.isLoggedIn()) {
            Toast.makeText(this, "Please sign in to view resources.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_resource_repository);
        currentUserId = SupabaseApi.userId();
        etSearch = findViewById(R.id.etResourceSearch);
        btnAdd = findViewById(R.id.btnAddResource);
        progress = findViewById(R.id.progressResources);
        tvStatus = findViewById(R.id.tvResourceStatus);
        resourceList = findViewById(R.id.resourceList);
        findViewById(R.id.btnResourceBack).setOnClickListener(v -> finish());
        btnAdd.setOnClickListener(v -> showAddDialog());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderResources(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        loadProfileThenResources();
    }

    private void loadProfileThenResources() {
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            Toast.makeText(this, "Your session has expired. Please sign in again.", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        setLoading(true, "Checking your account…");
        SupabaseApi.getProfile(currentUserId, new SupabaseApi.ObjectCallback() {
            @Override public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> {
                    role = profile == null ? "learner" : profile.optString("role", "learner");
                    btnAdd.setVisibility(("tutor".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role)) ? View.VISIBLE : View.GONE);
                    loadResources();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    // Read-only browsing remains available; database RLS remains the authority.
                    role = "learner";
                    btnAdd.setVisibility(View.GONE);
                    loadResources();
                    Toast.makeText(ResourceRepositoryActivity.this, "Could not verify your role; adding resources is disabled.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadResources() {
        setLoading(true, "Loading tutoring resources…");
        SupabaseApi.getResources(new SupabaseApi.JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    resources.clear();
                    if (data != null) for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.optJSONObject(i);
                        if (item != null) resources.add(item);
                    }
                    setLoading(false, resources.isEmpty() ? "No resources have been added yet." : resources.size() + " resource(s) available");
                    renderResources();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    setLoading(false, "Couldn't load resources. Check your connection and Supabase permissions.");
                    Toast.makeText(ResourceRepositoryActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showAddDialog() {
        if (!("tutor".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role))) {
            Toast.makeText(this, "Only tutors and administrators can add resources.", Toast.LENGTH_LONG).show(); return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18); form.setPadding(pad, dp(8), pad, 0);
        EditText title = field("Resource title *");
        EditText subject = field("Subject or module (e.g. Java)");
        EditText url = field("Resource URL (https://…)");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText description = field("Description / what learners will learn");
        description.setMinLines(2); description.setGravity(Gravity.TOP | Gravity.START);
        form.addView(title); form.addView(subject); form.addView(url); form.addView(description);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add tutoring resource")
                .setView(form)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Add resource", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            String s = subject.getText().toString().trim();
            String u = url.getText().toString().trim();
            String desc = description.getText().toString().trim();
            if (t.isEmpty()) { title.setError("Enter a title"); return; }
            if (!u.isEmpty() && !isValidWebUrl(u)) { url.setError("Enter a valid http:// or https:// URL"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            SupabaseApi.createResource(t, s, u, desc, new SupabaseApi.EmptyCallback() {
                @Override public void onSuccess() { runOnUiThread(() -> { dialog.dismiss(); Toast.makeText(ResourceRepositoryActivity.this, "Resource added.", Toast.LENGTH_SHORT).show(); loadResources(); }); }
                @Override public void onError(String message) { runOnUiThread(() -> { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); Toast.makeText(ResourceRepositoryActivity.this, "Could not add resource: " + message, Toast.LENGTH_LONG).show(); }); }
            });
        }));
        dialog.show();
    }

    private EditText field(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(false);
        e.setPadding(dp(8), dp(10), dp(8), dp(10));
        return e;
    }

    private void renderResources() {
        if (resourceList == null) return;
        resourceList.removeAllViews();
        String query = etSearch == null ? "" : etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (JSONObject item : resources) {
            String title = item.optString("title", "Untitled resource");
            String subject = item.optString("subject", "");
            String description = item.optString("description", "");
            String url = item.optString("url", "");
            String haystack = (title + " " + subject + " " + description).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            shown++;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.setMargins(0, 0, 0, dp(12)); resourceList.addView(card, cardParams);
            TextView heading = new TextView(this); heading.setText(title); heading.setTextSize(18); heading.setTypeface(null, android.graphics.Typeface.BOLD); card.addView(heading);
            if (!subject.trim().isEmpty()) { TextView sub = new TextView(this); sub.setText("Subject: " + subject); sub.setTextSize(14); sub.setPadding(0, dp(4), 0, 0); card.addView(sub); }
            if (!description.trim().isEmpty()) { TextView desc = new TextView(this); desc.setText(description); desc.setTextSize(14); desc.setPadding(0, dp(6), 0, dp(4)); card.addView(desc); }
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); card.addView(actions);
            if (!url.trim().isEmpty()) {
                Button open = new Button(this); open.setText("Open resource"); actions.addView(open);
                open.setOnClickListener(v -> openUrl(url));
            }
            String owner = item.optString("owner_id", "");
            if ("admin".equalsIgnoreCase(role) || (currentUserId != null && currentUserId.equals(owner))) {
                Button delete = new Button(this); delete.setText("Delete"); actions.addView(delete);
                String id = item.optString("id", "");
                delete.setOnClickListener(v -> confirmDelete(id, title));
            }
        }
        if (tvStatus != null && !resources.isEmpty()) {
            if (shown == 0) tvStatus.setText("No resources match your search.");
            else tvStatus.setText(shown + " resource(s) shown");
        }
    }

    private void confirmDelete(String id, String title) {
        if (id.trim().isEmpty()) { Toast.makeText(this, "This resource has no ID and cannot be deleted.", Toast.LENGTH_LONG).show(); return; }
        new AlertDialog.Builder(this).setTitle("Delete resource?").setMessage("Remove ‘" + title + "’ from the repository?")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, w) -> SupabaseApi.deleteResource(id, new SupabaseApi.EmptyCallback() {
                    @Override public void onSuccess() { runOnUiThread(() -> { Toast.makeText(ResourceRepositoryActivity.this, "Resource deleted.", Toast.LENGTH_SHORT).show(); loadResources(); }); }
                    @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(ResourceRepositoryActivity.this, "Could not delete resource: " + message, Toast.LENGTH_LONG).show()); }
                })).show();
    }

    private void openUrl(String raw) {
        String value = raw.trim();
        if (!isValidWebUrl(value)) { Toast.makeText(this, "This resource does not have a valid web link.", Toast.LENGTH_LONG).show(); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value))); }
        catch (Exception e) { Toast.makeText(this, "No app is available to open this link.", Toast.LENGTH_LONG).show(); }
    }

    private boolean isValidWebUrl(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value.trim());
        String scheme = uri.getScheme();
        return uri.getHost() != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
    }

    private void setLoading(boolean loading, String message) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvStatus.setText(message);
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
