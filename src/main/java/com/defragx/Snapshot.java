package com.defragx;

import java.time.LocalDateTime;
import java.util.UUID;

public class Snapshot {
    private final String snapshotId;
    private final LocalDateTime timestamp;
    private final String state;
    private final String filePath;
    private String status;

    public Snapshot(String state, String filePath) {
        this(UUID.randomUUID().toString(), LocalDateTime.now(), state, filePath, "ACTIVE");
    }
    public Snapshot(String snapshotId, LocalDateTime timestamp, String state, String filePath, String status) {
        this.snapshotId = snapshotId; this.timestamp = timestamp; this.state = state;
        this.filePath = filePath; this.status = status;
    }
    public String getSnapshotId() { return snapshotId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getState() { return state; }
    public String getFilePath() { return filePath; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetails() { return snapshotId + " | " + timestamp + " | " + status + " | " + filePath; }
}
