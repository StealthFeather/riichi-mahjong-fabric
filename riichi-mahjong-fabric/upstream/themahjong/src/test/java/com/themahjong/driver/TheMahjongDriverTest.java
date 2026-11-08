package com.themahjong.driver;

import com.themahjong.TheMahjongMatch;
import com.themahjong.driver.bots.StupidActiveBot;
import com.themahjong.driver.bots.StupidPassiveBot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TheMahjongDriverTest {
    private static final int TICK_BUDGET = 4000;

    private static List<MahjongPlayerInterface> bots(int n, java.util.function.Supplier<MahjongPlayerInterface> f) {
        List<MahjongPlayerInterface> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(f.get());
        return out;
    }

    private static TheMahjongDriver newDriver(List<MahjongPlayerInterface> players) {
        TheMahjongMatch match = TheMahjongMatch.defaultTenhou()
                .withPlayerCount(players.size())
                .validate();
        return new TheMahjongDriver(match, players, new Random(42));
    }

    private static MatchPhase pumpUntilTerminal(TheMahjongDriver d) {
        for (int i = 0; i < TICK_BUDGET; i++) {
            d.advance(0.0);
            MatchPhase p = d.currentPhase();
            if (p instanceof MatchPhase.RoundEnded
                    || p instanceof MatchPhase.MatchEnded
                    || p instanceof MatchPhase.BetweenRounds) {
                return p;
            }
        }
        throw new AssertionError("driver did not terminate within " + TICK_BUDGET + " ticks; phase=" + d.currentPhase());
    }

    @Test
    void notStartedHasNoLegalActions() {
        TheMahjongDriver d = newDriver(bots(4, StupidPassiveBot::new));
        for (int s = 0; s < 4; s++) {
            assertTrue(d.legalActions(s).isEmpty(), "seat " + s);
        }
        assertInstanceOf(MatchPhase.NotStarted.class, d.currentPhase());
    }

    @Test
    void startMatchEntersAwaitingDraw() {
        TheMahjongDriver d = newDriver(bots(4, StupidPassiveBot::new));
        d.startMatch();
        assertInstanceOf(MatchPhase.AwaitingDraw.class, d.currentPhase());
        MatchPhase.AwaitingDraw ad = (MatchPhase.AwaitingDraw) d.currentPhase();
        assertEquals(d.currentRound().dealerSeat(), ad.seat());
        assertFalse(ad.rinshan());
        assertEquals(List.of(PlayerAction.Draw.INSTANCE), d.legalActions(ad.seat()));
        for (int s = 0; s < 4; s++) {
            if (s == ad.seat()) continue;
            assertTrue(d.legalActions(s).isEmpty());
        }
    }

    @Test
    void allPassiveBotsReachRoundEnd() {
        TheMahjongDriver d = newDriver(bots(4, StupidPassiveBot::new));
        d.startMatch();
        MatchPhase terminal = pumpUntilTerminal(d);

        assertInstanceOf(MatchPhase.RoundEnded.class, terminal);
    }

    @Test
    void allActiveBotsReachRoundEnd() {
        TheMahjongDriver d = newDriver(bots(4, StupidActiveBot::new));
        d.startMatch();
        MatchPhase terminal = pumpUntilTerminal(d);
        assertInstanceOf(MatchPhase.RoundEnded.class, terminal);
    }

    @Test
    void mixedBotsReachRoundEnd() {
        List<MahjongPlayerInterface> players = List.of(
                new StupidActiveBot(), new StupidPassiveBot(),
                new StupidActiveBot(), new StupidPassiveBot());
        TheMahjongDriver d = newDriver(players);
        d.startMatch();
        MatchPhase terminal = pumpUntilTerminal(d);
        assertInstanceOf(MatchPhase.RoundEnded.class, terminal);
    }

    @Test
    void replacePlayerHotSwaps() {
        List<MahjongPlayerInterface> players = bots(4, StupidActiveBot::new);
        TheMahjongDriver d = newDriver(players);
        d.startMatch();
        d.advance(0.0);
        d.replacePlayer(0, new StupidPassiveBot());
        assertInstanceOf(StupidPassiveBot.class, d.playerAt(0));

        MatchPhase terminal = pumpUntilTerminal(d);
        assertInstanceOf(MatchPhase.RoundEnded.class, terminal);
    }

    @Test
    void submitActionFromHumanPlayer() {
        MahjongHumanPlayer human = new MahjongHumanPlayer();
        human.setAutoDrawAfterSeconds(0.0);
        human.setAutoDiscardAfterSeconds(0.0);
        List<MahjongPlayerInterface> players = new ArrayList<>();
        players.add(human);
        for (int i = 1; i < 4; i++) players.add(new StupidPassiveBot());
        TheMahjongDriver d = newDriver(players);
        d.startMatch();

        MatchPhase terminal = pumpUntilTerminal(d);
        assertInstanceOf(MatchPhase.RoundEnded.class, terminal);
    }

    @Test
    void advanceRoundProgressesMatch() {
        TheMahjongDriver d = newDriver(bots(4, StupidActiveBot::new));
        d.startMatch();
        MatchPhase terminal = pumpUntilTerminal(d);
        assertInstanceOf(MatchPhase.RoundEnded.class, terminal);

        d.advanceRound(false, 0);
        assertTrue(d.currentPhase() instanceof MatchPhase.AwaitingDraw
                || d.currentPhase() instanceof MatchPhase.MatchEnded);
    }
}
