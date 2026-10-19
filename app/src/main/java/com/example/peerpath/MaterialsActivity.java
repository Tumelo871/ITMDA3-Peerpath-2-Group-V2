package com.example.peerpath;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared resource repository. Uses SupabaseApi and the existing activity_materials.xml. */
public class MaterialsActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_FILE = 7041;
    private static final String STORAGE_BUCKET = "peerpath-files";

    private EditText etSearch;
    private TextView tvStatus;
    private LinearLayout container;
    private Button addButton;
    private boolean mine;
    private boolean canManageResources;
    private String uid;
    private String mySubject = "";
    private final List<JSONObject> allMaterials = new ArrayList<>();

    private String pendingTitle, pendingSubject, pendingDescription;
    private AlertDialog pendingDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_materials);

        uid = SupabaseApi.userId();
        if (uid == null || uid.trim().isEmpty() || !SupabaseApi.isLoggedIn()) {
            Toast.makeText(this, "Please sign in first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mine = getIntent().getBooleanExtra("mine", false);
        etSearch = findViewById(R.id.etSearch);
        tvStatus = findViewById(R.id.tvMaterialStatus);
        container = findViewById(R.id.materialContainer);
        TextView title = findViewById(R.id.tvMaterialsTitle);
        if (title != null) title.setText(mine ? "My Materials" : "Learning Materials");

        View back = findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> finish());

        addButton = findViewById(R.id.btnAddMaterial);
        if (addButton != null) {
            addButton.setVisibility(View.GONE);
            addButton.setOnClickListener(v -> showAddDialog());
        }

        if (etSearch != null) etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { showMaterials(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        loadProfileAndMaterials();
    }

    private void loadProfileAndMaterials() {
        SupabaseApi.getProfile(uid, new SupabaseApi.ObjectCallback() {
            @Override public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> {
                    if (profile != null) {
                        mySubject = profile.optString("tutor_subject", profile.optString("subject", ""));
                        String role = profile.optString("role", "");
                        String verification = profile.optString("verification_status", "");
                        canManageResources = "admin".equalsIgnoreCase(role) ||
                                ("tutor".equalsIgnoreCase(role) &&
                                        (verification.isEmpty() || "verified".equalsIgnoreCase(verification)));
                    }
                    if (addButton != null) addButton.setVisibility(canManageResources ? View.VISIBLE : View.GONE);
                    loadMaterials();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    canManageResources = false;
                    if (addButton != null) addButton.setVisibility(View.GONE);
                    loadMaterials();
                });
            }
        });
    }

    private void loadMaterials() {
        if (tvStatus != null) tvStatus.setText("Loading resources…");
        SupabaseApi.getResources(new SupabaseApi.JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                runOnUiThread(() -> {
                    allMaterials.clear();
                    if (data != null) for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.optJSONObject(i);
                        if (item == null) continue;
                        String owner = item.optString("owner_id", "");
                        if (!mine || uid.equals(owner)) allMaterials.add(item);
                    }
                    showMaterials();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (tvStatus != null) tvStatus.setText("Could not load resources. Check your connection and Supabase permissions.");
                    Toast.makeText(MaterialsActivity.this, message == null ? "Unable to load resources." : message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showMaterials() {
        if (container == null) return;
        String query = etSearch == null ? "" : etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        container.removeAllViews();
        int shown = 0;
        for (JSONObject item : allMaterials) {
            String title = val(item, "title"), subject = val(item, "subject"), description = val(item, "description");
            String searchable = (title + " " + subject + " " + description + " " + val(item, "tutor_name")).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchable.contains(query)) continue;
            addMaterialCard(item);
            shown++;
        }
        if (tvStatus != null) tvStatus.setText(shown == 0 ? (mine ? "You have not added any resources yet." : "No resources found. Tutors can add resources from My materials.") : shown + (shown == 1 ? " resource" : " resources"));
    }

    private void addMaterialCard(JSONObject item) {
        LinearLayout card = Ui.card(this);
        int pad = Ui.dp(this, 16);
        card.setPadding(pad, pad, pad, pad);
        String title = val(item, "title"), subject = val(item, "subject"), description = val(item, "description");
        card.addView(Ui.text(this, "📘 " + (title.isEmpty() ? "Untitled resource" : title), 18, R.color.text_primary, true));
        if (!subject.isEmpty()) Ui.add(card, Ui.text(this, subject, 13, R.color.peer_blue, true), 6);
        if (!description.isEmpty()) Ui.add(card, Ui.text(this, description, 14, R.color.text_secondary, false), 8);
        String url = val(item, "url");
        if (url.isEmpty()) url = val(item, "link");
        if (!url.isEmpty()) {
            final String finalUrl = url;
            Button open = makeButton("Open / download resource");
            open.setOnClickListener(v -> openLink(finalUrl));
            Ui.add(card, open, 8);
        } else {
            Ui.add(card, Ui.text(this, "No file or link was attached to this resource.", 13, R.color.text_secondary, false), 8);
        }
        if (mine && uid.equals(val(item, "owner_id"))) {
            Button delete = makeButton("Delete resource");
            delete.setOnClickListener(v -> confirmDelete(item));
            Ui.add(card, delete, 6);
        }
        Ui.addToList(container, card);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private void showAddDialog() {
        if (!canManageResources) {
            Toast.makeText(this, "Only verified tutors can add learning resources.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 18);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        EditText title = field("Resource title", InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText subject = field("Subject / module", InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        subject.setText(mySubject);
        EditText link = field("External link (optional)", InputType.TYPE_TEXT_VARIATION_URI);
        EditText desc = field("Description / notes", InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(title); box.addView(subject); box.addView(link); box.addView(desc);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Add learning resource").setView(box)
                .setPositiveButton("Continue", null).setNeutralButton("Choose file", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String t = title.getText().toString().trim(), s = subject.getText().toString().trim();
                String l = link.getText().toString().trim(), de = desc.getText().toString().trim();
                if (t.isEmpty()) { title.setError("Enter a title"); return; }
                if (l.isEmpty() && de.isEmpty()) { desc.setError("Add a description, a link, or choose a file"); return; }
                if (!l.isEmpty() && !l.startsWith("https://") && !l.startsWith("http://")) l = "https://" + l;
                dialog.dismiss(); saveResource(t, s, l, de, null);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String t = title.getText().toString().trim(), s = subject.getText().toString().trim();
                String l = link.getText().toString().trim(), de = desc.getText().toString().trim();
                if (t.isEmpty()) { title.setError("Enter a title before choosing a file"); return; }
                pendingTitle = t; pendingSubject = s; pendingDescription = de;
                if (!l.isEmpty() && !l.startsWith("https://") && !l.startsWith("http://")) l = "https://" + l;
                pendingExternalLink = l;
                pendingDialog = dialog;
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                pick.addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                );
                startActivityForResult(pick, REQUEST_PICK_FILE);
            });
        });
        dialog.show();
    }

    private String pendingExternalLink = "";

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_FILE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        if (pendingDialog != null && pendingDialog.isShowing()) pendingDialog.dismiss();
        String fileName = "resource_" + System.currentTimeMillis() + safeExtension(uri);
        String storagePath = uid + "/" + fileName;
        if (tvStatus != null) tvStatus.setText("Uploading file…");
        Toast.makeText(this, "Uploading resource…", Toast.LENGTH_SHORT).show();
        SupabaseApi.uploadFile(getContentResolver(), uri, storagePath, new SupabaseApi.EmptyCallback() {
            @Override public void onSuccess() {
                String publicUrl = getSupabasePublicUrl(storagePath);
                String combinedDescription = pendingDescription;
                saveResource(pendingTitle, pendingSubject, publicUrl, combinedDescription, null);
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (tvStatus != null) tvStatus.setText("File upload failed.");
                    Toast.makeText(MaterialsActivity.this, "Upload failed: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String getSupabasePublicUrl(String path) {
        String base = SupabaseConfig.URL.trim();

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        String encoded = path
                .replace("%", "%25")
                .replace(" ", "%20")
                .replace("#", "%23")
                .replace("?", "%3F");

        return base
                + "/storage/v1/object/public/"
                + STORAGE_BUCKET
                + "/"
                + encoded;
    }

    private String safeExtension(Uri uri) {
        String mime = getContentResolver().getType(uri);

        if ("application/pdf".equals(mime)) return ".pdf";
        if ("text/plain".equals(mime)) return ".txt";
        if ("application/msword".equals(mime)) return ".doc";
        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                .equals(mime)) return ".docx";
        if ("application/vnd.openxmlformats-officedocument.presentationml.presentation"
                .equals(mime)) return ".pptx";
        if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                .equals(mime)) return ".xlsx";
        if (mime != null && mime.startsWith("video/")) return ".mp4";

        return ".bin";
    }

    private EditText field(String hint, int flags) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | flags);
        e.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        return e;
    }

    private void saveResource(String title, String subject, String url, String description, AlertDialog ignored) {
        if (tvStatus != null) tvStatus.setText("Saving resource…");
        SupabaseApi.createResource(title, subject, url, description, new SupabaseApi.EmptyCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(MaterialsActivity.this, "Resource published.", Toast.LENGTH_SHORT).show();
                    pendingTitle = pendingSubject = pendingDescription = pendingExternalLink = "";
                    loadMaterials();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (tvStatus != null) tvStatus.setText("Could not save resource.");
                    Toast.makeText(MaterialsActivity.this, "Save failed: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void confirmDelete(JSONObject item) {
        String id = val(item, "id");
        if (id.isEmpty()) { Toast.makeText(this, "Resource ID is missing.", Toast.LENGTH_LONG).show(); return; }
        new AlertDialog.Builder(this).setTitle("Delete resource?").setMessage("Delete “" + val(item, "title") + "”?")
                .setPositiveButton("Delete", (d, w) -> deleteResource(id)).setNegativeButton("Cancel", null).show();
    }

    private void deleteResource(String id) {
        SupabaseApi.deleteResource(id, new SupabaseApi.EmptyCallback() {
            @Override public void onSuccess() { runOnUiThread(() -> { Toast.makeText(MaterialsActivity.this, "Resource deleted.", Toast.LENGTH_SHORT).show(); loadMaterials(); }); }
            @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(MaterialsActivity.this, "Delete failed: " + message, Toast.LENGTH_LONG).show()); }
        });
    }

    private void openLink(String link) {
        if (link == null || link.trim().isEmpty()
                || "null".equalsIgnoreCase(link.trim())) {
            Toast.makeText(this,
                    "This resource has no attached file or link. Ask the tutor to upload it again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            String value = link.trim();

            if (!value.startsWith("https://")
                    && !value.startsWith("http://")) {
                value = getSupabasePublicUrl(value);
            }

            Uri uri = Uri.parse(value);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);

        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this,
                    "No app is available to open this file.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "Could not open the resource. Check its file URL.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private String val(JSONObject obj, String key) { return obj == null ? "" : obj.optString(key, "").trim(); }
}
