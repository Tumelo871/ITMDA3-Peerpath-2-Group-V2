
package com.example.peerpath.ui.resources;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

    private static final int BLUE = Color.rgb(13, 91, 225);
    private static final int DARK_BLUE = Color.rgb(8, 43, 111);
    private static final int LIGHT_BLUE = Color.rgb(234, 243, 255);
    private static final int TEXT_COLOR = Color.rgb(45, 55, 72);
    private static final int GREY = Color.rgb(100, 116, 139);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseApi.init(getApplicationContext());

        if (!SupabaseApi.isLoggedIn()) {
            Toast.makeText(this,
                    "Please sign in to view resources.",
                    Toast.LENGTH_LONG).show();
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

        findViewById(R.id.btnResourceBack)
                .setOnClickListener(v -> finish());

        btnAdd.setOnClickListener(v -> showAddDialog());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                renderResources();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        loadProfileThenResources();
    }

    // --------------------------------------------------
    // LOAD USER PROFILE
    // --------------------------------------------------

    private void loadProfileThenResources() {

        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            Toast.makeText(this,
                    "Your session has expired. Please sign in again.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setLoading(true, "Checking your account...");

        SupabaseApi.getProfile(
                currentUserId,
                new SupabaseApi.ObjectCallback() {

                    @Override
                    public void onSuccess(JSONObject profile) {
                        runOnUiThread(() -> {

                            role = profile == null
                                    ? "learner"
                                    : profile.optString("role", "learner");

                            boolean canAdd =
                                    "tutor".equalsIgnoreCase(role)
                                            || "admin".equalsIgnoreCase(role);

                            btnAdd.setVisibility(
                                    canAdd ? View.VISIBLE : View.GONE
                            );

                            loadResources();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {

                            role = "learner";
                            btnAdd.setVisibility(View.GONE);

                            loadResources();

                            Toast.makeText(
                                    ResourceRepositoryActivity.this,
                                    "Could not verify your account role.",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    // --------------------------------------------------
    // LOAD RESOURCES FROM SUPABASE
    // --------------------------------------------------

    private void loadResources() {

        setLoading(true, "Loading learning resources...");

        SupabaseApi.getResources(new SupabaseApi.JsonCallback() {

            @Override
            public void onSuccess(JSONArray data) {

                runOnUiThread(() -> {

                    resources.clear();

                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {

                            JSONObject item = data.optJSONObject(i);

                            if (item != null) {
                                resources.add(item);
                            }
                        }
                    }

                    setLoading(
                            false,
                            resources.isEmpty()
                                    ? "No resources have been added yet."
                                    : resources.size() + " learning resource(s) available."
                    );

                    renderResources();
                });
            }

            @Override
            public void onError(String message) {

                runOnUiThread(() -> {

                    setLoading(
                            false,
                            "Could not load resources. Check your connection."
                    );

                    Toast.makeText(
                            ResourceRepositoryActivity.this,
                            message,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    // --------------------------------------------------
    // ADD RESOURCE
    // --------------------------------------------------

    private void showAddDialog() {

        if (!"tutor".equalsIgnoreCase(role)
                && !"admin".equalsIgnoreCase(role)) {

            Toast.makeText(this,
                    "Only tutors and administrators can add resources.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);

        EditText title = createField("Resource title *");
        EditText subject = createField("Subject or module");

        EditText url = createField("External resource URL (https://...)");
        url.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_URI
        );

        EditText description = createField("Resource description");
        description.setMinLines(2);
        description.setGravity(Gravity.TOP | Gravity.START);

        form.addView(title);
        form.addView(subject);
        form.addView(url);
        form.addView(description);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add learning resource")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add resource", null)
                .create();

        dialog.setOnShowListener(ignored -> {

            Button positive = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );

            positive.setOnClickListener(v -> {

                String t = title.getText().toString().trim();
                String s = subject.getText().toString().trim();
                String u = url.getText().toString().trim();
                String d = description.getText().toString().trim();

                if (t.isEmpty()) {
                    title.setError("Enter a resource title.");
                    return;
                }

                if (!u.isEmpty() && !isValidWebUrl(u)) {
                    url.setError("Enter a valid HTTP or HTTPS link.");
                    return;
                }

                positive.setEnabled(false);

                SupabaseApi.createResource(
                        t, s, u, d,
                        new SupabaseApi.EmptyCallback() {

                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> {

                                    dialog.dismiss();

                                    Toast.makeText(
                                            ResourceRepositoryActivity.this,
                                            "Resource added successfully.",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    loadResources();
                                });
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> {

                                    positive.setEnabled(true);

                                    Toast.makeText(
                                            ResourceRepositoryActivity.this,
                                            "Could not add resource: " + message,
                                            Toast.LENGTH_LONG
                                    ).show();
                                });
                            }
                        }
                );
            });
        });

        dialog.show();
    }

    private EditText createField(String hint) {

        EditText field = new EditText(this);
        field.setHint(hint);
        field.setPadding(dp(10), dp(10), dp(10), dp(10));
        field.setTextSize(14);

        return field;
    }

    // --------------------------------------------------
    // DISPLAY AND SEARCH RESOURCES
    // --------------------------------------------------

    private void renderResources() {

        if (resourceList == null) return;

        resourceList.removeAllViews();

        String query = etSearch.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        int shown = 0;

        for (JSONObject item : resources) {

            String title = clean(item.optString("title", ""));
            String subject = clean(item.optString("subject", ""));
            String description = clean(item.optString("description", ""));

            String url = clean(item.optString("url", ""));

            if (url.isEmpty()) {
                url = clean(item.optString("file_url", ""));
            }

            String searchable =
                    (title + " " + subject + " " + description)
                            .toLowerCase(Locale.ROOT);

            if (!query.isEmpty() && !searchable.contains(query)) {
                continue;
            }

            shown++;

            createResourceCard(
                    item,
                    title,
                    subject,
                    description,
                    url
            );
        }

        if (resources.isEmpty()) {
            tvStatus.setText("No learning resources available yet.");
        } else if (shown == 0) {
            tvStatus.setText("No resources match your search.");
        } else {
            tvStatus.setText(
                    shown + (shown == 1
                            ? " resource available"
                            : " resources available")
            );
        }
    }

    // --------------------------------------------------
    // RESOURCE CARD DESIGN
    // --------------------------------------------------

    private void createResourceCard(
            JSONObject item,
            String title,
            String subject,
            String description,
            String url) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));

        card.setBackground(roundBackground(
                Color.WHITE,
                dp(14),
                Color.rgb(220, 231, 245),
                dp(1)
        ));

        card.setElevation(dp(3));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        -1, -2
                );

        cardParams.setMargins(0, 0, 0, dp(16));

        resourceList.addView(card, cardParams);

        // Heading row

        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);

        card.addView(headingRow);

        TextView icon = new TextView(this);
        icon.setText(resourceIcon(url));
        icon.setTextSize(25);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundBackground(
                LIGHT_BLUE, dp(12), LIGHT_BLUE, 0
        ));

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(50), dp(50));

        iconParams.setMargins(0, 0, dp(12), 0);

        headingRow.addView(icon, iconParams);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);

        headingRow.addView(
                titleColumn,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        TextView heading = new TextView(this);
        heading.setText(
                title.isEmpty() ? "Untitled resource" : title
        );
        heading.setTextColor(DARK_BLUE);
        heading.setTextSize(16);
        heading.setTypeface(null, Typeface.BOLD);

        titleColumn.addView(heading);

        TextView type = new TextView(this);
        type.setText(resourceType(url));
        type.setTextColor(BLUE);
        type.setTextSize(12);
        type.setPadding(0, dp(4), 0, 0);

        titleColumn.addView(type);

        // Subject

        if (!subject.isEmpty()) {

            TextView subjectView = new TextView(this);
            subjectView.setText(subject);
            subjectView.setTextColor(BLUE);
            subjectView.setTextSize(12);
            subjectView.setTypeface(null, Typeface.BOLD);
            subjectView.setPadding(
                    dp(10), dp(6), dp(10), dp(6)
            );

            subjectView.setBackground(roundBackground(
                    LIGHT_BLUE, dp(20), LIGHT_BLUE, 0
            ));

            LinearLayout.LayoutParams subjectParams =
                    new LinearLayout.LayoutParams(-2, -2);

            subjectParams.setMargins(0, dp(12), 0, 0);

            card.addView(subjectView, subjectParams);
        }

        // Description

        if (!description.isEmpty()) {

            TextView desc = new TextView(this);
            desc.setText(description);
            desc.setTextColor(TEXT_COLOR);
            desc.setTextSize(13);
            desc.setLineSpacing(dp(2), 1f);
            desc.setPadding(0, dp(12), 0, dp(4));

            card.addView(desc);
        }

        // Buttons

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams actionParams =
                new LinearLayout.LayoutParams(-1, -2);

        actionParams.setMargins(0, dp(12), 0, 0);

        card.addView(actions, actionParams);

        if (!url.isEmpty()) {

            Button open = makeButton("Open resource", true);
            actions.addView(open);

            open.setOnClickListener(v -> openUrl(url));

        } else {

            TextView missing = new TextView(this);
            missing.setText("No file or link attached");
            missing.setTextColor(Color.rgb(183, 92, 36));
            missing.setTextSize(12);

            actions.addView(missing);
        }

        String owner = clean(item.optString("owner_id", ""));
        String id = clean(item.optString("id", ""));

        if ("admin".equalsIgnoreCase(role)
                || (currentUserId != null
                && currentUserId.equals(owner))) {

            Button delete = makeButton("Delete", false);

            LinearLayout.LayoutParams deleteParams =
                    new LinearLayout.LayoutParams(-2, -2);

            deleteParams.setMargins(dp(8), 0, 0, 0);

            actions.addView(delete, deleteParams);

            delete.setOnClickListener(
                    v -> confirmDelete(id, title)
            );
        }
    }

    // --------------------------------------------------
    // RESOURCE ICONS AND FILE TYPES
    // --------------------------------------------------

    private String resourceIcon(String url) {

        String type = resourceType(url);

        if (type.contains("PDF")) return "📕";
        if (type.contains("Word")) return "📘";
        if (type.contains("PowerPoint")) return "📙";
        if (type.contains("Excel")) return "📗";
        if (type.contains("Video")) return "🎬";
        if (type.contains("Audio")) return "🎧";
        if (type.contains("Image")) return "🖼️";
        if (type.contains("Website")) return "🔗";

        return "📚";
    }

    private String resourceType(String url) {

        if (url == null || url.trim().isEmpty()) {
            return "Learning material";
        }

        String path = Uri.parse(url).getPath();

        if (path == null) path = url;

        String lower = path.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".pdf")) return "PDF document";

        if (lower.endsWith(".doc")
                || lower.endsWith(".docx")) {
            return "Word document";
        }

        if (lower.endsWith(".ppt")
                || lower.endsWith(".pptx")) {
            return "PowerPoint presentation";
        }

        if (lower.endsWith(".xls")
                || lower.endsWith(".xlsx")) {
            return "Excel spreadsheet";
        }

        if (lower.endsWith(".mp4")
                || lower.endsWith(".mov")
                || lower.endsWith(".avi")
                || lower.endsWith(".mkv")) {
            return "Video";
        }

        if (lower.endsWith(".mp3")
                || lower.endsWith(".wav")
                || lower.endsWith(".m4a")) {
            return "Audio";
        }

        if (lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")) {
            return "Image";
        }

        if (lower.startsWith("http://")
                || lower.startsWith("https://")) {
            return "Website / external link";
        }

        return "Learning material";
    }

    // --------------------------------------------------
    // BUTTON DESIGN
    // --------------------------------------------------

    private Button makeButton(String label, boolean primary) {

        Button button = new Button(this);

        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(null, Typeface.BOLD);

        button.setTextColor(
                primary ? Color.WHITE : BLUE
        );

        button.setBackground(roundBackground(
                primary ? BLUE : LIGHT_BLUE,
                dp(10),
                primary ? BLUE : LIGHT_BLUE,
                dp(1)
        ));

        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));

        button.setPadding(
                dp(14), dp(6), dp(14), dp(6)
        );

        return button;
    }

    private GradientDrawable roundBackground(
            int fill,
            int radius,
            int stroke,
            int strokeWidth) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(fill);
        drawable.setCornerRadius(radius);

        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, stroke);
        }

        return drawable;
    }

    // --------------------------------------------------
    // DELETE RESOURCE
    // --------------------------------------------------

    private void confirmDelete(String id, String title) {

        if (id == null || id.trim().isEmpty()) {
            Toast.makeText(this,
                    "Resource ID is missing.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete resource")
                .setMessage(
                        "Are you sure you want to remove "
                                + title + "?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {

                    SupabaseApi.deleteResource(
                            id,
                            new SupabaseApi.EmptyCallback() {

                                @Override
                                public void onSuccess() {
                                    runOnUiThread(() -> {

                                        Toast.makeText(
                                                ResourceRepositoryActivity.this,
                                                "Resource deleted.",
                                                Toast.LENGTH_SHORT
                                        ).show();

                                        loadResources();
                                    });
                                }

                                @Override
                                public void onError(String message) {
                                    runOnUiThread(() -> {

                                        Toast.makeText(
                                                ResourceRepositoryActivity.this,
                                                "Delete failed: " + message,
                                                Toast.LENGTH_LONG
                                        ).show();
                                    });
                                }
                            }
                    );
                })
                .show();
    }

    // --------------------------------------------------
    // OPEN RESOURCE
    // --------------------------------------------------

    private void openUrl(String raw) {

        String value = clean(raw);

        if (value.isEmpty()) {
            Toast.makeText(this,
                    "This resource has no attached file or link.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        boolean isHttp = value.startsWith("https://")
                || value.startsWith("http://");

        // Ordinary external website.

        if (isHttp
                && isValidWebUrl(value)
                && !value.contains("/storage/v1/object/")) {

            try {
                startActivity(new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(value)
                ));
            } catch (Exception e) {
                Toast.makeText(this,
                        "No app is available to open this link.",
                        Toast.LENGTH_LONG).show();
            }

            return;
        }

        // Private Supabase Storage file.

        setLoading(true, "Preparing secure resource link...");

        SupabaseApi.createResourceSignedUrl(
                value,
                new SupabaseApi.StringCallback() {

                    @Override
                    public void onSuccess(String signedUrl) {

                        runOnUiThread(() -> {

                            setLoading(false, "Resource ready.");

                            if (signedUrl == null
                                    || signedUrl.trim().isEmpty()) {

                                Toast.makeText(
                                        ResourceRepositoryActivity.this,
                                        "The secure link is empty.",
                                        Toast.LENGTH_LONG
                                ).show();
                                return;
                            }

                            try {

                                Uri uri = Uri.parse(signedUrl.trim());

                                String path = uri.getPath();

                                if (!"https".equalsIgnoreCase(uri.getScheme())
                                        || uri.getHost() == null
                                        || path == null
                                        || !path.contains(
                                        "/storage/v1/object/sign/peerpath-files/")
                                        || uri.getQueryParameter("token") == null) {

                                    Toast.makeText(
                                            ResourceRepositoryActivity.this,
                                            "Invalid secure resource link.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                    return;
                                }

                                startActivity(new Intent(
                                        Intent.ACTION_VIEW,
                                        uri
                                ));

                            } catch (Exception e) {

                                Toast.makeText(
                                        ResourceRepositoryActivity.this,
                                        "Could not open the resource.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            setLoading(false, "Could not open resource.");

                            Toast.makeText(
                                    ResourceRepositoryActivity.this,
                                    "Could not access resource: " + message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private boolean isValidWebUrl(String value) {

        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        try {

            Uri uri = Uri.parse(value.trim());

            String scheme = uri.getScheme();

            return uri.getHost() != null
                    && ("https".equalsIgnoreCase(scheme)
                    || "http".equalsIgnoreCase(scheme));

        } catch (Exception e) {
            return false;
        }
    }

    // --------------------------------------------------
    // GENERAL HELPERS
    // --------------------------------------------------

    private String clean(String value) {

        if (value == null) return "";

        String result = value.trim();

        if ("null".equalsIgnoreCase(result)
                || "undefined".equalsIgnoreCase(result)) {
            return "";
        }

        return result;
    }

    private void setLoading(boolean loading, String message) {

        if (progress != null) {
            progress.setVisibility(
                    loading ? View.VISIBLE : View.GONE
            );
        }

        if (tvStatus != null) {
            tvStatus.setText(message);
        }
    }

    private int dp(int value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }
}
