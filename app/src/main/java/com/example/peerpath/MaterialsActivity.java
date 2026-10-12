package com.example.peerpath;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.peerpath.supabase.SupabaseApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MaterialsActivity extends AppCompatActivity {

    private EditText etSearch;
    private TextView tvStatus;
    private LinearLayout container;

    private boolean mine;
    private String uid;

    private String mySubject = "";

    private final List<JSONObject> allMaterials =
            new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_materials);

        // =========================================================
        // CURRENT USER
        // =========================================================

        uid = SupabaseApi.userId();

        if (uid == null || uid.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Please log in first.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        // =========================================================
        // MODE
        // =========================================================

        mine = getIntent().getBooleanExtra("mine", false);

        // =========================================================
        // VIEWS
        // =========================================================

        etSearch = findViewById(R.id.etSearch);
        tvStatus = findViewById(R.id.tvMaterialStatus);
        container = findViewById(R.id.materialContainer);

        TextView title = findViewById(R.id.tvMaterialsTitle);

        if (title != null) {
            title.setText(
                    mine
                            ? "My Materials"
                            : "Learning Materials"
            );
        }

        // =========================================================
        // BACK BUTTON
        // =========================================================

        View backButton = findViewById(R.id.btnBack);

        if (backButton != null) {

            backButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            finish();
                        }
                    }
            );
        }

        // =========================================================
        // ADD MATERIAL BUTTON
        // =========================================================

        Button addButton =
                findViewById(R.id.btnAddMaterial);

        if (addButton != null) {

            addButton.setVisibility(
                    mine
                            ? View.VISIBLE
                            : View.GONE
            );

            addButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showAddDialog();
                        }
                    }
            );
        }

        // =========================================================
        // SEARCH
        // =========================================================

        if (etSearch != null) {

            etSearch.addTextChangedListener(
                    new TextWatcher() {

                        @Override
                        public void beforeTextChanged(
                                CharSequence s,
                                int start,
                                int count,
                                int after
                        ) {
                        }

                        @Override
                        public void onTextChanged(
                                CharSequence s,
                                int start,
                                int before,
                                int count
                        ) {
                            showMaterials();
                        }

                        @Override
                        public void afterTextChanged(
                                Editable s
                        ) {
                        }
                    }
            );
        }

        // =========================================================
        // LOAD PROFILE
        // =========================================================

        loadProfile();

        // =========================================================
        // LOAD MATERIALS
        // =========================================================

        loadMaterials();
    }

    // =============================================================
    // PROFILE
    // =============================================================

    private void loadProfile() {

        SupabaseApi.getProfile(
                uid,
                new SupabaseApi.ObjectCallback() {

                    @Override
                    public void onSuccess(JSONObject profile) {

                        if (profile == null) {
                            return;
                        }

                        mySubject = profile.optString(
                                "tutor_subject",
                                profile.optString(
                                        "subject",
                                        ""
                                )
                        );
                    }

                    @Override
                    public void onError(String message) {
                        // Profile is optional.
                    }
                }
        );
    }

    // =============================================================
    // LOAD MATERIALS
    // =============================================================

    private void loadMaterials() {

        if (tvStatus != null) {
            tvStatus.setText("Loading materials...");
        }

        SupabaseApi.getResources(
                new SupabaseApi.JsonCallback() {

                    @Override
                    public void onSuccess(JSONArray data) {

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {

                                        allMaterials.clear();

                                        if (data != null) {

                                            for (
                                                    int i = 0;
                                                    i < data.length();
                                                    i++
                                            ) {

                                                JSONObject item =
                                                        data.optJSONObject(i);

                                                if (item == null) {
                                                    continue;
                                                }

                                                String ownerId =
                                                        item.optString(
                                                                "owner_id",
                                                                ""
                                                        );

                                                if (
                                                        !mine
                                                                || uid.equals(ownerId)
                                                ) {
                                                    allMaterials.add(item);
                                                }
                                            }
                                        }

                                        showMaterials();
                                    }
                                }
                        );
                    }

                    @Override
                    public void onError(String message) {

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {

                                        if (tvStatus != null) {
                                            tvStatus.setText(
                                                    "Could not load materials."
                                            );
                                        }

                                        Toast.makeText(
                                                MaterialsActivity.this,
                                                message == null
                                                        ? "Unable to load materials."
                                                        : message,
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }
                        );
                    }
                }
        );
    }

    // =============================================================
    // DISPLAY MATERIALS
    // =============================================================

    private void showMaterials() {

        if (container == null) {
            return;
        }

        String query = "";

        if (etSearch != null) {

            query = etSearch
                    .getText()
                    .toString()
                    .trim()
                    .toLowerCase(Locale.ROOT);
        }

        container.removeAllViews();

        int shown = 0;

        for (JSONObject material : allMaterials) {

            String title =
                    getString(material, "title");

            String subject =
                    getString(material, "subject");

            String description =
                    getString(material, "description");

            String searchableText =
                    (
                            title
                                    + " "
                                    + subject
                                    + " "
                                    + description
                    ).toLowerCase(Locale.ROOT);

            if (
                    !query.isEmpty()
                            && !searchableText.contains(query)
            ) {
                continue;
            }

            addMaterialCard(material);

            shown++;
        }

        if (tvStatus != null) {

            if (shown == 0) {

                tvStatus.setText(
                        mine
                                ? "You have not added any materials yet."
                                : "No materials found."
                );

            } else {

                tvStatus.setText(
                        shown
                                + (
                                shown == 1
                                        ? " material"
                                        : " materials"
                        )
                );
            }
        }
    }

    // =============================================================
    // MATERIAL CARD
    // =============================================================

    private void addMaterialCard(
            final JSONObject material
    ) {

        LinearLayout card =
                Ui.card(this);

        card.setPadding(
                Ui.dp(this, 16),
                Ui.dp(this, 16),
                Ui.dp(this, 16),
                Ui.dp(this, 16)
        );

        // =========================================================
        // TITLE
        // =========================================================

        String title =
                getString(material, "title");

        TextView titleView =
                Ui.text(
                        this,
                        "📘 " + title,
                        18,
                        R.color.text_primary,
                        true
                );

        card.addView(titleView);

        // =========================================================
        // SUBJECT
        // =========================================================

        String subject =
                getString(material, "subject");

        if (!subject.isEmpty()) {

            Ui.add(
                    card,
                    Ui.text(
                            this,
                            subject,
                            13,
                            R.color.peer_blue,
                            true
                    ),
                    6
            );
        }

        // =========================================================
        // DESCRIPTION
        // =========================================================

        String description =
                getString(material, "description");

        if (!description.isEmpty()) {

            Ui.add(
                    card,
                    Ui.text(
                            this,
                            description,
                            14,
                            R.color.text_secondary,
                            false
                    ),
                    8
            );
        }

        // =========================================================
        // LINK
        // =========================================================

        String url =
                getString(material, "url");

        if (url.isEmpty()) {

            url =
                    getString(material, "link");
        }

        /*
         * Make a final copy so Java can safely
         * use the URL inside the click listener.
         */

        final String materialUrl = url;

        if (!materialUrl.isEmpty()) {

            Button openButton =
                    createButton(
                            "Open material",
                            true
                    );

            openButton.setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {

                            openLink(materialUrl);
                        }
                    }
            );

            Ui.add(
                    card,
                    openButton,
                    10
            );
        }

        // =========================================================
        // DELETE
        // =========================================================

        if (mine) {

            Button deleteButton =
                    createButton(
                            "Delete",
                            false
                    );

            deleteButton.setTextColor(
                    getColor(
                            R.color.danger
                    )
            );

            deleteButton.setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {

                            confirmDelete(material);
                        }
                    }
            );

            Ui.add(
                    card,
                    deleteButton,
                    6
            );
        }

        // =========================================================
        // ADD CARD TO SCREEN
        // =========================================================

        Ui.addToList(
                container,
                card
        );
    }

    // =============================================================
    // BUTTON
    // =============================================================

    private Button createButton(
            String text,
            boolean primary
    ) {

        Button button =
                new Button(this);

        button.setText(text);
        button.setAllCaps(false);

        return button;
    }

    // =============================================================
    // DELETE CONFIRMATION
    // =============================================================

    private void confirmDelete(
            final JSONObject material
    ) {

        final String id =
                material.optString(
                        "id",
                        ""
                );

        if (id.isEmpty()) {

            Toast.makeText(
                    this,
                    "Material ID is missing.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String title =
                getString(
                        material,
                        "title"
                );

        new AlertDialog.Builder(this)

                .setTitle(
                        "Delete material?"
                )

                .setMessage(
                        "Are you sure you want to delete:\n\n"
                                + title
                )

                .setPositiveButton(
                        "Delete",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which
                            ) {

                                deleteMaterial(id);
                            }
                        }
                )

                .setNegativeButton(
                        "Cancel",
                        null
                )

                .show();
    }

    // =============================================================
    // DELETE MATERIAL
    // =============================================================

    private void deleteMaterial(
            String materialId
    ) {

        Toast.makeText(
                this,
                "Deleting material...",
                Toast.LENGTH_SHORT
        ).show();

        SupabaseApi.deleteResource(
                materialId,
                new SupabaseApi.EmptyCallback() {

                    @Override
                    public void onSuccess() {

                        runOnUiThread(
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        Toast.makeText(
                                                MaterialsActivity.this,
                                                "Material deleted.",
                                                Toast.LENGTH_SHORT
                                        ).show();

                                        loadMaterials();
                                    }
                                }
                        );
                    }

                    @Override
                    public void onError(
                            final String message
                    ) {

                        runOnUiThread(
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        Toast.makeText(
                                                MaterialsActivity.this,
                                                "Delete failed: "
                                                        + message,
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }
                        );
                    }
                }
        );
    }

    // =============================================================
    // ADD MATERIAL DIALOG
    // =============================================================

    private void showAddDialog() {

        LinearLayout box =
                new LinearLayout(this);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        int padding =
                Ui.dp(this, 20);

        box.setPadding(
                padding,
                Ui.dp(this, 8),
                padding,
                0
        );

        // =========================================================
        // TITLE
        // =========================================================

        final EditText title =
                createField(
                        box,
                        "Title",
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                );

        // =========================================================
        // SUBJECT
        // =========================================================

        final EditText subject =
                createField(
                        box,
                        "Subject / module",
                        InputType.TYPE_TEXT_FLAG_CAP_WORDS
                );

        subject.setText(mySubject);

        // =========================================================
        // LINK
        // =========================================================

        final EditText link =
                createField(
                        box,
                        "Link (optional)",
                        InputType.TYPE_TEXT_VARIATION_URI
                );

        // =========================================================
        // DESCRIPTION
        // =========================================================

        final EditText notes =
                createField(
                        box,
                        "Description / notes",
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                );

        // =========================================================
        // DIALOG
        // =========================================================

        final AlertDialog dialog =
                new AlertDialog.Builder(this)

                        .setTitle(
                                "Add learning material"
                        )

                        .setView(box)

                        .setPositiveButton(
                                "Add",
                                null
                        )

                        .setNegativeButton(
                                "Cancel",
                                null
                        )

                        .create();

        dialog.setOnShowListener(
                new DialogInterface.OnShowListener() {

                    @Override
                    public void onShow(
                            DialogInterface d
                    ) {

                        Button addButton =
                                dialog.getButton(
                                        AlertDialog.BUTTON_POSITIVE
                                );

                        addButton.setOnClickListener(
                                new View.OnClickListener() {

                                    @Override
                                    public void onClick(
                                            View v
                                    ) {

                                        String titleText =
                                                title.getText()
                                                        .toString()
                                                        .trim();

                                        String subjectText =
                                                subject.getText()
                                                        .toString()
                                                        .trim();

                                        String linkText =
                                                link.getText()
                                                        .toString()
                                                        .trim();

                                        String notesText =
                                                notes.getText()
                                                        .toString()
                                                        .trim();

                                        // =================================================
                                        // VALIDATION
                                        // =================================================

                                        if (titleText.isEmpty()) {

                                            title.setError(
                                                    "Enter a title"
                                            );

                                            title.requestFocus();

                                            return;
                                        }

                                        if (
                                                linkText.isEmpty()
                                                        && notesText.isEmpty()
                                        ) {

                                            link.setError(
                                                    "Add a link or description"
                                            );

                                            link.requestFocus();

                                            return;
                                        }

                                        // =================================================
                                        // NORMALIZE URL
                                        // =================================================

                                        if (
                                                !linkText.isEmpty()
                                                        && !linkText
                                                        .toLowerCase(Locale.ROOT)
                                                        .startsWith("http://")
                                                        && !linkText
                                                        .toLowerCase(Locale.ROOT)
                                                        .startsWith("https://")
                                        ) {

                                            linkText =
                                                    "https://"
                                                            + linkText;
                                        }

                                        // =================================================
                                        // SAVE
                                        // =================================================

                                        saveMaterial(
                                                titleText,
                                                subjectText,
                                                linkText,
                                                notesText,
                                                dialog
                                        );
                                    }
                                }
                        );
                    }
                }
        );

        dialog.show();
    }

    // =============================================================
    // FORM FIELD
    // =============================================================

    private EditText createField(
            LinearLayout parent,
            String hint,
            int type
    ) {

        EditText editText =
                new EditText(this);

        editText.setHint(hint);

        editText.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | type
        );

        parent.addView(
                editText,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        return editText;
    }

    // =============================================================
    // SAVE MATERIAL
    // =============================================================

    private void saveMaterial(
            String title,
            String subject,
            String link,
            String description,
            final AlertDialog dialog
    ) {

        Toast.makeText(
                this,
                "Saving material...",
                Toast.LENGTH_SHORT
        ).show();

        SupabaseApi.createResource(
                title,
                subject,
                link,
                description,
                new SupabaseApi.EmptyCallback() {

                    @Override
                    public void onSuccess() {

                        runOnUiThread(
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        dialog.dismiss();

                                        Toast.makeText(
                                                MaterialsActivity.this,
                                                "Material added successfully.",
                                                Toast.LENGTH_SHORT
                                        ).show();

                                        loadMaterials();
                                    }
                                }
                        );
                    }

                    @Override
                    public void onError(
                            final String message
                    ) {

                        runOnUiThread(
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        Toast.makeText(
                                                MaterialsActivity.this,
                                                "Could not add material: "
                                                        + message,
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }
                        );
                    }
                }
        );
    }

    // =============================================================
    // OPEN LINK
    // =============================================================

    private void openLink(
            String link
    ) {

        if (link == null || link.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "No material link available.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {

            Uri uri =
                    Uri.parse(link);

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            uri
                    );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Cannot open this material link.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // =============================================================
    // JSON STRING
    // =============================================================

    private String getString(
            JSONObject object,
            String key
    ) {

        if (object == null) {
            return "";
        }

        return object.optString(
                key,
                ""
        ).trim();
    }
}