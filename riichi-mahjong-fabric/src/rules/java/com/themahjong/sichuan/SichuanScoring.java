package com.themahjong.sichuan;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTile.Suit;
import com.themahjong.yaku.NonYakuman;
import com.themahjong.yaku.WinResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Sichuan shapes and payments, independent of riichi yaku, furiten and dealer bonuses. */
public final class SichuanScoring {
    public static final int BASE_POINTS = 1000;
    public static final int MAX_FAN = 4;
    private SichuanScoring() {}

    public static boolean isJoker(TheMahjongTile tile, boolean enabled) {
        return enabled && tile.suit() == Suit.DRAGON
                && tile.rank() == TheMahjongTile.Dragon.CHUN.tileRank();
    }

    public static boolean hasMissingSuit(TheMahjongPlayer player, Suit missingSuit) {
        return missingSuit != null && (player.currentHand().stream().anyMatch(t -> t.suit() == missingSuit)
                || player.melds().stream().flatMap(m -> m.tiles().stream()).anyMatch(t -> t.suit() == missingSuit));
    }

    /** The drawn tile is already in the hand; a claimed tile is added only for ron/chankan. */
    public static Optional<WinResult> tryWin(TheMahjongRound round, int seat, int fromSeat,
            TheMahjongTile tile, Suit missingSuit, Set<Integer> winners, boolean jokersEnabled,
            boolean rinshan, boolean chankan) {
        if (seat < 0 || seat >= round.players().size() || fromSeat < 0
                || fromSeat >= round.players().size() || missingSuit == null || !missingSuit.isNumber()
                || winners.contains(seat) || tile == null) return Optional.empty();
        var player = round.players().get(seat);
        var hand = new ArrayList<>(player.currentHand());
        if (seat != fromSeat) hand.add(tile);
        var shape = shape(hand, player.melds(), missingSuit, jokersEnabled);
        if (shape.isEmpty()) return Optional.empty();

        var yaku = new ArrayList<>(shape.get().yaku());
        int fan = shape.get().fan();
        if (seat == fromSeat) { yaku.add(NonYakuman.SC_ZIMO); fan++; }
        if (rinshan) { yaku.add(NonYakuman.SC_GANGSHANGHUA); fan++; }
        if (chankan) { yaku.add(NonYakuman.SC_QIANGGANGHU); fan++; }
        if (round.liveWall().isEmpty()) { yaku.add(NonYakuman.SC_HAIDI); fan++; }
        fan = Math.min(MAX_FAN, fan);
        int payment = BASE_POINTS * (1 << fan);
        var deltas = new ArrayList<Integer>();
        for (int i = 0; i < round.players().size(); i++) deltas.add(0);
        if (seat == fromSeat) {
            for (int other = 0; other < deltas.size(); other++) {
                if (other == seat || winners.contains(other)) continue;
                deltas.set(other, -payment);
                deltas.set(seat, deltas.get(seat) + payment);
            }
        } else {
            if (winners.contains(fromSeat)) return Optional.empty();
            deltas.set(fromSeat, -payment);
            deltas.set(seat, payment);
        }
        return Optional.of(new WinResult(List.copyOf(yaku), List.of(), fan, 0, 0, List.copyOf(deltas)));
    }

    /** A waiting player must already have discarded every tile of the declared missing suit. */
    public static boolean inTenpai(TheMahjongPlayer player, Suit missingSuit, boolean jokersEnabled) {
        if (missingSuit == null || !missingSuit.isNumber() || hasMissingSuit(player, missingSuit)) return false;
        var hand = new ArrayList<>(player.currentHand());
        for (var suit : List.of(Suit.MANZU, Suit.PINZU, Suit.SOUZU)) {
            if (suit == missingSuit) continue;
            for (int rank = 1; rank <= 9; rank++) {
                var tile = new TheMahjongTile(suit, rank, false);
                if (physicalCopies(player, tile) >= 4) continue;
                hand.add(tile);
                boolean legal = shape(hand, player.melds(), missingSuit, jokersEnabled).isPresent();
                hand.removeLast();
                if (legal) return true;
            }
        }
        if (jokersEnabled) {
            var joker = new TheMahjongTile(Suit.DRAGON, TheMahjongTile.Dragon.CHUN.tileRank(), false);
            if (physicalCopies(player, joker) < 4) {
                hand.add(joker);
                return shape(hand, player.melds(), missingSuit, true).isPresent();
            }
        }
        return false;
    }

