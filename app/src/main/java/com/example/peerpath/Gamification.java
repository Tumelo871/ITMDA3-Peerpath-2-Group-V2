package com.example.peerpath;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * PeerPath gamification calculations.
 * Profile values are read from Supabase; this class does not write XP.
 */
public final class Gamification {

    public static final int XP_DAILY_CHECKIN = 5;
    public static final int XP_SESSION_LEARNER = 25;
    public static final int XP_SESSION_TUTOR = 40;
    public static final int XP_REVIEW_GIVEN = 10;
    public static final int XP_FIVE_STAR_BONUS = 10;
    public static final int XP_PER_LEVEL = 100;

    private static final String[] TITLES = {
            "Newcomer", "Explorer", "Achiever", "Scholar",
            "Mentor", "Champion", "Legend"
    };

    private Gamification() { }

    public static int levelFor(long xp) {
        return (int) (Math.max(0L, xp) / XP_PER_LEVEL) + 1;
    }

    public static String titleFor(long xp) {
        int index = Math.min(levelFor(xp) - 1, TITLES.length - 1);
        return TITLES[index];
    }

    public static int progressInLevel(long xp) {
        return (int) (Math.max(0L, xp) % XP_PER_LEVEL);
    }

    public static long xpToNextLevel(long xp) {
        int progress = progressInLevel(xp);
        return progress == 0 ? XP_PER_LEVEL : XP_PER_LEVEL - progress;
    }

    public static int levelProgressPercent(long xp) {
        return (progressInLevel(xp) * 100) / XP_PER_LEVEL;
    }

    public static long getLong(JSONObject object, String key) {
        if (object == null || key == null) return 0L;
        try {
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) return 0L;
            if (value instanceof Number) return ((Number) value).longValue();
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static double getDouble(JSONObject object, String key) {
        if (object == null || key == null) return 0.0;
        try {
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) return 0.0;
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    public static String getString(JSONObject object, String key) {
        if (object == null || key == null) return "";
        String value = object.optString(key, "");
        return "null".equalsIgnoreCase(value) ? "" : value;
    }

    private static String dateKey(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new Date(calendar.getTimeInMillis()));
    }

    public static String todayKey() {
        return dateKey(Calendar.getInstance());
    }

    public static String yesterdayKey() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        return dateKey(calendar);
    }

    public static long calculateStreak(String lastActiveDate, long currentStreak) {
        if (todayKey().equals(lastActiveDate)) return Math.max(0L, currentStreak);
        if (yesterdayKey().equals(lastActiveDate)) return Math.max(0L, currentStreak) + 1L;
        return 1L;
    }

    public static boolean checkedInToday(String lastActiveDate) {
        return todayKey().equals(lastActiveDate);
    }

    public static long calculateBestStreak(long previousBest, long currentStreak) {
        return Math.max(Math.max(0L, previousBest), Math.max(0L, currentStreak));
    }

    public static int learnerSessionXp() { return XP_SESSION_LEARNER; }
    public static int tutorSessionXp() { return XP_SESSION_TUTOR; }
    public static int reviewXp() { return XP_REVIEW_GIVEN; }

    public static int reviewXp(double rating) {
        return XP_REVIEW_GIVEN + (rating >= 5.0 ? XP_FIVE_STAR_BONUS : 0);
    }

    public static long xpAfterSession(long currentXp, boolean tutor) {
        return Math.max(0L, currentXp) +
                (tutor ? XP_SESSION_TUTOR : XP_SESSION_LEARNER);
    }

    public static final class Badge {
        public final String emoji;
        public final String title;
        public final String description;
        public final boolean unlocked;
        public final int progress;
        public final int target;
        public final String progressText;

        public Badge(String emoji, String title, String description, boolean unlocked) {
            this(emoji, title, description, unlocked, unlocked ? 1 : 0, 1, "");
        }

        public Badge(String emoji, String title, String description,
                     boolean unlocked, long progress, long target, String progressText) {
            this.emoji = emoji;
            this.title = title;
            this.description = description;
            this.unlocked = unlocked;
            this.target = (int) Math.max(1L, target);
            this.progress = (int) Math.max(0L, Math.min(progress, this.target));
            this.progressText = progressText == null ? "" : progressText;
        }

        public int progressPercent() {
            return unlocked ? 100 : (progress * 100) / target;
        }
    }

