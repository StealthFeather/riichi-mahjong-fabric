package com.themahjong.variants;

import com.themahjong.*;
import com.themahjong.compat.DrawSettlement;
import com.themahjong.yaku.WinCalculator;
import com.themahjong.yaku.WinResult;
import java.util.*;

/** Accepted riichi wins remain separate from the immutable physical round while survivors continue. */
public final class BloodBattleState {
    public record Win(int seat, TheMahjongTile tile, boolean selfDrawn, WinResult result, int sourceSeat) {}
    public record Snapshot(List<Win> wins) {
        public Snapshot { wins = List.copyOf(wins); }
    }
    private final List<Win> wins = new ArrayList<>();
    private long revision;
    public BloodBattleState() {}
    public BloodBattleState(Snapshot saved) {
        var seats = new HashSet<Integer>();
        for (var win : saved.wins()) {
            if (win.seat() < 0 || win.tile() == null || win.result() == null || !seats.add(win.seat()))
                throw new IllegalArgumentException("invalid accepted blood-battle winner");
            wins.add(win);
        }
    }
    public List<Win> wins() { return List.copyOf(wins); }
    public Set<Integer> winners() {
        var result = new LinkedHashSet<Integer>(); wins.forEach(win -> result.add(win.seat()));
        return Set.copyOf(result);
    }
    public boolean won(int seat) { return wins.stream().anyMatch(win -> win.seat() == seat); }
    public TheMahjongTile winningTile(int seat) { return wins.stream().filter(w -> w.seat() == seat).map(Win::tile).findFirst().orElse(null); }
    public boolean selfDrawn(int seat) { return wins.stream().anyMatch(w -> w.seat() == seat && w.selfDrawn()); }
    public long revision() { return revision; }
    public Snapshot snapshot() { return new Snapshot(wins); }

    /** A retired responsibility seat cannot erase a later survivor's otherwise payable win. */
    public WinResult adjust(WinResult result, int winner, boolean selfDrawn, TheMahjongRound round,
            TheMahjongRuleSet rules) {
        result = WinCalculator.withoutRetiredResponsibilities(result, selfDrawn, winner, round, rules, winners());
        return adjust(result, winner);
    }

    /** Already exited players never pay for later wins; their canceled payments reduce the winner's gain. */
    public WinResult adjust(WinResult result, int winner) {
        var deltas = new ArrayList<>(result.pointDeltas());
        for (var win : wins) {
            int delta = deltas.get(win.seat());
            if (delta < 0) { deltas.set(win.seat(), 0); deltas.set(winner, deltas.get(winner) + delta); }
        }
        return withDeltas(result, deltas);
    }

    /** A continuing declaration is charged after ron responses were captured, and paid once. */
    public WinResult withAdditionalDeposits(WinResult result, int winner, int additionalSticks) {
        if (additionalSticks == 0) return result;
        if (additionalSticks < 0) throw new IllegalArgumentException("negative additional deposits");
        var deltas = new ArrayList<>(result.pointDeltas());
        deltas.set(winner, deltas.get(winner) + additionalSticks * 1000);
        return withDeltas(result, deltas);
    }

    public WinResult withoutDeposits(WinResult result, int winner, int originalSticks) {
        if (originalSticks == 0) return result;
        var deltas = new ArrayList<>(result.pointDeltas());
        deltas.set(winner, deltas.get(winner) - originalSticks * 1000);
        return withDeltas(result, deltas);
    }
    private static WinResult withDeltas(WinResult result, List<Integer> deltas) {
        return new WinResult(result.yaku(), result.yakuman(), result.han(), result.fu(), result.doraCount(), List.copyOf(deltas));
    }

    /** Validate through the original riichi acceptance path, then retain the live turn and physical win tile. */
    public TheMahjongRound accept(TheMahjongRound round, int winner, int source, TheMahjongTile tile,
            boolean selfDrawn, WinResult result) {
        if (won(winner)) throw new IllegalArgumentException("winner already exited this hand");
        var settled = round.declareWin(winner, source, result);
        var players = new ArrayList<>(settled.players());
        if (!selfDrawn) {
            var player = players.get(winner);
            var hand = new ArrayList<>(player.currentHand()); hand.add(tile);
            players.set(winner, player(player, player.points(), hand, player.melds(), player.kitaCount()));
        }
        wins.add(new Win(winner, tile, selfDrawn, result, source)); revision++;
        return copy(round, round.state(), round.currentTurnSeat(), round.claimSourceSeat().orElse(-1),
                round.activeTile(), 0, players);
    }

