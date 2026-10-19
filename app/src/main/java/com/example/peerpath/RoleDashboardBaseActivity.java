
package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONObject;

/**
 * Shared session and profile validation for role-specific dashboards.
 */
public abstract class RoleDashboardBaseActivity extends AppCompatActivity {

    protected abstract int layoutId();

    protected abstract String requiredRole();

    protected abstract boolean requiresVerifiedTutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseApi.init(getApplicationContext());

        // Redirect signed-out users to login.
        if (!SupabaseApi.isLoggedIn()) {
            redirect(LoginActivity.class);
            return;
        }

        setContentView(layoutId());

        // Wire common navigation actions.
        wire("btnLogout", null);
        wire("btnBack", DashboardActivity.class);

        String uid = SupabaseApi.userId();

        if (uid == null || uid.trim().isEmpty()) {
            SupabaseApi.clearSession();
            redirect(LoginActivity.class);
            return;
        }

        // Load the current user's profile from Supabase.
        SupabaseApi.getProfile(uid, new SupabaseApi.ObjectCallback() {
            @Override
            public void onSuccess(JSONObject profile) {
                runOnUiThread(() -> checkAndRender(profile));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(
                            RoleDashboardBaseActivity.this,
                            "Could not load profile: " + message,
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void checkAndRender(JSONObject profile) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (profile == null) {
            Toast.makeText(
                    this,
                    "Your profile could not be loaded.",
                    Toast.LENGTH_LONG
            ).show();

            redirect(LoginActivity.class);
            return;
        }

        String role = profile.optString("role", "").trim();
        String status = profile.optString(
                "verification_status",
                "unverified"
        ).trim();

        // Verify that the current user belongs on this dashboard.
        if (!requiredRole().equalsIgnoreCase(role)) {
            redirect(DashboardActivity.class);
            return;
        }

        // Unverified tutors must not access the verified tutor dashboard.
        if (requiresVerifiedTutor()
                && !"verified".equalsIgnoreCase(status)) {
            redirect(TutorPendingActivity.class);
            return;
        }

        TextView name = findViewById(R.id.tvDashboardName);

        if (name != null) {
            name.setText(
                    "Hello, "
                            + clean(profile.optString(
                            "full_name",
                            "PeerPath User"
                    ))
                            + " 👋"
            );
        }

        // Wire role-specific actions only after validation.
        wireRoleActions();
    }

    protected void wireRoleActions() {
        // Override this in each role-specific dashboard.
    }

    protected void wire(String idName, Class<?> target) {
        int id = getResources().getIdentifier(
                idName,
                "id",
                getPackageName()
        );

        View view = id == 0 ? null : findViewById(id);

        if (view == null) {
            return;
        }

        view.setOnClickListener(v -> {
            if ("btnLogout".equals(idName)) {
                SupabaseApi.clearSession();
                redirect(LoginActivity.class);
            } else if (target != null) {
                startActivity(new Intent(
                        RoleDashboardBaseActivity.this,
                        target
                ));
            }
        });
    }

    protected void hide(String idName) {
        int id = getResources().getIdentifier(
                idName,
                "id",
                getPackageName()
        );

        View view = id == 0 ? null : findViewById(id);

        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    protected void wireId(int id, Class<?> target) {
        View view = findViewById(id);

        if (view != null && target != null) {
            view.setOnClickListener(v ->
                    startActivity(new Intent(
                            RoleDashboardBaseActivity.this,
                            target
                    ))
            );
        }
    }

    private String clean(String value) {
        if (value == null
                || value.trim().isEmpty()
                || "null".equalsIgnoreCase(value.trim())) {
            return "PeerPath User";
        }

        return value.trim();
    }

    private void redirect(Class<?> destination) {
        Intent intent = new Intent(this, destination);

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}
