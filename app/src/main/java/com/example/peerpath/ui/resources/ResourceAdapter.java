package com.example.peerpath.ui.resources;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.peerpath.R;
import com.example.peerpath.models.Resource;

import java.util.List;

public class ResourceAdapter extends RecyclerView.Adapter<ResourceAdapter.ResourceViewHolder> {

    public interface OnResourceClickListener {
        void onResourceClick(Resource resource);
    }

    private List<Resource> resources;
    private final OnResourceClickListener listener;

    public ResourceAdapter(List<Resource> resources, OnResourceClickListener listener) {
        this.resources = resources;
        this.listener = listener;
    }

    public void updateData(List<Resource> newResources) {
        this.resources = newResources;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ResourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_resource, parent, false);
        return new ResourceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResourceViewHolder holder, int position) {
        Resource resource = resources.get(position);
        holder.tvTitle.setText(resource.getTitle());
        holder.tvModule.setText(resource.getModuleCode());
        holder.tvDescription.setText(resource.getDescription());
        holder.itemView.setOnClickListener(v -> listener.onResourceClick(resource));
    }

    @Override
    public int getItemCount() {
        return resources.size();
    }

    static class ResourceViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvModule, tvDescription;

        ResourceViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvResourceTitle);
            tvModule = itemView.findViewById(R.id.tvResourceModule);
            tvDescription = itemView.findViewById(R.id.tvResourceDescription);
        }
    }
}