    /** A declaration whose discard was won on never earned riichi or paid a deposit. */
    public TheMahjongRound cancelUncommittedRiichi(TheMahjongRound round, int seat) {
        var players = new ArrayList<>(round.players());
        var player = players.get(seat);
        var river = new ArrayList<>(player.discards());
        if (!river.isEmpty()) {
            var last = river.getLast();
            if (last.riichiDeclared()) river.set(river.size()-1, new TheMahjongDiscard(last.tile(), false));
        }
        players.set(seat, new TheMahjongPlayer(player.points(), TheMahjongPlayer.RiichiState.NONE, false,
                player.currentHand(), player.melds(), river, player.temporaryFuritenTiles(),
                player.riichiPermanentFuriten(), player.kitaCount()));
        return copy(round, round.state(), round.currentTurnSeat(), round.claimSourceSeat().orElse(-1),
                round.activeTile(), round.riichiSticks(), players);
    }

    public int nextActive(int after, int count) {
        for (int offset = 1; offset <= count; offset++) {
            int seat = Math.floorMod(after + offset, count);
            if (!won(seat)) return seat;
        }
        throw new IllegalStateException("blood battle has no survivor");
    }

    /** Undo only the robbed physical declaration; the accepted winning tile moves to the winner's display. */
    public TheMahjongRound cancelRobbedDeclaration(TheMahjongRound round, TheMahjongRuleSet rules) {
        int source = round.claimSourceSeat().orElseThrow();
        var players = new ArrayList<>(round.players()); var old = players.get(source);
        int revealed = round.revealedDoraCount(), pending = round.pendingKanDoraReveals();
        if (round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKakan held) {
            var melds = new ArrayList<>(old.melds());
            for (int i = 0; i < melds.size(); i++) if (melds.get(i) instanceof TheMahjongMeld.Kakan kan
                    && kan.addedTile().equals(held.tile())) { melds.set(i, kan.upgradedFrom()); break; }
            players.set(source, player(old, old.points(), old.currentHand(), melds, old.kitaCount()));
            if (rules.competitiveProfile() == com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE || rules.openKanDoraDelayedReveal()) pending = Math.max(0, pending - 1);
            else if (rules.kanDoraAllowed()) revealed = Math.max(1, revealed - 1);
        } else if (round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKita) {
            players.set(source, player(old, old.points(), old.currentHand(), old.melds(), Math.max(0, old.kitaCount() - 1)));
        }
        return new TheMahjongRound(round.roundWind(), round.handNumber(), round.honba(), round.riichiSticks(),
                round.dealerSeat(), round.state(), round.currentTurnSeat(), source, round.activeTile(),
                round.liveWall(), round.rinshanTiles(), round.doraIndicators(), round.uraDoraIndicators(),
                revealed, pending, players, List.of());
    }

    /** Noten and nagashi payments at exhaustion involve only players who have not already won. */
    public TheMahjongRound exhaustive(TheMahjongRound round, TheMahjongRuleSet rules) {
        var deltas = new int[round.players().size()];
        var active = new ArrayList<Integer>();
        for (int seat = 0; seat < deltas.length; seat++) if (!won(seat)) active.add(seat);
        var nagashi = active.stream().filter(seat -> rules.nagashiManganAllowed() && round.isNagashiManganEligible(seat)).toList();
        if (!nagashi.isEmpty()) {
            for (int winner : nagashi) for (int payer : active) if (payer != winner) {
                int amount = (winner == round.dealerSeat() || payer == round.dealerSeat() ? 4000 : 2000) + round.honba() * 100;
                deltas[payer] -= amount; deltas[winner] += amount;
            }
        } else {
            int ready = (int)active.stream().filter(seat -> DrawSettlement.inTenpai(round.players().get(seat))).count();
            int noten = active.size() - ready;
            if (ready > 0 && noten > 0) for (int seat : active)
                deltas[seat] = DrawSettlement.inTenpai(round.players().get(seat)) ? 3000 / ready : -3000 / noten;
        }
        var players = new ArrayList<>(round.players());
        for (int seat : active) {
            var player = players.get(seat);
            players.set(seat, player(player, player.points() + deltas[seat], player.currentHand(), player.melds(), player.kitaCount()));
        }
        revision++;
        return copy(round, TheMahjongRound.State.ENDED, round.currentTurnSeat(), -1,
                TheMahjongRound.ActiveTile.none(), round.riichiSticks(), players);
    }

    public static TheMahjongRound copy(TheMahjongRound round, TheMahjongRound.State state, int turn,
            int source, TheMahjongRound.ActiveTile active, int sticks, List<TheMahjongPlayer> players) {
        return new TheMahjongRound(round.roundWind(), round.handNumber(), round.honba(), sticks, round.dealerSeat(),
                state, turn, source, active, round.liveWall(), round.rinshanTiles(), round.doraIndicators(),
                round.uraDoraIndicators(), round.revealedDoraCount(), round.pendingKanDoraReveals(), players, List.of());
    }
    private static TheMahjongPlayer player(TheMahjongPlayer player, int points, List<TheMahjongTile> hand,
            List<TheMahjongMeld> melds, int kita) {
        return new TheMahjongPlayer(points, player.riichiState(), player.ippatsuEligible(), hand, melds,
                player.discards(), player.temporaryFuritenTiles(), player.riichiPermanentFuriten(), kita);
    }
}
