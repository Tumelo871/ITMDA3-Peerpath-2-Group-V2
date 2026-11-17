package com.example.peerpath;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class AssessmentDetailsActivity extends AppCompatActivity {

    private LinearLayout questionContainer;
    private ProgressBar progressBar;
    private Button btnSubmit;

    private String assignmentId;

    private final ArrayList<AnswerItem> answerItems =
            new ArrayList<>();

    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SupabaseApi.init(getApplicationContext());

        assignmentId = getIntent().getStringExtra("assignment_id");

        if (assignmentId == null || assignmentId.trim().isEmpty()) {
            Toast.makeText(this, "Assessment ID is missing.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildScreen();
        loadQuestions();
    }

    private void buildScreen() {

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF6F9FF);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));

        scroll.addView(root);

        TextView back = text("‹  Back to assignments", 15, 0xFF075CE5, true);
        back.setPadding(0, 0, 0, dp(16));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView heading = text("Assessment", 25, 0xFF082D68, true);
        root.addView(heading);

        TextView description = text(
                "Read each question carefully and submit your answers when finished.",
                14, 0xFF536783, false
        );

        description.setPadding(0, dp(6), 0, dp(18));
        root.addView(description);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.VISIBLE);
        root.addView(progressBar);

        questionContainer = new LinearLayout(this);
        questionContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(questionContainer);

        btnSubmit = new Button(this);
        btnSubmit.setText("Submit assessment");
        btnSubmit.setAllCaps(false);
        btnSubmit.setTextColor(0xFFFFFFFF);
        btnSubmit.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF075CE5)
        );
        btnSubmit.setOnClickListener(v -> submitAnswers());

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(-1, dp(54));

        buttonParams.setMargins(0, dp(20), 0, dp(10));
        root.addView(btnSubmit, buttonParams);

        setContentView(scroll);
    }

    private void loadQuestions() {

        progressBar.setVisibility(View.VISIBLE);
        btnSubmit.setEnabled(false);

        SupabaseApi.getAssessmentQuestions(
                assignmentId,
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray data) {

                        runOnUiThread(() -> {

                            if (isFinishing() || isDestroyed()) return;

                            progressBar.setVisibility(View.GONE);
                            questionContainer.removeAllViews();
                            answerItems.clear();

                            if (data == null || data.length() == 0) {
                                questionContainer.addView(
                                        text("No questions have been added to this assessment.",
                                                15, 0xFF536783, false)
                                );
                                return;
                            }

                            for (int i = 0; i < data.length(); i++) {

                                JSONObject question =
                                        data.optJSONObject(i);

                                if (question != null) {
                                    addQuestion(question);
                                }
                            }

                            btnSubmit.setEnabled(!answerItems.isEmpty());
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            if (isFinishing() || isDestroyed()) return;

                            progressBar.setVisibility(View.GONE);

                            showMessage(
                                    "Could not load questions: " + message
                            );
                        });
                    }
                }
        );
    }

    private void addQuestion(JSONObject question) {

        String questionId = question.optString("id", "");
        String questionText = question.optString(
                "question_text", "Question"
        );

        String type = question.optString(
                "question_type", "multiple_choice"
        );

        String marks = question.optString("marks", "1");

        boolean required = question.optBoolean(
                "is_required", true
        );

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundColor(0xFFFFFFFF);
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);

        cardParams.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(cardParams);

        int number = question.optInt(
                "question_number",
                answerItems.size() + 1
        );

        card.addView(text(
                "Question " + number + "  •  " + marks + " marks",
                14, 0xFF075CE5, true
        ));

        TextView prompt = text(
                questionText,
                17, 0xFF082D68, true
        );

        prompt.setPadding(0, dp(10), 0, dp(12));
        card.addView(prompt);

        AnswerItem item = new AnswerItem();
        item.questionId = questionId;
        item.questionType = type;
        item.required = required;

        if ("multiple_choice".equalsIgnoreCase(type)) {

            RadioGroup group = new RadioGroup(this);
            group.setOrientation(LinearLayout.VERTICAL);

            JSONArray options = question.optJSONArray(
                    "peerpath_assignment_options"
            );

            if (options != null) {

                for (int i = 0; i < options.length(); i++) {

                    JSONObject option = options.optJSONObject(i);

                    if (option == null) continue;

                    String optionId = option.optString("id", "");
                    String label = option.optString(
                            "option_label", ""
                    );

                    String optionText = option.optString(
                            "option_text", ""
                    );

                    RadioButton radio = new RadioButton(this);

                    radio.setText(
                            label + ". " + optionText
                    );

                    radio.setTextColor(0xFF344054);
                    radio.setTextSize(15);
                    radio.setTag(optionId);
                    radio.setPadding(0, dp(5), 0, dp(5));

                    group.addView(radio);
                }
            }

            item.radioGroup = group;
            card.addView(group);

        } else if ("written".equalsIgnoreCase(type)) {

            EditText answer = new EditText(this);

            answer.setHint("Type your answer here...");
            answer.setMinLines(3);
            answer.setGravity(Gravity.TOP);
            answer.setTextSize(15);
            answer.setPadding(dp(10), dp(10), dp(10), dp(10));
            answer.setBackgroundResource(
                    android.R.drawable.edit_text
            );

            item.textAnswer = answer;
            card.addView(answer);

        } else {

            card.addView(text(
                    "This question type is not supported yet.",
                    14, 0xFFB42318, false
            ));
        }

        answerItems.add(item);
        questionContainer.addView(card);
    }

    private void submitAnswers() {

        if (loading) return;

        JSONArray answers = new JSONArray();

        for (AnswerItem item : answerItems) {

            if (item.questionId.isEmpty()) {
                showMessage("A question is missing its ID.");
                return;
            }

            JSONObject answer = new JSONObject();

            try {

                answer.put("question_id", item.questionId);

                if ("multiple_choice".equalsIgnoreCase(
                        item.questionType)) {

                    if (item.radioGroup == null) {
                        showMessage("A multiple-choice question is incomplete.");
                        return;
                    }

                    int selectedId =
                            item.radioGroup.getCheckedRadioButtonId();

                    if (selectedId == -1) {

                        if (item.required) {
                            showMessage("Please answer all required questions.");
                            return;
                        }

                        continue;
                    }

                    RadioButton selected =
                            item.radioGroup.findViewById(selectedId);

                    String optionId = String.valueOf(
                            selected.getTag()
                    );

                    answer.put("selected_option_id", optionId);
                    answer.put("answer_text", "");

                } else if ("written".equalsIgnoreCase(
                        item.questionType)) {

                    String value = item.textAnswer.getText()
                            .toString().trim();

                    if (value.isEmpty() && item.required) {
                        showMessage("Please answer all required questions.");
                        item.textAnswer.requestFocus();
                        return;
                    }

                    if (value.isEmpty()) continue;

                    answer.put("answer_text", value);
                    answer.put("selected_option_id", JSONObject.NULL);

                } else {
                    continue;
                }

                answers.put(answer);

            } catch (Exception e) {

                showMessage("Could not prepare your answers.");
                return;
            }
        }

        if (answers.length() == 0) {
            showMessage("Please answer at least one question.");
            return;
        }

        JSONObject payload = new JSONObject();

        try {
            payload.put("assignment_id", assignmentId);
            payload.put("answers", answers);
        } catch (Exception e) {
            showMessage("Could not prepare your submission.");
            return;
        }

        setLoading(true);

        SupabaseApi.submitOnlineAssessment(
                payload,
                new SupabaseApi.ObjectCallback() {

                    @Override
                    public void onSuccess(JSONObject result) {

                        runOnUiThread(() -> {

                            if (isFinishing() || isDestroyed()) return;

                            setLoading(false);

                            showMessage(
                                    "Your assessment has been submitted successfully!"
                            );

                            setResult(RESULT_OK);
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            if (isFinishing() || isDestroyed()) return;

                            setLoading(false);

                            showMessage(
                                    "Submission failed: " + message
                            );
                        });
                    }
                }
        );
    }

    private void setLoading(boolean value) {

        loading = value;
        btnSubmit.setEnabled(!value);

        btnSubmit.setText(
                value ? "Submitting..." : "Submit assessment"
        );
    }

    private TextView text(
            String value,
            float size,
            int color,
            boolean bold) {

        TextView view = new TextView(this);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );
        }

        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static class AnswerItem {
        String questionId;
        String questionType;
        boolean required;

        RadioGroup radioGroup;
        EditText textAnswer;
    }
}
