
package com.example.peerpath;

import com.example.peerpath.supabase.SupabaseApi;

public class LearnerDashboardActivity extends RoleDashboardBaseActivity {

    @Override
    protected int layoutId() {
        return R.layout.activity_learner_dashboard;
    }

    @Override
    protected String requiredRole() {
        return "learner";
    }

    @Override
    protected boolean requiresVerifiedTutor() {
        return false;
    }

    @Override
    protected void wireRoleActions() {

        // Hide cards that learners should not see.
        hide("cardTutorRequests");
        hide("cardTutorSessions");
        hide("cardMyMaterials");
        hide("cardAnnounce");
        hide("cardAdminReview");

        // Learner dashboard actions.
        wire("cardFindTutor", TutorSearchActivity.class);
        wire("cardMySessions", MySessionsActivity.class);
        wire("cardEditProfile", EditProfileActivity.class);
        wire("cardMessages", MessagesActivity.class);
        wire("cardLeaderboard", LeaderboardActivity.class);
        wire("cardBadges", BadgesActivity.class);

        wire(
                "cardResources",
                com.example.peerpath.ui.resources.ResourceRepositoryActivity.class
        );

        wire("cardMaterials", MaterialsActivity.class);
        wire("btnNotifications", NotificationActivity.class);
        wire("cardBecomeTutor", BecomeTutorActivity.class);
    }
}
