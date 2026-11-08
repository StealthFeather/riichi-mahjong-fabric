package com.themahjong.yaku;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public sealed interface HandShape permits HandShape.Standard, HandShape.Chitoitsu, HandShape.Kokushimusou {
    enum WaitShape {
        RYANMEN(0),
        KANCHAN(2),
        PENCHAN(2),
        SHANPON(0),
        TANKI(2);

        private final int fu;
        WaitShape(int fu) { this.fu = fu; }
        public int fu() { return fu; }
    }

    sealed interface ConcealedGroup permits ConcealedGroup.Sequence, ConcealedGroup.Triplet {
        List<TheMahjongTile> tiles();

        record Sequence(List<TheMahjongTile> tiles) implements ConcealedGroup {
            public Sequence {
                if (tiles.size() != 3) throw new IllegalArgumentException("Sequence must have 3 tiles, got " + tiles.size());
                tiles = List.copyOf(tiles);
            }
        }

        record Triplet(List<TheMahjongTile> tiles) implements ConcealedGroup {
            public Triplet {
                if (tiles.size() != 3) throw new IllegalArgumentException("Triplet must have 3 tiles, got " + tiles.size());
                tiles = List.copyOf(tiles);
            }
        }
    }

    record Standard(List<TheMahjongMeld> melds, List<ConcealedGroup> closedGroups,
                    TheMahjongTile pair, Optional<WaitShape> waitShape)
            implements HandShape {
        public Standard {
            if (melds.size() + closedGroups.size() != 4)
                throw new IllegalArgumentException(
                        "Standard hand must have 4 groups total (melds + closedGroups), got "
                                + (melds.size() + closedGroups.size()));
            if (pair == null) throw new IllegalArgumentException("pair cannot be null");
            if (waitShape == null) throw new IllegalArgumentException("waitShape cannot be null; use Optional.empty()");
            melds = List.copyOf(melds);
            closedGroups = List.copyOf(closedGroups);
        }

        public Standard(List<TheMahjongMeld> melds, List<ConcealedGroup> closedGroups, TheMahjongTile pair) {
            this(melds, closedGroups, pair, Optional.empty());
        }

        public boolean closed() {
            return melds.stream().noneMatch(TheMahjongMeld::open);
        }
    }

    record Chitoitsu(List<TheMahjongTile> pairs) implements HandShape {
        public Chitoitsu {
            if (pairs.size() != 7) throw new IllegalArgumentException("Chitoitsu must have 7 pairs, got " + pairs.size());
            pairs = List.copyOf(pairs);
        }

        static Optional<Chitoitsu> tryFrom(List<TheMahjongTile> sorted) {
            Map<Long, Integer> counts = countsBySuitRank(sorted);
            if (counts.size() != 7) return Optional.empty();
            if (counts.values().stream().anyMatch(c -> c != 2)) return Optional.empty();
            List<TheMahjongTile> pairs = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (TheMahjongTile t : sorted) {
                if (seen.add(suitRankKey(t))) pairs.add(t);
            }
            return Optional.of(new Chitoitsu(pairs));
        }
    }

    record Kokushimusou(TheMahjongTile pairTile) implements HandShape {
        public Kokushimusou {
            if (pairTile == null) throw new IllegalArgumentException("pairTile cannot be null");
            if (!pairTile.terminal() && !pairTile.honor())
                throw new IllegalArgumentException("Kokushimusou pairTile must be a terminal or honor");
        }

        static Optional<Kokushimusou> tryFrom(List<TheMahjongTile> sorted) {
            Set<Long> required = kokushiRequiredKeys();
            Map<Long, Integer> counts = countsBySuitRank(sorted);
            if (!counts.keySet().equals(required)) return Optional.empty();
            TheMahjongTile pairTile = null;
            for (Map.Entry<Long, Integer> e : counts.entrySet()) {
                if (e.getValue() == 2) {
                    if (pairTile != null) return Optional.empty();
                    long key = e.getKey();
                    pairTile = sorted.stream().filter(t -> suitRankKey(t) == key).findFirst().orElseThrow();
                }
            }
            if (pairTile == null) return Optional.empty();
            return Optional.of(new Kokushimusou(pairTile));
        }

        private static Set<Long> kokushiRequiredKeys() {
            Set<Long> keys = new HashSet<>();
            for (TheMahjongTile.Suit suit : new TheMahjongTile.Suit[]{
                    TheMahjongTile.Suit.MANZU, TheMahjongTile.Suit.PINZU, TheMahjongTile.Suit.SOUZU}) {
                keys.add(suitRankKey(suit, 1));
                keys.add(suitRankKey(suit, suit.maxRank()));
            }
            for (TheMahjongTile.Wind w : TheMahjongTile.Wind.values()) {
                keys.add(suitRankKey(TheMahjongTile.Suit.WIND, w.tileRank()));
            }
            for (TheMahjongTile.Dragon d : TheMahjongTile.Dragon.values()) {
                keys.add(suitRankKey(TheMahjongTile.Suit.DRAGON, d.tileRank()));
            }
            return keys;
        }
    }

    public static List<HandShape> decompose(List<TheMahjongTile> concealedTiles, List<TheMahjongMeld> melds) {
        int closedNeeded = 4 - melds.size();
        if (closedNeeded < 0) return List.of();
        if (concealedTiles.size() != closedNeeded * 3 + 2) return List.of();

        List<TheMahjongTile> sorted = sort(concealedTiles);
        List<HandShape> results = new ArrayList<>();

        decomposeStandard(sorted, melds, closedNeeded, results);

        if (melds.isEmpty()) {
            Chitoitsu.tryFrom(sorted).ifPresent(results::add);
            Kokushimusou.tryFrom(sorted).ifPresent(results::add);
        }

        return results;
    }

    public static List<HandShape> decomposeForWin(
            List<TheMahjongTile> concealedTiles,
            List<TheMahjongMeld> melds,
            TheMahjongTile winningTile) {
        if (winningTile == null) throw new IllegalArgumentException("winningTile cannot be null");
        List<HandShape> base = decompose(concealedTiles, melds);
        List<HandShape> out = new ArrayList<>();
        for (HandShape h : base) {
            if (h instanceof Standard s) {
                List<Standard> variants = annotateWaits(s, winningTile);
                if (variants.isEmpty()) {
                    out.add(s);
                } else {
                    out.addAll(variants);
                }
            } else {
                out.add(h);
            }
        }
        return out;
    }

    private static List<Standard> annotateWaits(Standard s, TheMahjongTile winTile) {
        List<Standard> out = new ArrayList<>();
        if (winTile.matchesSuitRank(s.pair())) {
            out.add(new Standard(s.melds(), s.closedGroups(), s.pair(), Optional.of(WaitShape.TANKI)));
        }
        for (ConcealedGroup g : s.closedGroups()) {
            if (g instanceof ConcealedGroup.Triplet t) {
                if (winTile.matchesSuitRank(t.tiles().get(0))) {
                    out.add(new Standard(s.melds(), s.closedGroups(), s.pair(), Optional.of(WaitShape.SHANPON)));
                }
            } else if (g instanceof ConcealedGroup.Sequence seq) {
                TheMahjongTile low  = seq.tiles().get(0);
                TheMahjongTile mid  = seq.tiles().get(1);
                TheMahjongTile high = seq.tiles().get(2);
                if (winTile.matchesSuitRank(mid)) {
                    out.add(new Standard(s.melds(), s.closedGroups(), s.pair(), Optional.of(WaitShape.KANCHAN)));
                } else if (winTile.matchesSuitRank(low)) {
                    WaitShape ws = (high.rank() == low.suit().maxRank()) ? WaitShape.PENCHAN : WaitShape.RYANMEN;
                    out.add(new Standard(s.melds(), s.closedGroups(), s.pair(), Optional.of(ws)));
                } else if (winTile.matchesSuitRank(high)) {
                    WaitShape ws = (low.rank() == 1) ? WaitShape.PENCHAN : WaitShape.RYANMEN;
                    out.add(new Standard(s.melds(), s.closedGroups(), s.pair(), Optional.of(ws)));
                }
            }
        }
        return out;
    }

    private static void decomposeStandard(List<TheMahjongTile> sorted, List<TheMahjongMeld> melds,
                                           int closedNeeded, List<HandShape> out) {
        for (int i = 0; i < sorted.size(); i++) {
            TheMahjongTile pairCandidate = sorted.get(i);
            if (i > 0 && sorted.get(i - 1).matchesSuitRank(pairCandidate)) continue;

            boolean hasSecond = false;
            for (int j = i + 1; j < sorted.size(); j++) {
                if (sorted.get(j).matchesSuitRank(pairCandidate)) { hasSecond = true; break; }
            }
            if (!hasSecond) continue;

            List<TheMahjongTile> remaining = new ArrayList<>(sorted);
            removeFirstBySuitRank(remaining, pairCandidate);
            removeFirstBySuitRank(remaining, pairCandidate);

            List<List<ConcealedGroup>> groupDecomps = new ArrayList<>();
            decomposeGroups(remaining, closedNeeded, groupDecomps, new ArrayList<>());
            for (List<ConcealedGroup> groups : groupDecomps) {
                out.add(new Standard(melds, groups, pairCandidate));
            }
        }
    }

    private static void decomposeGroups(List<TheMahjongTile> tiles, int needed,
                                         List<List<ConcealedGroup>> out,
                                         List<ConcealedGroup> current) {
        if (needed == 0) {
            if (tiles.isEmpty()) out.add(new ArrayList<>(current));
            return;
        }
        if (tiles.isEmpty()) return;

        TheMahjongTile first = tiles.get(0);

        if (countBySuitRank(tiles, first) >= 3) {
            List<TheMahjongTile> rem = new ArrayList<>(tiles);
            List<TheMahjongTile> trip = new ArrayList<>();
            for (int i = 0; i < 3; i++) trip.add(removeFirstBySuitRank(rem, first));
            current.add(new ConcealedGroup.Triplet(trip));
            decomposeGroups(rem, needed - 1, out, current);
            current.remove(current.size() - 1);
        }

        TheMahjongTile.Suit suit = first.suit();
        if (suit.isNumber() && first.rank() <= suit.maxRank() - 2) {
            TheMahjongTile mid = findFirstBySuitRank(tiles, suit, first.rank() + 1);
            TheMahjongTile end = findFirstBySuitRank(tiles, suit, first.rank() + 2);
            if (mid != null && end != null) {
                List<TheMahjongTile> rem = new ArrayList<>(tiles);
                TheMahjongTile t1 = removeFirstBySuitRank(rem, first);
                TheMahjongTile t2 = removeFirstBySuitRank(rem, mid);
                TheMahjongTile t3 = removeFirstBySuitRank(rem, end);
                current.add(new ConcealedGroup.Sequence(List.of(t1, t2, t3)));
                decomposeGroups(rem, needed - 1, out, current);
                current.remove(current.size() - 1);
            }
        }
    }

    private static List<TheMahjongTile> sort(List<TheMahjongTile> tiles) {
        return tiles.stream()
                .sorted(Comparator.comparingInt((TheMahjongTile t) -> t.suit().ordinal())
                                  .thenComparingInt(TheMahjongTile::rank))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static long suitRankKey(TheMahjongTile t) {
        return suitRankKey(t.suit(), t.rank());
    }

    private static long suitRankKey(TheMahjongTile.Suit suit, int rank) {
        return (long) suit.ordinal() * 100 + rank;
    }

    private static int countBySuitRank(List<TheMahjongTile> tiles, TheMahjongTile ref) {
        int count = 0;
        for (TheMahjongTile t : tiles) if (t.matchesSuitRank(ref)) count++;
        return count;
    }

    private static Map<Long, Integer> countsBySuitRank(List<TheMahjongTile> tiles) {
        Map<Long, Integer> map = new HashMap<>();
        for (TheMahjongTile t : tiles) map.merge(suitRankKey(t), 1, Integer::sum);
        return map;
    }

    private static TheMahjongTile findFirstBySuitRank(List<TheMahjongTile> tiles,
                                                       TheMahjongTile.Suit suit, int rank) {
        for (TheMahjongTile t : tiles) if (t.suit() == suit && t.rank() == rank) return t;
        return null;
    }

    private static TheMahjongTile removeFirstBySuitRank(List<TheMahjongTile> tiles, TheMahjongTile ref) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).matchesSuitRank(ref)) return tiles.remove(i);
        }
        throw new IllegalStateException("Tile not found in list: " + ref);
    }
}
