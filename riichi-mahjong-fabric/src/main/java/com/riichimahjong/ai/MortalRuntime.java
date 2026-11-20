package com.riichimahjong.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.riichimahjong.mahjongtable.MatchLength;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/** One resident CPU model shared by tables. Each request reconstructs its own seat perspective. */
public final class MortalRuntime {
    private static final System.Logger LOG = System.getLogger("RiichiMortal");
    private static final AtomicLong IDS = new AtomicLong(), LAST_WARNING = new AtomicLong();
    private static final LongAdder SUCCESS = new LongAdder(), FALLBACK = new LongAdder(),
            TIMEOUT = new LongAdder(), MISSING_HISTORY = new LongAdder();
    private static final Set<CompletableFuture<Integer>> PENDING = ConcurrentHashMap.newKeySet();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), r -> Thread.ofPlatform().daemon().name("riichi-mortal").unstarted(r));
    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1,
            r -> Thread.ofPlatform().daemon().name("riichi-mortal-timeouts").unstarted(r));
    private static final AiWorker WORKER = new AiWorker(MortalRuntime::start, "Mortal");
    private static final AiWorker REPLAY_WORKER = new AiWorker(MortalRuntime::start, "Mortal");
    private static final AtomicLong REPLAY_IDS = new AtomicLong();
    private static volatile boolean replayClosed;
    private static volatile long retryAfterNanos;
    private static volatile String status = "Mortal 待命";

    static { TIMER.setRemoveOnCancelPolicy(true); }
    private MortalRuntime() {}

    private static Process start() throws IOException {
        String configured = System.getProperty("riichi.mortal.worker", System.getenv().getOrDefault(
                "RIICHI_MORTAL_WORKER", "config/riichi-mahjong-ai/mortal/worker"));
        Path worker = Path.of(configured).toAbsolutePath().normalize();
        return new ProcessBuilder(worker.toString()).directory(worker.getParent().toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
    }

    public static Map<String, Long> metrics() {
        return Map.of("submitted", IDS.get(), "engineSuccess", SUCCESS.sum(), "fallback", FALLBACK.sum(),
                "timeout", TIMEOUT.sum(), "missingHistory", MISSING_HISTORY.sum());
    }

    public static String status() { return status; }

    /** The snapshot and journal are fresh, detached, and handed off by the server thread. */
    public static CompletableFuture<Integer> choose(JsonObject snapshot, JsonArray events, MatchLength length) {
        long id = IDS.incrementAndGet();
        if (events.isEmpty()) {
            MISSING_HISTORY.increment();
            FALLBACK.increment();
            status = "本手缺少 Mortal 牌谱，使用电将 AI";
            return AiRuntime.choose(snapshot);
        }

        var request = request(id, snapshot, events, length);
        var model = new CompletableFuture<Integer>();
        var result = new CompletableFuture<Integer>();
        // Cancellation follows the current engine through handover, unload, and strategy fallback.
        var active = new AtomicReference<>(model);
        PENDING.add(result);
        result.whenComplete((value, error) -> {
            PENDING.remove(result);
            if (result.isCancelled()) active.get().cancel(false);
        });
        model.whenComplete((index, error) -> {
            if (result.isDone()) return;
            if (error == null) {
                SUCCESS.increment();
                status = "Mortal 正常";
                result.complete(index);
                return;
            }
            FALLBACK.increment();
            if (error instanceof TimeoutException) TIMEOUT.increment();
            status = "Mortal 暂不可用，使用电将 AI";
            warn(error);
            var fallback = AiRuntime.choose(snapshot);
            active.set(fallback);
            if (result.isCancelled()) fallback.cancel(false);
            else fallback.whenComplete((value, failure) -> {
                if (failure == null) result.complete(value);
                else result.completeExceptionally(failure);
            });
        });
        if (System.nanoTime() < retryAfterNanos) {
            model.completeExceptionally(new IOException("waiting to retry worker startup"));
            return result;
        }

        long deadline = Math.clamp(Long.getLong("riichi.mortal.timeoutMillis", 5000L), 100L, 30_000L);
        try {
            var timeout = TIMER.schedule(() -> model.completeExceptionally(new TimeoutException("decision deadline exceeded")),
                    deadline, TimeUnit.MILLISECONDS);
            model.whenComplete((value, error) -> {
                timeout.cancel(false);
                if (model.isCancelled() || error instanceof TimeoutException) WORKER.cancel(id);
            });
            EXECUTOR.execute(() -> {
                if (model.isDone()) return;
                try {
                    int index = WORKER.request(request, model::isDone);
                    var legal = request.getAsJsonArray("legal");
                    if (index < 0 || index >= legal.size() || legal.get(index).getAsJsonObject().get("index").getAsInt() != index)
                        throw new IOException("Mortal returned an index outside the current legal actions");
                    model.complete(index);
                } catch (AiWorker.DecisionError unsupportedAction) {
                    // An unmappable move is local to this position. Keep the loaded model for the next turn.
                    model.completeExceptionally(unsupportedAction);
                } catch (Exception unavailable) {
                    WORKER.kill();
                    if (!model.isDone()) {
                        retryAfterNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                        model.completeExceptionally(unavailable);
                    }
                }
            });
        } catch (RejectedExecutionException unavailable) {
            model.completeExceptionally(unavailable);
        }
        return result;
    }

    private static JsonObject request(long id, JsonObject snapshot, JsonArray events, MatchLength length) {
        var request = new JsonObject();
        request.addProperty("id", id);
        request.add("seat", snapshot.get("seat"));
        var rules = new JsonObject();
        rules.add("redFives", snapshot.getAsJsonObject("rules").get("redFives"));
        request.add("rules", rules);
        request.addProperty("length", length.name());
        request.add("legal", snapshot.getAsJsonArray("legal"));
        request.add("events", events);
        return request;
    }

    /** Called by ReplayAnalysis's single background executor, outside the live BOT queue and process. */
    public static JsonObject analyzeReplay(JsonObject snapshot, JsonArray events, MatchLength length) throws Exception {
        if (events.isEmpty()) throw new IOException("Mortal 牌谱缺少本手的完整公开行动");
        if (replayClosed) throw new IOException("Mortal 牌谱分析已关闭");

        long id = REPLAY_IDS.incrementAndGet();
        var request = request(id, snapshot, events, length);
        request.addProperty("mode", "analysis");
        var expired = new AtomicBoolean();
        long deadline = Math.clamp(Long.getLong("riichi.replay.analysisTimeoutMillis", 10_000L), 100L, 30_000L);
        var timeout = TIMER.schedule(() -> {
            expired.set(true);
            REPLAY_WORKER.cancel(id);
        }, deadline, TimeUnit.MILLISECONDS);
        try {
            var reply = REPLAY_WORKER.exchange(request, () -> replayClosed || expired.get());
            if (replayClosed || expired.get()) throw new IOException("Mortal 牌谱分析已取消或超时");
            if (!reply.has("weights") || !reply.get("weights").isJsonObject()
                    || !reply.has("actions") || !reply.get("actions").isJsonArray())
                throw new IOException("Mortal 牌谱分析返回了无效的评分");
            return reply;
        } catch (AiWorker.DecisionError unsupported) {
            throw unsupported;
        } catch (Exception unavailable) {
            REPLAY_WORKER.kill();
            throw unavailable;
        } finally {
            timeout.cancel(false);
        }
    }

    public static void closeReplay() {
        replayClosed = true;
        REPLAY_WORKER.kill();
    }

    private static void warn(Throwable error) {
        long now = System.nanoTime(), previous = LAST_WARNING.get();
        if ((previous == 0 || now - previous >= TimeUnit.SECONDS.toNanos(30)) && LAST_WARNING.compareAndSet(previous, now))
            LOG.log(System.Logger.Level.WARNING, "Mortal unavailable, using the seat's Kobalab AI. "
                    + error + ". Worker path: riichi.mortal.worker / RIICHI_MORTAL_WORKER");
    }

    static void shutdown() {
        closeReplay();
        PENDING.forEach(result -> result.cancel(false));
        EXECUTOR.shutdownNow();
        TIMER.shutdownNow();
        WORKER.kill();
        status = "Mortal 已关闭";
    }
}
