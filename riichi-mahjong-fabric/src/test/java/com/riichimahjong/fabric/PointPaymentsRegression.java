package com.riichimahjong.fabric;

import com.themahjong.TheMahjongDiscard;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.compat.PointPayments;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import com.themahjong.sichuan.SichuanRules;
import com.themahjong.sichuan.SichuanState;
import com.themahjong.variants.BloodBattleState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.riichimahjong.fabric.RulesRegression.*;

public final class PointPaymentsRegression {
    private static void checkPayments(Runnable action, int... expected) {
        int[] paid = new int[4];
        PointPayments.collect(action, (seat, points) -> paid[seat] += points);
        check(Arrays.equals(paid, expected), "expected payments " + Arrays.toString(expected)
                + " but got " + Arrays.toString(paid));
    }

    private static void simultaneousNagashi() {
        var winner = new TheMahjongPlayer(25000, TheMahjongPlayer.RiichiState.NONE, false,
                waiting(0), List.of(), List.of(new TheMahjongDiscard(tile(0, 9), false)), List.of());
        var driver = driver(Collections.nCopies(4, winner), List.of());
        var round = driver.currentRound();
        // All four qualify. Every final balance is 25,000, although all four have paid.
        checkPayments(() -> {
            var settled = round.exhaustiveDraw(driver.rules());
            check(settled.players().stream().allMatch(p -> p.points() == 25000), "expected unchanged balances");
        }, 12000, 8000, 8000, 8000);
        checkPayments(() -> new BloodBattleState().exhaustive(round, driver.rules()), 12000, 8000, 8000, 8000);
    }

    private static void ronOnDeclarationDiscard() {
        var white = tile(4, 1);
        var hand = new ArrayList<>(waiting(0));
        hand.add(white);
        var driver = driver(List.of(player(hand), player(waiting(1)), player(waiting(2)), player(waiting(1))),
                Collections.nCopies(24, white));
        checkPayments(() -> act(driver, 0, new PlayerAction.DiscardWithRiichi(white)), 0, 0, 0, 0);
        int ronTotal = 0;
        for (int winner : List.of(1, 2)) {
            var ron = driver.legalActions(winner).stream().filter(PlayerAction.DeclareRon.class::isInstance)
                    .map(PlayerAction.DeclareRon.class::cast).findFirst().orElseThrow();
            ronTotal -= ron.result().pointDeltas().getFirst();
            driver.submitAction(winner, ron);
        }
        driver.submitAction(3, PlayerAction.Pass.INSTANCE);
        checkPayments(() -> driver.advance(.05), ronTotal, 0, 0, 0);
        checkPayments(() -> driver.advance(.05), 0, 0, 0, 0);
    }

    private static void dealerTsumo() {
        var green = tile(4, 2);
        var hand = new ArrayList<>(waiting(0));
        hand.set(hand.size() - 1, green);
        hand.add(green);
        var driver = driver(List.of(player(hand), player(waiting(1)), player(waiting(2)), player(waiting(1))),
                Collections.nCopies(24, green));
        var win = driver.legalActions(0).stream().filter(PlayerAction.DeclareTsumo.class::isInstance)
                .map(PlayerAction.DeclareTsumo.class::cast).findFirst().orElseThrow();
        int payment = -win.result().pointDeltas().get(1);
        check(payment > 0, "expected an accepted tsumo payment");
        checkPayments(() -> act(driver, 0, win), 0, payment, payment, payment);
    }

    private static void reciprocalKanRefunds() {
        var hand = new ArrayList<>(waiting(0));
        var discard = tile(0, 1);
        hand.add(discard);
        var driver = driver(Collections.nCopies(4, player(hand)), List.of());
        var rules = SichuanRules.newMatch(2, false, false, true).ruleSet();
        var round = driver.currentRound().discard(discard, rules);
        var phase = new MatchPhase.AwaitingClaims(Set.of(1), discard, MatchPhase.ClaimSource.DISCARD);
        // All four still have their missing suit. Seats 0 and 1 must refund each other's open kan.
        var kans = List.of(new SichuanState.Payment(0, 1, 2000), new SichuanState.Payment(1, 0, 2000));
        var state = new SichuanState(new SichuanState.Snapshot(SichuanState.Stage.PLAY, 1,
                List.of(), Set.of(), List.of(0, 0, 0, 0), List.of(), kans, false, -1));
        checkPayments(() -> {
            var ended = state.resolveClaims(round, phase, rules, Map.of(1, PlayerAction.Pass.INSTANCE));
            check(ended.phase() instanceof MatchPhase.RoundEnded, "expected an exhaustive draw");
            check(ended.round().players().stream().allMatch(p -> p.points() == 25000), "refund balances changed");
        }, 2000, 2000, 0, 0);
    }

    public static void main(String[] args) {
        checkPayments(RulesRegression::successiveRiichi, 0, 0, 0, 0);
        checkPayments(RulesRegression::calledRiichiDeposit, 0, 0, 0, 0);
        simultaneousNagashi();
        ronOnDeclarationDiscard();
        dealerTsumo();
        reciprocalKanRefunds();
        System.out.println("PASS: riichi stick exemption, declaration-discard ron, double ron, tsumo and gross payments during simultaneous nagashi and Sichuan kan refunds");
    }
}
