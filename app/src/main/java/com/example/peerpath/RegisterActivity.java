package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etName, etStudentNumber, etFaculty;
    private TextInputEditText etEmail, etPassword;
    private TextInputEditText etSubject, etBio, etExperience;

    private RadioGroup rgAccountType;
    private View tutorFields;
    private MaterialButton btnRegister;

    private boolean registrationInProgress = false;

    private static final int ROLE_LEARNER = R.id.rbLearner;
    private static final int ROLE_TUTOR = R.id.rbTutor;
    // Add rbSponsor to activity_register.xml before using this updated Activity.
    private static final int ROLE_SPONSOR = R.id.rbSponsor;

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

        if (rgAccountType.getCheckedRadioButtonId() == -1) {
            rgAccountType.check(ROLE_LEARNER);
        }

        updateRoleFields();
        rgAccountType.setOnCheckedChangeListener((group, checkedId) -> updateRoleFields());
        btnRegister.setOnClickListener(v -> registerUser());

        findViewById(R.id.tvLogin).setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private String selectedRole() {
        int selectedId = rgAccountType.getCheckedRadioButtonId();
        if (selectedId == ROLE_TUTOR) return "tutor";
        if (selectedId == ROLE_SPONSOR) return "sponsor";
        return "learner";
    }

    private void updateRoleFields() {
        boolean isTutor = "tutor".equals(selectedRole());
        if (tutorFields != null) {
            tutorFields.setVisibility(isTutor ? View.VISIBLE : View.GONE);
        }

        if (btnRegister != null) {
            String role = selectedRole();
            if ("sponsor".equals(role)) {
                btnRegister.setText("Create Sponsor Account");
            } else if ("tutor".equals(role)) {
                btnRegister.setText("Create Tutor Account");
            } else {
                btnRegister.setText("Create Account");
            }
        }
    }

    private void registerUser() {
        if (registrationInProgress) return;

        String name = getText(etName);
        String studentNumber = getText(etStudentNumber);
        String faculty = getText(etFaculty);
        String email = getText(etEmail).toLowerCase(Locale.ROOT);
        String password = getText(etPassword);
        String role = selectedRole();
        boolean isTutor = "tutor".equals(role);
        boolean isSponsor = "sponsor".equals(role);

        if (TextUtils.isEmpty(name)) {
            etName.setError("Enter your full name");
            etName.requestFocus();
            return;
        }

        // Student number and faculty apply only to learner/tutor accounts.
        // Sponsors can register without student-specific information.
        if (!isSponsor && TextUtils.isEmpty(studentNumber)) {
            etStudentNumber.setError("Enter your student number");
            etStudentNumber.requestFocus();
            return;
        }

        if (!isSponsor && TextUtils.isEmpty(faculty)) {
            etFaculty.setError("Enter your faculty");
            etFaculty.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        if (!Config.isVossieEmail(email)) {
            etEmail.setError("Use your @vossie.net account");
            etEmail.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        if (isTutor && TextUtils.isEmpty(getText(etSubject))) {
            etSubject.setError("Enter the module or subject you tutor");
            etSubject.requestFocus();
            return;
        }

        registrationInProgress = true;
        btnRegister.setEnabled(false);
        btnRegister.setText("Creating account...");

        try {
            JSONObject metadata = new JSONObject();
            metadata.put("full_name", name);
            metadata.put("role", role);

            // Keep existing metadata for learner/tutor accounts.
            metadata.put("student_number", isSponsor ? "" : studentNumber);
            metadata.put("faculty", isSponsor ? "" : faculty);
            metadata.put("tutor_subject", isTutor ? getText(etSubject) : "");
            metadata.put("module_codes", isTutor ? getText(etSubject) : "");
            metadata.put("tutor_bio", isTutor ? getText(etBio) : "");
            metadata.put("tutor_experience", isTutor ? getText(etExperience) : "");

            SupabaseApi.signUp(email, password, metadata, new SupabaseApi.ObjectCallback() {
                @Override
                public void onSuccess(JSONObject data) {
                    runOnUiThread(() -> {
                        restoreRegisterButton();

                        Toast.makeText(
                                RegisterActivity.this,
                                "Account created. Check your Vossie email if confirmation is required, then sign in.",
                                Toast.LENGTH_LONG
                        ).show();

                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        // The destination is selected after login by the saved profile role.
                        intent.putExtra("registered_role", role);
                        startActivity(intent);
                        finish();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        restoreRegisterButton();
                        String error = TextUtils.isEmpty(message)
                                ? "Registration failed. Please try again."
                                : message;
                        Toast.makeText(RegisterActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } catch (Exception e) {
            restoreRegisterButton();
            Toast.makeText(
                    RegisterActivity.this,
                    "Could not start registration. Please try again.",
                    Toast.LENGTH_LONG
            ).show();
            android.util.Log.e("PeerPathRegister", "Registration request failed", e);
        }
    }

    private void restoreRegisterButton() {
        registrationInProgress = false;
        btnRegister.setEnabled(true);
        updateRoleFields();
    }

    private String getText(TextInputEditText field) {
        if (field == null || field.getText() == null) return "";
        return field.getText().toString().trim();
    }
}
