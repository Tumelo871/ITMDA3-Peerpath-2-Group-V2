package com.example.peerpath;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import org.json.JSONArray;
import org.json.JSONObject;

public class TutorSessionsActivity extends AppCompatActivity {
    private LinearLayout list; private TextView empty;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_tutor_sessions);list=findViewById(R.id.tutorSessionsContainer);empty=findViewById(R.id.tvEmptyTutorSessions);findViewById(R.id.btnBack).setOnClickListener(v->finish());load();}
    private void load(){SupabaseApi.getAcceptedSessions(new SupabaseApi.JsonCallback(){@Override public void onSuccess(JSONArray a){runOnUiThread(()->render(a));}@Override public void onError(String m){runOnUiThread(()->{empty.setVisibility(View.VISIBLE);empty.setText(m);});}});}
    private void render(JSONArray a){list.removeAllViews();empty.setVisibility(a.length()==0?View.VISIBLE:View.GONE);for(int i=0;i<a.length();i++){JSONObject s=a.optJSONObject(i);if(s==null)continue;LinearLayout c=Ui.card(this);c.setPadding(16,16,16,16);c.addView(Ui.text(this,s.optString("subject","Tutoring"),18,R.color.text_primary,true));Ui.add(c,Ui.text(this,s.optString("session_date")+" at "+s.optString("session_time"),14,R.color.text_secondary,false),5);com.google.android.material.button.MaterialButton complete=new com.google.android.material.button.MaterialButton(this);complete.setText("Mark Complete");c.addView(complete);complete.setOnClickListener(v->SupabaseApi.updateSessionStatus(s.optString("id"),"completed",new SupabaseApi.EmptyCallback(){public void onSuccess(){runOnUiThread(()->load());}public void onError(String m){runOnUiThread(()->android.widget.Toast.makeText(TutorSessionsActivity.this,m,android.widget.Toast.LENGTH_LONG).show());}}));list.addView(c);}}
}
