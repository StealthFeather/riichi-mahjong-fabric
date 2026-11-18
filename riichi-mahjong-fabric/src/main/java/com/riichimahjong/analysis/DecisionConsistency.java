package com.riichimahjong.analysis;

import com.riichimahjong.ai.AiRuntime;
import com.riichimahjong.ai.AiSnapshot;
import com.riichimahjong.ai.BotDifficulty;
import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Asynchronous comparison against each mode's supported benchmark, using only information visible at the decision. */
public final class DecisionConsistency implements AutoCloseable {
    private static final int MAX_PENDING = 128;
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private boolean closed;

    private record Pending(CompletableFuture<Integer> future, List<PlayerAction> legal,
                           PlayerAction chosen, Consumer<Boolean> outcome) {}
    private record Completed(Consumer<Boolean> outcome, boolean matched) {}

    /**
     * Called once for an accepted human discard, before advancing the rule engine.
     * The callback runs only from poll(), never from the background AI worker.
     * Forced decisions and unavailable evaluators produce no sample.
     */
    public boolean record(DecisionRequest request, PlayerAction chosen, boolean redFives,
                          Consumer<Boolean> outcome) {
        Objects.requireNonNull(outcome);
        if (closed || pending.size() >= MAX_PENDING || !eligible(request, chosen)) return false;
        boolean standardFour = request.round().players().size() == 4 && !request.rules().sichuan()
                && !request.rules().exchangeThree() && !request.rules().jokersEnabled() && !request.rules().bloodBattle();
        var future = standardFour ? AiRuntime.analyze(AiSnapshot.create(request, BotDifficulty.HARD, redFives))
                : LocalConsistencyPolicy.analyze(LocalConsistencyPolicy.snapshot(request));
        pending.addLast(new Pending(future, request.legalActions(), chosen, outcome));
        return true;
    }

    public static boolean eligible(DecisionRequest request, PlayerAction chosen) {
        if (request == null || chosen == null
                || !(request.phase() instanceof MatchPhase.AwaitingDiscard turn)
                || turn.seat() != request.seat()
                || !(chosen instanceof PlayerAction.Discard || chosen instanceof PlayerAction.DiscardWithRiichi)
                || !request.legalActions().contains(chosen)) return false;
        // Multiple physical copies of the same tile do not make a forced discard a choice.
        return request.legalActions().stream().anyMatch(action -> !equivalent(chosen, action));
    }

    /** Physical tile identity is irrelevant, while red fives and the riichi declaration are significant. */
    public static boolean equivalent(PlayerAction first, PlayerAction second) {
        if (first == null || second == null || first.getClass() != second.getClass()) return false;
        if (first instanceof PlayerAction.Discard a && second instanceof PlayerAction.Discard b)
            return a.tile().equals(b.tile());
        if (first instanceof PlayerAction.DiscardWithRiichi a && second instanceof PlayerAction.DiscardWithRiichi b)
            return a.tile().equals(b.tile());
        return first.equals(second);
    }

    /** Apply finished samples on the table/server thread; late results remain attached to their original callback. */
    public void poll() {
        var completed = new ArrayList<Completed>();
        int remaining = pending.size();
        while (remaining-- > 0) {
            var sample = pending.removeFirst();
            var future = sample.future();
            if (!future.isDone()) { pending.addLast(sample); continue; }
            if (future.isCancelled() || future.isCompletedExceptionally()) continue;
            int index = future.getNow(-1);
            if (index < 0 || index >= sample.legal().size()) continue;
            completed.add(new Completed(sample.outcome(), equivalent(sample.chosen(), sample.legal().get(index))));
        }
        for (var sample : completed) sample.outcome().accept(sample.matched());
    }

    public int pendingCount() { return pending.size(); }

    @Override public void close() {
        closed = true;
        for (var sample : pending) sample.future().cancel(false);
        pending.clear();
    }
}
