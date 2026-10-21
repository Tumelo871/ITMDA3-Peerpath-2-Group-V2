package com.example.peerpath;

public class TutorDashboardActivity extends RoleDashboardBaseActivity {
    @Override protected int layoutId() { return R.layout.activity_tutor_dashboard; }
    @Override protected String requiredRole() { return "tutor"; }
    @Override protected boolean requiresVerifiedTutor() { return true; }

    @Override protected void wireRoleActions() {
        wire("cardTutorRequests", TutorRequestsActivity.class);
        wire("cardTutorSessions", TutorSessionsActivity.class);
        wire("cardEditProfile", EditProfileActivity.class);
        wire("cardMessages", MessagesActivity.class);
        wire("cardReviewFollowups", ReviewFollowupInboxActivity.class);
        wire("cardMyMaterials", MaterialsActivity.class);
        wire("cardResources", com.example.peerpath.ui.resources.ResourceRepositoryActivity.class);
        wire("btnNotifications", NotificationActivity.class);
        wire("cardAnnounce", AnnounceActivity.class);
    }
}
