package com.themahjong.driver.bots;

import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.PlayerAction;

public final class StupidActiveBot extends AbstractStupidBot {
    public StupidActiveBot() { super(); }

    public StupidActiveBot(
            double drawSeconds, double discardSeconds, double claimSeconds,
            double winSeconds, double passSeconds, double riichiSeconds,
            double abortSeconds) {
        super(drawSeconds, discardSeconds, claimSeconds, winSeconds,
                passSeconds, riichiSeconds, abortSeconds);
    }

    public static StupidActiveBot humanLike() {
        return new StupidActiveBot(0.3, 0.3, 0.3, 1.0, 0.5, 1.0, 1.0);
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

        PlayerAction call = pickFirst(request,
                PlayerAction.Daiminkan.class,
                PlayerAction.Pon.class,
                PlayerAction.Chi.class,
                PlayerAction.DeclareKita.class,
                PlayerAction.Ankan.class,
                PlayerAction.Kakan.class,
                PlayerAction.DiscardWithRiichi.class);
        if (call != null) return call;

        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Draw) return a;
        }

        PlayerAction discard = null;
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Discard) discard = a;
        }
        if (discard != null) return discard;

        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Pass) return a;
        }
        return request.legalActions().isEmpty() ? null : request.legalActions().get(0);
    }

    @SafeVarargs
    private static PlayerAction pickFirst(DecisionRequest req, Class<? extends PlayerAction>... types) {
        for (Class<? extends PlayerAction> t : types) {
            for (PlayerAction a : req.legalActions()) {
                if (t.isInstance(a)) return a;
            }
        }
        return null;
    }
}
