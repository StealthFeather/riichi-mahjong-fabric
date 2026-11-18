package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.yaku.Yakuman;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Assert actual per-seat receipts, not just conservation, through repeated accepted blood-battle wins. */
public final class BloodBattlePaymentsRegression {
    private static List<MahjongPlayerInterface> humans(int count) {
        return Stream.generate(()->(MahjongPlayerInterface)new MahjongHumanPlayer()).limit(count).toList();
    }
    private static TheMahjongPlayer player(List<TheMahjongTile> hand,List<TheMahjongMeld> melds) {
        return new TheMahjongPlayer(50000,TheMahjongPlayer.RiichiState.NONE,false,hand,melds,
                List.of(new TheMahjongDiscard(tile(2,5),false)),List.of());
    }
    private static List<TheMahjongTile> ordinaryHand(int seat,boolean drawn) {
        var hand=new ArrayList<TheMahjongTile>();
        for(int rank=1;rank<=9;rank++)hand.add(tile(1,rank));
        hand.addAll(Collections.nCopies(3,tile(3,seat+1)));
        hand.add(tile(4,seat%3+1));if(drawn)hand.add(tile(4,seat%3+1));return hand;
    }
    private static TheMahjongDriver fixture(RuleSetPreset preset,int dealer,int honba,int deposits,int paoSource) {
        int count=preset.playerCount();var base=preset.newMatch(MatchLength.SOUTH,false,false,true);
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<count;seat++)players.add(player(ordinaryHand(seat,seat==0),List.of()));
        TheMahjongTile secondDraw=tile(4,2);
        if(paoSource>=0){
            var melds=new ArrayList<TheMahjongMeld>();
            for(int rank=1;rank<=3;rank++)melds.add(new TheMahjongMeld.Pon(Collections.nCopies(3,tile(4,rank)),0,rank==3?paoSource:2,0));
            players.set(1,player(List.of(tile(1,1),tile(1,2),tile(1,3),tile(2,9)),melds));
            secondDraw=tile(2,9);
        }
        var wall=new ArrayList<TheMahjongTile>();wall.add(secondDraw);
        for(int seat=2;seat<count;seat++)wall.add(tile(4,seat%3+1));
        wall.addAll(Collections.nCopies(20,tile(2,8)));
        var indicators=Collections.nCopies(5,tile(4,3));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,honba,deposits,dealer,TheMahjongRound.State.TURN,0,-1,
                TheMahjongRound.ActiveTile.drawn(tile(4,1)),wall,Collections.nCopies(4,tile(1,9)),indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(count,base.startingPoints(),base.targetPoints(),2,TheMahjongMatch.State.IN_ROUND,
                base.tileSet(),base.ruleSet(),List.of(),round);
        return new TheMahjongDriver(match,humans(count),new Random(7),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
    }
    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);DriverNbt.writeDriver(out,driver);
        return DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),out.buildResult()),humans(driver.match().playerCount()),new Random(7));
    }
    private static List<Integer> points(TheMahjongDriver driver) {
        return driver.currentRound().players().stream().map(TheMahjongPlayer::points).toList();
    }
    private static PlayerAction.DeclareTsumo tsumo(TheMahjongDriver driver,int seat) {
        return driver.legalActions(seat).stream().filter(PlayerAction.DeclareTsumo.class::isInstance)
                .map(PlayerAction.DeclareTsumo.class::cast).findFirst().orElseThrow();
    }
    private static void exactReceipt(TheMahjongDriver driver,int seat,PlayerAction.DeclareTsumo action) {
        var before=points(driver);var result=action.result();
        check(result.pointDeltas().get(seat)>0,"accepted self draw gives winner no receipt at seat "+seat+": "+result);
        var retired=driver.bloodBattleState().winners();
        for(int payer=0;payer<before.size();payer++) {
            if(payer==seat)continue;
            check(retired.contains(payer)?result.pointDeltas().get(payer)==0:result.pointDeltas().get(payer)<0,
                    "self-draw payment omitted a survivor or charged an exited winner at seat "+payer);
        }
        act(driver,seat,action);
        var after=points(driver);
        for(int payer=0;payer<before.size();payer++)check(after.get(payer)==before.get(payer)+result.pointDeltas().get(payer),
                "actual balance does not match accepted payment at seat "+payer);
        driver.advance(.05);check(points(driver).equals(after),"accepted self draw paid a second time");
    }
    private static void consecutiveReceipts() {
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN)
            for(int dealer=0;dealer<preset.playerCount();dealer++)for(int honba:new int[]{0,2}) {
                var driver=fixture(preset,dealer,honba,3,-1);int count=preset.playerCount();
                for(int winner=0;winner<count-1;winner++) {
                    if(winner>0)act(driver,winner,PlayerAction.Draw.INSTANCE);
                    exactReceipt(driver,winner,tsumo(driver,winner));
                    check(driver.currentRound().riichiSticks()==0,"visible deposits were not collected");
                    driver=reload(driver);
                }
            }
    }
    private static void retiredResponsibilitySeat() {
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN)
            for(int honba:new int[]{0,2}) {
                var driver=fixture(preset,0,honba,0,0);
                exactReceipt(driver,0,tsumo(driver,0));driver=reload(driver);
                act(driver,1,PlayerAction.Draw.INSTANCE);
                var action=tsumo(driver,1);
                check(action.result().yakuman().contains(Yakuman.DAISANGEN),"responsibility fixture did not score daisangen");
                int each=8000+honba*100;
                if(preset.playerCount()==3&&!driver.rules().tsumoLoss())each+=4000;
                int expected=each*(preset.playerCount()-2);
                check(action.result().pointDeltas().get(1)==expected,
                        "retired responsibility payer erased survivor payments: expected "+expected+" got "+action.result().pointDeltas());
                exactReceipt(driver,1,action);reload(driver);
            }
    }

    private static TheMahjongDriver turn(TheMahjongDriver previous,int seat,List<TheMahjongPlayer> players,
            TheMahjongTile drawn,List<TheMahjongTile> rinshan) {
        var old=previous.match();var r=previous.currentRound();
        var round=new TheMahjongRound(r.roundWind(),r.handNumber(),r.honba(),r.riichiSticks(),r.dealerSeat(),
                TheMahjongRound.State.TURN,seat,-1,TheMahjongRound.ActiveTile.drawn(drawn),r.liveWall(),rinshan,
                r.doraIndicators(),r.uraDoraIndicators(),r.revealedDoraCount(),r.pendingKanDoraReveals(),players,List.of());
        var match=new TheMahjongMatch(old.playerCount(),old.startingPoints(),old.targetPoints(),old.roundCount(),
                TheMahjongMatch.State.IN_ROUND,old.tileSet(),old.ruleSet(),old.completedRounds(),round);
        var driver=new TheMahjongDriver(match,humans(old.playerCount()),new Random(7),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(seat),false,OptionalInt.empty()));
        driver.restoreBloodBattleState(previous.bloodBattleState().snapshot());return driver;
    }
    private static TheMahjongPlayer withHand(TheMahjongPlayer old,List<TheMahjongTile> hand,List<TheMahjongMeld> melds) {
        return new TheMahjongPlayer(old.points(),old.riichiState(),old.ippatsuEligible(),hand,melds,
                old.discards(),old.temporaryFuritenTiles(),old.riichiPermanentFuriten(),old.kitaCount());
    }
    private static List<TheMahjongTile> numbered(int suit,String ranks) {
        var tiles=new ArrayList<TheMahjongTile>();for(char rank:ranks.toCharArray())tiles.add(tile(suit,rank-'0'));return tiles;
    }
    private static PlayerAction action(TheMahjongDriver driver,int seat,Class<? extends PlayerAction> type) {
        return driver.legalActions(seat).stream().filter(type::isInstance).findFirst()
                .orElseThrow(()->new AssertionError("missing "+type.getSimpleName()+" in "+driver.currentPhase()));
    }
    private static List<Integer> acceptedRon(TheMahjongDriver driver,int winner,boolean chankan) {
        var before=points(driver);var claims=(MatchPhase.AwaitingClaims)driver.currentPhase();
        var action=action(driver,winner,chankan?PlayerAction.DeclareChankan.class:PlayerAction.DeclareRon.class);
        var result=action instanceof PlayerAction.DeclareRon ron?ron.result():((PlayerAction.DeclareChankan)action).result();
        check(result.pointDeltas().get(winner)>0,"later ron failed to collect points");
        for(int seat:claims.pendingSeats())driver.submitAction(seat,seat==winner?action:PlayerAction.Pass.INSTANCE);
        driver.advance(.05);var after=points(driver);
        for(int seat=0;seat<before.size();seat++)check(after.get(seat)==before.get(seat)+result.pointDeltas().get(seat),
                "later ron balance differs from accepted score at "+seat);
        check(after.getFirst().equals(before.getFirst()),"retired winner was charged for later ron");
        driver.advance(.05);check(points(driver).equals(after),"later ron paid twice");
        reload(driver);return result.pointDeltas();
    }
    private static void responsibilityRonAndActivePao() {
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN)
            for(int honba:new int[]{0,2})for(int responsibility:new int[]{0,2}) {
                var driver=fixture(preset,0,honba,0,responsibility);
                exactReceipt(driver,0,tsumo(driver,0));driver=reload(driver);
                // The live discarder is seat 2; pao is either that live seat or exited seat 0.
                var players=new ArrayList<>(driver.currentRound().players());
                var hand=new ArrayList<>(players.get(2).currentHand());hand.add(tile(2,9));
                players.set(2,withHand(players.get(2),hand,List.of()));
                driver=turn(driver,2,players,tile(2,9),driver.currentRound().rinshanTiles());
                act(driver,2,new PlayerAction.Discard(tile(2,9)));
                var deltas=acceptedRon(driver,1,false);
                int expected=32000+honba*(preset.playerCount()-1)*100;
                check(deltas.get(1)==expected&&deltas.get(2)==-expected,
                        "retired pao reduced ron receipt or live-pao ron changed: "+deltas);

                // A responsibility seat still in play continues paying the full original tsumo.
                driver=fixture(preset,0,honba,0,2);exactReceipt(driver,0,tsumo(driver,0));
                act(driver,1,PlayerAction.Draw.INSTANCE);var tsumo=tsumo(driver,1);var before=points(driver);
                int gain=(preset.playerCount()==4?32000:24000)+honba*(preset.playerCount()-1)*100;
                check(tsumo.result().pointDeltas().get(1)==gain&&tsumo.result().pointDeltas().get(2)==-gain
                        &&tsumo.result().pointDeltas().getFirst()==0,"live responsibility payments changed");
                act(driver,1,tsumo);
                check(points(driver).get(1)==before.get(1)+gain&&points(driver).get(2)==before.get(2)-gain,
                        "live-pao self draw was not transferred");reload(driver);
            }
    }
    private static void laterNormalRonAndKanWins() {
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN) {
            var driver=fixture(preset,0,2,0,-1);exactReceipt(driver,0,tsumo(driver,0));
            var players=new ArrayList<>(driver.currentRound().players());
            var hand=new ArrayList<>(players.get(2).currentHand());hand.add(tile(4,2));
            players.set(2,withHand(players.get(2),hand,List.of()));
            driver=turn(driver,2,players,tile(4,2),driver.currentRound().rinshanTiles());
            act(driver,2,new PlayerAction.Discard(tile(4,2)));acceptedRon(driver,1,false);

            driver=fixture(preset,0,2,0,-1);exactReceipt(driver,0,tsumo(driver,0));
            players=new ArrayList<>(driver.currentRound().players());var added=tile(1,4);
            var own=numbered(2,"1234567899");own.add(added);
            var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,added),0,0,0);
            players.set(2,withHand(players.get(2),own,List.of(pon)));
            var waiting=numbered(1,"12356");waiting.addAll(numbered(2,"12367899"));
            players.set(1,withHand(players.get(1),waiting,List.of()));
            driver=turn(driver,2,players,added,driver.currentRound().rinshanTiles());
            act(driver,2,action(driver,2,PlayerAction.Kakan.class));acceptedRon(driver,1,true);

            driver=fixture(preset,0,2,0,-1);exactReceipt(driver,0,tsumo(driver,0));
            players=new ArrayList<>(driver.currentRound().players());
            own=numbered(1,"1111234567");own.addAll(numbered(2,"89"));own.addAll(numbered(4,"11"));
            players.set(1,withHand(players.get(1),own,List.of()));
            driver=turn(driver,1,players,tile(1,1),Collections.nCopies(4,tile(2,7)));
            act(driver,1,action(driver,1,PlayerAction.Ankan.class));passClaims(driver);
            if(driver.currentPhase() instanceof MatchPhase.AwaitingDraw)act(driver,1,PlayerAction.Draw.INSTANCE);
            var tsumo=tsumo(driver,1);
            check(tsumo.result().yaku().contains(com.themahjong.yaku.NonYakuman.RINSHAN_KAIHOU),
                    "blood-battle rinshan fixture did not score rinshan");
            exactReceipt(driver,1,tsumo);reload(driver);
        }
    }
    public static void main(String[] args) {
        consecutiveReceipts();retiredResponsibilitySeat();responsibilityRonAndActivePao();laterNormalRonAndKanWins();
        System.out.println("PASS: later tsumo, ron, chankan and rinshan transfer points after winner retirement, including every riichi preset/dealer, honba, deposits, reload and retired/live pao");
    }
}
