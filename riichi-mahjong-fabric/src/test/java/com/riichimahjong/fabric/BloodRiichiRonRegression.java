package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.*;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.variants.Jokers;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Blood battle accepts a ron-ed declaration exactly when that response leaves the hand running. */
public final class BloodRiichiRonRegression {
    private static final TheMahjongTile WHITE=tile(4,1);
    private static List<MahjongPlayerInterface> humans(int count) {
        return Stream.generate(()->(MahjongPlayerInterface)new MahjongHumanPlayer()).limit(count).toList();
    }
    private static List<Integer> points(TheMahjongDriver driver) {
        return driver.currentRound().players().stream().map(TheMahjongPlayer::points).toList();
    }
    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);DriverNbt.writeDriver(out,driver);
        var restored=DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),out.buildResult()),humans(driver.match().playerCount()),new Random(5));
        check(restored.currentRound().equals(driver.currentRound())&&restored.currentPhase().equals(driver.currentPhase()),
                "declaration-ron NBT changed the round or response phase");
        if(driver.bloodBattleState()!=null)check(restored.bloodBattleState().snapshot().equals(driver.bloodBattleState().snapshot()),
                "declaration-ron NBT changed accepted wins");
        return restored;
    }
    private static TheMahjongDriver replaceRound(TheMahjongDriver driver,TheMahjongRound round) {
        var base=driver.match();
        var match=new TheMahjongMatch(base.playerCount(),base.startingPoints(),base.targetPoints(),base.roundCount(),
                base.state(),base.tileSet(),base.ruleSet(),base.completedRounds(),round);
        var next=new TheMahjongDriver(match,humans(base.playerCount()),new Random(5),driver.snapshot());
        if(driver.bloodBattleState()!=null)next.restoreBloodBattleState(driver.bloodBattleState().snapshot());
        if(driver.exchangeState()!=null)next.restoreExchangeState(driver.exchangeState().snapshot());
        return next;
    }
    private static TheMahjongRound copy(TheMahjongRound round,int sticks,List<TheMahjongTile> wall) {
        return new TheMahjongRound(round.roundWind(),round.handNumber(),round.honba(),sticks,round.dealerSeat(),
                round.state(),round.currentTurnSeat(),round.claimSourceSeat().orElse(-1),round.activeTile(),wall,
                round.rinshanTiles(),round.doraIndicators(),round.uraDoraIndicators(),round.revealedDoraCount(),
                round.pendingKanDoraReveals(),round.players(),round.pendingDeltas());
    }
    private static TheMahjongDriver fixture(RuleSetPreset preset,boolean blood,boolean joker,int retired) {
        int count=preset.playerCount(),turn=retired==0?0:count-retired;
        var base=preset.newMatch(MatchLength.SOUTH,joker,joker,blood);
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<count;seat++) {
            var hand=new ArrayList<>(waiting(1));if(joker)hand.set(11,Jokers.TILE);if(seat==turn)hand.add(WHITE);
            players.add(new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(),
                    List.of(new TheMahjongDiscard(tile(2,5),false)),List.of()));
        }
        var indicators=Collections.nCopies(5,tile(4,3));
        var r=new TheMahjongRound(TheMahjongTile.Wind.SOUTH,1,0,0,0,TheMahjongRound.State.TURN,turn,-1,
                TheMahjongRound.ActiveTile.drawn(WHITE),Collections.nCopies(48,WHITE),Collections.nCopies(4,tile(2,9)),
                indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(count,base.startingPoints(),base.targetPoints(),2,TheMahjongMatch.State.IN_ROUND,
                base.tileSet(),base.ruleSet(),List.of(),r);
        var driver=new TheMahjongDriver(match,humans(count),new Random(1),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(turn),false,OptionalInt.empty()));
        for(int seat=turn;retired>0&&seat<count;seat++) {
            if(seat!=turn)act(driver,seat,PlayerAction.Draw.INSTANCE);
            var win=driver.legalActions(seat).stream().filter(PlayerAction.DeclareTsumo.class::isInstance).findFirst().orElseThrow();
            act(driver,seat,win);
        }
        if(retired>0)act(driver,0,PlayerAction.Draw.INSTANCE);
        // Existing table deposits are independent of the declaration under test.
        return replaceRound(driver,copy(driver.currentRound(),2,driver.currentRound().liveWall()));
    }
    private static Map<Integer,PlayerAction.DeclareRon> rons(TheMahjongDriver driver,int requested) {
        var actions=new LinkedHashMap<Integer,PlayerAction.DeclareRon>();
        for(int seat=1;seat<=requested;seat++)actions.put(seat,(PlayerAction.DeclareRon)driver.legalActions(seat).stream()
                .filter(PlayerAction.DeclareRon.class::isInstance).findFirst().orElseThrow());
        return actions;
    }
    private static void resolve(TheMahjongDriver driver,Map<Integer,PlayerAction.DeclareRon> actions) {
        var claims=(MatchPhase.AwaitingClaims)driver.currentPhase();
        for(int seat:claims.pendingSeats())driver.submitAction(seat,actions.containsKey(seat)?actions.get(seat):PlayerAction.Pass.INSTANCE);
        driver.advance(.05);
    }
    private static void declaration(RuleSetPreset preset,boolean blood,boolean joker,int retired,int requested,boolean emptyWall,boolean interrupt) {
        var driver=fixture(preset,blood,joker,retired);var before=points(driver);int count=preset.playerCount();
        act(driver,0,new PlayerAction.DiscardWithRiichi(WHITE));
        if(emptyWall) {
            // A restored pending declaration at the closing-wall boundary must converge without a phantom payment.
            driver=replaceRound(driver,copy(driver.currentRound(),2,List.of()));
        }
        if(interrupt)driver=reload(driver);
        var actions=rons(driver,requested);
        boolean abortive=requested==3&&driver.rules().sanchahouAbortive();
        int accepted=abortive?0:driver.rules().doubleRonAllowed()?requested:1;
        boolean terminal=!blood||abortive||emptyWall||retired+accepted>=count-1;
        int newFee=blood&&!terminal?1000:0;
        int[] expected=before.stream().mapToInt(Integer::intValue).toArray();expected[0]-=newFee;
        if(!abortive)for(int winner=1;winner<=accepted;winner++) {
            var deltas=actions.get(winner).result().pointDeltas();
            for(int seat=0;seat<count;seat++)expected[seat]+=deltas.get(seat);
            expected[winner]+=winner==1?newFee:-2000;
        }
        resolve(driver,actions);
        check(points(driver).equals(Arrays.stream(expected).boxed().toList()),
                "ron-ed declaration lost or duplicated a fee/pool for "+preset+" blood="+blood+" retired="+retired+" requested="+requested
                        +" empty="+emptyWall+": expected "+Arrays.toString(expected)+" got "+points(driver));
        check((driver.currentPhase() instanceof MatchPhase.RoundEnded)==terminal,"declaration ron chose the wrong continuation boundary");
        var source=driver.currentRound().players().getFirst();
        if(blood) {
            check(source.riichi()==!terminal,"blood declaration must stay active only when its ron response continues the hand");
            check(source.discards().getLast().riichiDeclared()==!terminal,"canceled declaration left a marker, or continuing one lost it");
            check(driver.bloodBattleState().wins().size()==retired+accepted,"head bump/multiple-ron retirement count changed");
        }
        if(blood)check(driver.snapshot().pendingRiichiCommit().isEmpty(),"processed declaration retained a pending payment");
        check(driver.currentRound().riichiSticks()==(abortive?2:0),"processed ron did not consume the correct deposit pool");
        driver=reload(driver);var paid=points(driver);driver.advance(.05);
        check(points(driver).equals(paid),"declaration fee or ron was charged again after reload/tick");
        if(!terminal) {
            // Continue real survivor turns to the declarer's next draw; the retained riichi lock must still apply.
            untilSourceDraw(driver);act(driver,0,PlayerAction.Draw.INSTANCE);
            var drawn=((TheMahjongRound.ActiveTile.Drawn)driver.currentRound().activeTile()).tile();
            check(driver.legalActions(0).stream().filter(PlayerAction.Discard.class::isInstance)
                            .map(a->((PlayerAction.Discard)a).tile()).allMatch(drawn::equals),
                    "a continuing ron-ed declaration allowed later hand discards");
            check(driver.legalActions(0).stream().noneMatch(PlayerAction.DiscardWithRiichi.class::isInstance),
                    "an accepted continuing declaration could pay a second riichi fee");
        }
    }
    private static void untilSourceDraw(TheMahjongDriver driver) {
        for(int step=0;step<16;step++) {
            if(driver.currentPhase() instanceof MatchPhase.AwaitingDraw draw&&draw.seat()==0)return;
            if(driver.currentPhase() instanceof MatchPhase.AwaitingDraw draw)act(driver,draw.seat(),PlayerAction.Draw.INSTANCE);
            else if(driver.currentPhase() instanceof MatchPhase.AwaitingDiscard discard) {
                var drawn=((TheMahjongRound.ActiveTile.Drawn)driver.currentRound().activeTile()).tile();
                act(driver,discard.seat(),new PlayerAction.Discard(drawn));passClaims(driver);
            } else throw new AssertionError("unexpected continuing phase "+driver.currentPhase());
        }
        throw new AssertionError("declarer did not receive another draw");
    }
    private static void establishedRiichiSurvivesLaterRon(RuleSetPreset preset) {
        var driver=fixture(preset,true,true,0);int initial=points(driver).stream().mapToInt(Integer::intValue).sum();
        // Each survivor draws/discards a non-waiting chun; only seat 0 draws white.
        // After each later ron one fewer opponent remains before seat 0's next draw.
        var wall=new ArrayList<TheMahjongTile>();
        for(int survivors=preset.playerCount()-1;survivors>=1;survivors--) {
            wall.addAll(Collections.nCopies(survivors,tile(4,3)));wall.add(WHITE);
        }
        wall.addAll(Collections.nCopies(24,tile(4,3)));
        driver=replaceRound(driver,copy(driver.currentRound(),2,wall));
        act(driver,0,new PlayerAction.DiscardWithRiichi(WHITE));passClaims(driver);
        check(driver.currentRound().riichiSticks()==3,"ordinary accepted declaration did not add one stick");
        for(int winner=1;winner<preset.playerCount();winner++) {
            untilSourceDraw(driver);act(driver,0,PlayerAction.Draw.INSTANCE);act(driver,0,new PlayerAction.Discard(WHITE));
            var action=(PlayerAction.DeclareRon)driver.legalActions(winner).stream().filter(PlayerAction.DeclareRon.class::isInstance).findFirst().orElseThrow();
            resolve(driver,Map.of(winner,action));
            check(driver.currentRound().players().getFirst().riichi(),"a later ron canceled already-established blood-battle riichi");
            check(points(driver).stream().mapToInt(Integer::intValue).sum()==initial+2000,
                    "later rons charged a second declaration or paid its fee/pool twice");
            driver=reload(driver);
        }
        check(driver.currentPhase() instanceof MatchPhase.RoundEnded,"last later ron did not finish blood battle");
    }
    public static void main(String[] args) {
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN) {
            for(boolean interrupted:new boolean[]{false,true}) {
                declaration(preset,true,true,0,1,false,interrupted);
                declaration(preset,true,true,preset.playerCount()-2,1,false,interrupted);
                declaration(preset,true,true,0,Math.min(2,preset.playerCount()-1),false,interrupted);
                declaration(preset,true,true,0,1,true,interrupted);
            }
            declaration(preset,false,false,0,1,false,true);
            establishedRiichiSurvivesLaterRon(preset);
        }
        declaration(RuleSetPreset.MAHJONG_SOUL_4P,true,true,1,2,false,true);
        declaration(RuleSetPreset.MAHJONG_SOUL_4P,true,true,0,3,false,true);
        declaration(RuleSetPreset.TENHOU_4P,true,true,0,3,false,true);
        System.out.println("PASS: declaration-ron continues or cancels only at blood-battle hand end; old/new deposits pay once across multi-ron/head bump, prior winners, exhausted wall, NBT, later forced draws and already-established later rons; ordinary modes unchanged");
    }
}
