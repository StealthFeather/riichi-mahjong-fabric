package com.themahjong;

public record TheMahjongRuleSet(

        boolean kiriageMangan,

        boolean doubleWindPairFu4,

        boolean renhouAllowed,

        boolean paoOnSuukantsu,

        boolean abortiveDrawsAllowed,

        boolean nagashiManganAllowed,

        boolean doubleRonAllowed,

        boolean bustingEndsGame,

        boolean riichiRequires1000Points,

        boolean depositsToFirstAtEnd,

        boolean agariYameAllowed,

        boolean suddenDeathRound,

        boolean openKanDoraDelayedReveal,

        boolean kuikaeForbidden,

        boolean strictRiichiAnkan,

        boolean chiDisabled,

        boolean kitaCountsAsDora,

        boolean tsumoLoss,

        boolean kitaDeclineCausesFuriten,

        boolean sanchahouAbortive

) {
    public static TheMahjongRuleSet wrc() {
        return new TheMahjongRuleSet(
                true,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                true,
                true
        );
    }

    public static TheMahjongRuleSet tenhou() {
        return new TheMahjongRuleSet(
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                true
        );
    }

    public static TheMahjongRuleSet mahjongSoul() {
        return new TheMahjongRuleSet(
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                false
        );
    }

    public static TheMahjongRuleSet tenhouSanma() {
        return new TheMahjongRuleSet(
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true
        );
    }

    public static TheMahjongRuleSet mahjongSoulSanma() {
        return new TheMahjongRuleSet(
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                false
        );
    }
}
