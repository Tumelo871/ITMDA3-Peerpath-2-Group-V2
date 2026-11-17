
package com.example.peerpath;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MarkAssessmentActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView message = new TextView(this);
        message.setText("Assessment marking screen");
        message.setTextSize(20);
        message.setPadding(30, 30, 30, 30);

        setContentView(message);
    }
}
