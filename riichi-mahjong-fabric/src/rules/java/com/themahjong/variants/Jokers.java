package com.themahjong.variants;

import com.themahjong.TheMahjongTile;

/** A dedicated identity leaves all four ordinary red dragons available in riichi variants. */
public final class Jokers {
    public static final TheMahjongTile TILE = new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 3, true);
    private Jokers() {}

    public static boolean isJoker(TheMahjongTile tile) {
        return tile != null && tile.suit() == TheMahjongTile.Suit.DRAGON
                && tile.rank() == 3 && tile.redDora();
    }
}
