package com.themahjong.replay;

import com.themahjong.TheMahjongTile;
import java.util.List;

public sealed interface TenhouAction permits
        TenhouAction.Draw,
        TenhouAction.Discard,
        TenhouAction.Claim,
        TenhouAction.RiichiStep1,
        TenhouAction.RiichiStep2,
        TenhouAction.Win,
        TenhouAction.DoraReveal,
        TenhouAction.ExhaustiveDraw {
    record Draw(int seat, TheMahjongTile tile) implements TenhouAction {}

    record Discard(int seat, TheMahjongTile tile) implements TenhouAction {}

    record Claim(int seat, ClaimType type, List<TheMahjongTile> tiles) implements TenhouAction {}

    record RiichiStep1(int seat) implements TenhouAction {}

    record RiichiStep2(int seat, int[] scores) implements TenhouAction {}

    record Win(
            int winner,
            int fromWho,
            List<TheMahjongTile> closedHand,
            List<TheMahjongTile> doraIndicators,
            List<TheMahjongTile> uraDoraIndicators,
            boolean isLastRound,

            int[] scoreDeltas
    ) implements TenhouAction {}

    record DoraReveal(TheMahjongTile indicator) implements TenhouAction {}

    record ExhaustiveDraw() implements TenhouAction {}

    enum ClaimType {
        CHI, PON, DAIMINKAN, KAKAN, ANKAN, KITA
    }
}
