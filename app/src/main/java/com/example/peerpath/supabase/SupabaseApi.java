package com.example.peerpath.supabase;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.example.peerpath.SupabaseConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Supabase REST/Auth helper for PeerPath.
 *
 * Uses the public publishable key and the signed-in user's access token.
 * Never put a service-role/secret key in an Android application.
 */
public final class SupabaseApi {
    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static volatile SharedPreferences prefs;

    private SupabaseApi() {}

    public interface JsonCallback {
        void onSuccess(JSONArray data);
        void onError(String message);
    }

    public interface ObjectCallback {
        void onSuccess(JSONObject data);
        void onError(String message);
    }

    public interface EmptyCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface StringCallback {
        void onSuccess(String value);
        void onError(String message);
    }

    public static void init(Context context) {
        if (context != null) {
            prefs = context.getApplicationContext().getSharedPreferences(
                    "supabase_session", Context.MODE_PRIVATE);
        }
    }


    public static boolean isConfigured() {
        String url = SupabaseConfig.URL;
        String key = SupabaseConfig.PUBLISHABLE_KEY;
        if (blank(url) || blank(key)) return false;

        url = url.trim();
        key = key.trim();
        android.net.Uri parsed = android.net.Uri.parse(url);
        boolean validUrl = "https".equalsIgnoreCase(parsed.getScheme())
                && parsed.getHost() != null
                && !parsed.getHost().trim().isEmpty()
                && parsed.getUserInfo() == null;
        boolean placeholder = key.contains("PASTE_YOUR")
                || key.contains("YOUR_SUPABASE")
                || key.equals("[REDACTED]")
                || key.toLowerCase(java.util.Locale.ROOT).contains("placeholder");
        // Supabase publishable keys use sb_publishable_. Legacy anon JWT keys begin eyJ.
        boolean plausibleKey = key.startsWith("sb_publishable_") || key.startsWith("eyJ");
        return validUrl && plausibleKey && !placeholder;
    }


    public static String token() {
        SharedPreferences p = prefs;
        return p == null ? null : p.getString("access_token", null);
    }

    public static String userId() {
        SharedPreferences p = prefs;
        return p == null ? null : p.getString("user_id", null);
    }

    public static String userEmail() {
        SharedPreferences p = prefs;
        return p == null ? null : p.getString("email", null);
    }

    public static boolean isLoggedIn() {
        String t = token(), id = userId();
        return t != null && !t.trim().isEmpty()
                && id != null && !id.trim().isEmpty();
    }

    public static void clearSession() {
        SharedPreferences p = prefs;
        if (p != null) p.edit().clear().apply();
    }

    // -------------------- Authentication --------------------

    public static void signUp(String email, String password,
                              JSONObject metadata, ObjectCallback callback) {
        if (callback == null) return;
        String problem = configurationError();
        if (problem != null) { callback.onError(problem); return; }
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Email is required."); return;
        }
        if (password == null || password.length() < 6) {
            callback.onError("Password must contain at least 6 characters."); return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("email", email.trim());
            body.put("password", password);
            body.put("data", metadata == null ? new JSONObject() : metadata);
        } catch (JSONException e) {
            callback.onError(e.getMessage()); return;
        }

