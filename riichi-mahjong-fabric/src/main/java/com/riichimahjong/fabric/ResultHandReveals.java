package com.riichimahjong.fabric;

import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Explicit public-display choices; the table authorizes each player's own seat before calling show. */
public final class ResultHandReveals {
    // Final-deposit settlement can replace the round object while retaining the same last hand.
    private record Hand(TheMahjongTile.Wind wind, int number, int honba, int dealer) {
        static Hand of(TheMahjongRound round) {
            return new Hand(round.roundWind(), round.handNumber(), round.honba(), round.dealerSeat());
        }
    }

    private TheMahjongDriver driver;
    private Hand hand;
    private final Set<Integer> shown = new HashSet<>();

    public static boolean canReveal(MatchPhase phase, TheMahjongRound round) {
        return round != null && (phase instanceof MatchPhase.RoundEnded
                || phase instanceof MatchPhase.MatchEnded && round.state() == TheMahjongRound.State.ENDED);
    }

    public static boolean canReveal(TheMahjongDriver driver) {
        return driver != null && canReveal(driver.currentPhase(), driver.match().currentRound().orElse(null));
    }

    private boolean bind(TheMahjongDriver current) {
        if (!canReveal(current)) {
            clear();
            return false;
        }
        var currentHand = Hand.of(current.currentRound());
        if (driver != current || !currentHand.equals(hand)) shown.clear();
        driver = current;
        hand = currentHand;
        return true;
    }

    public Set<Integer> shownSeats(TheMahjongDriver current) {
        return bind(current) ? Set.copyOf(shown) : Set.of();
    }

    public boolean show(TheMahjongDriver current, TheMahjongRound expectedRound, int seat) {
        if (!canReveal(current) || current.currentRound() != expectedRound
                || seat < 0 || seat >= expectedRound.players().size()) return false;
        return bind(current) && shown.add(seat);
    }

    public void clear() {
        driver = null;
        hand = null;
        shown.clear();
    }

    public void write(ValueOutput out, TheMahjongDriver current) {
        out.putIntArray("Seats", shownSeats(current).stream().mapToInt(Integer::intValue).sorted().toArray());
    }

    public void read(ValueInput in, TheMahjongDriver current) {
        clear();
        if (!bind(current)) return;
        for (int seat : in.getIntArray("Seats").orElseGet(() -> new int[0])) {
            if (seat >= 0 && seat < current.match().playerCount()) shown.add(seat);
        }
    }
}
