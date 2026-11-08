package com.themahjong.driver.bots;

import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.PlayerAction;

import java.util.Objects;
import java.util.Optional;

public abstract class AbstractStupidBot implements MahjongPlayerInterface {
    private final double drawSeconds;
    private final double discardSeconds;
    private final double claimSeconds;
    private final double winSeconds;
    private final double passSeconds;
    private final double riichiSeconds;
    private final double abortSeconds;

    private DecisionRequest currentRequest;
    private double elapsedThisRequest;

    protected AbstractStupidBot() {
        this(0, 0, 0, 0, 0, 0, 0);
    }

    protected AbstractStupidBot(
            double drawSeconds,
            double discardSeconds,
            double claimSeconds,
            double winSeconds,
            double passSeconds,
            double riichiSeconds,
            double abortSeconds) {
        if (drawSeconds < 0 || discardSeconds < 0 || claimSeconds < 0
                || winSeconds < 0 || passSeconds < 0 || riichiSeconds < 0
                || abortSeconds < 0) {
            throw new IllegalArgumentException("think durations must be non-negative");
        }
        this.drawSeconds = drawSeconds;
        this.discardSeconds = discardSeconds;
        this.claimSeconds = claimSeconds;
        this.winSeconds = winSeconds;
        this.passSeconds = passSeconds;
        this.riichiSeconds = riichiSeconds;
        this.abortSeconds = abortSeconds;
    }

    @Override
    public final Optional<PlayerAction> chooseAction(DecisionRequest request, double deltaSeconds) {
        PlayerAction chosen = pick(request);
        if (chosen == null) return Optional.empty();
        if (!Objects.equals(currentRequest, request)) {
            currentRequest = request;
            elapsedThisRequest = 0;
        }
        elapsedThisRequest += deltaSeconds;
        return elapsedThisRequest >= secondsFor(chosen)
                ? Optional.of(chosen)
                : Optional.empty();
    }

    protected abstract PlayerAction pick(DecisionRequest request);

    private double secondsFor(PlayerAction action) {
        if (action instanceof PlayerAction.Draw) return drawSeconds;
        if (action instanceof PlayerAction.Discard) return discardSeconds;
        if (action instanceof PlayerAction.DiscardWithRiichi) return riichiSeconds;
        if (action instanceof PlayerAction.Chi
                || action instanceof PlayerAction.Pon
                || action instanceof PlayerAction.Daiminkan
                || action instanceof PlayerAction.Ankan
                || action instanceof PlayerAction.Kakan
                || action instanceof PlayerAction.DeclareKita) return claimSeconds;
        if (action instanceof PlayerAction.DeclareTsumo
                || action instanceof PlayerAction.DeclareRon
                || action instanceof PlayerAction.DeclareChankan) return winSeconds;
        if (action instanceof PlayerAction.Pass) return passSeconds;
        if (action instanceof PlayerAction.KyuushuAbort) return abortSeconds;
        return 0;
    }
}
