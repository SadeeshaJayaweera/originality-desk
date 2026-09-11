package com.writingassistant.data;

import java.util.UUID;

/**
 * Represents persistent metadata for a user document tracked in SQLite.
 */
public class DocumentRecord {

    private String id;
    private String title;
    private String filePath;
    private long lastOpened;
    private long createdAt;

    public DocumentRecord() {
        this.id = UUID.randomUUID().toString();
        this.title = "Untitled Document";
        this.createdAt = System.currentTimeMillis();
        this.lastOpened = this.createdAt;
    }

    public DocumentRecord(String id, String title, String filePath, long lastOpened, long createdAt) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        this.title = (title != null && !title.isBlank()) ? title : "Untitled Document";
        this.filePath = filePath;
        this.lastOpened = lastOpened > 0 ? lastOpened : System.currentTimeMillis();
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public DocumentRecord(String title, String filePath) {
        this(UUID.randomUUID().toString(), title, filePath, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getLastOpened() {
        return lastOpened;
    }

    public void setLastOpened(long lastOpened) {
        this.lastOpened = lastOpened;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return title + (filePath != null ? " (" + filePath + ")" : "");
    }
}
