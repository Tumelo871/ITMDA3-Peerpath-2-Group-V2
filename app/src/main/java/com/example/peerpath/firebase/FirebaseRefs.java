package com.example.peerpath.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

/**
 * Centralised Firebase references used by PeerPath.
 * Keeping collection/storage names here reduces typos and makes the
 * data layer easier to maintain.
 */
public final class FirebaseRefs {

    private static final FirebaseFirestore DB = FirebaseFirestore.getInstance();
    private static final FirebaseStorage STORAGE = FirebaseStorage.getInstance();

    private FirebaseRefs() {}

    public static FirebaseAuth auth() {
        return FirebaseAuth.getInstance();
    }

    public static FirebaseFirestore db() {
        return DB;
    }

    public static CollectionReference users() {
        return DB.collection("users");
    }

    public static CollectionReference tutorProfiles() {
        return DB.collection("tutorProfiles");
    }

    public static CollectionReference sessionRequests() {
        return DB.collection("sessionRequests");
    }

    public static CollectionReference reviews() {
        return DB.collection("reviews");
    }

    public static CollectionReference notifications() {
        return DB.collection("notifications");
    }

    public static CollectionReference materials() {
        return DB.collection("materials");
    }

    public static CollectionReference resources() {
        return DB.collection("resources");
    }

    public static CollectionReference bookings() {
        return DB.collection("bookings");
    }

    public static StorageReference resourceFiles() {
        return STORAGE.getReference().child("resources");
    }

    public static StorageReference tutorEvidenceFiles() {
        return STORAGE.getReference().child("tutor-evidence");
    }
}
