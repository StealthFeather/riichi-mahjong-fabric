package com.riichimahjong.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.riichimahjong.replay.ReplayRecorder;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;

/** Exercise the same accepted-state observer as a live table, including duplicate physically selectable tiles. */
public final class MortalEventsRegression {
    private static final List<MahjongPlayerInterface> PAUSED = Collections.nCopies(4, (req, dt) -> Optional.empty());
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    private static JsonObject last(JsonArray events) { return events.get(events.size() - 1).getAsJsonObject(); }
    private static String type(JsonObject event) { return event.get("type").getAsString(); }

    private static void turn(TheMahjongDriver driver, int seat, PlayerAction action) {
        driver.submitAction(seat, action);
        driver.advance(.05);
    }

    private static void testEqualCopyAndReload(boolean discardDrawn) {
        // Select a deterministic ordinary game where the dealer's first draw matches an initial copy.
        TheMahjongDriver driver = null;
        ReplayRecorder recorder = null;
        for (int seed = 0; seed < 60; seed++) {
            var candidate = new TheMahjongDriver(TheMahjongMatch.defaultTenhou(), PAUSED, new Random(seed));
            var record = new ReplayRecorder("TENHOU_4P", List.of());
            candidate.setStateObserver(record::capture);
            candidate.startMatch();
            turn(candidate, 0, PlayerAction.Draw.INSTANCE);
            var player = candidate.currentRound().players().getFirst();
            var drawn = player.currentHand().getLast();
            if (Collections.frequency(player.currentHand(), drawn) > 1) { driver = candidate; recorder = record; break; }
        }
        check(driver != null, "test seed with duplicate draw");
        for (int viewer = 0; viewer < 4; viewer++) {
            var events = recorder.mortalEvents(viewer);
            check(events.size() == 3, "start and one draw");
            var start = events.get(1).getAsJsonObject();
            check(type(start).equals("start_kyoku") && start.get("bakaze").getAsString().equals("E"), "initial hand");
            var hands = start.getAsJsonArray("tehais");
            for (int actor = 0; actor < 4; actor++) {
                var tiles = hands.get(actor).getAsJsonArray();
                check(tiles.size() == 13, "13 tiles per MJAI hand");
                for (var tile : tiles) check(tile.getAsString().equals("?") == (actor != viewer), "opponent initial hand exposed");
            }
            check(type(last(events)).equals("tsumo") && last(events).get("pai").getAsString().equals("?") == (viewer != 0),
                    "draw perspective");
        }

        var tile = ((TheMahjongRound.ActiveTile.Drawn)driver.currentRound().activeTile()).tile();
        recorder.markHumanDiscard(0, discardDrawn);
        turn(driver, 0, new PlayerAction.Discard(tile));
        var events = recorder.mortalEvents(1);
        check(type(last(events)).equals("dahai") && last(events).get("tsumogiri").getAsBoolean() == discardDrawn,
                "physical equal copy from hand versus draw");
        var persisted = recorder.toNbt();
        var restored = ReplayRecorder.fromNbt(persisted);
        restored.capture(driver);
        check(restored.mortalEvents(1).equals(events), "save/load must neither duplicate actions nor change their visibility");
        for (var frame : persisted.getListOrEmpty("Frames")) ((CompoundTag)frame).remove("Tsumogiri");
        check(ReplayRecorder.fromNbt(persisted).mortalEvents(1).isEmpty(), "old ambiguous equal-copy discarded origin");
        var partial = ReplayRecorder.forCurrentHand("TENHOU_4P");
        partial.capture(driver);
        check(partial.mortalEvents(1).isEmpty() && ReplayRecorder.fromNbt(partial.toNbt()).currentHandOnly(), "old partial hand after restart");
    }

