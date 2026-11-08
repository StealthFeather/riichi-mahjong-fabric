package com.themahjong.yaku;

import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongTile;

import java.util.ArrayList;
import java.util.List;

public final class TenpaiChecker {
    private TenpaiChecker() {}

    public static List<TheMahjongTile> winningTiles(TheMahjongPlayer player) {
        List<TheMahjongTile> hand = player.currentHand();
        List<TheMahjongTile> result = new ArrayList<>();

        for (TheMahjongTile.Suit suit : TheMahjongTile.Suit.values()) {
            for (int rank = 1; rank <= suit.maxRank(); rank++) {
                TheMahjongTile candidate = new TheMahjongTile(suit, rank, false);
                List<TheMahjongTile> withCandidate = new ArrayList<>(hand);
                withCandidate.add(candidate);
                if (!HandShape.decompose(withCandidate, player.melds()).isEmpty()) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    public static boolean inTenpai(TheMahjongPlayer player) {
        return !winningTiles(player).isEmpty();
    }
}
