package com.example.peerpath.supabase;

import android.content.Context;
import android.content.SharedPreferences;
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
 * PeerPath Supabase REST API.
 *
 * This class handles:
 *
 * Authentication
 * Profiles
 * Tutors
 * Tutor applications
 * Sessions
 * Messages
 * Notifications
 * Learning materials/resources
 * Storage uploads
 */
public final class SupabaseApi {

    // =========================================================
    // CALLBACKS
    // =========================================================

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

    // =========================================================
    // HTTP
    // =========================================================

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient HTTP =
            new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

    // =========================================================
    // SESSION
    // =========================================================

    private static SharedPreferences prefs;

    private SupabaseApi() {
    }

    /**
     * Call this once when the application starts.
     */
    public static void init(Context context) {

        if (context == null) {
            return;
        }

        prefs = context
                .getApplicationContext()
                .getSharedPreferences(
                        "supabase_session",
                        Context.MODE_PRIVATE
                );
    }

    // =========================================================
    // CONFIG
    // =========================================================

    public static boolean isConfigured() {

        if (SupabaseConfig.URL == null) {
            return false;
        }

        if (SupabaseConfig.PUBLISHABLE_KEY == null) {
            return false;
        }

        return SupabaseConfig.URL.startsWith("https://")
                && !SupabaseConfig.URL.contains("YOUR_PROJECT_REF")
                && !SupabaseConfig.PUBLISHABLE_KEY
                .contains("YOUR_SUPABASE_PUBLISHABLE_KEY");
    }

    // =========================================================
    // SESSION HELPERS
    // =========================================================

    public static String token() {

        if (prefs == null) {
            return null;
        }

        return prefs.getString(
                "access_token",
                null
        );
    }

    public static String userId() {

        if (prefs == null) {
            return null;
        }

        return prefs.getString(
                "user_id",
                null
        );
    }

    public static String userEmail() {

        if (prefs == null) {
            return null;
        }

        return prefs.getString(
                "email",
                null
        );
    }

    public static boolean isLoggedIn() {

        String token = token();
        String id = userId();

        return token != null
                && !token.trim().isEmpty()
                && id != null
                && !id.trim().isEmpty();
    }

    public static void clearSession() {

        if (prefs != null) {
            prefs.edit()
                    .clear()
                    .apply();
        }
    }

    // =========================================================
    // AUTH - SIGN UP
    // =========================================================

    public static void signUp(
            String email,
            String password,
            JSONObject metadata,
            ObjectCallback callback
    ) {

        if (!isConfigured()) {
            callback.onError(
                    "Supabase is not configured."
            );
            return;
        }

        if (email == null || email.trim().isEmpty()) {
            callback.onError(
                    "Email is required."
            );
            return;
        }

        if (password == null || password.length() < 6) {
            callback.onError(
                    "Password must contain at least 6 characters."
            );
            return;
        }

        JSONObject body = new JSONObject();

        try {

            body.put(
                    "email",
                    email.trim()
            );

            body.put(
                    "password",
                    password
            );

            body.put(
                    "data",
                    metadata == null
                            ? new JSONObject()
                            : metadata
            );

        } catch (JSONException e) {

            callback.onError(
                    errorMessage(e)
            );

            return;
        }

        Request request =
                new Request.Builder()
                        .url(
                                SupabaseConfig.URL
                                        + "/auth/v1/signup"
                        )
                        .addHeader(
                                "apikey",
                                SupabaseConfig.PUBLISHABLE_KEY
                        )
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .post(
                                RequestBody.create(
                                        body.toString(),
                                        JSON
                                )
                        )
                        .build();

        executeObject(
                request,
                callback
        );
    }

    // =========================================================
    // AUTH - SIGN IN
    // =========================================================

