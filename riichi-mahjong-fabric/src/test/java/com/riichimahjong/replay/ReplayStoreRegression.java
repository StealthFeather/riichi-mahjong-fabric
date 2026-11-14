package com.riichimahjong.replay;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** In-process H2/MySQL-mode regression. Does not read live configuration or contact any server. */
public final class ReplayStoreRegression {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static ReplayRecord match(UUID id, UUID player, long ended, String name) {
        CompoundTag driver = new CompoundTag(), phase = new CompoundTag(), game = new CompoundTag();
        phase.putString("type", "MATCH_ENDED"); game.putInt("playerCount", 4);
        driver.put("phase", phase); driver.put("match", game);
        return new ReplayRecord(id, ended - 1000, ended, "MAHJONG_SOUL_4P", List.of(
                new ReplayRecord.Participant(0, player, name, 35000),
                new ReplayRecord.Participant(0, player, name, 35000), // Duplicate admission must not duplicate access/stats.
                new ReplayRecord.Participant(1, null, "BOT", 30000),
                new ReplayRecord.Participant(2, null, "BOT", 20000),
                new ReplayRecord.Participant(3, null, "BOT", 15000)),
                List.of(new ReplayRecord.Frame(0, "终局", driver)));
    }
    private static int rows(String database, String table) throws Exception {
        try (Connection c = DriverManager.getConnection(database); Statement q = c.createStatement(); ResultSet r = q.executeQuery("SELECT COUNT(*) FROM " + table)) { r.next(); return r.getInt(1); }
    }
    private static void awaitRows(String database, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try { if (rows(database, "riichi_replays") == expected) return; } catch (SQLException ignored) {}
            Thread.sleep(10);
        }
        throw new AssertionError("Archive did not reach expected row count " + expected);
    }
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        Path directory = Files.createTempDirectory(Path.of(args[0]), "riichi-replay-jdbc-");
        Path config = directory.resolve("quest.json");
        Files.writeString(config, "{\"timezone\":\"Pacific/Kiritimati\",\"database\":{\"jdbc_url\":\"jdbc:mysql://unused.invalid/test\",\"username\":\"test\",\"password\":\"example-secret\"}}");
        check(!ReplayStore.Settings.read(config).toString().contains("example-secret"), "Settings must redact credentials");
        String database = "jdbc:h2:mem:replay_test;MODE=MySQL;DB_CLOSE_DELAY=-1";
        UUID player = UUID.randomUUID(), stranger = UUID.randomUUID();
        long ended = Instant.parse("2026-11-14T12:05:00Z").toEpochMilli();
        LocalDate localDate = LocalDate.of(2026, 11, 15);
        List<ReplayRecord> records = new ArrayList<>();
        try (ReplayStore.Service store = new ReplayStore.Service(config, directory.resolve("outbox"), ignored -> DriverManager.getConnection(database))) {
            // Fresh service: date boundaries must use the configured zone on the first query.
            check(store.calendar(player, YearMonth.of(2026, 11)).isEmpty(), "Fresh schema/calendar");
            for (int n = 0; n < 7; n++) {
                ReplayRecord record = match(UUID.randomUUID(), player, ended + n * 1000L, "Player"); records.add(record);
                store.archive(record).get(5, TimeUnit.SECONDS);
            }
            ReplayRecord other = match(UUID.randomUUID(), stranger, ended, "Other");
            store.archive(other).get(5, TimeUnit.SECONDS);
            awaitRows(database, 8);
            store.archive(records.getFirst()).get(5, TimeUnit.SECONDS);
            awaitRows(database, 8);
            check(store.stats(player).matches() == 7, "UUID idempotency and duplicate participant deduplication");
            check(store.calendar(player, YearMonth.of(2026, 11)).get(localDate) == 7, "Timezone and participant month filter");
            check(!store.calendar(player, YearMonth.of(2026, 11)).containsKey(localDate.minusDays(1)), "UTC must not determine archive date");
            ReplayStore.DayPage first = store.day(player, localDate, 0), second = store.day(player, localDate, 1);
            check(first.matches().size() == 5 && first.hasNext(), "First page size and continuation");
            check(second.matches().size() == 2 && !second.hasNext(), "Last page size and continuation");
            check(first.matches().getFirst().id().equals(records.getLast().id()), "Descending finish time");
            check(first.matches().getFirst().participants().size() == 4, "Seat scores loaded without duplicate participant");
            check(store.load(player, records.getFirst().id()).id().equals(records.getFirst().id()), "Authorized full payload");
            boolean denied = false;
            try { store.load(stranger, records.getFirst().id()); } catch (IllegalArgumentException expected) { denied = true; }
            check(denied, "Other players cannot load a private replay");
            JsonObject analysis = new JsonObject(); analysis.addProperty("quality", 99);
            store.saveAnalysis(records.getFirst().id(), analysis.toString());
            check(store.loadAnalysis(player, records.getFirst().id()).get("quality").getAsInt() == 99, "Analysis persistence");
            check(store.loadAnalysis(stranger, records.getFirst().id()).isEmpty(), "Analysis participant filter");
            ReplayRecord invalid = match(UUID.randomUUID(), player, ended, "x".repeat(129));
            store.archive(invalid).get(5, TimeUnit.SECONDS); // Durable acknowledgment precedes SQL and survives failure.
            long wait = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < wait && !Files.isRegularFile(directory.resolve("outbox/" + invalid.id() + ".nbt"))) Thread.sleep(10);
            check(Files.isRegularFile(directory.resolve("outbox/" + invalid.id() + ".nbt")), "Failed transaction retains outbox");
        }
        // Constructor schema creation must be idempotent, and the failed player insert must roll back its parent replay.
        try (ReplayStore.Service reopened = new ReplayStore.Service(config, directory.resolve("empty-outbox"), ignored -> DriverManager.getConnection(database))) {
            check(reopened.stats(player).matches() == 7 && rows(database, "riichi_replays") == 8, "Schema re-open and transactional rollback");
        }
        Path failedOutbox = directory.resolve("offline-outbox");
        ReplayRecord pending = match(UUID.randomUUID(), player, ended, "Player");
        try (ReplayStore.Service offline = new ReplayStore.Service(config, failedOutbox, ignored -> { throw new SQLException("offline", "08001"); })) {
            offline.archive(pending).get(5, TimeUnit.SECONDS);
            check(Files.isRegularFile(failedOutbox.resolve(pending.id() + ".nbt")), "SQL outage still acknowledges durable outbox");
        }
        try (ReplayStore.Service recovered = new ReplayStore.Service(config, failedOutbox, ignored -> DriverManager.getConnection(database))) {
            awaitRows(database, 9);
            check(recovered.load(player, pending.id()).id().equals(pending.id()), "Restart recovers pending completed replay");
        }
        try (ReplayStore.Service missingConfig = new ReplayStore.Service(directory.resolve("absent.json"), directory.resolve("unconfigured-outbox"), ignored -> { throw new AssertionError("Must not connect without config"); })) {
            missingConfig.archive(pending).get(5, TimeUnit.SECONDS);
            check(Files.isRegularFile(directory.resolve("unconfigured-outbox/" + pending.id() + ".nbt")), "No configured database still preserves completed replay");
            check(!Files.exists(directory.resolve("absent.json")), "Quest configuration is never generated");
        }
        check(ReplayStore.archive(pending).isCompletedExceptionally(), "Archive before server start must not acknowledge persistence");
        Path blocked = directory.resolve("blocked-outbox"); Files.writeString(blocked, "not a directory");
        try (ReplayStore.Service blockedStore = new ReplayStore.Service(config, blocked, ignored -> DriverManager.getConnection(database))) {
            boolean failed = false;
            try { blockedStore.archive(pending).get(5, TimeUnit.SECONDS); } catch (java.util.concurrent.ExecutionException expected) { failed = true; }
            check(failed, "Disk write failure must not acknowledge persistence");
        }
        ReplayStore.Service stopped = new ReplayStore.Service(config, directory.resolve("stopped-outbox"), ignored -> DriverManager.getConnection(database));
        stopped.close();
        check(stopped.archive(pending).isCompletedExceptionally(), "Stopped executor must not acknowledge persistence");
        try (Connection version = DriverManager.getConnection(database)) { System.out.println("H2 version: " + version.getMetaData().getDatabaseProductVersion()); }
        System.out.println("ReplayStoreRegression: " + checks + " checks passed (isolated H2; no live DB).");
    }
}
