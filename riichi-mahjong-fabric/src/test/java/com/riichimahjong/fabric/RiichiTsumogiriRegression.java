package com.riichimahjong.fabric;

import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.ai.KobalabBot;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.variants.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** A real local controller must never receive a concealed-hand discard after an accepted riichi. */
public final class RiichiTsumogiriRegression {
    private static TheMahjongDriver fixture(RuleSetPreset preset,boolean variants,KobalabBot bot,TheMahjongTile draw) {
        var base=preset.newMatch(MatchLength.SOUTH,variants,variants,variants);int count=base.playerCount();
        var hand=new ArrayList<>(waiting(0));if(variants)hand.set(0,Jokers.TILE);hand.add(draw);
        var ready=new TheMahjongPlayer(24000,TheMahjongPlayer.RiichiState.RIICHI,true,hand,List.of(),
                List.of(new TheMahjongDiscard(tile(4,2),true)),List.of());
        var players=new ArrayList<TheMahjongPlayer>();var controllers=new ArrayList<MahjongPlayerInterface>();
        for(int seat=0;seat<count;seat++){players.add(seat==0?ready:player(waiting(1)));controllers.add(seat==0?bot:new MahjongHumanPlayer());}
        var indicators=Collections.nCopies(5,tile(4,3));
        var round=new TheMahjongRound(TheMahjongTile.Wind.SOUTH,1,0,1,0,TheMahjongRound.State.TURN,0,-1,
                TheMahjongRound.ActiveTile.drawn(draw),Collections.nCopies(20,tile(1,8)),Collections.nCopies(4,tile(1,9)),indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(count,base.startingPoints(),base.targetPoints(),2,TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),round);
        var driver=new TheMahjongDriver(match,controllers,new Random(1),new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
        if(variants)driver.restoreExchangeState(new ExchangeThreeState.Snapshot(count,1,
                java.util.stream.IntStream.range(0,count).mapToObj(i->List.<TheMahjongTile>of()).toList(),Set.of(),true));
        return driver;
    }
    private static void realBot(RuleSetPreset preset,boolean variants,TheMahjongTile draw) {
        try(var bot=new KobalabBot(BotDifficulty.NORMAL,true)) {
            var driver=fixture(preset,variants,bot,draw);var before=new ArrayList<>(driver.currentRound().players().get(0).currentHand());before.removeLast();
            int river=driver.currentRound().players().get(0).discards().size();
            for(int tick=0;tick<100&&driver.currentRound().players().get(0).discards().size()==river;tick++)driver.advance(.05);
            var after=driver.currentRound().players().get(0);
            check(after.discards().size()==river+1,"BOT did not act in deterministic thinking window");
            check(after.discards().getLast().tile().equals(draw),"riichi BOT discarded a concealed tile instead of draw: "+after.discards().getLast().tile()+" vs "+draw);
            check(after.currentHand().equals(before),"riichi BOT changed its fixed concealed hand");
            check(after.riichi(),"tsumogiri lost accepted riichi state");
        }
    }
    private static void legalBoundary(RuleSetPreset preset,boolean variants) {
        var drawn=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);
        try(var bot=new KobalabBot(BotDifficulty.NORMAL,true)) {
            var driver=fixture(preset,variants,bot,drawn);
            var discards=driver.legalActions(0).stream().filter(PlayerAction.Discard.class::isInstance).map(PlayerAction.Discard.class::cast).toList();
            check(discards.equals(List.of(new PlayerAction.Discard(drawn))),"accepted riichi exposes illegal non-drawn or differently red discards: "+discards);
            boolean rejected=false;try{driver.currentRound().discard(tile(4,1),driver.rules());}catch(IllegalStateException expected){rejected=true;}
            check(rejected,"round mutation bypass accepted-riichi discard lock");
        }
    }
    public static void main(String[] args) {
        // The reported combination uses the local variant policy, which previously preferred an isolated hand honor.
        realBot(RuleSetPreset.MAHJONG_SOUL_4P,true,tile(0,5));
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN) {
            legalBoundary(preset,false);legalBoundary(preset,true);
        }
        System.out.println("PASS: real local BOT in four-player south/joker/exchange/blood battle tsumogiris after riichi; all riichi profiles lock exact red/non-red drawn identity at driver and round boundaries");
    }
}