    public static void signIn(
            String email,
            String password,
            ObjectCallback callback
    ) {

        if (!isConfigured()) {
            callback.onError(
                    "Supabase is not configured."
            );
            return;
        }

        JSONObject body = new JSONObject();

        try {

            body.put(
                    "email",
                    email.trim()
            );

            body.put(
                    "password",
                    password
            );

        } catch (JSONException e) {

            callback.onError(
                    errorMessage(e)
            );

            return;
        }

        Request request =
                new Request.Builder()
                        .url(
                                SupabaseConfig.URL
                                        + "/auth/v1/token?grant_type=password"
                        )
                        .addHeader(
                                "apikey",
                                SupabaseConfig.PUBLISHABLE_KEY
                        )
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .post(
                                RequestBody.create(
                                        body.toString(),
                                        JSON
                                )
                        )
                        .build();

        executeObject(
                request,
                new ObjectCallback() {

                    @Override
                    public void onSuccess(
                            JSONObject data
                    ) {

                        try {

                            String accessToken =
                                    data.optString(
                                            "access_token",
                                            ""
                                    );

                            String refreshToken =
                                    data.optString(
                                            "refresh_token",
                                            ""
                                    );

                            JSONObject user =
                                    data.optJSONObject(
                                            "user"
                                    );

                            if (
                                    accessToken.isEmpty()
                                            || user == null
                            ) {

                                callback.onError(
                                        "Supabase did not return a valid login session."
                                );

                                return;
                            }

                            String id =
                                    user.optString(
                                            "id",
                                            ""
                                    );

                            String userEmail =
                                    user.optString(
                                            "email",
                                            email
                                    );

                            if (prefs == null) {

                                callback.onError(
                                        "Supabase session has not been initialized."
                                );

                                return;
                            }

                            prefs.edit()
                                    .putString(
                                            "access_token",
                                            accessToken
                                    )
                                    .putString(
                                            "refresh_token",
                                            refreshToken
                                    )
                                    .putString(
                                            "user_id",
                                            id
                                    )
                                    .putString(
                                            "email",
                                            userEmail
                                    )
                                    .apply();

                            callback.onSuccess(
                                    data
                            );

                        } catch (Exception e) {

                            callback.onError(
                                    errorMessage(e)
                            );
                        }
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        callback.onError(
                                message
                        );
                    }
                }
        );
    }

    // =========================================================
    // PASSWORD RESET
    // =========================================================

    public static void resetPassword(
            String email,
            EmptyCallback callback
    ) {

        JSONObject body = new JSONObject();

        try {

            body.put(
                    "email",
                    email.trim()
            );

        } catch (JSONException e) {

            callback.onError(
                    errorMessage(e)
            );

            return;
        }

        Request request =
                new Request.Builder()
                        .url(
                                SupabaseConfig.URL
                                        + "/auth/v1/recover"
                        )
                        .addHeader(
                                "apikey",
                                SupabaseConfig.PUBLISHABLE_KEY
                        )
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .post(
                                RequestBody.create(
                                        body.toString(),
                                        JSON
                                )
                        )
                        .build();

        executeEmpty(
                request,
                callback
        );
    }

    // =========================================================
    // PROFILE
    // =========================================================

    public static void getProfile(
            String id,
            ObjectCallback callback
    ) {

        if (id == null || id.trim().isEmpty()) {

            callback.onError(
                    "User ID is missing."
            );

            return;
        }

        dbGet(
                "profiles",
                "id=eq."
                        + encode(id)
                        + "&select=*",
                new JsonCallback() {

                    @Override
                    public void onSuccess(
                            JSONArray data
                    ) {

                        if (
                                data == null
                                        || data.length() == 0
                        ) {

                            callback.onError(
                                    "Profile not found."
                            );

                            return;
                        }

                        JSONObject profile =
                                data.optJSONObject(0);

                        if (profile == null) {

                            callback.onError(
                                    "Invalid profile returned."
                            );

                            return;
                        }

                        callback.onSuccess(
                                profile
                        );
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        callback.onError(
                                message
                        );
                    }
                }
        );
    }

    public static void updateProfile(
            JSONObject values,
            EmptyCallback callback
    ) {

        String id = userId();

        if (
                id == null
                        || id.trim().isEmpty()
        ) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        dbPatch(
                "profiles",
                "id=eq." + encode(id),
                values,
                callback
        );
    }

