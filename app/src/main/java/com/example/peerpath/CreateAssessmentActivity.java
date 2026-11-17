
package com.example.peerpath;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

public class CreateAssessmentActivity extends RoleDashboardBaseActivity {

    private Spinner spinnerSession;
    private Spinner spinnerAssessmentType;

    private EditText etTitle;
    private EditText etSubject;
    private EditText etInstructions;
    private EditText etDueDate;

    private CheckBox cbOnline;
    private CheckBox cbFile;

    private LinearLayout questionContainer;
    private TextView tvQuestionSummary;
    private Button btnPublish;

    private final ArrayList<JSONObject> sessions = new ArrayList<>();
    private final ArrayList<QuestionCard> questions = new ArrayList<>();
    private final ArrayList<String> sessionLabels = new ArrayList<>();

    private ArrayAdapter<String> sessionAdapter;

    private boolean loading = false;

    // ---------------------------------------------------------
    // ACTIVITY CONFIGURATION
    // ---------------------------------------------------------

    @Override
    protected int layoutId() {
        return R.layout.activity_create_assessment;
    }

    @Override
    protected String requiredRole() {
        return "tutor";
    }

    @Override
    protected boolean requiresVerifiedTutor() {
        return true;
    }

    @Override
    protected void wireRoleActions() {

        bindViews();
        setupAssessmentTypes();
        setupDatePicker();

        findViewById(R.id.btnAddQuestion)
                .setOnClickListener(v -> addQuestion());

        btnPublish.setOnClickListener(v -> publishAssessment());

        loadAcceptedSessions();
    }

    // ---------------------------------------------------------
    // INITIALISE VIEWS
    // ---------------------------------------------------------

    private void bindViews() {

        spinnerSession = findViewById(R.id.spinnerSession);
        spinnerAssessmentType = findViewById(R.id.spinnerAssessmentType);

        etTitle = findViewById(R.id.etAssessmentTitle);
        etSubject = findViewById(R.id.etAssessmentSubject);
        etInstructions = findViewById(R.id.etAssessmentInstructions);
        etDueDate = findViewById(R.id.etAssessmentDueDate);

        cbOnline = findViewById(R.id.cbOnlineAnswers);
        cbFile = findViewById(R.id.cbFileSubmission);

        questionContainer = findViewById(R.id.questionContainer);
        tvQuestionSummary = findViewById(R.id.tvQuestionSummary);
        btnPublish = findViewById(R.id.btnPublishAssessment);
    }

    // ---------------------------------------------------------
    // ASSESSMENT TYPES
    // ---------------------------------------------------------