    /**
     * The live profiles table uses sessions_completed. Older aliases remain
     * supported so existing accounts are not broken.
     */
    public static List<Badge> badgesFor(JSONObject profile) {
        long completed = getProfileLong(profile,
                "sessions_completed", "completed_sessions", "completedSessions");
        long xp = getProfileLong(profile, "xp");
        long streak = getProfileLong(profile, "streak", "streak_days", "streakDays");
        long bestStreak = getProfileLong(profile, "best_streak", "bestStreak");
        long reviews = getProfileLong(profile, "reviews_given", "reviewsGiven");
        long ratingCount = getProfileLong(profile, "rating_count", "ratingCount");
        double rating = getProfileDouble(profile, "rating_average", "ratingAverage");
        long bestAvailableStreak = Math.max(streak, bestStreak);
        boolean tutor = "tutor".equalsIgnoreCase(getString(profile, "role"));

        List<Badge> badges = new ArrayList<>();
        badges.add(new Badge("🎯", "First Step", "Complete your first session",
                completed >= 1, completed, 1, completed + " / 1 session"));
        badges.add(new Badge("📚", "Regular", "Complete 5 sessions",
                completed >= 5, completed, 5, Math.min(completed, 5) + " / 5 sessions"));
        badges.add(new Badge("🔥", "On Fire", "Reach a 3-day streak",
                bestAvailableStreak >= 3, bestAvailableStreak, 3,
                Math.min(bestAvailableStreak, 3) + " / 3 days"));
        badges.add(new Badge("⚡", "Week Warrior", "Reach a 7-day streak",
                bestAvailableStreak >= 7, bestAvailableStreak, 7,
                Math.min(bestAvailableStreak, 7) + " / 7 days"));
        badges.add(new Badge("✍️", "Reviewer", "Rate a tutor after a session",
                reviews >= 1, reviews, 1, reviews + " / 1 review"));
        badges.add(new Badge("🌟", "Rising Star", "Earn 250 XP",
                xp >= 250, xp, 250, Math.min(xp, 250) + " / 250 XP"));

        // Top Rated is a tutor achievement, not a learner achievement.
        if (tutor) {
            boolean topRated = ratingCount >= 3 && rating >= 4.5;
            String ratingText = String.format(Locale.getDefault(),
                    "%.1f average · %d / 3 reviews", rating, Math.min(ratingCount, 3));
            badges.add(new Badge("🏆", "Top Rated",
                    "Maintain a 4.5+ rating from at least 3 reviews",
                    topRated, ratingCount, 3, ratingText));
        }
        return badges;
    }

    public static int unlockedCount(JSONObject profile) {
        int count = 0;
        for (Badge badge : badgesFor(profile)) if (badge.unlocked) count++;
        return count;
    }

    public static int totalBadgeCount() { return 7; }

    public static int totalBadgeCount(JSONObject profile) {
        return badgesFor(profile).size();
    }

    public static int badgeProgressPercent(JSONObject profile) {
        int total = totalBadgeCount(profile);
        return total == 0 ? 0 : (unlockedCount(profile) * 100) / total;
    }

    public static JSONObject summary(JSONObject profile) {
        JSONObject result = new JSONObject();
        try {
            long xp = getLong(profile, "xp");
            result.put("xp", xp);
            result.put("level", levelFor(xp));
            result.put("title", titleFor(xp));
            result.put("level_progress", progressInLevel(xp));
            result.put("level_progress_percent", levelProgressPercent(xp));
            result.put("xp_to_next_level", xpToNextLevel(xp));
            result.put("badges_unlocked", unlockedCount(profile));
            result.put("badges_total", totalBadgeCount(profile));
            result.put("badge_progress_percent", badgeProgressPercent(profile));
        } catch (Exception ignored) { }
        return result;
    }

    private static long getProfileLong(JSONObject object, String... keys) {
        if (object == null) return 0L;
        for (String key : keys) {
            if (object.has(key) && !object.isNull(key)) return getLong(object, key);
        }
        return 0L;
    }

    private static double getProfileDouble(JSONObject object, String... keys) {
        if (object == null) return 0.0;
        for (String key : keys) {
            if (object.has(key) && !object.isNull(key)) return getDouble(object, key);
        }
        return 0.0;
    }
}
