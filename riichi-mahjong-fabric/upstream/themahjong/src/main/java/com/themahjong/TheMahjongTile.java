package com.themahjong;

import java.util.Comparator;

public record TheMahjongTile(
        Suit suit,
        int rank,
        boolean redDora) {
    public static final Comparator<TheMahjongTile> DISPLAY_ORDER =
            Comparator.comparingInt((TheMahjongTile t) -> t.suit().ordinal())
                    .thenComparingInt(TheMahjongTile::rank)
                    .thenComparing(TheMahjongTile::redDora);

    public enum Suit {
        MANZU,
        PINZU,
        SOUZU,
        WIND,
        DRAGON;

        public int maxRank() {
            return switch (this) {
                case MANZU, PINZU, SOUZU -> 9;
                case WIND -> Wind.values().length;
                case DRAGON -> Dragon.values().length;
            };
        }

        public boolean isNumber() {
            return this == MANZU || this == PINZU || this == SOUZU;
        }
    }

    public enum Wind {
        EAST,
        SOUTH,
        WEST,
        NORTH;

        public int tileRank() {
            return ordinal() + 1;
        }

        public String displayName() {
            return switch (this) {
                case EAST -> "East";
                case SOUTH -> "South";
                case WEST -> "West";
                case NORTH -> "North";
            };
        }

        public Wind next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Wind fromTileRank(int rank) {
            for (Wind w : values()) {
                if (w.tileRank() == rank) return w;
            }
            throw new IllegalArgumentException("no Wind with tileRank " + rank);
        }
    }

    public enum Dragon {
        HAKU,
        HATSU,
        CHUN;

        public int tileRank() {
            return ordinal() + 1;
        }

        public String displayName() {
            return switch (this) {
                case HAKU -> "White";
                case HATSU -> "Green";
                case CHUN -> "Red";
            };
        }

        public static Dragon fromTileRank(int rank) {
            for (Dragon d : values()) {
                if (d.tileRank() == rank) return d;
            }
            throw new IllegalArgumentException("no Dragon with tileRank " + rank);
        }
    }

    public TheMahjongTile {
        if (suit == null) {
            throw new IllegalArgumentException("suit cannot be null");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be positive");
        }
        if (rank > suit.maxRank()) {
            throw new IllegalArgumentException("rank must be between 1 and " + suit.maxRank() + " for suit " + suit);
        }
    }

    public boolean matchesSuitRank(TheMahjongTile other) {
        return this.suit == other.suit && this.rank == other.rank;
    }

    public boolean honor() {
        return suit == Suit.WIND || suit == Suit.DRAGON;
    }

    public boolean terminal() {
        return !honor() && (rank == 1 || rank == 9);
    }

    public String displayName() {
        String suffix = redDora ? " Red" : "";
        return switch (suit) {
            case MANZU -> rank + " Man" + suffix;
            case PINZU -> rank + " Pin" + suffix;
            case SOUZU -> rank + " Sou" + suffix;
            case WIND -> Wind.fromTileRank(rank).displayName() + suffix;
            case DRAGON -> Dragon.fromTileRank(rank).displayName() + suffix;
        };
    }
}
