package com.example.peerpath;

import android.os.Bundle;
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

/** A tutor sends one message to every student they teach. */
public class AnnounceActivity extends AppCompatActivity {

    private final Set<String> students = new LinkedHashSet<>();

    private EditText etAnnouncement;
    private TextView tvRecipients;
    private MaterialButton btnSend;

    private String tutorName = "Your tutor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announce);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        etAnnouncement = findViewById(R.id.etAnnouncement);
        tvRecipients = findViewById(R.id.tvRecipients);
        btnSend = findViewById(R.id.btnSendAnnouncement);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        db.collection("users").document(uid).get().addOnSuccessListener(d -> {
            if (d.getString("name") != null) tutorName = d.getString("name");
        });

        db.collection("sessionRequests").whereEqualTo("tutorId", uid).get()
                .addOnSuccessListener(snapshot -> {

                    for (DocumentSnapshot d : snapshot.getDocuments()) {
                        String st = d.getString("status");
                        if (("accepted".equals(st) || "completed".equals(st))
                                && d.getString("studentId") != null) {
                            students.add(d.getString("studentId"));
                        }
                    }

                    tvRecipients.setText(students.isEmpty()
                            ? "You have no students yet. Accept a session "
                            + "request first."
                            : "This will be sent to " + students.size()
                            + (students.size() == 1
                            ? " student." : " students."));
                });

        btnSend.setOnClickListener(v -> {

            String text = etAnnouncement.getText().toString().trim();

            if (text.isEmpty()) {
                etAnnouncement.setError("Write your announcement");
                return;
            }

            if (students.isEmpty()) {
                Toast.makeText(this, "No students to send to yet.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            for (String sid : students) {
                NotificationHelper.sendNotification(sid,
                        "\uD83D\uDCE2 Announcement from " + tutorName,
                        text, "announcement", uid);
            }

            Toast.makeText(this, "Sent to " + students.size()
                    + (students.size() == 1 ? " student" : " students"),
                    Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
