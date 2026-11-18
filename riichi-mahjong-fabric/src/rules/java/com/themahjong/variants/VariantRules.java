package com.themahjong.variants;

import com.themahjong.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Optional rules around the existing riichi presets, without replacing their scoring rules. */
public final class VariantRules {
    private VariantRules() {}

    public static TheMahjongMatch withOptions(TheMahjongMatch match, boolean exchange, boolean jokers) {
        return withOptions(match, exchange, jokers, match.ruleSet().bloodBattle());
    }
    public static TheMahjongMatch withOptions(TheMahjongMatch match, boolean exchange, boolean jokers, boolean bloodBattle) {
        var r = match.ruleSet();
        var rules = new TheMahjongRuleSet(r.kiriageMangan(), r.doubleWindPairFu4(), r.renhouAllowed(),
                r.paoOnSuukantsu(), r.abortiveDrawsAllowed(), r.nagashiManganAllowed(), r.doubleRonAllowed(),
                r.bustingEndsGame(), r.riichiRequires1000Points(), r.depositsToFirstAtEnd(),
                r.agariYameAllowed(), r.suddenDeathRound(), r.openKanDoraDelayedReveal(),
                r.kuikaeForbidden(), r.strictRiichiAnkan(), r.chiDisabled(), r.kitaCountsAsDora(),
                r.tsumoLoss(), r.kitaDeclineCausesFuriten(), r.sanchahouAbortive(),
                r.sichuan(), exchange, jokers, bloodBattle, r.competitiveProfile());
        var tiles = new ArrayList<>(match.tileSet().tiles());
        var copies = new LinkedHashMap<>(match.tileSet().copiesPerTile());
        tiles.removeIf(Jokers::isJoker);
        copies.keySet().removeIf(Jokers::isJoker);
        if (jokers && !rules.sichuan()) {
            tiles.add(Jokers.TILE);
            copies.put(Jokers.TILE, match.playerCount());
        }
        return match.withRuleSet(rules).withTileSet(new TheMahjongTileSet(tiles, copies));
    }

    /** Each seat receives one joker and twelve ordinary tiles; every drawable/dead-wall tile is ordinary. */
    public static TheMahjongRound prepareRound(TheMahjongRound round) {
        var all = new ArrayList<TheMahjongTile>();
        round.players().forEach(player -> all.addAll(player.currentHand()));
        all.addAll(round.liveWall()); all.addAll(round.rinshanTiles());
        all.addAll(round.doraIndicators()); all.addAll(round.uraDoraIndicators());
        long jokerCount = all.stream().filter(Jokers::isJoker).count();
        if (jokerCount == 0) return round;
        int count = round.players().size();
        if (jokerCount != count) throw new IllegalArgumentException("one joker per seat required");
        boolean alreadyPrepared = round.players().stream().allMatch(player ->
                player.currentHand().size() == 13 && player.currentHand().stream().filter(Jokers::isJoker).count() == 1)
                && java.util.stream.Stream.of(round.liveWall(), round.rinshanTiles(), round.doraIndicators(), round.uraDoraIndicators())
                    .flatMap(List::stream).noneMatch(Jokers::isJoker);
        if (alreadyPrepared) return round;
        var ordinary = all.stream().filter(tile -> !Jokers.isJoker(tile)).toList();
        var players = new ArrayList<>(round.players());
        int offset = 0;
        for (int seat = 0; seat < count; seat++) {
            var old = players.get(seat);
            var hand = new ArrayList<TheMahjongTile>(); hand.add(Jokers.TILE);
            hand.addAll(ordinary.subList(offset, offset + 12)); offset += 12;
            players.set(seat, new TheMahjongPlayer(old.points(), old.riichiState(), old.ippatsuEligible(),
                    hand, old.melds(), old.discards(), old.temporaryFuritenTiles(), old.riichiPermanentFuriten(), old.kitaCount()));
        }
        int rinshanSize = round.rinshanTiles().size(), doraSize = round.doraIndicators().size(), uraSize = round.uraDoraIndicators().size();
        int liveEnd = ordinary.size() - rinshanSize - doraSize - uraSize;
        var live = ordinary.subList(offset, liveEnd);
        var rinshan = ordinary.subList(liveEnd, liveEnd + rinshanSize);
        var dora = ordinary.subList(liveEnd + rinshanSize, liveEnd + rinshanSize + doraSize);
        var ura = ordinary.subList(liveEnd + rinshanSize + doraSize, ordinary.size());
        return new TheMahjongRound(round.roundWind(), round.handNumber(), round.honba(), round.riichiSticks(),
                round.dealerSeat(), round.state(), round.currentTurnSeat(), round.claimSourceSeat().orElse(-1),
                round.activeTile(), live, rinshan, dora, ura, round.revealedDoraCount(),
                round.pendingKanDoraReveals(), players, round.pendingDeltas());
    }
}
