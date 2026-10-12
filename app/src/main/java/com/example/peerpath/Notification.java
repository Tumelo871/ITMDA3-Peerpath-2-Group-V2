package com.example.peerpath;

public class Notification {

    private String userId;
    private String title;
    private String message;
    private boolean read;

    public Notification() {
    }

    public Notification(
            String userId,
            String title,
            String message,
            boolean read) {

        this.userId = userId;
        this.title = title;
        this.message = message;
        this.read = read;
    }

    public String getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }
}