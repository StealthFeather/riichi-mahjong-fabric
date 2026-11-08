package com.themahjong.yaku;

import com.themahjong.TheMahjongDiscard;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongTile;

import java.util.List;

public final class Furiten {
    private Furiten() {}

    public static boolean isFuriten(TheMahjongPlayer player) {
        if (player.riichiPermanentFuriten()) return true;
        if (player.temporaryFuriten()) return true;
        return inDiscardRiverFuriten(player);
    }

    public static boolean inDiscardRiverFuriten(TheMahjongPlayer player) {
        List<TheMahjongTile> waits = TenpaiChecker.winningTiles(player);
        if (waits.isEmpty()) return false;
        for (TheMahjongDiscard d : player.discards()) {
            for (TheMahjongTile w : waits) {
                if (d.tile().matchesSuitRank(w)) return true;
            }
        }
        return false;
    }

    public static boolean canRon(TheMahjongPlayer player, TheMahjongTile winningTile) {
        if (isFuriten(player)) return false;
        for (TheMahjongTile w : TenpaiChecker.winningTiles(player)) {
            if (w.matchesSuitRank(winningTile)) return true;
        }
        return false;
    }
}
