package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MySessionsActivity extends AppCompatActivity {
    private RecyclerView rv; private TextView empty;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_simple_list);TextView header=findViewById(R.id.tvHeader);header.setText("My Sessions");empty=findViewById(R.id.tvEmptyState);findViewById(R.id.btnBack).setOnClickListener(v->finish());findViewById(R.id.etSearchModule).setVisibility(View.GONE);findViewById(R.id.btnPrimaryAction).setVisibility(View.GONE);rv=findViewById(R.id.recyclerView);rv.setLayoutManager(new LinearLayoutManager(this));load();}
    private void load(){SupabaseApi.getMySessions(new SupabaseApi.JsonCallback(){public void onSuccess(JSONArray a){runOnUiThread(()->render(a));}public void onError(String m){runOnUiThread(()->empty.setText(m));}});}
    private void render(JSONArray a){List<JSONObject> rows=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)rows.add(x);}rv.setAdapter(new RecyclerView.Adapter<Holder>(){public Holder onCreateViewHolder(android.view.ViewGroup p,int v){android.widget.LinearLayout c=Ui.card(MySessionsActivity.this);c.setPadding(16,16,16,16);return new Holder(c);}public void onBindViewHolder(Holder h,int i){JSONObject s=rows.get(i);h.c.removeAllViews();h.c.addView(Ui.text(MySessionsActivity.this,s.optString("subject","Tutoring"),18,R.color.text_primary,true));Ui.add(h.c,Ui.text(MySessionsActivity.this,s.optString("session_date")+" at "+s.optString("session_time"),14,R.color.text_secondary,false),5);Ui.add(h.c,Ui.text(MySessionsActivity.this,"Status: "+s.optString("status"),14,R.color.peer_blue,true),5);h.c.setOnClickListener(v->{if("accepted".equals(s.optString("status"))){Intent in=new Intent(MySessionsActivity.this,ChatActivity.class);in.putExtra("sessionId",s.optString("id"));String other=SupabaseApi.userId().equals(s.optString("learner_id"))?s.optString("tutor_id"):s.optString("learner_id");in.putExtra("receiverId",other);startActivity(in);}});}public int getItemCount(){return rows.size();}});empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);if(rows.isEmpty())empty.setText("You have no sessions yet.");}
    static class Holder extends RecyclerView.ViewHolder{android.widget.LinearLayout c;Holder(android.widget.LinearLayout c){super(c);this.c=c;}}
}
