package com.example.peerpath;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.example.peerpath.SupabaseConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

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

/**
 * Registration with mandatory academic evidence for tutor applicants.
 *
 * IMPORTANT: proof upload requires a Supabase session after signup. If Supabase
 * email confirmation is enabled and signup returns no session, this activity
 * refuses to report the tutor application as submitted. Server-side signup /
 * evidence handling is still required for a fully reliable atomic workflow.
 */
public class RegisterActivity extends AppCompatActivity {

    private static final int ROLE_LEARNER = R.id.rbLearner;
    private static final int ROLE_TUTOR = R.id.rbTutor;
    private static final int ROLE_SPONSOR = R.id.rbSponsor;
    private static final int PICK_EVIDENCE = 7301;
    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;

    private TextInputEditText etName, etStudentNumber, etFaculty, etEmail, etPassword;
    private TextInputEditText etSubject, etBio, etExperience;
    private RadioGroup rgAccountType;
    private View tutorFields;
    private MaterialButton btnRegister;
    private Button btnChooseEvidence;
    private TextView tvEvidenceName;
    private Uri evidenceUri;
    private String evidenceDisplayName = "";
    private boolean registrationInProgress;

    private final OkHttpClient http = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.etName);
        etStudentNumber = findViewById(R.id.etStudentNumber);
        etFaculty = findViewById(R.id.etFaculty);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etSubject = findViewById(R.id.etSubject);
        etBio = findViewById(R.id.etBio);
        etExperience = findViewById(R.id.etExperience);
        tutorFields = findViewById(R.id.layoutTutorFields);
        rgAccountType = findViewById(R.id.rgAccountType);
        btnRegister = findViewById(R.id.btnRegister);

        if (rgAccountType == null || btnRegister == null || etName == null ||
                etEmail == null || etPassword == null) {
            Toast.makeText(this, "Registration layout is missing required fields.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Add proof controls to the existing tutor-only section, so XML IDs need not change.
        if (tutorFields instanceof LinearLayout) {
            LinearLayout tutorContainer = (LinearLayout) tutorFields;
            btnChooseEvidence = new Button(this);
            btnChooseEvidence.setText("Choose Academic Proof (PDF, image, or document)");
            tvEvidenceName = new TextView(this);
            tvEvidenceName.setText("No academic proof selected");
            tvEvidenceName.setTextSize(13);
            tutorContainer.addView(btnChooseEvidence);
            tutorContainer.addView(tvEvidenceName);
            btnChooseEvidence.setOnClickListener(v -> chooseEvidence());
        } else {
            Toast.makeText(this,
                    "Tutor fields must be a LinearLayout to add the required proof picker.",
                    Toast.LENGTH_LONG).show();
        }

        if (rgAccountType.getCheckedRadioButtonId() == -1) rgAccountType.check(ROLE_LEARNER);
        updateRoleFields();
        rgAccountType.setOnCheckedChangeListener((group, checkedId) -> updateRoleFields());
        btnRegister.setOnClickListener(v -> registerUser());

        View loginLink = findViewById(R.id.tvLogin);
        if (loginLink != null) {
            loginLink.setOnClickListener(v -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
        }
    }

    private String selectedRole() {
        int id = rgAccountType.getCheckedRadioButtonId();
        if (id == ROLE_TUTOR) return "tutor";
        if (id == ROLE_SPONSOR) return "sponsor";
        return "learner";
    }

    private void updateRoleFields() {
        boolean tutor = "tutor".equals(selectedRole());
        if (tutorFields != null) tutorFields.setVisibility(tutor ? View.VISIBLE : View.GONE);
        if (btnRegister != null) {
            btnRegister.setText(tutor ? "Submit Tutor Application" :
                    ("sponsor".equals(selectedRole()) ? "Create Sponsor Account" : "Create Account"));
        }
    }

    private void chooseEvidence() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf",
                "image/jpeg", "image/png", "image/webp",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        });
        startActivityForResult(intent, PICK_EVIDENCE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_EVIDENCE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri picked = data.getData();
        String mime = getContentResolver().getType(picked);
        if (!allowedMime(mime)) {
            Toast.makeText(this, "Choose a PDF, JPG, PNG, WebP, DOC, or DOCX file.", Toast.LENGTH_LONG).show();
            return;
        }
        String name = queryDisplayName(picked);
        try (InputStream in = getContentResolver().openInputStream(picked)) {
            if (in == null) throw new Exception("File could not be opened");
            long size = countBytes(in);
            if (size <= 0 || size > MAX_FILE_BYTES) {
                Toast.makeText(this, "Academic proof must be between 1 byte and 10 MB.", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Could not read the selected file: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        evidenceUri = picked;
        evidenceDisplayName = name;
        if (tvEvidenceName != null) tvEvidenceName.setText("Selected: " + name);
    }

    private long countBytes(InputStream input) throws Exception {
        long total = 0;
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) total += n;
        return total;
    }

    private boolean allowedMime(String mime) {
        if (mime == null) return false;
        return mime.equals("application/pdf") || mime.equals("image/jpeg") ||
                mime.equals("image/png") || mime.equals("image/webp") ||
                mime.equals("application/msword") ||
                mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return c.getString(index);
            }
        } catch (Exception ignored) { }
        return "academic-proof";
    }

    private void registerUser() {
        if (registrationInProgress) return;
        String name = text(etName);
        String studentNo = text(etStudentNumber);
        String faculty = text(etFaculty);
        String email = text(etEmail).toLowerCase(Locale.ROOT);
        String password = text(etPassword);
        String role = selectedRole();
        boolean tutor = "tutor".equals(role);
        boolean sponsor = "sponsor".equals(role);

        if (TextUtils.isEmpty(name)) { etName.setError("Enter your full name"); etName.requestFocus(); return; }
        if (!sponsor && TextUtils.isEmpty(studentNo)) { etStudentNumber.setError("Enter your student number"); etStudentNumber.requestFocus(); return; }
        if (!sponsor && TextUtils.isEmpty(faculty)) { etFaculty.setError("Enter your faculty"); etFaculty.requestFocus(); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches() || !Config.isVossieEmail(email)) {
            etEmail.setError("Use a valid @vossie.net email address"); etEmail.requestFocus(); return;
        }
        if (password.length() < 6) { etPassword.setError("Password must be at least 6 characters"); etPassword.requestFocus(); return; }
        if (tutor && TextUtils.isEmpty(text(etSubject))) { etSubject.setError("Enter the modules or subjects you tutor"); etSubject.requestFocus(); return; }
        if (tutor && evidenceUri == null) {
            Toast.makeText(this, "You must attach academic proof before registering as a tutor.", Toast.LENGTH_LONG).show();
            if (btnChooseEvidence != null) btnChooseEvidence.requestFocus();
            return;
        }

        registrationInProgress = true;
        btnRegister.setEnabled(false);
        btnRegister.setText(tutor ? "Submitting tutor application..." : "Creating account...");

        try {
            JSONObject metadata = new JSONObject();
            metadata.put("full_name", name);
            metadata.put("role", role);
            metadata.put("requested_role", role);
            metadata.put("verification_status", tutor ? "pending" : "unverified");
            metadata.put("student_number", sponsor ? "" : studentNo);
            metadata.put("faculty", sponsor ? "" : faculty);
            metadata.put("tutor_subject", tutor ? text(etSubject) : "");
            metadata.put("module_codes", tutor ? text(etSubject) : "");
            metadata.put("tutor_bio", tutor ? text(etBio) : "");
            metadata.put("tutor_experience", tutor ? text(etExperience) : "");

            SupabaseApi.signUp(email, password, metadata, new SupabaseApi.ObjectCallback() {
                @Override public void onSuccess(JSONObject data) {
                    if (!tutor && !sponsor) {
                        runOnUiThread(() -> {
                            restoreButton();
                            Toast.makeText(RegisterActivity.this,
                                    "Learner account created. Check your Vossie email if confirmation is required, then sign in.",
                                    Toast.LENGTH_LONG).show();
                            goToLogin(role);
                        });
                        return;
                    }

                    if (sponsor) {
                        String accessToken = data.optString("access_token", "");
                        JSONObject user = data.optJSONObject("user");
                        String userId = user == null ? "" : user.optString("id", "");
                        if (accessToken.isEmpty() || userId.isEmpty()) {
                            runOnUiThread(() -> {
                                restoreButton();
                                Toast.makeText(RegisterActivity.this,
                                        "Your account may have been created, but Supabase did not issue a session. "
                                                + "Your sponsor application was NOT submitted. Sign in after email confirmation and contact support to submit it.",
                                        Toast.LENGTH_LONG).show();
                            });
                            return;
                        }
                        submitSponsorApplication(userId, accessToken, role);
                        return;
                    }

                    // Signup only permits secure Storage upload if it returns a session.
                    String accessToken = data.optString("access_token", "");
                    JSONObject user = data.optJSONObject("user");
                    String userId = user == null ? "" : user.optString("id", "");
                    if (accessToken.isEmpty() || userId.isEmpty()) {
                        runOnUiThread(() -> {
                            restoreButton();
                            Toast.makeText(RegisterActivity.this,
                                    "Your account may have been created, but Supabase did not issue a session. "
                                            + "Your proof was NOT uploaded and your tutor application was NOT submitted. "
                                            + "Server-side signup handling is required when email confirmation is enabled.",
                                    Toast.LENGTH_LONG).show();
                        });
                        return;
                    }
                    uploadEvidenceAndCreateApplication(userId, accessToken, text(etSubject), email, role);
                }

                @Override public void onError(String message) {
                    runOnUiThread(() -> {
                        restoreButton();
                        Toast.makeText(RegisterActivity.this,
                                TextUtils.isEmpty(message) ? "Registration failed." : message,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception e) {
            restoreButton();
            Toast.makeText(this, "Could not start registration.", Toast.LENGTH_LONG).show();
        }
    }

    private void submitSponsorApplication(String userId, String accessToken, String role) {
        new Thread(() -> {
            String error = null;
            try {
                // Use a SECURITY DEFINER RPC so sponsor submission does not require
                // a broad direct INSERT policy on sponsor_applications.
                JSONObject application = new JSONObject();
                Request request = new Request.Builder()
                        .url(SupabaseConfig.URL.trim() + "/rest/v1/rpc/peerpath_submit_sponsor_application")
                        .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY.trim())
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(application.toString(), MediaType.parse("application/json")))
                        .build();
                try (Response response = http.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String details = response.body() == null ? "" : response.body().string();
                        throw new Exception("Sponsor application save failed (HTTP " + response.code() + "). "
                                + (details.isEmpty() ? "" : details));
                    }
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String resultError = error;
            runOnUiThread(() -> {
                restoreButton();
                if (resultError == null) {
                    Toast.makeText(RegisterActivity.this,
                            "Sponsor application submitted. You cannot access the sponsor dashboard until an administrator approves it.",
                            Toast.LENGTH_LONG).show();
                    goToLogin(role);
                } else {
                    Toast.makeText(RegisterActivity.this,
                            "Your account may have been created, but the sponsor application was NOT submitted: "
                                    + resultError + " Please do not register again.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void uploadEvidenceAndCreateApplication(String userId, String accessToken, String modules, String email, String role) {
        new Thread(() -> {
            String error = null;
            String path = null;
            try {
                ContentResolver resolver = getContentResolver();
                String mime = resolver.getType(evidenceUri);
                byte[] bytes;
                try (InputStream input = resolver.openInputStream(evidenceUri);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    if (input == null) throw new Exception("Selected proof file cannot be read.");
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                        if (out.size() > MAX_FILE_BYTES) throw new Exception("Academic proof exceeds 10 MB.");
                    }
                    bytes = out.toByteArray();
                }
                String safeName = evidenceDisplayName.replaceAll("[^A-Za-z0-9._-]", "_");
                path = userId + "/" + UUID.randomUUID() + "_" + safeName;
                String encodedPath = encodePath(path);
                Request upload = new Request.Builder()
                        .url(SupabaseConfig.URL.trim() + "/storage/v1/object/tutor-evidence/" + encodedPath)
                        .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY.trim())
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", mime == null ? "application/octet-stream" : mime)
                        .addHeader("x-upsert", "false")
                        .post(RequestBody.create(bytes, MediaType.parse(mime == null ? "application/octet-stream" : mime)))
                        .build();
                try (Response response = http.newCall(upload).execute()) {
                    if (!response.isSuccessful()) throw new Exception("Proof upload failed (HTTP " + response.code() + ").");
                }

                JSONObject application = new JSONObject();
                application.put("tutor_id", userId);
                application.put("module_codes", modules);
                application.put("evidence_path", path);
                application.put("status", "pending");
                Request insert = new Request.Builder()
                        .url(SupabaseConfig.URL.trim() + "/rest/v1/tutor_applications")
                        .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY.trim())
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .post(RequestBody.create(application.toString(), MediaType.parse("application/json")))
                        .build();
                try (Response response = http.newCall(insert).execute()) {
                    if (!response.isSuccessful()) throw new Exception("Proof uploaded, but application save failed (HTTP " + response.code() + "). Contact support; do not register again.");
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String resultError = error;
            runOnUiThread(() -> {
                restoreButton();
                if (resultError == null) {
                    Toast.makeText(RegisterActivity.this,
                            "Tutor application submitted with academic proof. You cannot sign in until an administrator approves it.",
                            Toast.LENGTH_LONG).show();
                    goToLogin(role);
                } else {
                    Toast.makeText(RegisterActivity.this,
                            "Tutor application is NOT complete: " + resultError,
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private String encodePath(String path) throws Exception {
        String[] parts = path.split("/");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append("/");
            result.append(URLEncoder.encode(part, "UTF-8").replace("+", "%20"));
        }
        return result.toString();
    }

    private void goToLogin(String role) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("registered_role", role);
        startActivity(intent);
        finish();
    }

    private void restoreButton() {
        registrationInProgress = false;
        if (btnRegister != null) {
            btnRegister.setEnabled(true);
            updateRoleFields();
        }
    }

    private String text(TextInputEditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }
}
