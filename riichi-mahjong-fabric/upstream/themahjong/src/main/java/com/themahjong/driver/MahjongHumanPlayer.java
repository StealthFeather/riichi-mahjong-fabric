package com.themahjong.driver;

import java.util.Optional;
import java.util.OptionalDouble;

public class MahjongHumanPlayer implements MahjongPlayerInterface {
    private PlayerAction queued;
    private double elapsedThisPhase;
    private MatchPhase lastPhase;
    private OptionalDouble autoDrawAfterSeconds = OptionalDouble.empty();
    private OptionalDouble autoDiscardAfterSeconds = OptionalDouble.empty();

    public void queue(PlayerAction action) {
        this.queued = action;
    }

    public void clearQueued() {
        this.queued = null;
    }

    public boolean hasQueued() { return queued != null; }

    public void setAutoDrawAfterSeconds(double seconds) {
        if (seconds < 0) throw new IllegalArgumentException("seconds must be non-negative");
        this.autoDrawAfterSeconds = OptionalDouble.of(seconds);
    }

    public void disableAutoDraw() { this.autoDrawAfterSeconds = OptionalDouble.empty(); }

    public void setAutoDiscardAfterSeconds(double seconds) {
        if (seconds < 0) throw new IllegalArgumentException("seconds must be non-negative");
        this.autoDiscardAfterSeconds = OptionalDouble.of(seconds);
    }

    public void disableAutoDiscard() { this.autoDiscardAfterSeconds = OptionalDouble.empty(); }

    @Override
    public Optional<PlayerAction> chooseAction(DecisionRequest request, double deltaSeconds) {
        if (!request.phase().equals(lastPhase)) {
            elapsedThisPhase = 0.0;
            lastPhase = request.phase();
        }
        elapsedThisPhase += deltaSeconds;

        if (queued != null) {
            if (request.legalActions().contains(queued)) {
                PlayerAction picked = queued;
                queued = null;
                return Optional.of(picked);
            }

            queued = null;
        }

        if (request.phase() instanceof MatchPhase.AwaitingDraw
                && autoDrawAfterSeconds.isPresent()
                && elapsedThisPhase >= autoDrawAfterSeconds.getAsDouble()) {
            return Optional.of(PlayerAction.Draw.INSTANCE);
        }

        if (autoDiscardAfterSeconds.isPresent()
                && elapsedThisPhase >= autoDiscardAfterSeconds.getAsDouble()) {
            if (request.phase() instanceof MatchPhase.AwaitingClaims) {
                return Optional.of(PlayerAction.Pass.INSTANCE);
            }
            if (request.phase() instanceof MatchPhase.AwaitingDiscard) {
                PlayerAction fallback = null;
                for (PlayerAction a : request.legalActions()) {
                    if (a instanceof PlayerAction.Discard) fallback = a;
                }
                if (fallback != null) return Optional.of(fallback);
            }
        }

        return Optional.empty();
    }
}
