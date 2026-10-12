package com.example.peerpath.ui.resources;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.peerpath.databinding.ActivitySimpleListBinding;
import com.example.peerpath.firebase.FirebaseRefs;
import com.example.peerpath.models.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists study resources, filterable by module code. Tapping a resource opens
 * its file URL; the primary action button opens UploadResourceActivity.
 */
public class ResourceRepositoryActivity extends AppCompatActivity {

    private ActivitySimpleListBinding binding;
    private final List<Resource> allResources = new ArrayList<>();
    private ResourceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySimpleListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());

        binding.tvHeader.setText("Resource Repository");
        binding.etSearchModule.setHint("Filter by module code (e.g. ITMDA3-22)");
        binding.btnPrimaryAction.setText("Upload a Resource");
        binding.btnPrimaryAction.setVisibility(View.VISIBLE);
        binding.btnPrimaryAction.setOnClickListener(v ->
                startActivity(new Intent(this, UploadResourceActivity.class)));

        List<Resource> visible = new ArrayList<>();
        adapter = new ResourceAdapter(visible, resource -> {
            if (resource.getFileUrl() != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(resource.getFileUrl())));
            }
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        binding.etSearchModule.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllResources(); // refresh in case a new resource was just uploaded
    }

    private void loadAllResources() {
        FirebaseRefs.resources()
                .get()
                .addOnSuccessListener(snapshot -> {
                    allResources.clear();
                    for (var doc : snapshot.getDocuments()) {
                        Resource resource = doc.toObject(Resource.class);
                        if (resource != null) {
                            resource.setResourceId(doc.getId());
                            allResources.add(resource);
                        }
                    }
                    applyFilter(binding.etSearchModule.getText().toString());
                })
                .addOnFailureListener(e -> binding.tvEmptyState.setVisibility(View.VISIBLE));
    }

    private void applyFilter(String moduleQuery) {
        List<Resource> filtered = new ArrayList<>();
        String query = moduleQuery.trim().toLowerCase();

        for (Resource resource : allResources) {
            if (query.isEmpty() || (resource.getModuleCode() != null
                    && resource.getModuleCode().toLowerCase().contains(query))) {
                filtered.add(resource);
            }
        }

        adapter.updateData(filtered);
        binding.tvEmptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.tvEmptyState.setText(allResources.isEmpty()
                ? "No resources yet — be the first to upload one."
                : "No resources found for that module code.");
    }
}
