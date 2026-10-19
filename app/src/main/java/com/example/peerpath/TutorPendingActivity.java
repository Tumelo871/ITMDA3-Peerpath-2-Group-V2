package com.example.peerpath;
public class TutorPendingActivity extends RoleDashboardBaseActivity {
    @Override protected int layoutId() { return R.layout.activity_tutor_pending; }
    @Override protected String requiredRole() { return "tutor"; }
    @Override protected boolean requiresVerifiedTutor() { return false; }
    @Override protected void wireRoleActions() {
        wire("cardEditProfile", EditProfileActivity.class);
        wire("cardResources", com.example.peerpath.ui.resources.ResourceRepositoryActivity.class);
    }
}
