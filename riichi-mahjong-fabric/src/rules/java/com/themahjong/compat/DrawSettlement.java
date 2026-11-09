package com.themahjong.compat;

import com.themahjong.TheMahjongPlayer;
import com.themahjong.yaku.TenpaiChecker;
import java.util.ArrayList;
import java.util.List;

/** Shared exhaustive-draw readiness and zero-sum noten settlement. */
public final class DrawSettlement {
    private DrawSettlement() {}

    public static boolean inTenpai(TheMahjongPlayer player) {
        var ownTiles = new ArrayList<>(player.currentHand());
        player.melds().forEach(meld -> ownTiles.addAll(meld.tiles()));
        // A shape completed only by a fifth self-owned tile is not a valid wait.
        // Red fives share a kind with normal fives; external tiles do not count.
        return TenpaiChecker.winningTiles(player).stream().anyMatch(candidate ->
                ownTiles.stream().filter(tile -> tile.matchesSuitRank(candidate)).count() < 4);
    }

    public static List<TheMahjongPlayer> applyNotenPayments(List<TheMahjongPlayer> players) {
        var ready = players.stream().map(DrawSettlement::inTenpai).toList();
        int tenpai = (int) ready.stream().filter(Boolean::booleanValue).count();
        int noten = players.size() - tenpai;
        if (tenpai == 0 || noten == 0) return players;

        // Both supported Mahjong Soul/Tenhou 3p and 4p rules split 3000 points.
        // Honba and riichi deposits are carried separately and do not change it.
        var settled = new ArrayList<TheMahjongPlayer>(players.size());
        for (int seat = 0; seat < players.size(); seat++) {
            var player = players.get(seat);
            int delta = ready.get(seat) ? 3000 / tenpai : -3000 / noten;
            settled.add(new TheMahjongPlayer(player.points() + delta,
                    player.riichiState(), player.ippatsuEligible(),
                    player.currentHand(), player.melds(), player.discards(),
                    player.temporaryFuritenTiles(), player.riichiPermanentFuriten(), player.kitaCount()));
        }
        return List.copyOf(settled);
    }
}
