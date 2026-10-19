package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.peerpath.supabase.SupabaseApi;
import org.json.JSONObject;

public class DashboardActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        if (!SupabaseApi.isLoggedIn()) { go(LoginActivity.class); return; }
        String id = SupabaseApi.userId();
        if (id == null || id.trim().isEmpty()) { SupabaseApi.clearSession(); go(LoginActivity.class); return; }
        SupabaseApi.getProfile(id, new SupabaseApi.ObjectCallback() {
            @Override public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> route(profile));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(DashboardActivity.this,
                        "Could not load your profile: " + message, android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    private void route(JSONObject p) {
        if (p == null) { android.widget.Toast.makeText(this,"Profile not found. Please contact support.",android.widget.Toast.LENGTH_LONG).show(); return; }
        String role = p.optString("role","").trim();
        String status = p.optString("verification_status","unverified").trim();
        if ("learner".equalsIgnoreCase(role)) go(LearnerDashboardActivity.class);
        else if ("tutor".equalsIgnoreCase(role)) go("verified".equalsIgnoreCase(status) ? TutorDashboardActivity.class : TutorPendingActivity.class);
        else if ("admin".equalsIgnoreCase(role)) go(AdminDashboardActivity.class);
        else {
            android.widget.Toast.makeText(this,"Your account role is not configured. Please contact support.",android.widget.Toast.LENGTH_LONG).show();
        }
    }
    private void go(Class<?> c) {
        Intent i = new Intent(this,c);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i); finish();
    }
}
