package com.riichimahjong.fabric.wineffect;

import com.riichimahjong.fabric.wineffect.WinEffectCue.Anchor;
import com.riichimahjong.fabric.wineffect.WinEffectFrame.ActorPose;
import com.riichimahjong.fabric.wineffect.WinEffectFrame.TilePose;
import java.util.List;

import static com.riichimahjong.fabric.wineffect.WinEffectCue.Anchor.*;
import static com.riichimahjong.fabric.wineffect.WinEffectFrame.Action.*;
import static com.riichimahjong.fabric.wineffect.WinEffectFrame.Actor.*;

/** The discard stays in place while the winning hands tip over. Each scene then delivers it face-up. */
enum RonWinEffect implements WinEffect {
    EVOKER_FANGS("evoker_fangs", "唤魔尖牙", 66, List.of(
            sound(14, "entity.evoker.prepare_attack", WINNER, .4f, 1.25f),
            sound(32, "entity.evoker_fangs.attack", SOURCE, .4f, 1.35f),
            sound(48, "block.enchantment_table.use", WINNER, .4f, 1.2f))),
    ENDERMAN_CARRY("enderman", "末影人搬牌", 50, List.of(
            sound(14, "entity.enderman.teleport", SOURCE, .35f, 1.35f),
            sound(32, "entity.enderman.teleport", WINNER, .35f, 1.35f),
            sound(42, "block.bamboo_wood.place", WINNER, .4f, 1.2f))),
    WARDEN_SHRIEK("warden", "监守者尖啸", Sonic.LAND + 5, List.of(
            sound(14, "entity.warden.emerge", SOURCE, .35f, 1.5f),
            sound(Sonic.CHARGE, "entity.warden.sonic_charge", SOURCE, .25f, 1),
            sound(Sonic.RELEASE, "entity.warden.sonic_boom", SOURCE, .35f, 1),
            sound(Sonic.LAND, "block.bamboo_wood.place", WINNER, .4f, 1.1f)));

    private static final class Sonic {
        static final int CHARGE = 30;
        // Vanilla SonicBoom waits 34 ticks after the entity event before firing and applying knockback.
        static final int RELEASE = CHARGE + 34;
        static final int FLIGHT_TICKS = 16;
        static final int LAND = RELEASE + FLIGHT_TICKS - 1;
    }

    private final String id;
    private final String name;
    private final int duration;
    private final List<WinEffectCue> cues;

    RonWinEffect(String path, String name, int duration, List<WinEffectCue> cues) {
        this.id = "riichi_mahjong:" + path;
        this.name = name;
        this.duration = duration;
        this.cues = cues;
    }

    public String id() { return id; }
    public String displayName() { return name; }
    public int durationTicks() { return duration; }
    public List<WinEffectCue> cues() { return cues; }

    public WinEffectFrame frame(WinEffectContext context, int tick) {
        if (tick < 14) {
            return new WinEffectFrame(TilePose.STANDING, TilePose.HIDDEN, ease(tick, 0, 12));
        }
        return switch (this) {
            case ENDERMAN_CARRY -> enderman(tick);
            case EVOKER_FANGS -> evoker(tick);
            case WARDEN_SHRIEK -> warden(tick);
        };
    }

    private static WinEffectFrame enderman(int tick) {
        if (tick < 32) {
            boolean carrying = tick >= 20;
            var actor = new ActorPose(ENDERMAN, SOURCE, carrying ? CARRY : IDLE, 0, tick - (carrying ? 20 : 14));
            var source = new TilePose(true, 0, .06 * ease(tick, 20, 28), 0, 1, 0);
            return new WinEffectFrame(source, TilePose.HIDDEN, 1, 1, List.of(actor));
        }
        if (tick >= 48) return WinEffectFrame.FINAL;
        boolean carrying = tick <= 42;
        var actor = new ActorPose(ENDERMAN, WINNER, carrying ? CARRY : IDLE, 0, tick - (carrying ? 32 : 43));
        return new WinEffectFrame(TilePose.HIDDEN, faceUp(.06 * (1 - ease(tick, 36, 42))), 1, 1, List.of(actor));
    }

    private static WinEffectFrame evoker(int tick) {
        if (tick >= 62) return WinEffectFrame.FINAL;
        if (tick >= 48) {
            return new WinEffectFrame(TilePose.HIDDEN, faceUp(-.20 * (1 - ease(tick, 48, 62))), 1);
        }
        if (tick >= 46) return new WinEffectFrame(TilePose.HIDDEN, TilePose.HIDDEN, 1);
        var caster = new ActorPose(EVOKER, WINNER, CAST, 0, tick - 14);
        if (tick < 24) return new WinEffectFrame(TilePose.STANDING, TilePose.HIDDEN, 1, 1, List.of(caster));

        var jaws = new ActorPose(FANGS, SOURCE, IDLE, 0, tick - 24);
        double bite = ease(tick, 32, 42);
        var source = new TilePose(tick < 42, 0, -.14 * bite, 0, (float)(1 - .75 * bite), 0);
        return new WinEffectFrame(source, TilePose.HIDDEN, 1, 1, List.of(caster, jaws));
    }

    private static WinEffectFrame warden(int tick) {
        if (tick >= Sonic.LAND) return WinEffectFrame.FINAL;
        ActorPose warden;
        int retreat = Sonic.RELEASE + 6;
        if (tick < Sonic.CHARGE) {
            warden = new ActorPose(WARDEN, SOURCE, EMERGE, -.22 * (1 - ease(tick, 14, 29)), tick - 14);
        } else if (tick < retreat) {
            warden = new ActorPose(WARDEN, SOURCE, SONIC, 0, tick - Sonic.CHARGE);
        } else {
            warden = new ActorPose(WARDEN, SOURCE, IDLE, -.22 * ease(tick, retreat, Sonic.LAND - 1), tick - retreat);
        }
        if (tick < Sonic.RELEASE) return new WinEffectFrame(TilePose.STANDING, TilePose.HIDDEN, 1, 1, List.of(warden));

        int sinceImpact = tick - Sonic.RELEASE;
        var actors = sinceImpact < 4
                ? List.of(warden, new ActorPose(SONIC_WAVE, SOURCE, SONIC, 0, sinceImpact))
                : List.of(warden);
        double travel = (sinceImpact + 1.0) / Sonic.FLIGHT_TICKS;
        double height = .26 * 4 * travel * (1 - travel);
        return new WinEffectFrame(TilePose.HIDDEN, faceUp(height), 1, travel, actors);
    }

    private static TilePose faceUp(double height) {
        return new TilePose(true, 0, height, 0, 1, 1);
    }

    private static double ease(int tick, int start, int end) {
        double t = Math.clamp((double)(tick - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static WinEffectCue sound(int tick, String path, Anchor anchor, float volume, float pitch) {
        return new WinEffectCue(tick, "sound", "minecraft:" + path, anchor, volume, pitch);
    }
}
