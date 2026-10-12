package com.example.peerpath;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Ui {

    public static int dp(Context context, int value) {
        return Math.round(
                value * context.getResources().getDisplayMetrics().density
        );
    }

    public static LinearLayout card(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static TextView text(
            Context context,
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView tv = new TextView(context);

        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(context.getResources().getColor(color));

        if (bold) {
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
        }

        return tv;
    }

    public static void addToList(
            LinearLayout parent,
            View child
    ) {
        parent.addView(child);
    }

    public static long num(Long value) {
        return value == null ? 0L : value;
    }

    public static void add(
            LinearLayout parent,
            View child,
            int margin
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(0, dp(parent.getContext(), margin), 0, 0);

        parent.addView(child, params);
    }
}