    private static boolean playHand(TheMahjongDriver driver, int seed) {
        var random = new Random(seed);
        for (int step = 0; step < 320; step++) {
            var phase = driver.currentPhase();
            if (phase instanceof MatchPhase.RoundEnded) return true;
            if (phase instanceof MatchPhase.AwaitingDraw draw) {
                turn(driver, draw.seat(), PlayerAction.Draw.INSTANCE);
            } else if (phase instanceof MatchPhase.AwaitingDiscard discard) {
                var legal = driver.legalActions(discard.seat());
                var priority = legal.stream().filter(a -> a instanceof PlayerAction.Ankan || a instanceof PlayerAction.Kakan
                        || a instanceof PlayerAction.DiscardWithRiichi).findFirst();
                var discards = legal.stream().filter(PlayerAction.Discard.class::isInstance).toList();
                turn(driver, discard.seat(), priority.orElseGet(() -> discards.get(random.nextInt(discards.size()))));
            } else if (phase instanceof MatchPhase.AwaitingClaims claims) {
                for (int seat : claims.pendingSeats()) {
                    var legal = driver.legalActions(seat);
                    var call = legal.stream().filter(a -> a instanceof PlayerAction.Daiminkan || a instanceof PlayerAction.Pon
                            || a instanceof PlayerAction.Chi).findFirst();
                    driver.submitAction(seat, call.orElse(PlayerAction.Pass.INSTANCE));
                }
                driver.advance(.05);
            } else return false;
        }
        return false;
    }

    private static void replayAcceptedActions() {
        var driver = new TheMahjongDriver(TheMahjongMatch.defaultTenhou(), PAUSED, new Random(3));
        var record = ReplayRecorder.forCurrentHand("TENHOU_4P");
        driver.setStateObserver(record::capture);
        driver.startMatch();
        check(playHand(driver, 25), "full hand terminates");
        var events = record.mortalEvents(2);
        check(events.size() > 100, "observer captured complete hand");
        int discards = 0, calls = 0;
        for (var value : events) {
            var event = value.getAsJsonObject();
            switch(type(event)) {
                case "dahai" -> discards++;
                case "tsumo" -> check(event.get("pai").getAsString().equals("?") == (event.get("actor").getAsInt() != 2), "hidden draw");
                case "chi", "pon", "daiminkan" -> {
                    calls++;
                    int target = event.get("target").getAsInt();
                    check(event.get("actor").getAsInt() != target, "meld source seat");
                    check(event.getAsJsonArray("consumed").size() == (type(event).equals("daiminkan") ? 3 : 2), "meld consumes own tiles");
                }
            }
        }
        check(discards == driver.currentRound().players().stream().mapToInt(player -> player.discards().size()).sum() && calls > 0,
                "ordered public river and melds");
        driver.advanceRound(false, 1);
        var next = record.mortalEvents(2);
        check(next.size() == 2 && next.get(1).getAsJsonObject().get("kyoku").getAsInt() == 2, "fresh hand after incomplete legacy recording");
    }

