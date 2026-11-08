package com.themahjong;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public final class TheMahjongMatch {
    public enum State {
        NOT_STARTED,
        IN_ROUND,
        BETWEEN_ROUNDS,
        ENDED
    }

    private final int playerCount;
    private final int startingPoints;
    private final int targetPoints;
    private final int roundCount;
    private final State state;
    private final TheMahjongTileSet tileSet;
    private final TheMahjongRuleSet ruleSet;
    private final List<TheMahjongRound> completedRounds;
    private final TheMahjongRound currentRound;

    public TheMahjongMatch(
            int playerCount,
            int startingPoints,
            int targetPoints,
            int roundCount,
            State state,
            TheMahjongTileSet tileSet,
            TheMahjongRuleSet ruleSet,
            List<TheMahjongRound> completedRounds,
            TheMahjongRound currentRound) {
        int maxPlayers = TheMahjongTile.Wind.values().length;
        if (playerCount < 1 || playerCount > maxPlayers)
            throw new IllegalArgumentException("playerCount must be between 1 and " + maxPlayers);
        if (startingPoints < 0) throw new IllegalArgumentException("startingPoints cannot be negative");
        if (targetPoints < 0) throw new IllegalArgumentException("targetPoints cannot be negative");
        if (roundCount < 1) throw new IllegalArgumentException("roundCount must be positive");
        if (state == null) throw new IllegalArgumentException("state cannot be null");
        if (tileSet == null) throw new IllegalArgumentException("tileSet cannot be null");
        if (ruleSet == null) throw new IllegalArgumentException("ruleSet cannot be null");
        if (completedRounds == null) throw new IllegalArgumentException("completedRounds cannot be null");
        this.playerCount = playerCount;
        this.startingPoints = startingPoints;
        this.targetPoints = targetPoints;
        this.roundCount = roundCount;
        this.state = state;
        this.tileSet = tileSet;
        this.ruleSet = ruleSet;
        this.completedRounds = List.copyOf(completedRounds);
        this.currentRound = currentRound;
    }

    public static TheMahjongMatch defaults() {
        return new TheMahjongMatch(
                4,
                30_000,
                30_000,
                2,
                State.NOT_STARTED,
                TheMahjongTileSet.standardRiichi(false),
                TheMahjongRuleSet.wrc(),
                List.of(),
                null);
    }

    public static TheMahjongMatch defaultTenhou() {
        return new TheMahjongMatch(
                4,
                25_000,
                30_000,
                2,
                State.NOT_STARTED,
                TheMahjongTileSet.standardRiichi(true),
                TheMahjongRuleSet.tenhou(),
                List.of(),
                null);
    }

    public static TheMahjongMatch defaultMahjongSoul() {
        return new TheMahjongMatch(
                4,
                25_000,
                30_000,
                2,
                State.NOT_STARTED,
                TheMahjongTileSet.standardRiichi(true),
                TheMahjongRuleSet.mahjongSoul(),
                List.of(),
                null);
    }

    public static TheMahjongMatch defaultTenhouSanma() {
        return new TheMahjongMatch(
                3,
                35_000,
                40_000,
                2,
                State.NOT_STARTED,
                TheMahjongTileSet.standardSanma(true),
                TheMahjongRuleSet.tenhouSanma(),
                List.of(),
                null);
    }

    public static TheMahjongMatch defaultMahjongSoulSanma() {
        return new TheMahjongMatch(
                3,
                35_000,
                40_000,
                2,
                State.NOT_STARTED,
                TheMahjongTileSet.standardSanma(true),
                TheMahjongRuleSet.mahjongSoulSanma(),
                List.of(),
                null);
    }

    public int playerCount() {
        return playerCount;
    }

    public int startingPoints() {
        return startingPoints;
    }

    public int targetPoints() {
        return targetPoints;
    }

    public int roundCount() {
        return roundCount;
    }

    public State state() {
        return state;
    }

    public TheMahjongTileSet tileSet() {
        return tileSet;
    }

    public TheMahjongRuleSet ruleSet() {
        return ruleSet;
    }

    public List<TheMahjongRound> completedRounds() {
        return completedRounds;
    }

    public Optional<TheMahjongRound> currentRound() {
        return Optional.ofNullable(currentRound);
    }

    public TheMahjongMatch withPlayerCount(int playerCount) {
        int maxPlayers = TheMahjongTile.Wind.values().length;
        if (playerCount < 1 || playerCount > maxPlayers) {
            throw new IllegalArgumentException("playerCount must be between 1 and " + maxPlayers);
        }
        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount, state, tileSet, ruleSet, completedRounds, currentRound);
    }

    public TheMahjongMatch withStartingPoints(int startingPoints) {
        if (startingPoints < 0) {
            throw new IllegalArgumentException("startingPoints cannot be negative");
        }
        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount, state, tileSet, ruleSet, completedRounds, currentRound);
    }

    public TheMahjongMatch withTargetPoints(int targetPoints) {
        if (targetPoints < 0) {
            throw new IllegalArgumentException("targetPoints cannot be negative");
        }
        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount, state, tileSet, ruleSet, completedRounds, currentRound);
    }

    public TheMahjongMatch withRoundCount(int roundCount) {
        if (roundCount < 1) {
            throw new IllegalArgumentException("roundCount must be positive");
        }
        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount, state, tileSet, ruleSet, completedRounds, currentRound);
    }

    public TheMahjongMatch withTileSet(TheMahjongTileSet tileSet) {
        if (tileSet == null) {
            throw new IllegalArgumentException("tileSet cannot be null");
        }
        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount, state, tileSet, ruleSet, completedRounds, currentRound);
    }

    public TheMahjongMatch withRuleSet(TheMahjongRuleSet ruleSet) {
        if (ruleSet == null) {
            throw new IllegalArgumentException("ruleSet cannot be null");
        }
        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount, state, tileSet, ruleSet, completedRounds, currentRound);
    }

    public TheMahjongMatch validate() {
        if (targetPoints < startingPoints) {
            throw new IllegalArgumentException("targetPoints must be at least startingPoints");
        }
        if (state == State.NOT_STARTED) {
            if (currentRound != null) {
                throw new IllegalArgumentException("currentRound must be null before the match starts");
            }
            if (!completedRounds.isEmpty()) {
                throw new IllegalArgumentException("completedRounds must be empty before the match starts");
            }
        } else {
            if (currentRound == null) {
                throw new IllegalArgumentException("currentRound cannot be null after the match starts");
            }
            if (currentRound.players().size() != playerCount) {
                throw new IllegalArgumentException("currentRound player count must match the match playerCount");
            }
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TheMahjongMatch other)) return false;
        return playerCount == other.playerCount
                && startingPoints == other.startingPoints
                && targetPoints == other.targetPoints
                && roundCount == other.roundCount
                && state == other.state
                && tileSet.equals(other.tileSet)
                && ruleSet.equals(other.ruleSet)
                && completedRounds.equals(other.completedRounds)
                && Objects.equals(currentRound, other.currentRound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerCount, startingPoints, targetPoints, roundCount, state, tileSet, ruleSet, completedRounds, currentRound);
    }

    @Override
    public String toString() {
        return "TheMahjongMatch{state=" + state
                + ", playerCount=" + playerCount
                + ", startingPoints=" + startingPoints
                + ", targetPoints=" + targetPoints
                + ", roundCount=" + roundCount
                + ", ruleSet=" + ruleSet
                + ", completedRounds=" + completedRounds.size()
                + ", currentRound=" + (currentRound == null ? "none" : currentRound.state())
                + '}';
    }

    public TheMahjongMatch startRound() {
        return startRound(new Random());
    }

    public TheMahjongMatch startRound(Random random) {
        validate();
        if (state != State.NOT_STARTED) {
            throw new IllegalStateException("match has already started");
        }
        if (random == null) {
            throw new IllegalArgumentException("random cannot be null");
        }
        List<TheMahjongTile> wall = new ArrayList<>(tileSet.createOrderedWall());
        Collections.shuffle(wall, random);
        TheMahjongRound initialRound = TheMahjongRound.start(playerCount, startingPoints, wall);
        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount, State.IN_ROUND, tileSet, ruleSet, completedRounds, initialRound);
    }

    public TheMahjongMatch advanceRound(Random random, boolean renchan, int nextHonba) {
        if (state != State.IN_ROUND)
            throw new IllegalStateException("advanceRound() requires state IN_ROUND, was " + state);
        if (currentRound == null || currentRound.state() != TheMahjongRound.State.ENDED)
            throw new IllegalStateException("advanceRound() requires currentRound state ENDED");
        if (random == null)
            throw new IllegalArgumentException("random cannot be null");
        if (nextHonba < 0)
            throw new IllegalArgumentException("nextHonba cannot be negative");

        List<TheMahjongRound> newCompleted = new ArrayList<>(completedRounds);
        newCompleted.add(currentRound);

        if (ruleSet.bustingEndsGame()) {
            for (TheMahjongPlayer p : currentRound.players()) {
                if (p.points() < 0) {
                    return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount,
                            State.ENDED, tileSet, ruleSet, List.copyOf(newCompleted), currentRound);
                }
            }
        }

        if (ruleSet.suddenDeathRound() && currentRound.roundWind().ordinal() >= roundCount) {
            for (TheMahjongPlayer p : currentRound.players()) {
                if (p.points() >= targetPoints) {
                    return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount,
                            State.ENDED, tileSet, ruleSet, List.copyOf(newCompleted), currentRound);
                }
            }
        }

        int nextDealerSeat;

        int nextWindOrdinal;
        int nextHandNumber;

        if (renchan) {
            nextDealerSeat = currentRound.dealerSeat();
            nextWindOrdinal = currentRound.roundWind().ordinal();
            nextHandNumber = currentRound.handNumber();
        } else {
            nextDealerSeat = (currentRound.dealerSeat() + 1) % playerCount;
            if (nextDealerSeat == 0) {
                nextWindOrdinal = currentRound.roundWind().ordinal() + 1;
                nextHandNumber = 1;
            } else {
                nextWindOrdinal = currentRound.roundWind().ordinal();
                nextHandNumber = currentRound.handNumber() + 1;
            }
        }

        if (!renchan && nextWindOrdinal >= roundCount) {
            boolean noneAtTarget = currentRound.players().stream().noneMatch(p -> p.points() >= targetPoints);
            boolean extendForSuddenDeath = ruleSet.suddenDeathRound()
                    && noneAtTarget
                    && nextWindOrdinal < TheMahjongTile.Wind.values().length;
            if (!extendForSuddenDeath) {
                return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount,
                        State.ENDED, tileSet, ruleSet, List.copyOf(newCompleted), currentRound);
            }
        }

        TheMahjongTile.Wind nextRoundWind = TheMahjongTile.Wind.values()[nextWindOrdinal];

        List<Integer> playerPoints = new ArrayList<>(playerCount);
        for (TheMahjongPlayer p : currentRound.players()) playerPoints.add(p.points());

        List<TheMahjongTile> wall = new ArrayList<>(tileSet.createOrderedWall());
        Collections.shuffle(wall, random);
        TheMahjongRound nextRound = TheMahjongRound.start(
                playerPoints, wall, nextRoundWind, nextHandNumber, nextHonba,
                currentRound.riichiSticks(), nextDealerSeat);

        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount,
                State.IN_ROUND, tileSet, ruleSet, List.copyOf(newCompleted), nextRound);
    }

    public boolean isAllLast() {
        if (state != State.IN_ROUND || currentRound == null) return false;

        if (ruleSet.suddenDeathRound() && currentRound.roundWind().ordinal() >= roundCount) return false;
        int nextDealerSeat = (currentRound.dealerSeat() + 1) % playerCount;
        if (nextDealerSeat != 0) return false;
        return currentRound.roundWind().ordinal() + 1 >= roundCount;
    }

    public boolean isAgariYameEligible() {
        if (!ruleSet.agariYameAllowed()) return false;
        if (state != State.IN_ROUND || currentRound == null) return false;
        if (currentRound.state() != TheMahjongRound.State.ENDED) return false;
        if (!isAllLast()) return false;
        int dealerSeat = currentRound.dealerSeat();
        int dealerPoints = currentRound.players().get(dealerSeat).points();
        for (int i = 0; i < currentRound.players().size(); i++) {
            if (i != dealerSeat && currentRound.players().get(i).points() >= dealerPoints)
                return false;
        }
        return true;
    }

    public TheMahjongMatch applyFinalDeposits() {
        if (state != State.ENDED)
            throw new IllegalStateException("applyFinalDeposits() requires state ENDED, was " + state);
        if (!ruleSet.depositsToFirstAtEnd() || currentRound == null || currentRound.riichiSticks() == 0)
            return this;

        List<TheMahjongPlayer> players = currentRound.players();
        int firstSeat = 0;
        for (int i = 1; i < players.size(); i++) {
            if (players.get(i).points() > players.get(firstSeat).points()) {
                firstSeat = i;
            }
        }

        int bonus = currentRound.riichiSticks() * 1000;
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        TheMahjongPlayer p = newPlayers.get(firstSeat);
        newPlayers.set(firstSeat, new TheMahjongPlayer(
                p.points() + bonus, p.riichiState(), p.ippatsuEligible(),
                p.currentHand(), p.melds(), p.discards(), p.temporaryFuritenTiles(),
                p.riichiPermanentFuriten()));

        TheMahjongRound updatedRound = new TheMahjongRound(
                currentRound.roundWind(), currentRound.handNumber(), currentRound.honba(), 0,
                currentRound.dealerSeat(), currentRound.state(), currentRound.currentTurnSeat(),
                -1, TheMahjongRound.ActiveTile.none(),
                currentRound.liveWall(), currentRound.rinshanTiles(),
                currentRound.doraIndicators(), currentRound.uraDoraIndicators(),
                currentRound.revealedDoraCount(), newPlayers, List.of());

        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount,
                State.ENDED, tileSet, ruleSet, completedRounds, updatedRound);
    }
}
