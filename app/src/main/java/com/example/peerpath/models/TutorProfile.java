package com.example.peerpath.models;

import java.util.List;

/**
 * A tutor's profile: the modules they claim to be able to tutor,
 * and the verification status for each claim.

 * Stored in Firestore collection: "tutorProfiles/{uid}"

 * verificationStatus values:
 *  "PENDING"  - submitted, awaiting admin review
 *  "VERIFIED" - admin confirmed the tutor passed the module
 *  "REJECTED" - admin could not confirm the claim
 */
public class TutorProfile {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_REJECTED = "REJECTED";

    private String uid;
    private String tutorName;              // denormalized copy of User.fullName, kept in sync at write time —
                                            // avoids an extra Firestore read per row when rendering tutor lists
    private List<String> moduleCodes;      // e.g. ["ITMDA3-22", "PROG6-22"]
    private String verificationStatus;
    private String verificationEvidenceUrl; // link/photo of proof of pass, reviewed by admin
    private double ratingAverage;
    private int ratingCount;
    private List<String> availabilitySlots; // simple ISO date-time strings for v1

    public TutorProfile() {
    }

    public TutorProfile(String uid, String tutorName, List<String> moduleCodes, String verificationEvidenceUrl) {
        this.uid = uid;
        this.tutorName = tutorName;
        this.moduleCodes = moduleCodes;
        this.verificationEvidenceUrl = verificationEvidenceUrl;
        this.verificationStatus = STATUS_PENDING;
        this.ratingAverage = 0.0;
        this.ratingCount = 0;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getTutorName() { return tutorName; }
    public void setTutorName(String tutorName) { this.tutorName = tutorName; }

    public List<String> getModuleCodes() { return moduleCodes; }
    public void setModuleCodes(List<String> moduleCodes) { this.moduleCodes = moduleCodes; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getVerificationEvidenceUrl() { return verificationEvidenceUrl; }
    public void setVerificationEvidenceUrl(String url) { this.verificationEvidenceUrl = url; }

    public double getRatingAverage() { return ratingAverage; }
    public void setRatingAverage(double ratingAverage) { this.ratingAverage = ratingAverage; }

    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }

    public List<String> getAvailabilitySlots() { return availabilitySlots; }
    public void setAvailabilitySlots(List<String> availabilitySlots) { this.availabilitySlots = availabilitySlots; }
}
