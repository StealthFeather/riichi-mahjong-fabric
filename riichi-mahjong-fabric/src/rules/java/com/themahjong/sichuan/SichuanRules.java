package com.themahjong.sichuan;

import com.themahjong.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Four-player Sichuan rules: numbered tiles, an optional red-dragon wildcard and no dead wall. */
public final class SichuanRules {
    private SichuanRules() {}

    public static TheMahjongMatch newMatch(int roundCount, boolean exchangeThree, boolean jokersEnabled) {
        return newMatch(roundCount, exchangeThree, jokersEnabled, false);
    }

    public static TheMahjongMatch newMatch(int roundCount, boolean exchangeThree, boolean jokersEnabled, boolean bloodBattle) {
        var tiles = new ArrayList<TheMahjongTile>();
        var copies = new LinkedHashMap<TheMahjongTile,Integer>();
        for (var suit : List.of(TheMahjongTile.Suit.MANZU, TheMahjongTile.Suit.PINZU, TheMahjongTile.Suit.SOUZU)) {
            for (int rank = 1; rank <= 9; rank++) {
                var tile = new TheMahjongTile(suit, rank, false);
                tiles.add(tile); copies.put(tile, 4);
            }
        }
        if (jokersEnabled) {
            var tile = new TheMahjongTile(TheMahjongTile.Suit.DRAGON, TheMahjongTile.Dragon.CHUN.tileRank(), false);
            tiles.add(tile); copies.put(tile, 4);
        }
        var rules = new TheMahjongRuleSet(false, false, false, false, false, false, true,
                false, false, false, false, false, false, false, false, true,
                false, false, false, false, true, exchangeThree, jokersEnabled, bloodBattle);
        return new TheMahjongMatch(4, 25000, 25000, roundCount, TheMahjongMatch.State.NOT_STARTED,
                new TheMahjongTileSet(tiles, copies), rules, List.of(), null);
    }

    /** The shared deal initially reserves fourteen riichi tiles; Sichuan draws all of them. */
    public static TheMahjongRound prepareRound(TheMahjongRound round) {
        if (round.doraIndicators().isEmpty()) return round;
        var wall = new ArrayList<>(round.liveWall());
        wall.addAll(round.rinshanTiles());
        wall.addAll(round.doraIndicators());
        wall.addAll(round.uraDoraIndicators());
        var allTiles = new ArrayList<TheMahjongTile>();
        for (var player : round.players()) allTiles.addAll(player.currentHand());
        allTiles.addAll(wall);
        var joker = new TheMahjongTile(TheMahjongTile.Suit.DRAGON, TheMahjongTile.Dragon.CHUN.tileRank(), false);
        if (allTiles.stream().filter(joker::equals).count() == 4) {
            // A wildcard is dealt to every leftmost hand slot, never shuffled into the drawable wall.
            var numbered = allTiles.stream().filter(tile -> !tile.equals(joker)).toList();
            var players = new ArrayList<>(round.players());
            for (int seat = 0; seat < 4; seat++) {
                var old = players.get(seat);
                var hand = new ArrayList<TheMahjongTile>(); hand.add(joker);
                hand.addAll(numbered.subList(seat * 12, (seat + 1) * 12));
                players.set(seat, player(old, old.points(), hand, old.melds(), old.discards()));
            }
            return copy(round, TheMahjongRound.State.SETUP, round.currentTurnSeat(), -1,
                    TheMahjongRound.ActiveTile.none(), numbered.subList(48, numbered.size()), players);
        }
        return copy(round, TheMahjongRound.State.SETUP, round.currentTurnSeat(), -1,
                TheMahjongRound.ActiveTile.none(), wall, round.players());
    }

    public static TheMahjongRound copy(TheMahjongRound round, TheMahjongRound.State state,
            int turn, int source, TheMahjongRound.ActiveTile active, List<TheMahjongTile> wall,
            List<TheMahjongPlayer> players) {
        return new TheMahjongRound(round.roundWind(), round.handNumber(), 0, 0, round.dealerSeat(),
                state, turn, source, active, wall, List.of(), List.of(), List.of(), 0, players, List.of());
    }

    public static TheMahjongPlayer player(TheMahjongPlayer old, int points, List<TheMahjongTile> hand,
            List<TheMahjongMeld> melds, List<TheMahjongDiscard> discards) {
        return new TheMahjongPlayer(points, TheMahjongPlayer.RiichiState.NONE, false,
                hand, melds, discards, List.of(), false, 0);
    }
}
