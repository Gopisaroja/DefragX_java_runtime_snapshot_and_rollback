package com.defragx;

import java.time.LocalDateTime;
import java.util.UUID;

public class RollbackManager {
    private String rollbackId;
    private String reason;
    private String status="IDLE";
    private final SnapshotManager snapshots;
    private Snapshot restoredSnapshot;
    private LocalDateTime recoveredAt;
    public RollbackManager(SnapshotManager snapshots) { this.snapshots=snapshots; }
    public void rollback(String snapshotId, String reason) throws Exception {
        rollbackId=UUID.randomUUID().toString(); this.reason=reason;
        Snapshot selected=snapshots.selectSnapshot(snapshotId);
        if(selected==null) { status="FAILED"; throw new IllegalArgumentException("Snapshot not found: "+snapshotId); }
        boolean manual="MANUAL".equals(selected.getStatus());
        restoreSnapshot(selected);
        if(!manual)snapshots.markRestored(selected);
    }
    public void restoreSnapshot(Snapshot snapshot) { restoredSnapshot=snapshot; if(!"MANUAL".equals(snapshot.getStatus()))snapshot.setStatus("RESTORED"); recoveredAt=LocalDateTime.now(); status="RECOVERED"; }
    public String recordRecovery() { return "Recovery "+rollbackId+" | "+status+" | "+reason+" | "+recoveredAt; }
    public String getStatus() { return status; }
    public Snapshot getRestoredSnapshot() { return restoredSnapshot; }
}
