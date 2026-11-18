package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.driver.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Real riichi wins may retire seats without ending the hand when the optional variant is enabled. */
public final class RiichiBloodBattleRegression {
    private static List<MahjongPlayerInterface> humans(int count) {
        return Stream.generate(() -> (MahjongPlayerInterface)new MahjongHumanPlayer()).limit(count).toList();
    }
    private static TheMahjongTile pair(int seat) { return tile(4,seat%3+1); }
    private static List<TheMahjongTile> hand(int seat, TheMahjongTile wait) {
        var hand = new ArrayList<TheMahjongTile>();
        for (int rank=1;rank<=9;rank++) hand.add(tile(1,rank));
        hand.addAll(Collections.nCopies(3,tile(3,seat+1)));
        hand.add(wait);
        return hand;
    }
    private static TheMahjongPlayer playerAt(int seat, TheMahjongTile wait, boolean drawn) {
        var tiles=hand(seat,wait);if(drawn)tiles.add(wait);
        return new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,tiles,List.of(),
                List.of(new TheMahjongDiscard(tile(2,5),false)),List.of());
    }
    private static TheMahjongDriver rig(RuleSetPreset preset,boolean blood,int sticks,boolean commonWait) {
        int count=preset.playerCount();var base=preset.newMatch(MatchLength.SOUTH,false,false,blood);
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<count;seat++)players.add(playerAt(seat,commonWait?pair(0):pair(seat),seat==0));
        var wall=new ArrayList<TheMahjongTile>();
        for(int seat=1;seat<count;seat++)wall.add(commonWait?pair(0):pair(seat));
        wall.addAll(Collections.nCopies(20,tile(2,8)));
        var indicators=Collections.nCopies(5,tile(4,3));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,sticks,0,TheMahjongRound.State.TURN,0,-1,
                TheMahjongRound.ActiveTile.drawn(pair(0)),wall,Collections.nCopies(4,tile(2,9)),
                indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(count,base.startingPoints(),base.targetPoints(),2,
                TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),round);
        return new TheMahjongDriver(match,humans(count),new Random(9),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
    }
    private static PlayerAction action(TheMahjongDriver driver,int seat,Class<? extends PlayerAction> type) {
        return driver.legalActions(seat).stream().filter(type::isInstance).findFirst()
                .orElseThrow(()->new AssertionError("missing "+type.getSimpleName()+" at seat "+seat+" phase "+driver.currentPhase()));
    }
    private static List<Integer> points(TheMahjongDriver driver) {
        return driver.currentRound().players().stream().map(TheMahjongPlayer::points).toList();
    }
    private static int total(TheMahjongDriver driver) { return points(driver).stream().mapToInt(Integer::intValue).sum(); }
    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(out,driver);
        var input=TagValueInput.create(ProblemReporter.DISCARDING,HolderLookup.Provider.create(Stream.empty()),out.buildResult());
        var restored=DriverNbt.readDriver(input,humans(driver.match().playerCount()),new Random(17));
        check(restored.currentRound().equals(driver.currentRound())&&restored.currentPhase().equals(driver.currentPhase()),
                "blood-battle save/reload changed the current round or next player");
        check(restored.bloodBattleState().snapshot().equals(driver.bloodBattleState().snapshot()),
                "blood-battle save/reload lost winners, accepted tiles or settlements");
        return restored;
    }
    private static void tsumoAndWinnerRetirement(RuleSetPreset preset) {
        var driver=rig(preset,true,3,false);int initial=total(driver),count=preset.playerCount();
        check(driver.rules().bloodBattle(),"riichi factory dropped the enabled blood-battle option");
        var settled=new HashMap<Integer,Integer>();
        for(int winner=0;winner<count-1;winner++) {
            if(winner>0)act(driver,winner,PlayerAction.Draw.INSTANCE);
            act(driver,winner,action(driver,winner,PlayerAction.DeclareTsumo.class));
            var state=driver.bloodBattleState();
            check(state!=null&&state.wins().size()==winner+1&&state.won(winner),"accepted tsumo did not retire its winner");
            check(state.wins().getLast().selfDrawn()&&state.wins().getLast().sourceSeat()==winner
                            &&state.winningTile(winner).equals(pair(winner)),"accepted tsumo lost its physical winning tile/source");
            check(driver.legalActions(winner).isEmpty(),"a retired winner could still draw, discard or claim");
            for(var entry:settled.entrySet())check(points(driver).get(entry.getKey()).equals(entry.getValue()),
                    "an earlier winner paid a later tsumo");
            settled.put(winner,points(driver).get(winner));
            check(total(driver)==initial+3000,"riichi deposits were paid more than once or payments stopped summing to zero");
            check(driver.currentRound().riichiSticks()==0,"accepted winner did not collect the visible riichi sticks");
            var before=points(driver);driver.advance(.05);check(points(driver).equals(before),"a completed win paid twice on a later tick");
            if(winner<count-2) {
                check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw next&&next.seat()==winner+1,
                        "driver did not yield before the next active seat's draw");
                check(driver.currentRound().state()!=TheMahjongRound.State.ENDED,"first riichi win ended an enabled blood battle");
                driver=reload(driver);
            }else {
                check(driver.currentPhase() instanceof MatchPhase.RoundEnded ended&&ended.winResults().size()==count-1,
                        "the final active opponent did not end a three/four-player blood battle");
                check(driver.currentRound().state()==TheMahjongRound.State.ENDED,"terminal blood battle retained a live round");
                reload(driver);
                var settledPoints=points(driver);
                driver.advanceRound(false,0);
                check(driver.bloodBattleState()!=null&&driver.bloodBattleState().wins().isEmpty(),
                        "next hand retained old blood-battle winners");
                check(points(driver).equals(settledPoints),"new hand repeated the prior blood-battle payments");
                check(driver.legalActions(driver.currentRound().currentTurnSeat()).contains(PlayerAction.Draw.INSTANCE),
                        "new hand did not restore former winners to ordinary turn order");
            }
        }
    }
    private static void simultaneousRon(RuleSetPreset preset) {
        var driver=rig(preset,true,2,true);int initial=total(driver);
        act(driver,0,new PlayerAction.Discard(pair(0)));
        check(driver.currentPhase() instanceof MatchPhase.AwaitingClaims,"real discard did not create riichi claims");
        var claims=(MatchPhase.AwaitingClaims)driver.currentPhase();
        int claimed=2, wanted=driver.rules().doubleRonAllowed()?2:1;
        for(int seat:claims.pendingSeats())driver.submitAction(seat,seat<=claimed
                ?action(driver,seat,PlayerAction.DeclareRon.class):PlayerAction.Pass.INSTANCE);
        driver.advance(.05);
        var state=driver.bloodBattleState();
        check(state.wins().size()==wanted&&state.won(1)&&(wanted==1||state.won(2)),"one batch of multiple ron did not preserve the preset claim policy");
        check(state.wins().stream().allMatch(win->!win.selfDrawn()&&win.sourceSeat()==0&&win.tile().equals(pair(0))),
                "multi-ron lost its shared discarder/winning identity");
        check(total(driver)==initial+2000,"multiple ron paid the same riichi pool more than once");
        for(int seat:state.winners())check(driver.legalActions(seat).isEmpty(),"multi-ron winners retained legal actions");
        if(wanted<preset.playerCount()-1)check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw next&&next.seat()==wanted+1,
                "multi-ron did not skip both retired winners");
        else check(driver.currentPhase() instanceof MatchPhase.RoundEnded ended&&ended.winResults().size()==2,
                "two simultaneous three-player winners did not finish the hand");
        reload(driver);
    }
    private static void defaultAndDisabled() {
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN) {
            check(!preset.newMatch().ruleSet().bloodBattle(),"new riichi table unexpectedly enables blood battle");
            var driver=rig(preset,false,0,false);
            act(driver,0,action(driver,0,PlayerAction.DeclareTsumo.class));
            check(driver.currentPhase() instanceof MatchPhase.RoundEnded ended&&ended.winResults().size()==1,
                    "disabled riichi blood battle changed the original first-win termination");
        }
    }

    private static TheMahjongDriver withRound(TheMahjongDriver previous, TheMahjongRound round, MatchPhase phase) {
        var old=previous.match();
        var match=new TheMahjongMatch(old.playerCount(),old.startingPoints(),old.targetPoints(),old.roundCount(),
                TheMahjongMatch.State.IN_ROUND,old.tileSet(),old.ruleSet(),old.completedRounds(),round);
        var restored=new TheMahjongDriver(match,humans(old.playerCount()),new Random(3),
                new TheMahjongDriver.Snapshot(phase,false,OptionalInt.empty()));
        restored.restoreBloodBattleState(previous.bloodBattleState().snapshot());
        return restored;
    }
    private static TheMahjongRound copy(TheMahjongRound round,List<TheMahjongTile> wall,
            List<TheMahjongPlayer> players,TheMahjongTile.Wind wind,int hand,int dealer) {
        return new TheMahjongRound(wind,hand,round.honba(),round.riichiSticks(),dealer,
                round.state(),round.currentTurnSeat(),round.claimSourceSeat().orElse(-1),round.activeTile(),wall,
                round.rinshanTiles(),round.doraIndicators(),round.uraDoraIndicators(),round.revealedDoraCount(),
                round.pendingKanDoraReveals(),players,round.pendingDeltas());
    }
    private static void activeOnlyExhaustion(RuleSetPreset preset) {
        var driver=rig(preset,true,0,false);int count=preset.playerCount();
        act(driver,0,action(driver,0,PlayerAction.DeclareTsumo.class));
        var current=driver.currentRound();var players=new ArrayList<>(current.players());
        var noten=players.get(count-1);var hand=new ArrayList<>(noten.currentHand());hand.set(0,tile(2,2));
        var replaced=new TheMahjongPlayer(noten.points(),noten.riichiState(),noten.ippatsuEligible(),hand,
                noten.melds(),noten.discards(),noten.temporaryFuritenTiles(),noten.riichiPermanentFuriten(),noten.kitaCount());
        check(!com.themahjong.compat.DrawSettlement.inTenpai(replaced),"blood-battle noten fixture accidentally waits");
        players.set(count-1,replaced);
        var round=copy(current,List.of(tile(2,8)),players,current.roundWind(),current.handNumber(),current.dealerSeat());
        driver=withRound(driver,round,new MatchPhase.AwaitingDraw(1,false));
        var before=points(driver);act(driver,1,PlayerAction.Draw.INSTANCE);act(driver,1,new PlayerAction.Discard(tile(2,8)));
        check(driver.currentPhase() instanceof MatchPhase.RoundEnded ended&&ended.winResults().size()==1,
                "exhaustion did not finish a continuing riichi blood battle");
        check(points(driver).get(0).equals(before.get(0)),"an exited winner paid an exhaustive-draw penalty");
        for(int seat=1;seat<count;seat++) {
            int expected=seat==count-1?-3000:3000/(count-2);
            check(points(driver).get(seat)==before.get(seat)+expected,"noten payments were not restricted to active survivors");
        }
        reload(driver);
        // A legacy/imported record can already be at an empty-wall draw boundary.
        var empty=withRound(driver,copy(round,List.of(),players,round.roundWind(),round.handNumber(),round.dealerSeat()),
                new MatchPhase.AwaitingDraw(1,false));
        empty.advance(.05);
        check(empty.currentPhase() instanceof MatchPhase.RoundEnded,"empty-wall draw boundary threw or waited forever");
    }
    private static void continuingDeclaration(RuleSetPreset preset) {
        var driver=rig(preset,true,2,true);int initial=total(driver);
        var declaration=new PlayerAction.DiscardWithRiichi(pair(0));
        check(driver.legalActions(0).contains(declaration),"riichi declaration fixture is not legal");
        act(driver,0,declaration);
        var claims=(MatchPhase.AwaitingClaims)driver.currentPhase();
        for(int seat:claims.pendingSeats())driver.submitAction(seat,seat==1
                ?action(driver,seat,PlayerAction.DeclareRon.class):PlayerAction.Pass.INSTANCE);
        driver.advance(.05);
        var source=driver.currentRound().players().get(0);
        check(source.riichi()&&source.discards().getLast().riichiDeclared(),
                "a declaration discard won by ron canceled riichi despite the blood battle continuing");
        var result=driver.bloodBattleState().wins().getFirst().result();
        check(source.points()==24000+result.pointDeltas().get(0),"continuing declaration did not charge its deposit exactly once");
        check(result.pointDeltas().stream().mapToInt(Integer::intValue).sum()==3000,
                "the first ron winner did not receive the existing pool plus the newly committed declaration");
        check(total(driver)==initial+2000&&driver.currentRound().riichiSticks()==0,
                "continuing declaration lost or duplicated the preexisting pool");
        reload(driver);
    }
    private static void finalMatchKeepsWinners() {
        var driver=rig(RuleSetPreset.WRC_4P,true,0,false);
        for(int seat=0;seat<3;seat++) {
            if(seat>0)act(driver,seat,PlayerAction.Draw.INSTANCE);
            act(driver,seat,action(driver,seat,PlayerAction.DeclareTsumo.class));
        }
        var round=driver.currentRound();
        // WRC has no sudden-death extension: the scheduled final dealer advances into MatchEnded.
        var terminal=copy(round,round.liveWall(),round.players(),TheMahjongTile.Wind.SOUTH,4,3);
        driver=withRound(driver,terminal,driver.currentPhase());
        var wins=driver.bloodBattleState().snapshot();driver.advanceRound(false,0);
        check(driver.currentPhase() instanceof MatchPhase.MatchEnded&&driver.bloodBattleState().snapshot().equals(wins),
                "advancing the final hand erased its blood-battle winners");
        reload(driver);
    }
    private static void robbedKanContinues(RuleSetPreset preset) {
        var driver=rig(preset,true,0,false);var r=driver.currentRound();
        var added=tile(0,4);var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,added),0,1,0);
        var own=new ArrayList<TheMahjongTile>();own.add(added);
        for(char rank:"1234567899".toCharArray())own.add(tile(1,rank-'0'));
        var waiting=new ArrayList<TheMahjongTile>();
        for(char rank:"12356".toCharArray())waiting.add(tile(0,rank-'0'));
        for(char rank:"12367899".toCharArray())waiting.add(tile(1,rank-'0'));
        var players=new ArrayList<>(r.players());
        players.set(0,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,own,List.of(pon),
                List.of(new TheMahjongDiscard(tile(2,5),false)),List.of()));
        players.set(1,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,waiting,List.of(),
                List.of(new TheMahjongDiscard(tile(2,5),false)),List.of()));
        var round=new TheMahjongRound(r.roundWind(),r.handNumber(),r.honba(),0,r.dealerSeat(),TheMahjongRound.State.TURN,
                0,-1,TheMahjongRound.ActiveTile.drawn(added),r.liveWall(),r.rinshanTiles(),r.doraIndicators(),
                r.uraDoraIndicators(),r.revealedDoraCount(),r.pendingKanDoraReveals(),players,List.of());
        driver=withRound(driver,round,new MatchPhase.AwaitingDiscard(0));
        act(driver,0,action(driver,0,PlayerAction.Kakan.class));
        check(driver.currentPhase() instanceof MatchPhase.AwaitingClaims claims&&claims.source()==MatchPhase.ClaimSource.KAKAN,
                "real added kan did not expose a robbery window");
        var claims=(MatchPhase.AwaitingClaims)driver.currentPhase();
        for(int seat:claims.pendingSeats())driver.submitAction(seat,seat==1
                ?action(driver,seat,PlayerAction.DeclareChankan.class):PlayerAction.Pass.INSTANCE);
        driver.advance(.05);
        check(driver.bloodBattleState().wins().size()==1&&driver.bloodBattleState().wins().getFirst().sourceSeat()==0,
                "accepted chankan did not retain its source");
        check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw next&&next.seat()==2,
                "accepted chankan did not continue with the next survivor");
        var source=driver.currentRound().players().get(0);
        check(source.melds().getFirst() instanceof TheMahjongMeld.Pon&&source.currentHand().stream().noneMatch(added::equals),
                "robbed declaration kept a completed kan or returned the robbed tile to the payer");
        check(driver.currentRound().revealedDoraCount()==r.revealedDoraCount()
                        &&driver.currentRound().pendingKanDoraReveals()==r.pendingKanDoraReveals(),
                "robbed kan retained a new visible or pending kan indicator");
        check(driver.acceptedWinningTile() instanceof TheMahjongRound.ActiveTile.HeldKakan,
                "continuing chankan lost the original accepted source kind");
        reload(driver);
    }
    public static void main(String[] args) {
        defaultAndDisabled();
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN) {
            tsumoAndWinnerRetirement(preset);simultaneousRon(preset);
            activeOnlyExhaustion(preset);continuingDeclaration(preset);robbedKanContinues(preset);
        }
        finalMatchKeepsWinners();
        System.out.println("PASS: 3p/4p riichi blood battles retire winners, settle deposits once, preserve preset multi-ron policy, cancel robbed declarations/indicators, settle active-only draws and retain continuing/final NBT; next hands reset winners and disabled defaults keep ordinary termination");
    }
}
