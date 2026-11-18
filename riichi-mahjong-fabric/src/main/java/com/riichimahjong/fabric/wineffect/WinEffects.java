package com.riichimahjong.fabric.wineffect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Register once during server initialization; selection is stored on each table. */
public final class WinEffects {
    public static final String DEFAULT = "riichi_mahjong:evoker_fangs";
    private static final Map<String,WinEffect> EFFECTS = new LinkedHashMap<>();
    static {
        register(new EvokerFangsWinEffect());
        register(new WinEffect() {
            public String id() { return "riichi_mahjong:none"; }
            public String displayName() { return "直接摊牌"; }
            public int durationTicks() { return 1; }
            public boolean consumesSource() { return false; }
            public WinEffectFrame frame(WinEffectContext context,int tick) {
                return new WinEffectFrame(WinEffectFrame.TilePose.STANDING,WinEffectFrame.FINAL.winningTile(),1);
            }
        });
    }
    private WinEffects() {}
    public static synchronized void register(WinEffect effect) {
        if (effect.id()==null || !effect.id().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || effect.durationTicks()<1 || effect.durationTicks()>200)
            throw new IllegalArgumentException("Effect needs a namespaced ID and 1–200 tick duration");
        if (effect.cues().stream().anyMatch(c -> c.tick() >= effect.durationTicks()))
            throw new IllegalArgumentException("Cue outside effect duration");
        if (EFFECTS.putIfAbsent(effect.id(),effect)!=null)
            throw new IllegalArgumentException("Duplicate win effect: "+effect.id());
    }
    public static WinEffect resolve(String id) { return EFFECTS.getOrDefault(id,EFFECTS.get(DEFAULT)); }
    public static List<WinEffect> all() { return List.copyOf(EFFECTS.values()); }
}