    // =========================================================
    // VERIFIED TUTORS
    // =========================================================

    public static void getVerifiedTutors(
            @Nullable String search,
            JsonCallback callback
    ) {

        String query =
                "role=eq.tutor"
                        + "&verification_status=eq.verified"
                        + "&select=*"
                        + "&order=full_name.asc";

        if (
                search != null
                        && !search.trim().isEmpty()
        ) {

            String value =
                    encodeLike(
                            search.trim()
                    );

            query =
                    "role=eq.tutor"
                            + "&verification_status=eq.verified"
                            + "&or=("
                            + "full_name.ilike.*"
                            + value
                            + "*,"
                            + "module_codes.ilike.*"
                            + value
                            + "*,"
                            + "tutor_subject.ilike.*"
                            + value
                            + "*)"
                            + "&select=*"
                            + "&order=full_name.asc";
        }

        dbGet(
                "profiles",
                query,
                callback
        );
    }

    // =========================================================
    // SESSIONS
    // =========================================================

    public static void createSession(
            String tutorId,
            String subject,
            String date,
            String time,
            String message,
            EmptyCallback callback
    ) {

        String learnerId = userId();

        if (
                learnerId == null
                        || learnerId.trim().isEmpty()
        ) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        JSONObject row =
                new JSONObject();

        try {

            row.put(
                    "learner_id",
                    learnerId
            );

            row.put(
                    "tutor_id",
                    tutorId
            );

            row.put(
                    "subject",
                    subject
            );

            row.put(
                    "session_date",
                    date
            );

            row.put(
                    "session_time",
                    time
            );

            row.put(
                    "learner_message",
                    message
            );

            row.put(
                    "status",
                    "pending"
            );

        } catch (JSONException e) {

            callback.onError(
                    errorMessage(e)
            );

            return;
        }

        dbPost(
                "sessions",
                row,
                callback
        );
    }

    public static void getMySessions(
            JsonCallback callback
    ) {

        String id = userId();

        if (
                id == null
                        || id.trim().isEmpty()
        ) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        dbGet(
                "sessions",
                "or=(learner_id.eq."
                        + encode(id)
                        + ",tutor_id.eq."
                        + encode(id)
                        + ")"
                        + "&select=*"
                        + "&order=created_at.desc",
                callback
        );
    }

    public static void getAcceptedSessions(
            JsonCallback callback
    ) {

        String id = userId();

        if (
                id == null
                        || id.trim().isEmpty()
        ) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        dbGet(
                "sessions",
                "status=eq.accepted"
                        + "&or=(learner_id.eq."
                        + encode(id)
                        + ",tutor_id.eq."
                        + encode(id)
                        + ")"
                        + "&select=*"
                        + "&order=created_at.desc",
                callback
        );
    }

    public static void getPendingTutorRequests(
            JsonCallback callback
    ) {

        String id = userId();

        if (
                id == null
                        || id.trim().isEmpty()
        ) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        dbGet(
                "sessions",
                "tutor_id=eq."
                        + encode(id)
                        + "&status=eq.pending"
                        + "&select=*"
                        + "&order=created_at.desc",
                callback
        );
    }

    public static void updateSessionStatus(
            String sessionId,
            String status,
            EmptyCallback callback
    ) {

        JSONObject values =
                new JSONObject();

        try {

            values.put(
                    "status",
                    status
            );

        } catch (JSONException e) {

            callback.onError(
                    errorMessage(e)
            );

            return;
        }

        dbPatch(
                "sessions",
                "id=eq."
                        + encode(sessionId),
                values,
                callback
        );
    }

    // =========================================================
    // MESSAGES
    // =========================================================

    public static void getMessages(
            String sessionId,
            JsonCallback callback
    ) {

        dbGet(
                "messages",
                "session_id=eq."
                        + encode(sessionId)
                        + "&select=*"
                        + "&order=created_at.asc",
                callback
        );
    }

