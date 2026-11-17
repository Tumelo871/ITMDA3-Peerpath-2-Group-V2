
package com.example.peerpath;

import com.example.peerpath.ui.resources.ResourceRepositoryActivity;

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

        // Hide sections that learners should not see.
        hide("cardTutorRequests");
        hide("cardTutorSessions");
        hide("cardMyMaterials");
        hide("cardAnnounce");
        hide("cardAdminReview");
        hide("cardMaterials");

        // Learner navigation.
        wire("cardFindTutor", TutorSearchActivity.class);
        wire("cardMySessions", MySessionsActivity.class);
        wire("cardMyReviews", MyReviewsActivity.class);
        wire("cardReviewFollowups", ReviewFollowupInboxActivity.class);
        wire("cardEditProfile", EditProfileActivity.class);
        wire("cardMessages", MessagesActivity.class);
        wire("cardLeaderboard", LeaderboardActivity.class);
        wire("cardBadges", BadgesActivity.class);
        wire("cardSponsorRequests", SponsorRequestsActivity.class);

        // Learning resources.
        wire("cardResources", ResourceRepositoryActivity.class);

        // Notifications.
        wire("btnNotifications", NotificationActivity.class);

        // Tutor application.
        wire("cardBecomeTutor", BecomeTutorActivity.class);

        // Announcements.
        wire("cardAnnouncements", LearnerAnnouncementsActivity.class);

        // Assignments and tests.
        wire("cardAssignments", LearnerAssignmentsActivity.class);
    }
}