    private void setupAssessmentTypes() {

        String[] types = {
                "Quiz / Multiple-choice test",
                "Written assignment",
                "Document assignment",
                "Mixed assessment"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                types
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerAssessmentType.setAdapter(adapter);

        spinnerAssessmentType.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        boolean documentOnly = position == 2;

                        findViewById(R.id.questionBuilderSection)
                                .setVisibility(
                                        documentOnly
                                                ? View.GONE
                                                : View.VISIBLE
                                );

                        if (documentOnly) {

                            questions.clear();
                            questionContainer.removeAllViews();
                            updateQuestionSummary();

                        } else if (questions.isEmpty()) {

                            addQuestion();
                        }
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // DATE PICKER
    // ---------------------------------------------------------

    private void setupDatePicker() {

        etDueDate.setOnClickListener(v -> {

            LocalDate today = LocalDate.now();

            DatePickerDialog dialog = new DatePickerDialog(
                    this,

                    (picker, year, month, day) -> {

                        String selectedDate = String.format(
                                Locale.ROOT,
                                "%04d-%02d-%02d",
                                year,
                                month + 1,
                                day
                        );

                        etDueDate.setText(selectedDate);
                    },

                    today.getYear(),
                    today.getMonthValue() - 1,
                    today.getDayOfMonth()
            );

            dialog.getDatePicker().setMinDate(
                    System.currentTimeMillis() - 1000
            );

            dialog.show();
        });
    }

    // ---------------------------------------------------------
    // LOAD ACCEPTED SESSIONS AND REAL LEARNER NAMES
    // ---------------------------------------------------------

    private void loadAcceptedSessions() {

        btnPublish.setEnabled(false);

        sessionLabels.clear();
        sessions.clear();

        SupabaseApi.getTutorAcceptedSessions(
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray data) {

                        runOnUiThread(() -> {

                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            sessions.clear();
                            sessionLabels.clear();

                            for (int i = 0; i < data.length(); i++) {

                                JSONObject session =
                                        data.optJSONObject(i);

                                if (session == null) {
                                    continue;
                                }

                                String sessionId =
                                        session.optString("id", "");

                                String learnerId =
                                        session.optString("learner_id", "");

                                if (sessionId.trim().isEmpty()
                                        || learnerId.trim().isEmpty()) {
                                    continue;
                                }

                                sessions.add(session);

                                String subject = session.optString(
                                        "subject",
                                        "Tutoring session"
                                );

                                String date = session.optString(
                                        "session_date",
                                        ""
                                );

                                String time = session.optString(
                                        "session_time",
                                        ""
                                );

                                sessionLabels.add(
                                        subject + " | "
                                                + date + " "
                                                + time
                                                + " | Loading learner..."
                                );
                            }

                            sessionAdapter = new ArrayAdapter<>(
                                    CreateAssessmentActivity.this,
                                    android.R.layout.simple_spinner_item,
                                    sessionLabels
                            );

                            sessionAdapter.setDropDownViewResource(
                                    android.R.layout.simple_spinner_dropdown_item
                            );

                            spinnerSession.setAdapter(sessionAdapter);

                            btnPublish.setEnabled(!sessions.isEmpty());

                            if (sessions.isEmpty()) {

                                showMessage(
                                        "No accepted tutoring sessions were found."
                                );

                                return;
                            }

                            // Retrieve each learner's actual name.
                            loadLearnerNames();
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            btnPublish.setEnabled(false);

                            showMessage(
                                    "Could not load tutoring sessions: "
                                            + message
                            );
                        });
                    }
                }
        );
    }

    private void loadLearnerNames() {

        for (int i = 0; i < sessions.size(); i++) {

            final int position = i;

            JSONObject session = sessions.get(i);

            String sessionId = session.optString("id", "");

            SupabaseApi.getTutorRequesterName(
                    sessionId,

                    new SupabaseApi.JsonCallback() {

                        @Override
                        public void onSuccess(JSONArray result) {

                            String learnerName = "Learner name unavailable";

                            if (result != null && result.length() > 0) {

                                JSONObject learner =
                                        result.optJSONObject(0);

                                if (learner != null) {

                                    learnerName = learner.optString(
                                            "full_name",
                                            "Learner name unavailable"
                                    );
                                }
                            }

                            final String finalName =
                                    learnerName.trim().isEmpty()
                                            ? "Learner name unavailable"
                                            : learnerName.trim();

                            runOnUiThread(() -> {

                                if (isFinishing()
                                        || isDestroyed()
                                        || sessionAdapter == null) {
                                    return;
                                }

                                updateSessionLabel(
                                        position,
                                        finalName
                                );
                            });
                        }

                        @Override
                        public void onError(String message) {

                            android.util.Log.e(
                                    "PeerPathAssessment",
                                    "Learner name lookup failed: " + message
                            );

                            runOnUiThread(() -> {

                                if (isFinishing()
                                        || isDestroyed()
                                        || sessionAdapter == null) {
                                    return;
                                }

                                updateSessionLabel(
                                        position,
                                        "Learner name unavailable"
                                );
                            });
                        }
                    }
            );
        }
    }

    private void updateSessionLabel(
            int position,
            String learnerName) {

        if (position < 0 || position >= sessions.size()) {
            return;
        }

        JSONObject session = sessions.get(position);

        String subject = session.optString(
                "subject",
                "Tutoring session"
        );

        String date = session.optString(
                "session_date",
                ""
        );

        String time = session.optString(
                "session_time",
                ""
        );

        String label = subject + " | "
                + date + " "
                + time + " | "
                + learnerName;

        sessionLabels.set(position, label);

        sessionAdapter.notifyDataSetChanged();
    }

