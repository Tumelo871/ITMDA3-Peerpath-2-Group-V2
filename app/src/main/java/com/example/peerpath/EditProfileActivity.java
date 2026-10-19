package com.example.peerpath;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

public class EditProfileActivity extends AppCompatActivity {

    private TextInputEditText etModuleCodes;
    private TextInputEditText etSubject;
    private TextInputEditText etBio;
    private TextInputEditText etExperience;
    private MaterialButton btnSaveProfile;
    private boolean requestInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_edit_profile);

        etModuleCodes = findViewById(R.id.etModuleCodes);
        etSubject = findViewById(R.id.etSubject);
        etBio = findViewById(R.id.etBio);
        etExperience = findViewById(R.id.etExperience);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);

        View btnBack = findViewById(R.id.btnBack); // Optional in the XML.
        if (btnBack != null) {
            btnBack.setOnClickListener(view -> finish());
        }

        if (etModuleCodes == null || etSubject == null || etBio == null
                || etExperience == null || btnSaveProfile == null) {
            Toast.makeText(this,
                    "Edit Profile layout is missing a required view ID.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnSaveProfile.setOnClickListener(view -> saveProfile());

        if (!SupabaseApi.isConfigured()) {
            showMessage("Supabase is not configured. Check SupabaseConfig.");
            return;
        }
        if (!SupabaseApi.isLoggedIn()) {
            showMessage("Please sign in again to edit your profile.");
            return;
        }

        loadProfile();
    }

    private void loadProfile() {
        setBusy(true);
        SupabaseApi.getMyProfile(new SupabaseApi.ObjectCallback() {
            @Override
            public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    etModuleCodes.setText(clean(profile.optString("module_codes", "")));
                    etSubject.setText(clean(profile.optString("tutor_subject", "")));
                    etBio.setText(clean(profile.optString("tutor_bio", "")));
                    etExperience.setText(clean(profile.optString("tutor_experience", "")));
                    setBusy(false);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false);
                    showMessage("Could not load profile: " + safeMessage(message));
                });
            }
        });
    }

    private void saveProfile() {
        if (requestInProgress) return;

        String modules = valueOf(etModuleCodes);
        String subject = valueOf(etSubject);
        String bio = valueOf(etBio);
        String experience = valueOf(etExperience);

        if (TextUtils.isEmpty(modules)) {
            etModuleCodes.setError("Enter your module codes.");
            etModuleCodes.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(subject)) {
            etSubject.setError("Enter your tutoring subject.");
            etSubject.requestFocus();
            return;
        }
        if (!SupabaseApi.isConfigured()) {
            showMessage("Supabase is not configured. Check SupabaseConfig.");
            return;
        }
        if (!SupabaseApi.isLoggedIn()) {
            showMessage("Please sign in again to save your profile.");
            return;
        }

        JSONObject values = new JSONObject();
        try {
            values.put("module_codes", modules);
            values.put("tutor_subject", subject);
            values.put("tutor_bio", bio);
            values.put("tutor_experience", experience);
        } catch (JSONException exception) {
            showMessage("Could not prepare profile data.");
            return;
        }

        requestInProgress = true;
        setBusy(true);
        SupabaseApi.updateProfile(values, new SupabaseApi.EmptyCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    requestInProgress = false;
                    setBusy(false);
                    Toast.makeText(EditProfileActivity.this,
                            "Profile saved successfully.", Toast.LENGTH_LONG).show();
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    requestInProgress = false;
                    setBusy(false);
                    showMessage("Could not save profile: " + safeMessage(message));
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        if (btnSaveProfile != null) btnSaveProfile.setEnabled(!busy);
        if (etModuleCodes != null) etModuleCodes.setEnabled(!busy);
        if (etSubject != null) etSubject.setEnabled(!busy);
        if (etBio != null) etBio.setEnabled(!busy);
        if (etExperience != null) etExperience.setEnabled(!busy);
    }

    private String valueOf(TextInputEditText field) {
        return field == null || field.getText() == null
                ? "" : field.getText().toString().trim();
    }

    private String clean(String value) {
        if (value == null || "null".equalsIgnoreCase(value.trim())) return "";
        return value.trim();
    }

    private String safeMessage(String message) {
        return message == null || message.trim().isEmpty()
                ? "Please try again." : message.trim();
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
