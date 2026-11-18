package com.riichimahjong.fabric.wineffect;

import java.util.List;
import static com.riichimahjong.fabric.wineffect.WinEffectFrame.TilePose;

/** Bite at the source, emerge upright in each winning slot, then push the hand down. */
public final class EvokerFangsWinEffect implements WinEffect {
    public String id() { return "riichi_mahjong:evoker_fangs"; }
    public String displayName() { return "唤魔尖牙"; }
    public int durationTicks() { return 64; }
    private static double ease(int tick, int start, int end) {
        double t = Math.clamp((double)(tick-start)/(end-start), 0, 1);
        return t*t*(3-2*t);
    }
    public WinEffectFrame frame(WinEffectContext context, int tick) {
        double bite = ease(tick, 8, 18), rise = ease(tick, 24, 44), flip = ease(tick, 48, 60);
        return new WinEffectFrame(new TilePose(tick < 18, 0, -.48*bite, 0, (float)(1-.75*bite), 0),
                new TilePose(tick >= 24, 0, -.6*(1-rise), 0, 1, flip), flip);
    }
    public List<WinEffectCue> cues() {
        return List.of(new WinEffectCue(0,"entity","minecraft:evoker_fangs",WinEffectCue.Anchor.SOURCE,.22f,1),
                new WinEffectCue(0,"sound","minecraft:entity.evoker.prepare_attack",WinEffectCue.Anchor.SOURCE,.45f,1.3f),
                new WinEffectCue(24,"sound","minecraft:block.enchantment_table.use",WinEffectCue.Anchor.WINNER,.45f,1.2f),
                new WinEffectCue(48,"sound","minecraft:block.bamboo_wood.place",WinEffectCue.Anchor.WINNER,.6f,.8f));
    }
}