    public static void sendMessage(
            String sessionId,
            String receiverId,
            String message,
            EmptyCallback callback
    ) {

        String senderId = userId();

        if (
                senderId == null
                        || senderId.trim().isEmpty()
        ) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        JSONObject row =
                new JSONObject();

        try {

            row.put(
                    "session_id",
                    sessionId
            );

            row.put(
                    "sender_id",
                    senderId
            );

            row.put(
                    "receiver_id",
                    receiverId
            );

            row.put(
                    "body",
                    message
            );

        } catch (JSONException e) {

            callback.onError(
                    errorMessage(e)
            );

            return;
        }

        dbPost(
                "messages",
                row,
                callback
        );
    }

    // =========================================================
    // NOTIFICATIONS
    // =========================================================

    public static void getNotifications(
            JsonCallback callback
    ) {

        String id = userId();

        if (
                id == null
                        || id.trim().isEmpty()
        ) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        dbGet(
                "notifications",
                "user_id=eq."
                        + encode(id)
                        + "&select=*"
                        + "&order=created_at.desc",
                callback
        );
    }

    public static void markNotificationRead(
            String notificationId,
            EmptyCallback callback
    ) {

        JSONObject values =
                new JSONObject();

        try {

            values.put(
                    "is_read",
                    true
            );

        } catch (JSONException e) {

            callback.onError(
                    errorMessage(e)
            );

            return;
        }

        dbPatch(
                "notifications",
                "id=eq."
                        + encode(notificationId),
                values,
                callback
        );
    }

    // =========================================================
    // TUTOR APPLICATIONS
    // =========================================================

    public static void submitTutorApplication(
            String moduleCodes,
            String evidencePath,
            EmptyCallback callback
    ) {

        String id = userId();

        if (
                id == null
                        || id.trim().isEmpty()
        ) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        JSONObject row =
                new JSONObject();

        try {

            row.put(
                    "tutor_id",
                    id
            );

            row.put(
                    "module_codes",
                    moduleCodes
            );

            row.put(
                    "evidence_path",
                    evidencePath
            );

            row.put(
                    "status",
                    "pending"
            );

        } catch (JSONException e) {

            callback.onError(
                    errorMessage(e)
            );

            return;
        }

        dbPost(
                "tutor_applications",
                row,
                callback
        );
    }

    public static void getPendingApplications(
            JsonCallback callback
    ) {

        dbGet(
                "tutor_applications",
                "status=eq.pending"
                        + "&select=*"
                        + "&order=created_at.asc",
                callback
        );
    }

    public static void reviewApplication(
            String applicationId,
            String tutorId,
            boolean approve,
            String note,
            EmptyCallback callback
    ) {

        String status =
                approve
                        ? "verified"
                        : "rejected";

        JSONObject application =
                new JSONObject();

        JSONObject profile =
                new JSONObject();

        try {

            application.put(
                    "status",
                    status
            );

            application.put(
                    "reviewer_id",
                    userId()
            );

            application.put(
                    "reviewer_note",
                    note == null
                            ? ""
                            : note
            );

            /*
             * Keep profile role consistent with approval.
             */
            profile.put(
                    "role",
                    approve
                            ? "tutor"
                            : "learner"
            );

            profile.put(
                    "verification_status",
                    status
            );

        } catch (JSONException e) {

            callback.onError(
                    errorMessage(e)
            );

            return;
        }

        dbPatch(
                "tutor_applications",
                "id=eq."
                        + encode(applicationId),
                application,
                new EmptyCallback() {

                    @Override
                    public void onSuccess() {

                        dbPatch(
                                "profiles",
                                "id=eq."
                                        + encode(tutorId),
                                profile,
                                callback
                        );
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        callback.onError(
                                message
                        );
                    }
                }
        );
    }

    // =========================================================
    // RESOURCES / MATERIALS
    // =========================================================

    public static void getResources(
            JsonCallback callback
    ) {

        dbGet(
                "resources",
                "select=*"
                        + "&order=created_at.desc",
                callback
        );
    }

