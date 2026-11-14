package com.riichimahjong.fabric;

import com.themahjong.TheMahjongPlayer;
import java.util.ArrayList;
import java.util.List;

/** Transfer a called declaration's sideways marker to the next visible discard. */
final class RiverDisplay {
    private RiverDisplay() {}

    static List<PublicTable.Tile> capture(List<TheMahjongPlayer> players, int seat) {
        var discards = players.get(seat).discards();
        var claimed = PublicTable.claimed(players, seat);
        var river = new ArrayList<PublicTable.Tile>();
        boolean sideways = false;
        for (int index = 0; index < discards.size(); index++) {
            var discard = discards.get(index);
            sideways |= discard.riichiDeclared();
            if (claimed.contains(index)) continue;
            river.add(new PublicTable.Tile(PublicTable.code(discard.tile()), sideways));
            sideways = false;
        }
        return List.copyOf(river);
    }
}
