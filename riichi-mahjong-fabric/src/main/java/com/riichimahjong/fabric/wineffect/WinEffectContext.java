package com.riichimahjong.fabric.wineffect;

import java.util.List;
import java.util.Objects;

/** Public winning identities only; no driver, concealed opponents, seed or wall. */
public record WinEffectContext(String effectId, Source source, List<Winner> winners) {
    public record Source(String slot, int tileCode) {
        public Source { Objects.requireNonNull(slot); }
    }
    public record Winner(int seat, int tileCode, String effectId) {
        public Winner(int seat, int tileCode) { this(seat, tileCode, ""); }
        public String effectOr(String fallback) { return effectId.isEmpty() ? fallback : effectId; }
    }
    public WinEffectContext withChoices(java.util.function.IntFunction<String> effectAtSeat) {
        return new WinEffectContext(effectId, source, winners.stream()
                .map(w -> new Winner(w.seat(), w.tileCode(), effectAtSeat.apply(w.seat()))).toList());
    }
    public WinEffectContext {
        Objects.requireNonNull(effectId);
        Objects.requireNonNull(source);
        winners = List.copyOf(winners);
        if (winners.isEmpty()) throw new IllegalArgumentException("A win needs a winner");
    }
    public boolean includes(int seat) { return winners.stream().anyMatch(w -> w.seat() == seat); }
}
