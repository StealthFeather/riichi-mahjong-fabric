package com.riichimahjong.ai;

import com.themahjong.TheMahjongTile;
import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.PlayerAction;
import com.themahjong.variants.Jokers;

/** The external riichi engine has no wildcard vocabulary. Choose only the server's legal actions. */
final class VariantBotPolicy {
    private VariantBotPolicy() {}
    static PlayerAction choose(DecisionRequest request) {
        for (var action : request.legalActions())
            if (action instanceof PlayerAction.DiscardWithRiichi d && !Jokers.isJoker(d.tile())) return action;
        var hand = request.round().players().get(request.seat()).currentHand();
        PlayerAction best = null;
        double bestValue = Double.POSITIVE_INFINITY;
        for (var action : request.legalActions()) if (action instanceof PlayerAction.Discard discard) {
            var tile = discard.tile();
            double value = Jokers.isJoker(tile) ? 10000 : tile.redDora() ? 2 : 0;
            for (var other : hand) {
                if (other.equals(tile)) value += 4;
                else if (tile.suit().isNumber() && other.suit() == tile.suit()) {
                    int gap = Math.abs(other.rank() - tile.rank());
                    if (gap == 1) value += 2;
                    else if (gap == 2) value += 1;
                }
            }
            if (tile.suit().isNumber()) value += (5 - Math.abs(tile.rank() - 5)) * .1;
            if (value < bestValue) { bestValue = value; best = action; }
        }
        if (best != null) return best;
        if (request.legalActions().contains(PlayerAction.Pass.INSTANCE)) return PlayerAction.Pass.INSTANCE;
        return request.legalActions().getFirst();
    }
}
