package com.writingassistant.server;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.util.*;

/** One process owns this database. Every service operation is a short, serialized transaction. */
final class ServerDatabase implements AutoCloseable {
    interface Work<T> { T run(Sql sql) throws Exception; }
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final Connection connection;
    final Path blobs;

    ServerDatabase(Path directory) throws Exception {
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) throw new IOException("Data directory must not be a symlink");
        privatePermissions(directory, "rwx------");
        blobs = directory.resolve("documents");
        Files.createDirectories(blobs);
        if (Files.isSymbolicLink(blobs)) throw new IOException("Document directory must not be a symlink");
        privatePermissions(blobs, "rwx------");
        Path lockPath = directory.resolve("server.lock");
        Path database = directory.resolve("server.sqlite3");
        for (String name : List.of("server.lock", "server.sqlite3", "server.sqlite3-wal", "server.sqlite3-shm"))
            if (Files.isSymbolicLink(directory.resolve(name))) throw new IOException("Unsafe data file");
        lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (Exception e) { lockChannel.close(); throw new IOException("Server data directory is already in use", e); }
        if (acquired == null) { lockChannel.close(); throw new IOException("Server data directory is already in use"); }
        lock = acquired;
        Connection opened = null;
        try {
            opened = DriverManager.getConnection("jdbc:sqlite:" + database);
            connection = opened;
            privatePermissions(database, "rw-------");
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("PRAGMA secure_delete=ON");
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=FULL");
                st.execute("PRAGMA journal_size_limit=0");
                                st.execute("PRAGMA max_page_count=524288");
                st.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
            }
            migrate();
        } catch (Exception e) {
            if (opened != null) opened.close();
            lock.release(); lockChannel.close(); throw e;
        }
    }

    private void migrate() throws Exception {
        transaction(sql -> {
            long current = sql.number("SELECT COALESCE(MAX(version),0) FROM schema_migrations");
            if (current > 1) throw new IOException("Database schema is newer than this server");
            if (current < 1) {
                try (var input = ServerDatabase.class.getResourceAsStream("/db/V001__server.sql")) {
                    if (input == null) throw new IOException("Missing database migration");
                    for (String statement : new String(input.readAllBytes(), StandardCharsets.UTF_8).split(";"))
                        if (!statement.isBlank()) sql.execute(statement);
                }
                sql.execute("INSERT INTO schema_migrations(version,applied_at) VALUES(1,?)", Service.now());
            }
            return null;
        });
    }

    synchronized <T> T transaction(Work<T> work) throws Exception {
        connection.setAutoCommit(false);
        try {
            T result = work.run(new Sql(connection));
            connection.commit();
            return result;
        } catch (Exception e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }

    synchronized void checkpoint() throws SQLException {
        try (Statement st = connection.createStatement()) { st.execute("PRAGMA wal_checkpoint(TRUNCATE)"); }
    }

    static void privatePermissions(Path path, String mode) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode));
    }

    @Override public synchronized void close() throws Exception {
        try { connection.close(); } finally { try { lock.release(); } finally { lockChannel.close(); } }
    }

    static final class Sql {
        private final Connection connection;
        Sql(Connection connection) { this.connection = connection; }
        private PreparedStatement prepare(String query, Object... values) throws SQLException {
            PreparedStatement st = connection.prepareStatement(query);
            for (int i = 0; i < values.length; i++) st.setObject(i + 1, values[i]);
            return st;
        }
        int execute(String query, Object... values) throws SQLException {
            try (PreparedStatement st = prepare(query, values)) { return st.executeUpdate(); }
        }
        List<Map<String,Object>> rows(String query, Object... values) throws SQLException {
            try (PreparedStatement st = prepare(query, values); ResultSet rs = st.executeQuery()) {
                List<Map<String,Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        }
        Map<String,Object> one(String query, Object... values) throws SQLException {
            List<Map<String,Object>> rows = rows(query, values);
            return rows.isEmpty() ? null : rows.get(0);
        }
        long number(String query, Object... values) throws SQLException {
            Map<String,Object> row = one(query, values);
            return row == null ? 0 : ((Number) row.values().iterator().next()).longValue();
        }
    }
}
