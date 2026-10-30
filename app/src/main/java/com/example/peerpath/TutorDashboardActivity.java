
package com.example.peerpath;

import android.content.Intent;
import android.view.View;

public class TutorDashboardActivity extends RoleDashboardBaseActivity {

    @Override
    protected int layoutId() {
        return R.layout.activity_tutor_dashboard;
    }

    @Override
    protected String requiredRole() {
        return "tutor";
    }

    @Override
    protected boolean requiresVerifiedTutor() {
        return true;
    }

    @Override
    protected void wireRoleActions() {

        wire("cardTutorRequests", TutorRequestsActivity.class);
        wire("cardTutorSessions", TutorSessionsActivity.class);
        wire("cardEditProfile", EditProfileActivity.class);
        wire("cardMessages", MessagesActivity.class);
        wire("cardReviewFollowups", ReviewFollowupInboxActivity.class);

        // Tutor's own learning materials
        View myMaterials = findViewById(R.id.cardMyMaterials);

        if (myMaterials != null) {
            myMaterials.setOnClickListener(v -> {
                Intent intent = new Intent(
                        TutorDashboardActivity.this,
                        MaterialsActivity.class
                );
                intent.putExtra("mine", true);
                startActivity(intent);
            });
        }

        // Shared learning repository
        wire("cardResources", MaterialsActivity.class);

        wire("btnNotifications", NotificationActivity.class);
        wire("cardAnnounce", AnnounceActivity.class);
    }
}
