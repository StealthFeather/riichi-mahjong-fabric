package com.riichimahjong.fabric.wineffect;

import java.util.List;
import java.util.Objects;

/** Public winning identities only; no driver, concealed opponents, seed or wall. */
public record WinEffectContext(String effectId, Source source, List<Winner> winners) {
    public record Source(String slot, int tileCode) {
        public Source { Objects.requireNonNull(slot); }
    }
    public record Winner(int seat, int tileCode) {}
    public WinEffectContext {
        Objects.requireNonNull(effectId);
        Objects.requireNonNull(source);
        winners = List.copyOf(winners);
        if (winners.isEmpty()) throw new IllegalArgumentException("A win needs a winner");
    }
    public boolean includes(int seat) { return winners.stream().anyMatch(w -> w.seat() == seat); }
}
