package com.themahjong.variants;

import com.themahjong.*;
import java.util.*;

/** A simultaneous riichi exchange of any three ordinary tiles; the dead wall and scoring state stay intact. */
public final class ExchangeThreeState {
    public record Snapshot(int playerCount, int direction, List<List<TheMahjongTile>> selections,
            Set<Integer> confirmed, boolean complete) {}
    private final int playerCount, direction;
    private final List<List<TheMahjongTile>> selections = new ArrayList<>();
    private final Set<Integer> confirmed = new LinkedHashSet<>();
    private boolean complete;
    private long revision;

    public ExchangeThreeState(int playerCount, int direction) {
        if (playerCount < 3 || playerCount > 4 || direction < 1 || direction >= playerCount)
            throw new IllegalArgumentException("invalid exchange players/direction");
        this.playerCount = playerCount; this.direction = direction;
        for (int seat = 0; seat < playerCount; seat++) selections.add(new ArrayList<>());
    }
    public ExchangeThreeState(Snapshot snapshot) {
        this(snapshot.playerCount(), snapshot.direction());
        if (snapshot.selections().size() != playerCount) throw new IllegalArgumentException("exchange seat count");
        for (int seat = 0; seat < playerCount; seat++) {
            var picked = snapshot.selections().get(seat);
            if (picked.size() > 3 || picked.stream().anyMatch(Jokers::isJoker))
                throw new IllegalArgumentException("invalid saved exchange selection");
            selections.get(seat).addAll(picked);
        }
        if (snapshot.confirmed().stream().anyMatch(s -> s < 0 || s >= playerCount || selections.get(s).size() != 3))
            throw new IllegalArgumentException("invalid saved exchange confirmations");
        confirmed.addAll(snapshot.confirmed()); complete = snapshot.complete();
    }
    public Snapshot snapshot() {
        return new Snapshot(playerCount, direction, selections.stream().map(List::copyOf).toList(),
                Set.copyOf(confirmed), complete);
    }
    public boolean complete() { return complete; }
    public long revision() { return revision; }
    public int direction() { return direction; }
    public boolean pending(int seat) { return !complete && seat >= 0 && seat < playerCount && !confirmed.contains(seat); }
    public List<TheMahjongTile> selection(int seat) {
        return seat >= 0 && seat < playerCount ? List.copyOf(selections.get(seat)) : List.of();
    }
    public boolean toggle(TheMahjongRound round, int seat, TheMahjongTile tile) {
        if (!pending(seat) || tile == null || com.themahjong.variants.Jokers.isJoker(tile)) return false;
        var picked = selections.get(seat);
        long owned = round.players().get(seat).currentHand().stream().filter(tile::equals).count();
        long count = picked.stream().filter(tile::equals).count();
        if (count > 0 && (count == owned || picked.size() == 3)) { picked.remove(tile); revision++; return true; }
        if (owned == 0 || picked.size() == 3) return false;
        picked.add(tile); revision++; return true;
    }
    public boolean clear(int seat) {
        if (!pending(seat) || selections.get(seat).isEmpty()) return false;
        selections.get(seat).clear(); revision++; return true;
    }
    public boolean confirm(TheMahjongRound round, int seat) {
        if (!pending(seat) || selections.get(seat).size() != 3) return false;
        var hand = new ArrayList<>(round.players().get(seat).currentHand());
        for (var tile : selections.get(seat)) if (!hand.remove(tile)) return false;
        confirmed.add(seat); revision++; return true;
    }
    public void automatic(TheMahjongRound round, int seat) {
        if (!pending(seat)) return;
        var hand = round.players().get(seat).currentHand();
        var suit = Arrays.stream(TheMahjongTile.Suit.values())
                .filter(s -> hand.stream().filter(t -> t.suit() == s && !Jokers.isJoker(t)).count() >= 3)
                .min(Comparator.comparingLong(s -> hand.stream().filter(t -> t.suit() == s && !Jokers.isJoker(t)).count())).orElse(null);
        selections.get(seat).clear();
        selections.get(seat).addAll(hand.stream().filter(t -> !Jokers.isJoker(t) && (suit == null || t.suit() == suit))
                .sorted(TheMahjongTile.DISPLAY_ORDER).limit(3).toList());
        confirm(round, seat);
    }
    public boolean ready() { return !complete && confirmed.size() == playerCount; }
    public TheMahjongRound exchange(TheMahjongRound round) {
        if (!ready()) return round;
        var players = new ArrayList<TheMahjongPlayer>();
        for (int seat = 0; seat < playerCount; seat++) {
            var player = round.players().get(seat);
            var hand = new ArrayList<>(player.currentHand());
            for (var tile : selections.get(seat))
                if (!hand.remove(tile)) throw new IllegalStateException("exchange hand changed");
            hand.addAll(selections.get(Math.floorMod(seat - direction, playerCount)));
            players.add(new TheMahjongPlayer(player.points(), player.riichiState(), player.ippatsuEligible(),
                    hand, player.melds(), player.discards(), player.temporaryFuritenTiles(),
                    player.riichiPermanentFuriten(), player.kitaCount()));
        }
        complete = true; revision++;
        return copy(round, players);
    }
    public static TheMahjongRound copy(TheMahjongRound round, List<TheMahjongPlayer> players) {
        return new TheMahjongRound(round.roundWind(), round.handNumber(), round.honba(), round.riichiSticks(),
                round.dealerSeat(), round.state(), round.currentTurnSeat(), round.claimSourceSeat().orElse(-1),
                round.activeTile(), round.liveWall(), round.rinshanTiles(), round.doraIndicators(),
                round.uraDoraIndicators(), round.revealedDoraCount(), round.pendingKanDoraReveals(), players, round.pendingDeltas());
    }
}