    private static TheMahjongDriver fixture(ReplayRecorder record, List<TheMahjongTile> dealerHand,
                                            List<TheMahjongTile> callerHand, TheMahjongTile firstDraw) {
        var wall = new ArrayList<>(com.themahjong.TheMahjongTileSet.standardRiichi(true).createOrderedWall());
        Collections.shuffle(wall, new Random(117));
        for (int i = 0; i < 13; i++) { wall.set(i, dealerHand.get(i)); wall.set(13 + i, callerHand.get(i)); }
        wall.set(66, firstDraw);
        var base = TheMahjongMatch.defaultTenhou();
        var round = TheMahjongRound.start(4, 25000, wall);
        var match = new TheMahjongMatch(4, 25000, 30000, 2, TheMahjongMatch.State.IN_ROUND,
                base.tileSet(), base.ruleSet(), List.of(), round);
        var driver = new TheMahjongDriver(match, PAUSED, new Random(1),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDraw(0,false),false,OptionalInt.empty()));
        driver.setStateObserver(record::capture);
        record.capture(driver);
        return driver;
    }

    private static List<String> types(JsonArray events) {
        var result = new ArrayList<String>();
        for (var event : events) result.add(type(event.getAsJsonObject()));
        return result;
    }

    private static void declarations() {
        var record = new ReplayRecorder("TENHOU_4P", List.of());
        var green = RulesRegression.tile(4,2);
        var caller = new ArrayList<>(RulesRegression.waiting(1));
        caller.set(1,green); caller.set(2,green);
        var driver = fixture(record, RulesRegression.waiting(0), caller, green);
        turn(driver,0, PlayerAction.Draw.INSTANCE);
        turn(driver,0, new PlayerAction.DiscardWithRiichi(green));
        var window = (MatchPhase.AwaitingClaims)driver.currentPhase();
        check(types(record.mortalEvents(1)).equals(List.of("start_game","start_kyoku","tsumo","reach","dahai")),
                "riichi deposit must await resolution of the declaration discard");
        for (int seat : window.pendingSeats()) {
            PlayerAction choice = seat == 1 ? driver.legalActions(1).stream().filter(PlayerAction.Pon.class::isInstance).findFirst().orElseThrow()
                    : PlayerAction.Pass.INSTANCE;
            driver.submitAction(seat, choice);
        }
        driver.advance(.05);
        var events = record.mortalEvents(1);
        check(types(events).equals(List.of("start_game","start_kyoku","tsumo","reach","dahai","reach_accepted","pon")),
                "riichi declaration called by the next player");
        check(last(events).get("pai").getAsString().equals("F") && last(events).get("target").getAsInt()==0,
                "precise public called tile and source");

        record = new ReplayRecorder("TENHOU_4P", List.of());
        var dealer = new ArrayList<>(RulesRegression.waiting(0));
        var normal = RulesRegression.tile(0,5);
        dealer.set(0,normal); dealer.set(1,normal);
        var red = new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);
        driver = fixture(record,dealer,caller,red);
        turn(driver,0,PlayerAction.Draw.INSTANCE);
        turn(driver,0,driver.legalActions(0).stream().filter(PlayerAction.Ankan.class::isInstance).findFirst().orElseThrow());
        var afterKan = record.mortalEvents(2);
        check(types(afterKan).equals(List.of("start_game","start_kyoku","tsumo","ankan","dora")), "immediate ankan dora order");
        check(afterKan.get(3).getAsJsonObject().getAsJsonArray("consumed").asList().stream()
                .anyMatch(tile -> tile.getAsString().equals("5mr")), "red tile in revealed kan");
        var liveWallBefore = driver.currentRound().liveWall();
        turn(driver,0, PlayerAction.Draw.INSTANCE);
        check(last(record.mortalEvents(2)).get("pai").getAsString().equals("?"), "hidden replacement draw");
        check(driver.currentRound().liveWall().equals(liveWallBefore.subList(0,liveWallBefore.size()-1)),
                "reserve the last live tile on the first replacement draw");
        var legacy = fromWall(driver, liveWallBefore);
        legacy.restoreLegacyKanWall();
        check(legacy.currentRound().liveWall().equals(driver.currentRound().liveWall()), "consume previously unreserved tiles in old saves");

        caller.set(1,normal); caller.set(2,normal);
        driver = fixture(new ReplayRecorder("TENHOU_4P",List.of()), dealer,caller,red);
        turn(driver,0,PlayerAction.Draw.INSTANCE);
        driver = fromWall(driver, List.of());
        check(driver.legalActions(0).stream().noneMatch(PlayerAction.Ankan.class::isInstance), "cannot kan on the last draw");
        turn(driver,0,new PlayerAction.Discard(red));
        if (driver.currentPhase() instanceof MatchPhase.AwaitingClaims claims) {
            for (int seat : claims.pendingSeats()) check(driver.legalActions(seat).stream()
                    .allMatch(a -> a instanceof PlayerAction.Pass || a instanceof PlayerAction.DeclareRon), "last discard allows only ron and pass");
        }
    }

    private static TheMahjongDriver fromWall(TheMahjongDriver d, List<TheMahjongTile> wall) {
        var r = d.currentRound();
        var round = new TheMahjongRound(r.roundWind(),r.handNumber(),r.honba(),r.riichiSticks(),r.dealerSeat(),r.state(),
                r.currentTurnSeat(),r.claimSourceSeat().orElse(-1),r.activeTile(),wall,r.rinshanTiles(),r.doraIndicators(),
                r.uraDoraIndicators(),r.revealedDoraCount(),r.pendingKanDoraReveals(),r.players(),r.pendingDeltas());
        var m = d.match();
        var match = new TheMahjongMatch(4,m.startingPoints(),m.targetPoints(),m.roundCount(),m.state(),m.tileSet(),m.ruleSet(),
                m.completedRounds(),round);
        return new TheMahjongDriver(match,PAUSED,new Random(1),d.snapshot());
    }

    public static void main(String[] args) {
        testEqualCopyAndReload(false);
        testEqualCopyAndReload(true);
        replayAcceptedActions();
        declarations();
        System.out.println("Mortal MJAI privacy, ordered play, save continuity and physical discard checks passed");
    }
}
