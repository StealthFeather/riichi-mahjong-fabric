package com.riichimahjong.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Completed matches only. SQL is confined to bounded workers; world-local outbox survives outages. */
public final class ReplayStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("riichi-replays");
    private static volatile Service service;
    public static final int PAGE_SIZE = 5;
    private ReplayStore() {}

    public record Summary(UUID id, long endedAt, String preset, List<ReplayRecord.Participant> participants) {
        public ReplayRecord.Participant participantAt(int seat) {
            return participants.stream().filter(p -> p.seat() == seat).findFirst().orElse(null);
        }
    }
    public record Stats(long matches, long firstPlaces, double averagePlace, long totalPoints) {}
    public record DayPage(List<Summary> matches, boolean hasNext) {}

    public static synchronized void start(MinecraftServer server) {
        close();
        service = new Service(FabricLoader.getInstance().getConfigDir().resolve("timory_quest_server.json"),
                server.getWorldPath(LevelResource.ROOT).resolve("data/riichi-replay-outbox"));
    }
    public static synchronized void close() {
        Service previous = service;
        service = null;
        if (previous != null) previous.close();
    }
    public static CompletableFuture<Void> archive(ReplayRecord record) {
        if (!record.completed()) return CompletableFuture.failedFuture(new IllegalArgumentException("Only completed replays can be archived"));
        Service current = service;
        if (current == null) return CompletableFuture.failedFuture(new IllegalStateException("Replay archive is not started"));
        return current.archive(record);
    }
    public static ZoneId zone() { Service current = service; return current == null ? ZoneId.of("Asia/Shanghai") : current.zone; }
    public static CompletableFuture<Stats> stats(UUID player) { return query(s -> s.stats(player)); }
    public static CompletableFuture<Map<LocalDate, Integer>> calendar(UUID player, YearMonth month) { return query(s -> s.calendar(player, month)); }
    public static CompletableFuture<DayPage> day(UUID player, LocalDate date, int page) { return query(s -> s.day(player, date, page)); }
    public static CompletableFuture<ReplayRecord> load(UUID player, UUID id) { return query(s -> s.load(player, id)); }
    public static CompletableFuture<JsonObject> loadAnalysis(UUID player, UUID id) { return query(s -> s.loadAnalysis(player, id)); }
    public static CompletableFuture<Void> saveAnalysis(UUID id, JsonObject analysis) {
        Service current = service;
        if (current == null) return CompletableFuture.failedFuture(new IllegalStateException("Replay store is not started"));
        String payload = analysis.toString();
        return current.submit(current.writer, () -> { current.saveAnalysis(id, payload); return null; });
    }
    private interface Query<T> { T run(Service service) throws Exception; }
    private static <T> CompletableFuture<T> query(Query<T> query) {
        Service current = service;
        if (current == null) return CompletableFuture.failedFuture(new IllegalStateException("牌谱数据库尚未启动"));
        return current.submit(current.reader, () -> query.run(current));
    }

    /** Pure configuration parser. Never generates or rewrites the Quest file or logs credentials. */
    static record Settings(String url, String username, String password, ZoneId zone) {
        static Settings read(Path file) throws IOException {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject db = root.getAsJsonObject("database");
            if (db == null) throw new IllegalArgumentException("Quest database is not configured");
            String user = string(db, "username", "");
            if (user.isBlank()) throw new IllegalArgumentException("Quest database is not configured");
            String url = string(db, "jdbc_url", "");
            if (url.isBlank()) {
                String host = string(db, "host", "127.0.0.1");
                String database = string(db, "database", "timory");
                int port = db.has("port") ? db.get("port").getAsInt() : 3306;
                if (!host.matches("[A-Za-z0-9._:-]+") || !database.matches("[A-Za-z0-9_-]{1,64}") || port < 1 || port > 65535)
                    throw new IllegalArgumentException("Invalid database address");
                url = "jdbc:mysql://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + port + "/" + database
                        + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            }
            if (!url.startsWith("jdbc:mysql:")) throw new IllegalArgumentException("Quest replay database requires MySQL");
            ZoneId zone;
            try { zone = ZoneId.of(string(root, "timezone", "Asia/Shanghai")); }
            catch (DateTimeException invalid) { zone = ZoneId.of("Asia/Shanghai"); }
            return new Settings(url, user, string(db, "password", ""), zone);
        }
        private static String string(JsonObject obj, String key, String fallback) {
            return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : fallback;
        }
        @Override public String toString() { return "Replay database settings [credentials hidden]"; }
    }

    static final class Service implements AutoCloseable {
        private final Path configFile, outbox;
        private final ThreadPoolExecutor reader = executor("Riichi-replay-read", 32);
        private final ThreadPoolExecutor writer = executor("Riichi-replay-write", 128);
        private final ScheduledExecutorService retry = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "Riichi-replay-retry"));
        private volatile ZoneId zone = ZoneId.of("Asia/Shanghai");
        private volatile Settings settings;
        @FunctionalInterface interface Connections { Connection open(Settings settings) throws Exception; }
        private final Connections connections;
        private boolean schemaReady;
        private volatile boolean closed;
        private long lastWarning;
        Service(Path configFile, Path outbox) { this(configFile, outbox, Service::jdbc); }
        Service(Path configFile, Path outbox, Connections connections) {
            this.configFile = configFile; this.outbox = outbox; this.connections = connections;
            submit(writer, () -> { retryOutbox(); return null; });
            retry.scheduleWithFixedDelay(() -> {
                if (!closed && writer.getQueue().isEmpty()) submit(writer, () -> { retryOutbox(); return null; });
            }, 30, 30, TimeUnit.SECONDS);
        }
        private static Thread daemon(Runnable r, String name) { Thread t = new Thread(r, name); t.setDaemon(true); return t; }
        private static ThreadPoolExecutor executor(String name, int capacity) {
            return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity), r -> daemon(r, name), new ThreadPoolExecutor.AbortPolicy());
        }
        private interface Checked<T> { T get() throws Exception; }
        <T> CompletableFuture<T> submit(ThreadPoolExecutor executor, Checked<T> action) {
            CompletableFuture<T> result = new CompletableFuture<>();
            try { executor.execute(() -> {
                try { result.complete(action.get()); }
                catch (Exception failure) { warn(failure); result.completeExceptionally(new IllegalStateException("牌谱数据库暂不可用，请稍后重试")); }
            }); }
            catch (RejectedExecutionException busy) { result.completeExceptionally(new IllegalStateException("牌谱请求较多，请稍后重试")); }
            return result;
        }
        private synchronized Settings settings() throws IOException {
            if (settings == null) { settings = Settings.read(configFile); zone = settings.zone(); }
            return settings;
        }
        private Connection connection() throws Exception {
            return connections.open(settings());
        }
        private static Connection jdbc(Settings config) throws Exception {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Properties properties = new Properties();
            properties.setProperty("user", config.username()); properties.setProperty("password", config.password());
            properties.setProperty("connectTimeout", "5000"); properties.setProperty("socketTimeout", "10000");
            return DriverManager.getConnection(config.url(), properties);
        }
        private synchronized void ensureSchema(Connection connection) throws SQLException {
            if (schemaReady) return;
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS riichi_replays (id VARCHAR(36) PRIMARY KEY, started_at BIGINT NOT NULL, ended_at BIGINT NOT NULL, preset VARCHAR(64) NOT NULL, payload LONGBLOB NOT NULL, INDEX riichi_replays_ended (ended_at, id))");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS riichi_replay_analysis (replay_id VARCHAR(36) PRIMARY KEY, payload LONGTEXT NOT NULL)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS riichi_replay_players (replay_id VARCHAR(36) NOT NULL, participant_key VARCHAR(80) NOT NULL, player_uuid VARCHAR(36), seat INT NOT NULL, player_name VARCHAR(128) NOT NULL, points INT NOT NULL, placement INT NOT NULL, PRIMARY KEY (replay_id, participant_key), INDEX riichi_replay_player_history (player_uuid, replay_id))");
            }
            schemaReady = true;
        }
        CompletableFuture<Void> archive(ReplayRecord record) {
            CompletableFuture<Void> persisted = new CompletableFuture<>();
            if (!record.completed()) return CompletableFuture.failedFuture(new IllegalArgumentException("Only completed replays can be archived"));
            try { writer.execute(() -> {
                Path destination = outbox.resolve(record.id() + ".nbt");
                try {
                    Files.createDirectories(outbox);
                    if (!Files.isRegularFile(destination)) {
                        Path temporary = Files.createTempFile(outbox, "replay-", ".tmp");
                        try {
                            byte[] payload = record.encode();
                            try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                                var bytes = java.nio.ByteBuffer.wrap(payload);
                                while (bytes.hasRemaining()) channel.write(bytes);
                                channel.force(true);
                            }
                            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
                            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
                        } finally { Files.deleteIfExists(temporary); }
                    }
                    // Table may now release its saved pending copy. SQL failure leaves this durable file.
                    persisted.complete(null);
                } catch (Exception failure) {
                    warn(failure); persisted.completeExceptionally(new IllegalStateException("Cannot persist replay outbox")); return;
                }
                try { save(record); Files.deleteIfExists(destination); }
                catch (Exception failure) { warn(failure); }
            }); } catch (RejectedExecutionException busy) {
                persisted.completeExceptionally(new IllegalStateException("Replay archive queue is full or stopping"));
            }
            return persisted;
        }
        private void retryOutbox() throws Exception {
            // Load timezone even when no pending files exist, without opening a database connection.
            settings();
            if (!Files.isDirectory(outbox)) return;
            try (var files = Files.list(outbox)) {
                for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".nbt")).toList()) {
                    try {
                        ReplayRecord record = ReplayRecord.decode(Files.readAllBytes(path));
                        if (!record.completed()) continue;
                        save(record); Files.delete(path);
                    } catch (Exception failure) { warn(failure); }
                }
            }
        }
        private void save(ReplayRecord record) throws Exception {
            if (!record.completed()) throw new IllegalArgumentException("Only completed replays can be archived");
            byte[] payload = record.encode();
            try (Connection connection = connection()) {
                ensureSchema(connection); connection.setAutoCommit(false);
                try {
                    try (PreparedStatement exists = connection.prepareStatement("SELECT id FROM riichi_replays WHERE id=?")) {
                        exists.setString(1, record.id().toString());
                        try (ResultSet rs = exists.executeQuery()) { if (rs.next()) { connection.rollback(); return; } }
                    }
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO riichi_replays(id,started_at,ended_at,preset,payload) VALUES(?,?,?,?,?)")) {
                        insert.setString(1, record.id().toString()); insert.setLong(2, record.startedAt()); insert.setLong(3, record.endedAt());
                        insert.setString(4, record.preset()); insert.setBytes(5, payload); insert.executeUpdate();
                    }
                    Set<String> participants = new HashSet<>();
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO riichi_replay_players(replay_id,participant_key,player_uuid,seat,player_name,points,placement) VALUES(?,?,?,?,?,?,?)")) {
                        for (ReplayRecord.Participant p : record.participants()) {
                            String key = p.uuid() == null ? "bot:" + p.seat() : p.uuid().toString();
                            if (!participants.add(key)) continue;
                            int placement = 1;
                            for (int seat = 0; seat < record.playerCount(); seat++) {
                                ReplayRecord.Participant other = record.participantAt(seat);
                                if (other != null && (other.points() > p.points() || other.points() == p.points() && other.seat() < p.seat())) placement++;
                            }
                            insert.setString(1, record.id().toString()); insert.setString(2, key); insert.setString(3, p.uuid() == null ? null : p.uuid().toString());
                            insert.setInt(4, p.seat()); insert.setString(5, p.name()); insert.setInt(6, p.points()); insert.setInt(7, placement); insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                    connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
            }
        }
        Stats stats(UUID player) throws Exception {
            try (Connection connection = connection()) {
                ensureSchema(connection);
                try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*),COALESCE(SUM(CASE WHEN placement=1 THEN 1 ELSE 0 END),0),COALESCE(AVG(placement),0),COALESCE(SUM(points),0) FROM riichi_replay_players WHERE player_uuid=?")) {
                    query.setString(1, player.toString());
                    try (ResultSet rs = query.executeQuery()) { rs.next(); return new Stats(rs.getLong(1), rs.getLong(2), rs.getDouble(3), rs.getLong(4)); }
                }
            }
        }
        Map<LocalDate, Integer> calendar(UUID player, YearMonth month) throws Exception {
            settings();
            long start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
            Map<LocalDate, Integer> result = new HashMap<>();
            try (Connection connection = connection()) {
                ensureSchema(connection);
                try (PreparedStatement query = connection.prepareStatement("SELECT r.ended_at FROM riichi_replays r JOIN riichi_replay_players p ON p.replay_id=r.id WHERE p.player_uuid=? AND r.ended_at>=? AND r.ended_at<? ORDER BY r.ended_at DESC,r.id DESC")) {
                    query.setString(1, player.toString()); query.setLong(2, start); query.setLong(3, end);
                    try (ResultSet rs = query.executeQuery()) { while (rs.next()) result.merge(Instant.ofEpochMilli(rs.getLong(1)).atZone(zone).toLocalDate(), 1, Integer::sum); }
                }
            }
            return Map.copyOf(result);
        }
        DayPage day(UUID player, LocalDate date, int page) throws Exception {
            settings();
            if (page < 0 || page > 100000) throw new IllegalArgumentException("Invalid page");
            List<Summary> result = new ArrayList<>();
            try (Connection connection = connection()) {
                ensureSchema(connection);
                try (PreparedStatement query = connection.prepareStatement("SELECT r.id,r.ended_at,r.preset FROM riichi_replays r JOIN riichi_replay_players p ON p.replay_id=r.id WHERE p.player_uuid=? AND r.ended_at>=? AND r.ended_at<? ORDER BY r.ended_at DESC,r.id DESC LIMIT ? OFFSET ?")) {
                    query.setString(1, player.toString()); query.setLong(2, date.atStartOfDay(zone).toInstant().toEpochMilli());
                    query.setLong(3, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()); query.setInt(4, PAGE_SIZE + 1); query.setInt(5, page * PAGE_SIZE);
                    try (ResultSet rs = query.executeQuery()) {
                        while (rs.next()) result.add(new Summary(UUID.fromString(rs.getString(1)), rs.getLong(2), rs.getString(3), participants(connection, rs.getString(1))));
                    }
                }
            }
            boolean next = result.size() > PAGE_SIZE;
            return new DayPage(List.copyOf(result.subList(0, Math.min(PAGE_SIZE, result.size()))), next);
        }
        private List<ReplayRecord.Participant> participants(Connection connection, String id) throws SQLException {
            List<ReplayRecord.Participant> result = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("SELECT seat,player_uuid,player_name,points FROM riichi_replay_players WHERE replay_id=? ORDER BY seat,CASE WHEN player_uuid IS NULL THEN 1 ELSE 0 END,participant_key")) {
                query.setString(1, id);
                try (ResultSet rs = query.executeQuery()) { while (rs.next()) {
                    String uuid = rs.getString(2);
                    result.add(new ReplayRecord.Participant(rs.getInt(1), uuid == null ? null : UUID.fromString(uuid), rs.getString(3), rs.getInt(4)));
                } }
            }
            return List.copyOf(result);
        }
        ReplayRecord load(UUID player, UUID id) throws Exception {
            try (Connection connection = connection()) {
                ensureSchema(connection);
                try (PreparedStatement query = connection.prepareStatement("SELECT r.payload FROM riichi_replays r JOIN riichi_replay_players p ON p.replay_id=r.id WHERE r.id=? AND p.player_uuid=?")) {
                    query.setString(1, id.toString()); query.setString(2, player.toString());
                    try (ResultSet rs = query.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Replay is not available for this player");
                        return ReplayRecord.decode(rs.getBytes(1));
                    }
                }
            }
        }
        JsonObject loadAnalysis(UUID player, UUID id) throws Exception {
            try (Connection connection = connection()) {
                ensureSchema(connection);
                try (PreparedStatement query = connection.prepareStatement("SELECT a.payload FROM riichi_replay_analysis a JOIN riichi_replay_players p ON p.replay_id=a.replay_id WHERE a.replay_id=? AND p.player_uuid=?")) {
                    query.setString(1, id.toString()); query.setString(2, player.toString());
                    try (ResultSet rs = query.executeQuery()) { return rs.next() ? JsonParser.parseString(rs.getString(1)).getAsJsonObject() : new JsonObject(); }
                }
            }
        }
        void saveAnalysis(UUID id, String payload) throws Exception {
            try (Connection connection = connection()) {
                ensureSchema(connection);
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement exists = connection.prepareStatement("SELECT id FROM riichi_replays WHERE id=?")) {
                        exists.setString(1, id.toString());
                        try (ResultSet rs = exists.executeQuery()) { if (!rs.next()) { connection.rollback(); return; } }
                    }
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM riichi_replay_analysis WHERE replay_id=?")) {
                        delete.setString(1, id.toString()); delete.executeUpdate();
                    }
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO riichi_replay_analysis(replay_id,payload) VALUES(?,?)")) {
                        insert.setString(1, id.toString()); insert.setString(2, payload); insert.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
            }
        }
        private synchronized void warn(Exception failure) {
            long now = System.currentTimeMillis();
            if (now - lastWarning < 30000) return;
            lastWarning = now;
            LOGGER.warn("Replay database unavailable ({}); completed match outbox is retained", failure instanceof SQLException sql ? "SQLSTATE=" + sql.getSQLState() : failure.getClass().getSimpleName());
        }
        @Override public void close() { closed = true; retry.shutdownNow(); reader.shutdown(); writer.shutdown(); }
    }
}
