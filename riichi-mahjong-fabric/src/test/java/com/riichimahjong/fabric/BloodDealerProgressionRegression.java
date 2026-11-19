package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.TheMahjongTile.Wind;
import com.themahjong.driver.*;
import com.themahjong.sichuan.SichuanState;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Drive each winning event and the table's actual next-hand decision across save/reload. */
public final class BloodDealerProgressionRegression {
    private static final TheMahjongTile NINE = tile(0, 9);
    private static final TheMahjongTile SPARE = tile(2, 6);
    private enum DealerHand { READY, NOTEN, MISSING_SUIT }

    private static List<MahjongPlayerInterface> humans() {
        return Stream.generate(() -> (MahjongPlayerInterface)new MahjongHumanPlayer()).limit(4).toList();
    }

    // The concealed straight supplies a riichi yaku. Seats 1 and 3 can ron the same nine.
    private static TheMahjongDriver fixture(RuleSetPreset preset, Wind wind, int dealer, int turn,
            TheMahjongTile drawn, List<TheMahjongTile> wall, DealerHand dealerHand) {
        var base = preset.newMatch(MatchLength.SOUTH, false, false, true);
        boolean sichuan = base.ruleSet().sichuan();
        var players = new ArrayList<TheMahjongPlayer>();
        for (int seat = 0; seat < 4; seat++) {
            var hand = new ArrayList<TheMahjongTile>();
            for (int rank = 1; rank <= 9; rank++) hand.add(tile(1, rank));
            hand.addAll(Collections.nCopies(3, tile(0, seat + 1)));
            hand.add(seat % 2 == 1 ? NINE : tile(0, 8));
            if (seat == dealer && dealerHand == DealerHand.NOTEN) hand.set(0, tile(0, 6));
            if (seat == dealer && dealerHand == DealerHand.MISSING_SUIT) hand.set(12, tile(2, 1));
            if (seat == turn) hand.add(drawn);
            players.add(new TheMahjongPlayer(40000, TheMahjongPlayer.RiichiState.NONE, false, hand, List.of(),
                    List.of(new TheMahjongDiscard(tile(0, 5), false)), List.of()));
        }
        var indicators = sichuan ? List.<TheMahjongTile>of() : Collections.nCopies(5, tile(4, 3));
        var round = new TheMahjongRound(wind, dealer + 1, sichuan ? 0 : 2, 0, dealer, TheMahjongRound.State.TURN,
                turn, -1, TheMahjongRound.ActiveTile.drawn(drawn), wall,
                sichuan ? List.of() : Collections.nCopies(4, tile(3, 2)), indicators, indicators, sichuan ? 0 : 1,
                players, List.of());
        var match = new TheMahjongMatch(4, base.startingPoints(), base.targetPoints(), 2,
                TheMahjongMatch.State.IN_ROUND, base.tileSet(), base.ruleSet(), List.of(), round);
        var driver = new TheMahjongDriver(match, humans(), new Random(23),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(turn), false, OptionalInt.empty()));
        if (sichuan) driver.restoreSichuanState(new SichuanState.Snapshot(SichuanState.Stage.PLAY, 1,
                List.of(List.of(), List.of(), List.of(), List.of()), Set.of(), List.of(2, 2, 2, 2),
                List.of(), List.of(), false, -1));
        return driver;
    }

    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(out, driver);
        var input = TagValueInput.create(ProblemReporter.DISCARDING, HolderLookup.Provider.create(Stream.empty()), out.buildResult());
        return DriverNbt.readDriver(input, humans(), new Random(29));
    }

    private static PlayerAction action(TheMahjongDriver driver, int seat, Class<? extends PlayerAction> type) {
        return driver.legalActions(seat).stream().filter(type::isInstance).findFirst()
                .orElseThrow(() -> new AssertionError("missing " + type.getSimpleName() + " for seat " + seat));
    }

    private static void ron(TheMahjongDriver driver, int from, Set<Integer> winners) {
        act(driver, from, new PlayerAction.Discard(NINE));
        var claims = (MatchPhase.AwaitingClaims)driver.currentPhase();
        check(claims.pendingSeats().containsAll(winners), "fixture did not offer the expected ron claims");
        for (int seat : claims.pendingSeats()) {
            driver.submitAction(seat, winners.contains(seat) ? action(driver, seat, PlayerAction.DeclareRon.class)
                    : PlayerAction.Pass.INSTANCE);
        }
        driver.advance(.05);
    }

    private static void drawAndDiscard(TheMahjongDriver driver, int seat, TheMahjongTile tile) {
        check(driver.legalActions(seat).contains(PlayerAction.Draw.INSTANCE), "wrong next draw seat " + seat);
        act(driver, seat, PlayerAction.Draw.INSTANCE);
        act(driver, seat, new PlayerAction.Discard(tile));
        passClaims(driver);
    }

    private static void advance(TheMahjongDriver driver, int winners, boolean repeats) {
        check(driver.currentPhase() instanceof MatchPhase.RoundEnded end && end.winResults().size() == winners,
                "unexpected terminal phase or number of accepted wins");
        var policy = RoundProgression.next(driver);
        check(policy.equals(RoundProgression.next(reload(driver))), "reloading a finished hand changed the next dealer");
        check(policy.renchan() == repeats, "wrong continuation for " + driver.rules().sichuan() + "/" + winners + "/" + repeats);
        var old = driver.currentRound();
        int dealer = old.dealerSeat();
        driver.advanceRound(policy.renchan(), policy.nextHonba());
        check(driver.match().completedRounds().equals(List.of(old)), "wrong saved round history");
        if (old.roundWind() == Wind.SOUTH && dealer == 3 && !repeats) {
            check(driver.currentPhase() instanceof MatchPhase.MatchEnded, "South four incorrectly continued");
        } else {
            var next = driver.currentRound();
            check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw draw && draw.seat() == next.dealerSeat(),
                    "next hand did not begin with the dealer");
            check(next.dealerSeat() == (repeats ? dealer : (dealer + 1) % 4)
                            && next.handNumber() == next.dealerSeat() + 1
                            && next.roundWind() == (dealer == 3 && !repeats ? Wind.SOUTH : old.roundWind()),
                    "incorrect next dealer, hand or wind");
        }
    }

    private static void firstWinners(RuleSetPreset preset) {
        var tsumo = fixture(preset, Wind.SOUTH, 3, 3, NINE, List.of(SPARE), DealerHand.READY);
        act(tsumo, 3, action(tsumo, 3, PlayerAction.DeclareTsumo.class));
        tsumo = reload(tsumo);
        drawAndDiscard(tsumo, 0, SPARE);
        advance(tsumo, 1, true);

        var joint = fixture(preset, Wind.SOUTH, 3, 0, NINE, List.of(SPARE), DealerHand.READY);
        ron(joint, 0, Set.of(1, 3)); // Dealer is the second claimant in seat order.
        joint = reload(joint);
        drawAndDiscard(joint, 2, SPARE);
        advance(joint, 2, true);

        var other = fixture(preset, Wind.EAST, 3, 1, NINE, List.of(SPARE), DealerHand.READY);
        act(other, 1, action(other, 1, PlayerAction.DeclareTsumo.class));
        drawAndDiscard(other, 2, SPARE);
        advance(other, 1, false);
    }

    private static void laterDealerRon(RuleSetPreset preset) {
        // Same discarder and same tile on two different turns. Only seat 1 won the first event.
        var d = fixture(preset, Wind.SOUTH, 3, 0, NINE, List.of(SPARE, SPARE, NINE), DealerHand.READY);
        ron(d, 0, Set.of(1));
        d = reload(d);
        drawAndDiscard(d, 2, SPARE);
        drawAndDiscard(d, 3, SPARE);
        act(d, 0, PlayerAction.Draw.INSTANCE);
        ron(d, 0, Set.of(3));
        advance(d, 2, false);
    }

    private static void noWinners(RuleSetPreset preset, DealerHand hand, boolean repeats) {
        var d = fixture(preset, Wind.EAST, 2, 0, SPARE, List.of(), hand);
        act(d, 0, new PlayerAction.Discard(SPARE));
        passClaims(d);
        advance(d, 0, repeats);
    }

    public static void main(String[] args) {
        for (var preset : List.of(RuleSetPreset.MAHJONG_SOUL_4P, RuleSetPreset.SICHUAN_4P)) {
            firstWinners(preset);
            laterDealerRon(preset);
            noWinners(preset, DealerHand.READY, true);
            noWinners(preset, DealerHand.NOTEN, false);
        }
        noWinners(RuleSetPreset.SICHUAN_4P, DealerHand.MISSING_SUIT, false);
        System.out.println("PASS: riichi and Sichuan blood-battle dealer progression follows first wins, simultaneous ron, exhaustive readiness and dingque across save/reload and South four");
    }
}
