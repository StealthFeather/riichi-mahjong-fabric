package com.riichimahjong.fabric;

import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer.TableButton;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.TheMahjongDriver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static com.riichimahjong.fabric.RulesRegression.*;

public final class DiscardConfirmationRegression {
    private record Turn(TheMahjongDriver driver, MahjongTableHumanPlayer human) {
        private void click(int slot, boolean confirm) {
            human.onCuteClick(new InteractKey.SeatSlot((byte) 0, InteractKey.SeatSlot.AREA_HAND, (short) slot),
                    driver, 0, null, confirm);
        }
        private void selected(int slot) {
            check(human.selectedDiscardIndex(driver) == slot && !human.hasQueued(),
                    "unexpected pending discard or queued action for slot " + slot);
        }
    }

    private static Turn turn() {
        var first = new ArrayList<>(waiting(0));
        first.add(tile(4, 2));
        var driver = driver(List.of(player(first), player(waiting(1)), player(waiting(2)), player(waiting(0))),
                Collections.nCopies(28, tile(4, 2)));
        var human = new MahjongTableHumanPlayer();
        human.setOccupant(UUID.randomUUID());
        driver.replacePlayer(0, human);
        return new Turn(driver, human);
    }

    private static void duplicatesAndConfirm() {
        var turn = turn();
        var round = turn.driver.currentRound();
        var hand = round.handDisplayOrder(0);
        var east = tile(3, 1);
        int first = hand.indexOf(east);
        int last = hand.lastIndexOf(east);
        check(first < last, "fixture needs distinguishable slots containing equal tiles");
        var display = new HandDisplayState();
        display.update(round, 100);
        for (int slot : List.of(first, last)) {
            check(display.canonicalIndex(0, slot, round) == slot && display.displayedIndex(0, slot, round) == slot,
                    "duplicate copy changed position during display-to-hand conversion");
            turn.click(slot, true);
            turn.selected(slot);
        }
        turn.click(-1, true);
        turn.click(99, true);
        turn.human.onTableRightClick(turn.driver, turn.driver.currentPhase(), 0, null);
        turn.selected(last);
        check(round.players().getFirst().discards().isEmpty(), "a preview discarded a tile");
        turn.click(last, true);
        turn.click(first, true);
        check(turn.human.hasQueued() && turn.human.selectedDiscardIndex(turn.driver) == -1,
                "confirming should queue once and clear the preview");
        turn.driver.advance(.05);
        var river = turn.driver.currentRound().players().getFirst().discards();
        check(river.size() == 1 && river.getFirst().tile().equals(east), "confirmed discard missed the river");
    }

    private static void staleClicks() {
        var turn = turn();
        turn.click(0, true);
        turn.human.setOccupant(UUID.randomUUID());
        turn.selected(-1);
        turn.click(0, true);
        turn.selected(0);

        // A replacement match can have the same seat, tile index and exact round snapshot.
        var players = new ArrayList<MahjongPlayerInterface>();
        for (int seat = 0; seat < 4; seat++) players.add(turn.driver.playerAt(seat));
        var restored = new Turn(new TheMahjongDriver(turn.driver.match(), players, new Random(2),
                turn.driver.snapshot()), turn.human);
        restored.selected(-1);
        restored.click(0, true);
        restored.selected(0);
        int drawn = restored.driver.currentRound().handDisplayOrder(0).size() - 1;
        var toggle = new TableButton.RiichiToggle(false);
        check(restored.human.selectTableButton(toggle, restored.driver, 0, null), "fixture cannot declare riichi");
        restored.selected(-1);
        restored.click(drawn, true);
        restored.selected(drawn);
        restored.click(drawn, true);
        restored.driver.advance(.05);
        check(restored.driver.currentRound().players().getFirst().riichi(), "confirmed riichi did not take effect");
        restored.selected(-1);
    }

    private static void directDiscard() {
        var turn = turn();
        turn.click(0, false);
        check(turn.human.hasQueued() && turn.human.selectedDiscardIndex(turn.driver) == -1,
                "direct mode required a second click");
        turn.driver.advance(.05);
        check(turn.driver.currentRound().players().getFirst().discards().size() == 1,
                "direct mode did not discard");
    }

    public static void main(String[] args) {
        directDiscard();
        duplicatesAndConfirm();
        staleClicks();
        System.out.println("PASS: direct discard, exact duplicate-copy preview and confirmation, missed clicks, occupant and match replacement, riichi confirmation");
    }
}
