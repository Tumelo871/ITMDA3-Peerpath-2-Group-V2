package com.example.peerpath;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * PeerPath Gamification
 *
 * Supabase-compatible gamification helper.
 *
 * XP, levels, streaks and badges are derived from the
 * user's profile/session information.
 *
 * IMPORTANT:
 * This class does NOT use Firebase.
 *
 * Persistent values such as:
 *
 * - xp
 * - streak
 * - completed_sessions
 * - total_sessions
 *
 * are stored in the Supabase profiles table.
 */
public final class Gamification {

    // ============================================================
    // XP VALUES
    // ============================================================

    public static final int XP_DAILY_CHECKIN = 5;

    public static final int XP_SESSION_LEARNER = 25;

    public static final int XP_SESSION_TUTOR = 40;

    public static final int XP_REVIEW_GIVEN = 10;

    public static final int XP_FIVE_STAR_BONUS = 10;

    public static final int XP_PER_LEVEL = 100;


    // ============================================================
    // LEVEL TITLES
    // ============================================================

    private static final String[] TITLES = {
            "Newcomer",
            "Explorer",
            "Achiever",
            "Scholar",
            "Mentor",
            "Champion",
            "Legend"
    };


    private Gamification() {
        // Utility class
    }


    // ============================================================
    // LEVEL SYSTEM
    // ============================================================

    /**
     * Calculate the user's level from XP.
     *
     * 0-99 XP   = Level 1
     * 100-199   = Level 2
     * 200-299   = Level 3
     */
    public static int levelFor(long xp) {

        xp = Math.max(0, xp);

        return (int) (xp / XP_PER_LEVEL) + 1;
    }


    /**
     * Get the title associated with the user's level.
     */
    public static String titleFor(long xp) {

        int level = levelFor(xp);

        int index = Math.min(
                level - 1,
                TITLES.length - 1
        );

        return TITLES[index];
    }


    /**
     * XP progress inside the current level.
     */
    public static int progressInLevel(long xp) {

        xp = Math.max(0, xp);

        return (int) (xp % XP_PER_LEVEL);
    }


    /**
     * XP remaining until the next level.
     */
    public static long xpToNextLevel(long xp) {

        xp = Math.max(0, xp);

        long progress = xp % XP_PER_LEVEL;

        if (progress == 0) {
            return XP_PER_LEVEL;
        }

        return XP_PER_LEVEL - progress;
    }


    /**
     * Percentage progress toward the next level.
     */
    public static int levelProgressPercent(long xp) {

        int progress = progressInLevel(xp);

        return (progress * 100) / XP_PER_LEVEL;
    }


    // ============================================================
    // SAFE JSON HELPERS
    // ============================================================

    /**
     * Safely retrieve a long value from Supabase JSON.
     *
     * Handles:
     * Long
     * Integer
     * Double
     * String
     * null
     */
    public static long getLong(
            JSONObject object,
            String key
    ) {

        if (object == null || key == null) {
            return 0L;
        }

        try {

            Object value = object.opt(key);

            if (value == null ||
                    value == JSONObject.NULL) {

                return 0L;
            }

            if (value instanceof Number) {

                return ((Number) value).longValue();
            }

            return Long.parseLong(
                    String.valueOf(value)
            );

        } catch (Exception e) {

            return 0L;
        }
    }


    /**
     * Safely retrieve a double from Supabase JSON.
     */
    public static double getDouble(
            JSONObject object,
            String key
    ) {

        if (object == null || key == null) {
            return 0.0;
        }

        try {

            Object value = object.opt(key);

            if (value == null ||
                    value == JSONObject.NULL) {

                return 0.0;
            }

            if (value instanceof Number) {

                return ((Number) value).doubleValue();
            }

            return Double.parseDouble(
                    String.valueOf(value)
            );

        } catch (Exception e) {

            return 0.0;
        }
    }


    /**
     * Safely retrieve a String.
     */
    public static String getString(
            JSONObject object,
            String key
    ) {

        if (object == null || key == null) {
            return "";
        }

        return object.optString(
                key,
                ""
        );
    }


    // ============================================================
    // DATE HELPERS
    // ============================================================

    private static String dateKey(
            Calendar calendar
    ) {

        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(
                new Date(
                        calendar.getTimeInMillis()
                )
        );
    }


    /**
     * Get today's date in yyyy-MM-dd format.
     */
    public static String todayKey() {

        return dateKey(
                Calendar.getInstance()
        );
    }