    public static void createResource(
            String title,
            String subject,
            String link,
            String description,
            EmptyCallback callback
    ) {

        String id = userId();

        if (
                id == null
                        || id.trim().isEmpty()
        ) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        JSONObject row =
                new JSONObject();

        try {

            row.put(
                    "owner_id",
                    id
            );

            row.put(
                    "title",
                    title
            );

            row.put(
                    "subject",
                    subject
            );

            row.put(
                    "url",
                    link == null
                            ? ""
                            : link
            );

            row.put(
                    "description",
                    description == null
                            ? ""
                            : description
            );

        } catch (JSONException e) {

            callback.onError(
                    errorMessage(e)
            );

            return;
        }

        dbPost(
                "resources",
                row,
                callback
        );
    }

    public static void deleteResource(
            String resourceId,
            EmptyCallback callback
    ) {

        if (
                resourceId == null
                        || resourceId.trim().isEmpty()
        ) {

            callback.onError(
                    "Resource ID is missing."
            );

            return;
        }

        dbDelete(
                "resources",
                "id=eq."
                        + encode(resourceId),
                callback
        );
    }

    // =========================================================
    // STORAGE
    // =========================================================

    public static void uploadFile(
            android.content.ContentResolver resolver,
            Uri uri,
            String path,
            EmptyCallback callback
    ) {

        if (!isLoggedIn()) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        if (resolver == null || uri == null) {

            callback.onError(
                    "Invalid file."
            );

            return;
        }

        try {

            InputStream input =
                    resolver.openInputStream(uri);

            if (input == null) {

                callback.onError(
                        "Could not open selected file."
                );

                return;
            }

            byte[] bytes =
                    readAll(input);

            input.close();

            String mime =
                    resolver.getType(uri);

            if (
                    mime == null
                            || mime.trim().isEmpty()
            ) {

                mime =
                        "application/octet-stream";
            }

            String url =
                    SupabaseConfig.URL
                            + "/storage/v1/object/peerpath-files/"
                            + path;

            Request request =
                    new Request.Builder()
                            .url(url)
                            .post(
                                    RequestBody.create(
                                            bytes,
                                            MediaType.parse(mime)
                                    )
                            )
                            .addHeader(
                                    "apikey",
                                    SupabaseConfig.PUBLISHABLE_KEY
                            )
                            .addHeader(
                                    "Authorization",
                                    "Bearer " + token()
                            )
                            .addHeader(
                                    "Content-Type",
                                    mime
                            )
                            .build();

            executeEmpty(
                    request,
                    callback
            );

        } catch (Exception e) {

            callback.onError(
                    errorMessage(e)
            );
        }
    }

    // =========================================================
    // DATABASE - GET
    // =========================================================

    private static void dbGet(
            String table,
            String query,
            JsonCallback callback
    ) {

        if (!isLoggedIn()) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        String url =
                SupabaseConfig.URL
                        + "/rest/v1/"
                        + table
                        + "?"
                        + query;

        Request request =
                new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader(
                                "apikey",
                                SupabaseConfig.PUBLISHABLE_KEY
                        )
                        .addHeader(
                                "Authorization",
                                "Bearer " + token()
                        )
                        .addHeader(
                                "Accept",
                                "application/json"
                        )
                        .build();

        executeArray(
                request,
                callback
        );
    }

    // =========================================================
    // DATABASE - POST
    // =========================================================

    private static void dbPost(
            String table,
            JSONObject row,
            EmptyCallback callback
    ) {

        if (!isLoggedIn()) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        String url =
                SupabaseConfig.URL
                        + "/rest/v1/"
                        + table;

        Request request =
                new Request.Builder()
                        .url(url)
                        .post(
                                RequestBody.create(
                                        row.toString(),
                                        JSON
                                )
                        )
                        .addHeader(
                                "apikey",
                                SupabaseConfig.PUBLISHABLE_KEY
                        )
                        .addHeader(
                                "Authorization",
                                "Bearer " + token()
                        )
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .addHeader(
                                "Prefer",
                                "return=minimal"
                        )
                        .build();

        executeEmpty(
                request,
                callback
        );
    }

