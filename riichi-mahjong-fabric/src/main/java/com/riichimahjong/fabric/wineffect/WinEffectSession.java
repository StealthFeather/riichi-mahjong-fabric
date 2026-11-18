package com.riichimahjong.fabric.wineffect;

/** One playback per accepted win event; multi-ron shares its single source. */
public final class WinEffectSession {
    private final WinEffectContext context;
    private final WinEffect effect;
    private int elapsedTicks;
    public WinEffectSession(WinEffectContext context) {
        this.context=context;
        effect=WinEffects.resolve(context.effectId());
    }
    public WinEffectContext context() { return context; }
    public WinEffect effect() { return effect; }
    public int elapsedTicks() { return elapsedTicks; }
    public WinEffectFrame frame() { return effect.frame(context,elapsedTicks); }
    public boolean advance() {
        elapsedTicks=Math.min(elapsedTicks+1,effect.durationTicks());
        return elapsedTicks>=effect.durationTicks();
    }
}
