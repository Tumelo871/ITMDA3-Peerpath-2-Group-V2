package com.example.peerpath;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class NotificationHelper {

    public static void sendNotification(
            String userId,
            String title,
            String message,
            String type,
            String referenceId) {

        FirebaseFirestore db =
                FirebaseFirestore.getInstance();

        Map<String, Object> notification =
                new HashMap<>();

        notification.put("userId", userId);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("type", type);
        notification.put("referenceId", referenceId);
        notification.put("read", false);
        notification.put(
                "createdAt",
                System.currentTimeMillis()
        );

        db.collection("notifications")
                .add(notification);
    }
}