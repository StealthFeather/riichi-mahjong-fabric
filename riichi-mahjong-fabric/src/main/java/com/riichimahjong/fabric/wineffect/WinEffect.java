package com.riichimahjong.fabric.wineffect;

import java.util.List;

/** Stateless, server-tick presentation. Never mutate rule hands or award rewards here. */
public interface WinEffect {
    String id();
    String displayName();
    int durationTicks();
    WinEffectFrame frame(WinEffectContext context, int elapsedTicks);
    default List<WinEffectCue> cues() { return List.of(); }
    default boolean consumesSource() { return true; }
}
