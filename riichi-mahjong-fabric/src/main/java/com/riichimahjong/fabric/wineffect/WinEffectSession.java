package com.riichimahjong.fabric.wineffect;

/** One playback per accepted win event; multi-ron shares its single source. */
public final class WinEffectSession {
    private final WinEffectContext context;
    private final WinEffect effect;
    private final java.util.Map<Integer,WinEffect> bySeat = new java.util.LinkedHashMap<>();
    private final int duration;
    private int elapsedTicks;
    public WinEffectSession(WinEffectContext context) {
        this.context=context;
        for (var winner : context.winners())
            bySeat.put(winner.seat(), WinEffects.resolve(winner.effectOr(context.effectId())));
        // One river tile is shared by simultaneous ron. Prefer an effect that animates it.
        effect = bySeat.values().stream().filter(WinEffect::consumesSource).findFirst().orElse(bySeat.values().iterator().next());
        duration = bySeat.values().stream().mapToInt(WinEffect::durationTicks).max().orElseThrow();
    }
    public WinEffectContext context() { return context; }
    public WinEffect effect() { return effect; }
    public WinEffect effect(int seat) { return bySeat.get(seat); }
    public WinEffectFrame frame(int seat) {
        var selected = effect(seat);
        return selected.frame(context, Math.min(elapsedTicks, selected.durationTicks()));
    }
    public int elapsedTicks() { return elapsedTicks; }
    public WinEffectFrame frame() { return effect.frame(context,Math.min(elapsedTicks,effect.durationTicks())); }
    public boolean advance() {
        elapsedTicks=Math.min(elapsedTicks+1,duration);
        return elapsedTicks>=duration;
    }
}
