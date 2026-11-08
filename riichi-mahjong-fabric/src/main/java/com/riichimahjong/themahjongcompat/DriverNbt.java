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
        out.putInt("v", VERSION);
        MatchNbt.writeMatch(out.child("match"), driver.match());
        TheMahjongDriver.Snapshot snap = driver.snapshot();
        writePhase(out.child("phase"), snap.phase());
        out.putBoolean("lastDrawWasRinshan", snap.lastDrawWasRinshan());
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
        TheMahjongDriver.Snapshot snapshot = new TheMahjongDriver.Snapshot(
                phase, lastDrawWasRinshan, pendingRiichiCommit);
        return new TheMahjongDriver(match, players, random, snapshot);
    }

    private static void writePhase(ValueOutput out, MatchPhase phase) {
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

    private static MatchPhase readPhase(ValueInput in) {
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
