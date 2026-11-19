package com.riichimahjong.fabric;

import com.themahjong.compat.DrawSettlement;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;

/** Dealer continuation and honba passed to the driver when accepting a result. */
public record RoundProgression(boolean renchan, int nextHonba) {
    public static RoundProgression next(TheMahjongDriver driver) {
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded ended)) {
            throw new IllegalStateException("Round progression requires a round result");
        }
        var round = driver.currentRound();
        if (driver.rules().sichuan()) {
            boolean repeat = driver.rules().bloodBattle() && driver.sichuanState().dealerContinues(round, driver.rules());
            return new RoundProgression(repeat, 0);
        }
        if (driver.bloodBattleState() != null) {
            var blood = driver.bloodBattleState();
            boolean repeat = blood.dealerContinues(round);
            boolean noWins = blood.wins().isEmpty();
            return new RoundProgression(repeat, noWins || repeat ? round.honba() + 1 : 0);
        }
        int dealer = round.dealerSeat();
        boolean draw = ended.winResults().isEmpty();
        boolean renchan = draw
                ? driver.abortiveDraw() || driver.rules().dealerTenpaiContinues() && DrawSettlement.inTenpai(round.players().get(dealer))
                : ended.winResults().stream().anyMatch(win -> win.pointDeltas().get(dealer) > 0);
        // Draws increase honba even when the dealer rotates; a non-dealer win resets it.
        return new RoundProgression(renchan, draw || renchan ? round.honba() + 1 : 0);
    }
}
