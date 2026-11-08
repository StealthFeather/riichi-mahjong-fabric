package com.themahjong.replay;

import com.themahjong.TheMahjongTile;
import java.util.List;

public record TenhouRound(
        int roundNumber,
        int honba,
        int riichiSticks,
        int dealer,
        TheMahjongTile doraIndicator,
        int[] startingScores,
        List<List<TheMahjongTile>> initialHands,
        List<TenhouAction> actions
) {
    public int playerCount() {
        return (int) initialHands.stream().filter(h -> !h.isEmpty()).count();
    }
}
