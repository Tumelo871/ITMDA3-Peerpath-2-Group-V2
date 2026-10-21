package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.Locale;

/** Login screen with role-based routing for PeerPath accounts. */
public class LoginActivity extends AppCompatActivity {
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
                runOnUiThread(() -> loadProfileAndRoute());
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showMessage(message);
                });
            }
        });
    }

    private void loadProfileAndRoute() {
        String userId = SupabaseApi.userId();
        if (userId == null || userId.trim().isEmpty()) {
            setBusy(false);
            showMessage("Login succeeded, but the user ID is missing.");
            return;
        }

        SupabaseApi.getProfile(userId, new SupabaseApi.ObjectCallback() {
            @Override public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> {
                    String role = profile.optString("role", "").trim().toLowerCase(Locale.ROOT);
                    switch (role) {
                        case "admin": openScreen(AdminDashboardActivity.class); break;
                        case "sponsor": openScreen(SponsorActivity.class); break;
                        case "learner":
                        case "tutor": openScreen(DashboardActivity.class); break;
                        default:
                            setBusy(false);
                            showMessage("Your profile has no valid account role. Please contact PeerPath support.");
                    }
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showMessage("Unable to load your profile: " + message);
                });
            }
        });
    }

    private void openScreen(Class<?> screen) {
        Intent intent = new Intent(LoginActivity.this, screen);
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
        SupabaseApi.resetPassword(email, new SupabaseApi.EmptyCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> showMessage("Password reset email sent."));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> showMessage(message));
            }
        });
    }

    private void setBusy(boolean busy) {
        btnSignIn.setEnabled(!busy);
        btnSignIn.setText(busy ? "Signing In..." : "Sign In     →");
    }

    private String getText(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private void showMessage(String message) {
        Toast.makeText(this,
                message == null || message.trim().isEmpty()
                        ? "Something went wrong. Please try again." : message,
                Toast.LENGTH_LONG).show();
    }
}
