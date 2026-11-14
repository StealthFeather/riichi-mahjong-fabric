package com.riichimahjong.fabric;

import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer.TableButton;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.yaku.Furiten;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Exercise the rule actions and the human button/hand path with furiten hands. */
public final class FuritenRiichiRegression {
    private static final TheMahjongTile WHITE = tile(4,1), GREEN = tile(4,2);
    private record Rig(TheMahjongDriver driver, MahjongTableHumanPlayer human) {}

    private static TheMahjongPlayer ready(int count, boolean riverFuriten, boolean temporaryFuriten, int points) {
        // The reported four-player hand was 11123567m678s55z, with a 4m in its river.
        var hand = new ArrayList<>(count==4 ? List.of(tile(0,1),tile(0,1),tile(0,1),tile(0,2),tile(0,3),
                tile(0,5),tile(0,6),tile(0,7),tile(2,6),tile(2,7),tile(2,8),WHITE,WHITE) : waiting(1));
        hand.add(GREEN);
        var river = count==4 ? List.of(tile(4,3),tile(1,9),tile(2,1),tile(1,5),tile(1,3),tile(2,5),
                tile(2,3),tile(2,8),tile(0,4),tile(0,9),tile(0,8),tile(2,3),
                tile(2,9),tile(0,6),tile(1,2),tile(0,3),tile(2,1),tile(0,6)).stream()
                .map(t -> new TheMahjongDiscard(t,false)).toList() : List.of(new TheMahjongDiscard(WHITE,false));
        return new TheMahjongPlayer(points, TheMahjongPlayer.RiichiState.NONE, false,
                hand, List.of(), riverFuriten ? river : List.of(),
                temporaryFuriten ? List.of(WHITE) : List.of());
    }

