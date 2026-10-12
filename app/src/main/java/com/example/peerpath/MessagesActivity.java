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

public class MessagesActivity extends AppCompatActivity {
    private LinearLayout list; private TextView empty; private android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_messages);list=findViewById(R.id.messageListContainer);empty=findViewById(R.id.tvEmptyMessages);findViewById(R.id.btnBack).setOnClickListener(v->finish());load();}
    @Override protected void onResume(){super.onResume();load();}
    private void load(){empty.setVisibility(View.GONE);list.removeAllViews();SupabaseApi.getAcceptedSessions(new SupabaseApi.JsonCallback(){@Override public void onSuccess(JSONArray a){runOnUiThread(()->render(a));}@Override public void onError(String m){runOnUiThread(()->{empty.setVisibility(View.VISIBLE);empty.setText("Messages unavailable: "+m);});}});}
    private void render(JSONArray a){if(a.length()==0){empty.setVisibility(View.VISIBLE);empty.setText("No conversations yet. An accepted tutoring session will appear here.");return;}for(int i=0;i<a.length();i++){JSONObject s=a.optJSONObject(i);if(s==null)continue;String sid=s.optString("id"),other=SupabaseApi.userId().equals(s.optString("learner_id"))?s.optString("tutor_id"):s.optString("learner_id"),subject=s.optString("subject","Tutoring");addConversation(sid,other,subject);}}
    private void addConversation(String sid,String other,String subject){SupabaseApi.getProfile(other,new SupabaseApi.ObjectCallback(){@Override public void onSuccess(JSONObject p){runOnUiThread(()->addCard(sid,other,subject,p.optString("full_name","PeerPath User")));}@Override public void onError(String m){runOnUiThread(()->addCard(sid,other,subject,"PeerPath User"));}});}
    private void addCard(String sid,String other,String subject,String person){LinearLayout card=Ui.card(this);card.setPadding(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16));card.addView(Ui.text(this,"💬 "+person,18,R.color.text_primary,true));Ui.add(card,Ui.text(this,subject,14,R.color.text_secondary,false),6);Ui.add(card,Ui.text(this,"Open conversation",13,R.color.peer_blue,true),6);list.addView(card);card.setOnClickListener(v->{Intent i=new Intent(this,ChatActivity.class);i.putExtra("sessionId",sid);i.putExtra("receiverId",other);i.putExtra("title",person);startActivity(i);});}
}
