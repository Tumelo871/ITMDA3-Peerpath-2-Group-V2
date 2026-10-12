
package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONObject;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvWelcome;
    private TextView tvRole;
    private TextView tvLevel;
    private TextView tvXp;
    private TextView tvXpHint;
    private TextView tvProgress;
    private TextView tvSessions;
    private TextView tvStreak;
    private TextView tvBadges;
    private TextView tvRating;
    private TextView tvNotificationCount;

    private ProgressBar progressXp;
    private ProgressBar progressLearning;

    private View cardFindTutor;
    private View cardMySessions;
    private View cardTutorRequests;
    private View cardTutorSessions;
    private View cardEditProfile;
    private View cardMyMaterials;
    private View cardAnnounce;
    private View cardBecomeTutor;
    private View cardAdminReview;
    private View cardResources;

    private String role = "learner";
    private boolean verified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_dashboard);

        if (!SupabaseApi.isLoggedIn()) {
            go(LoginActivity.class);
            return;
        }

        bindViews();
        wireClicks();
        loadProfile();
    }

    private void bindViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvRole = findViewById(R.id.tvRole);
        tvLevel = findViewById(R.id.tvLevel);
        tvXp = findViewById(R.id.tvXp);
        tvXpHint = findViewById(R.id.tvXpHint);
        tvProgress = findViewById(R.id.tvProgress);
        tvSessions = findViewById(R.id.tvSessions);
        tvStreak = findViewById(R.id.tvStreak);
        tvBadges = findViewById(R.id.tvBadges);
        tvRating = findViewById(R.id.tvRating);
        tvNotificationCount = findViewById(R.id.tvNotificationCount);

        progressXp = findViewById(R.id.progressXp);
        progressLearning = findViewById(R.id.progressLearning);

        cardFindTutor = findViewById(R.id.cardFindTutor);
        cardMySessions = findViewById(R.id.cardMySessions);
        cardTutorRequests = findViewById(R.id.cardTutorRequests);
        cardTutorSessions = findViewById(R.id.cardTutorSessions);
        cardEditProfile = findViewById(R.id.cardEditProfile);
        cardMyMaterials = findViewById(R.id.cardMyMaterials);
        cardAnnounce = findViewById(R.id.cardAnnounce);
        cardBecomeTutor = findViewById(R.id.cardBecomeTutor);
        cardAdminReview = findViewById(R.id.cardAdminReview);
        cardResources = findViewById(R.id.cardResources);
    }

    private void wireClicks() {
        open(R.id.cardFindTutor, TutorSearchActivity.class);
        open(R.id.cardMySessions, MySessionsActivity.class);
        open(R.id.cardTutorRequests, TutorRequestsActivity.class);
        open(R.id.cardTutorSessions, TutorSessionsActivity.class);
        open(R.id.cardEditProfile, EditProfileActivity.class);
        open(R.id.cardMessages, MessagesActivity.class);
        open(R.id.cardLeaderboard, LeaderboardActivity.class);
        open(R.id.cardBadges, BadgesActivity.class);
        open(R.id.cardAnnounce, AnnounceActivity.class);

        // IMPORTANT: BecomeTutorActivity is in com.example.peerpath,
        // not com.example.peerpath.ui.tutors.
        setClickIfPresent(
                cardBecomeTutor,
                BecomeTutorActivity.class
        );

        // These activities must exist in the packages specified here.
        setClickIfPresent(
                cardAdminReview,
                com.example.peerpath.ui.admin.AdminReviewActivity.class
        );

        setClickIfPresent(
                cardResources,
                com.example.peerpath.ui.resources.ResourceRepositoryActivity.class
        );

        open(R.id.cardMaterials, MaterialsActivity.class);

        View notifications = findViewById(R.id.btnNotifications);
        if (notifications != null) {
            notifications.setOnClickListener(v ->
                    openActivity(NotificationActivity.class));
        }

        View logout = findViewById(R.id.btnLogout);
        if (logout != null) {
            logout.setOnClickListener(v -> {
                SupabaseApi.clearSession();
                go(LoginActivity.class);
            });
        }
    }

    private void open(int viewId, Class<?> activityClass) {
        View view = findViewById(viewId);
        setClickIfPresent(view, activityClass);
    }

    private void setClickIfPresent(View view, Class<?> activityClass) {
        if (view == null) {
            return;
        }

        view.setOnClickListener(v -> openActivity(activityClass));
    }

    private void openActivity(Class<?> activityClass) {
        try {
            Intent intent = new Intent(this, activityClass);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Could not open " + activityClass.getSimpleName()
                            + ". Check its package and AndroidManifest.xml.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void loadProfile() {
        String userId = SupabaseApi.userId();

        if (userId == null || userId.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "Your session has expired. Please sign in again.",
                    Toast.LENGTH_LONG
            ).show();

            go(LoginActivity.class);
            return;
        }

        SupabaseApi.getProfile(
                userId,
                new SupabaseApi.ObjectCallback() {
                    @Override
                    public void onSuccess(JSONObject profile) {
                        runOnUiThread(() -> showProfile(profile));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(
                                DashboardActivity.this,
                                "Could not load your profile: " + message,
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    private void showProfile(JSONObject profile) {
        if (profile == null) {
            Toast.makeText(
                    this,
                    "Your profile could not be loaded.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        role = profile.optString("role", "learner");

        String verificationStatus =
                profile.optString("verification_status", "unverified");

        verified = "verified".equalsIgnoreCase(verificationStatus);

        String name = profile.optString("full_name", "PeerPath User");
        if (name.trim().isEmpty()) {
            name = "PeerPath User";
        }

        long xp = Math.max(0, profile.optLong("xp", 0));

        // Support the column names used by different profile versions.
        long completed = profile.optLong(
                "sessions_completed",
                profile.optLong("completed_sessions", 0)
        );

        long total = profile.optLong("total_sessions", 0);

        long streak = profile.optLong(
                "streak",
                profile.optLong("streak_days", 0)
        );

        int level = (int) Math.max(1, (xp / 100) + 1);
        int xpProgress = (int) (xp % 100);

        tvWelcome.setText("Hello, " + name + " 👋");

        if ("admin".equalsIgnoreCase(role)) {
            tvRole.setText("Administrator");
        } else if ("tutor".equalsIgnoreCase(role)) {
            tvRole.setText(verified
                    ? "Verified Tutor"
                    : "Tutor · Verification pending");
        } else {
            tvRole.setText("Learner");
        }

        tvLevel.setText("Level " + level);
        tvXp.setText(xp + " XP");
        progressXp.setMax(100);
        progressXp.setProgress(xpProgress);
        tvXpHint.setText((100 - xpProgress) + " XP to next level");

        tvSessions.setText(completed + "\nSessions");
        tvStreak.setText("🔥 " + streak + "\nDay streak");
        tvBadges.setText("🏅 View badges");

        int percentage = total > 0
                ? (int) Math.min(100, completed * 100 / total)
                : 0;

        progressLearning.setMax(100);
        progressLearning.setProgress(percentage);
        tvProgress.setText("Learning progress: " + percentage + "%");

        updateCards();
    }

    private void updateCards() {
        boolean admin = "admin".equalsIgnoreCase(role);
        boolean tutor = "tutor".equalsIgnoreCase(role);

        setVisibility(cardFindTutor,
                !admin && !tutor);

        setVisibility(cardMySessions,
                !admin && !tutor);

        boolean verifiedTutor = tutor && verified;

        setVisibility(cardTutorRequests, verifiedTutor);
        setVisibility(cardTutorSessions, verifiedTutor);
        setVisibility(cardEditProfile, true);
        setVisibility(cardMyMaterials, verifiedTutor);
        setVisibility(cardAnnounce, verifiedTutor);

        setVisibility(cardBecomeTutor,
                !admin && !verifiedTutor);

        setVisibility(cardAdminReview, admin);
        setVisibility(cardResources, true);
    }

    private void setVisibility(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void go(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }
}
