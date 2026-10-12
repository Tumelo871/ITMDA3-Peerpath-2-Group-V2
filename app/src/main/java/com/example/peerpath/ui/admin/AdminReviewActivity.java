package com.example.peerpath.ui.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.R;
import com.example.peerpath.Ui;
import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

public class AdminReviewActivity extends AppCompatActivity {
    private LinearLayout list; private android.widget.TextView empty;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_admin_review);empty=findViewById(R.id.tvEmptyState);androidx.recyclerview.widget.RecyclerView rv=findViewById(R.id.recyclerView);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<Holder>(){public Holder onCreateViewHolder(android.view.ViewGroup p,int v){LinearLayout c=Ui.card(AdminReviewActivity.this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(16,16,16,16);return new Holder(c);}public void onBindViewHolder(Holder h,int i){JSONObject a=rows.optJSONObject(i);h.c.removeAllViews();if(a==null)return;String tutor=a.optString("tutor_id");h.c.addView(Ui.text(AdminReviewActivity.this,"Tutor application",18,R.color.text_primary,true));Ui.add(h.c,Ui.text(AdminReviewActivity.this,"Modules: "+a.optString("module_codes"),14,R.color.text_secondary,false),6);Ui.add(h.c,Ui.text(AdminReviewActivity.this,"Status: "+a.optString("status"),14,R.color.peer_blue,true),6);com.google.android.material.button.MaterialButton approve=new com.google.android.material.button.MaterialButton(AdminReviewActivity.this);approve.setText("Verify tutor");h.c.addView(approve);approve.setOnClickListener(v->review(a,true));com.google.android.material.button.MaterialButton reject=new com.google.android.material.button.MaterialButton(AdminReviewActivity.this);reject.setText("Reject");h.c.addView(reject);reject.setOnClickListener(v->review(a,false));}public int getItemCount(){return rows.length();}});findViewById(R.id.btnBack).setOnClickListener(v->finish());load();}
    private JSONArray rows=new JSONArray(); private androidx.recyclerview.widget.RecyclerView.Adapter<?> adapter;
    private void load(){SupabaseApi.getPendingApplications(new SupabaseApi.JsonCallback(){public void onSuccess(JSONArray a){runOnUiThread(()->{rows=a;if(adapter!=null)adapter.notifyDataSetChanged();else {androidx.recyclerview.widget.RecyclerView rv=findViewById(R.id.recyclerView);adapter=rv.getAdapter();if(adapter!=null)adapter.notifyDataSetChanged();}empty.setVisibility(a.length()==0?View.VISIBLE:View.GONE);empty.setText(a.length()==0?"No pending tutor applications.":"");});}public void onError(String m){runOnUiThread(()->Toast.makeText(AdminReviewActivity.this,m,Toast.LENGTH_LONG).show());}});}
    private void review(JSONObject a,boolean approve){SupabaseApi.reviewApplication(a.optString("id"),a.optString("tutor_id"),approve,approve?"Approved by administrator":"Application rejected",new SupabaseApi.EmptyCallback(){public void onSuccess(){runOnUiThread(()->{Toast.makeText(AdminReviewActivity.this,approve?"Tutor verified.":"Tutor rejected.",Toast.LENGTH_LONG).show();load();});}public void onError(String m){runOnUiThread(()->Toast.makeText(AdminReviewActivity.this,m,Toast.LENGTH_LONG).show());}});}
    static class Holder extends androidx.recyclerview.widget.RecyclerView.ViewHolder{LinearLayout c;Holder(LinearLayout c){super(c);this.c=c;}}
}
