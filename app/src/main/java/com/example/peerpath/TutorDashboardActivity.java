
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

        wire("cardResources", MaterialsActivity.class);
        wire("btnNotifications", NotificationActivity.class);
        wire("cardAnnounce", AnnounceActivity.class);

        // Create tests and assignments
        wire("cardAssignments", CreateAssessmentActivity.class);

        // View learner submissions, marks and results
        wire("cardTutorResults", TutorSubmissionsActivity.class);
    }
}
