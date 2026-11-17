package com.defragx;

import java.util.List;

public class SnapshotManager {
    private final RuntimeMonitor monitor;
    private final StorageManager storage;
    public SnapshotManager(RuntimeMonitor monitor, StorageManager storage) { this.monitor=monitor; this.storage=storage; }
    public Snapshot createSnapshot() throws Exception {
        return createSnapshot("");
    }
    public Snapshot createSnapshot(String bankingState) throws Exception {
        return createSnapshot(bankingState,"ACTIVE");
    }
    private Snapshot createSnapshot(String bankingState,String status) throws Exception {
        RuntimeMetrics m=monitor.collectMetrics();
        String state="timestamp="+m.timestamp()+"; memoryPercent="+m.memoryUsage()+"; cpuFraction="+m.cpuUsage()+"; threads="+m.threadCount()+"; gc="+m.gcInfo()+"; jvm="+monitor.getJvmInfo()+"; bankingState="+bankingState;
        Snapshot snapshot=new Snapshot(state, "");
        snapshot.setStatus(status);
        storage.saveSnapshot(snapshot); return snapshot;
    }
    public Snapshot createRecoveryCheckpoint(String bankingState) throws Exception {
        return createSnapshot(bankingState,"RECOVERY_CHECKPOINT");
    }
    public Snapshot createManualSnapshot(String bankingState) throws Exception {
        return createSnapshot(bankingState,"MANUAL");
    }
    public void markIncidentState(Snapshot snapshot) throws Exception {
        snapshot.setStatus("INCIDENT_STATE");
        storage.saveSnapshot(snapshot);
    }
    public List<Snapshot> getSnapshots() throws Exception { return storage.getSnapshots(); }
    public Snapshot selectSnapshot(String id) throws Exception { return storage.loadSnapshot(id); }
    public void markRestored(Snapshot snapshot) throws Exception { snapshot.setStatus("RESTORED"); storage.saveSnapshot(snapshot); }
    public void resolveRecoveryCheckpoints() throws Exception {
        for(Snapshot snapshot:storage.getSnapshots())if("RECOVERY_CHECKPOINT".equals(snapshot.getStatus()))markRestored(snapshot);
    }
    public void deleteSnapshot(String id) throws Exception { storage.deleteSnapshot(id); }
}
