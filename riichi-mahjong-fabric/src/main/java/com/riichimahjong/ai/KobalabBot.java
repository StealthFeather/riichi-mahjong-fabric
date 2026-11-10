package com.riichimahjong.ai;

import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.PlayerAction;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Pollable asynchronous controller. A completed decision is tied to its original rule-engine window. */
public final class KobalabBot implements MahjongPlayerInterface, AutoCloseable {
    private final BotDifficulty difficulty;
    private final Boolean redFives;
    private boolean closed;
    private DecisionRequest current;
    private CompletableFuture<Integer> pending;
    private double elapsed;
    private PlayerAction automatic;

    public KobalabBot(BotDifficulty difficulty) { this(difficulty, null); }
    public KobalabBot(BotDifficulty difficulty, boolean redFives) { this(difficulty, Boolean.valueOf(redFives)); }
    private KobalabBot(BotDifficulty difficulty, Boolean redFives) { this.difficulty = Objects.requireNonNull(difficulty); this.redFives = redFives; }
    public BotDifficulty difficulty() { return difficulty; }
    public boolean isClosed() { return closed; }

    @Override public Optional<PlayerAction> chooseAction(DecisionRequest request, double deltaSeconds) {
        if (closed || request.legalActions().isEmpty()) return Optional.empty();
        if (!Objects.equals(current, request)) {
            if (pending != null) pending.cancel(false);
            current = request; elapsed = 0; automatic = forced(request); pending = null;
            if (automatic == null) {
                var snapshot = redFives == null ? AiSnapshot.create(request, difficulty) : AiSnapshot.create(request, difficulty, redFives);
                pending = AiRuntime.choose(snapshot);
            }
        }
        elapsed += Double.isFinite(deltaSeconds) ? Math.max(0, deltaSeconds) : 0;
        PlayerAction chosen = automatic;
        if (chosen == null && pending != null && pending.isDone() && !pending.isCancelled() && !pending.isCompletedExceptionally()) {
            int index = pending.getNow(-1);
            if (index >= 0 && index < request.legalActions().size()) chosen = request.legalActions().get(index);
        }
        if (chosen == null) return Optional.empty();
        double seconds = chosen instanceof PlayerAction.DeclareRon || chosen instanceof PlayerAction.DeclareTsumo
                || chosen instanceof PlayerAction.DeclareChankan || chosen instanceof PlayerAction.DiscardWithRiichi ? 1.0 : chosen instanceof PlayerAction.Pass ? 0.5 : 0.3;
        return elapsed >= seconds ? Optional.of(chosen) : Optional.empty();
    }

    private static PlayerAction forced(DecisionRequest request) {
        for (var action : request.legalActions()) if (action instanceof PlayerAction.DeclareRon || action instanceof PlayerAction.DeclareTsumo || action instanceof PlayerAction.DeclareChankan) return action;
        if (request.legalActions().size() == 1) return request.legalActions().get(0);
        for (var action : request.legalActions()) if (action instanceof PlayerAction.Draw) return action;
        return null;
    }

    @Override public void close() {
        closed = true;
        if (pending != null) pending.cancel(false);
        pending = null; current = null; automatic = null;
    }
}
