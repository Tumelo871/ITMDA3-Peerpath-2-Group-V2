package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

public class TutorRequestsActivity extends AppCompatActivity {
    private LinearLayout list; private TextView empty;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_tutor_requests);list=findViewById(R.id.requestContainer);empty=findViewById(R.id.tvEmptyRequests);findViewById(R.id.btnBack).setOnClickListener(v->finish());load();}
    private void load(){SupabaseApi.getPendingTutorRequests(new SupabaseApi.JsonCallback(){@Override public void onSuccess(JSONArray a){runOnUiThread(()->render(a));}@Override public void onError(String m){runOnUiThread(()->{empty.setVisibility(View.VISIBLE);empty.setText("Could not load requests: "+m);});}});}
    private void render(JSONArray a){list.removeAllViews();empty.setVisibility(a.length()==0?View.VISIBLE:View.GONE);empty.setText("No pending tutoring requests.");for(int i=0;i<a.length();i++){JSONObject s=a.optJSONObject(i);if(s==null)continue;String id=s.optString("id"),learner=s.optString("learner_id"),subject=s.optString("subject","Tutoring"),date=s.optString("session_date"),time=s.optString("session_time");LinearLayout c=Ui.card(this);c.setPadding(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16));c.addView(Ui.text(this,"📚 "+subject,18,R.color.text_primary,true));Ui.add(c,Ui.text(this,date+" at "+time,14,R.color.text_secondary,false),5);Ui.add(c,Ui.text(this,s.optString("learner_message","No message"),14,R.color.text_secondary,false),5);com.google.android.material.button.MaterialButton accept=new com.google.android.material.button.MaterialButton(this);accept.setText("Accept");c.addView(accept);accept.setOnClickListener(v->update(id,"accepted"));com.google.android.material.button.MaterialButton reject=new com.google.android.material.button.MaterialButton(this);reject.setText("Reject");c.addView(reject);reject.setOnClickListener(v->update(id,"rejected"));list.addView(c);}}
    private void update(String id,String status){SupabaseApi.updateSessionStatus(id,status,new SupabaseApi.EmptyCallback(){@Override public void onSuccess(){runOnUiThread(()->load());}@Override public void onError(String m){runOnUiThread(()->android.widget.Toast.makeText(TutorRequestsActivity.this,m,android.widget.Toast.LENGTH_LONG).show());}});}
}
