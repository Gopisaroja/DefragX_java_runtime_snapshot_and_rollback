package com.defragx;

import java.util.List;

public interface SnapshotStore extends AutoCloseable {
    void save(Snapshot snapshot) throws Exception;
    Snapshot load(String id) throws Exception;
    List<Snapshot> list() throws Exception;
    void delete(String id) throws Exception;
    @Override default void close() throws Exception { }
}
