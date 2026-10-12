package com.example.peerpath.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.peerpath.R;
import com.example.peerpath.models.TutorProfile;

import java.util.List;

public class PendingTutorAdapter extends RecyclerView.Adapter<PendingTutorAdapter.PendingViewHolder> {

    public interface OnDecisionListener {
        void onVerify(TutorProfile profile);
        void onReject(TutorProfile profile);
        void onViewEvidence(TutorProfile profile);
    }

    private List<TutorProfile> pending;
    private final OnDecisionListener listener;

    public PendingTutorAdapter(List<TutorProfile> pending, OnDecisionListener listener) {
        this.pending = pending;
        this.listener = listener;
    }

    public void updateData(List<TutorProfile> newPending) {
        this.pending = newPending;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PendingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_tutor, parent, false);
        return new PendingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PendingViewHolder holder, int position) {
        TutorProfile profile = pending.get(position);
        holder.tvName.setText(profile.getTutorName());
        holder.tvModules.setText(
                profile.getModuleCodes() != null ? String.join(", ", profile.getModuleCodes()) : "");

        holder.tvViewEvidence.setOnClickListener(v -> listener.onViewEvidence(profile));
        holder.btnVerify.setOnClickListener(v -> listener.onVerify(profile));
        holder.btnReject.setOnClickListener(v -> listener.onReject(profile));
    }

    @Override
    public int getItemCount() {
        return pending.size();
    }

    static class PendingViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvModules, tvViewEvidence;
        android.widget.Button btnVerify, btnReject;

        PendingViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvApplicantName);
            tvModules = itemView.findViewById(R.id.tvApplicantModules);
            tvViewEvidence = itemView.findViewById(R.id.tvViewEvidence);
            btnVerify = itemView.findViewById(R.id.btnVerify);
            btnReject = itemView.findViewById(R.id.btnReject);
        }
    }
}
