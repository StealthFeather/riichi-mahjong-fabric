package com.riichimahjong.ai;

import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import java.util.Objects;
import java.util.Optional;

/** Hold a chosen discard while its avatar looks at the tile, alongside normal thinking time. */
public final class BotDiscardAnimation {
    public static final double LOOK_SECONDS = .25;
    private DecisionRequest current;
    private PlayerAction chosen;
    private double elapsed;
    private boolean submitted;

    public Optional<PlayerAction> select(DecisionRequest request, PlayerAction candidate,
                                         double deltaSeconds, boolean thinkingReady) {
        if (!Objects.equals(current, request)) {
            clear();
            current = Objects.requireNonNull(request);
        }
        if (submitted) return Optional.empty();
        if (chosen == null) {
            if (candidate == null || !request.legalActions().contains(candidate)) return Optional.empty();
            chosen = candidate;
            // The elapsed delta belongs to the frame before the preview became visible.
            if (tile(chosen) != null) return Optional.empty();
        } else if (Double.isFinite(deltaSeconds)) {
            elapsed += Math.max(0, deltaSeconds);
        }
        if (!thinkingReady || tile(chosen) != null && elapsed + 1e-9 < LOOK_SECONDS) return Optional.empty();
        var result = chosen;
        chosen = null;
        submitted = true;
        return Optional.of(result);
    }

    /** Only expose a positional target for the live decision, never an old round's choice. */
    public Optional<TheMahjongTile> tileFor(TheMahjongRound round, MatchPhase phase) {
        if (current == null || submitted || current.round() != round
                || !current.phase().equals(phase)
                || !(phase instanceof MatchPhase.AwaitingDiscard discard)
                || discard.seat() != current.seat()) return Optional.empty();
        return Optional.ofNullable(tile(chosen));
    }

    public void clear() {
        current = null;
        chosen = null;
        elapsed = 0;
        submitted = false;
    }

    private static TheMahjongTile tile(PlayerAction action) {
        if (action instanceof PlayerAction.Discard discard) return discard.tile();
        if (action instanceof PlayerAction.DiscardWithRiichi discard) return discard.tile();
        return null;
    }
}
