// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongtable;

import com.themahjong.TheMahjongMatch;
import com.themahjong.sichuan.SichuanRules;
import com.themahjong.competition.CompetitiveRules;
import java.util.function.Supplier;

public enum RuleSetPreset {
    MAHJONG_SOUL_4P("mahjong_soul_4p", 4, Family.MAHJONG_SOUL, TheMahjongMatch::defaultMahjongSoul),
    TENHOU_4P("tenhou_4p", 4, Family.TENHOU, TheMahjongMatch::defaultTenhou),
    WRC_4P("wrc_4p", 4, Family.WRC, TheMahjongMatch::defaults),
    MAHJONG_SOUL_SANMA_3P("mahjong_soul_sanma_3p", 3, Family.MAHJONG_SOUL, TheMahjongMatch::defaultMahjongSoulSanma),
    TENHOU_SANMA_3P("tenhou_sanma_3p", 3, Family.TENHOU, TheMahjongMatch::defaultTenhouSanma),
    SICHUAN_4P("sichuan_4p", 4, Family.SICHUAN, () -> SichuanRules.newMatch(2, false, false)),
    JPML_4P("jpml_4p", 4, Family.JPML, CompetitiveRules::jpmlA),
    M_LEAGUE_4P("m_league_4p", 4, Family.M_LEAGUE, CompetitiveRules::mLeague);

    public enum Family {
        MAHJONG_SOUL("雀魂", true), TENHOU("天凤", true), WRC("WRC", false), SICHUAN("川麻", false),
        JPML("公式战", false), M_LEAGUE("M-League", false);
        private final String label;
        private final boolean threePlayers;
        Family(String label, boolean threePlayers) { this.label=label; this.threePlayers=threePlayers; }
        public String label() { return label; }
        public boolean supportsThreePlayers() { return threePlayers; }
        public RuleSetPreset preset(int playerCount) {
            int count = playerCount == 3 && threePlayers ? 3 : 4;
            for (var preset : RuleSetPreset.values())
                if (preset.family == this && preset.playerCount == count) return preset;
            throw new IllegalStateException("Missing preset for " + this);
        }
    }

    private final String key;
    private final int playerCount;
    private final Family family;
    private final Supplier<TheMahjongMatch> factory;
    RuleSetPreset(String key, int playerCount, Family family, Supplier<TheMahjongMatch> factory) {
        this.key=key; this.playerCount=playerCount; this.family=family; this.factory=factory;
    }
    public int playerCount() { return playerCount; }
    public Family family() { return family; }
    public TheMahjongMatch newMatch() { return newMatch(MatchLength.SOUTH); }
    public TheMahjongMatch newMatch(MatchLength length) { return newMatch(length, false, false); }
    public TheMahjongMatch newMatch(MatchLength length, boolean exchangeThree, boolean jokersEnabled) {
        return newMatch(length, exchangeThree, jokersEnabled, false);
    }
    public TheMahjongMatch newMatch(MatchLength length, boolean exchangeThree, boolean jokersEnabled, boolean bloodBattle) {
        return family==Family.SICHUAN ? SichuanRules.newMatch(length.roundCount(), exchangeThree, jokersEnabled, bloodBattle)
                : com.themahjong.variants.VariantRules.withOptions(factory.get().withRoundCount(length.roundCount()), exchangeThree, jokersEnabled, bloodBattle);
    }
    public boolean supportsMortal(int activePlayers, MatchLength length, boolean exchangeThree, boolean jokers, boolean bloodBattle) {
        return (this == MAHJONG_SOUL_4P || this == TENHOU_4P || this == M_LEAGUE_4P)
                && activePlayers == 4 && (length == MatchLength.EAST || length == MatchLength.SOUTH)
                && !exchangeThree && !jokers && !bloodBattle;
    }
    public String langKey() { return "riichi_mahjong.preset." + key; }
}
