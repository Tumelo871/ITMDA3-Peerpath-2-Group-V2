package com.example.peerpath.models;

/**
 * A study resource uploaded and tagged to a specific module code.
 * File itself lives in Firebase Storage; this document just indexes it
 * so it can be searched/filtered by module in Firestore.

 * Stored in Firestore collection: "resources/{resourceId}"
 */
public class Resource {

    private String resourceId;
    private String moduleCode;
    private String title;
    private String description;
    private String fileUrl;      // Firebase Storage download URL
    private String uploadedByUid;
    private long uploadedAt;

    public Resource() {
    }

    public Resource(String moduleCode, String title, String description, String fileUrl, String uploadedByUid) {
        this.moduleCode = moduleCode;
        this.title = title;
        this.description = description;
        this.fileUrl = fileUrl;
        this.uploadedByUid = uploadedByUid;
        this.uploadedAt = System.currentTimeMillis();
    }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getUploadedByUid() { return uploadedByUid; }
    public void setUploadedByUid(String uploadedByUid) { this.uploadedByUid = uploadedByUid; }

    public long getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(long uploadedAt) { this.uploadedAt = uploadedAt; }
}
