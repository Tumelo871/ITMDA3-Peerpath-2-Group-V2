package com.example.peerpath;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/** Lets a tutor set the subject, bio and experience learners see. */
public class EditProfileActivity extends AppCompatActivity {

    private TextInputEditText etSubject, etBio, etExperience;
    private MaterialButton btnSave;

    private FirebaseFirestore db;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseFirestore.getInstance();

        etSubject = findViewById(R.id.etSubject);
        etBio = findViewById(R.id.etBio);
        etExperience = findViewById(R.id.etExperience);
        btnSave = findViewById(R.id.btnSaveProfile);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    etSubject.setText(doc.getString("subject"));
                    etBio.setText(doc.getString("bio"));
                    etExperience.setText(doc.getString("experience"));
                });

        btnSave.setOnClickListener(v -> save());
    }

    private void save() {

        String subject = text(etSubject);

        if (subject.isEmpty()) {
            etSubject.setError("Enter the module or subject you tutor");
            etSubject.requestFocus();
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("subject", subject);
        update.put("bio", text(etBio));
        update.put("experience", text(etExperience));

        btnSave.setEnabled(false);

        db.collection("users").document(uid).update(update)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Profile saved",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Could not save: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private String text(TextInputEditText f) {
        return f.getText() == null ? "" : f.getText().toString().trim();
    }
}