    private static Rig rig(int count, TheMahjongPlayer first, int wallSize, boolean calledRiver) {
        var players = new ArrayList<TheMahjongPlayer>();
        players.add(first);
        for (int seat=1; seat<count; seat++) players.add(player(waiting(2)));
        if (calledRiver) {
            var caller = players.get(1);
            int calledIndex = count==4 ? 8 : 0;
            var pon = new TheMahjongMeld.Pon(Collections.nCopies(3,first.discards().get(calledIndex).tile()),2,0,calledIndex);
            players.set(1,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,
                    caller.currentHand().subList(3,13),List.of(pon),List.of(),List.of()));
        }
        var wall = new ArrayList<>(Collections.nCopies(wallSize,GREEN));
        if (wallSize>=count) wall.set(count-1,WHITE);
        var round = driver(players,wall).currentRound();
        var humans = new ArrayList<MahjongPlayerInterface>();
        for (int seat=0; seat<count; seat++) {
            var human = new MahjongTableHumanPlayer();
            human.setOccupant(new UUID(0,seat+1));
            humans.add(human);
        }
        var match = new TheMahjongMatch(count,25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                TheMahjongTileSet.standardRiichi(false),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        return new Rig(new TheMahjongDriver(match,humans,new Random(1),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty())),
                (MahjongTableHumanPlayer)humans.getFirst());
    }

    private static boolean hasRiichiButton(Rig rig) {
        return MahjongTableHumanPlayer.tableButtons(rig.driver,0,rig.human.riichiPending())
                .stream().anyMatch(b -> b instanceof TableButton.RiichiToggle);
    }

    private static void declareThroughHumanControls(Rig rig) {
        var driver = rig.driver;
        check(driver.legalActions(0).contains(new PlayerAction.DiscardWithRiichi(GREEN)),
                "furiten removed a legal riichi discard");
        check(hasRiichiButton(rig),"furiten hid the human riichi button");
        check(rig.human.selectTableButton(new TableButton.RiichiToggle(false),driver,0,null),
                "furiten riichi button was not selectable");
        var order = driver.currentRound().handDisplayOrder(0);
        rig.human.onCuteClick(new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_HAND,(short)order.indexOf(GREEN)),
                driver,0,null);
        check(rig.human.hasQueued(),"selecting the riichi discard did not queue an action");
        driver.advance(.05);
        passClaims(driver);
        var declared = driver.currentRound().players().getFirst();
        check(declared.riichi(),"human furiten riichi did not take effect");
        check(declared.points()==24000 && driver.currentRound().riichiSticks()==1,
                "furiten riichi did not pay exactly one deposit");
        check(!declared.temporaryFuriten(),"own discard did not clear temporary furiten");
    }

    private static void riverFuriten(int count, boolean calledRiver) {
        var rig = rig(count,ready(count,true,false,25000),20,calledRiver);
        declareThroughHumanControls(rig);
        var driver = rig.driver;
        check(Furiten.isFuriten(driver.currentRound().players().getFirst()),
                "riichi erased discard-river furiten");
        check(!Furiten.canRon(driver.currentRound().players().getFirst(),WHITE),
                "furiten riichi incorrectly permits ron");
        // A real opponent discard must offer no ron action or ron button to seat 0.
        act(driver,1,PlayerAction.Draw.INSTANCE);
        act(driver,1,new PlayerAction.Discard(WHITE));
        check(driver.legalActions(0).stream().noneMatch(a -> a instanceof PlayerAction.DeclareRon),
                "driver offered ron to a furiten riichi player");
        check(MahjongTableHumanPlayer.tableButtons(driver,0,false).stream()
                        .noneMatch(b -> b instanceof TableButton.SubmitAction s && s.action() instanceof PlayerAction.DeclareRon),
                "human controls offered a forbidden furiten ron");
        passClaims(driver);
        for (int seat=2; seat<count; seat++) {
            act(driver,seat,PlayerAction.Draw.INSTANCE);
            act(driver,seat,new PlayerAction.Discard(GREEN));
            passClaims(driver);
        }
        act(driver,0,PlayerAction.Draw.INSTANCE);
        var tsumo = driver.legalActions(0).stream().filter(a -> a instanceof PlayerAction.DeclareTsumo)
                .findFirst().orElseThrow(() -> new AssertionError("furiten riichi lost a legal tsumo"));
        act(driver,0,tsumo);
        check(driver.currentPhase() instanceof MatchPhase.RoundEnded ended && !ended.winResults().isEmpty(),
                "furiten riichi tsumo was not accepted");
    }

    private static void unavailable(Rig rig, String reason) {
        check(rig.driver.legalActions(0).stream().noneMatch(a -> a instanceof PlayerAction.DiscardWithRiichi),
                reason+" unexpectedly offers a riichi action");
        check(!hasRiichiButton(rig),reason+" unexpectedly shows a riichi button");
    }

    private static void otherRequirements(int count) {
        check(hasRiichiButton(rig(count,ready(count,true,false,25000),4,false)),
                "four remaining live-wall tiles should still allow riichi");
        unavailable(rig(count,ready(count,true,false,25000),3,false),"fewer than four wall tiles");
        unavailable(rig(count,ready(count,true,false,999),20,false),"fewer than 1000 points");
        var closed = ready(count,true,false,25000);
        var chi = new TheMahjongMeld.Chi(waiting(1).subList(0,3),0,count-1,0);
        var openHand = new ArrayList<>(waiting(1).subList(3,13));
        openHand.add(GREEN);
        var open = new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,
                openHand,List.of(chi),closed.discards(),List.of());
        unavailable(rig(count,open,20,false),"open hand");
        var noten = player(List.of(tile(1,1),tile(1,3),tile(1,5),tile(1,7),tile(1,9),
                tile(2,1),tile(2,3),tile(2,5),tile(2,7),tile(2,9),tile(3,1),tile(3,2),tile(3,3),GREEN));
        unavailable(rig(count,noten,20,false),"no discard leaves tenpai");
    }

    public static void main(String[] args) {
        for (int count : new int[]{3,4}) {
            riverFuriten(count,false);
            riverFuriten(count,true);
            declareThroughHumanControls(rig(count,ready(count,false,true,25000),20,false));
            otherRequirements(count);
        }
        System.out.println("PASS: 3/4-player furiten riichi actions, human controls, deposits, ron exclusion and tsumo");
        System.out.println("PASS: riichi button still requires tenpai, a closed hand, 1000 points and four live-wall tiles");
    }
}
