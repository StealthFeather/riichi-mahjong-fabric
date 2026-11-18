// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.themahjongcompat;

import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.yaku.NonYakuman;
import com.themahjong.yaku.WinResult;
import com.themahjong.yaku.Yakuman;
import com.themahjong.sichuan.SichuanState;
import java.util.HashMap;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class DriverNbt {
    private static final int VERSION = 1;

    private DriverNbt() {}

    public static void writeDriver(ValueOutput out, TheMahjongDriver driver) {
        writeDriver(out, driver, false);
    }

    public static void writeReplayDriver(ValueOutput out, TheMahjongDriver driver) {
        writeDriver(out, driver, true);
    }

    private static void writeDriver(ValueOutput out, TheMahjongDriver driver, boolean replay) {
        out.putInt("v", VERSION);
        if (replay) MatchNbt.writeReplayMatch(out.child("match"), driver.match());
        else MatchNbt.writeMatch(out.child("match"), driver.match());
        if (driver.sichuanState() != null) {
            writeSichuan(out.child("sichuan"), driver.sichuanState());
            var claims = out.childrenList("sichuanClaims");
            driver.sichuanClaimDecisions().forEach((seat, index) -> {
                var entry = claims.addChild(); entry.putInt("seat", seat); entry.putInt("action", index);
            });
        }
        if (driver.exchangeState() != null) {
            var snapshot = driver.exchangeState().snapshot();
            var exchange = out.child("exchange");
            exchange.putInt("playerCount", snapshot.playerCount());
            exchange.putInt("direction", snapshot.direction());
            exchange.putBoolean("complete", snapshot.complete());
            var selections = exchange.childrenList("selections");
            for (int seat = 0; seat < snapshot.playerCount(); seat++) {
                var selection = selections.addChild();
                selection.putBoolean("confirmed", snapshot.confirmed().contains(seat));
                MatchNbt.writeTileList(selection, "tiles", snapshot.selections().get(seat));
            }
        }
        if (driver.bloodBattleState() != null) {
            var state = out.child("bloodBattleState");
            var wins = state.childrenList("wins");
            for (var win : driver.bloodBattleState().wins()) {
                var entry = wins.addChild();entry.putInt("seat", win.seat());
                entry.putBoolean("selfDrawn", win.selfDrawn());entry.putInt("sourceSeat", win.sourceSeat());
                MatchNbt.writeTile(entry.child("tile"), win.tile());
                writeWinResult(entry.child("result"), win.result());
            }
            var claims = state.childrenList("claims");
            driver.bloodBattleClaimDecisions().forEach((seat,index) -> {
                var entry=claims.addChild();entry.putInt("seat",seat);entry.putInt("action",index);
            });
        }
        TheMahjongDriver.Snapshot snap = driver.snapshot();
        writePhase(out.child("phase"), snap.phase());
        out.putBoolean("lastDrawWasRinshan", snap.lastDrawWasRinshan());
        out.putBoolean("abortiveDraw", snap.abortiveDraw());
        if (snap.pendingRiichiCommit().isPresent()) {
            out.putInt("pendingRiichiCommit", snap.pendingRiichiCommit().getAsInt());
        }
    }

    public static TheMahjongDriver readDriver(
            ValueInput in,
            List<MahjongPlayerInterface> players,
            Random random) {
        int v = in.getIntOr("v", 0);
        if (v != VERSION) {
            throw new IllegalArgumentException("Unsupported DriverNbt version: " + v);
        }
        TheMahjongMatch match = MatchNbt.readMatch(in.childOrEmpty("match"));
        MatchPhase phase = readPhase(in.childOrEmpty("phase"));
        boolean lastDrawWasRinshan = in.getBooleanOr("lastDrawWasRinshan", false);
        OptionalInt pendingRiichiCommit = in.getInt("pendingRiichiCommit")
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
        // Older saves lack the reason; a remaining live wall identifies their abortive draws.
        // A legacy last-tile abort cannot be recovered, so new saves always persist the flag.
        boolean legacyAbortive = phase instanceof MatchPhase.RoundEnded ended && ended.winResults().isEmpty()
                && match.currentRound().map(round -> !round.liveWall().isEmpty()).orElse(false);
        boolean abortiveDraw = in.getBooleanOr("abortiveDraw", legacyAbortive);
        TheMahjongDriver.Snapshot snapshot = new TheMahjongDriver.Snapshot(
                phase, lastDrawWasRinshan, pendingRiichiCommit, abortiveDraw);
        var driver = new TheMahjongDriver(match, players, random, snapshot);
        if (match.ruleSet().sichuan()) {
            in.child("sichuan").ifPresent(state -> driver.restoreSichuanState(readSichuan(state)));
            var claims = new HashMap<Integer,Integer>();
            for (var entry : in.childrenListOrEmpty("sichuanClaims")) {
                claims.put(entry.getIntOr("seat", -1), entry.getIntOr("action", -1));
            }
            driver.restoreSichuanClaims(claims);
        }
        if (!match.ruleSet().sichuan() && match.ruleSet().exchangeThree()) {
            in.child("exchange").ifPresent(exchange -> {
                var selections = new ArrayList<List<TheMahjongTile>>();
                var confirmed = new LinkedHashSet<Integer>();
                for (var entry : exchange.childrenListOrEmpty("selections")) {
                    int seat = selections.size();
                    if (entry.getBooleanOr("confirmed", false)) confirmed.add(seat);
                    selections.add(MatchNbt.readTileList(entry, "tiles"));
                }
                driver.restoreExchangeState(new com.themahjong.variants.ExchangeThreeState.Snapshot(
                        exchange.getIntOr("playerCount", match.playerCount()), exchange.getIntOr("direction", 1),
                        selections, confirmed, exchange.getBooleanOr("complete", false)));
            });
        }
        if (match.ruleSet().bloodBattle() && !match.ruleSet().sichuan()) {
            in.child("bloodBattleState").ifPresent(state -> {
                var wins = new ArrayList<com.themahjong.variants.BloodBattleState.Win>();
                for (var entry : state.childrenListOrEmpty("wins")) {
                    int seat=entry.getIntOr("seat",-1);
                    boolean selfDrawn=entry.getBooleanOr("selfDrawn",false);
                    wins.add(new com.themahjong.variants.BloodBattleState.Win(seat,
                            MatchNbt.readTile(entry.childOrEmpty("tile")), selfDrawn,
                            readWinResult(entry.childOrEmpty("result")),entry.getIntOr("sourceSeat",selfDrawn?seat:-1)));
                }
                driver.restoreBloodBattleState(new com.themahjong.variants.BloodBattleState.Snapshot(wins));
                var claims=new HashMap<Integer,Integer>();
                for (var entry : state.childrenListOrEmpty("claims"))
                    claims.put(entry.getIntOr("seat",-1),entry.getIntOr("action",-1));
                driver.restoreBloodBattleClaims(claims);
            });
        }
        return driver;
    }

    private static void writeSichuan(ValueOutput out, SichuanState state) {
        var snapshot = state.snapshot();
        out.putString("stage", snapshot.stage().name());
        out.putInt("exchangeDirection", snapshot.exchangeDirection());
        out.putBoolean("lastDrawRinshan", snapshot.lastDrawRinshan());
        out.putInt("pendingKakan", snapshot.pendingKakan());
        var selections = out.childrenList("selections");
        for (int seat = 0; seat < 4; seat++) {
            var selection = selections.addChild();
            selection.putBoolean("confirmed", snapshot.confirmed().contains(seat));
            selection.putInt("missingSuit", snapshot.missingSuits().get(seat));
            MatchNbt.writeTileList(selection, "tiles", snapshot.selections().get(seat));
        }
        var wins = out.childrenList("wins");
        for (var win : snapshot.wins()) {
            var entry = wins.addChild(); entry.putInt("seat", win.seat());
            entry.putBoolean("selfDrawn", win.selfDrawn());
            entry.putInt("sourceSeat", win.sourceSeat());
            MatchNbt.writeTile(entry.child("tile"), win.tile());
            writeWinResult(entry.child("result"), win.result());
        }
        var payments = out.childrenList("kanPayments");
        for (var payment : snapshot.kanPayments()) {
            var entry = payments.addChild(); entry.putInt("payer", payment.payer());
            entry.putInt("receiver", payment.receiver()); entry.putInt("amount", payment.amount());
        }
    }

    private static SichuanState.Snapshot readSichuan(ValueInput in) {
        var stage = SichuanState.Stage.valueOf(in.getStringOr("stage", "VOID_SUIT"));
        var selections = new ArrayList<List<TheMahjongTile>>();
        var confirmed = new LinkedHashSet<Integer>(); var missingSuits = new ArrayList<Integer>();
        for (var entry : in.childrenListOrEmpty("selections")) {
            int seat = selections.size();
            if (entry.getBooleanOr("confirmed", false)) confirmed.add(seat);
            missingSuits.add(entry.getIntOr("missingSuit", -1));
            selections.add(MatchNbt.readTileList(entry, "tiles"));
        }
        var wins = new ArrayList<SichuanState.Win>();
        for (var entry : in.childrenListOrEmpty("wins")) {
            int seat = entry.getIntOr("seat", 0);
            var tile = MatchNbt.readTile(entry.childOrEmpty("tile"));
            boolean selfDrawn = entry.getBooleanOr("selfDrawn", false);
            var result = readWinResult(entry.childOrEmpty("result"));
            var legacy = new SichuanState.Win(seat, tile, selfDrawn, result);
            wins.add(new SichuanState.Win(seat, tile, selfDrawn, result,
                    entry.getIntOr("sourceSeat", legacy.sourceSeat())));
        }
        var payments = new ArrayList<SichuanState.Payment>();
        for (var entry : in.childrenListOrEmpty("kanPayments")) {
            payments.add(new SichuanState.Payment(entry.getIntOr("payer", 0),
                    entry.getIntOr("receiver", 0), entry.getIntOr("amount", 0)));
        }
        return new SichuanState.Snapshot(stage, in.getIntOr("exchangeDirection", 1), selections,
                confirmed, missingSuits, wins, payments, in.getBooleanOr("lastDrawRinshan", false),
                in.getIntOr("pendingKakan", -1));
    }

    public static void writePhase(ValueOutput out, MatchPhase phase) {
        if (phase instanceof MatchPhase.NotStarted) {
            out.putString("type", "NOT_STARTED");
        } else if (phase instanceof MatchPhase.Dealing d) {
            out.putString("type", "DEALING");
            out.putString("stage", d.stage().name());
            out.putDouble("elapsed", d.elapsed());
        } else if (phase instanceof MatchPhase.AwaitingDraw d) {
            out.putString("type", "AWAITING_DRAW");
            out.putInt("seat", d.seat());
            out.putBoolean("rinshan", d.rinshan());
        } else if (phase instanceof MatchPhase.AwaitingDiscard d) {
            out.putString("type", "AWAITING_DISCARD");
            out.putInt("seat", d.seat());
        } else if (phase instanceof MatchPhase.AwaitingClaims c) {
            out.putString("type", "AWAITING_CLAIMS");
            int[] pending = new int[c.pendingSeats().size()];
            int i = 0;
            for (Integer seat : c.pendingSeats()) {
                pending[i++] = seat;
            }
            out.putIntArray("pendingSeats", pending);
            MatchNbt.writeTile(out.child("heldTile"), c.heldTile());
            out.putString("source", c.source().name());
        } else if (phase instanceof MatchPhase.Resolving) {
            out.putString("type", "RESOLVING");
        } else if (phase instanceof MatchPhase.RoundEnded re) {
            out.putString("type", "ROUND_ENDED");
            ValueOutput.ValueOutputList wins = out.childrenList("winResults");
            for (WinResult wr : re.winResults()) {
                writeWinResult(wins.addChild(), wr);
            }
        } else if (phase instanceof MatchPhase.BetweenRounds) {
            out.putString("type", "BETWEEN_ROUNDS");
        } else if (phase instanceof MatchPhase.MatchEnded) {
            out.putString("type", "MATCH_ENDED");
        } else {
            throw new IllegalArgumentException("Unknown MatchPhase: " + phase);
        }
    }

    public static MatchPhase readPhase(ValueInput in) {
        String type = in.getStringOr("type", "");
        return switch (type) {
            case "NOT_STARTED" -> new MatchPhase.NotStarted();
            case "DEALING" -> new MatchPhase.Dealing(
                    MatchPhase.Stage.valueOf(in.getStringOr("stage", "")),
                    in.getDoubleOr("elapsed", 0.0));
            case "AWAITING_DRAW" -> new MatchPhase.AwaitingDraw(in.getIntOr("seat", 0), in.getBooleanOr("rinshan", false));
            case "AWAITING_DISCARD" -> new MatchPhase.AwaitingDiscard(in.getIntOr("seat", 0));
            case "AWAITING_CLAIMS" -> {
                Set<Integer> pending = new LinkedHashSet<>();
                for (int seat : in.getIntArray("pendingSeats").orElse(new int[0])) {
                    pending.add(seat);
                }
                TheMahjongTile heldTile = MatchNbt.readTile(in.childOrEmpty("heldTile"));
                MatchPhase.ClaimSource source = MatchPhase.ClaimSource.valueOf(in.getStringOr("source", ""));
                yield new MatchPhase.AwaitingClaims(pending, heldTile, source);
            }
            case "RESOLVING" -> new MatchPhase.Resolving();
            case "ROUND_ENDED" -> {
                List<WinResult> results = new ArrayList<>();
                for (ValueInput win : in.childrenListOrEmpty("winResults")) {
                    results.add(readWinResult(win));
                }
                yield new MatchPhase.RoundEnded(results);
            }
            case "BETWEEN_ROUNDS" -> new MatchPhase.BetweenRounds();
            case "MATCH_ENDED" -> new MatchPhase.MatchEnded();
            default -> throw new IllegalArgumentException("Unknown MatchPhase type: " + type);
        };
    }

    private static void writeWinResult(ValueOutput out, WinResult wr) {
        ValueOutput.TypedOutputList<String> yaku = out.list("yaku", Codec.STRING);
        for (NonYakuman y : wr.yaku()) {
            yaku.add(y.name());
        }
        ValueOutput.TypedOutputList<String> yakuman = out.list("yakuman", Codec.STRING);
        for (Yakuman y : wr.yakuman()) {
            yakuman.add(y.name());
        }
        out.putInt("han", wr.han());
        out.putInt("fu", wr.fu());
        out.putInt("doraCount", wr.doraCount());
        int[] deltas = new int[wr.pointDeltas().size()];
        for (int i = 0; i < deltas.length; i++) {
            deltas[i] = wr.pointDeltas().get(i);
        }
        out.putIntArray("pointDeltas", deltas);
    }

    private static WinResult readWinResult(ValueInput in) {
        List<NonYakuman> yaku = new ArrayList<>();
        for (String name : in.listOrEmpty("yaku", Codec.STRING)) {
            yaku.add(NonYakuman.valueOf(name));
        }
        List<Yakuman> yakuman = new ArrayList<>();
        for (String name : in.listOrEmpty("yakuman", Codec.STRING)) {
            yakuman.add(Yakuman.valueOf(name));
        }
        List<Integer> deltas = new ArrayList<>();
        for (int delta : in.getIntArray("pointDeltas").orElse(new int[0])) {
            deltas.add(delta);
        }
        return new WinResult(yaku, yakuman,
                in.getIntOr("han", 0), in.getIntOr("fu", 0), in.getIntOr("doraCount", 0), deltas);
    }
}
