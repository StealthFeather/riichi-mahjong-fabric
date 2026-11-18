package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer.TableButton;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import com.themahjong.driver.TheMahjongDriver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.riichimahjong.fabric.RulesRegression.*;

public final class PostCallKanRegression {
    private static final TheMahjongTile CALLED = tile(1, 3);
    private static final TheMahjongTile SPARE = tile(0, 8);
    private static final List<TheMahjongTile> QUAD = Collections.nCopies(4, tile(1, 9));

    private static TheMahjongDriver fixture(boolean chi, boolean sichuan) {
        var hand = new ArrayList<>(QUAD);
        hand.add(SPARE);
        var melds = new ArrayList<TheMahjongMeld>();
        if (chi) {
            // The hand can chi the discard and already has a pon with its fourth tile.
            var olderPon = tile(0, 6);
            melds.add(new TheMahjongMeld.Pon(Collections.nCopies(3, olderPon), 0, 2, 0));
            hand.addAll(List.of(tile(1, 1), tile(1, 2), olderPon, tile(0, 2), tile(0, 4)));
        } else {
            // Choose pon over the available daiminkan and keep the fourth copy in hand.
            hand.addAll(Collections.nCopies(3, CALLED));
            hand.addAll(List.of(tile(1, 1), tile(1, 2), tile(0, 4), tile(0, 5), tile(0, 6)));
        }
        var caller = new TheMahjongPlayer(25000, TheMahjongPlayer.RiichiState.NONE, false,
                hand, melds, List.of(), List.of());
        var other = List.of(tile(0, 1), tile(0, 2), tile(0, 3), tile(0, 4), tile(0, 5), tile(0, 7),
                tile(0, 9), tile(1, 1), tile(1, 2), tile(1, 4), tile(1, 5), tile(1, 7), tile(1, 8));
        var source = new ArrayList<>(other);
        source.add(CALLED);
        if (!sichuan) source.set(0, tile(4, 2)); // The riichi fixture starts with a drawn green dragon.
        var players = List.of(player(source), caller, player(other), player(other));
        return sichuan ? SichuanDriverRegression.rig(players, CALLED)
                : driver(players, Collections.nCopies(20, SPARE));
    }

    private static void rejectedByRound(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("round accepted a self-kan before the caller drew");
    }

    private static void play(boolean chi, boolean sichuan) {
        var driver = fixture(chi, sichuan);
        act(driver, 0, new PlayerAction.Discard(CALLED));
        var claims = driver.legalActions(1);
        if (!chi) check(claims.stream().anyMatch(PlayerAction.Daiminkan.class::isInstance),
                "player holding three copies must still be able to kan the discard directly");
        var call = claims.stream().filter(a -> chi ? a instanceof PlayerAction.Chi : a instanceof PlayerAction.Pon)
                .findFirst().orElseThrow();
        act(driver, 1, call);
        passClaims(driver);
        var round = driver.currentRound();
        check(driver.currentPhase().equals(new MatchPhase.AwaitingDiscard(1))
                && round.activeTile() instanceof TheMahjongRound.ActiveTile.None, "call did not enter the discard turn");
        var pon = round.players().get(1).melds().stream().filter(TheMahjongMeld.Pon.class::isInstance)
                .map(TheMahjongMeld.Pon.class::cast).findFirst().orElseThrow();
        var ankan = new PlayerAction.Ankan(QUAD);
        var kakan = new PlayerAction.Kakan(pon, pon.tiles().getFirst());
        var legal = driver.legalActions(1);
        check(!legal.contains(ankan) && !legal.contains(kakan), "self-kan offered immediately after " + call);
        var kanButtons = List.of(new TableButton.SubmitAction(ankan), new TableButton.SubmitAction(kakan));
        check(Collections.disjoint(MahjongTableHumanPlayer.tableButtons(driver, 1, false), kanButtons),
                "the table offered a kan button immediately after " + call);
        if (!sichuan) {
            rejectedByRound(() -> round.declareAnkan(QUAD, driver.rules()));
            rejectedByRound(() -> round.declareKakan(pon, kakan.addedTile(), driver.rules()));
        }

        act(driver, 1, new PlayerAction.Discard(SPARE));
        passClaims(driver);
        for (int seat : List.of(2, 3, 0)) {
            act(driver, seat, PlayerAction.Draw.INSTANCE);
            act(driver, seat, new PlayerAction.Discard(SPARE));
            passClaims(driver);
        }
        act(driver, 1, PlayerAction.Draw.INSTANCE);
        check(driver.legalActions(1).containsAll(List.of(ankan, kakan)), "own draw did not restore both kan choices");
        check(MahjongTableHumanPlayer.tableButtons(driver, 1, false).containsAll(kanButtons),
                "the table did not offer both kan buttons after the next draw");
        act(driver, 1, ankan);
        act(driver, 1, PlayerAction.Draw.INSTANCE);
        act(driver, 1, kakan);
        passClaims(driver);
        check(driver.currentPhase().equals(new MatchPhase.AwaitingDraw(1, true)),
                "kakan after a replacement draw did not award another replacement tile");
    }

    public static void main(String[] args) {
        play(true, false);
        play(false, false);
        play(false, true);
        System.out.println("PASS: riichi chi/pon and Sichuan pon require a discard before self-kan; normal and replacement draws allow kan");
    }
}