    /** Largest ordinary ron payment used by exhaustive-draw 查大叫 settlement. */
    public static int tenpaiPayment(TheMahjongPlayer player, Suit missingSuit, boolean jokersEnabled) {
        if (missingSuit == null || !missingSuit.isNumber() || hasMissingSuit(player, missingSuit)) return 0;
        int best = 0;
        var hand = new ArrayList<>(player.currentHand());
        for (var suit : List.of(Suit.MANZU, Suit.PINZU, Suit.SOUZU, Suit.DRAGON)) {
            if (suit == missingSuit || suit == Suit.DRAGON && !jokersEnabled) continue;
            int first = suit == Suit.DRAGON ? TheMahjongTile.Dragon.CHUN.tileRank() : 1;
            int last = suit == Suit.DRAGON ? first : 9;
            for (int rank = first; rank <= last; rank++) {
                var tile = new TheMahjongTile(suit, rank, false);
                if (physicalCopies(player, tile) >= 4) continue;
                hand.add(tile);
                var result = shape(hand, player.melds(), missingSuit, jokersEnabled);
                hand.removeLast();
                if (result.isPresent()) best = Math.max(best, BASE_POINTS * (1 << Math.min(MAX_FAN, result.get().fan())));
            }
        }
        return best;
    }

    private static int physicalCopies(TheMahjongPlayer player, TheMahjongTile tile) {
        return (int) (player.currentHand().stream().filter(tile::matchesSuitRank).count()
                + player.melds().stream().flatMap(m -> m.tiles().stream()).filter(tile::matchesSuitRank).count());
    }

    private record ShapeScore(int fan, List<NonYakuman> yaku) {}

    private static Optional<ShapeScore> shape(List<TheMahjongTile> hand, List<TheMahjongMeld> melds,
            Suit missingSuit, boolean jokersEnabled) {
        int groups = 4 - melds.size();
        if (groups < 0 || hand.size() != groups * 3 + 2) return Optional.empty();
        int[] counts = new int[27];
        int[] physical = new int[28];
        int wild = 0;
        Suit onlySuit = null;
        boolean pure = true, jiang = true;
        var allTiles = new ArrayList<>(hand);
        for (var meld : melds) {
            if (meld instanceof TheMahjongMeld.Chi) return Optional.empty();
            allTiles.addAll(meld.tiles());
        }
        for (var tile : allTiles) {
            if (tile.suit() == missingSuit) return Optional.empty();
            if (isJoker(tile, jokersEnabled)) {
                if (++physical[27] > 4) return Optional.empty();
                continue;
            }
            if (!tile.suit().isNumber()) return Optional.empty();
            int kind = tile.suit().ordinal() * 9 + tile.rank() - 1;
            if (++physical[kind] > 4) return Optional.empty();
            if (onlySuit == null) onlySuit = tile.suit();
            else if (onlySuit != tile.suit()) pure = false;
            if (tile.rank() != 2 && tile.rank() != 5 && tile.rank() != 8) jiang = false;
        }
        for (var meld : melds) {
            for (var tile : meld.tiles()) if (isJoker(tile, jokersEnabled)) return Optional.empty();
        }
        for (var tile : hand) {
            if (isJoker(tile, jokersEnabled)) wild++;
            else counts[tile.suit().ordinal() * 9 + tile.rank() - 1]++;
        }
        int roots = 0;
        for (int i = 0; i < 27; i++) if (physical[i] == 4) roots++;
        boolean standard = standard(counts, wild, groups, false);
        boolean seven = melds.isEmpty() && sevenPairs(counts, wild);
        if (!standard && !seven) return Optional.empty();

        ShapeScore best = null;
        if (standard) {
            int fan = 0;
            var yaku = new ArrayList<NonYakuman>();
            boolean triplets = standard(counts, wild, groups, true);
            if (triplets && jiang) { fan += 3; yaku.add(NonYakuman.SC_JIANGDUI); }
            else if (triplets) { fan++; yaku.add(NonYakuman.SC_PENGPENGHU); }
            if (melds.size() == 4) { fan++; yaku.add(NonYakuman.SC_JINGOUDIAO); }
            if (pure) { fan += 2; yaku.add(NonYakuman.SC_QINGYISE); }
            for (int i = 0; i < roots; i++) { fan++; yaku.add(NonYakuman.SC_GEN); }
            if (yaku.isEmpty()) yaku.add(NonYakuman.SC_PINGHU);
            best = new ShapeScore(fan, List.copyOf(yaku));
        }
        if (seven) {
            int fan = 2 + roots;
            var yaku = new ArrayList<NonYakuman>();
            yaku.add(roots > 0 ? NonYakuman.SC_LONGQIDUI : NonYakuman.SC_QIDUI);
            for (int i = 1; i < roots; i++) yaku.add(NonYakuman.SC_GEN);
            if (pure) { fan += 2; yaku.add(NonYakuman.SC_QINGYISE); }
            if (best == null || fan > best.fan()) best = new ShapeScore(fan, List.copyOf(yaku));
        }
        return Optional.of(best);
    }