    // ---------------------------------------------------------
    // QUESTION BUILDER
    // ---------------------------------------------------------

    private void addQuestion() {

        QuestionCard question = new QuestionCard(
                questions.size() + 1
        );

        questions.add(question);

        questionContainer.addView(question.view);

        updateQuestionNumbers();
        updateQuestionSummary();
    }

    private void removeQuestion(QuestionCard question) {

        questions.remove(question);

        questionContainer.removeView(question.view);

        updateQuestionNumbers();
        updateQuestionSummary();
    }

    private void updateQuestionNumbers() {

        for (int i = 0; i < questions.size(); i++) {

            questions.get(i).number = i + 1;

            questions.get(i).title.setText(
                    "Question " + (i + 1)
            );
        }
    }

    private void updateQuestionSummary() {

        double total = 0;

        for (QuestionCard question : questions) {
            total += question.getMarks();
        }

        tvQuestionSummary.setText(
                questions.size()
                        + " questions | Total: "
                        + formatMarks(total)
                        + " marks"
        );
    }

    private String formatMarks(double marks) {

        if (marks == Math.floor(marks)) {
            return String.valueOf((int) marks);
        }

        return String.valueOf(marks);
    }

    // ---------------------------------------------------------
    // PUBLISH ASSESSMENT
    // ---------------------------------------------------------

