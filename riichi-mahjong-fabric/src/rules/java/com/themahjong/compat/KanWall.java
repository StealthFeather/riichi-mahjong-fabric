package com.themahjong.compat;

import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongRuleSet;
import com.themahjong.competition.CompetitiveRules;

/** The live wall supplies one replacement to the dead wall for each kan draw. */
public final class KanWall {
    private KanWall() {}
    private static final TheMahjongRuleSet SOUL = TheMahjongRuleSet.mahjongSoul();
    private static final TheMahjongRuleSet TENHOU = TheMahjongRuleSet.tenhou();

    public static boolean applies(TheMahjongMatch match) {
        var rules = match.ruleSet();
        return match.playerCount() == 4 && (match.roundCount() == 1 || match.roundCount() == 2)
                && !rules.sichuan() && !rules.jokersEnabled() && !rules.exchangeThree() && !rules.bloodBattle()
                && (rules.equals(SOUL) || rules.equals(TENHOU) || rules.competitiveProfile() == CompetitiveRules.Profile.M_LEAGUE);
    }

    /** The reserved tail of the live wall is inaccessible for the rest of the hand. */
    public static TheMahjongRound reserve(TheMahjongRound r, int count) {
        var wall = r.liveWall();
        // A pre-fix save can already have promised a rinshan draw with no tiles left to reserve.
        if (count <= 0 || wall.isEmpty()) return r;
        return new TheMahjongRound(r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(),
                r.dealerSeat(), r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1), r.activeTile(),
                wall.subList(0, wall.size() - count), r.rinshanTiles(), r.doraIndicators(), r.uraDoraIndicators(),
                r.revealedDoraCount(), r.pendingKanDoraReveals(), r.players(), r.pendingDeltas());
    }
}
