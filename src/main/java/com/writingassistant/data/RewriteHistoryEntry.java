package com.writingassistant.data;

import java.util.UUID;

/**
 * Represents a persistent log entry of an accepted style rewrite action.
 */
public class RewriteHistoryEntry {

    private String id;
    private String documentId;
    private String originalText;
    private String mode;
    private String chosenAlternative;
    private long createdAt;

    public RewriteHistoryEntry() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    public RewriteHistoryEntry(String id, String documentId, String originalText, String mode,
                               String chosenAlternative, long createdAt) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        this.documentId = documentId;
        this.originalText = (originalText != null) ? originalText : "";
        this.mode = (mode != null) ? mode : "CLARITY";
        this.chosenAlternative = (chosenAlternative != null) ? chosenAlternative : "";
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public RewriteHistoryEntry(String documentId, String originalText, String mode, String chosenAlternative) {
        this(UUID.randomUUID().toString(), documentId, originalText, mode, chosenAlternative, System.currentTimeMillis());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getChosenAlternative() {
        return chosenAlternative;
    }

    public void setChosenAlternative(String chosenAlternative) {
        this.chosenAlternative = chosenAlternative;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
