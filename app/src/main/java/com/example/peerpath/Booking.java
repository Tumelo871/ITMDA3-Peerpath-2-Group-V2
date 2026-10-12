package com.example.peerpath;

public class Booking {

    private String bookingId;
    private String studentId;
    private String tutorId;
    private String date;
    private String time;
    private String subject;
    private String status;

    public Booking() {
    }

    public Booking(
            String bookingId,
            String studentId,
            String tutorId,
            String date,
            String time,
            String subject,
            String status) {

        this.bookingId = bookingId;
        this.studentId = studentId;
        this.tutorId = tutorId;
        this.date = date;
        this.time = time;
        this.subject = subject;
        this.status = status;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getTutorId() {
        return tutorId;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public String getSubject() {
        return subject;
    }

    public String getStatus() {
        return status;
    }
}