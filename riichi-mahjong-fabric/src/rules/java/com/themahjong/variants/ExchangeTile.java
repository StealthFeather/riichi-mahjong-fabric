package com.themahjong.variants;

import com.themahjong.TheMahjongTile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A particular copy of a tile in the unchanged opening hand, independent of display sorting. */
public record ExchangeTile(TheMahjongTile tile, int copy) {
    public static ExchangeTile at(List<TheMahjongTile> hand, int index) {
        var tile = hand.get(index);
        return new ExchangeTile(tile, Collections.frequency(hand.subList(0, index), tile));
    }

    public int indexIn(List<TheMahjongTile> hand) {
        int occurrence = 0;
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).equals(tile) && occurrence++ == copy) return i;
        }
        return -1;
    }

    /** Older selections and bot choices identify equal tiles in order from the left. */
    public static List<ExchangeTile> firstCopies(List<TheMahjongTile> tiles) {
        var picks = new ArrayList<ExchangeTile>();
        for (int i = 0; i < tiles.size(); i++) picks.add(at(tiles, i));
        return List.copyOf(picks);
    }

    public static List<TheMahjongTile> tiles(List<ExchangeTile> picks) {
        return picks.stream().map(ExchangeTile::tile).toList();
    }

    /** Preserve the value-based rule API for callers that do not identify a hand slot. */
    public static ExchangeTile byValue(List<TheMahjongTile> hand, List<ExchangeTile> picks, TheMahjongTile tile) {
        int owned = Collections.frequency(hand, tile);
        if (picks.size() < 3) {
            for (int copy = 0; copy < owned; copy++) {
                var pick = new ExchangeTile(tile, copy);
                if (!picks.contains(pick)) return pick;
            }
        }
        return picks.stream().filter(p -> p.tile.equals(tile)).findFirst().orElse(null);
    }

    public static boolean toggle(List<ExchangeTile> picks, List<TheMahjongTile> hand, ExchangeTile pick, boolean sameSuit) {
        if (pick == null || pick.indexIn(hand) < 0) return false;
        if (picks.remove(pick)) return true;
        if (picks.size() == 3 || sameSuit && !picks.isEmpty() && picks.getFirst().tile.suit() != pick.tile.suit()) return false;
        return picks.add(pick);
    }

    public static List<TheMahjongTile> remaining(List<TheMahjongTile> hand, List<ExchangeTile> picks) {
        var remaining = new ArrayList<TheMahjongTile>();
        for (int i = 0; i < hand.size(); i++) {
            if (!picks.contains(at(hand, i))) remaining.add(hand.get(i));
        }
        if (remaining.size() + picks.size() != hand.size()) throw new IllegalStateException("exchange selection changed");
        return remaining;
    }
}