    // =========================================================
    // DATABASE - PATCH
    // =========================================================

    private static void dbPatch(
            String table,
            String query,
            JSONObject values,
            EmptyCallback callback
    ) {

        if (!isLoggedIn()) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        String url =
                SupabaseConfig.URL
                        + "/rest/v1/"
                        + table
                        + "?"
                        + query;

        Request request =
                new Request.Builder()
                        .url(url)
                        .patch(
                                RequestBody.create(
                                        values.toString(),
                                        JSON
                                )
                        )
                        .addHeader(
                                "apikey",
                                SupabaseConfig.PUBLISHABLE_KEY
                        )
                        .addHeader(
                                "Authorization",
                                "Bearer " + token()
                        )
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .addHeader(
                                "Prefer",
                                "return=minimal"
                        )
                        .build();

        executeEmpty(
                request,
                callback
        );
    }

    // =========================================================
    // DATABASE - DELETE
    // =========================================================

    private static void dbDelete(
            String table,
            String query,
            EmptyCallback callback
    ) {

        if (!isLoggedIn()) {

            callback.onError(
                    "You are not logged in."
            );

            return;
        }

        String url =
                SupabaseConfig.URL
                        + "/rest/v1/"
                        + table
                        + "?"
                        + query;

        Request request =
                new Request.Builder()
                        .url(url)
                        .delete()
                        .addHeader(
                                "apikey",
                                SupabaseConfig.PUBLISHABLE_KEY
                        )
                        .addHeader(
                                "Authorization",
                                "Bearer " + token()
                        )
                        .addHeader(
                                "Prefer",
                                "return=minimal"
                        )
                        .build();

        executeEmpty(
                request,
                callback
        );
    }

    // =========================================================
    // EXECUTE OBJECT
    // =========================================================

    private static void executeObject(
            Request request,
            ObjectCallback callback
    ) {

        HTTP.newCall(request)
                .enqueue(
                        new Callback() {

                            @Override
                            public void onFailure(
                                    Call call,
                                    IOException e
                            ) {

                                callback.onError(
                                        e.getMessage() == null
                                                ? "Network request failed."
                                                : e.getMessage()
                                );
                            }

                            @Override
                            public void onResponse(
                                    Call call,
                                    Response response
                            ) throws IOException {

                                String text =
                                        response.body() == null
                                                ? ""
                                                : response.body().string();

                                try {

                                    if (!response.isSuccessful()) {

                                        callback.onError(
                                                parseError(
                                                        text,
                                                        response.code()
                                                )
                                        );

                                        return;
                                    }

                                    JSONObject object;

                                    if (
                                            text == null
                                                    || text.trim().isEmpty()
                                    ) {

                                        object =
                                                new JSONObject();

                                    } else {

                                        object =
                                                new JSONObject(text);
                                    }

                                    callback.onSuccess(
                                            object
                                    );

                                } catch (JSONException e) {

                                    callback.onError(
                                            "Invalid Supabase response."
                                    );

                                } finally {

                                    response.close();
                                }
                            }
                        }
                );
    }

    // =========================================================
    // EXECUTE ARRAY
    // =========================================================

    private static void executeArray(
            Request request,
            JsonCallback callback
    ) {

        HTTP.newCall(request)
                .enqueue(
                        new Callback() {

                            @Override
                            public void onFailure(
                                    Call call,
                                    IOException e
                            ) {

                                callback.onError(
                                        e.getMessage() == null
                                                ? "Network request failed."
                                                : e.getMessage()
                                );
                            }

                            @Override
                            public void onResponse(
                                    Call call,
                                    Response response
                            ) throws IOException {

                                String text =
                                        response.body() == null
                                                ? "[]"
                                                : response.body().string();

                                try {

                                    if (!response.isSuccessful()) {

                                        callback.onError(
                                                parseError(
                                                        text,
                                                        response.code()
                                                )
                                        );

                                        return;
                                    }

                                    callback.onSuccess(
                                            new JSONArray(text)
                                    );

                                } catch (JSONException e) {

                                    callback.onError(
                                            "Invalid Supabase response."
                                    );

                                } finally {

                                    response.close();
                                }
                            }
                        }
                );
    }

