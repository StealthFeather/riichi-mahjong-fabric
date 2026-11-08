package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.driver.*;
import java.util.*;

/** Deterministic regressions; run with test.cjs, without a Minecraft server. */
public final class RulesRegression {
    static TheMahjongTile tile(int suit,int rank){return new TheMahjongTile(TheMahjongTile.Suit.values()[suit],rank,false);}
    static List<TheMahjongTile> waiting(int suit){return List.of(tile(suit,1),tile(suit,2),tile(suit,3),tile(suit,4),tile(suit,5),tile(suit,6),tile(suit,7),tile(suit,8),tile(suit,9),tile(3,1),tile(3,1),tile(3,1),tile(4,1));}
    static TheMahjongPlayer player(List<TheMahjongTile> hand){return new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(),List.of(),List.of());}
    static TheMahjongDriver driver(List<TheMahjongPlayer> players,List<TheMahjongTile> wall){
        var indicators=Collections.nCopies(5,tile(4,3));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,TheMahjongRound.State.TURN,0,-1,TheMahjongRound.ActiveTile.drawn(tile(4,2)),wall,Collections.nCopies(4,tile(4,3)),indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(players.size(),25000,30000,2,TheMahjongMatch.State.IN_ROUND,TheMahjongTileSet.standardRiichi(false),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        return new TheMahjongDriver(match,players.stream().map(p->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),new Random(1),new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void act(TheMahjongDriver d,int seat,PlayerAction a){d.submitAction(seat,a);d.advance(.05);}
    static void passClaims(TheMahjongDriver d){
        for(int n=0;d.currentPhase() instanceof MatchPhase.AwaitingClaims c;n++){
            check(n<5,"claims did not finish");
            for(int seat:c.pendingSeats())if(d.legalActions(seat).contains(PlayerAction.Pass.INSTANCE))d.submitAction(seat,PlayerAction.Pass.INSTANCE);
            d.advance(.05);
        }
    }
    static void successiveRiichi(){
        var first=new ArrayList<>(waiting(0));first.add(tile(4,2));
        var d=driver(List.of(player(first),player(waiting(1)),player(waiting(2)),player(waiting(0))),Collections.nCopies(20,tile(4,2)));
        for(int seat=0;seat<3;seat++){
            if(seat>0)act(d,seat,PlayerAction.Draw.INSTANCE);
            var action=new PlayerAction.DiscardWithRiichi(tile(4,2));
            check(d.legalActions(seat).contains(action),"seat "+seat+" cannot riichi after another player's riichi");
            act(d,seat,action);passClaims(d);
            check(d.currentRound().riichiSticks()==seat+1,"riichi deposits were lost");
            check(d.currentRound().players().get(seat).points()==24000,"deposit not deducted for seat "+seat);
        }
    }
    static void calledRiichiDeposit(){
        var first=new ArrayList<>(waiting(0));first.add(tile(4,2));
        var caller=new ArrayList<>(waiting(1));caller.set(0,tile(4,2));caller.set(1,tile(4,2));
        var d=driver(List.of(player(first),player(caller),player(waiting(2)),player(waiting(0))),Collections.nCopies(20,tile(4,2)));
        act(d,0,new PlayerAction.DiscardWithRiichi(tile(4,2)));
        check(d.currentPhase() instanceof MatchPhase.AwaitingClaims,"declaration discard should be callable");
        var pon=d.legalActions(1).stream().filter(a->a instanceof PlayerAction.Pon).findFirst().orElseThrow();
        act(d,1,pon);passClaims(d);
        check(d.currentRound().riichiSticks()==1,"calling a riichi discard erased its deposit");
        check(d.currentRound().players().get(0).points()==24000,"calling a riichi discard refunded its depositor");
        check(d.currentRound().players().get(1).melds().size()==1,"pon was not applied");
        var discard=d.legalActions(1).stream().filter(a->a instanceof PlayerAction.Discard).findFirst().orElseThrow();
        act(d,1,discard);passClaims(d);
        act(d,2,PlayerAction.Draw.INSTANCE);
        check(d.legalActions(2).contains(new PlayerAction.DiscardWithRiichi(tile(4,2))),"another player lost riichi after a called declaration");
    }
    static void acceptedClaimsExpireAndLeave(){
        var first=new ArrayList<>(waiting(0));first.add(tile(4,2));
        var caller=new ArrayList<>(waiting(1));caller.set(0,tile(4,2));caller.set(1,tile(4,2));
        var d=driver(List.of(player(first),player(caller),player(caller),player(waiting(0))),Collections.nCopies(20,tile(4,2)));
        act(d,0,new PlayerAction.Discard(tile(4,2)));
        var pon=d.legalActions(1).stream().filter(a->a instanceof PlayerAction.Pon).findFirst().orElseThrow();
        act(d,1,pon);
        check(d.currentPhase() instanceof MatchPhase.AwaitingClaims,"other player must still be deciding");
        check(d.legalActions(1).isEmpty(),"accepted claim still exposes obsolete buttons");
        var old=d.currentRound();d.replacePlayer(1,com.themahjong.driver.bots.StupidActiveBot.humanLike());
        check(old==d.currentRound(),"leaving changed the current hand");
        check(d.legalActions(1).isEmpty(),"leaving erased the accepted claim");
        act(d,2,PlayerAction.Pass.INSTANCE);
        check(d.currentRound().players().get(1).melds().size()==1,"leaving lost an accepted pon");
    }
    static void meldDirections(){
        for(int players:new int[]{3,4})for(int seat=0;seat<players;seat++)for(int source=0;source<players;source++)if(source!=seat){
            var claimed=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);var normal=tile(0,5);
            var pon=new TheMahjongMeld.Pon(List.of(normal,claimed,normal),1,source,0);
            var arranged=MeldLayout.arrange(pon,seat,players);
            int relative=Math.floorMod(source-seat,players),expected=relative==players-1?0:relative==1?2:1;
            check(arranged.get(expected).sideways()&&arranged.get(expected).tile().redDora(),"pon direction or red identity wrong");
            var kakan=MeldLayout.arrange(new TheMahjongMeld.Kakan(pon,normal),seat,players);
            check(kakan.get(expected+1).stacked()&&kakan.get(expected+1).sideways(),"added kan not stacked on the called tile");
            var kan=MeldLayout.arrange(new TheMahjongMeld.Daiminkan(List.of(normal,claimed,normal,normal),1,source,0),seat,players);
            int kanIndex=relative==players-1?0:relative==1?3:1;
            check(kan.get(kanIndex).sideways()&&kan.get(kanIndex).tile().redDora(),"open kan direction wrong");
        }
        var chi=MeldLayout.arrange(new TheMahjongMeld.Chi(List.of(tile(0,1),tile(0,2),tile(0,3)),2,3,0),0,4);
        check(chi.get(0).sideways()&&chi.get(0).tile().rank()==3,"chi must identify the player on the left");
        var ankan=MeldLayout.arrange(new TheMahjongMeld.Ankan(Collections.nCopies(4,tile(0,1))),0,4);
        check(ankan.get(0).concealed()&&ankan.get(3).concealed()&&!ankan.get(1).concealed(),"concealed kan faces wrong");
    }
    public static void main(String[] args){
        successiveRiichi();System.out.println("PASS: three successive independent riichi declarations and deposits");
        calledRiichiDeposit();System.out.println("PASS: called declaration preserves deposit and later riichi availability");
        acceptedClaimsExpireAndLeave();System.out.println("PASS: accepted claims expire and survive BOT takeover");
        meldDirections();System.out.println("PASS: chi, pon and kan directions for every 3/4-player relative seat");
    }
}
