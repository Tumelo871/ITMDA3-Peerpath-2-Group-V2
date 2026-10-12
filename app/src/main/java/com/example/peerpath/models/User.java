package com.example.peerpath.models;

/**
 * Represents a registered user of PeerPath.
 * A user can be a STUDENT, a TUTOR, or both (isTutor flag),
 * plus an ADMIN role for the admin dashboard.
 * Stored in Firestore collection: "users/{uid}"
 */
public class User {

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_ADMIN = "ADMIN";

    // v1 scope: Eduvos Pretoria campus only. Kept as a field (rather than
    // assumed) so a future version could open up to other campuses without
    // a schema change.
    public static final String CAMPUS_PRETORIA = "Pretoria";

    private String uid;          // Firebase Auth UID
    private String fullName;
    private String email;
    private String studentNumber;
    private String faculty;
    private String campus;       // always CAMPUS_PRETORIA for this version of the app
    private String role;         // ROLE_STUDENT or ROLE_ADMIN — admins are promoted manually in the Firebase console
    private boolean isTutor;     // true if this user has an associated TutorProfile
    private long createdAt;

    public User() {
        // Required empty constructor for Firestore deserialization
    }

    public User(String uid, String fullName, String email, String studentNumber, String faculty) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.studentNumber = studentNumber;
        this.faculty = faculty;
        this.campus = CAMPUS_PRETORIA;
        this.role = ROLE_STUDENT;
        this.isTutor = false;
        this.createdAt = System.currentTimeMillis();
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getStudentNumber() { return studentNumber; }
    public void setStudentNumber(String studentNumber) { this.studentNumber = studentNumber; }

    public String getFaculty() { return faculty; }
    public void setFaculty(String faculty) { this.faculty = faculty; }

    public String getCampus() { return campus; }
    public void setCampus(String campus) { this.campus = campus; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isTutor() { return isTutor; }
    public void setTutor(boolean tutor) { isTutor = tutor; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
