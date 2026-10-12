package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnSignIn;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_login);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnSignIn.setOnClickListener(v -> loginUser());
        findViewById(R.id.tvForgotPassword).setOnClickListener(v -> resetPassword());
        findViewById(R.id.tvSignUp).setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void loginUser() {
        String email = get(etEmail).toLowerCase(Locale.ROOT);
        String password = get(etPassword);
        if (!Config.isVossieEmail(email)) { etEmail.setError("Use your Vossie account, e.g. eduv45738339@vossie.net"); etEmail.requestFocus(); return; }
        if (TextUtils.isEmpty(password)) { etPassword.setError("Enter your password"); etPassword.requestFocus(); return; }
        setBusy(true);
        SupabaseApi.signIn(email, password, new SupabaseApi.ObjectCallback() {
            @Override public void onSuccess(JSONObject data) { runOnUiThread(() -> goToDashboard()); }
            @Override public void onError(String message) { runOnUiThread(() -> { setBusy(false); Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show(); }); }
        });
    }

    private void resetPassword() {
        String email = get(etEmail).toLowerCase(Locale.ROOT);
        if (!Config.isVossieEmail(email)) { etEmail.setError("Enter your Vossie email first"); return; }
        SupabaseApi.resetPassword(email, new SupabaseApi.EmptyCallback() {
            @Override public void onSuccess() { runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Password reset email sent.", Toast.LENGTH_LONG).show()); }
            @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show()); }
        });
    }

    private void setBusy(boolean busy) { btnSignIn.setEnabled(!busy); btnSignIn.setText(busy ? "Signing In..." : "Sign In     →"); }
    private void goToDashboard() { Intent i = new Intent(this, DashboardActivity.class); i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); startActivity(i); finish(); }
    private String get(TextInputEditText f) { return f.getText() == null ? "" : f.getText().toString().trim(); }
}
