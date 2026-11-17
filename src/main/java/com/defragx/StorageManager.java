package com.defragx;

import java.util.List;

public class StorageManager implements AutoCloseable {
    private final SnapshotStore store;
    public StorageManager(String storageType, String storagePath) throws Exception {
        store = "SQLITE".equalsIgnoreCase(storageType) ? new SQLiteStorage(storagePath) : new JSONStorage(storagePath);
    }
    public void saveSnapshot(Snapshot snapshot) throws Exception { store.save(snapshot); }
    public Snapshot loadSnapshot(String id) throws Exception { return store.load(id); }
    public List<Snapshot> getSnapshots() throws Exception { return store.list(); }
    public void deleteSnapshot(String id) throws Exception { store.delete(id); }
    public void saveEvent(TransactionEvent event) throws Exception {
        if (!(store instanceof EventStore events)) throw new UnsupportedOperationException("Event persistence requires SQLite storage");
        events.saveEvent(event);
    }
    public List<TransactionEvent> getEvents() throws Exception {
        if (!(store instanceof EventStore events)) throw new UnsupportedOperationException("Event persistence requires SQLite storage");
        return events.listEvents();
    }
    @Override public void close() throws Exception { store.close(); }
}