        Request request = baseRequest(SupabaseConfig.URL.trim() + "/auth/v1/signup")
                .post(RequestBody.create(body.toString(), JSON)).build();
        executeObject(request, callback);
    }

    public static void signIn(String email, String password, ObjectCallback callback) {
        if (callback == null) return;
        String problem = configurationError();
        if (problem != null) { callback.onError(problem); return; }
        if (email == null || email.trim().isEmpty()
                || password == null || password.isEmpty()) {
            callback.onError("Enter your email and password."); return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("email", email.trim());
            body.put("password", password);
        } catch (JSONException e) {
            callback.onError(e.getMessage()); return;
        }

        Request request = baseRequest(SupabaseConfig.URL.trim()
                + "/auth/v1/token?grant_type=password")
                .post(RequestBody.create(body.toString(), JSON)).build();

        executeObject(request, new ObjectCallback() {
            @Override public void onSuccess(JSONObject data) {
                JSONObject user = data.optJSONObject("user");
                String access = data.optString("access_token", "");
                String refresh = data.optString("refresh_token", "");
                if (user == null || access.trim().isEmpty() || refresh.trim().isEmpty()
                        || user.optString("id", "").trim().isEmpty()) {
                    callback.onError("Supabase did not return a complete login session.");
                    return;
                }
                SharedPreferences p = prefs;
                if (p == null) {
                    callback.onError("Session storage is not initialized. Please restart the app.");
                    return;
                }
                p.edit()
                        .putString("access_token", access)
                        .putString("refresh_token", refresh)
                        .putString("user_id", user.optString("id"))
                        .putString("email", user.optString("email", email.trim()))
                        .apply();
                callback.onSuccess(data);
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public static void resetPassword(String email, EmptyCallback callback) {
        if (callback == null) return;
        String problem = configurationError();
        if (problem != null) { callback.onError(problem); return; }
        if (email == null || email.trim().isEmpty()) {
            callback.onError("Email is required."); return;
        }
        JSONObject body = new JSONObject();
        try { body.put("email", email.trim()); }
        catch (JSONException e) { callback.onError(e.getMessage()); return; }

        Request request = baseRequest(SupabaseConfig.URL.trim() + "/auth/v1/recover")
                .post(RequestBody.create(body.toString(), JSON)).build();
        executeEmpty(request, callback);
    }

    // -------------------- Profiles and tutors --------------------

    public static void getProfile(String id, ObjectCallback callback) {
        if (callback == null) return;
        if (blank(id)) { callback.onError("User ID is missing."); return; }
        dbGet("profiles", "id=eq." + encode(id) + "&select=*", new JsonCallback() {
            @Override public void onSuccess(JSONArray data) {
                JSONObject profile = data == null ? null : data.optJSONObject(0);
                if (profile == null) callback.onError("Profile not found.");
                else callback.onSuccess(profile);
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    /**
     * Loads a tutor profile by ID and confirms that it is a verified tutor.
     * The returned profile is real data from the profiles table.
     */
    public static void getTutorProfile(String tutorId, ObjectCallback callback) {
        if (callback == null) return;
        if (blank(tutorId)) {
            callback.onError("Tutor ID is missing.");
            return;
        }
        getProfile(tutorId, new ObjectCallback() {
            @Override public void onSuccess(JSONObject profile) {
                String role = profile.optString("role", "");
                String verification = profile.optString("verification_status", "");
                if (!"tutor".equalsIgnoreCase(role)
                        || !"verified".equalsIgnoreCase(verification)) {
                    callback.onError("This profile is not a verified tutor.");
                    return;
                }
                callback.onSuccess(profile);
            }
            @Override public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public static void updateProfile(JSONObject values, EmptyCallback callback) {
        if (callback == null) return;
        if (values == null || values.length() == 0) {
            callback.onError("Profile data is missing.");
            return;
        }
        String id = userId();
        if (blank(id)) {
            callback.onError("You are not logged in.");
            return;
        }
        dbPatch("profiles", "id=eq." + encode(id), values, callback);
    }

    public static void getVerifiedTutors(@Nullable String search, JsonCallback callback) {
        String query = "role=eq.tutor&verification_status=eq.verified&select=*&order=full_name.asc";
        if (!blank(search)) {
            String value = encodeLike(search.trim());
            query = "role=eq.tutor&verification_status=eq.verified"
                    + "&or=(full_name.ilike.*" + value + "*,module_codes.ilike.*"
                    + value + "*,tutor_subject.ilike.*" + value + "*)"
                    + "&select=*&order=full_name.asc";
        }
        dbGet("profiles", query, callback);
    }

    // -------------------- Sessions --------------------

    public static void createSession(String tutorId, String subject, String date,
                                     String time, String message, EmptyCallback callback) {
        if (blank(userId())) { callback.onError("Please sign in before requesting a session."); return; }
        if (blank(tutorId) || blank(subject) || blank(date) || blank(time)) {
            callback.onError("Tutor, subject, date and time are required."); return;
        }
        if (tutorId.equals(userId())) {
            callback.onError("You cannot book a session with your own profile."); return;
        }
        JSONObject row = new JSONObject();
        try {
            row.put("learner_id", userId());
            row.put("tutor_id", tutorId);
            row.put("subject", subject.trim());
            row.put("session_date", date.trim());
            row.put("session_time", time.trim());
            row.put("learner_message", message == null ? "" : message.trim());
            row.put("status", "pending");
        } catch (JSONException e) { callback.onError(e.getMessage()); return; }
        dbPost("sessions", row, callback);
    }

    /**
     * Requests a session with a verified tutor. This rechecks the tutor profile
     * before inserting the pending booking request.
     */
    public static void requestTutorSession(String tutorId, String subject,
                                           String date, String time, String message,
                                           EmptyCallback callback) {
        if (callback == null) return;
        getTutorProfile(tutorId, new ObjectCallback() {
            @Override public void onSuccess(JSONObject profile) {
                String verifiedSubject = subject;
                if (blank(verifiedSubject)) {
                    verifiedSubject = profile.optString("tutor_subject", "");
                    if (blank(verifiedSubject)) {
                        verifiedSubject = profile.optString("module_codes", "");
                    }
                }
                if (blank(verifiedSubject)) {
                    callback.onError("Please choose or enter a subject for this session.");
                    return;
                }
                createSession(tutorId, verifiedSubject, date, time, message, callback);
            }
            @Override public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /** Loads reviews for a tutor. Requires reviews.tutor_id and the fields shown below. */
    public static void getTutorReviews(String tutorId, JsonCallback callback) {
        if (callback == null) return;
        if (blank(tutorId)) { callback.onError("Tutor ID is missing."); return; }
        dbGet("reviews", "tutor_id=eq." + encode(tutorId)
                + "&select=rating,comment,is_anonymous,created_at,student_id"
                + "&order=created_at.desc", new JsonCallback() {
            @Override public void onSuccess(JSONArray rows) {
                JSONArray safeRows = new JSONArray();
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject source = rows.optJSONObject(i);
                        if (source == null) continue;
                        JSONObject item = new JSONObject();
                        try {
                            item.put("rating", source.optDouble("rating", 0));
                            item.put("comment", source.optString("comment", ""));
                            item.put("created_at", source.optString("created_at", ""));
                            boolean anonymous = source.optBoolean("is_anonymous", false);
                            item.put("reviewer_name", anonymous ? "Anonymous learner" : "Learner");
                            safeRows.put(item);
                        } catch (JSONException ignored) { }
                    }
                }
                callback.onSuccess(safeRows);
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    /** Resolves the learner attached to a tutoring session. */
    public static void getTutorRequesterName(String sessionId, JsonCallback callback) {
        if (callback == null) return;
        if (blank(sessionId)) { callback.onError("Session ID is missing."); return; }
        dbGet("sessions", "id=eq." + encode(sessionId) + "&select=learner_id", new JsonCallback() {
            @Override public void onSuccess(JSONArray sessions) {
                JSONObject session = sessions == null ? null : sessions.optJSONObject(0);
                String learnerId = session == null ? "" : session.optString("learner_id", "");
                if (blank(learnerId) || "null".equalsIgnoreCase(learnerId)) {
                    callback.onError("The learner for this session could not be found.");
                    return;
                }
                dbGet("profiles", "id=eq." + encode(learnerId) + "&select=full_name", callback);
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    /** Submits a review for the signed-in learner's session. */
    public static void submitReview(String sessionId, float rating, String comment,
                                    boolean anonymous, JsonCallback callback) {
        if (callback == null) return;
        String learnerId = userId();
        if (blank(learnerId) || blank(token())) { callback.onError("Please sign in again."); return; }
        if (blank(sessionId)) { callback.onError("Session ID is missing."); return; }
        if (rating < 1f || rating > 5f) { callback.onError("Rating must be between 1 and 5."); return; }
        dbGet("sessions", "id=eq." + encode(sessionId) + "&select=id,learner_id,tutor_id,status", new JsonCallback() {
            @Override public void onSuccess(JSONArray sessions) {
                JSONObject session = sessions == null ? null : sessions.optJSONObject(0);
                if (session == null) { callback.onError("Session not found or access denied."); return; }
                String sessionLearner = session.optString("learner_id", "");
                String tutorId = session.optString("tutor_id", "");
                if (!learnerId.equals(sessionLearner)) { callback.onError("Only the learner who attended this session can review it."); return; }
                if (!"completed".equalsIgnoreCase(session.optString("status", ""))) {
                    callback.onError("You can review a session after it is completed."); return;
                }
                JSONObject row = new JSONObject();
                try {
                    row.put("session_id", sessionId);
                    row.put("student_id", learnerId);
                    row.put("tutor_id", tutorId);
                    row.put("rating", rating);
                    row.put("comment", comment == null ? "" : comment.trim());
                    row.put("is_anonymous", anonymous);
                } catch (JSONException e) { callback.onError("Could not prepare the review."); return; }
                dbPost("reviews", row, new EmptyCallback() {
                    @Override public void onSuccess() { callback.onSuccess(new JSONArray()); }
                    @Override public void onError(String message) { callback.onError(message); }
                });
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public static void getMySessions(JsonCallback callback) {
        String id = userId();
        if (blank(id)) { callback.onError("You are not logged in."); return; }
        dbGet("sessions", "or=(learner_id.eq." + encode(id) + ",tutor_id.eq." + encode(id)
                + ")&select=*&order=created_at.desc", callback);
    }


    /**
     * Returns session IDs for reviews written by the signed-in learner.
     * Assumes public.reviews uses student_id and session_id columns.
     * Supabase RLS remains enabled; this uses the normal authenticated REST request.
     */
    public static void getMyReviews(JsonCallback callback) {
        if (callback == null) return;

        String id = userId();
        if (blank(id) || blank(token())) {
            callback.onError("Please sign in to view your reviews.");
            return;
        }

        dbGet("reviews",
                "student_id=eq." + encode(id) + "&select=session_id",
                callback);
    }

    public static void getAcceptedSessions(JsonCallback callback) {
        String id = userId();
        if (blank(id)) { callback.onError("You are not logged in."); return; }
        dbGet("sessions", "status=eq.accepted&or=(learner_id.eq." + encode(id)
                + ",tutor_id.eq." + encode(id) + ")&select=*&order=created_at.desc", callback);
    }

    public static void getTutorAcceptedSessions(JsonCallback callback) {
        String id = userId();
        if (blank(id)) { callback.onError("Please sign in to view tutor sessions."); return; }
        dbGet("sessions", "tutor_id=eq." + encode(id)
                + "&status=eq.accepted&select=*&order=session_date.asc,session_time.asc", callback);
    }

    public static void getPendingTutorRequests(JsonCallback callback) {
        String id = userId();
        if (blank(id)) { callback.onError("You are not logged in."); return; }
        dbGet("sessions", "tutor_id=eq." + encode(id)
                + "&status=eq.pending&select=*&order=created_at.desc", callback);
    }

    public static void updateSessionStatus(String sessionId, String status, EmptyCallback callback) {
        if (blank(sessionId)) { callback.onError("Session ID is missing."); return; }
        if (!("pending".equals(status) || "accepted".equals(status)
                || "rejected".equals(status) || "completed".equals(status)
                || "cancelled".equals(status))) {
            callback.onError("Invalid session status."); return;
        }
        JSONObject values = new JSONObject();
        try { values.put("status", status); }
        catch (JSONException e) { callback.onError(e.getMessage()); return; }
        dbPatch("sessions", "id=eq." + encode(sessionId), values, callback);
    }

    // -------------------- Messages --------------------

    public static void getMessages(String sessionId, JsonCallback callback) {
        if (blank(sessionId)) { callback.onError("Session ID is missing."); return; }
        dbGet("messages", "session_id=eq." + encode(sessionId)
                + "&select=*&order=created_at.asc", callback);
    }

    public static void sendMessage(String sessionId, String receiverId,
                                   String message, EmptyCallback callback) {
        if (callback == null) return;
        String senderId = userId();
        if (blank(senderId) || blank(token())) {
            callback.onError("You are not logged in. Please sign in again.");
            return;
        }
        if (blank(sessionId) || blank(receiverId) || blank(message)) {
            callback.onError("Session, recipient and message are required.");
            return;
        }
        String cleanMessage = message.trim();
        if (cleanMessage.isEmpty()) {
            callback.onError("Enter a message before sending.");
            return;
        }
        if (cleanMessage.length() > 4000) {
            callback.onError("Messages must be 4,000 characters or fewer.");
            return;
        }
        if (senderId.equals(receiverId)) {
            callback.onError("You cannot send a message to yourself.");
            return;
        }

        JSONObject row = new JSONObject();
        try {
            row.put("session_id", sessionId.trim());
            row.put("sender_id", senderId);
            row.put("receiver_id", receiverId.trim());
            row.put("body", cleanMessage);
        } catch (JSONException e) {
            callback.onError("Could not prepare the message.");
            return;
        }

        // The database must still enforce session participation with RLS.
        // Never bypass RLS or use a service-role key in the Android app.
        dbPost("messages", row, callback);
    }

    // -------------------- Notifications --------------------

    public static void getNotifications(JsonCallback callback) {
        String id = userId();
        if (blank(id)) { callback.onError("You are not logged in."); return; }
        dbGet("notifications", "user_id=eq." + encode(id)
                + "&select=*&order=created_at.desc", callback);
    }

    public static void markNotificationRead(String notificationId, EmptyCallback callback) {
        if (blank(notificationId)) { callback.onError("Notification ID is missing."); return; }
        JSONObject values = new JSONObject();
        try { values.put("is_read", true); }
        catch (JSONException e) { callback.onError(e.getMessage()); return; }
        dbPatch("notifications", "id=eq." + encode(notificationId), values, callback);
    }

    // -------------------- Tutor applications --------------------

    public static void submitTutorApplication(String moduleCodes, String evidencePath,
                                              EmptyCallback callback) {
        String id = userId();
        if (blank(id)) { callback.onError("You are not logged in."); return; }
        if (blank(moduleCodes)) { callback.onError("Enter the modules you tutor."); return; }
        JSONObject row = new JSONObject();
        try {
            row.put("tutor_id", id);
            row.put("module_codes", moduleCodes.trim());
            row.put("evidence_path", evidencePath == null ? "" : evidencePath);
            row.put("status", "pending");
        } catch (JSONException e) { callback.onError(e.getMessage()); return; }
        dbPost("tutor_applications", row, callback);
    }

    /** Lists all tutor applications (pending and already reviewed) via an admin-only RPC. */
    public static void getTutorApplications(JsonCallback callback) {
        if (callback == null) return;
        if (blank(userId()) || blank(token())) {
            callback.onError("Please sign in first.");
            return;
        }
        if (!isConfigured()) {
            callback.onError("Supabase is not configured.");
            return;
        }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_admin_list_tutor_applications")
                    .post(RequestBody.create("{}", JSON))
                    .build();
        } catch (Exception e) {
            callback.onError(messageOf(e));
            return;
        }
        executeArray(request, callback);
    }

    /** Backwards-compatible name for screens that still call the pending method. */
    public static void getPendingApplications(JsonCallback callback) {
        getTutorApplications(callback);
    }

    public static void reviewApplication(String applicationId, String tutorId,
                                         boolean approve, String note, EmptyCallback callback) {
        adminReviewApplication(applicationId, tutorId, approve, note, callback);
    }

    /**
     * Lists profiles through an admin-only database function.
     * The database function must independently verify the current user is an admin.
     */
    public static void adminListUsers(JsonCallback callback) {
        if (callback == null) return;
        if (blank(userId()) || blank(token())) {
            callback.onError("Please sign in first.");
            return;
        }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_admin_list_users")
                    .post(RequestBody.create("{}", JSON))
                    .build();
        } catch (Exception e) {
            callback.onError(messageOf(e));
            return;
        }
        executeArray(request, callback);
    }



    /**
     * Loads negative reviews through a protected admin-only Supabase RPC.
     * Expected RPC: peerpath_admin_list_negative_reviews.
     */
    public static void getAdminNegativeReviews(JsonCallback callback) {
        if (callback == null) return;

        if (!isLoggedIn()) {
            callback.onError("Please sign in first.");
            return;
        }

        if (!isConfigured()) {
            callback.onError("Supabase is not configured.");
            return;
        }

        Request request = authedRequest(
                SupabaseConfig.URL.trim()
                        + "/rest/v1/rpc/peerpath_admin_list_negative_reviews")
                .post(RequestBody.create("{}", JSON))
                .build();

        executeArray(request, callback);
    }

    /**
     * Updates a user through the protected admin RPC.
     * Requires peerpath_admin_update_user in Supabase.
     */
    public static void adminUpdateUser(
            String targetUserId,
            String fullName,
            String studentNumber,
            String faculty,
            String role,
            String verificationStatus,
            EmptyCallback callback) {

        if (callback == null) return;

        if (!isLoggedIn()) {
            callback.onError("Please sign in first.");
            return;
        }

        if (blank(targetUserId) || blank(fullName)
                || blank(role) || blank(verificationStatus)) {
            callback.onError("Complete all required user fields.");
            return;
        }

        if (!isConfigured()) {
            callback.onError("Supabase is not configured.");
            return;
        }

        JSONObject body = new JSONObject();

        try {
            body.put("p_user_id", targetUserId.trim());
            body.put("p_full_name", fullName.trim());
            body.put("p_student_number",
                    studentNumber == null ? "" : studentNumber.trim());
            body.put("p_faculty",
                    faculty == null ? "" : faculty.trim());
            body.put("p_role", role.trim().toLowerCase(
                    java.util.Locale.ROOT));
            body.put("p_verification_status",
                    verificationStatus.trim());
        } catch (JSONException e) {
            callback.onError("Could not prepare the user update.");
            return;
        }

        Request request = authedRequest(
                SupabaseConfig.URL.trim()
                        + "/rest/v1/rpc/peerpath_admin_update_user")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        executeEmpty(request, callback);
    }

    /**
     * Deletes a profile through the protected admin RPC.
     * This does not delete the Supabase Auth account.
     */
    public static void adminDeleteUser(
            String targetUserId,
            EmptyCallback callback) {

        if (callback == null) return;

        if (!isLoggedIn()) {
            callback.onError("Please sign in first.");
            return;
        }

        if (blank(targetUserId)) {
            callback.onError("User ID is required.");
            return;
        }

        if (targetUserId.trim().equals(userId())) {
            callback.onError("You cannot delete your own account.");
            return;
        }

        if (!isConfigured()) {
            callback.onError("Supabase is not configured.");
            return;
        }

        JSONObject body = new JSONObject();

        try {
            body.put("p_user_id", targetUserId.trim());
        } catch (JSONException e) {
            callback.onError("Could not prepare the delete request.");
            return;
        }

        Request request = authedRequest(
                SupabaseConfig.URL.trim()
                        + "/rest/v1/rpc/peerpath_admin_delete_user")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        executeEmpty(request, callback);
    }



    /**
     * Reviews an application via a single database transaction.
     * Requires public.peerpath_admin_review_tutor_application(uuid, uuid, boolean, text).
     */
    public static void adminReviewApplication(String applicationId, String tutorId,
                                              boolean approve, String note,
                                              EmptyCallback callback) {
        if (callback == null) return;
        if (blank(applicationId) || blank(tutorId)) {
            callback.onError("Application ID and tutor ID are required.");
            return;
        }
        if (blank(userId()) || blank(token())) {
            callback.onError("Please sign in first.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("p_application_id", applicationId);
            body.put("p_tutor_id", tutorId);
            body.put("p_approve", approve);
            body.put("p_note", note == null ? "" : note);
        } catch (JSONException e) {
            callback.onError(e.getMessage());
            return;
        }

        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_admin_review_tutor_application")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
        } catch (Exception e) {
            callback.onError(messageOf(e));
            return;
        }
        executeEmpty(request, callback);
    }

    /**
     * Temporarily suspends/restores a non-admin profile through a protected RPC.
     * The database policies/functions must enforce the suspension across the app.
     */
    public static void adminSetSuspended(String targetUserId, boolean suspended,
                                         EmptyCallback callback) {
        if (callback == null) return;
        if (blank(targetUserId)) {
            callback.onError("User ID is required.");
            return;
        }
        if (blank(userId()) || blank(token())) {
            callback.onError("Please sign in first.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("p_user_id", targetUserId);
            body.put("p_suspended", suspended);
        } catch (JSONException e) {
            callback.onError(e.getMessage());
            return;
        }

        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_admin_set_suspended")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
        } catch (Exception e) {
            callback.onError(messageOf(e));
            return;
        }
        executeEmpty(request, callback);
    }

    /** Creates a short-lived signed URL for an evidence object in the private bucket. */
    public static void createTutorEvidenceSignedUrl(String evidencePath, StringCallback callback) {
        if (callback == null) return;
        if (!isLoggedIn()) { callback.onError("Please sign in first."); return; }
        if (blank(evidencePath)) { callback.onError("No evidence file was attached to this application."); return; }
        if (!isConfigured()) { callback.onError("Supabase is not configured."); return; }

        String path = evidencePath.trim();
        // If evidence_path contains a Supabase Storage URL, extract the object path.
        String marker = "/object/";
        int markerAt = path.indexOf(marker);
        if (path.startsWith("http") && markerAt >= 0) {
            String after = path.substring(markerAt + marker.length());
            int slash = after.indexOf('/');
            if (slash >= 0) {
                String bucket = after.substring(0, slash);
                if (!"tutor-evidence".equals(bucket)) {
                    callback.onError("Evidence is not in the tutor-evidence bucket: " + bucket);
                    return;
                }
                path = after.substring(slash + 1);
                int query = path.indexOf('?');
                if (query >= 0) path = path.substring(0, query);
            }
        } else if (path.startsWith("tutor-evidence/")) {
            path = path.substring("tutor-evidence/".length());
        }
        if (blank(path)) { callback.onError("The evidence storage path is invalid."); return; }

        JSONObject body = new JSONObject();
        try { body.put("expiresIn", 300); }
        catch (JSONException e) { callback.onError(e.getMessage()); return; }
        String url = SupabaseConfig.URL.trim() + "/storage/v1/object/sign/tutor-evidence/" + encodePath(path);
        Request request = authedRequest(url)
                .post(RequestBody.create(body.toString(), JSON)).build();
        executeObject(request, new ObjectCallback() {
            @Override public void onSuccess(JSONObject data) {
                String signed = data.optString("signedURL", data.optString("signedUrl", ""));
                if (blank(signed)) { callback.onError("Supabase did not return a signed evidence URL."); return; }
                if (signed.startsWith("http://") || signed.startsWith("https://")) {
                    callback.onSuccess(signed);
                } else {
                    String normalized = signed.startsWith("/") ? signed : "/" + signed;
                    callback.onSuccess(SupabaseConfig.URL.trim() + normalized);
                }
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    // -------------------- Resources --------------------

    public static void getResources(JsonCallback callback) {
        dbGet("resources", "select=*&order=created_at.desc", callback);
    }

    public static void createResource(String title, String subject, String link,
                                      String description, EmptyCallback callback) {
        String id = userId();
        if (blank(id)) { callback.onError("You are not logged in."); return; }
        if (blank(title)) { callback.onError("Resource title is required."); return; }
        JSONObject row = new JSONObject();
        try {
            row.put("owner_id", id);
            row.put("title", title.trim());
            row.put("subject", subject == null ? "" : subject.trim());
            row.put("url", link == null ? "" : link.trim());
            row.put("description", description == null ? "" : description.trim());
        } catch (JSONException e) { callback.onError(e.getMessage()); return; }
        dbPost("resources", row, callback);
    }

    public static void deleteResource(String resourceId, EmptyCallback callback) {
        if (blank(resourceId)) { callback.onError("Resource ID is missing."); return; }
        dbDelete("resources", "id=eq." + encode(resourceId), callback);
    }

    // -------------------- Storage --------------------

    public static void uploadFile(ContentResolver resolver, Uri uri, String path,
                                  EmptyCallback callback) {
        if (!isLoggedIn()) { callback.onError("You are not logged in."); return; }
        if (!isConfigured()) { callback.onError("Supabase is not configured."); return; }
        if (resolver == null || uri == null || blank(path)) {
            callback.onError("Choose a file and provide a storage path."); return;
        }
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) { callback.onError("Could not open selected file."); return; }
            byte[] bytes = readAll(input);
            String mime = resolver.getType(uri);
            if (blank(mime)) mime = "application/octet-stream";
            String safePath = path.replace("\\", "/");
            String url = SupabaseConfig.URL.trim() + "/storage/v1/object/peerpath-files/"
                    + encodePath(safePath);
            Request request = authedRequest(url)
                    .post(RequestBody.create(bytes, MediaType.parse(mime)))
                    .addHeader("Content-Type", mime)
                    .build();
            executeEmpty(request, callback);
        } catch (Exception e) {
            callback.onError(messageOf(e));
        }
    }

    // -------------------- Leaderboard RPC --------------------

    public static void getLeaderboard(JsonCallback callback) {
        if (callback == null) return;
        if (!isLoggedIn()) { callback.onError("Please sign in to view the leaderboard."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_leaderboard")
                    .post(RequestBody.create("{}", JSON)).build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeArray(request, callback);
    }

    // -------------------- HTTP helpers --------------------

    private static String configurationError() {
        if (!isConfigured()) {
            return "Supabase configuration is missing or invalid. Check the project URL and publishable key.";
        }
        return null;
    }

    private static Request.Builder baseRequest(String url) {
        return new Request.Builder().url(url)
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY.trim())
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json");
    }

    private static Request.Builder authedRequest(String url) {
        return baseRequest(url).addHeader("Authorization", "Bearer " + token());
    }

    private static void dbGet(String table, String query, JsonCallback callback) {
        if (callback == null) return;
        String problem = configurationError();
        if (problem != null) { callback.onError(problem); return; }
        if (!isLoggedIn()) { callback.onError("You are not logged in."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim() + "/rest/v1/"
                    + table + "?" + query).get().build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeArray(request, callback);
    }

    private static void dbPost(String table, JSONObject row, EmptyCallback callback) {
        if (callback == null) return;
        String problem = configurationError();
        if (problem != null) { callback.onError(problem); return; }
        if (!isLoggedIn()) { callback.onError("You are not logged in."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim() + "/rest/v1/" + table)
                    .post(RequestBody.create(row.toString(), JSON))
                    .addHeader("Prefer", "return=minimal").build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeEmpty(request, callback);
    }

    private static void dbPatch(String table, String query, JSONObject values,
                                EmptyCallback callback) {
        if (callback == null) return;
        String problem = configurationError();
        if (problem != null) { callback.onError(problem); return; }
        if (!isLoggedIn()) { callback.onError("You are not logged in."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim() + "/rest/v1/"
                    + table + "?" + query)
                    .patch(RequestBody.create(values.toString(), JSON))
                    .addHeader("Prefer", "return=minimal").build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeEmpty(request, callback);
    }

    private static void dbDelete(String table, String query, EmptyCallback callback) {
        if (callback == null) return;
        String problem = configurationError();
        if (problem != null) { callback.onError(problem); return; }
        if (!isLoggedIn()) { callback.onError("You are not logged in."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim() + "/rest/v1/"
                    + table + "?" + query)
                    .delete().addHeader("Prefer", "return=minimal").build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeEmpty(request, callback);
    }

    private static void executeObject(Request request, ObjectCallback callback) {
        executeObject(request, callback, false);
    }

    private static void executeObject(Request request, ObjectCallback callback,
                                      boolean alreadyRetried) {
        HTTP.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError(messageOf(e));
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String responseText;
                int status;
                try (Response r = response) {
                    status = r.code();
                    responseText = r.body() == null ? "" : r.body().string();
                }
                if (status == 401 && !alreadyRetried
                        && request.header("Authorization") != null) {
                    refreshSession(new ObjectCallback() {
                        @Override public void onSuccess(JSONObject ignored) {
                            executeObject(withCurrentToken(request), callback, true);
                        }
                        @Override public void onError(String message) {
                            callback.onError("Your session expired. Please sign in again. " + message);
                        }
                    });
                    return;
                }
                if (status < 200 || status >= 300) {
                    callback.onError(parseError(responseText, status));
                    return;
                }
                try {
                    callback.onSuccess(responseText.trim().isEmpty()
                            ? new JSONObject() : new JSONObject(responseText));
                } catch (JSONException e) {
                    callback.onError("Supabase returned an unreadable response.");
                }
            }
        });
    }

    private static void executeArray(Request request, JsonCallback callback) {
        executeArray(request, callback, false);
    }

    private static void executeArray(Request request, JsonCallback callback,
                                     boolean alreadyRetried) {
        HTTP.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError(messageOf(e));
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String responseText;
                int status;
                try (Response r = response) {
                    status = r.code();
                    responseText = r.body() == null ? "[]" : r.body().string();
                }
                if (status == 401 && !alreadyRetried
                        && request.header("Authorization") != null) {
                    refreshSession(new ObjectCallback() {
                        @Override public void onSuccess(JSONObject ignored) {
                            executeArray(withCurrentToken(request), callback, true);
                        }
                        @Override public void onError(String message) {
                            callback.onError("Your session expired. Please sign in again. " + message);
                        }
                    });
                    return;
                }
                if (status < 200 || status >= 300) {
                    callback.onError(parseError(responseText, status));
                    return;
                }
                try {
                    callback.onSuccess(new JSONArray(responseText));
                } catch (JSONException e) {
                    callback.onError("Supabase returned an unreadable list response.");
                }
            }
        });
    }

    private static void executeEmpty(Request request, EmptyCallback callback) {
        executeEmpty(request, callback, false);
    }

    private static void executeEmpty(Request request, EmptyCallback callback,
                                     boolean alreadyRetried) {
        HTTP.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError(messageOf(e));
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                String responseText;
                int status;
                try (Response r = response) {
                    status = r.code();
                    responseText = r.body() == null ? "" : r.body().string();
                }
                if (status == 401 && !alreadyRetried
                        && request.header("Authorization") != null) {
                    refreshSession(new ObjectCallback() {
                        @Override public void onSuccess(JSONObject ignored) {
                            executeEmpty(withCurrentToken(request), callback, true);
                        }
                        @Override public void onError(String message) {
                            callback.onError("Your session expired. Please sign in again. " + message);
                        }
                    });
                    return;
                }
                if (status < 200 || status >= 300) {
                    callback.onError(parseError(responseText, status));
                } else {
                    callback.onSuccess();
                }
            }
        });
    }

    /** Refreshes the Supabase session using the saved rotating refresh token. */
    private static void refreshSession(ObjectCallback callback) {
        SharedPreferences p = prefs;
        String refresh = p == null ? null : p.getString("refresh_token", null);
        if (blank(refresh)) {
            callback.onError("No refresh token is saved.");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("refresh_token", refresh);
        } catch (JSONException e) {
            callback.onError("Could not prepare session refresh.");
            return;
        }
        Request request = baseRequest(SupabaseConfig.URL.trim()
                + "/auth/v1/token?grant_type=refresh_token")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        executeObject(request, new ObjectCallback() {
            @Override public void onSuccess(JSONObject data) {
                String access = data.optString("access_token", "");
                String newRefresh = data.optString("refresh_token", "");
                JSONObject user = data.optJSONObject("user");
                if (blank(access) || blank(newRefresh) || p == null) {
                    callback.onError("Supabase did not return a valid refreshed session.");
                    return;
                }
                SharedPreferences.Editor editor = p.edit()
                        .putString("access_token", access)
                        .putString("refresh_token", newRefresh);
                if (user != null) {
                    String id = user.optString("id", "");
                    String email = user.optString("email", "");
                    if (!blank(id)) editor.putString("user_id", id);
                    if (!blank(email)) editor.putString("email", email);
                }
                editor.apply();
                callback.onSuccess(data);
            }
            @Override public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private static Request withCurrentToken(Request original) {
        String current = token();
        if (blank(current)) return original;
        return original.newBuilder()
                .header("Authorization", "Bearer " + current)
                .build();
    }

    private static String parseError(String text, int code) {
        if (text != null && !text.trim().isEmpty()) {
            try {
                JSONObject obj = new JSONObject(text);
                String message = obj.optString("message", "");
                if (message.isEmpty()) message = obj.optString("msg", "");
                if (message.isEmpty()) message = obj.optString("error_description", "");
                if (message.isEmpty()) message = obj.optString("hint", "");
                if (message.isEmpty()) message = obj.optString("error", "");
                if (!message.isEmpty()) return message + " (HTTP " + code + ")";
            } catch (JSONException ignored) { }
        }
        if (code == 401 || code == 403) {
            return "Supabase rejected the request (HTTP " + code
                    + "). Check the project URL, publishable key, login session and Row Level Security policies.";
        }
        return "Supabase request failed (HTTP " + code + ").";
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) { return value; }
    }

    private static String encodeLike(String value) {
        return encode(value).replace("%25", "%");
    }

    private static String encodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append("/");
            result.append(encode(part));
        }
        return result.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String messageOf(Exception e) {
        return e == null || blank(e.getMessage()) ? "Network request failed." : e.getMessage();
    }









    /**
     * Loads the minimal public-facing name for a conversation participant.
     * The profiles SELECT policy must permit legitimate conversation participants
     * to read this field; this method does not bypass Row Level Security.
     */
    public static void getConversationProfile(String participantId, ObjectCallback callback) {
        if (callback == null) return;
        if (blank(participantId)) {
            callback.onError("Participant ID is missing.");
            return;
        }
        getProfile(participantId, new ObjectCallback() {
            @Override public void onSuccess(JSONObject profile) {
                JSONObject safeProfile = new JSONObject();
                try {
                    safeProfile.put("id", profile.optString("id", participantId));
                    safeProfile.put("full_name", profile.optString("full_name", ""));
                } catch (JSONException e) {
                    callback.onError("Could not read the participant profile.");
                    return;
                }
                callback.onSuccess(safeProfile);
            }

            @Override public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** Loads the signed-in user's profile from public.profiles. */
    public static void getMyProfile(ObjectCallback callback) {
        if (callback == null) return;
        String id = userId();
        if (blank(id)) {
            callback.onError("You are not logged in. Please sign in again.");
            return;
        }
        getProfile(id, callback);
    }




    // -------------------- Announcements --------------------

    public static void sendTutorAnnouncement(
            String message,
            ObjectCallback callback) {

        if (callback == null) return;

        if (message == null || message.trim().isEmpty()) {
            callback.onError("Please enter an announcement.");
            return;
        }

        if (message.trim().length() > 4000) {
            callback.onError("Announcement must be 4,000 characters or fewer.");
            return;
        }

        if (!isLoggedIn()) {
            callback.onError("Please sign in again.");
            return;
        }

        JSONObject body = new JSONObject();

        try {
            body.put("p_message", message.trim());
        } catch (JSONException e) {
            callback.onError("Could not prepare announcement.");
            return;
        }

        Request request = authedRequest(
                SupabaseConfig.URL.trim()
                        + "/rest/v1/rpc/peerpath_send_announcement")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        executeObject(request, callback);
    }

    public static void getMyAnnouncements(JsonCallback callback) {
        if (callback == null) return;

        if (!isLoggedIn()) {
            callback.onError("Please sign in again.");
            return;
        }

        Request request = authedRequest(
                SupabaseConfig.URL.trim()
                        + "/rest/v1/rpc/peerpath_list_my_announcements")
                .post(RequestBody.create("{}", JSON))
                .build();

        executeArray(request, callback);
    }

    public static void deleteTutorAnnouncement(
            String announcementId,
            EmptyCallback callback) {

        if (callback == null) return;

        if (announcementId == null || announcementId.trim().isEmpty()) {
            callback.onError("Announcement ID is required.");
            return;
        }

        if (!isLoggedIn()) {
            callback.onError("Please sign in again.");
            return;
        }

        JSONObject body = new JSONObject();

        try {
            body.put("p_announcement_id", announcementId.trim());
        } catch (JSONException e) {
            callback.onError("Could not prepare the delete request.");
            return;
        }

        Request request = authedRequest(
                SupabaseConfig.URL.trim()
                        + "/rest/v1/rpc/peerpath_delete_announcement")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        executeEmpty(request, callback);
    }



    // -------------------- Learner announcements --------------------

    public static void getLearnerAnnouncements(JsonCallback callback) {
        if (callback == null) return;

        String problem = configurationError();
        if (problem != null) {
            callback.onError(problem);
            return;
        }

        if (!isLoggedIn()) {
            callback.onError("Please sign in to view announcements.");
            return;
        }

        Request request;
        try {
            request = authedRequest(
                    SupabaseConfig.URL.trim()
                            + "/rest/v1/rpc/peerpath_list_learner_announcements")
                    .post(RequestBody.create("{}", JSON))
                    .build();
        } catch (Exception e) {
            callback.onError(messageOf(e));
            return;
        }

        executeArray(request, callback);
    }



    // -------------------- Private review follow-up conversations --------------------

    public static void getReviewFollowupInbox(JsonCallback callback) {
        if (callback == null) return;
        String problem = configurationError();
        if (problem != null) { callback.onError(problem); return; }
        if (!isLoggedIn()) { callback.onError("Please sign in again."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_review_followup_list_inbox")
                    .post(RequestBody.create("{}", JSON)).build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeArray(request, callback);
    }

    public static void getReviewFollowupMessages(String reviewId, String audience,
                                                 JsonCallback callback) {
        if (callback == null) return;
        String problem = configurationError();
        if (problem != null) { callback.onError(problem); return; }
        if (!isLoggedIn()) { callback.onError("Please sign in again."); return; }
        if (blank(reviewId)) { callback.onError("Review ID is missing."); return; }
        if (!"learner".equals(audience) && !"tutor".equals(audience)) {
            callback.onError("Conversation must be learner or tutor."); return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("p_review_id", reviewId.trim());
            body.put("p_audience", audience);
        } catch (JSONException e) { callback.onError("Could not prepare conversation request."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_review_followup_list_messages")
                    .post(RequestBody.create(body.toString(), JSON)).build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeArray(request, callback);
    }

    public static void sendReviewFollowupMessage(String reviewId, String audience,
                                                 String message, JsonCallback callback) {
        if (callback == null) return;
        String problem = configurationError();
        if (problem != null) { callback.onError(problem); return; }
        if (!isLoggedIn()) { callback.onError("Please sign in again."); return; }
        if (blank(reviewId)) { callback.onError("Review ID is missing."); return; }
        if (!"learner".equals(audience) && !"tutor".equals(audience)) {
            callback.onError("Conversation must be learner or tutor."); return;
        }
        if (blank(message)) { callback.onError("Write a message first."); return; }
        if (message.trim().length() > 4000) { callback.onError("Messages must be 4,000 characters or fewer."); return; }
        JSONObject body = new JSONObject();
        try {
            body.put("p_review_id", reviewId.trim());
            body.put("p_audience", audience);
            body.put("p_body", message.trim());
        } catch (JSONException e) { callback.onError("Could not prepare your message."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_review_followup_send_message")
                    .post(RequestBody.create(body.toString(), JSON)).build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeArray(request, callback);
    }


    // -------------------- Sponsor application workflow --------------------

    /** Submit a sponsor application. The table policy must allow only the authenticated
     * sponsor to insert their own application; status/review fields are server controlled. */
    public static void submitSponsorApplication(EmptyCallback callback) {
        if (callback == null) return;

        if (!isLoggedIn()) {
            callback.onError("Please sign in again to submit your sponsor application.");
            return;
        }

        try {
            Request request = authedRequest(
                    SupabaseConfig.URL.trim()
                            + "/rest/v1/rpc/peerpath_submit_sponsor_application")
                    .post(RequestBody.create("{}", JSON))
                    .build();

            // The RPC returns a UUID; this callback only needs success/failure.
            executeEmpty(request, callback);
        } catch (Exception e) {
            callback.onError(messageOf(e));
        }
    }

    /** Admin-only RPC: lists sponsor applications for review. */
    /** Admin-only RPC: lists sponsor applications for review. */
    public static void getSponsorApplications(JsonCallback callback) {
        if (callback == null) return;

        if (!isConfigured()) {
            callback.onError("Supabase is not configured. Check the project URL and publishable key.");
            return;
        }

        if (!isLoggedIn() || blank(token())) {
            callback.onError("Your admin session is missing. Please sign in again.");
            return;
        }

        try {
            Request request = authedRequest(
                    SupabaseConfig.URL.trim()
                            + "/rest/v1/rpc/peerpath_admin_list_sponsor_applications")
                    .post(RequestBody.create("{}", JSON))
                    .build();

            executeArray(request, new JsonCallback() {
                @Override
                public void onSuccess(JSONArray data) {
                    callback.onSuccess(data == null ? new JSONArray() : data);
                }

                @Override
                public void onError(String message) {
                    callback.onError(
                            "Could not load sponsor applications: " + message
                    );
                }
            });
        } catch (Exception e) {
            callback.onError(
                    "Could not prepare sponsor application request: "
                            + messageOf(e)
            );
        }
    }
    /** Admin-only RPC: approve or reject a sponsor application. */
    public static void adminReviewSponsorApplication(String applicationId, boolean approve,
                                                     String note, EmptyCallback callback) {
        if (callback == null) return;
        if (blank(applicationId)) { callback.onError("Sponsor application ID is missing."); return; }
        if (!isLoggedIn()) { callback.onError("Please sign in first."); return; }
        JSONObject body = new JSONObject();
        try {
            body.put("p_application_id", applicationId.trim());
            body.put("p_approve", approve);
            body.put("p_note", note == null ? "" : note.trim());
        } catch (JSONException e) {
            callback.onError("Could not prepare sponsor review."); return;
        }
        Request request = authedRequest(SupabaseConfig.URL.trim()
                + "/rest/v1/rpc/peerpath_admin_review_sponsor_application")
                .post(RequestBody.create(body.toString(), JSON)).build();
        executeEmpty(request, callback);
    }

    // -------------------- Sponsorship workflow --------------------

    public static void getSponsorLearners(JsonCallback callback) {
        if (callback == null) return;
        if (!isLoggedIn()) { callback.onError("Please sign in first."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_sponsor_list_learners")
                    .post(RequestBody.create("{}", JSON)).build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeArray(request, callback);
    }

    public static void getSponsorRequests(JsonCallback callback) {
        if (callback == null) return;
        if (!isLoggedIn()) { callback.onError("Please sign in first."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_sponsor_list_requests")
                    .post(RequestBody.create("{}", JSON)).build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeArray(request, callback);
    }

    public static void requestSponsorLearner(String learnerId, EmptyCallback callback) {
        if (callback == null) return;
        if (!isLoggedIn()) { callback.onError("Please sign in first."); return; }
        if (blank(learnerId)) { callback.onError("Select a learner first."); return; }
        JSONObject body = new JSONObject();
        try { body.put("p_learner_id", learnerId.trim()); }
        catch (JSONException e) { callback.onError("Could not prepare the request."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_sponsor_request")
                    .post(RequestBody.create(body.toString(), JSON)).build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeEmpty(request, callback);
    }

    public static void approveSponsorRequest(String requestId, EmptyCallback callback) {
        if (callback == null) return;
        if (!isLoggedIn()) { callback.onError("Please sign in first."); return; }
        if (blank(requestId)) { callback.onError("Request ID is missing."); return; }
        JSONObject body = new JSONObject();
        try { body.put("p_request_id", requestId.trim()); }
        catch (JSONException e) { callback.onError("Could not prepare approval."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_sponsor_approve")
                    .post(RequestBody.create(body.toString(), JSON)).build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeEmpty(request, callback);
    }

    public static void rejectSponsorRequest(String requestId, String reason, EmptyCallback callback) {
        if (callback == null) return;
        if (!isLoggedIn()) { callback.onError("Please sign in first."); return; }
        if (blank(requestId)) { callback.onError("Request ID is missing."); return; }
        if (blank(reason)) { callback.onError("Enter a rejection reason."); return; }
        JSONObject body = new JSONObject();
        try {
            body.put("p_request_id", requestId.trim());
            body.put("p_reason", reason.trim());
        } catch (JSONException e) { callback.onError("Could not prepare rejection."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_sponsor_reject")
                    .post(RequestBody.create(body.toString(), JSON)).build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeEmpty(request, callback);
    }

    public static void revokeSponsorLink(String linkId, String reason, EmptyCallback callback) {
        if (callback == null) return;
        if (!isLoggedIn()) { callback.onError("Please sign in first."); return; }
        if (blank(linkId)) { callback.onError("Active sponsorship ID is missing."); return; }
        if (blank(reason)) { callback.onError("Enter a revocation reason."); return; }
        JSONObject body = new JSONObject();
        try {
            body.put("p_link_id", linkId.trim());
            body.put("p_reason", reason.trim());
        } catch (JSONException e) { callback.onError("Could not prepare revocation."); return; }
        Request request;
        try {
            request = authedRequest(SupabaseConfig.URL.trim()
                    + "/rest/v1/rpc/peerpath_sponsor_revoke")
                    .post(RequestBody.create(body.toString(), JSON)).build();
        } catch (Exception e) { callback.onError(messageOf(e)); return; }
        executeEmpty(request, callback);
    }





}
