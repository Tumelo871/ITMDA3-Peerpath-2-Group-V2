package com.example.peerpath.ui.tutors;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.Config;
import com.example.peerpath.databinding.ActivityBecomeTutorBinding;
import com.example.peerpath.supabase.SupabaseApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BecomeTutorActivity extends AppCompatActivity {
    private ActivityBecomeTutorBinding binding; private Uri selectedEvidenceUri;
    private final ActivityResultLauncher<String[]> picker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri!=null){selectedEvidenceUri=uri;binding.tvSelectedFile.setText(uri.getLastPathSegment()==null?"Evidence selected":uri.getLastPathSegment());}});
    @Override protected void onCreate(Bundle b){super.onCreate(b);SupabaseApi.init(getApplicationContext());binding=ActivityBecomeTutorBinding.inflate(getLayoutInflater());setContentView(binding.getRoot());binding.btnBack.setOnClickListener(v->finish());binding.btnPickEvidence.setOnClickListener(v->picker.launch(new String[]{"application/pdf","image/*"}));binding.btnSubmit.setOnClickListener(v->submit());}
    private void submit(){String raw=binding.etModuleCodes.getText().toString().trim();if(raw.isEmpty()){error("Enter at least one module code.");return;}if(selectedEvidenceUri==null){error("Attach your academic evidence.");return;}String email=SupabaseApi.userEmail();if(!Config.isVossieEmail(email)){error("Only Vossie accounts may apply.");return;}List<String> codes=new ArrayList<>();for(String x:raw.split(",")){if(!x.trim().isEmpty()&&!codes.contains(x.trim()))codes.add(x.trim());}setLoading(true);String path=SupabaseApi.userId()+"/tutor-evidence/"+UUID.randomUUID();SupabaseApi.uploadFile(getContentResolver(),selectedEvidenceUri,path,new SupabaseApi.EmptyCallback(){@Override public void onSuccess(){SupabaseApi.submitTutorApplication(String.join(", ",codes),path,new SupabaseApi.EmptyCallback(){@Override public void onSuccess(){runOnUiThread(()->{setLoading(false);Toast.makeText(BecomeTutorActivity.this,"Application submitted. It is now pending admin verification.",Toast.LENGTH_LONG).show();finish();});}@Override public void onError(String m){runOnUiThread(()->fail(m));}});}@Override public void onError(String m){runOnUiThread(()->fail(m));}});}
    private void setLoading(boolean x){binding.progressSubmit.setVisibility(x?View.VISIBLE:View.GONE);binding.btnSubmit.setEnabled(!x);binding.btnPickEvidence.setEnabled(!x);}
    private void fail(String m){setLoading(false);error(m==null?"Application failed.":m);}
    private void error(String m){binding.tvError.setText(m);binding.tvError.setVisibility(View.VISIBLE);}
}
