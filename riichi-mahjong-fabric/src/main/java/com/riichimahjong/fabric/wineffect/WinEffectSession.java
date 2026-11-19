package com.riichimahjong.fabric.wineffect;

/** One playback per accepted win event; multi-ron shares its single source. */
public final class WinEffectSession {
    private final WinEffectContext context;
    private final WinEffect effect;
    private final java.util.Map<Integer,WinEffect> bySeat = new java.util.LinkedHashMap<>();
    private final int duration;
    private final java.util.Map<WinEffect,WinEffectFrame> frames = new java.util.HashMap<>();
    private int elapsedTicks;
    public WinEffectSession(WinEffectContext context) {
        this.context=context;
        for (var winner : context.winners())
            bySeat.put(winner.seat(), WinEffects.forPlayback(context,winner.effectOr(context.effectId())));
        // One river tile is shared by simultaneous ron. Prefer an effect that animates it.
        effect = bySeat.values().stream().filter(WinEffect::consumesSource).findFirst().orElse(bySeat.values().iterator().next());
        duration = bySeat.values().stream().mapToInt(WinEffect::durationTicks).max().orElseThrow();
    }
    public WinEffectContext context() { return context; }
    public WinEffect effect() { return effect; }
    public WinEffect effect(int seat) { return bySeat.get(seat); }
    public WinEffectFrame frame(int seat) {
        var selected = effect(seat);
        return frame(selected);
    }
    public int elapsedTicks() { return elapsedTicks; }
    public WinEffectFrame frame() { return frame(effect); }
    private WinEffectFrame frame(WinEffect selected) {
        return frames.computeIfAbsent(selected, scene -> scene.frame(context,Math.min(elapsedTicks,scene.durationTicks())));
    }
    public boolean advance() {
        elapsedTicks=Math.min(elapsedTicks+1,duration);
        frames.clear();
        return elapsedTicks>=duration;
    }
}
