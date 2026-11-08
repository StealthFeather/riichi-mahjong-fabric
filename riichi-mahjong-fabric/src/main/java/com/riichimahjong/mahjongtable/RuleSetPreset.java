// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongtable;

import com.themahjong.TheMahjongMatch;

import java.util.function.Supplier;

public enum RuleSetPreset {
    MAHJONG_SOUL_4P("mahjong_soul_4p", 4, TheMahjongMatch::defaultMahjongSoul),
    TENHOU_4P("tenhou_4p", 4, TheMahjongMatch::defaultTenhou),
    WRC_4P("wrc_4p", 4, TheMahjongMatch::defaults),
    MAHJONG_SOUL_SANMA_3P("mahjong_soul_sanma_3p", 3, TheMahjongMatch::defaultMahjongSoulSanma),
    TENHOU_SANMA_3P("tenhou_sanma_3p", 3, TheMahjongMatch::defaultTenhouSanma);

    private final String key;
    private final int playerCount;
    private final Supplier<TheMahjongMatch> factory;

    RuleSetPreset(String key, int playerCount, Supplier<TheMahjongMatch> factory) {
        this.key = key;
        this.playerCount = playerCount;
        this.factory = factory;
    }

    public int playerCount() { return playerCount; }

    public TheMahjongMatch newMatch() { return factory.get(); }

    public String langKey() { return "riichi_mahjong.preset." + key; }
}
