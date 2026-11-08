package com.themahjong.yaku;

import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.HandShape.ConcealedGroup;
import com.themahjong.yaku.HandShape.Standard;

public final class FuCalculator {
    private FuCalculator() {}

    public static int calculate(HandShape shape, WinContext ctx, TheMahjongRuleSet rules) {
        if (shape instanceof HandShape.Chitoitsu) return 25;
        if (shape instanceof HandShape.Kokushimusou) return 30;
        Standard s = (Standard) shape;

        if (NonYakuman.checkPinfu(s, ctx)) return ctx.tsumo() ? 20 : 30;

        boolean ronLike = ctx.winType() != WinContext.WinType.TSUMO;
        int fu = (ronLike && s.closed()) ? 30 : 20;

        if (ctx.tsumo()) fu += 2;

        boolean shanponRon = ronLike
                && (s.waitShape().isPresent()
                        ? s.waitShape().get() == HandShape.WaitShape.SHANPON
                        : winTileCanOnlyCompleteTriplet(s, ctx.winningTile()));

        for (TheMahjongMeld meld : s.melds()) fu += meldFu(meld);
        for (ConcealedGroup group : s.closedGroups()) fu += closedGroupFu(group, ctx, shanponRon);

        fu += pairFu(s.pair(), ctx, rules.doubleWindPairFu4());
        fu += s.waitShape().map(HandShape.WaitShape::fu).orElseGet(() -> waitFu(s, ctx.winningTile()));

        return Math.max(30, roundUpTo10(fu));
    }

    private static int meldFu(TheMahjongMeld meld) {
        if (meld instanceof TheMahjongMeld.Chi)       return 0;
        if (meld instanceof TheMahjongMeld.Pon p)     return tripletFu(p.tiles().get(0), false);
        if (meld instanceof TheMahjongMeld.Daiminkan d) return kanFu(d.tiles().get(0), false);
        if (meld instanceof TheMahjongMeld.Kakan k)   return kanFu(k.tiles().get(0), false);
        if (meld instanceof TheMahjongMeld.Ankan a)   return kanFu(a.tiles().get(0), true);
        return 0;
    }

    private static int closedGroupFu(ConcealedGroup group, WinContext ctx, boolean shanponRon) {
        if (group instanceof ConcealedGroup.Sequence) return 0;
        if (group instanceof ConcealedGroup.Triplet t) {
            boolean closed = !(shanponRon && ctx.winningTile().matchesSuitRank(t.tiles().get(0)));
            return tripletFu(t.tiles().get(0), closed);
        }
        return 0;
    }

    static boolean winTileCanOnlyCompleteTriplet(Standard s, TheMahjongTile winTile) {
        if (winTile.matchesSuitRank(s.pair())) return false;
        for (ConcealedGroup g : s.closedGroups()) {
            if (g instanceof ConcealedGroup.Sequence seq) {
                for (TheMahjongTile t : seq.tiles()) {
                    if (winTile.matchesSuitRank(t)) return false;
                }
            }
        }
        return true;
    }

    private static int tripletFu(TheMahjongTile tile, boolean closed) {
        boolean yaochu = tile.terminal() || tile.honor();
        return yaochu ? (closed ? 8 : 4) : (closed ? 4 : 2);
    }

    private static int kanFu(TheMahjongTile tile, boolean closed) {
        boolean yaochu = tile.terminal() || tile.honor();
        return yaochu ? (closed ? 32 : 16) : (closed ? 16 : 8);
    }

    private static int pairFu(TheMahjongTile pair, WinContext ctx, boolean doubleWindPairFu4) {
        if (pair.suit() == TheMahjongTile.Suit.DRAGON) return 2;
        if (pair.suit() == TheMahjongTile.Suit.WIND) {
            int rank = pair.rank();
            boolean isSeat  = rank == ctx.seatWind().tileRank();
            boolean isRound = rank == ctx.roundWind().tileRank();
            if (isSeat && isRound) return doubleWindPairFu4 ? 4 : 2;
            if (isSeat || isRound) return 2;
        }
        return 0;
    }

    static int waitFu(Standard s, TheMahjongTile winTile) {
        int best = -1;
        for (ConcealedGroup group : s.closedGroups()) {
            if (group instanceof ConcealedGroup.Triplet t) {
                if (winTile.matchesSuitRank(t.tiles().get(0))) best = Math.max(best, 0);
            } else if (group instanceof ConcealedGroup.Sequence seq) {
                TheMahjongTile low  = seq.tiles().get(0);
                TheMahjongTile mid  = seq.tiles().get(1);
                TheMahjongTile high = seq.tiles().get(2);
                if (winTile.matchesSuitRank(mid)) best = Math.max(best, 2);
                else if (winTile.matchesSuitRank(low)) {
                    best = Math.max(best, high.rank() == low.suit().maxRank() ? 2 : 0);
                } else if (winTile.matchesSuitRank(high)) {
                    best = Math.max(best, low.rank() == 1 ? 2 : 0);
                }
            }
        }
        if (winTile.matchesSuitRank(s.pair())) best = Math.max(best, 2);
        return Math.max(best, 0);
    }

    static int roundUpTo10(int fu) {
        return ((fu + 9) / 10) * 10;
    }
}
