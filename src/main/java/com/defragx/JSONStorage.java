package com.defragx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JSONStorage implements SnapshotStore {
    private final Path directory;
    public JSONStorage(String filePath) throws IOException { directory = Path.of(filePath); Files.createDirectories(directory); }
    @Override public void save(Snapshot s) throws IOException { Files.writeString(path(s.getSnapshotId()), encode(s), StandardCharsets.UTF_8); }
    @Override public Snapshot load(String id) throws IOException {
        Path file = path(id); if (!Files.exists(file)) return null;
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return new Snapshot(value(json,"snapshotId"), LocalDateTime.parse(value(json,"timestamp")),
                value(json,"state"), value(json,"filePath"), value(json,"status"));
    }
    @Override public List<Snapshot> list() throws IOException {
        List<Snapshot> result = new ArrayList<>();
        try (var files = Files.list(directory)) { for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) { Snapshot s = load(p.getFileName().toString().replaceFirst("\\.json$", "")); if (s != null) result.add(s); } }
        result.sort(Comparator.comparing(Snapshot::getTimestamp).reversed()); return result;
    }
    @Override public void delete(String id) throws IOException { Files.deleteIfExists(path(id)); }
    private Path path(String id) { return directory.resolve(id.replaceAll("[^a-zA-Z0-9-]", "") + ".json"); }
    private String encode(Snapshot s) { return "{\n  \"snapshotId\":\""+esc(s.getSnapshotId())+"\",\n  \"timestamp\":\""+s.getTimestamp()+"\",\n  \"state\":\""+esc(s.getState())+"\",\n  \"filePath\":\""+esc(s.getFilePath())+"\",\n  \"status\":\""+esc(s.getStatus())+"\"\n}\n"; }
    private String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    private String value(String json, String key) throws IOException { Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(json); if(!m.find()) throw new IOException("Missing JSON field: "+key); return m.group(1).replace("\\n","\n").replace("\\\"","\"").replace("\\\\","\\"); }
}
