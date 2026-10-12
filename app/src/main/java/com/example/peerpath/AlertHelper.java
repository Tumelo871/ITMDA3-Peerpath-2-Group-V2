package com.example.peerpath;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Shows a phone notification when something new arrives. */
final class AlertHelper {

    private static final String CHANNEL = "peerpath_updates";

    private AlertHelper() {
    }

    static void show(Context c, String title, String message) {

        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;

        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "PeerPath updates", NotificationManager.IMPORTANCE_HIGH));
        }

        Intent open = new Intent(c, NotificationActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE
                        | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title == null ? "PeerPath" : title)
                .setContentText(message == null ? "" : message)
                .setStyle(new Notification.BigTextStyle()
                        .bigText(message == null ? "" : message))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
    }
}
