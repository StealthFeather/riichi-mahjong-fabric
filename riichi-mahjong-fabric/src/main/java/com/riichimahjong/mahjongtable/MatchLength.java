package com.riichimahjong.mahjongtable;

import com.themahjong.TheMahjongMatch;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Scheduled winds are independent of player count and the scoring rules preset. */
public enum MatchLength {
    EAST(1, "东风场"),
    SOUTH(2, "南风场");

    private static final String NBT_KEY = "MatchLength";
    private final int roundCount;
    private final String label;

    MatchLength(int roundCount, String label) {
        this.roundCount = roundCount;
        this.label = label;
    }

    public int roundCount() { return roundCount; }

    public String label() { return label; }

    public static MatchLength fromMatch(TheMahjongMatch match) {
        return match.roundCount() == 1 ? EAST : SOUTH;
    }

    public void write(ValueOutput out) {
        out.putString(NBT_KEY, name());
    }

    /** Live games and records retain their own scheduled winds, including after an extension. */
    public static MatchLength read(ValueInput in, @Nullable TheMahjongMatch activeMatch) {
        if (activeMatch != null) return fromMatch(activeMatch);
        try {
            return valueOf(in.getStringOr(NBT_KEY, SOUTH.name()));
        } catch (IllegalArgumentException ignored) {
            return SOUTH;
        }
    }
}
