package com.riichimahjong.fabric.wineffect;

/** Offsets are world blocks; flip is 0 standing to 1 face-up. Hand reveal uses the same range. */
public record WinEffectFrame(TilePose source, TilePose winningTile, double handReveal) {
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
    public WinEffectFrame {
        java.util.Objects.requireNonNull(source);
        java.util.Objects.requireNonNull(winningTile);
        if (!Double.isFinite(handReveal) || handReveal < 0 || handReveal > 1)
            throw new IllegalArgumentException("Invalid hand reveal");
    }
}
