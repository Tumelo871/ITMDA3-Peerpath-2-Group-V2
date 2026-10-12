package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Completes a Vossie user's profile if their Firestore profile is missing.
 */
public class CompleteProfileActivity extends AppCompatActivity {

    private TextInputEditText etName, etStudentNumber, etFaculty, etSubject, etBio, etExperience;
    private RadioGroup rgAccountType;
    private View tutorFields;
    private MaterialButton btnFinish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_profile);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        etName = findViewById(R.id.etName);
        etStudentNumber = findViewById(R.id.etStudentNumber);
        etFaculty = findViewById(R.id.etFaculty);
        etSubject = findViewById(R.id.etSubject);
        etBio = findViewById(R.id.etBio);
        etExperience = findViewById(R.id.etExperience);
        rgAccountType = findViewById(R.id.rgAccountType);
        tutorFields = findViewById(R.id.layoutTutorFields);
        btnFinish = findViewById(R.id.btnFinish);

        if (user.getDisplayName() != null) {
            etName.setText(user.getDisplayName());
        }

        rgAccountType.setOnCheckedChangeListener((g, id) ->
                tutorFields.setVisibility(
                        id == R.id.rbTutor ? View.VISIBLE : View.GONE));

        btnFinish.setOnClickListener(v -> save(user));

        findViewById(R.id.tvUseAnother).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    private void save(FirebaseUser user) {

        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        if (!Config.isVossieEmail(email)) {
            FirebaseAuth.getInstance().signOut();
            Toast.makeText(this, "Only Vossie accounts can use PeerPath.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String name = text(etName);
        String studentNumber = text(etStudentNumber);
        String faculty = text(etFaculty);
        boolean tutor =
                rgAccountType.getCheckedRadioButtonId() == R.id.rbTutor;
        String subject = text(etSubject);

        if (name.isEmpty()) {
            etName.setError("Enter your full name");
            etName.requestFocus();
            return;
        }

        if (studentNumber.isEmpty()) {
            etStudentNumber.setError("Enter your student number");
            etStudentNumber.requestFocus();
            return;
        }

        if (faculty.isEmpty()) {
            etFaculty.setError("Enter your faculty");
            etFaculty.requestFocus();
            return;
        }

        if (tutor && subject.isEmpty()) {
            etSubject.setError("Enter the module or subject you tutor");
            etSubject.requestFocus();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("name", name);
        data.put("email", user.getEmail() == null ? "" : user.getEmail());
        data.put("studentNumber", studentNumber);
        data.put("faculty", faculty);
        data.put("role", tutor ? "tutor" : "learner");
        data.put("tutor", false);
        data.put("xp", 0);
        data.put("streakDays", 0);
        data.put("bestStreak", 0);
        data.put("completedSessions", 0);
        data.put("totalSessions", 0);
        data.put("reviewsGiven", 0);

        if (tutor) {
            data.put("subject", subject);
            data.put("bio", text(etBio));
            data.put("experience", text(etExperience));
            data.put("ratingAverage", 0.0);
            data.put("ratingSum", 0.0);
            data.put("ratingCount", 0);
        }

        data.put("createdAt", FieldValue.serverTimestamp());

        btnFinish.setEnabled(false);

        FirebaseFirestore.getInstance().collection("users")
                .document(user.getUid()).set(data)
                .addOnSuccessListener(unused -> {
                    Intent i = new Intent(this, DashboardActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnFinish.setEnabled(true);
                    Toast.makeText(this, "Could not save: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private String text(TextInputEditText f) {
        return f.getText() == null ? "" : f.getText().toString().trim();
    }
}
