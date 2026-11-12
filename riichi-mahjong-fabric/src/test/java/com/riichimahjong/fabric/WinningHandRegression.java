package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.driver.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Accepted wins must retain the exact winning tile at the drawn-tile position, including instant BOT ron. */
public final class WinningHandRegression {
    private static TheMahjongDriver rig(int count,boolean open,TheMahjongTile draw) {
        var hand=new ArrayList<>(waiting(0));hand.set(hand.size()-1,tile(0,5));
        List<TheMahjongMeld> melds=List.of();
        if(open){hand.subList(9,12).clear();melds=List.of(new TheMahjongMeld.Pon(Collections.nCopies(3,tile(3,1)),0,1,0));}
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<count;seat++){
            var own=new ArrayList<>(hand);if(seat==0)own.add(draw);
            players.add(new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,own,melds,List.of(),List.of()));
        }
        var base=driver(players,Collections.nCopies(20,tile(4,2))).currentRound();
        var round=new TheMahjongRound(base.roundWind(),base.handNumber(),base.honba(),base.riichiSticks(),base.dealerSeat(),
                base.state(),0,-1,TheMahjongRound.ActiveTile.drawn(draw),base.liveWall(),base.rinshanTiles(),
                base.doraIndicators(),base.uraDoraIndicators(),1,players,List.of());
        var match=new TheMahjongMatch(count,25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                TheMahjongTileSet.standardRiichi(true),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        return new TheMahjongDriver(match,players.stream().map(p->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),
                new Random(1),new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
    }
    private static PlayerAction action(TheMahjongDriver d,int seat,Class<? extends PlayerAction> type) {
        return d.legalActions(seat).stream().filter(type::isInstance).findFirst().orElseThrow();
    }
    private static WinningHandDisplay capture(TheMahjongDriver d) {
        check(d.currentPhase() instanceof MatchPhase.RoundEnded,"fixture did not finish");
        var display=WinningHandDisplay.capture(d.winningTile(),d.currentPhase());
        check(display!=null,"accepted win lost its tile");return display;
    }
    private static void verify(TheMahjongDriver d,WinningHandDisplay display,int seat,TheMahjongTile winning,boolean selfDraw) {
        var round=d.currentRound();var hand=round.players().get(seat).currentHand();
        var layout=display.layout(round,d.currentPhase(),seat).orElseThrow();
        check(layout.order().getLast().equals(winning),"winning identity was sorted into the main hand");
        check(layout.order().size()==hand.size()+(selfDraw?0:1),"winning tile duplicated or missing");
        var expected=new ArrayList<>(hand);if(selfDraw)check(expected.remove(winning),"missing self draw");
        expected.sort(TheMahjongTile.DISPLAY_ORDER);expected.add(winning);
        check(layout.order().equals(expected),"ordinary hand order or red-five identity changed");
        check(layout.gapStarts().equals(List.of(layout.order().size()-1)),"winning tile has no separate rightmost gap");
        for(var dir:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
            int last=layout.slots().size()-1;var pos=new BlockPos(15,64,-20);
            var previous=TableLayout.handAnchor(pos,dir,TableLayout.handAlong(layout.slots().get(last-1),layout.gapStarts()),true);
            var at=TableLayout.handAnchor(pos,dir,TableLayout.handAlong(layout.slots().get(last),layout.gapStarts()),true);
            check(Math.abs(previous.distanceTo(at)-TableLayout.TILE_WIDTH-TableLayout.TILE_WIDTH/3)<1e-8,
                    "winning gap differs from one-third tile width: "+dir);
        }
        check(display.layout(round,new MatchPhase.BetweenRounds(),seat).isEmpty(),"winning layout leaked into next round");
        check(display.layout(round,new MatchPhase.RoundEnded(List.of()),seat).isEmpty(),"draw result acquired a winning tile");
        var output=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);display.write(output);
        var restored=WinningHandDisplay.read(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()),output.buildResult()));
        check(restored.equals(display)&&restored.layout(round,d.currentPhase(),seat).orElseThrow().equals(layout),
                "save/reload changed winning tile or gap");
        check(round.players().get(seat).currentHand().equals(hand),"display changed the rule hand");
    }
    private static void wins() {
        for(int count:new int[]{3,4})for(boolean open:new boolean[]{false,true})for(boolean red:new boolean[]{false,true}){
            var winning=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,red);
            var d=rig(count,open,winning);act(d,0,action(d,0,PlayerAction.DeclareTsumo.class));
            var display=capture(d);check(display.selfDraw(),"tsumo classified as ron");verify(d,display,0,winning,true);
            check(display.layout(d.currentRound(),d.currentPhase(),1).isEmpty(),"non-winner was exposed");

            for(boolean multiple:new boolean[]{false,true}){
                d=rig(count,open,winning);act(d,0,new PlayerAction.Discard(winning));
                var before=d.currentRound();
                d.submitAction(1,action(d,1,PlayerAction.DeclareRon.class));
                for(int seat=2;seat<count;seat++)d.submitAction(seat,multiple&&seat==2?action(d,seat,PlayerAction.DeclareRon.class):PlayerAction.Pass.INSTANCE);
                d.advance(.05);display=capture(d);check(!display.selfDraw(),"ron classified as tsumo");
                verify(d,display,1,winning,false);if(multiple)verify(d,display,2,winning,false);
                check(display.layout(d.currentRound(),d.currentPhase(),0).isEmpty(),"discarder acquired a winning layout");
                for(int seat=0;seat<count;seat++)check(d.currentRound().players().get(seat).currentHand().equals(before.players().get(seat).currentHand()),
                        "ron display appended a tile to the rule hand");
            }
        }
    }
    private static void instantRon() {
        var winning=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);var d=rig(4,false,winning);
        for(int seat=1;seat<4;seat++){
            final int index=seat;
            d.replacePlayer(seat,(request,seconds)->index==1
                    ?request.legalActions().stream().filter(a->a instanceof PlayerAction.DeclareRon).findFirst()
                    :Optional.of(PlayerAction.Pass.INSTANCE));
        }
        // Discard and BOT ron are both applied in this one advance: a pre-tick snapshot still says Drawn.
        act(d,0,new PlayerAction.Discard(winning));var display=capture(d);
        check(!display.selfDraw(),"same-frame BOT ron captured the discarder draw instead of the held discard");
        verify(d,display,1,winning,false);
    }
    private static void chankan() {
        var winning=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);var d=rig(4,false,winning);
        var base=d.currentRound();var sourceHand=new ArrayList<>(waiting(2));sourceHand.subList(0,3).clear();sourceHand.add(winning);
        var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,tile(0,5)),0,3,0);
        var players=new ArrayList<>(base.players());players.set(0,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,sourceHand,List.of(pon),List.of(),List.of()));
        var round=new TheMahjongRound(base.roundWind(),1,0,0,0,base.state(),0,-1,base.activeTile(),base.liveWall(),
                base.rinshanTiles(),base.doraIndicators(),base.uraDoraIndicators(),1,players,List.of());
        var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,TheMahjongTileSet.standardRiichi(true),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        d=new TheMahjongDriver(match,players.stream().map(p->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),new Random(1),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
        act(d,0,action(d,0,PlayerAction.Kakan.class));
        d.submitAction(1,action(d,1,PlayerAction.DeclareChankan.class));
        d.submitAction(2,PlayerAction.Pass.INSTANCE);d.submitAction(3,PlayerAction.Pass.INSTANCE);d.advance(.05);
        var display=capture(d);check(!display.selfDraw(),"chankan classified as tsumo");verify(d,display,1,winning,false);
    }
    public static void main(String[] args) {
        wins();instantRon();chankan();
        System.out.println("PASS: tsumo/ron/double ron/chankan keep the exact winning tile at right with one-third-width gap; open hands, red fives, 3/4 players and save/reload match");
    }
}
