
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

        // Default to learner if no option is selected.
        if (rgAccountType.getCheckedRadioButtonId() == -1) {
            rgAccountType.check(R.id.rbLearner);
        }

        updateTutorFields();

        rgAccountType.setOnCheckedChangeListener((group, checkedId) -> {
            updateTutorFields();
        });

        btnRegister.setOnClickListener(v -> registerUser());

        findViewById(R.id.tvLogin).setOnClickListener(v -> {
            startActivity(new Intent(
                    RegisterActivity.this,
                    LoginActivity.class
            ));
            finish();
        });
    }

    private void updateTutorFields() {
        boolean isTutor =
                rgAccountType.getCheckedRadioButtonId() == R.id.rbTutor;

        tutorFields.setVisibility(isTutor ? View.VISIBLE : View.GONE);
    }

    private void registerUser() {

        if (registrationInProgress) {
            return;
        }

        String name = getText(etName);
        String studentNumber = getText(etStudentNumber);
        String faculty = getText(etFaculty);
        String email = getText(etEmail).toLowerCase(Locale.ROOT);
        String password = getText(etPassword);

        boolean isTutor =
                rgAccountType.getCheckedRadioButtonId() == R.id.rbTutor;

        // Validate the form before contacting Supabase.
        if (TextUtils.isEmpty(name)) {
            etName.setError("Enter your full name");
            etName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(studentNumber)) {
            etStudentNumber.setError("Enter your student number");
            etStudentNumber.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(faculty)) {
            etFaculty.setError("Enter your faculty");
            etFaculty.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)
                || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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

        // Prevent duplicate taps and duplicate registration requests.
        registrationInProgress = true;
        btnRegister.setEnabled(false);
        btnRegister.setText("Creating account...");

        try {
            JSONObject metadata = new JSONObject();

            metadata.put("full_name", name);
            metadata.put("student_number", studentNumber);
            metadata.put("faculty", faculty);

            // These values must match the Supabase profile trigger/schema.
            metadata.put("role", isTutor ? "tutor" : "learner");

            metadata.put("tutor_subject",
                    isTutor ? getText(etSubject) : "");

            metadata.put("module_codes",
                    isTutor ? getText(etSubject) : "");

            metadata.put("tutor_bio",
                    isTutor ? getText(etBio) : "");

            metadata.put("tutor_experience",
                    isTutor ? getText(etExperience) : "");

            SupabaseApi.signUp(
                    email,
                    password,
                    metadata,
                    new SupabaseApi.ObjectCallback() {

                        @Override
                        public void onSuccess(JSONObject data) {
                            runOnUiThread(() -> {
                                restoreRegisterButton();

                                Toast.makeText(
                                        RegisterActivity.this,
                                        "Account created successfully. "
                                                + "Check your Vossie email "
                                                + "if confirmation is required, "
                                                + "then sign in.",
                                        Toast.LENGTH_LONG
                                ).show();

                                Intent intent = new Intent(
                                        RegisterActivity.this,
                                        LoginActivity.class
                                );

                                // Do not return to the registration form.
                                intent.addFlags(
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                );

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

                                Toast.makeText(
                                        RegisterActivity.this,
                                        error,
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                        }
                    }
            );

        } catch (Exception e) {
            restoreRegisterButton();

            Toast.makeText(
                    RegisterActivity.this,
                    "Could not start registration. Please try again.",
                    Toast.LENGTH_LONG
            ).show();

            android.util.Log.e(
                    "PeerPathRegister",
                    "Registration request failed",
                    e
            );
        }
    }

    private void restoreRegisterButton() {
        registrationInProgress = false;
        btnRegister.setEnabled(true);
        btnRegister.setText("Create Account");
    }

    private String getText(TextInputEditText field) {
        if (field == null || field.getText() == null) {
            return "";
        }

        return field.getText().toString().trim();
    }
}