    /**
     * Get yesterday's date.
     */
    public static String yesterdayKey() {

        Calendar calendar =
                Calendar.getInstance();

        calendar.add(
                Calendar.DAY_OF_YEAR,
                -1
        );

        return dateKey(calendar);
    }


    // ============================================================
    // STREAK CALCULATION
    // ============================================================

    /**
     * Calculate the new streak based on the user's
     * previous activity date.
     *
     * If the user already checked in today:
     * the existing streak is preserved.
     *
     * If they checked in yesterday:
     * streak increases by one.
     *
     * Otherwise:
     * streak starts at one.
     */
    public static long calculateStreak(
            String lastActiveDate,
            long currentStreak
    ) {

        String today =
                todayKey();

        String yesterday =
                yesterdayKey();

        if (today.equals(lastActiveDate)) {

            return Math.max(
                    0,
                    currentStreak
            );
        }

        if (yesterday.equals(lastActiveDate)) {

            return Math.max(
                    0,
                    currentStreak
            ) + 1;
        }

        return 1;
    }


    /**
     * Determine whether the user has already
     * checked in today.
     */
    public static boolean checkedInToday(
            String lastActiveDate
    ) {

        return todayKey().equals(
                lastActiveDate
        );
    }


    /**
     * Calculate the best streak.
     */
    public static long calculateBestStreak(
            long previousBest,
            long currentStreak
    ) {

        return Math.max(
                Math.max(0, previousBest),
                Math.max(0, currentStreak)
        );
    }


    // ============================================================
    // XP CALCULATIONS
    // ============================================================

    /**
     * XP awarded when a learner completes a session.
     */
    public static int learnerSessionXp() {

        return XP_SESSION_LEARNER;
    }


    /**
     * XP awarded when a tutor completes a session.
     */
    public static int tutorSessionXp() {

        return XP_SESSION_TUTOR;
    }


    /**
     * XP awarded for submitting a review.
     */
    public static int reviewXp() {

        return XP_REVIEW_GIVEN;
    }


    /**
     * Calculate the XP for a five-star review.
     */
    public static int reviewXp(
            double rating
    ) {

        int xp =
                XP_REVIEW_GIVEN;

        if (rating >= 5.0) {

            xp += XP_FIVE_STAR_BONUS;
        }

        return xp;
    }


    /**
     * Calculate XP after a completed session.
     */
    public static long xpAfterSession(
            long currentXp,
            boolean tutor
    ) {

        long reward = tutor
                ? XP_SESSION_TUTOR
                : XP_SESSION_LEARNER;

        return Math.max(
                0,
                currentXp
        ) + reward;
    }


    // ============================================================
    // BADGES
    // ============================================================

    public static class Badge {

        public final String emoji;

        public final String title;

        public final String description;

        public final boolean unlocked;


        public Badge(
                String emoji,
                String title,
                String description,
                boolean unlocked
        ) {

            this.emoji = emoji;

            this.title = title;

            this.description = description;

            this.unlocked = unlocked;
        }
    }


