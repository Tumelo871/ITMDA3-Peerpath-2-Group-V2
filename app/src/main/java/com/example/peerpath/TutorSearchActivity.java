package com.example.peerpath;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class TutorSearchActivity extends AppCompatActivity {
    private EditText etSearch; private LinearLayout tutorContainer; private TextView tvStatus;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_tutor_search);etSearch=findViewById(R.id.etSearch);tutorContainer=findViewById(R.id.tutorContainer);tvStatus=findViewById(R.id.tvSearchStatus);findViewById(R.id.btnBack).setOnClickListener(v->finish());etSearch.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){load(s.toString());}public void afterTextChanged(Editable e){}});load("");}
    private void load(String q){tvStatus.setText("Loading verified tutors...");SupabaseApi.getVerifiedTutors(q,new SupabaseApi.JsonCallback(){@Override public void onSuccess(JSONArray a){runOnUiThread(()->render(a));}@Override public void onError(String m){runOnUiThread(()->tvStatus.setText("Could not load tutors: "+m));}});}
    private void render(JSONArray a){tutorContainer.removeAllViews();if(a.length()==0){tvStatus.setText("No verified tutors found. Try another module or tutor name.");return;}for(int i=0;i<a.length();i++){JSONObject t=a.optJSONObject(i);if(t==null)continue;String uid=t.optString("id");if(uid.equals(SupabaseApi.userId()))continue;String name=t.optString("full_name","PeerPath Tutor"),modules=t.optString("module_codes",t.optString("tutor_subject","Modules not listed"));LinearLayout card=Ui.card(this);card.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14));card.addView(Ui.text(this,name,19,R.color.text_primary,true));Ui.add(card,Ui.text(this,"📚 "+modules,14,R.color.text_secondary,false),6);Ui.add(card,Ui.text(this,"⭐ "+String.format(Locale.US,"%.1f",t.optDouble("rating_average",0))+"   ·   ✓ Verified tutor",14,R.color.peer_blue,true),6);Ui.addToList(tutorContainer,card);card.setOnClickListener(v->{Intent in=new Intent(this,TutorProfileActivity.class);in.putExtra("tutorId",uid);startActivity(in);});}tvStatus.setText(a.length()+" verified tutor"+(a.length()==1?"":"s"));}
}
