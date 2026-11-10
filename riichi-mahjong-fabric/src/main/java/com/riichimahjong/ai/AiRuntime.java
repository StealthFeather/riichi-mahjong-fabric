package com.riichimahjong.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;

/** Two bounded background workers; Node is never launched or waited for on a server tick. */
public final class AiRuntime {
    private static final System.Logger LOG = System.getLogger("RiichiMahjongAI");
    private static final AtomicLong IDS = new AtomicLong();
    private static final AtomicLong THREAD_IDS = new AtomicLong();
    private static final LongAdder SUBMITTED = new LongAdder(), SUCCESS = new LongAdder(), FALLBACK = new LongAdder(), TIMEOUT = new LongAdder();
    private static final Map<String, LongAdder> LEVEL_SUCCESS = Map.of("EASY", new LongAdder(), "NORMAL", new LongAdder(), "HARD", new LongAdder());
    private static final Set<Worker> WORKERS = ConcurrentHashMap.newKeySet();
    private static final Set<CompletableFuture<Integer>> PENDING = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Worker> LOCAL = ThreadLocal.withInitial(() -> { var w = new Worker(); WORKERS.add(w); return w; });
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), r -> { var t = new Thread(r, "riichi-ai-" + THREAD_IDS.incrementAndGet()); t.setDaemon(true); return t; });
    private static final ScheduledThreadPoolExecutor WATCHDOG = new ScheduledThreadPoolExecutor(1,
            r -> { var t = new Thread(r, "riichi-ai-timeouts"); t.setDaemon(true); return t; });
    private static volatile String status = "电将 AI 待命";
    private static volatile long retryAfterNanos;
    private static final AtomicLong LAST_LOG = new AtomicLong();
    private static Path extracted;

    static { WATCHDOG.setRemoveOnCancelPolicy(true); }
    private AiRuntime() {}

    public static String status() { return status; }
    public static Map<String, Long> metrics() {
        return Map.of("submitted", SUBMITTED.sum(), "engineSuccess", SUCCESS.sum(), "fallback", FALLBACK.sum(), "timeout", TIMEOUT.sum(),
                "engineEasy", LEVEL_SUCCESS.get("EASY").sum(), "engineNormal", LEVEL_SUCCESS.get("NORMAL").sum(), "engineHard", LEVEL_SUCCESS.get("HARD").sum());
    }

    public static CompletableFuture<Integer> choose(JsonObject snapshot) {
        // Ownership transfers to this method; copying also prevents a caller changing a queued request.
        JsonObject input = snapshot.deepCopy();
        long id = IDS.incrementAndGet();
        input.addProperty("id", id);
        int emergency = FallbackPolicy.cheapIndex(input);
        var result = new DecisionFuture();
        var running = new AtomicReference<Worker>();
        long timeoutMillis = Math.max(100, Math.min(10_000, Long.getLong("riichi.ai.timeoutMillis", 1500L)));
        SUBMITTED.increment();
        if (EXECUTOR.isShutdown()) return degraded(result, emergency, "AI 已关闭，使用基础牌效");
        PENDING.add(result);
        java.util.concurrent.ScheduledFuture<?> timeout;
        try { timeout = WATCHDOG.schedule(() -> {
            boolean timedOut;
            synchronized (result) {
                timedOut = !result.isDone();
                if (timedOut) {
                    FALLBACK.increment(); TIMEOUT.increment();
                    status = "AI 计算超时，使用基础牌效";
                    result.complete(emergency);
                }
            }
            if (timedOut) {
                Worker worker = running.get();
                if (worker != null) worker.cancel(id);
                warn("AI decision exceeded " + timeoutMillis + " ms; using a legal fallback");
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS); }
        catch (RejectedExecutionException stopped) { PENDING.remove(result); return degraded(result, emergency, "AI 已关闭，使用基础牌效"); }
        result.whenComplete((value, error) -> {
            PENDING.remove(result);
            timeout.cancel(false);
            if (result.isCancelled()) { Worker worker = running.get(); if (worker != null) worker.cancel(id); }
        });
        try {
            EXECUTOR.execute(() -> {
                if (result.isDone()) return;
                if (System.nanoTime() < retryAfterNanos) {
                    int fallback = FallbackPolicy.chooseIndex(input);
                    degraded(result, fallback, "AI 暂不可用，使用基础牌效");
                    return;
                }
                Worker worker = LOCAL.get();
                running.set(worker);
                if (result.isDone()) return;
                try {
                    int index = worker.request(input, result::isDone);
                    boolean allowed = false;
                    for (var element : input.getAsJsonArray("legal")) if (element.getAsJsonObject().get("index").getAsInt() == index) allowed = true;
                    if (!allowed) throw new IOException("AI returned an index outside the current legal actions");
                    synchronized (result) {
                        if (!result.isDone()) {
                            SUCCESS.increment(); LEVEL_SUCCESS.get(input.get("difficulty").getAsString()).increment();
                            status = "电将 AI 正常"; retryAfterNanos = 0;
                            result.complete(index);
                        }
                    }
                } catch (Exception error) {
                    worker.kill();
                    if (!result.isDone()) {
                        retryAfterNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                        warn("AI unavailable (" + error.getClass().getSimpleName() + "); using a legal fallback. Configure riichi.ai.node / RIICHI_AI_NODE if Node.js is not on PATH.");
                        int fallback;
                        try { fallback = FallbackPolicy.chooseIndex(input); }
                        catch (RuntimeException ignored) { fallback = emergency; }
                        degraded(result, fallback, "AI 暂不可用，使用基础牌效");
                    }
                } finally { running.set(null); }
            });
        } catch (RejectedExecutionException rejected) { degraded(result, emergency, "AI 队列已满，使用基础牌效"); }
        return result;
    }

    private static CompletableFuture<Integer> degraded(CompletableFuture<Integer> result, int index, String message) {
        synchronized (result) {
            if (!result.isDone()) { FALLBACK.increment(); status = message; result.complete(index); }
        }
        return result;
    }

    /** Serialize cancellation with accounting, so observing completion also observes its metrics. */
    private static final class DecisionFuture extends CompletableFuture<Integer> {
        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (this) { return super.cancel(mayInterruptIfRunning); }
        }
    }

    private static void warn(String message) {
        long now = System.nanoTime(), previous = LAST_LOG.get();
        if ((previous == 0 || now - previous > TimeUnit.SECONDS.toNanos(30)) && LAST_LOG.compareAndSet(previous, now)) LOG.log(System.Logger.Level.WARNING, message);
    }

    public static void shutdown() {
        PENDING.forEach(future -> future.cancel(false));
        EXECUTOR.shutdownNow();
        WATCHDOG.shutdownNow();
        WORKERS.forEach(Worker::kill);
        WORKERS.clear();
        status = "AI 已关闭";
    }

    private static synchronized Path workerPath() throws Exception {
        String configured = System.getProperty("riichi.ai.worker", "");
        if (!configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        if (extracted != null) return extracted;
        InputStream manifestStream = AiRuntime.class.getResourceAsStream("/ai/runtime-files.txt");
        if (manifestStream == null) {
            Path development = Path.of("ai", "worker.cjs").toAbsolutePath().normalize();
            if (Files.isRegularFile(development)) return development;
            throw new IOException("Bundled AI runtime manifest is missing");
        }
        String manifest;
        try (manifestStream) { manifest = new String(manifestStream.readAllBytes(), StandardCharsets.UTF_8); }
        var resources = new LinkedHashMap<String, byte[]>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String entry : manifest.split("\\R")) {
            String name = entry.trim();
            if (name.isEmpty() || name.startsWith("#")) continue;
            if (name.contains("\\") || name.startsWith("/") || Arrays.asList(name.split("/")).contains("..")) throw new IOException("Invalid runtime resource path");
            try (InputStream resource = AiRuntime.class.getResourceAsStream("/ai/" + name)) {
                if (resource == null) throw new IOException("Missing AI runtime resource " + name);
                byte[] bytes = resource.readAllBytes();
                resources.put(name, bytes); digest.update(name.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0); digest.update(bytes);
            }
        }
        Path root = Path.of(System.getProperty("user.dir"), "config", "riichi-mahjong-ai", "runtime", HexFormat.of().formatHex(digest.digest()).substring(0, 20)).toAbsolutePath().normalize();
        for (var entry : resources.entrySet()) {
            Path destination = root.resolve(entry.getKey()).normalize();
            if (!destination.startsWith(root)) throw new IOException("Runtime resource escaped extraction root");
            Files.createDirectories(destination.getParent());
            if (Files.isRegularFile(destination) && Arrays.equals(Files.readAllBytes(destination), entry.getValue())) continue;
            Path temporary = Files.createTempFile(destination.getParent(), ".riichi-ai-", ".tmp");
            try { Files.write(temporary, entry.getValue()); Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            finally { Files.deleteIfExists(temporary); }
        }
        Path worker = root.resolve("worker.cjs");
        if (!Files.isRegularFile(worker)) throw new IOException("AI runtime manifest does not contain worker.cjs");
        extracted = worker;
        return worker;
    }

    private static final class Worker {
        private volatile Process process;
        private BufferedReader output;
        private BufferedWriter input;
        private long activeId = -1;

        int request(JsonObject snapshot, BooleanSupplier done) throws Exception {
            if (done.getAsBoolean()) throw new IOException("Decision cancelled");
            long id = snapshot.get("id").getAsLong();
            synchronized (this) { activeId = id; }
            try {
                Process current = process;
                if (current == null || !current.isAlive()) {
                    Path worker = workerPath();
                    String node = System.getProperty("riichi.ai.node", System.getenv().getOrDefault("RIICHI_AI_NODE", "node"));
                    Process started = new ProcessBuilder(node, worker.toString()).directory(worker.getParent().toFile())
                            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    synchronized (this) {
                        // A cancelled launch must never publish its process into a later request.
                        if (activeId != id || done.getAsBoolean()) { started.destroyForcibly(); throw new IOException("Decision cancelled"); }
                        process = started;
                        output = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8));
                        input = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
                    }
                }
                if (done.getAsBoolean()) { cancel(id); throw new IOException("Decision cancelled"); }
                input.write(snapshot.toString()); input.newLine(); input.flush();
                String line = readLine(output);
                if (line == null) throw new IOException("AI worker exited without a response");
                JsonObject reply = JsonParser.parseString(line).getAsJsonObject();
                if (!reply.has("id") || reply.get("id").getAsBigDecimal().longValueExact() != snapshot.get("id").getAsLong()) throw new IOException("AI response id mismatch");
                if (reply.has("error") || !reply.has("index") || reply.get("index").isJsonNull()) throw new IOException("AI worker could not choose an action");
                return reply.get("index").getAsBigDecimal().intValueExact();
            } finally {
                synchronized (this) { if (activeId == id) activeId = -1; }
            }
        }

        synchronized void cancel(long id) { if (activeId == id) { activeId = -1; kill(); } }
        synchronized void kill() { Process current = process; process = null; if (current != null) current.destroyForcibly(); }

        private static String readLine(BufferedReader reader) throws IOException {
            var line = new StringBuilder();
            for (int next; (next = reader.read()) >= 0;) {
                if (next == '\n') return line.toString();
                if (next != '\r') line.append((char) next);
                if (line.length() > 65_536) throw new IOException("AI worker response exceeded limit");
            }
            return line.isEmpty() ? null : line.toString();
        }
    }
}
