package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.driver.bots.AbstractStupidBot;
import java.util.*;

/** The observer sees accepted human, submitted and bot decisions before the round is changed. */
public final class ActionObserverRegression {
    private record Observed(DecisionRequest request, PlayerAction action) {}
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static TheMahjongDriver start() {
        var driver = new TheMahjongDriver(TheMahjongMatch.defaultMahjongSoul(),
                List.of(new MahjongHumanPlayer(), new MahjongHumanPlayer(), new MahjongHumanPlayer(), new MahjongHumanPlayer()),
                new Random(17));
        driver.startMatch();
        return driver;
    }
    private static List<Observed> observe(TheMahjongDriver driver) {
        var events = new ArrayList<Observed>();
        driver.setActionObserver((request, action) -> {
            check(driver.currentRound() == request.round(), "observer did not receive the pre-action round");
            check(driver.currentPhase() == request.phase(), "observer did not receive the pre-action phase");
            check(request.legalActions().contains(action), "observer received an illegal action");
            events.add(new Observed(request, action));
        });
        return events;
    }
    private static void humanAndSubmitted() {
        var driver = start();
        var events = observe(driver);
        driver.advance(.05);
        driver.advance(.05);
        check(events.isEmpty(), "idle controller produced a decision");
        var beforeDraw = driver.currentRound();
        ((MahjongHumanPlayer) driver.playerAt(0)).queue(PlayerAction.Draw.INSTANCE);
        driver.advance(.05);
        check(events.size() == 1 && events.getFirst().action() instanceof PlayerAction.Draw,
                "human draw missing or duplicated");
        check(events.getFirst().request().round() == beforeDraw && driver.currentRound() != beforeDraw,
                "draw observer ran after execution");
        var discard = driver.legalActions(0).stream().filter(PlayerAction.Discard.class::isInstance).findFirst().orElseThrow();
        var beforeDiscard = driver.currentRound();
        ((MahjongHumanPlayer) driver.playerAt(0)).queue(discard);
        driver.advance(.05);
        check(events.size() == 2 && events.getLast().request().round() == beforeDiscard,
                "human discard was omitted or observed after execution");

        var submitted = start();
        var submittedEvents = observe(submitted);
        var before = submitted.currentRound();
        submitted.submitAction(0, PlayerAction.Draw.INSTANCE);
        check(submittedEvents.isEmpty(), "queued action observed before polling");
        submitted.advance(.05);
        submitted.advance(.05);
        check(submittedEvents.size() == 1 && submittedEvents.getFirst().request().round() == before,
                "submitted draw missing, duplicated or observed with the wrong round");
        var replacementEvents = new ArrayList<PlayerAction>();
        submitted.setActionObserver((request, action) -> replacementEvents.add(action));
        var chosen = submitted.legalActions(0).stream().filter(PlayerAction.Discard.class::isInstance).findFirst().orElseThrow();
        submitted.submitAction(0, chosen);
        submitted.advance(.05);
        check(replacementEvents.equals(List.of(chosen)) && submittedEvents.size() == 1,
                "replacing the observer retained the old callback");
    }
    private static void bots() {
        var drawDriver = start();
        var drawBot = new AbstractStupidBot() {
            @Override protected PlayerAction pick(DecisionRequest request) {
                return request.legalActions().contains(PlayerAction.Draw.INSTANCE) ? PlayerAction.Draw.INSTANCE : null;
            }
        };
        drawDriver.replacePlayer(0, drawBot);
        var drawEvents = observe(drawDriver);
        drawDriver.advance(.05);
        check(drawEvents.size() == 1 && drawEvents.getFirst().action() instanceof PlayerAction.Draw,
                "bot draw must count as an operation");
        check(drawDriver.playerAt(0) == drawBot, "observer wrapped or replaced the bot controller");

        var passDriver = start();
        passDriver.submitAction(0, PlayerAction.Draw.INSTANCE);
        passDriver.advance(.05);
        var discard = passDriver.legalActions(0).stream().filter(PlayerAction.Discard.class::isInstance).findFirst().orElseThrow();
        // Restore a pending claim after a real discard, independently from the randomly dealt hands.
        var held = ((PlayerAction.Discard) discard).tile();
        var afterDiscard = passDriver.currentRound().discard(held, passDriver.rules());
        var base = passDriver.match();
        var match = new TheMahjongMatch(base.playerCount(), base.startingPoints(), base.targetPoints(), base.roundCount(),
                base.state(), base.tileSet(), base.ruleSet(), base.completedRounds(), afterDiscard);
        var phase = new MatchPhase.AwaitingClaims(Set.of(1), held, MatchPhase.ClaimSource.DISCARD);
        var passBot = new AbstractStupidBot() {
            @Override protected PlayerAction pick(DecisionRequest request) {
                return request.legalActions().contains(PlayerAction.Pass.INSTANCE) ? PlayerAction.Pass.INSTANCE : null;
            }
        };
        var rigged = new TheMahjongDriver(match,
                List.of(new MahjongHumanPlayer(), passBot, new MahjongHumanPlayer(), new MahjongHumanPlayer()), new Random(19),
                new TheMahjongDriver.Snapshot(phase, false, OptionalInt.empty()));
        var passEvents = observe(rigged);
        rigged.advance(.05);
        check(passEvents.size() == 1 && passEvents.getFirst().request().seat() == 1
                        && passEvents.getFirst().action() instanceof PlayerAction.Pass,
                "bot pass must count as an operation");
        check(rigged.playerAt(1) == passBot, "observer changed the pass bot identity");
    }
    private static void illegalActions() {
        var driver = start();
        var events = observe(driver);
        try {
            driver.submitAction(0, PlayerAction.Pass.INSTANCE);
            throw new AssertionError("illegal submitted action accepted");
        } catch (IllegalArgumentException expected) {}
        check(events.isEmpty(), "illegal submitted action notified observer");
        driver.replacePlayer(0, (request, delta) -> Optional.of(PlayerAction.Pass.INSTANCE));
        try {
            driver.advance(.05);
            throw new AssertionError("illegal controller action changed existing driver rejection");
        } catch (IllegalStateException expected) {}
        check(events.isEmpty(), "illegal controller action notified observer");
        driver.replacePlayer(0, new MahjongHumanPlayer());
        driver.advance(.05);
        check(events.isEmpty(), "absent action notified observer");
    }
    public static void main(String[] args) {
        humanAndSubmitted();
        bots();
        illegalActions();
        System.out.println("PASS: action observer preserves controllers and observes legal human/submitted/BOT decisions before execution");
    }
}
