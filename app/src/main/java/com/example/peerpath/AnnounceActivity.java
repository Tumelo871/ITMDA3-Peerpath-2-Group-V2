package com.example.peerpath;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.LinkedHashSet;
import java.util.Set;

public class AnnounceActivity extends AppCompatActivity {

    private final Set<String> students = new LinkedHashSet<>();

    private EditText etAnnouncement;
    private TextView tvRecipients;
    private MaterialButton btnSend;

    private FirebaseFirestore db;
    private String tutorId;
    private String tutorName = "Your tutor";

    private boolean recipientsLoaded = false;
    private boolean sending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_announce);

        etAnnouncement = findViewById(R.id.etAnnouncement);
        tvRecipients = findViewById(R.id.tvRecipients);
        btnSend = findViewById(R.id.btnSendAnnouncement);
        View back = findViewById(R.id.btnBack);

        if (etAnnouncement == null || tvRecipients == null
                || btnSend == null || back == null) {
            Toast.makeText(this,
                    "Announcement screen layout is incomplete.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        back.setOnClickListener(v -> finish());

        // This screen currently uses Firebase authentication.
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this,
                    "Please sign in to send announcements.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tutorId = FirebaseAuth.getInstance()
                .getCurrentUser().getUid();

        db = FirebaseFirestore.getInstance();

        btnSend.setEnabled(false);
        tvRecipients.setText("Loading your students...");

        loadTutorName();
        loadStudents();

        btnSend.setOnClickListener(v -> sendAnnouncement());
    }

    private void loadTutorName() {
        db.collection("users").document(tutorId).get()
                .addOnSuccessListener(document -> {
                    if (isFinishing() || isDestroyed()) return;

                    String name = document.getString("name");
                    if (name != null && !name.trim().isEmpty()) {
                        tutorName = name.trim();
                    }
                })
                .addOnFailureListener(error -> {
                    // Keep the safe fallback name.
                });
    }

    private void loadStudents() {
        db.collection("sessionRequests")
                .whereEqualTo("tutorId", tutorId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;

                    students.clear();

                    for (DocumentSnapshot document : snapshot) {
                        String status = document.getString("status");
                        String studentId = document.getString("studentId");

                        if (studentId != null && !studentId.trim().isEmpty()
                                && ("accepted".equalsIgnoreCase(status)
                                || "completed".equalsIgnoreCase(status))) {
                            students.add(studentId.trim());
                        }
                    }

                    recipientsLoaded = true;
                    btnSend.setEnabled(!students.isEmpty());

                    if (students.isEmpty()) {
                        tvRecipients.setText(
                                "No students found. Accept a tutoring request first.");
                    } else {
                        tvRecipients.setText("Ready to send to "
                                + students.size()
                                + (students.size() == 1
                                ? " student." : " students."));
                    }
                })
                .addOnFailureListener(error -> {
                    if (isFinishing() || isDestroyed()) return;

                    recipientsLoaded = false;
                    btnSend.setEnabled(false);
                    tvRecipients.setText(
                            "Could not load students. Check your connection and Firestore permissions.");
                    Toast.makeText(this,
                            "Loading students failed: " + error.getLocalizedMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void sendAnnouncement() {
        if (sending) return;

        if (!recipientsLoaded) {
            Toast.makeText(this,
                    "Wait until your students have loaded.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String message = etAnnouncement.getText().toString().trim();

        if (message.isEmpty()) {
            etAnnouncement.setError("Write your announcement.");
            etAnnouncement.requestFocus();
            return;
        }

        if (students.isEmpty()) {
            Toast.makeText(this, "There are no recipients.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        sending = true;
        btnSend.setEnabled(false);
        btnSend.setText("Sending...");

        final int total = students.size();
        final int[] completed = {0};
        final int[] failed = {0};

        for (String studentId : students) {
            NotificationHelper.sendNotification(
                    studentId,
                    "📢 Announcement from " + tutorName,
                    message,
                    "announcement",
                    tutorId
            );

            /*
             * The existing NotificationHelper method has no callback,
             * so this activity cannot confirm whether each notification
             * was actually saved. See the note below.
             */
            completed[0]++;
        }

        Toast.makeText(this,
                "Announcement submitted for " + total
                        + (total == 1 ? " student." : " students."),
                Toast.LENGTH_LONG).show();

        sending = false;
        finish();
    }
}
