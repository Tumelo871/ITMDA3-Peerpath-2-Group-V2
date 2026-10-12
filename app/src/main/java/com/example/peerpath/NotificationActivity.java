package com.example.peerpath;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Live notification list. New ones appear instantly, unread ones are
 * highlighted, tapping one opens the screen it is about, and everything
 * you saw is marked read when you leave.
 */
public class NotificationActivity extends AppCompatActivity {

    private LinearLayout container;
    private View tvEmpty;

    private FirebaseFirestore db;
    private String uid;
    private boolean isTutor = false;

    private ListenerRegistration reg;

    private final Set<String> unread = new HashSet<>();
    private final SimpleDateFormat when =
            new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        db = FirebaseFirestore.getInstance();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        container = findViewById(R.id.notificationContainer);
        tvEmpty = findViewById(R.id.tvEmptyNotifications);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        db.collection("users").document(uid).get().addOnSuccessListener(d ->
                isTutor = "tutor".equalsIgnoreCase(d.getString("role")));

        reg = db.collection("notifications")
                .whereEqualTo("userId", uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) return;

                    List<DocumentSnapshot> docs =
                            new ArrayList<>(snapshot.getDocuments());

                    docs.sort((a, b) -> Long.compare(
                            Ui.num(b.getLong("createdAt")),
                            Ui.num(a.getLong("createdAt"))));

                    container.removeAllViews();
                    unread.clear();

                    tvEmpty.setVisibility(
                            docs.isEmpty() ? View.VISIBLE : View.GONE);

                    for (DocumentSnapshot d : docs) {

                        boolean isNew = !Boolean.TRUE.equals(d.getBoolean("read"));
                        if (isNew) unread.add(d.getId());

                        LinearLayout card = Ui.card(this);
                        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12),
                                Ui.dp(this, 12), Ui.dp(this, 12));
                        if (isNew) card.setBackgroundResource(R.drawable.bg_quote);

                        card.addView(Ui.text(this,
                                (isNew ? "\u2022 " : "") + d.getString("title"),
                                17, R.color.text_primary, true));
                        card.addView(Ui.text(this, d.getString("message"),
                                15, R.color.text_secondary, false));
                        card.addView(Ui.text(this,
                                when.format(new Date(
                                        Ui.num(d.getLong("createdAt")))),
                                12, R.color.text_secondary, false));

                        Ui.addToList(container, card);

                        String type = d.getString("type");
                        String ref = d.getString("referenceId");
                        card.setOnClickListener(v -> open(type, ref));
                    }
                });
    }

    private void open(String type, String ref) {

        Intent i = null;
        String t = type == null ? "" : type;

        switch (t) {
            case "chat":
                if (ref != null && ref.contains("_")) {
                    String[] p = ref.split("_");
                    i = new Intent(this, ChatActivity.class)
                            .putExtra("studentId", p[0])
                            .putExtra("tutorId", p[1]);
                }
                break;
            case "announcement":
                if (ref != null) {
                    i = new Intent(this, ChatActivity.class)
                            .putExtra("studentId", uid)
                            .putExtra("tutorId", ref);
                }
                break;
            case "sessionRequest":
                i = new Intent(this, TutorRequestsActivity.class);
                break;
            case "session":
                i = new Intent(this, isTutor
                        ? TutorSessionsActivity.class
                        : MySessionsActivity.class);
                break;
            case "review":
                i = new Intent(this, TutorProfileActivity.class)
                        .putExtra("tutorId", uid);
                break;
            default:
                break;
        }

        if (i != null) startActivity(i);
    }

    /** Everything you have seen becomes read when you leave. */
    @Override
    protected void onStop() {
        super.onStop();
        for (String id : new ArrayList<>(unread)) {
            db.collection("notifications").document(id).update("read", true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reg != null) reg.remove();
    }
}
