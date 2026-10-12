package com.example.peerpath;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.provider.CalendarContract;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.Calendar;
import java.util.Locale;

/** Date/time pickers, small text prompts and "add to calendar". */
final class ScheduleHelper {

    interface DateTimeCallback {
        void onPicked(long millis, String date, String time);
    }

    interface TextCallback {
        void onText(String text);
    }

    private ScheduleHelper() {
    }

    static void pick(Context c, long initialMillis, DateTimeCallback cb) {

        Calendar start = Calendar.getInstance();
        if (initialMillis > System.currentTimeMillis()) {
            start.setTimeInMillis(initialMillis);
        }

        DatePickerDialog dp = new DatePickerDialog(c, (v, y, m, d) ->
                new TimePickerDialog(c, (tv, h, min) -> {

                    Calendar r = Calendar.getInstance();
                    r.set(y, m, d, h, min, 0);
                    r.set(Calendar.MILLISECOND, 0);

                    if (r.getTimeInMillis() <= System.currentTimeMillis()) {
                        Toast.makeText(c, "Pick a time in the future",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    cb.onPicked(r.getTimeInMillis(),
                            String.format(Locale.US, "%02d/%02d/%d",
                                    d, m + 1, y),
                            String.format(Locale.US, "%02d:%02d", h, min));

                }, start.get(Calendar.HOUR_OF_DAY),
                        start.get(Calendar.MINUTE), true).show(),
                start.get(Calendar.YEAR), start.get(Calendar.MONTH),
                start.get(Calendar.DAY_OF_MONTH));

        dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dp.show();
    }

    static void askText(Context c, String title, String hint,
                        String initial, TextCallback cb) {

        EditText input = new EditText(c);
        input.setHint(hint);
        input.setText(initial == null ? "" : initial);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        LinearLayout box = new LinearLayout(c);
        int pad = Ui.dp(c, 20);
        box.setPadding(pad, Ui.dp(c, 8), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Save", (d, w) ->
                        cb.onText(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    static void addToCalendar(Context c, String title, long startMillis,
                              String location) {

        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,
                        startMillis + 60L * 60L * 1000L)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.Events.EVENT_LOCATION,
                        location == null ? "" : location);

        try {
            c.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(c, "No calendar app found on this device.",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