    // =========================================================
    // EXECUTE EMPTY
    // =========================================================

    private static void executeEmpty(
            Request request,
            EmptyCallback callback
    ) {

        HTTP.newCall(request)
                .enqueue(
                        new Callback() {

                            @Override
                            public void onFailure(
                                    Call call,
                                    IOException e
                            ) {

                                callback.onError(
                                        e.getMessage() == null
                                                ? "Network request failed."
                                                : e.getMessage()
                                );
                            }

                            @Override
                            public void onResponse(
                                    Call call,
                                    Response response
                            ) throws IOException {

                                String text =
                                        response.body() == null
                                                ? ""
                                                : response.body().string();

                                try {

                                    if (!response.isSuccessful()) {

                                        callback.onError(
                                                parseError(
                                                        text,
                                                        response.code()
                                                )
                                        );

                                        return;
                                    }

                                    callback.onSuccess();

                                } finally {

                                    response.close();
                                }
                            }
                        }
                );
    }

    // =========================================================
    // FILE READER
    // =========================================================

    private static byte[] readAll(
            InputStream input
    ) throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        byte[] buffer =
                new byte[8192];

        int count;

        while (
                (count = input.read(buffer)) != -1
        ) {

            output.write(
                    buffer,
                    0,
                    count
            );
        }

        return output.toByteArray();
    }

    // =========================================================
    // URL ENCODING
    // =========================================================

    private static String encode(
            String value
    ) {

        try {

            return URLEncoder
                    .encode(
                            value,
                            StandardCharsets.UTF_8.name()
                    )
                    .replace(
                            "+",
                            "%20"
                    );

        } catch (Exception e) {

            return value;
        }
    }

    private static String encodeLike(
            String value
    ) {

        return encode(value)
                .replace(
                        "%25",
                        "%"
                );
    }

    // =========================================================
    // ERROR HANDLING
    // =========================================================

    private static String parseError(
            String text,
            int statusCode
    ) {

        try {

            if (
                    text != null
                            && !text.trim().isEmpty()
            ) {

                JSONObject error =
                        new JSONObject(text);

                String message =
                        error.optString(
                                "message",
                                ""
                        );

                if (message.isEmpty()) {

                    message =
                            error.optString(
                                    "msg",
                                    ""
                            );
                }

                if (message.isEmpty()) {

                    message =
                            error.optString(
                                    "error_description",
                                    ""
                            );
                }

                if (message.isEmpty()) {

                    message =
                            error.optString(
                                    "hint",
                                    ""
                            );
                }

                if (!message.isEmpty()) {

                    return message;
                }
            }

        } catch (Exception ignored) {
        }

        return "Supabase request failed (HTTP "
                + statusCode
                + ").";
    }

    private static String errorMessage(
            Exception exception
    ) {

        if (
                exception == null
                        || exception.getMessage() == null
        ) {

            return "An unexpected error occurred.";
        }

        return exception.getMessage();
    }


    public static void getLeaderboard(JsonCallback callback) {
        if (callback == null) {
            return;
        }

        if (!isConfigured()) {
            callback.onError("Supabase is not configured.");
            return;
        }

        if (!isLoggedIn()) {
            callback.onError("Please sign in to view the leaderboard.");
            return;
        }

        String accessToken = token();

        if (accessToken == null || accessToken.trim().isEmpty()) {
            callback.onError("Your session has expired. Please sign in again.");
            return;
        }

        Request request = new Request.Builder()
                .url(SupabaseConfig.URL + "/rest/v1/rpc/peerpath_leaderboard")
                .post(RequestBody.create("{}", JSON))
                .addHeader("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();

        executeArray(request, callback);
    }


}