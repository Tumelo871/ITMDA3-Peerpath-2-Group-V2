package com.example.peerpath.ui.resources;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.databinding.ActivityUploadResourceBinding;
import com.example.peerpath.firebase.FirebaseRefs;
import com.example.peerpath.models.Resource;
import com.google.firebase.auth.FirebaseUser;

import java.util.UUID;

/**
 * Lets a signed-in user upload a study resource tagged to a module code.
 * File goes to Firebase Storage; a Resource document indexing it (with the
 * resulting download URL) goes to Firestore so it's searchable by module.
 */
public class UploadResourceActivity extends AppCompatActivity {

    private ActivityUploadResourceBinding binding;
    private Uri selectedFileUri;

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    selectedFileUri = uri;
                    binding.tvSelectedFile.setText(uri.getLastPathSegment());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUploadResourceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnPickFile.setOnClickListener(v ->
                filePicker.launch(new String[]{"application/pdf", "image/*", "text/plain"}));

        binding.btnUpload.setOnClickListener(v -> attemptUpload());
    }

    private void attemptUpload() {
        String moduleCode = binding.etModuleCode.getText().toString().trim();
        String title = binding.etTitle.getText().toString().trim();
        String description = binding.etDescription.getText().toString().trim();

        if (moduleCode.isEmpty() || title.isEmpty()) {
            showError("Module code and title are required.");
            return;
        }
        if (selectedFileUri == null) {
            showError("Please choose a file first.");
            return;
        }

        FirebaseUser fbUser = FirebaseRefs.auth().getCurrentUser();
        if (fbUser == null) {
            showError("You need to be signed in to upload.");
            return;
        }

        setLoading(true);
        String storagePath = fbUser.getUid() + "/" + UUID.randomUUID();

        FirebaseRefs.resourceFiles().child(storagePath).putFile(selectedFileUri)
                .addOnSuccessListener(taskSnapshot ->
                        FirebaseRefs.resourceFiles().child(storagePath).getDownloadUrl()
                                .addOnSuccessListener(downloadUrl -> saveResourceDoc(
                                        moduleCode, title, description, downloadUrl.toString(), fbUser.getUid()))
                                .addOnFailureListener(e -> {
                                    setLoading(false);
                                    showError(e.getMessage());
                                }))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(e.getMessage());
                });
    }

    private void saveResourceDoc(String moduleCode, String title, String description,
                                  String fileUrl, String uploaderUid) {
        Resource resource = new Resource(moduleCode, title, description, fileUrl, uploaderUid);
        FirebaseRefs.resources().add(resource)
                .addOnSuccessListener(docRef -> finish())
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(e.getMessage());
                });
    }

    private void setLoading(boolean loading) {
        binding.progressUpload.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnUpload.setEnabled(!loading);
    }

    private void showError(String message) {
        binding.tvError.setText(message);
        binding.tvError.setVisibility(View.VISIBLE);
    }
}
