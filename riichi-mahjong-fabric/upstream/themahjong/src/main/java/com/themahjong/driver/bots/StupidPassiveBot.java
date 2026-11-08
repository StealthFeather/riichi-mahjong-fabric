package com.themahjong.driver.bots;

import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.PlayerAction;

public final class StupidPassiveBot extends AbstractStupidBot {
    public StupidPassiveBot() { super(); }

    public StupidPassiveBot(
            double drawSeconds, double discardSeconds, double claimSeconds,
            double winSeconds, double passSeconds, double riichiSeconds,
            double abortSeconds) {
        super(drawSeconds, discardSeconds, claimSeconds, winSeconds,
                passSeconds, riichiSeconds, abortSeconds);
    }

    public static StupidPassiveBot humanLike() {
        return new StupidPassiveBot(0.3, 1.0, 0.5, 1.0, 0.5, 1.5, 1.0);
    }

    @Override
    protected PlayerAction pick(DecisionRequest request) {
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.DeclareTsumo
                    || a instanceof PlayerAction.DeclareRon
                    || a instanceof PlayerAction.DeclareChankan) {
                return a;
            }
        }
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Draw) return a;
        }
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Pass) return a;
        }
        PlayerAction discard = null;
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Discard) discard = a;
        }
        if (discard != null) return discard;
        return request.legalActions().isEmpty() ? null : request.legalActions().get(0);
    }
}
