package com.defragx;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SQLiteStorage implements SnapshotStore, EventStore {
    private final Connection connection;
    public SQLiteStorage(String dbPath) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS snapshots (snapshot_id TEXT PRIMARY KEY, timestamp TEXT NOT NULL, state TEXT NOT NULL, file_path TEXT NOT NULL, status TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS events (event_id TEXT PRIMARY KEY, timestamp TEXT NOT NULL, account_holder TEXT NOT NULL, account_number TEXT NOT NULL, amount TEXT NOT NULL, status TEXT NOT NULL, details TEXT NOT NULL)");
        }
    }
    @Override public void save(Snapshot s) throws SQLException {
        try (PreparedStatement ps=connection.prepareStatement("INSERT OR REPLACE INTO snapshots VALUES(?,?,?,?,?)")) {
            ps.setString(1,s.getSnapshotId()); ps.setString(2,s.getTimestamp().toString()); ps.setString(3,s.getState()); ps.setString(4,s.getFilePath()); ps.setString(5,s.getStatus()); ps.executeUpdate();
        }
    }
    @Override public Snapshot load(String id) throws SQLException {
        try (PreparedStatement ps=connection.prepareStatement("SELECT * FROM snapshots WHERE snapshot_id=?")) { ps.setString(1,id); try(ResultSet rs=ps.executeQuery()){return rs.next()?read(rs):null;} }
    }
    @Override public List<Snapshot> list() throws SQLException {
        List<Snapshot> result=new ArrayList<>(); try(Statement st=connection.createStatement(); ResultSet rs=st.executeQuery("SELECT * FROM snapshots ORDER BY timestamp DESC")){while(rs.next())result.add(read(rs));} return result;
    }
    @Override public void delete(String id) throws SQLException { try(PreparedStatement ps=connection.prepareStatement("DELETE FROM snapshots WHERE snapshot_id=?")){ps.setString(1,id);ps.executeUpdate();} }
    @Override public void saveEvent(TransactionEvent event) throws SQLException {
        try (PreparedStatement ps=connection.prepareStatement("INSERT OR REPLACE INTO events VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1,event.eventId); ps.setString(2,event.timestamp.toString()); ps.setString(3,event.accountHolder);
            ps.setString(4,event.accountNumber); ps.setString(5,event.amount); ps.setString(6,event.status); ps.setString(7,event.details); ps.executeUpdate();
        }
    }
    @Override public List<TransactionEvent> listEvents() throws SQLException {
        List<TransactionEvent> result=new ArrayList<>();
        try(Statement st=connection.createStatement(); ResultSet rs=st.executeQuery("SELECT * FROM events ORDER BY timestamp DESC, rowid DESC LIMIT 250")) {
            while(rs.next()) result.add(new TransactionEvent(rs.getString("event_id"), LocalDateTime.parse(rs.getString("timestamp")),
                    rs.getString("account_holder"), rs.getString("account_number"), rs.getString("amount"),
                    rs.getString("status"), rs.getString("details")));
        }
        return result;
    }
    private Snapshot read(ResultSet rs) throws SQLException { return new Snapshot(rs.getString("snapshot_id"), LocalDateTime.parse(rs.getString("timestamp")),rs.getString("state"),rs.getString("file_path"),rs.getString("status")); }
    @Override public void close() throws SQLException { connection.close(); }
}
