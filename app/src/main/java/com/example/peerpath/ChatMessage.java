package com.example.peerpath;

public class ChatMessage {

    private String senderId;
    private String message;
    private long timestamp;

    public ChatMessage() {
    }

    public ChatMessage(
            String senderId,
            String message,
            long timestamp) {

        this.senderId = senderId;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }
}