package com.themahjong.yaku;

import com.themahjong.TheMahjongTile;

/** Reuse the scoring engine's indicator cycles for display-only highlights. */
public final class DoraIndicators {
    private DoraIndicators() {}

    public static TheMahjongTile dora(TheMahjongTile indicator, int playerCount) {
        return WinCalculator.doraFromIndicator(indicator, playerCount);
    }
}
