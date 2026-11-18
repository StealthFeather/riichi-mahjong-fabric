package com.riichimahjong.fabric;

import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.mahjongtable.*;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.variants.Jokers;
import com.themahjong.yaku.NonYakuman;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Called declarations retain actual riichi independently of the lost ippatsu or hidden river tile. */
public final class CalledRiichiVariantsRegression {
    private static List<MahjongPlayerInterface> humans() {
        return IntStream.range(0,4).mapToObj(seat->{
            var human=new MahjongTableHumanPlayer();human.setOccupant(new UUID(0,seat+1));
            return (MahjongPlayerInterface)human;
        }).toList();
    }
    private static TheMahjongPlayer concealed(List<TheMahjongTile> hand) {
        return new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(),
                List.of(new TheMahjongDiscard(tile(1,9),false)),List.of());
    }
    private static ArrayList<TheMahjongTile> waitingJoker(int suit) {
        var hand=new ArrayList<>(waiting(suit));hand.set(11,Jokers.TILE);return hand;
    }
    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);DriverNbt.writeDriver(out,driver);
        var restored=DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),out.buildResult()),humans(),new Random(8));
        check(restored.currentRound().equals(driver.currentRound()),"called-riichi save changed the physical round");
        check(restored.bloodBattleState().snapshot().equals(driver.bloodBattleState().snapshot()),
                "called-riichi save changed accepted earlier winners");
        return restored;
    }
    private static TheMahjongDriver fixture(String call,boolean earlierWinner) {
        var original=new TheMahjongDriver(RuleSetPreset.MAHJONG_SOUL_4P.newMatch(MatchLength.SOUTH,true,true,true),humans(),new Random(5));
        original.startMatch();
        // Complete a real simultaneous exchange before preparing the later, directed hand position.
        for(int seat=0;seat<4;seat++) {
            for(var selected:original.currentRound().players().get(seat).currentHand().stream().filter(t->!Jokers.isJoker(t)).limit(3).toList())
                check(original.toggleExchangeTile(seat,selected),"fixture could not select exchange tile");
            check(original.confirmExchange(seat),"fixture could not confirm exchange");
        }
        check(original.exchangeState().complete(),"fixture did not finish simultaneous exchange");
        var declared=call.equals("Chi")?tile(1,3):tile(4,2);
        var source=waitingJoker(0);if(!earlierWinner)source.add(declared);
        var caller=waitingJoker(1);
        if(call.equals("Chi")){caller.set(0,tile(1,1));caller.set(1,tile(1,2));}
        else {caller.set(0,declared);caller.set(1,declared);if(call.equals("Daiminkan"))caller.set(2,declared);}
        var contender=waitingJoker(2);contender.set(0,declared);contender.set(1,declared);
        var fourth=waitingJoker(0);if(earlierWinner)fourth.add(tile(4,1));
        var players=List.of(concealed(source),concealed(caller),concealed(contender),concealed(fourth));
        var wall=new ArrayList<>(Collections.nCopies(24,tile(4,1)));if(earlierWinner)wall.addFirst(declared);
        var round=new TheMahjongRound(TheMahjongTile.Wind.SOUTH,1,0,0,0,TheMahjongRound.State.TURN,
                earlierWinner?3:0,-1,TheMahjongRound.ActiveTile.drawn(earlierWinner?tile(4,1):declared),wall,
                Collections.nCopies(4,tile(2,9)),Collections.nCopies(5,tile(4,3)),Collections.nCopies(5,tile(4,3)),1,players,List.of());
        var base=original.match();
        var match=new TheMahjongMatch(4,base.startingPoints(),base.targetPoints(),2,TheMahjongMatch.State.IN_ROUND,
                base.tileSet(),base.ruleSet(),List.of(),round);
        var driver=new TheMahjongDriver(match,humans(),new Random(1),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(earlierWinner?3:0),false,OptionalInt.empty()));
        driver.restoreExchangeState(original.exchangeState().snapshot());
        if(earlierWinner) {
            var win=driver.legalActions(3).stream().filter(PlayerAction.DeclareTsumo.class::isInstance).findFirst().orElseThrow();
            act(driver,3,win);check(driver.bloodBattleState().won(3),"earlier real blood-battle win was not accepted");
            act(driver,0,PlayerAction.Draw.INSTANCE);
        }
        return driver;
    }
    private static void preserved(TheMahjongDriver driver,int expectedPoints) {
        var player=driver.currentRound().players().getFirst();
        check(player.riichiState()==TheMahjongPlayer.RiichiState.RIICHI,"calling declaration cleared actual riichi state");
        check(player.points()==expectedPoints&&driver.currentRound().riichiSticks()==1,
                "called declaration did not charge exactly one deposit");
        check(!player.ippatsuEligible(),"a called declaration incorrectly retained ippatsu");
        check(player.discards().getLast().riichiDeclared(),"called declaration lost original riichi metadata");
        check(RiverDisplay.capture(driver.currentRound().players(),0).stream().noneMatch(PublicTable.Tile::sideways),
                "called declaration left an invented public sideways tile");
    }
    private static void calledDeclaration(String call,boolean earlierWinner,boolean interrupted) {
        var driver=fixture(call,earlierWinner);int balance=driver.currentRound().players().getFirst().points();
        var human=(MahjongTableHumanPlayer)driver.playerAt(0);var declared=call.equals("Chi")?tile(1,3):tile(4,2);
        check(human.selectTableButton(new MahjongTableHumanPlayer.TableButton.RiichiToggle(false),driver,0,null),
                "human could not select riichi in the combined variant");
        int index=driver.currentRound().handDisplayOrder(0).indexOf(declared);
        human.onCuteClick(new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_HAND,(short)index),driver,0,null);
        check(human.hasQueued(),"human declaration discard did not queue");driver.advance(.05);
        check(driver.currentRound().players().getFirst().riichi(),"human declaration was not accepted");
        check(driver.snapshot().pendingRiichiCommit().orElse(-1)==0,"declaration deposit committed before responses");
        var action=driver.legalActions(1).stream().filter(a->a.getClass().getSimpleName().equals(call)).findFirst().orElseThrow();
        act(driver,1,action);
        if(interrupted) {
            check(driver.currentPhase() instanceof MatchPhase.AwaitingClaims,"fixture did not retain another response for interrupted save");
            driver=reload(driver);
        }
        passClaims(driver);preserved(driver,balance-1000);
        check(driver.currentRound().players().get(1).melds().getLast().getClass().getSimpleName().equals(call),
                "selected declaration call was not applied");
        driver=reload(driver);preserved(driver,balance-1000);
        // Continue actual turn actions until the declarer next draws; an earlier winner must be skipped.
        for(int steps=0;steps<12;steps++) {
            if(driver.currentPhase() instanceof MatchPhase.AwaitingDraw draw&&draw.seat()==0)break;
            if(driver.currentPhase() instanceof MatchPhase.AwaitingDraw draw)act(driver,draw.seat(),PlayerAction.Draw.INSTANCE);
            else if(driver.currentPhase() instanceof MatchPhase.AwaitingDiscard discard) {
                var d=driver.legalActions(discard.seat()).stream().filter(PlayerAction.Discard.class::isInstance).findFirst().orElseThrow();
                act(driver,discard.seat(),d);passClaims(driver);
            } else throw new AssertionError("unexpected continuing blood-battle phase "+driver.currentPhase());
            check(driver.currentRound().players().getFirst().riichi(),"riichi disappeared on a later survivor action");
        }
        check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw draw&&draw.seat()==0,"declarer did not receive its next draw");
        act(driver,0,PlayerAction.Draw.INSTANCE);
        var win=(PlayerAction.DeclareTsumo)driver.legalActions(0).stream().filter(PlayerAction.DeclareTsumo.class::isInstance).findFirst().orElseThrow();
        check(win.result().yaku().contains(NonYakuman.RIICHI)&&!win.result().yaku().contains(NonYakuman.IPPATSU),
                "later accepted win lost riichi yaku or retained interrupted ippatsu");
        act(driver,0,win);check(driver.bloodBattleState().won(0),"later riichi self draw did not enter blood-battle wins");
        check(driver.currentRound().players().getFirst().riichi(),"blood-battle win cleared its accepted riichi state");
    }
    public static void main(String[] args) {
        for(var call:List.of("Chi","Pon","Daiminkan"))for(boolean earlier:new boolean[]{false,true})for(boolean interrupted:new boolean[]{false,true})
            calledDeclaration(call,earlier,interrupted);
        System.out.println("PASS: four-player South exchange+joker+blood-battle human riichi survives chi/pon/open-kan, prior winners, accepted-claim and post-call NBT; ippatsu alone clears and the later self draw scores riichi");
    }
}
