package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {
    private TextInputEditText etName, etStudentNumber, etFaculty, etEmail, etPassword, etSubject, etBio, etExperience;
    private RadioGroup rgAccountType;
    private View tutorFields;
    private MaterialButton btnRegister;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_register);
        etName=findViewById(R.id.etName); etStudentNumber=findViewById(R.id.etStudentNumber); etFaculty=findViewById(R.id.etFaculty);
        etEmail=findViewById(R.id.etEmail); etPassword=findViewById(R.id.etPassword); etSubject=findViewById(R.id.etSubject);
        etBio=findViewById(R.id.etBio); etExperience=findViewById(R.id.etExperience); tutorFields=findViewById(R.id.layoutTutorFields);
        rgAccountType=findViewById(R.id.rgAccountType); btnRegister=findViewById(R.id.btnRegister);
        rgAccountType.setOnCheckedChangeListener((g,id)-> tutorFields.setVisibility(id==R.id.rbTutor?View.VISIBLE:View.GONE));
        btnRegister.setOnClickListener(v->registerUser());
        findViewById(R.id.tvLogin).setOnClickListener(v->{ startActivity(new Intent(this,LoginActivity.class)); finish(); });
    }

    private void registerUser() {
        String name=get(etName), student=get(etStudentNumber), faculty=get(etFaculty), email=get(etEmail).toLowerCase(Locale.ROOT), password=get(etPassword);
        boolean tutor=rgAccountType.getCheckedRadioButtonId()==R.id.rbTutor;
        if(TextUtils.isEmpty(name)){etName.setError("Enter your full name");return;}
        if(TextUtils.isEmpty(student)){etStudentNumber.setError("Enter your student number");return;}
        if(TextUtils.isEmpty(faculty)){etFaculty.setError("Enter your faculty");return;}
        if(!Config.isVossieEmail(email)){etEmail.setError("Only @vossie.net accounts are allowed");return;}
        if(password.length()<6){etPassword.setError("Password must be at least 6 characters");return;}
        if(tutor && TextUtils.isEmpty(get(etSubject))){etSubject.setError("Enter the module/subject you tutor");return;}
        btnRegister.setEnabled(false); btnRegister.setText("Creating Account...");
        JSONObject meta=new JSONObject();
        try{
            meta.put("full_name",name); meta.put("student_number",student); meta.put("faculty",faculty);
            meta.put("role",tutor?"tutor":"learner"); meta.put("tutor_subject",get(etSubject)); meta.put("tutor_bio",get(etBio)); meta.put("tutor_experience",get(etExperience)); meta.put("module_codes",get(etSubject));
        }catch(Exception e){showError(e.getMessage());return;}
        SupabaseApi.signUp(email,password,meta,new SupabaseApi.ObjectCallback(){
            @Override public void onSuccess(JSONObject data){runOnUiThread(()->{
                btnRegister.setEnabled(true); btnRegister.setText("Create Account");
                Toast.makeText(RegisterActivity.this,"Registration successful. Check your Vossie email if confirmation is required, then sign in.",Toast.LENGTH_LONG).show();
                startActivity(new Intent(RegisterActivity.this,LoginActivity.class)); finish();
            });}
            @Override public void onError(String message){runOnUiThread(()->showError(message));}
        });
    }
    private void showError(String m){btnRegister.setEnabled(true);btnRegister.setText("Create Account");Toast.makeText(this,m==null?"Registration failed":m,Toast.LENGTH_LONG).show();}
    private String get(TextInputEditText f){return f.getText()==null?"":f.getText().toString().trim();}
}
