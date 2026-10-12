package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONObject;

public class TutorProfileActivity extends AppCompatActivity {
    private String tutorId; private TextView name,subject,rating,level,bio,experience,noReviews; private LinearLayout reviews;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_tutor_profile);tutorId=getIntent().getStringExtra("tutorId");name=findViewById(R.id.tvTutorName);subject=findViewById(R.id.tvTutorSubject);rating=findViewById(R.id.tvTutorRating);level=findViewById(R.id.tvTutorLevel);bio=findViewById(R.id.tvTutorBio);experience=findViewById(R.id.tvTutorExperience);noReviews=findViewById(R.id.tvNoReviews);reviews=findViewById(R.id.reviewContainer);findViewById(R.id.btnBack).setOnClickListener(v->finish());findViewById(R.id.btnBookSession).setOnClickListener(v->{Intent i=new Intent(this,SessionRequestActivity.class);i.putExtra("tutorId",tutorId);i.putExtra("subject",subject.getText().toString());startActivity(i);});load();}
    private void load(){SupabaseApi.getProfile(tutorId,new SupabaseApi.ObjectCallback(){@Override public void onSuccess(JSONObject p){runOnUiThread(()->{name.setText(p.optString("full_name","PeerPath Tutor"));subject.setText(p.optString("tutor_subject",p.optString("module_codes","Tutor")));rating.setText("⭐ "+String.format(java.util.Locale.US,"%.1f",p.optDouble("rating_average",0))+" ("+p.optInt("rating_count",0)+" reviews) · ✓ Verified");level.setText("Level "+Math.max(1,p.optInt("level",1)));bio.setText(p.optString("tutor_bio","No bio provided."));experience.setText(p.optString("tutor_experience","Experience not provided."));noReviews.setText(p.optInt("rating_count",0)==0?"No reviews yet":"Reviews loaded from Supabase");});}@Override public void onError(String m){runOnUiThread(()->Toast.makeText(TutorProfileActivity.this,m,Toast.LENGTH_LONG).show());}});}
}
