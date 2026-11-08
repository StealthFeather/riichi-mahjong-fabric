package com.themahjong.yaku;

import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.HandShape.Chitoitsu;
import com.themahjong.yaku.HandShape.ConcealedGroup;
import com.themahjong.yaku.HandShape.Kokushimusou;
import com.themahjong.yaku.HandShape.Standard;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public enum Yakuman {
    KOKUSHIMUSOU,
    SUUANKOU,
    CHUURENPOUTOU,
    DAISANGEN,
    TSUISOU,
    SHOUSUUSHI,
    DAISUUSHI,
    RYUUIISOU,
    CHINROUTOU,
    SUUKANTSU,
    RENHOU,
    CHIHOU,
    TENHOU;

    public static List<Yakuman> check(HandShape shape, WinContext ctx, TheMahjongRuleSet rules) {
        List<Yakuman> result = new ArrayList<>();
        if (checkKokushimusou(shape))                       result.add(KOKUSHIMUSOU);
        if (checkSuuankou(shape, ctx))                      result.add(SUUANKOU);
        if (checkChuurenpoutou(shape))                      result.add(CHUURENPOUTOU);
        if (checkDaisangen(shape))                          result.add(DAISANGEN);
        if (checkTsuisou(shape))                            result.add(TSUISOU);
        if (checkShousuushi(shape))                         result.add(SHOUSUUSHI);
        if (checkDaisuushi(shape))                          result.add(DAISUUSHI);
        if (checkRyuuiisou(shape))                          result.add(RYUUIISOU);
        if (checkChinroutou(shape))                         result.add(CHINROUTOU);
        if (checkSuukantsu(shape))                          result.add(SUUKANTSU);
        if (checkTenhou(ctx))                               result.add(TENHOU);
        if (checkChihou(ctx))                               result.add(CHIHOU);
        if (rules.renhouAllowed() && checkRenhou(shape, ctx)) result.add(RENHOU);
        return result;
    }

    public static boolean checkKokushimusou(HandShape shape) {
        return shape instanceof Kokushimusou;
    }

    public static boolean checkSuuankou(HandShape shape, WinContext ctx) {
        if (!(shape instanceof Standard s)) return false;
        boolean meldsOk = s.melds().stream().allMatch(m -> m instanceof TheMahjongMeld.Ankan);
        boolean groupsOk = s.closedGroups().stream().allMatch(g -> g instanceof ConcealedGroup.Triplet);
        if (!meldsOk || !groupsOk) return false;
        if (!ctx.tsumo()) return ctx.winningTile().matchesSuitRank(s.pair());
        return true;
    }

    public static boolean checkChuurenpoutou(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (!s.closed()) return false;

        List<TheMahjongTile> tiles = allTiles(s);

        TheMahjongTile.Suit suit = tiles.get(0).suit();
        if (suit == TheMahjongTile.Suit.WIND || suit == TheMahjongTile.Suit.DRAGON) return false;

        int[] counts = new int[9];
        for (TheMahjongTile t : tiles) {
            if (t.suit() != suit) return false;
            counts[t.rank() - 1]++;
        }

        int[] pattern = {3, 1, 1, 1, 1, 1, 1, 1, 3};
        int extras = 0;
        for (int i = 0; i < 9; i++) {
            int diff = counts[i] - pattern[i];
            if (diff < 0 || diff > 1) return false;
            if (diff == 1) extras++;
        }
        return extras == 1;
    }

    public static boolean checkDaisangen(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        long fromMelds = s.melds().stream()
                .filter(m -> !(m instanceof TheMahjongMeld.Chi) && isDragon(m.tiles().get(0)))
                .count();
        long fromClosed = s.closedGroups().stream()
                .filter(g -> g instanceof ConcealedGroup.Triplet && isDragon(g.tiles().get(0)))
                .count();
        return fromMelds + fromClosed == 3;
    }

    public static boolean checkTsuisou(HandShape shape) {
        if (shape instanceof Standard s) {
            if (s.melds().stream().anyMatch(m -> m instanceof TheMahjongMeld.Chi)) return false;
            if (s.closedGroups().stream().anyMatch(g -> g instanceof ConcealedGroup.Sequence)) return false;
            return allTiles(s).stream().allMatch(TheMahjongTile::honor);
        }
        if (shape instanceof Chitoitsu c) {
            return c.pairs().stream().allMatch(TheMahjongTile::honor);
        }
        return false;
    }

    public static boolean checkShousuushi(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (!isWind(s.pair())) return false;
        long fromMelds = s.melds().stream()
                .filter(m -> !(m instanceof TheMahjongMeld.Chi) && isWind(m.tiles().get(0)))
                .count();
        long fromClosed = s.closedGroups().stream()
                .filter(g -> g instanceof ConcealedGroup.Triplet && isWind(g.tiles().get(0)))
                .count();
        return fromMelds + fromClosed == 3;
    }

    public static boolean checkDaisuushi(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        long fromMelds = s.melds().stream()
                .filter(m -> !(m instanceof TheMahjongMeld.Chi) && isWind(m.tiles().get(0)))
                .count();
        long fromClosed = s.closedGroups().stream()
                .filter(g -> g instanceof ConcealedGroup.Triplet && isWind(g.tiles().get(0)))
                .count();
        return fromMelds + fromClosed == 4;
    }

    public static boolean checkRyuuiisou(HandShape shape) {
        if (shape instanceof Kokushimusou) return false;
        return allTiles(shape).stream().allMatch(Yakuman::isGreen);
    }

    public static boolean checkChinroutou(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (s.melds().stream().anyMatch(m -> m instanceof TheMahjongMeld.Chi)) return false;
        if (s.closedGroups().stream().anyMatch(g -> g instanceof ConcealedGroup.Sequence)) return false;
        return allTiles(s).stream().allMatch(TheMahjongTile::terminal);
    }

    public static boolean checkSuukantsu(HandShape shape) {
        if (!(shape instanceof Standard s)) return false;
        if (!s.closedGroups().isEmpty()) return false;
        return s.melds().stream().allMatch(m ->
                m instanceof TheMahjongMeld.Daiminkan ||
                m instanceof TheMahjongMeld.Kakan ||
                m instanceof TheMahjongMeld.Ankan);
    }

    public static boolean checkTenhou(WinContext ctx) {
        return ctx.dealer() && ctx.tsumo() && ctx.uninterruptedFirstRound();
    }

    public static boolean checkChihou(WinContext ctx) {
        return !ctx.dealer() && ctx.tsumo() && ctx.uninterruptedFirstRound();
    }

    public static boolean checkRenhou(HandShape shape, WinContext ctx) {
        if (ctx.dealer() || ctx.tsumo() || !ctx.uninterruptedFirstRound()) return false;
        if (shape instanceof Standard s) return s.closed();
        return shape instanceof Chitoitsu || shape instanceof Kokushimusou;
    }

    private static boolean isWind(TheMahjongTile t) {
        return t.suit() == TheMahjongTile.Suit.WIND;
    }

    private static boolean isDragon(TheMahjongTile t) {
        return t.suit() == TheMahjongTile.Suit.DRAGON;
    }

    private static boolean isGreen(TheMahjongTile t) {
        if (t.suit() == TheMahjongTile.Suit.DRAGON)
            return t.rank() == TheMahjongTile.Dragon.HATSU.tileRank();
        if (t.suit() == TheMahjongTile.Suit.SOUZU) {
            int r = t.rank();
            return r == 2 || r == 3 || r == 4 || r == 6 || r == 8;
        }
        return false;
    }

    private static List<TheMahjongTile> allTiles(Standard s) {
        return Stream.concat(
                Stream.concat(
                        s.melds().stream().flatMap(m -> m.tiles().stream()),
                        s.closedGroups().stream().flatMap(g -> g.tiles().stream())),
                Stream.of(s.pair(), s.pair())
        ).toList();
    }

    private static List<TheMahjongTile> allTiles(HandShape shape) {
        if (shape instanceof Standard s) return allTiles(s);
        if (shape instanceof Chitoitsu c) return c.pairs();
        if (shape instanceof Kokushimusou k) return List.of(k.pairTile());
        throw new IllegalStateException("unhandled HandShape: " + shape);
    }
}
