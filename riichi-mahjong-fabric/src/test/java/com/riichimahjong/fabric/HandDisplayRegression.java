package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.driver.*;
import java.util.*;
import java.util.stream.IntStream;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Accepted rule actions must leave the selected physical slot empty until the ten-tick deadline. */
public final class HandDisplayRegression {
    private static TheMahjongDriver fixture(){return fixture(tile(0,2));}
    private static TheMahjongDriver fixture(TheMahjongTile draw){
        var hand=new ArrayList<>(waiting(2));hand.add(draw);
        var players=List.of(player(hand),player(waiting(1)),player(waiting(0)),player(waiting(2)));
        var indicators=Collections.nCopies(5,tile(4,3));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,TheMahjongRound.State.TURN,0,-1,
                TheMahjongRound.ActiveTile.drawn(draw),Collections.nCopies(30,tile(4,2)),
                Collections.nCopies(4,tile(4,3)),indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                TheMahjongTileSet.standardRiichi(false),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        return new TheMahjongDriver(match,players.stream().map(p->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),
                new Random(1),new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
    }
    private static List<Integer> contiguous(int count){return IntStream.range(0,count).boxed().toList();}
    private static void sameLayout(HandDisplayState state,int seat,List<Integer> slots,List<Integer> gaps,String message){
        check(state.slots(seat).equals(slots)&&state.gapStarts(seat).equals(gaps),message+": slots="+state.slots(seat)+" gaps="+state.gapStarts(seat));
        check(state.hand(seat).size()==state.slots(seat).size(),"tile/slot lists differ in size");
        check(new HashSet<>(state.slots(seat)).size()==state.slots(seat).size(),"physical slots overlap");
    }
    private static double position(int slot,List<Integer> gaps){return slot*.073+gaps.stream().filter(start->start<=slot).count()*((11.0/16*.10)*4/3-.073);}

    private static void holeAndDeadline(){
        var d=fixture();var display=new HandDisplayState();display.update(d.currentRound(),100);
        check(display.hand(0).getLast().equals(tile(0,2))&&display.separatedSlot(0)==13,"draw must start at physical slot thirteen");
        var before=display.hand(0);int clicked=before.indexOf(tile(2,9));display.recordDiscardSlot(0,clicked);
        sameLayout(display,0,contiguous(14),List.of(13),"queued click changed layout before acceptance");
        act(d,0,new PlayerAction.Discard(tile(2,9)));display.update(d.currentRound(),101);
        var expected=new ArrayList<>(before);expected.remove(clicked);
        var slots=new ArrayList<>(contiguous(14));slots.remove(clicked);
        check(display.hand(0).equals(expected),"discard immediately sorted surviving tiles");
        sameLayout(display,0,slots,List.of(13),"discard failed to leave its original slot empty");
        check(display.separatedSlot(0)==13,"retained drawn tile moved into the thirteenth compact-list position");
        int canonical=display.canonicalIndex(0,display.hand(0).size()-1,d.currentRound());
        check(d.currentRound().handDisplayOrder(0).get(canonical).equals(tile(0,2)),"sparse-layout click maps to a different tile");
        display.update(d.currentRound(),110);sameLayout(display,0,slots,List.of(13),"hole collapsed before ten ticks");
        display.update(d.currentRound(),111);
        check(display.hand(0).equals(d.currentRound().handDisplayOrder(0))&&!display.separated(0),"hand did not sort at ten ticks");
        sameLayout(display,0,contiguous(13),List.of(),"ten-tick deadline did not collapse the hole");
    }

    private static void duplicateSelection(){
        var duplicate=tile(2,1);var d=fixture(duplicate);var display=new HandDisplayState();display.update(d.currentRound(),200);
        check(display.hand(0).getFirst().equals(duplicate)&&display.hand(0).getLast().equals(duplicate),"duplicate draw fixture incorrect");
        display.recordDiscardSlot(0,0);act(d,0,new PlayerAction.Discard(duplicate));display.update(d.currentRound(),201);
        sameLayout(display,0,IntStream.range(1,14).boxed().toList(),List.of(13),"discarding matching ordinary copy removed the drawn copy's slot");
        check(display.separatedSlot(0)==13&&display.hand(0).getLast().equals(duplicate),"same-identity draw did not remain at its original position");

        d=fixture(duplicate);display.clear();display.update(d.currentRound(),220);
        display.recordDiscardSlot(0,13);act(d,0,new PlayerAction.Discard(duplicate));display.update(d.currentRound(),221);
        sameLayout(display,0,contiguous(13),List.of(),"tsumogiri removed the ordinary matching copy instead");
        check(!display.separated(0)&&display.hand(0).getFirst().equals(duplicate),"tsumogiri left a separation marker on the ordinary copy");

        d=fixture();display.clear();display.update(d.currentRound(),240);
        int east=display.hand(0).indexOf(tile(3,1))+1;display.recordDiscardSlot(0,east);
        act(d,0,new PlayerAction.Discard(tile(3,1)));display.update(d.currentRound(),241);
        var expected=new ArrayList<>(contiguous(14));expected.remove(east);
        sameLayout(display,0,expected,List.of(13),"clicking the middle of three equal copies emptied the first copy's slot");

        d=fixture(duplicate);display.clear();display.update(d.currentRound(),260);
        display.recordDiscardSlot(0,0);act(d,0,new PlayerAction.Discard(tile(2,9)));display.update(d.currentRound(),261);
        expected=new ArrayList<>(contiguous(14));expected.remove(Integer.valueOf(8));
        sameLayout(display,0,expected,List.of(13),"unaccepted stale slot selection overrode a different accepted discard");

        d=fixture(duplicate);display.clear();display.update(d.currentRound(),280);
        act(d,0,new PlayerAction.Discard(duplicate));display.update(d.currentRound(),281);
        sameLayout(display,0,contiguous(13),List.of(),"BOT fallback should remove a matching drawn copy");
    }

    private static void rapidDrawsAndIndependentSeats(){
        var d=fixture();var display=new HandDisplayState();display.update(d.currentRound(),300);
        display.recordDiscardSlot(0,8);act(d,0,new PlayerAction.Discard(tile(2,9)));display.update(d.currentRound(),301);
        var originalSlots=display.slots(0);var originalGaps=display.gapStarts(0);
        passClaims(d);display.update(d.currentRound(),302);
        for(int seat=1;seat<4;seat++){
            act(d,seat,PlayerAction.Draw.INSTANCE);display.update(d.currentRound(),302);
            check(display.separated(seat)&&display.hand(seat).getLast().equals(tile(4,2)),"another seat's draw is not separated");
            display.recordDiscardSlot(seat,display.hand(seat).size()-1);
            act(d,seat,new PlayerAction.Discard(tile(4,2)));display.update(d.currentRound(),302);passClaims(d);display.update(d.currentRound(),302);
        }
        act(d,0,PlayerAction.Draw.INSTANCE);display.update(d.currentRound(),302);
        var extended=new ArrayList<>(originalSlots);extended.add(14);
        sameLayout(display,0,extended,List.of(13,14),"rapid redraw reused the discarded slot or moved the previous draw");
        for(int slot:originalSlots)check(Math.abs(position(slot,originalGaps)-position(slot,display.gapStarts(0)))<1e-9,"rapid redraw moved a surviving tile");
        check(display.separatedSlot(0)==14,"latest rapid draw has no separate physical slot");
        int man2=display.hand(0).indexOf(tile(0,2));
        check(!d.currentRound().handDisplayOrder(0).get(man2).equals(tile(0,2)),"rapid redraw fixture does not distinguish displayed and canonical indices");
        int canonical=display.canonicalIndex(0,man2,d.currentRound());
        check(d.currentRound().handDisplayOrder(0).get(canonical).equals(tile(0,2)),"rapid redraw display index selects the wrong tile");
        display.recordDiscardSlot(0,man2);act(d,0,new PlayerAction.Discard(tile(0,2)));display.update(d.currentRound(),303);
        extended.remove(Integer.valueOf(13));sameLayout(display,0,extended,List.of(13,14),"second discard compacted either pending hole");
        check(display.separatedSlot(0)==14,"second discard moved the surviving rapid draw");
        display.update(d.currentRound(),312);sameLayout(display,0,extended,List.of(13,14),"latest discard's hole collapsed before ten ticks");
        display.update(d.currentRound(),313);sameLayout(display,0,contiguous(13),List.of(),"latest deadline failed to merge both pending holes");
        check(display.hand(0).equals(d.currentRound().handDisplayOrder(0)),"rapid redraw's settled hand is not sorted");
        display.clear();display.update(d.currentRound(),314);
        sameLayout(display,0,contiguous(13),List.of(),"new driver reused sparse slots");
        check(display.canonicalIndex(0,-1,d.currentRound())==-1&&display.canonicalIndex(0,99,d.currentRound())==-1,"invalid slots accepted");
    }

    private static void queuedDuplicateAcrossDeadline(){
        var duplicate=tile(2,1);var d=fixture(duplicate);var display=new HandDisplayState();display.update(d.currentRound(),400);
        display.recordDiscardSlot(0,8);act(d,0,new PlayerAction.Discard(tile(2,9)));display.update(d.currentRound(),401);
        passClaims(d);display.update(d.currentRound(),402);
        for(int seat=1;seat<4;seat++){
            act(d,seat,PlayerAction.Draw.INSTANCE);display.update(d.currentRound(),402);
            act(d,seat,new PlayerAction.Discard(tile(4,2)));display.update(d.currentRound(),402);passClaims(d);display.update(d.currentRound(),402);
        }
        act(d,0,PlayerAction.Draw.INSTANCE);display.update(d.currentRound(),402);
        int selected=display.hand(0).lastIndexOf(duplicate);check(display.slots(0).get(selected)==13,"deadline fixture lost its retained duplicate draw");
        display.recordDiscardSlot(0,selected);
        display.update(d.currentRound(),411);sameLayout(display,0,contiguous(14),List.of(13),"older deadline failed to compact before the queued discard");
        act(d,0,new PlayerAction.Discard(duplicate));display.update(d.currentRound(),411);
        var expected=new ArrayList<>(contiguous(14));expected.remove(Integer.valueOf(1));
        sameLayout(display,0,expected,List.of(13),"queued duplicate lost its selected occurrence across an older deadline");
    }

    public static void main(String[] args){
        holeAndDeadline();duplicateSelection();rapidDrawsAndIndependentSeats();queuedDuplicateAcrossDeadline();
        System.out.println("PASS: ten-tick empty discard slots, exact duplicate selection, sparse click mapping and rapid redraw stability");
    }
}
