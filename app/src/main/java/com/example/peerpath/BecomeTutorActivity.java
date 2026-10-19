package com.example.peerpath;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BecomeTutorActivity extends AppCompatActivity {

    private EditText etModuleCodes;
    private Button btnPickEvidence, btnSubmit;
    private ImageButton btnBack;
    private TextView tvSelectedFile, tvError;
    private ProgressBar progressSubmit;

    private Uri selectedFileUri;
    private String selectedFileName = "";

    private final OkHttpClient client = new OkHttpClient();

    private final ActivityResultLauncher<String[]> evidencePicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            selectedFileUri = uri;
                            selectedFileName = getFileName(uri);

                            tvSelectedFile.setText(
                                    "Selected: " + selectedFileName
                            );
                            tvError.setText("");
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_become_tutor);

        etModuleCodes = findViewById(R.id.etModuleCodes);
        btnPickEvidence = findViewById(R.id.btnPickEvidence);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnBack = findViewById(R.id.btnBack);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvError = findViewById(R.id.tvError);
        progressSubmit = findViewById(R.id.progressSubmit);

        btnBack.setOnClickListener(v -> finish());

        btnPickEvidence.setOnClickListener(v ->
                evidencePicker.launch(new String[]{
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "image/jpeg",
                        "image/png",
                        "image/webp"
                })
        );

        btnSubmit.setOnClickListener(v -> submitApplication());
    }

    private void submitApplication() {
        tvError.setText("");

        if (!SupabaseApi.isConfigured()) {
            showError("Supabase is not configured. Check SupabaseConfig.");
            return;
        }

        if (!SupabaseApi.isLoggedIn()) {
            showError("Please sign in before applying.");
            return;
        }

        String userId = SupabaseApi.userId();
        String token = SupabaseApi.token();

        if (userId == null || userId.trim().isEmpty()
                || token == null || token.trim().isEmpty()) {
            showError("Your login session has expired. Please sign in again.");
            return;
        }

        String modules = etModuleCodes.getText().toString().trim();

        if (modules.isEmpty()) {
            etModuleCodes.setError("Enter the modules you want to tutor.");
            etModuleCodes.requestFocus();
            return;
        }

        if (selectedFileUri == null) {
            showError("Please attach your proof of passing the modules.");
            return;
        }

        if (!isAllowedFile(selectedFileUri)) {
            showError("Please select a PDF, DOCX, JPEG, PNG or WebP file.");
            return;
        }

        setLoading(true);

        new Thread(() -> {
            try {
                String bucket = "tutor-evidence";
                String filePath = userId + "/" + UUID.randomUUID()
                        + "_" + safeFileName(selectedFileName);

                byte[] fileBytes = readSelectedFile();

                if (fileBytes.length == 0) {
                    throw new Exception("The selected file is empty.");
                }

                // Limit evidence files to 10 MB.
                if (fileBytes.length > 10 * 1024 * 1024) {
                    throw new Exception("Please choose a file smaller than 10 MB.");
                }

                String contentType = getContentType(selectedFileUri);

                String encodedPath = encodePath(filePath);

                RequestBody uploadBody = RequestBody.create(
                        MediaType.parse(contentType),
                        fileBytes
                );

                Request uploadRequest = new Request.Builder()
                        .url(SupabaseConfig.URL + "/storage/v1/object/"
                                + bucket + "/" + encodedPath)
                        .post(uploadBody)
                        .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                        .addHeader("Authorization", "Bearer " + token)
                        .addHeader("Content-Type", contentType)
                        .addHeader("x-upsert", "false")
                        .build();

                String evidencePath;

                try (Response response = client.newCall(uploadRequest).execute()) {
                    String body = response.body() != null
                            ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        throw new Exception(
                                "Evidence upload failed (" + response.code()
                                        + "). Check the Storage bucket and its policies. "
                                        + body
                        );
                    }

                    evidencePath = filePath;
                }

                JSONObject application = new JSONObject();
                application.put("tutor_id", userId);
                application.put("module_codes", modules);
                application.put("evidence_path", evidencePath);
                application.put("status", "pending");

                RequestBody jsonBody = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        application.toString()
                );

                Request insertRequest = new Request.Builder()
                        .url(SupabaseConfig.URL
                                + "/rest/v1/tutor_applications")
                        .post(jsonBody)
                        .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                        .addHeader("Authorization", "Bearer " + token)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                try (Response response = client.newCall(insertRequest).execute()) {
                    String body = response.body() != null
                            ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        throw new Exception(
                                "The evidence uploaded, but the application "
                                        + "could not be saved (" + response.code()
                                        + "). Check your table columns and RLS policies. "
                                        + body
                        );
                    }
                }

                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(
                            this,
                            "Application submitted for review!",
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError(e.getMessage() != null
                            ? e.getMessage()
                            : "Something went wrong. Please try again.");
                });
            }
        }).start();
    }

    private byte[] readSelectedFile() throws Exception {
        try (InputStream input = getContentResolver()
                .openInputStream(selectedFileUri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            if (input == null) {
                throw new Exception("Unable to read the selected file.");
            }

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);

                if (output.size() > 10 * 1024 * 1024) {
                    throw new Exception("Please choose a file smaller than 10 MB.");
                }
            }

            return output.toByteArray();
        }
    }

    private boolean isAllowedFile(Uri uri) {
        String type = getContentType(uri).toLowerCase(Locale.ROOT);
        String name = getFileName(uri).toLowerCase(Locale.ROOT);

        return "application/pdf".equals(type) || name.endsWith(".pdf")
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                .equals(type) || name.endsWith(".docx")
                || "image/jpeg".equals(type) || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || "image/png".equals(type) || name.endsWith(".png")
                || "image/webp".equals(type) || name.endsWith(".webp");
    }

    private String getContentType(Uri uri) {
        String type = getContentResolver().getType(uri);

        // Some document providers return a generic or missing MIME type.
        // Fall back to the filename extension so DOCX and image uploads
        // use the correct Content-Type when sent to Supabase Storage.
        if (type != null && !type.trim().isEmpty()
                && !"application/octet-stream".equalsIgnoreCase(type)) {
            return type;
        }

        String name = getFileName(uri).toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";

        return type != null ? type : "application/octet-stream";
    }

    private String getFileName(Uri uri) {
        String name = null;

        try (Cursor cursor = getContentResolver()
                .query(uri, null, null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }

        return name != null ? name : "evidence.pdf";
    }

    private String safeFileName(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");

        return safe.isEmpty() ? "evidence.pdf" : safe;
    }

    private String encodePath(String path) throws Exception {
        String[] parts = path.split("/");
        StringBuilder encoded = new StringBuilder();

        for (String part : parts) {
            if (encoded.length() > 0) {
                encoded.append("/");
            }

            encoded.append(URLEncoder.encode(part, "UTF-8")
                    .replace("+", "%20"));
        }

        return encoded.toString();
    }

    private void setLoading(boolean loading) {
        progressSubmit.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!loading);
        btnPickEvidence.setEnabled(!loading);
        etModuleCodes.setEnabled(!loading);
    }

    private void showError(String message) {
        tvError.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}


