package com.riichimahjong.fabric;

import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer.TableButton;
import com.themahjong.*;
import com.themahjong.driver.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** No fake server player is needed for control decisions that never touch inventory. */
public final class WinSkipRegression {
    private record Rig(TheMahjongDriver driver,List<MahjongTableHumanPlayer> humans) {}
    private static Rig rig(TheMahjongRound round) {
        var humans=new ArrayList<MahjongTableHumanPlayer>();
        for(int seat=0;seat<round.players().size();seat++){
            var human=new MahjongTableHumanPlayer();human.setOccupant(new UUID(0,seat+1));humans.add(human);
        }
        var match=new TheMahjongMatch(round.players().size(),25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                TheMahjongTileSet.standardRiichi(false),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        return new Rig(new TheMahjongDriver(match,new ArrayList<MahjongPlayerInterface>(humans),new Random(1),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty())),humans);
    }
    private static Rig tsumoRig() {
        var hand=new ArrayList<>(waiting(0));hand.add(tile(4,1));
        var base=driver(List.of(player(hand),player(waiting(1)),player(waiting(2)),player(waiting(0))),Collections.nCopies(20,tile(4,2))).currentRound();
        var round=new TheMahjongRound(base.roundWind(),base.handNumber(),base.honba(),base.riichiSticks(),base.dealerSeat(),
                base.state(),base.currentTurnSeat(),base.claimSourceSeat().orElse(-1),TheMahjongRound.ActiveTile.drawn(tile(4,1)),
                base.liveWall(),base.rinshanTiles(),base.doraIndicators(),base.uraDoraIndicators(),base.revealedDoraCount(),base.players(),List.of());
        var rig=rig(round);
        check(rig.driver.legalActions(0).stream().anyMatch(a->a instanceof PlayerAction.DeclareTsumo),"tsumo fixture lacks a legal win");
        check(!rig.driver.legalActions(0).contains(PlayerAction.Pass.INSTANCE),"tsumo fixture unexpectedly has a rule-level pass");
        return rig;
    }
    private static Rig ronRig() {
        var rig=tsumoRig();act(rig.driver,0,new PlayerAction.Discard(tile(4,1)));
        check(rig.driver.currentPhase() instanceof MatchPhase.AwaitingClaims,"ron fixture lacks a claim phase");
        check(rig.driver.legalActions(1).stream().anyMatch(a->a instanceof PlayerAction.DeclareRon),"ron fixture lacks a legal win");
        return rig;
    }
    private static Rig kakanRig() {
        var hand=new ArrayList<TheMahjongTile>();for(int rank=1;rank<=9;rank++)hand.add(tile(0,rank));
        hand.add(tile(3,1));hand.add(tile(4,1));
        var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,tile(4,1)),0,3,0);
        var source=new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(pon),List.of(),List.of());
        var base=driver(List.of(source,player(waiting(1)),player(waiting(2)),player(waiting(0))),Collections.nCopies(20,tile(4,2))).currentRound();
        return rig(base);
    }
    private static Rig chankanRig() {
        var rig=kakanRig();
        var kakan=rig.driver.legalActions(0).stream().filter(a->a instanceof PlayerAction.Kakan).findFirst().orElseThrow();
        act(rig.driver,0,kakan);
        check(rig.driver.legalActions(1).stream().anyMatch(a->a instanceof PlayerAction.DeclareChankan),"chankan fixture lacks a legal win");
        return rig;
    }
    private static Rig callRig() {
        var source=new ArrayList<>(waiting(0));source.add(tile(0,5));
        var caller=List.of(tile(0,3),tile(0,4),tile(0,5),tile(0,5),tile(0,5),
                tile(1,1),tile(1,2),tile(1,4),tile(1,6),tile(2,2),tile(2,5),tile(3,2),tile(4,2));
        var rig=rig(driver(List.of(player(source),player(caller),player(waiting(2)),player(waiting(0))),
                Collections.nCopies(20,tile(4,2))).currentRound());
        act(rig.driver,0,new PlayerAction.Discard(tile(0,5)));
        var legal=rig.driver.legalActions(1);
        check(legal.stream().anyMatch(a->a instanceof PlayerAction.Chi)
                &&legal.stream().anyMatch(a->a instanceof PlayerAction.Pon)
                &&legal.stream().anyMatch(a->a instanceof PlayerAction.Daiminkan)
                &&legal.stream().noneMatch(a->a instanceof PlayerAction.DeclareRon),"chi/pon/open-kan fixture is invalid");
        return rig;
    }
    private static Rig ankanRig() {
        var hand=List.of(tile(0,1),tile(0,1),tile(0,1),tile(0,1),tile(0,2),tile(0,3),tile(0,4),
                tile(1,2),tile(1,3),tile(1,4),tile(2,5),tile(2,6),tile(3,1),tile(4,2));
        var rig=rig(driver(List.of(player(hand),player(waiting(1)),player(waiting(2)),player(waiting(0))),
                Collections.nCopies(20,tile(4,2))).currentRound());
        check(rig.driver.legalActions(0).stream().anyMatch(a->a instanceof PlayerAction.Ankan),"concealed-kan fixture is invalid");
        return rig;
    }
    private static List<TableButton> buttons(Rig rig,int seat) {
        return MahjongTableHumanPlayer.tableButtons(rig.driver,seat,rig.humans.get(seat).riichiPending());
    }
    private static <T extends TableButton> T button(Rig rig,int seat,Class<T> type) {
        return buttons(rig,seat).stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
    private static TableButton.SubmitAction pass() {return new TableButton.SubmitAction(PlayerAction.Pass.INSTANCE);}
    private static boolean select(Rig rig,int seat,TableButton button) {
        return rig.humans.get(seat).selectTableButton(button,rig.driver,seat,null);
    }
    private static void assertTableMissUnchanged(Rig rig,int seat) {
        var human=rig.humans.get(seat);var before=buttons(rig,seat);
        var round=rig.driver.currentRound();var phase=rig.driver.currentPhase();
        var legal=List.copyOf(rig.driver.legalActions(seat));var riichi=human.riichiPending();
        check(!human.hasQueued(),"miss fixture already has a queued action");
        for(int repeat=0;repeat<3;repeat++) {
            human.onTableRightClick(rig.driver,rig.driver.currentPhase(),seat,null);
            check(!human.hasQueued(),"table miss queued a pass, discard or button action");
            check(buttons(rig,seat).equals(before),"table miss opened/closed a prompt or changed buttons");
            check(human.riichiPending()==riichi,"table miss changed riichi selection");
            rig.driver.advance(.05);
            check(rig.driver.currentRound()==round&&rig.driver.currentPhase().equals(phase),"table miss advanced the round or phase");
            check(rig.driver.legalActions(seat).equals(legal),"table miss changed legal actions");
        }
    }
    private static void missedButtons() {
        for(var type:List.of(PlayerAction.Chi.class,PlayerAction.Pon.class,PlayerAction.Daiminkan.class)) {
            var rig=callRig();assertTableMissUnchanged(rig,1);
            var choice=buttons(rig,1).stream().filter(b->b instanceof TableButton.SubmitAction s&&type.isInstance(s.action())).findFirst().orElseThrow();
            check(select(rig,1,choice)&&rig.humans.get(1).hasQueued(),"explicit call button stopped working after a miss: "+type.getSimpleName());
        }
        for(var rig:List.of(ronRig(),chankanRig())) {
            assertTableMissUnchanged(rig,1);
            var win=buttons(rig,1).stream().filter(b->b instanceof TableButton.SubmitAction s
                    &&(s.action() instanceof PlayerAction.DeclareRon||s.action() instanceof PlayerAction.DeclareChankan)).findFirst().orElseThrow();
            check(select(rig,1,win)&&rig.humans.get(1).hasQueued(),"explicit claim-win button stopped working after a miss");
        }
        for(var rig:List.of(tsumoRig(),ankanRig(),kakanRig())) {
            assertTableMissUnchanged(rig,0);
            var choice=buttons(rig,0).stream().filter(b->b instanceof TableButton.SubmitAction s
                    &&(s.action() instanceof PlayerAction.DeclareTsumo||s.action() instanceof PlayerAction.Ankan||s.action() instanceof PlayerAction.Kakan)).findFirst().orElseThrow();
            check(select(rig,0,choice)&&rig.humans.get(0).hasQueued(),"explicit tsumo/self-kan button stopped working after a miss");
        }
        var rig=tsumoRig();
        check(select(rig,0,button(rig,0,TableButton.RiichiToggle.class)),"riichi fixture lacks a toggle");
        assertTableMissUnchanged(rig,0);
        assertTableMissUnchanged(rig,1);
    }
    private static void assertPrompt(Rig rig,int seat) {
        var visible=buttons(rig,seat);
        check(visible.size()==2&&visible.get(0) instanceof TableButton.ConfirmSkip&&visible.get(1) instanceof TableButton.CancelSkip,
                "confirmation must show exactly confirm and return");
        check(!rig.humans.get(seat).hasQueued(),"opening confirmation submitted a pass");
    }
    private static void claimConfirmation() {
        for(var rig:List.of(ronRig(),chankanRig())) {
            check(select(rig,1,pass()),"first skip was not handled");assertPrompt(rig,1);
            var first=button(rig,1,TableButton.ConfirmSkip.class);
            assertTableMissUnchanged(rig,1);
            assertPrompt(rig,1);
            check(button(rig,1,TableButton.ConfirmSkip.class).equals(first),"block fallback replaced/accepted the prompt");
            check(select(rig,1,button(rig,1,TableButton.CancelSkip.class)),"return did not cancel");
            check(buttons(rig,1).contains(pass())&&!rig.humans.get(1).hasQueued(),"return failed to restore original actions");
            check(!select(rig,1,first)&&!rig.humans.get(1).hasQueued(),"cancelled confirmation remained actionable");
            select(rig,1,pass());var confirm=button(rig,1,TableButton.ConfirmSkip.class);
            check(!confirm.equals(first),"a new prompt reused an expired token");
            check(select(rig,1,confirm)&&rig.humans.get(1).hasQueued(),"explicit confirmation did not submit pass");
            check(!select(rig,1,confirm),"double confirmation could overwrite a queued decision");
            rig.driver.advance(.05);
            check(!rig.humans.get(1).hasQueued()&&rig.driver.legalActions(1).isEmpty(),"confirmed pass was not accepted by the driver");
        }
    }
    private static void ordinaryPass() {
        var hand=new ArrayList<>(waiting(0));hand.add(tile(4,2));
        var caller=new ArrayList<>(waiting(1));caller.set(0,tile(4,2));caller.set(1,tile(4,2));
        var rig=rig(driver(List.of(player(hand),player(caller),player(waiting(2)),player(waiting(0))),Collections.nCopies(20,tile(4,2))).currentRound());
        act(rig.driver,0,new PlayerAction.Discard(tile(4,2)));
        var legal=rig.driver.legalActions(1);
        check(legal.stream().anyMatch(a->a instanceof PlayerAction.Pon)&&legal.stream().noneMatch(a->a instanceof PlayerAction.DeclareRon),"plain pon fixture is invalid");
        assertTableMissUnchanged(rig,1);
        check(select(rig,1,pass())&&rig.humans.get(1).hasQueued(),"non-winning claim skip stopped being single-click");
    }
    private static void tsumoAndDiscard() {
        var rig=tsumoRig();var human=rig.humans.get(0);
        var win=buttons(rig,0).stream().filter(b->b instanceof TableButton.SubmitAction s&&s.action() instanceof PlayerAction.DeclareTsumo).findFirst().orElseThrow();
        assertTableMissUnchanged(rig,0);
        select(rig,0,button(rig,0,TableButton.SkipTsumo.class));assertPrompt(rig,0);
        select(rig,0,button(rig,0,TableButton.CancelSkip.class));
        check(buttons(rig,0).contains(win),"return failed to restore tsumo");
        select(rig,0,button(rig,0,TableButton.SkipTsumo.class));
        select(rig,0,button(rig,0,TableButton.ConfirmSkip.class));
        check(!human.hasQueued(),"tsumo confirmation queued an illegal rule-level Pass");
        check(buttons(rig,0).stream().noneMatch(b->b instanceof TableButton.SkipTsumo||b instanceof TableButton.SubmitAction s&&s.action() instanceof PlayerAction.DeclareTsumo),
                "confirmed skip did not hide this tsumo opportunity");
        check(!select(rig,0,win),"hidden tsumo action remained clickable");
        assertTableMissUnchanged(rig,0);
        human.onCuteClick(new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_HAND,(short)0),rig.driver,0,null);
        check(human.hasQueued(),"confirmed tsumo skip blocked a normal discard");

        var pending=tsumoRig();select(pending,0,button(pending,0,TableButton.SkipTsumo.class));
        var old=button(pending,0,TableButton.ConfirmSkip.class);
        assertTableMissUnchanged(pending,0);
        check(button(pending,0,TableButton.ConfirmSkip.class).equals(old),"table miss replaced the pending tsumo confirmation");
        pending.humans.get(0).onCuteClick(new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_HAND,(short)0),pending.driver,0,null);
        check(pending.humans.get(0).hasQueued(),"a pending tsumo prompt blocked an explicit hand-tile discard");
        check(!select(pending,0,old),"old tsumo confirmation overwrote a discard");
    }
    private static void expiryAndIndependence() {
        var rig=ronRig();select(rig,1,pass());select(rig,2,pass());
        var one=button(rig,1,TableButton.ConfirmSkip.class);var two=button(rig,2,TableButton.ConfirmSkip.class);
        check(!one.token().equals(two.token())&&!select(rig,2,one),"players shared a confirmation token");
        check(button(rig,1,TableButton.ConfirmSkip.class).equals(one)&&button(rig,2,TableButton.ConfirmSkip.class).equals(two),"another player's click changed this prompt");
        rig.humans.get(1).setOccupant(UUID.randomUUID());
        check(!select(rig,1,one)&&buttons(rig,1).contains(pass()),"occupant change preserved confirmation");
        var oldDriver=rig.driver;
        var replacement=new TheMahjongDriver(oldDriver.match(),new ArrayList<MahjongPlayerInterface>(rig.humans),new Random(2),oldDriver.snapshot());
        check(!rig.humans.get(2).selectTableButton(two,replacement,2,null),"another driver accepted an old confirmation");
        check(!rig.humans.get(2).hasQueued(),"driver replacement submitted an old pass");

        var expired=ronRig();select(expired,1,pass());var token=button(expired,1,TableButton.ConfirmSkip.class);
        passClaims(expired.driver);
        check(!select(expired,1,token)&&!expired.humans.get(1).hasQueued(),"phase/round change accepted stale confirmation");
        var fresh=tsumoRig();var h=fresh.humans.get(0);select(fresh,0,button(fresh,0,TableButton.SkipTsumo.class));
        var pending=button(fresh,0,TableButton.ConfirmSkip.class);
        check(!select(fresh,0,new TableButton.ConfirmSkip(UUID.randomUUID()))&&!select(fresh,0,new TableButton.CancelSkip(UUID.randomUUID())),"wrong confirmation token was accepted");
        check(button(fresh,0,TableButton.ConfirmSkip.class).equals(pending)&&!h.hasQueued(),"wrong token erased a current prompt");
        h.clearOccupant();check(!select(fresh,0,pending),"cleared occupant retained a confirmation");
    }
    public static void main(String[] args) {
        missedButtons();System.out.println("PASS: missed chi/pon/open-kan/ron/chankan/tsumo/self-kan buttons preserve state; explicit buttons remain available");
        claimConfirmation();System.out.println("PASS: ron/chankan skips require explicit confirmation; return and block fallback are safe");
        ordinaryPass();System.out.println("PASS: non-winning claim skips remain single-click");
        tsumoAndDiscard();System.out.println("PASS: tsumo skip confirms locally without illegal Pass; normal discards remain available");
        expiryAndIndependence();System.out.println("PASS: stale/wrong/cancelled tokens, changed occupants/drivers/phases and other humans cannot submit a skip");
    }
}
