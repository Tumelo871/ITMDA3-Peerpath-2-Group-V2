
package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONObject;

public class DashboardActivity extends AppCompatActivity {

    private boolean routing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseApi.init(getApplicationContext());

        if (!SupabaseApi.isLoggedIn()) {
            go(LoginActivity.class);
            return;
        }

        String id = SupabaseApi.userId();

        if (id == null || id.trim().isEmpty()) {
            SupabaseApi.clearSession();
            go(LoginActivity.class);
            return;
        }

        SupabaseApi.getProfile(id, new SupabaseApi.ObjectCallback() {

            @Override
            public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> route(profile));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(
                            DashboardActivity.this,
                            "Could not load your profile: " + message,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void route(JSONObject profile) {
        if (isFinishing() || isDestroyed() || routing) return;

        if (profile == null) {
            Toast.makeText(
                    this,
                    "Profile not found. Please contact support.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String role = profile.optString("role", "").trim();
        String status = profile.optString(
                "verification_status", "unverified"
        ).trim();

        if ("learner".equalsIgnoreCase(role)) {
            go(LearnerDashboardActivity.class);

        } else if ("tutor".equalsIgnoreCase(role)) {
            if ("verified".equalsIgnoreCase(status)) {
                go(TutorDashboardActivity.class);
            } else {
                go(TutorPendingActivity.class);
            }

        } else if ("admin".equalsIgnoreCase(role)) {
            go(AdminDashboardActivity.class);

        } else {
            Toast.makeText(
                    this,
                    "Your account role is not configured. "
                            + "Please contact support.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void go(Class<?> destination) {
        if (routing || isFinishing() || isDestroyed()) return;

        routing = true;

        Intent intent = new Intent(this, destination);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}
