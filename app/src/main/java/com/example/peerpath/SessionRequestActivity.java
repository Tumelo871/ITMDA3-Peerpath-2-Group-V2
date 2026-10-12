package com.example.peerpath;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;

public class SessionRequestActivity extends AppCompatActivity {
    private EditText etSubject,etDate,etTime,etMessage; private MaterialButton btnRequest; private String tutorId;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_session_request);tutorId=getIntent().getStringExtra("tutorId");etSubject=findViewById(R.id.etSubject);etDate=findViewById(R.id.etDate);etTime=findViewById(R.id.etTime);etMessage=findViewById(R.id.etMessage);btnRequest=findViewById(R.id.btnRequest);findViewById(R.id.btnBack).setOnClickListener(v->finish());String subject=getIntent().getStringExtra("subject");if(subject!=null)etSubject.setText(subject);btnRequest.setOnClickListener(v->submit());}
    private void submit(){String subject=etSubject.getText().toString().trim(),date=etDate.getText().toString().trim(),time=etTime.getText().toString().trim(),msg=etMessage.getText().toString().trim();if(TextUtils.isEmpty(tutorId)){Toast.makeText(this,"Tutor could not be identified.",Toast.LENGTH_LONG).show();return;}if(subject.isEmpty()||date.isEmpty()||time.isEmpty()){Toast.makeText(this,"Please enter the subject, date and time.",Toast.LENGTH_LONG).show();return;}btnRequest.setEnabled(false);SupabaseApi.createSession(tutorId,subject,date,time,msg,new SupabaseApi.EmptyCallback(){@Override public void onSuccess(){runOnUiThread(()->{Toast.makeText(SessionRequestActivity.this,"Session request sent successfully.",Toast.LENGTH_LONG).show();finish();});}@Override public void onError(String m){runOnUiThread(()->{btnRequest.setEnabled(true);Toast.makeText(SessionRequestActivity.this,"Request failed: "+m,Toast.LENGTH_LONG).show();});}});}
}
