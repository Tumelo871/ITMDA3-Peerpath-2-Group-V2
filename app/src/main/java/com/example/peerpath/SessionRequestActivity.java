package com.example.peerpath;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;
import com.google.android.material.button.MaterialButton;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class SessionRequestActivity extends AppCompatActivity {

    private EditText etSubject;
    private EditText etDate;
    private EditText etTime;
    private EditText etMessage;
    private MaterialButton btnRequest;

    private String tutorId;
    private boolean submitting = false;

    private static final Pattern DATE_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern TIME_PATTERN =
            Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$");

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SupabaseApi.init(getApplicationContext());
        setContentView(R.layout.activity_session_request);

        dateFormat.setLenient(false);

        tutorId = getIntent().getStringExtra("tutorId");

        etSubject = findViewById(R.id.etSubject);
        etDate = findViewById(R.id.etDate);
        etTime = findViewById(R.id.etTime);
        etMessage = findViewById(R.id.etMessage);
        btnRequest = findViewById(R.id.btnRequest);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        String suppliedSubject = getIntent().getStringExtra("subject");
        if (!TextUtils.isEmpty(suppliedSubject)) {
            etSubject.setText(suppliedSubject.trim());
        }

        // Use pickers rather than free-form typing to reduce invalid input.
        etDate.setKeyListener(null);
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(v -> showDatePicker());

        etTime.setKeyListener(null);
        etTime.setFocusable(false);
        etTime.setClickable(true);
        etTime.setOnClickListener(v -> showTimePicker());

        btnRequest.setOnClickListener(v -> submitRequest());
    }

    private void showDatePicker() {
        Calendar today = startOfToday();
        Calendar initial = (Calendar) today.clone();

        Date existingDate = parseStrictDate(etDate.getText().toString().trim());
        if (existingDate != null) {
            initial.setTime(existingDate);
            if (initial.before(today)) {
                initial = (Calendar) today.clone();
            }
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar chosen = Calendar.getInstance();
                    chosen.clear();
                    chosen.set(year, month, dayOfMonth);

                    Calendar minimum = startOfToday();
                    if (chosen.before(minimum)) {
                        etDate.setError("Choose today or a future date.");
                        return;
                    }

                    etDate.setError(null);
                    etDate.setText(dateFormat.format(chosen.getTime()));

                    // A previously selected time may now be invalid if today is chosen.
                    if (isToday(chosen) && !isTimeAfterNow(etTime.getText().toString().trim())) {
                        etTime.setText("");
                        etTime.setError(null);
                    }
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));

        dialog.getDatePicker().setMinDate(today.getTimeInMillis());
        dialog.show();
    }

    private void showTimePicker() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);

        int[] existing = parseStrictTime(etTime.getText().toString().trim());
        if (existing != null) {
            hour = existing[0];
            minute = existing[1];
        }

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {
                    String selectedTime = String.format(
                            Locale.ROOT, "%02d:%02d", selectedHour, selectedMinute);

                    Date chosenDate = parseStrictDate(etDate.getText().toString().trim());
                    if (chosenDate != null && isToday(chosenDate)
                            && !isTimeAfterNow(selectedTime)) {
                        etTime.setError("For today, choose a time later than now.");
                        Toast.makeText(this,
                                "For a session today, choose a future time.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    etTime.setError(null);
                    etTime.setText(selectedTime);
                },
                hour,
                minute,
                true);

        dialog.show();
    }

    private void submitRequest() {
        if (submitting) {
            return;
        }

        String subject = etSubject.getText().toString().trim();
        String dateText = etDate.getText().toString().trim();
        String timeText = etTime.getText().toString().trim();
        String message = etMessage.getText().toString().trim();

        if (TextUtils.isEmpty(tutorId)) {
            showToast("Tutor could not be identified.");
            return;
        }

        if (subject.isEmpty()) {
            etSubject.setError("Enter a subject or module.");
            etSubject.requestFocus();
            return;
        }

        if (subject.length() > 150) {
            etSubject.setError("Subject must be 150 characters or fewer.");
            etSubject.requestFocus();
            return;
        }

        if (dateText.isEmpty()) {
            etDate.setError("Choose a session date.");
            showToast("Please choose a session date.");
            return;
        }

        Date chosenDate = parseStrictDate(dateText);
        if (chosenDate == null) {
            etDate.setError("Choose a valid date.");
            showToast("Please select a valid date using the date picker.");
            return;
        }

        Calendar chosenDay = Calendar.getInstance();
        chosenDay.setTime(chosenDate);
        clearTime(chosenDay);

        Calendar today = startOfToday();
        if (chosenDay.before(today)) {
            etDate.setError("Past dates are not allowed.");
            showToast("Choose today or a future date.");
            return;
        }

        if (timeText.isEmpty()) {
            etTime.setError("Choose a session time.");
            showToast("Please choose a session time.");
            return;
        }

        if (parseStrictTime(timeText) == null) {
            etTime.setError("Choose a valid time.");
            showToast("Please select a valid time using the time picker.");
            return;
        }

        if (isToday(chosenDay) && !isTimeAfterNow(timeText)) {
            etTime.setError("For today, choose a time later than now.");
            showToast("The session time must be later than the current time.");
            return;
        }

        etSubject.setError(null);
        etDate.setError(null);
        etTime.setError(null);

        submitting = true;
        btnRequest.setEnabled(false);
        btnRequest.setText("Sending request...");

        SupabaseApi.requestTutorSession(
                tutorId,
                subject,
                dateText,
                timeText,
                message,
                new SupabaseApi.EmptyCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            showToast("Session request sent successfully!");
                            finish();
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            submitting = false;
                            btnRequest.setEnabled(true);
                            btnRequest.setText("Send session request");
                            showToast("Request failed: " + errorMessage);
                        });
                    }
                });
    }

    private Date parseStrictDate(String value) {
        if (value == null || !DATE_PATTERN.matcher(value).matches()) {
            return null;
        }

        ParsePosition position = new ParsePosition(0);
        Date parsed = dateFormat.parse(value, position);

        if (parsed == null || position.getIndex() != value.length()) {
            return null;
        }

        // Round-trip check rejects dates that the parser might normalize.
        if (!dateFormat.format(parsed).equals(value)) {
            return null;
        }

        return parsed;
    }

    /**
     * Returns {hour, minute}, or null if the value is not a strict 24-hour time.
     */
    private int[] parseStrictTime(String value) {
        if (value == null || !TIME_PATTERN.matcher(value).matches()) {
            return null;
        }

        try {
            return new int[] {
                    Integer.parseInt(value.substring(0, 2)),
                    Integer.parseInt(value.substring(3, 5))
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isTimeAfterNow(String value) {
        int[] parsedTime = parseStrictTime(value);
        if (parsedTime == null) {
            return false;
        }

        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60
                + now.get(Calendar.MINUTE);
        int chosenMinutes = parsedTime[0] * 60 + parsedTime[1];

        // Times are minute-precision, so the selected minute must be later.
        return chosenMinutes > nowMinutes;
    }

    private boolean isToday(Date date) {
        Calendar selected = Calendar.getInstance();
        selected.setTime(date);
        Calendar today = Calendar.getInstance();

        return selected.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && selected.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isToday(Calendar selected) {
        Calendar today = Calendar.getInstance();
        return selected.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && selected.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private Calendar startOfToday() {
        Calendar today = Calendar.getInstance();
        clearTime(today);
        return today;
    }

    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