    private static boolean sevenPairs(int[] counts, int wild) {
        int odd = 0, total = wild;
        for (int n : counts) { odd += n & 1; total += n; }
        return total == 14 && odd <= wild && ((wild - odd) & 1) == 0;
    }

    private static boolean standard(int[] counts, int wild, int groups, boolean tripletsOnly) {
        var memo = new HashMap<String, Boolean>();
        if (wild >= 2 && melds(counts, wild - 2, groups, tripletsOnly, memo)) return true;
        for (int kind = 0; kind < counts.length; kind++) {
            int max = Math.min(2, counts[kind]);
            for (int take = 1; take <= max; take++) {
                if (2 - take > wild) continue;
                counts[kind] -= take;
                boolean formed = melds(counts, wild - (2 - take), groups, tripletsOnly, memo);
                counts[kind] += take;
                if (formed) return true;
            }
        }
        return false;
    }

    private static boolean melds(int[] counts, int wild, int groups, boolean tripletsOnly,
            Map<String, Boolean> memo) {
        int first = -1, total = wild;
        for (int i = 0; i < counts.length; i++) {
            total += counts[i];
            if (first < 0 && counts[i] > 0) first = i;
        }
        if (total != groups * 3 || wild < 0 || groups < 0) return false;
        if (first < 0) return total == groups * 3;
        String key = Arrays.toString(counts) + ':' + wild + ':' + groups;
        Boolean cached = memo.get(key);
        if (cached != null) return cached;
        for (int take = 1; take <= Math.min(3, counts[first]); take++) {
            int need = 3 - take;
            if (need > wild) continue;
            counts[first] -= take;
            boolean formed = melds(counts, wild - need, groups - 1, tripletsOnly, memo);
            counts[first] += take;
            if (formed) { memo.put(key, true); return true; }
        }
        if (!tripletsOnly) {
            int rank = first % 9;
            for (int start = Math.max(0, rank - 2); start <= Math.min(6, rank); start++) {
                int kind = first - rank + start;
                if (sequence(counts, wild, groups, kind, 0, first, memo)) {
                    memo.put(key, true);
                    return true;
                }
            }
        }
        memo.put(key, false);
        return false;
    }

    private static boolean sequence(int[] counts, int wild, int groups, int start, int offset,
            int required, Map<String, Boolean> memo) {
        if (offset == 3) return melds(counts, wild, groups - 1, false, memo);
        int kind = start + offset;
        if (counts[kind] > 0) {
            counts[kind]--;
            boolean formed = sequence(counts, wild, groups, start, offset + 1, required, memo);
            counts[kind]++;
            if (formed) return true;
        }
        return kind != required && wild > 0
                && sequence(counts, wild - 1, groups, start, offset + 1, required, memo);
    }
}