    /**
     * Generate badges from a Supabase profile.
     *
     * Expected profile fields include:
     *
     * completed_sessions
     * xp
     * streak
     * best_streak
     * reviews_given
     * rating_count
     * rating_average
     *
     * Some older PeerPath profiles may use:
     *
     * completedSessions
     * streakDays
     * bestStreak
     * reviewsGiven
     * ratingCount
     * ratingAverage
     *
     * Both formats are supported.
     */
    public static List<Badge> badgesFor(
            JSONObject profile
    ) {

        long completed =
                getProfileLong(
                        profile,
                        "completed_sessions",
                        "completedSessions"
                );

        long xp =
                getProfileLong(
                        profile,
                        "xp"
                );

        long streak =
                getProfileLong(
                        profile,
                        "streak",
                        "streak_days",
                        "streakDays"
                );

        long bestStreak =
                getProfileLong(
                        profile,
                        "best_streak",
                        "bestStreak"
                );

        long reviews =
                getProfileLong(
                        profile,
                        "reviews_given",
                        "reviewsGiven"
                );

        long ratingCount =
                getProfileLong(
                        profile,
                        "rating_count",
                        "ratingCount"
                );

        double rating =
                getProfileDouble(
                        profile,
                        "rating_average",
                        "ratingAverage"
                );


        long bestAvailableStreak =
                Math.max(
                        streak,
                        bestStreak
                );


        List<Badge> badges =
                new ArrayList<>();


        // --------------------------------------------------------
        // First Step
        // --------------------------------------------------------

        badges.add(
                new Badge(
                        "🎯",
                        "First Step",
                        "Complete your first session",
                        completed >= 1
                )
        );


        // --------------------------------------------------------
        // Regular
        // --------------------------------------------------------

        badges.add(
                new Badge(
                        "📚",
                        "Regular",
                        "Complete 5 sessions",
                        completed >= 5
                )
        );


        // --------------------------------------------------------
        // On Fire
        // --------------------------------------------------------

        badges.add(
                new Badge(
                        "🔥",
                        "On Fire",
                        "Reach a 3-day streak",
                        bestAvailableStreak >= 3
                )
        );


        // --------------------------------------------------------
        // Week Warrior
        // --------------------------------------------------------

        badges.add(
                new Badge(
                        "⚡",
                        "Week Warrior",
                        "Reach a 7-day streak",
                        bestAvailableStreak >= 7
                )
        );


        // --------------------------------------------------------
        // Reviewer
        // --------------------------------------------------------

        badges.add(
                new Badge(
                        "✍️",
                        "Reviewer",
                        "Rate a tutor after a session",
                        reviews >= 1
                )
        );


        // --------------------------------------------------------
        // Rising Star
        // --------------------------------------------------------

        badges.add(
                new Badge(
                        "🌟",
                        "Rising Star",
                        "Earn 250 XP",
                        xp >= 250
                )
        );


        // --------------------------------------------------------
        // Top Rated
        // --------------------------------------------------------

        badges.add(
                new Badge(
                        "🏆",
                        "Top Rated",
                        "Tutors: hold a 4.5+ rating from 3 reviews",
                        ratingCount >= 3 &&
                                rating >= 4.5
                )
        );


        return badges;
    }


    /**
     * Count unlocked badges.
     */
    public static int unlockedCount(
            JSONObject profile
    ) {

        int count = 0;

        for (Badge badge :
                badgesFor(profile)) {

            if (badge.unlocked) {
                count++;
            }
        }

        return count;
    }


    /**
     * Get total badge count.
     */
    public static int totalBadgeCount() {

        return 7;
    }


    /**
     * Get badge completion percentage.
     */
    public static int badgeProgressPercent(
            JSONObject profile
    ) {

        int total =
                totalBadgeCount();

        if (total == 0) {
            return 0;
        }

        return (
                unlockedCount(profile)
                        * 100
        ) / total;
    }


    // ============================================================
    // PROFILE GAMIFICATION SUMMARY
    // ============================================================

    /**
     * Build a convenient gamification summary from
     * a Supabase profile.
     */
    public static JSONObject summary(
            JSONObject profile
    ) {

        JSONObject result =
                new JSONObject();

        try {

            long xp =
                    getLong(
                            profile,
                            "xp"
                    );

            int level =
                    levelFor(xp);

            result.put(
                    "xp",
                    xp
            );

            result.put(
                    "level",
                    level
            );

            result.put(
                    "title",
                    titleFor(xp)
            );

            result.put(
                    "level_progress",
                    progressInLevel(xp)
            );

            result.put(
                    "level_progress_percent",
                    levelProgressPercent(xp)
            );

            result.put(
                    "xp_to_next_level",
                    xpToNextLevel(xp)
            );

            result.put(
                    "badges_unlocked",
                    unlockedCount(profile)
            );

            result.put(
                    "badges_total",
                    totalBadgeCount()
            );

            result.put(
                    "badge_progress_percent",
                    badgeProgressPercent(profile)
            );

        } catch (Exception ignored) {
        }

        return result;
    }


    // ============================================================
    // PROFILE FIELD COMPATIBILITY
    // ============================================================

    /**
     * Read a long from the first available field.
     *
     * This allows the app to work while older Firebase-style
     * field names are being migrated to Supabase snake_case.
     */
    private static long getProfileLong(
            JSONObject object,
            String... keys
    ) {

        if (object == null) {
            return 0L;
        }

        for (String key : keys) {

            if (object.has(key) &&
                    !object.isNull(key)) {

                return getLong(
                        object,
                        key
                );
            }
        }

        return 0L;
    }


    /**
     * Read a double from the first available field.
     */
    private static double getProfileDouble(
            JSONObject object,
            String... keys
    ) {

        if (object == null) {
            return 0.0;
        }

        for (String key : keys) {

            if (object.has(key) &&
                    !object.isNull(key)) {

                return getDouble(
                        object,
                        key
                );
            }
        }

        return 0.0;
    }
}