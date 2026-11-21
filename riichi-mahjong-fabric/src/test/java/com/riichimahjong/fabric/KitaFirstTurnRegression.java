package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.variants.Jokers;
import com.themahjong.variants.VariantRules;
import com.themahjong.yaku.Yakuman;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

public final class KitaFirstTurnRegression {
    private static final TheMahjongTile NORTH = tile(3, 4);
    private static TheMahjongTile pair(int seat) { return tile(4, seat + 1); }

    private static TheMahjongDriver fixture(TheMahjongMatch base, int actor, TheMahjongTile draw, TheMahjongTile replacement) {
        var players = new ArrayList<TheMahjongPlayer>();
        for (int seat = 0; seat < 3; seat++) {
            var hand = new ArrayList<TheMahjongTile>();
            for (int rank = 1; rank <= 9; rank++) hand.add(tile(1 + seat % 2, rank));
            hand.addAll(Collections.nCopies(3, tile(3, seat + 1)));
            hand.add(pair(seat));
            if (seat == actor) hand.add(draw);
            var river = seat < actor ? List.of(new TheMahjongDiscard(tile(0, 9), false)) : List.<TheMahjongDiscard>of();
            players.add(new TheMahjongPlayer(35000, TheMahjongPlayer.RiichiState.NONE, false, hand, List.of(), river, List.of()));
        }
        var indicators = Collections.nCopies(5, tile(1, 7));
        var round = new TheMahjongRound(TheMahjongTile.Wind.EAST, 1, 0, 0, 0, TheMahjongRound.State.TURN, actor, -1,
                TheMahjongRound.ActiveTile.drawn(draw), Collections.nCopies(12, pair(1)), Collections.nCopies(8, replacement),
                indicators, indicators, 1, players, List.of());
        var match = new TheMahjongMatch(3, 35000, 40000, 2, TheMahjongMatch.State.IN_ROUND, base.tileSet(), base.ruleSet(), List.of(), round);
        return new TheMahjongDriver(match, List.of(new MahjongHumanPlayer(), new MahjongHumanPlayer(), new MahjongHumanPlayer()),
                new Random(91), new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(actor), false, OptionalInt.empty()));
    }

    private static void extractAndDraw(TheMahjongDriver driver, int seat) {
        act(driver, seat, new PlayerAction.DeclareKita(NORTH));
        passClaims(driver);
        act(driver, seat, PlayerAction.Draw.INSTANCE);
        check(driver.currentRound().players().get(seat).kitaCount() == 1, "extraction was not recorded");
    }

    private static void checkFirstTurn(TheMahjongDriver driver, int seat, boolean uninterrupted) {
        var win = driver.legalActions(seat).stream().filter(PlayerAction.DeclareTsumo.class::isInstance)
                .map(PlayerAction.DeclareTsumo.class::cast).findFirst().orElseThrow();
        check(win.result().yakuman().contains(Yakuman.TENHOU) == (uninterrupted && seat == 0), "incorrect tenhou");
        check(win.result().yakuman().contains(Yakuman.CHIHOU) == (uninterrupted && seat != 0), "incorrect chihou");
        var round = driver.currentRound();
        check(round.declareRiichi(driver.rules()).players().get(seat).doubleRiichi() == uninterrupted, "incorrect direct riichi");
        var drawn = ((TheMahjongRound.ActiveTile.Drawn) round.activeTile()).tile();
        act(driver, seat, new PlayerAction.DiscardWithRiichi(drawn));
        passClaims(driver);
        var player = driver.currentRound().players().get(seat);
        check(player.riichi() && player.doubleRiichi() == uninterrupted && player.points() == 34000,
                "incorrect accepted riichi type or deposit");
    }

    public static void main(String[] args) {
        for (var standard : List.of(TheMahjongMatch.defaultMahjongSoulSanma(), TheMahjongMatch.defaultTenhouSanma())) {
            for (int seat : List.of(0, 1)) {
                checkFirstTurn(fixture(standard, seat, pair(seat), pair(seat)), seat, true);
                var afterKita = fixture(standard, seat, NORTH, pair(seat));
                extractAndDraw(afterKita, seat);
                checkFirstTurn(afterKita, seat, false);
            }
            var other = fixture(standard, 0, NORTH, pair(0));
            extractAndDraw(other, 0);
            act(other, 0, new PlayerAction.Discard(pair(0)));
            passClaims(other);
            act(other, 1, PlayerAction.Draw.INSTANCE);
            checkFirstTurn(other, 1, false);
        }
        // Imported positions may contain a drawable joker and take a different tsumo scoring path.
        var variants = VariantRules.withOptions(TheMahjongMatch.defaultMahjongSoulSanma(), false, true, true);
        var joker = fixture(variants, 0, NORTH, Jokers.TILE);
        extractAndDraw(joker, 0);
        var win = joker.legalActions(0).stream().filter(PlayerAction.DeclareTsumo.class::isInstance)
                .map(PlayerAction.DeclareTsumo.class::cast).findFirst().orElseThrow();
        act(joker, 0, win);
        check(!joker.bloodBattleState().wins().getFirst().result().yakuman().contains(Yakuman.TENHOU),
                "kita and a winning joker received tenhou in blood battle");
        System.out.println("PASS: first-turn sanma wins and double riichi require no prior kita by any seat");
    }
}
