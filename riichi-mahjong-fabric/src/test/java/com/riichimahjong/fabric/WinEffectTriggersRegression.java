package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.WinEffectContext;
import com.riichimahjong.fabric.wineffect.WinEffects;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.sichuan.SichuanRules;
import com.themahjong.sichuan.SichuanState;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Accepted rule events, including wins which continue a Sichuan blood battle, trigger once. */
public final class WinEffectTriggersRegression {
    private static TheMahjongDriver rig(int count,boolean open,TheMahjongTile drawn) {
        var waiting=new ArrayList<>(waiting(0));waiting.set(waiting.size()-1,tile(0,5));
        List<TheMahjongMeld> melds=List.of();
        if(open) {
            waiting.subList(9,12).clear();
            melds=List.of(new TheMahjongMeld.Pon(Collections.nCopies(3,tile(3,1)),0,1,0));
        }
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<count;seat++) {
            var hand=new ArrayList<>(waiting);if(seat==0)hand.add(drawn);
            players.add(new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,
                    hand,melds,List.of(),List.of()));
        }
        var base=driver(players,Collections.nCopies(20,tile(4,2))).currentRound();
        var round=new TheMahjongRound(base.roundWind(),1,0,0,0,base.state(),0,-1,
                TheMahjongRound.ActiveTile.drawn(drawn),base.liveWall(),base.rinshanTiles(),
                base.doraIndicators(),base.uraDoraIndicators(),1,players,List.of());
        return riichi(round,new MatchPhase.AwaitingDiscard(0));
    }
    private static TheMahjongDriver riichi(TheMahjongRound round,MatchPhase phase) {
        int count=round.players().size();
        var match=new TheMahjongMatch(count,25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                TheMahjongTileSet.standardRiichi(true),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        return new TheMahjongDriver(match,round.players().stream()
                .map(p->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),new Random(1),
                new TheMahjongDriver.Snapshot(phase,false,OptionalInt.empty()));
    }
    private static PlayerAction action(TheMahjongDriver driver,int seat,Class<? extends PlayerAction> type) {
        return driver.legalActions(seat).stream().filter(type::isInstance).findFirst()
                .orElseThrow(()->new AssertionError("missing "+type.getSimpleName()+" for seat "+seat));
    }
    private static WinEffectContext capture(TheMahjongDriver driver,MatchPhase before,int previousWins) {
        var round=driver.currentRound();
        var phase=driver.currentPhase();
        var result=WinEffectTriggers.capture(driver,before,previousWins,WinEffects.DEFAULT);
        check(driver.currentRound()==round&&driver.currentPhase()==phase,"capture changed rules or points");
        return result;
    }
    private static void verify(WinEffectContext result,TheMahjongTile tile,String source,int... seats) {
        check(result!=null,"accepted win did not produce an effect context");
        check(result.effectId().equals(WinEffects.DEFAULT),"selected effect was lost");
        check(result.source().slot().equals(source),"wrong source slot: "+result.source().slot()+" instead of "+source);
        int code=MahjongTileItems.codeForTile(tile);
        check(result.source().tileCode()==code,"source lost winning tile identity");
        check(result.winners().size()==seats.length,"wrong number of winners");
        for(int seat:seats)check(result.includes(seat),"missing winner "+seat);
        check(result.winners().stream().allMatch(w->w.tileCode()==code),"a winner lost the exact tile identity");
    }
    private static void riichiWins() {
        for(int count:new int[]{3,4})for(boolean open:new boolean[]{false,true})for(boolean red:new boolean[]{false,true}) {
            var winning=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,red);
            var driver=rig(count,open,winning);var before=driver.currentPhase();
            int last=driver.currentRound().players().getFirst().currentHand().size()-1;
            act(driver,0,action(driver,0,PlayerAction.DeclareTsumo.class));
            verify(capture(driver,before,0),winning,"hand/0/"+last,0);
            check(capture(driver,driver.currentPhase(),0)==null,"RoundEnded replays an accepted self draw");
            for(boolean multiple:new boolean[]{false,true}) {
                driver=rig(count,open,winning);act(driver,0,new PlayerAction.Discard(winning));
                before=driver.currentPhase();
                driver.submitAction(1,action(driver,1,PlayerAction.DeclareRon.class));
                for(int seat=2;seat<count;seat++)driver.submitAction(seat,
                        multiple&&seat==2?action(driver,seat,PlayerAction.DeclareRon.class):PlayerAction.Pass.INSTANCE);
                driver.advance(.05);
                verify(capture(driver,before,0),winning,"river/0/0",multiple?new int[]{1,2}:new int[]{1});
                check(capture(driver,driver.currentPhase(),0)==null,"RoundEnded replays an accepted ron");
            }
        }
    }
    private static void sameAdvanceBotRon() {
        var winning=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);
        var driver=rig(4,false,winning);var before=driver.currentPhase();
        for(int seat=1;seat<4;seat++) {
            final int winner=seat;
            driver.replacePlayer(seat,(request,seconds)->winner==1
                    ?request.legalActions().stream().filter(a->a instanceof PlayerAction.DeclareRon).findFirst()
                    :Optional.of(PlayerAction.Pass.INSTANCE));
        }
        act(driver,0,new PlayerAction.Discard(winning));
        verify(capture(driver,before,0),winning,"river/0/0",1);
    }
    private static void robAddedKan() {
        var winning=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);
        var driver=rig(4,false,winning);var base=driver.currentRound();
        var sourceHand=new ArrayList<>(waiting(2));sourceHand.subList(0,3).clear();sourceHand.add(winning);
        var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,tile(0,5)),0,3,0);
        var players=new ArrayList<>(base.players());
        players.set(0,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,
                sourceHand,List.of(pon),List.of(),List.of()));
        var round=new TheMahjongRound(base.roundWind(),1,0,0,0,base.state(),0,-1,base.activeTile(),
                base.liveWall(),base.rinshanTiles(),base.doraIndicators(),base.uraDoraIndicators(),1,players,List.of());
        driver=riichi(round,new MatchPhase.AwaitingDiscard(0));
        act(driver,0,action(driver,0,PlayerAction.Kakan.class));
        var before=driver.currentPhase();
        driver.submitAction(1,action(driver,1,PlayerAction.DeclareChankan.class));
        driver.submitAction(2,PlayerAction.Pass.INSTANCE);driver.submitAction(3,PlayerAction.Pass.INSTANCE);driver.advance(.05);
        verify(capture(driver,before,0),winning,"robbed/0",1);
    }
    private static void unrelatedAndDrawnRounds() {
        var driver=rig(4,false,tile(4,2));
        check(capture(driver,driver.currentPhase(),0)==null,"ordinary discard phase triggered a win effect");
        var base=driver.currentRound();
        var ended=new TheMahjongRound(base.roundWind(),1,0,0,0,TheMahjongRound.State.ENDED,0,-1,
                TheMahjongRound.ActiveTile.none(),List.of(),base.rinshanTiles(),base.doraIndicators(),
                base.uraDoraIndicators(),1,base.players(),List.of());
        driver=riichi(ended,new MatchPhase.RoundEnded(List.of()));
        check(capture(driver,new MatchPhase.AwaitingDraw(0,false),0)==null,"exhaustive draw triggered a win effect");
    }
    private static List<TheMahjongTile> sichuanHand(boolean complete) {
        var tiles=new ArrayList<TheMahjongTile>();
        for(char rank:"1234569".toCharArray())tiles.add(tile(0,rank-'0'));
        for(char rank:"234678".toCharArray())tiles.add(tile(1,rank-'0'));
        if(complete)tiles.add(tile(0,9));
        return tiles;
    }
    private static TheMahjongDriver sichuan() {
        var base=SichuanRules.newMatch(2,false,false,true);
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<4;seat++)players.add(player(sichuanHand(seat==0)));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,TheMahjongRound.State.TURN,
                0,-1,TheMahjongRound.ActiveTile.drawn(tile(0,9)),Collections.nCopies(20,tile(0,9)),
                List.of(),List.of(),List.of(),0,players,List.of());
        var match=new TheMahjongMatch(4,base.startingPoints(),base.targetPoints(),2,
                TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),round);
        var driver=new TheMahjongDriver(match,players.stream().map(p->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),
                new Random(1),new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
        driver.restoreSichuanState(new SichuanState.Snapshot(SichuanState.Stage.PLAY,1,
                List.of(List.of(),List.of(),List.of(),List.of()),Set.of(),List.of(2,2,2,2),
                List.of(),List.of(),false,-1));
        return driver;
    }
    private static void continuingSichuanWins() {
        var driver=sichuan();
        for(int seat=0;seat<3;seat++) {
            if(seat>0)act(driver,seat,PlayerAction.Draw.INSTANCE);
            var before=driver.currentPhase();
            int previous=driver.sichuanState().wins().size();
            act(driver,seat,action(driver,seat,PlayerAction.DeclareTsumo.class));
            verify(capture(driver,before,previous),tile(0,9),"hand/"+seat+"/13",seat);
            check(driver.sichuanState().wins().size()==seat+1,"win capture altered blood-battle winners");
            check(capture(driver,driver.currentPhase(),seat+1)==null,"Sichuan winner replays without another accepted win");
            if(seat<2)check(!(driver.currentPhase() instanceof MatchPhase.RoundEnded),"early Sichuan fixture ended the hand");
            else check(driver.currentPhase() instanceof MatchPhase.RoundEnded,"third Sichuan win did not end the hand");
        }
        check(capture(driver,new MatchPhase.AwaitingDraw(2,false),3)==null,"final Sichuan settlement replays earlier wins");
    }
    private static void simultaneousSichuanRon() {
        var driver=sichuan();act(driver,0,new PlayerAction.Discard(tile(0,9)));
        var before=driver.currentPhase();
        driver.submitAction(1,action(driver,1,PlayerAction.DeclareRon.class));
        driver.submitAction(2,action(driver,2,PlayerAction.DeclareRon.class));
        driver.submitAction(3,PlayerAction.Pass.INSTANCE);driver.advance(.05);
        verify(capture(driver,before,0),tile(0,9),"river/0/0",1,2);
        var history=WinEffectTriggers.history(driver,WinEffects.DEFAULT);
        check(history.size()==2,"historical double ron lost accepted winners");
        verify(history.get(0),tile(0,9),"robbed/0",1);
        verify(history.get(1),tile(0,9),"robbed/0",2);
        check(!(driver.currentPhase() instanceof MatchPhase.RoundEnded),"double Sichuan ron should continue the blood battle");
        check(capture(driver,driver.currentPhase(),2)==null,"double Sichuan ron replays in its next turn");
    }
    public static void main(String[] args) {
        riichiWins();sameAdvanceBotRon();robAddedKan();unrelatedAndDrawnRounds();
        continuingSichuanWins();simultaneousSichuanRon();
        System.out.println("PASS: winning effects capture exact source/winners for 3p/4p tsumo, single/double ron, red fives, instant BOT ron, chankan and each continuing Sichuan win without replay");
    }
}
