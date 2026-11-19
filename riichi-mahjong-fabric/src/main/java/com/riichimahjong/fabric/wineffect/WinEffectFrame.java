package com.riichimahjong.fabric.wineffect;

import java.util.List;
import java.util.Objects;

/** Tile offsets use blocks. Travel interpolates the winning tile from its source to the winner's slot. */
public record WinEffectFrame(TilePose source, TilePose winningTile, double handReveal,
                             double travel, List<ActorPose> actors) {
    public enum Actor { ENDERMAN, EVOKER, FANGS, WARDEN, SONIC_WAVE }
    public enum Action { IDLE, CARRY, CAST, EMERGE, SONIC }

    /** Y offsets the feet from the felt, and age counts ticks since this action began. */
    public record ActorPose(Actor kind, WinEffectCue.Anchor anchor, Action action, double y, int age) {
        public ActorPose {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(anchor);
            Objects.requireNonNull(action);
            if (!Double.isFinite(y) || age < 0) throw new IllegalArgumentException("Invalid win actor pose");
        }
    }

    /** Flip and hand reveal run from 0 (standing) to 1 (face-up). */
    public record TilePose(boolean visible, double x, double y, double z, float scale, double flip) {
        public static final TilePose HIDDEN = new TilePose(false, 0, 0, 0, 1, 0);
        public static final TilePose STANDING = new TilePose(true, 0, 0, 0, 1, 0);
        public TilePose {
            if (!Double.isFinite(x+y+z+flip) || !Float.isFinite(scale) || scale < 0 || flip < 0 || flip > 1)
                throw new IllegalArgumentException("Invalid win tile pose");
        }
    }
    public static final WinEffectFrame FINAL = new WinEffectFrame(TilePose.HIDDEN,
            new TilePose(true, 0, 0, 0, 1, 1), 1);
    public WinEffectFrame(TilePose source, TilePose winningTile, double handReveal) {
        this(source, winningTile, handReveal, 1, List.of());
    }
    public WinEffectFrame {
        Objects.requireNonNull(source);
        Objects.requireNonNull(winningTile);
        actors = List.copyOf(actors);
        if (!Double.isFinite(handReveal) || handReveal < 0 || handReveal > 1)
            throw new IllegalArgumentException("Invalid hand reveal");
        if (!Double.isFinite(travel) || travel < 0 || travel > 1)
            throw new IllegalArgumentException("Invalid winning tile travel");
    }
}
