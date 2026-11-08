package com.themahjong.driver;

import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.WinResult;

import java.util.List;
import java.util.Set;

public sealed interface MatchPhase
        permits MatchPhase.NotStarted, MatchPhase.Dealing, MatchPhase.AwaitingDraw,
                MatchPhase.AwaitingDiscard, MatchPhase.AwaitingClaims, MatchPhase.Resolving,
                MatchPhase.RoundEnded, MatchPhase.BetweenRounds, MatchPhase.MatchEnded {
    record NotStarted() implements MatchPhase {}

    record Dealing(Stage stage, double elapsed) implements MatchPhase {}

    enum Stage {
        WALL_BUILDING(1.0, 0.5),
        DEALING_HANDS(1.0, 0.5),
        DORA_FLIP(0.5, 0.5);

        private final double buildDuration;
        private final double pauseAfter;

        Stage(double buildDuration, double pauseAfter) {
            this.buildDuration = buildDuration;
            this.pauseAfter = pauseAfter;
        }

        public double buildDuration() {
            return buildDuration;
        }

        public double duration() {
            return buildDuration + pauseAfter;
        }
    }

    record AwaitingDraw(int seat, boolean rinshan) implements MatchPhase {}

    record AwaitingDiscard(int seat) implements MatchPhase {}

    record AwaitingClaims(
            Set<Integer> pendingSeats,
            TheMahjongTile heldTile,
            ClaimSource source) implements MatchPhase {}

    enum ClaimSource { DISCARD, KITA, KAKAN }

    record Resolving() implements MatchPhase {}

    record RoundEnded(List<WinResult> winResults) implements MatchPhase {}

    record BetweenRounds() implements MatchPhase {}

    record MatchEnded() implements MatchPhase {}
}
