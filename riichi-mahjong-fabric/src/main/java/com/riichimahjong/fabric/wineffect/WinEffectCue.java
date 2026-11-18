package com.riichimahjong.fabric.wineffect;

/** One-shot visuals/sounds owned and cleaned up by the table renderer. */
public record WinEffectCue(int tick, String type, String resource, Anchor anchor, float volume, float pitch) {
    public enum Anchor { SOURCE, WINNER }
    public WinEffectCue {
        if (tick < 0 || resource == null || anchor == null || !("entity".equals(type) || "sound".equals(type)))
            throw new IllegalArgumentException("Invalid win effect cue");
    }
}
