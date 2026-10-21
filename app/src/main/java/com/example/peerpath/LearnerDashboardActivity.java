package com.example.peerpath;

public class LearnerDashboardActivity extends RoleDashboardBaseActivity {
    @Override protected int layoutId() { return R.layout.activity_learner_dashboard; }
    @Override protected String requiredRole() { return "learner"; }
    @Override protected boolean requiresVerifiedTutor() { return false; }

    @Override protected void wireRoleActions() {
        hide("cardTutorRequests");
        hide("cardTutorSessions");
        hide("cardMyMaterials");
        hide("cardAnnounce");
        hide("cardAdminReview");

        wire("cardFindTutor", TutorSearchActivity.class);
        wire("cardMySessions", MySessionsActivity.class);
        wire("cardMyReviews", MyReviewsActivity.class);
        wire("cardReviewFollowups", ReviewFollowupInboxActivity.class);
        wire("cardEditProfile", EditProfileActivity.class);
        wire("cardMessages", MessagesActivity.class);
        wire("cardLeaderboard", LeaderboardActivity.class);
        wire("cardBadges", BadgesActivity.class);
        wire("cardResources", com.example.peerpath.ui.resources.ResourceRepositoryActivity.class);
        wire("cardMaterials", MaterialsActivity.class);
        wire("btnNotifications", NotificationActivity.class);
        wire("cardBecomeTutor", BecomeTutorActivity.class);
    }
}