    private void publishAssessment() {

        if (loading) {
            return;
        }

        if (sessions.isEmpty()
                || spinnerSession.getSelectedItemPosition() < 0) {

            showMessage("Select a learner first.");
            return;
        }

        String title = etTitle.getText().toString().trim();
        String subject = etSubject.getText().toString().trim();
        String instructions =
                etInstructions.getText().toString().trim();

        String dueDate = etDueDate.getText().toString().trim();

        if (title.isEmpty()
                || subject.isEmpty()
                || instructions.isEmpty()
                || dueDate.isEmpty()) {

            showMessage(
                    "Complete the title, subject, instructions and deadline."
            );

            return;
        }

        if (!cbOnline.isChecked() && !cbFile.isChecked()) {

            showMessage(
                    "Choose at least one submission method."
            );

            return;
        }

        LocalDate date;

        try {

            date = LocalDate.parse(dueDate);

        } catch (Exception e) {

            showMessage("Choose a valid deadline.");
            return;
        }

        if (date.isBefore(LocalDate.now())) {

            showMessage("The deadline cannot be in the past.");
            return;
        }

        int typePosition =
                spinnerAssessmentType.getSelectedItemPosition();

        String assessmentType;

        switch (typePosition) {

            case 1:
                assessmentType = "written";
                break;

            case 2:
                assessmentType = "document";
                break;

            case 3:
                assessmentType = "mixed";
                break;

            default:
                assessmentType = "multiple_choice";
                break;
        }

        boolean documentOnly =
                "document".equals(assessmentType);

        JSONArray questionArray = new JSONArray();

        double totalMarks = 0;

        if (!documentOnly) {

            if (questions.isEmpty()) {

                showMessage("Add at least one question.");
                return;
            }

            for (QuestionCard question : questions) {

                JSONObject item = question.toJson();

                if (item == null) {
                    return;
                }

                questionArray.put(item);

                totalMarks += question.getMarks();
            }

        } else {

            // Default maximum marks for document assignments.
            totalMarks = 100;
        }

        if (totalMarks <= 0) {

            showMessage("The assessment must have marks.");
            return;
        }

        JSONObject session = sessions.get(
                spinnerSession.getSelectedItemPosition()
        );

        String sessionId = session.optString("id", "");
        String learnerId = session.optString("learner_id", "");

        String dueAt = date.atTime(23, 59)
                .atZone(ZoneId.systemDefault())
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        JSONObject payload = new JSONObject();

        try {

            payload.put("session_id", sessionId);
            payload.put("learner_id", learnerId);
            payload.put("title", title);
            payload.put("subject", subject);
            payload.put("instructions", instructions);
            payload.put("due_at", dueAt);
            payload.put("max_score", totalMarks);
            payload.put("assessment_type", assessmentType);

            payload.put(
                    "allow_online_answers",
                    cbOnline.isChecked()
            );

            payload.put(
                    "allow_file_submission",
                    cbFile.isChecked()
            );

            payload.put("max_attempts", 1);
            payload.put("questions", questionArray);

        } catch (Exception e) {

            showMessage(
                    "Could not prepare assessment information."
            );

            return;
        }

        setLoading(true);

        SupabaseApi.createAssessmentWithQuestions(
                payload,

                new SupabaseApi.ObjectCallback() {

                    @Override
                    public void onSuccess(JSONObject result) {

                        runOnUiThread(() -> {

                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            setLoading(false);

                            showMessage(
                                    "Assessment published successfully!"
                            );

                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(() -> {

                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            setLoading(false);

                            showMessage(
                                    "Publishing failed: " + message
                            );
                        });
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // LOADING STATE
    // ---------------------------------------------------------

    private void setLoading(boolean value) {

        loading = value;

        btnPublish.setEnabled(!value);

        btnPublish.setText(
                value
                        ? "Publishing..."
                        : "Publish assessment"
        );
    }

    // ---------------------------------------------------------
    // GENERAL UI HELPERS
    // ---------------------------------------------------------

    private void showMessage(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private int dp(float value) {

        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text) {

        TextView view = new TextView(this);

        view.setText(text);
        view.setTextColor(0xFF172B4D);
        view.setTextSize(14);

        view.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        return view;
    }

    private EditText edit(String hint) {

        EditText view = new EditText(this);

        view.setHint(hint);
        view.setTextSize(14);

        view.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        view.setBackgroundResource(
                android.R.drawable.edit_text
        );

        return view;
    }

    private LinearLayout vertical() {

        LinearLayout layout = new LinearLayout(this);

        layout.setOrientation(LinearLayout.VERTICAL);

        return layout;
    }

    private LinearLayout.LayoutParams params(
            int width,
            int height) {

        return new LinearLayout.LayoutParams(
                width,
                height
        );
    }

    // ---------------------------------------------------------
    // INDIVIDUAL QUESTION CARD
    // ---------------------------------------------------------

    private class QuestionCard {

        int number;

        final LinearLayout view;
        final TextView title;

        final Spinner typeSpinner;

        final EditText questionText;
        final EditText marksText;

        final LinearLayout answerContainer;

        final ArrayList<EditText> optionInputs =
                new ArrayList<>();

        EditText acceptedAnswer;
        EditText markingGuidance;

        Spinner correctOptionSpinner;

        QuestionCard(int number) {

            this.number = number;

            view = vertical();

            view.setPadding(
                    dp(14),
                    dp(14),
                    dp(14),
                    dp(14)
            );

            view.setBackgroundColor(0xFFF0F5FC);

            LinearLayout.LayoutParams cardParams =
                    params(-1, -2);

            cardParams.setMargins(
                    0,
                    0,
                    0,
                    dp(14)
            );

            view.setLayoutParams(cardParams);

            LinearLayout heading = new LinearLayout(
                    CreateAssessmentActivity.this
            );

            heading.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            heading.setGravity(Gravity.CENTER_VERTICAL);

            title = label("Question " + number);

            heading.addView(
                    title,
                    new LinearLayout.LayoutParams(
                            0,
                            -2,
                            1
                    )
            );

            Button remove = new Button(
                    CreateAssessmentActivity.this
            );

            remove.setText("Remove");

            remove.setOnClickListener(
                    v -> removeQuestion(this)
            );

            heading.addView(remove);

            view.addView(heading);

            view.addView(label("Question type"));

            typeSpinner = new Spinner(
                    CreateAssessmentActivity.this
            );

            String[] types = {
                    "Multiple choice",
                    "True / False",
                    "Written response"
            };

            ArrayAdapter<String> typeAdapter =
                    new ArrayAdapter<>(
                            CreateAssessmentActivity.this,
                            android.R.layout.simple_spinner_item,
                            types
                    );

            typeAdapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
            );

            typeSpinner.setAdapter(typeAdapter);

            view.addView(typeSpinner);

            view.addView(label("Question"));

            questionText = edit(
                    "Enter your question here..."
            );

            questionText.setMinLines(2);
            questionText.setGravity(Gravity.TOP);

            view.addView(questionText);

            view.addView(
                    label("Marks for this question")
            );

            marksText = edit("e.g. 5");

            marksText.setInputType(
                    android.text.InputType.TYPE_CLASS_NUMBER
                            | android.text.InputType
                            .TYPE_NUMBER_FLAG_DECIMAL
            );

            marksText.setText("1");

            view.addView(marksText);

            view.addView(
                    label("Answer configuration")
            );

            answerContainer = vertical();

            view.addView(answerContainer);

            typeSpinner.setOnItemSelectedListener(
                    new AdapterView.OnItemSelectedListener() {

                        @Override
                        public void onItemSelected(
                                AdapterView<?> parent,
                                View selected,
                                int position,
                                long id) {

                            renderAnswerInputs();
                        }

                        @Override
                        public void onNothingSelected(
                                AdapterView<?> parent) {
                        }
                    }
            );

            marksText.addTextChangedListener(
                    new android.text.TextWatcher() {

                        @Override
                        public void beforeTextChanged(
                                CharSequence s,
                                int start,
                                int count,
                                int after) {
                        }

                        @Override
                        public void onTextChanged(
                                CharSequence s,
                                int start,
                                int before,
                                int count) {

                            updateQuestionSummary();
                        }

                        @Override
                        public void afterTextChanged(
                                android.text.Editable s) {
                        }
                    }
            );
        }

        // -----------------------------------------------------
        // RENDER ANSWER INPUTS
        // -----------------------------------------------------

        private void renderAnswerInputs() {

            answerContainer.removeAllViews();

            optionInputs.clear();

            acceptedAnswer = null;
            markingGuidance = null;
            correctOptionSpinner = null;

            int selectedType =
                    typeSpinner.getSelectedItemPosition();

            if (selectedType == 1) {

                addOption("True");
                addOption("False");

                addCorrectAnswerSelector();

                return;
            }

            if (selectedType == 0) {

                addOption("Option A");
                addOption("Option B");
                addOption("Option C");
                addOption("Option D");

                Button add = new Button(
                        CreateAssessmentActivity.this
                );

                add.setText("+ Add option");

                add.setOnClickListener(v -> {

                    if (optionInputs.size() >= 10) {

                        showMessage(
                                "A question can have a maximum of 10 options."
                        );

                        return;
                    }

                    addOption("New option");

                    refreshCorrectOptions();
                });

                answerContainer.addView(add);

                addCorrectAnswerSelector();

            } else {

                answerContainer.addView(
                        label("Expected answer")
                );

                acceptedAnswer = edit(
                        "Enter the expected answer..."
                );

                acceptedAnswer.setMinLines(2);

                answerContainer.addView(
                        acceptedAnswer
                );

                answerContainer.addView(
                        label("Marking guidance")
                );

                markingGuidance = edit(
                        "Explain how this answer should be marked..."
                );

                markingGuidance.setMinLines(2);

                answerContainer.addView(
                        markingGuidance
                );
            }
        }

        // -----------------------------------------------------
        // ADD ANSWER OPTION
        // -----------------------------------------------------

        private void addOption(String hint) {

            int index = optionInputs.size();

            EditText option = edit(hint);

            option.setSingleLine(false);

            optionInputs.add(option);

            answerContainer.addView(
                    label("Option " + optionLabel(index))
            );

            answerContainer.addView(option);
        }

        // -----------------------------------------------------
        // CORRECT ANSWER
        // -----------------------------------------------------

        private void addCorrectAnswerSelector() {

            answerContainer.addView(
                    label("Select the correct answer")
            );

            correctOptionSpinner = new Spinner(
                    CreateAssessmentActivity.this
            );

            refreshCorrectOptions();

            answerContainer.addView(
                    correctOptionSpinner
            );
        }

        private void refreshCorrectOptions() {

            if (correctOptionSpinner == null) {
                return;
            }

            ArrayList<String> labels = new ArrayList<>();

            for (int i = 0; i < optionInputs.size(); i++) {

                labels.add(
                        "Option " + optionLabel(i)
                );
            }

            ArrayAdapter<String> adapter =
                    new ArrayAdapter<>(
                            CreateAssessmentActivity.this,
                            android.R.layout.simple_spinner_item,
                            labels
                    );

            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
            );

            correctOptionSpinner.setAdapter(adapter);
        }

        private String optionLabel(int index) {

            return String.valueOf(
                    (char) ('A' + index)
            );
        }

        // -----------------------------------------------------
        // QUESTION MARKS
        // -----------------------------------------------------

        double getMarks() {

            try {

                return Double.parseDouble(
                        marksText.getText()
                                .toString()
                                .trim()
                );

            } catch (Exception e) {

                return 0;
            }
        }

        // -----------------------------------------------------
        // CONVERT QUESTION TO JSON
        // -----------------------------------------------------

        JSONObject toJson() {

            String text = questionText.getText()
                    .toString()
                    .trim();

            if (text.isEmpty()) {

                showMessage(
                        "Enter the text for question " + number
                );

                return null;
            }

            double marks = getMarks();

            if (marks <= 0) {

                showMessage(
                        "Enter valid marks for question " + number
                );

                return null;
            }

            JSONObject result = new JSONObject();

            try {

                int selectedType =
                        typeSpinner.getSelectedItemPosition();

                String questionType =
                        selectedType == 2
                                ? "written"
                                : "multiple_choice";

                result.put(
                        "question_type",
                        questionType
                );

                result.put("question_text", text);
                result.put("marks", marks);
                result.put("is_required", true);

                // Written-response question
                if (selectedType == 2) {

                    String answer =
                            acceptedAnswer == null
                                    ? ""
                                    : acceptedAnswer.getText()
                                    .toString()
                                    .trim();

                    String guidance =
                            markingGuidance == null
                                    ? ""
                                    : markingGuidance.getText()
                                    .toString()
                                    .trim();

                    if (answer.isEmpty()
                            && guidance.isEmpty()) {

                        showMessage(
                                "Add an expected answer or marking guidance "
                                        + "for question " + number
                        );

                        return null;
                    }

                    result.put(
                            "accepted_answer",
                            answer
                    );

                    result.put(
                            "marking_guidance",
                            guidance
                    );

                } else {

                    // Multiple-choice and True/False
                    JSONArray options = new JSONArray();

                    for (int i = 0;
                         i < optionInputs.size();
                         i++) {

                        String optionText =
                                optionInputs.get(i)
                                        .getText()
                                        .toString()
                                        .trim();

                        if (optionText.isEmpty()) {

                            showMessage(
                                    "Complete every option in question "
                                            + number
                            );

                            return null;
                        }

                        JSONObject option = new JSONObject();

                        option.put(
                                "option_label",
                                optionLabel(i)
                        );

                        option.put(
                                "option_text",
                                optionText
                        );

                        option.put(
                                "display_order",
                                i + 1
                        );

                        options.put(option);
                    }

                    if (correctOptionSpinner == null
                            || correctOptionSpinner
                            .getSelectedItemPosition() < 0) {

                        showMessage(
                                "Choose the correct answer for question "
                                        + number
                        );

                        return null;
                    }

                    result.put(
                            "options",
                            options
                    );

                    result.put(
                            "correct_option_label",
                            optionLabel(
                                    correctOptionSpinner
                                            .getSelectedItemPosition()
                            )
                    );
                }

            } catch (Exception e) {

                showMessage(
                        "Could not prepare question " + number
                );

                return null;
            }

            return result;
        }
    }
}
