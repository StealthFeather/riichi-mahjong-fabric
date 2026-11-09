package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.driver.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Exercise the display timer against accepted rule actions, including click-slot translation. */
public final class HandDisplayRegression {
    private static TheMahjongDriver fixture(){
        var hand=new ArrayList<>(waiting(2));hand.add(tile(0,2));
        var players=List.of(player(hand),player(waiting(1)),player(waiting(0)),player(waiting(2)));
        var indicators=Collections.nCopies(5,tile(4,3));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,TheMahjongRound.State.TURN,0,-1,
                TheMahjongRound.ActiveTile.drawn(tile(0,2)),Collections.nCopies(30,tile(4,2)),
                Collections.nCopies(4,tile(4,3)),indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                TheMahjongTileSet.standardRiichi(false),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        return new TheMahjongDriver(match,players.stream().map(p->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),
                new Random(1),new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
    }
    public static void main(String[] args){
        var d=fixture();var display=new HandDisplayState();display.update(d.currentRound(),100);
        check(display.hand(0).getLast().equals(tile(0,2))&&display.separated(0),"draw must start at the right");
        var before=display.hand(0);act(d,0,new PlayerAction.Discard(tile(2,9)));
        display.update(d.currentRound(),101);
        var expected=new ArrayList<>(before);expected.remove(tile(2,9));
        check(display.hand(0).equals(expected),"discard immediately sorted the remaining hand");
        check(display.separated(0),"remaining drawn tile lost its gap before sorting");
        check(!display.hand(0).equals(d.currentRound().handDisplayOrder(0)),"fixture must distinguish delayed and canonical order");
        int canonical=display.canonicalIndex(0,display.hand(0).size()-1,d.currentRound());
        check(d.currentRound().handDisplayOrder(0).get(canonical).equals(tile(0,2)),"display click maps to a different tile");
        display.update(d.currentRound(),120);
        check(display.hand(0).equals(expected)&&display.separated(0),"hand sorted before twenty ticks");
        display.update(d.currentRound(),121);
        check(display.hand(0).equals(d.currentRound().handDisplayOrder(0))&&!display.separated(0),"hand did not sort exactly at twenty ticks");

        d=fixture();display.clear();display.update(d.currentRound(),200);
        act(d,0,new PlayerAction.Discard(tile(0,2)));display.update(d.currentRound(),201);
        check(!display.separated(0),"tsumogiri left a gap on an ordinary hand tile");
        check(display.hand(0).size()==13&&!display.hand(0).contains(tile(0,2)),"discarded draw remains visible");

        d=fixture();display.clear();display.update(d.currentRound(),300);
        act(d,0,new PlayerAction.Discard(tile(2,9)));display.update(d.currentRound(),301);
        passClaims(d);act(d,1,PlayerAction.Draw.INSTANCE);display.update(d.currentRound(),302);
        check(display.separated(1)&&display.hand(1).getLast().equals(tile(4,2)),"next seat's draw must remain separate");
        check(display.separated(0)&&display.hand(0).getLast().equals(tile(0,2)),"next seat's draw erased the first seat's timer");
        display.clear();display.update(d.currentRound(),303);
        check(display.hand(0).equals(d.currentRound().handDisplayOrder(0))&&!display.separated(0),"new driver reused a pending display timer");
        check(display.canonicalIndex(0,-1,d.currentRound())==-1&&display.canonicalIndex(0,99,d.currentRound())==-1,"invalid slots accepted");
        System.out.println("PASS: twenty-tick discard sorting, separated draws, independent seat timers and click-slot mapping");
    }
}
