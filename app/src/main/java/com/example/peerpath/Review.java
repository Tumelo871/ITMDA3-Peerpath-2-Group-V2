package com.example.peerpath;

public class Review {

    private String studentId;
    private String tutorId;
    private String requestId;
    private double rating;
    private String comment;

    public Review() {
    }

    public Review(
            String studentId,
            String tutorId,
            String requestId,
            double rating,
            String comment) {

        this.studentId = studentId;
        this.tutorId = tutorId;
        this.requestId = requestId;
        this.rating = rating;
        this.comment = comment;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getTutorId() {
        return tutorId;
    }

    public String getRequestId() {
        return requestId;
    }

    public double getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }
}