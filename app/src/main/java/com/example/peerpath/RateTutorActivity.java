package com.example.peerpath;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class RateTutorActivity extends AppCompatActivity {

    private RatingBar ratingBar;
    private EditText etComment;
    private Button btnSubmit;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tutorId;
    private String requestId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_rate_tutor);

        // =========================================================
        // FIREBASE
        // =========================================================

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // =========================================================
        // GET DATA FROM PREVIOUS SCREEN
        // =========================================================

        tutorId = getIntent().getStringExtra("tutorId");
        requestId = getIntent().getStringExtra("requestId");

        // =========================================================
        // VALIDATION
        // =========================================================

        if (
                tutorId == null
                        || tutorId.trim().isEmpty()
                        || requestId == null
                        || requestId.trim().isEmpty()
                        || auth.getCurrentUser() == null
        ) {
            finish();
            return;
        }

        // =========================================================
        // VIEWS
        // =========================================================

        ratingBar = findViewById(R.id.ratingBar);
        etComment = findViewById(R.id.etComment);
        btnSubmit = findViewById(R.id.btnSubmitRating);

        // =========================================================
        // BUTTONS
        // =========================================================

        findViewById(R.id.btnBack)
                .setOnClickListener(
                        v -> finish()
                );

        btnSubmit.setOnClickListener(
                v -> submitReview()
        );
    }

    // =============================================================
    // SUBMIT REVIEW
    // =============================================================

    private void submitReview() {

        // =========================================================
        // GET RATING
        // =========================================================

        float stars = ratingBar.getRating();

        if (stars <= 0) {

            Toast.makeText(
                    this,
                    "Please select a rating.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // =========================================================
        // GET COMMENT
        // =========================================================

        String comment =
                etComment.getText()
                        .toString()
                        .trim();

        // =========================================================
        // CURRENT STUDENT
        // =========================================================

        if (auth.getCurrentUser() == null) {

            Toast.makeText(
                    this,
                    "Please log in again.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String studentId =
                auth.getCurrentUser().getUid();

        // =========================================================
        // FIRESTORE REFERENCES
        // =========================================================

        DocumentReference requestRef =
                db.collection("sessionRequests")
                        .document(requestId);

        DocumentReference tutorRef =
                db.collection("users")
                        .document(tutorId);

        DocumentReference studentRef =
                db.collection("users")
                        .document(studentId);

        DocumentReference reviewRef =
                db.collection("reviews")
                        .document();

        // =========================================================
        // PREVENT DOUBLE SUBMISSION
        // =========================================================

        btnSubmit.setEnabled(false);

        // =========================================================
        // TRANSACTION
        // =========================================================

        db.runTransaction(tx -> {

            // -----------------------------------------------------
            // READ SESSION REQUEST
            // -----------------------------------------------------

            DocumentSnapshot request =
                    tx.get(requestRef);

            // -----------------------------------------------------
            // READ TUTOR
            // -----------------------------------------------------

            DocumentSnapshot tutor =
                    tx.get(tutorRef);

            // -----------------------------------------------------
            // MAKE SURE SESSION EXISTS
            // -----------------------------------------------------

            if (!request.exists()) {

                throw new FirebaseFirestoreException(
                        "This tutoring session could not be found.",
                        FirebaseFirestoreException.Code.NOT_FOUND
                );
            }

            // -----------------------------------------------------
            // CHECK IF ALREADY RATED
            // -----------------------------------------------------

            if (
                    Boolean.TRUE.equals(
                            request.getBoolean("rated")
                    )
            ) {

                throw new FirebaseFirestoreException(
                        "You already rated this session.",
                        FirebaseFirestoreException.Code.ABORTED
                );
            }

            // =====================================================
            // GET CURRENT RATING COUNT
            // =====================================================

            long count = 0;

            Long ratingCountValue =
                    tutor.getLong("ratingCount");

            if (ratingCountValue != null) {
                count = ratingCountValue;
            }

            // =====================================================
            // GET CURRENT RATING SUM
            // =====================================================

            double sum = 0.0;

            Double ratingSumValue =
                    tutor.getDouble("ratingSum");

            if (ratingSumValue != null) {

                sum = ratingSumValue;

            } else {

                // -------------------------------------------------
                // FALLBACK TO AVERAGE IF SUM DOES NOT EXIST
                // -------------------------------------------------

                Double ratingAverageValue =
                        tutor.getDouble("ratingAverage");

                if (ratingAverageValue != null) {

                    sum =
                            ratingAverageValue
                                    * count;
                }
            }

            // =====================================================
            // ADD NEW RATING
            // =====================================================

            count++;
            sum += stars;

            // =====================================================
            // CREATE REVIEW
            // =====================================================

            Map<String, Object> review =
                    new HashMap<>();

            review.put(
                    "studentId",
                    studentId
            );

            review.put(
                    "tutorId",
                    tutorId
            );

            review.put(
                    "requestId",
                    requestId
            );

            review.put(
                    "rating",
                    (double) stars
            );

            review.put(
                    "comment",
                    comment
            );

            review.put(
                    "createdAt",
                    System.currentTimeMillis()
            );

            tx.set(
                    reviewRef,
                    review
            );

            // =====================================================
            // MARK SESSION AS RATED
            // =====================================================

            Map<String, Object> requestUpdate =
                    new HashMap<>();

            requestUpdate.put(
                    "rated",
                    true
            );

            requestUpdate.put(
                    "rating",
                    (double) stars
            );

            tx.set(
                    requestRef,
                    requestUpdate,
                    SetOptions.merge()
            );

            // =====================================================
            // UPDATE TUTOR RATING
            // =====================================================

            Map<String, Object> tutorUpdate =
                    new HashMap<>();

            tutorUpdate.put(
                    "ratingSum",
                    sum
            );

            tutorUpdate.put(
                    "ratingCount",
                    count
            );

            tutorUpdate.put(
                    "ratingAverage",
                    sum / count
            );

            // =====================================================
            // 5 STAR XP BONUS
            // =====================================================

            if (stars >= 5f) {

                tutorUpdate.put(
                        "xp",
                        FieldValue.increment(
                                Gamification.XP_FIVE_STAR_BONUS
                        )
                );
            }

            tx.set(
                    tutorRef,
                    tutorUpdate,
                    SetOptions.merge()
            );

            // =====================================================
            // UPDATE STUDENT XP
            // =====================================================

            Map<String, Object> studentUpdate =
                    new HashMap<>();

            studentUpdate.put(
                    "xp",
                    FieldValue.increment(
                            Gamification.XP_REVIEW_GIVEN
                    )
            );

            studentUpdate.put(
                    "reviewsGiven",
                    FieldValue.increment(1)
            );

            tx.set(
                    studentRef,
                    studentUpdate,
                    SetOptions.merge()
            );

            return null;

        }).addOnSuccessListener(
                unused -> {

                    // =================================================
                    // NOTIFICATION
                    // =================================================

                    NotificationHelper.sendNotification(
                            tutorId,
                            "New review ⭐",
                            "A learner rated your session "
                                    + (int) stars
                                    + "/5.",
                            "review",
                            requestId
                    );

                    // =================================================
                    // SUCCESS MESSAGE
                    // =================================================

                    Toast.makeText(
                            this,
                            "Thanks! +"
                                    + Gamification.XP_REVIEW_GIVEN
                                    + " XP",
                            Toast.LENGTH_SHORT
                    ).show();

                    // =================================================
                    // CLOSE
                    // =================================================

                    finish();
                }
        ).addOnFailureListener(
                e -> {

                    btnSubmit.setEnabled(true);

                    String message =
                            e.getMessage();

                    if (
                            message == null
                                    || message.trim().isEmpty()
                    ) {

                        message =
                                "Could not submit review.";
                    }

                    Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }
}