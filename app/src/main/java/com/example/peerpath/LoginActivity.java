package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Role routing with a fail-closed tutor approval gate.
 * Supabase RLS / server-side auth enforcement is still required to protect API data.
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "PeerPathLogin";
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);

        if (etEmail == null || etPassword == null || btnSignIn == null) {
            Toast.makeText(this, "Login layout is missing required view IDs.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnSignIn.setOnClickListener(v -> loginUser());
        findViewById(R.id.tvForgotPassword).setOnClickListener(v -> resetPassword());
        findViewById(R.id.tvSignUp).setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private void loginUser() {
        String email = getText(etEmail).toLowerCase(Locale.ROOT);
        String password = getText(etPassword);

        if (!Config.isVossieEmail(email)) {
            etEmail.setError("Enter your Vossie email address.");
            etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Enter your password.");
            etPassword.requestFocus();
            return;
        }

        setBusy(true);
        SupabaseApi.signIn(email, password, new SupabaseApi.ObjectCallback() {
            @Override public void onSuccess(JSONObject data) {
                runOnUiThread(LoginActivity.this::loadProfileAndRoute);
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    SupabaseApi.clearSession();
                    setBusy(false);
                    showMessage(message);
                });
            }
        });
    }

    private void loadProfileAndRoute() {
        String id = SupabaseApi.userId();
        if (TextUtils.isEmpty(id)) {
            denyAndSignOut("Your account session is incomplete. Please sign in again.");
            return;
        }

        SupabaseApi.getProfile(id, new SupabaseApi.ObjectCallback() {
            @Override public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> routeUsingProfile(profile));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> denyAndSignOut(
                        "PeerPath could not verify your account status. You have been signed out. " + safe(message)));
            }
        });
    }

    private void routeUsingProfile(JSONObject profile) {
        String role = lower(profile.optString("role", ""));
        String requestedRole = lower(profile.optString("requested_role", ""));
        String verification = lower(profile.optString("verification_status", ""));
        String applicationStatus = lower(profile.optString("application_status", ""));
        String note = profile.optString("verification_note", "").trim();

        // Fail closed for tutor role. Missing verification status is NOT approval.
        if ("tutor".equals(role)) {
            boolean approved = "verified".equals(verification)
                    && (applicationStatus.isEmpty()
                    || "approved".equals(applicationStatus)
                    || "verified".equals(applicationStatus));
            if (!approved) {
                String message = "Your tutor account is not verified. You cannot sign in until an administrator "
                        + "approves your academic proof.";
                if ("rejected".equals(verification) || "rejected".equals(applicationStatus)) {
                    message = "Your tutor application was rejected. You cannot sign in as a tutor.";
                    if (!note.isEmpty()) message += " Reason: " + note;
                }
                denyAndSignOut(message);
                return;
            }
            openScreen(DashboardActivity.class);
            return;
        }

        // If trigger stores requested_role in profiles, do not let a pending
        // tutor applicant bypass the gate by receiving learner as their role.
        if ("tutor".equals(requestedRole)) {
            denyAndSignOut("Your tutor application is pending or not verified. You cannot sign in until an administrator approves your academic proof.");
            return;
        }

        switch (role) {
            case "admin":
                openScreen(AdminDashboardActivity.class);
                break;
            case "sponsor":
                openScreen(SponsorActivity.class);
                break;
            case "learner":
                openScreen(DashboardActivity.class);
                break;
            default:
                Log.w(TAG, "Unknown profile role: " + role);
                denyAndSignOut("Your profile has no valid account role. Please contact PeerPath support.");
                break;
        }
    }

    private void denyAndSignOut(String message) {
        // Supabase has already authenticated the password at this point.
        // Clear the local session and prevent access to any app screen.
        SupabaseApi.clearSession();
        setBusy(false);
        showMessage(message);
    }

    private void openScreen(Class<?> screen) {
        Intent intent = new Intent(this, screen);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void resetPassword() {
        String email = getText(etEmail).toLowerCase(Locale.ROOT);
        if (!Config.isVossieEmail(email)) {
            etEmail.setError("Enter your Vossie email first.");
            etEmail.requestFocus();
            return;
        }
        setBusy(true);
        SupabaseApi.resetPassword(email, new SupabaseApi.EmptyCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> {
                    setBusy(false);
                    showMessage("Password reset email sent. Check your inbox.");
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showMessage(message);
                });
            }
        });
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "" : value.trim();
    }

    private String getText(TextInputEditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    private void setBusy(boolean busy) {
        if (btnSignIn == null) return;
        btnSignIn.setEnabled(!busy);
        btnSignIn.setText(busy ? "Checking account..." : "Sign In     →");
    }

    private void showMessage(String message) {
        Toast.makeText(this, message == null || message.trim().isEmpty()
                ? "Something went wrong. Please try again." : message, Toast.LENGTH_LONG).show();
    }
}
