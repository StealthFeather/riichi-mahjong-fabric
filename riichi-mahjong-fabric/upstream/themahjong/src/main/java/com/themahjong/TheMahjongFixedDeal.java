package com.themahjong;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class TheMahjongFixedDeal {
    private TheMahjongFixedDeal() {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int playerCount = 4;
        private int startingPoints = 25_000;
        private int targetPoints = 30_000;
        private int roundCount = 2;
        private TheMahjongTileSet tileSet = TheMahjongTileSet.standardRiichi(false);
        private TheMahjongRuleSet ruleSet = TheMahjongRuleSet.wrc();
        private TheMahjongTile.Wind roundWind = TheMahjongTile.Wind.EAST;
        private int handNumber = 1;
        private int honba = 0;
        private int riichiSticks = 0;
        private int dealerSeat = 0;
        private final List<List<TheMahjongTile>> handsBySeat = new ArrayList<>();
        private List<TheMahjongTile> rinshanReserved = List.of();
        private List<TheMahjongTile> doraIndicatorsReserved = List.of();
        private List<TheMahjongTile> uraDoraIndicatorsReserved = List.of();
        private List<TheMahjongTile> topOfWall = List.of();
        private boolean drawForDealer = false;
        private long seed = 0xC6E4D31L;

        public Builder playerCount(int n) {
            if (n < 1 || n > TheMahjongTile.Wind.values().length) {
                throw new IllegalArgumentException("playerCount must be between 1 and "
                        + TheMahjongTile.Wind.values().length);
            }
            this.playerCount = n;
            return this;
        }

        public Builder startingPoints(int p) { this.startingPoints = p; return this; }
        public Builder targetPoints(int p) { this.targetPoints = p; return this; }
        public Builder roundCount(int n) { this.roundCount = n; return this; }
        public Builder tileSet(TheMahjongTileSet ts) { this.tileSet = ts; return this; }
        public Builder ruleSet(TheMahjongRuleSet rs) { this.ruleSet = rs; return this; }
        public Builder roundWind(TheMahjongTile.Wind w) { this.roundWind = w; return this; }
        public Builder handNumber(int n) { this.handNumber = n; return this; }
        public Builder honba(int n) { this.honba = n; return this; }
        public Builder riichiSticks(int n) { this.riichiSticks = n; return this; }
        public Builder dealerSeat(int s) { this.dealerSeat = s; return this; }
        public Builder seed(long s) { this.seed = s; return this; }

        public Builder handForSeat(int seat, List<TheMahjongTile> tiles) {
            if (tiles == null || tiles.size() != TheMahjongRound.INITIAL_HAND_SIZE) {
                throw new IllegalArgumentException("handForSeat tiles must be exactly "
                        + TheMahjongRound.INITIAL_HAND_SIZE);
            }
            while (handsBySeat.size() <= seat) handsBySeat.add(null);
            handsBySeat.set(seat, List.copyOf(tiles));
            return this;
        }

        public Builder rinshanTiles(List<TheMahjongTile> tiles) {
            this.rinshanReserved = List.copyOf(tiles);
            return this;
        }

        public Builder doraIndicators(List<TheMahjongTile> tiles) {
            if (tiles.size() > TheMahjongRound.MAX_DORA_INDICATORS) {
                throw new IllegalArgumentException("at most " + TheMahjongRound.MAX_DORA_INDICATORS + " dora indicators");
            }
            this.doraIndicatorsReserved = List.copyOf(tiles);
            return this;
        }

        public Builder uraDoraIndicators(List<TheMahjongTile> tiles) {
            if (tiles.size() > TheMahjongRound.MAX_DORA_INDICATORS) {
                throw new IllegalArgumentException("at most " + TheMahjongRound.MAX_DORA_INDICATORS + " ura indicators");
            }
            this.uraDoraIndicatorsReserved = List.copyOf(tiles);
            return this;
        }

        public Builder topOfWall(List<TheMahjongTile> tiles) {
            this.topOfWall = List.copyOf(tiles);
            return this;
        }

        public Builder drawForDealer(boolean draw) {
            this.drawForDealer = draw;
            return this;
        }

        public TheMahjongRound buildRound() {
            while (handsBySeat.size() < playerCount) handsBySeat.add(null);

            Map<TheMahjongTile, Integer> available = new LinkedHashMap<>(tileSet.copiesPerTile());

            for (int seat = 0; seat < playerCount; seat++) {
                List<TheMahjongTile> hand = handsBySeat.get(seat);
                if (hand != null) reserveAll(available, hand, "hand seat " + seat);
            }
            reserveAll(available, rinshanReserved,         "rinshan reserved");
            reserveAll(available, doraIndicatorsReserved,  "dora indicators reserved");
            reserveAll(available, uraDoraIndicatorsReserved, "ura dora indicators reserved");
            reserveAll(available, topOfWall,               "top of wall");

            List<TheMahjongTile> pool = new ArrayList<>();
            for (Map.Entry<TheMahjongTile, Integer> e : available.entrySet()) {
                for (int i = 0; i < e.getValue(); i++) pool.add(e.getKey());
            }
            Collections.shuffle(pool, new Random(seed));

            int rinshanCount = TheMahjongRound.rinshanTileCountFor(playerCount);
            int doraCount    = TheMahjongRound.MAX_DORA_INDICATORS;
            List<TheMahjongTile> wall = new ArrayList<>();

            for (int seat = 0; seat < playerCount; seat++) {
                List<TheMahjongTile> hand = handsBySeat.get(seat);
                if (hand != null) {
                    wall.addAll(hand);
                } else {
                    wall.addAll(takeFromPool(pool, TheMahjongRound.INITIAL_HAND_SIZE,
                            "hand seat " + seat));
                }
            }

            wall.addAll(rinshanReserved);
            wall.addAll(takeFromPool(pool, rinshanCount - rinshanReserved.size(), "rinshan fill"));

            wall.addAll(doraIndicatorsReserved);
            wall.addAll(takeFromPool(pool, doraCount - doraIndicatorsReserved.size(), "dora fill"));

            wall.addAll(uraDoraIndicatorsReserved);
            wall.addAll(takeFromPool(pool, doraCount - uraDoraIndicatorsReserved.size(), "ura fill"));

            wall.addAll(topOfWall);
            wall.addAll(pool);

            TheMahjongRound round = TheMahjongRound.start(
                    playerCount, startingPoints, wall,
                    roundWind, handNumber, honba, riichiSticks, dealerSeat);
            if (drawForDealer) round = round.draw();
            return round;
        }

        public TheMahjongMatch buildMatch() {
            TheMahjongRound round = buildRound();
            return new TheMahjongMatch(
                    playerCount, startingPoints, targetPoints, roundCount,
                    TheMahjongMatch.State.IN_ROUND, tileSet, ruleSet,
                    List.of(), round);
        }

        private static void reserveAll(Map<TheMahjongTile, Integer> available,
                                       List<TheMahjongTile> tiles, String label) {
            for (TheMahjongTile t : tiles) {
                Integer remaining = available.get(t);
                if (remaining == null || remaining <= 0) {
                    throw new IllegalStateException(
                            "Fixed deal: tile " + t + " required by " + label
                                    + " is not available in the tile set");
                }
                if (remaining == 1) available.remove(t);
                else available.put(t, remaining - 1);
            }
        }

        private static List<TheMahjongTile> takeFromPool(List<TheMahjongTile> pool, int n, String label) {
            if (n < 0) {
                throw new IllegalStateException("Fixed deal: " + label + " requested negative tiles");
            }
            if (pool.size() < n) {
                throw new IllegalStateException(
                        "Fixed deal: not enough tiles to fill " + label
                                + " (need " + n + ", have " + pool.size() + ")");
            }
            List<TheMahjongTile> out = new ArrayList<>(pool.subList(0, n));
            pool.subList(0, n).clear();
            return out;
        }
    }
}
