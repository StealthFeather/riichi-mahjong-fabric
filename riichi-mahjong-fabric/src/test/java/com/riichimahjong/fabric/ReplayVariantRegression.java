package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.replay.ReplayRecord;
import com.riichimahjong.replay.ReplayRecorder;
import com.themahjong.*;
import com.themahjong.TheMahjongTile.Suit;
import com.themahjong.driver.*;
import com.themahjong.sichuan.SichuanRules;
import com.themahjong.sichuan.SichuanState;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Real accepted variant actions must survive replay capture, NBT restart and frame restore. */
public final class ReplayVariantRegression {
    private static final HolderLookup.Provider REGISTRIES=HolderLookup.Provider.create(Stream.empty());
    private static List<MahjongPlayerInterface> humans() {
        return Stream.generate(()->(MahjongPlayerInterface)new MahjongHumanPlayer()).limit(4).toList();
    }
    private static ReplayRecorder recorder(TheMahjongDriver driver) {
        var players=IntStream.range(0,4).mapToObj(i->new ReplayRecord.Participant(i,null,"BOT",0)).toList();
        var result=new ReplayRecorder("test",players);
        driver.setStateObserver(result::capture);
        result.capture(driver);
        return result;
    }
    private static List<ReplayRecord.Frame> frames(ReplayRecorder recorder) {
        return ReplayRecord.fromNbt(recorder.toNbt()).frames();
    }
    private static ReplayRecord.Frame last(ReplayRecorder recorder) {
        return frames(recorder).getLast();
    }
    private static void exchange(boolean sichuan) {
        var preset=sichuan?RuleSetPreset.SICHUAN_4P:RuleSetPreset.MAHJONG_SOUL_4P;
        var driver=new TheMahjongDriver(preset.newMatch(MatchLength.SOUTH,true,true),humans(),new Random(3));
        driver.startMatch();
        var replay=recorder(driver);
        var initial=frames(replay).size();
        var hand=driver.currentRound().players().getFirst().currentHand();
        var tile=hand.stream().filter(t->t.suit().isNumber() && !com.themahjong.variants.Jokers.isJoker(t)).findFirst().orElseThrow();
        check(driver.toggleExchangeTile(0,tile),"exchange selection failed");
        check(frames(replay).size()==initial+1,"exchange selection was not recorded");
        var restored=last(replay).restore(REGISTRIES);
        check(restored.exchangeSelection(0).equals(List.of(tile)),"partial selection was lost in replay NBT");
        var copied=ReplayRecorder.fromNbt(replay.toNbt());
        copied.capture(driver);
        check(frames(copied).size()==frames(replay).size(),"reload duplicated an identical state");
        check(driver.clearExchangeSelection(0),"exchange selection could not clear");
        check(frames(replay).size()==initial+2 && last(replay).restore(REGISTRIES).exchangeSelection(0).isEmpty(),
                "selection clear was not recorded and restored");

        var displayed = driver.currentRound().handDisplayOrder(0);
        var equal = displayed.stream().filter(t -> t.suit().isNumber() && Collections.frequency(displayed, t) > 1)
                .findFirst().orElseThrow();
        int chosen = displayed.lastIndexOf(equal);
        var beforePick = frames(replay).size();
        check(driver.toggleExchangeSlot(0, chosen) && frames(replay).size() == beforePick + 1,
                "clicking a particular copy did not produce a replay frame");
        var recovered = last(replay).restore(REGISTRIES);
        check(recovered.exchangePicks(0).size() == 1 && recovered.exchangePicks(0).getFirst().indexIn(displayed) == chosen,
                "replay marked a different copy from the clicked tile");
        check(driver.toggleExchangeSlot(0, chosen) && last(replay).restore(REGISTRIES).exchangePicks(0).isEmpty(),
                "replay retained a deselected copy");

        if(sichuan) {
            // Sichuan state can change without replacing the match or phase.
            var unchangedMatch=driver.match();
            var before=frames(replay).size();
            check(driver.sichuanState().toggleExchangeTile(driver.currentRound(),0,tile),
                    "direct Sichuan selection failed");
            check(driver.match()==unchangedMatch,"fixture unexpectedly replaced the match");
            replay.capture(driver);
            check(frames(replay).size()==before+1
                            && last(replay).restore(REGISTRIES).exchangeSelection(0).equals(List.of(tile)),
                    "mutable state change was dropped when match and phase were unchanged");
            check(driver.clearExchangeSelection(0),"direct Sichuan selection could not clear");
            for(int seat=0;seat<4;seat++) {
                var tiles=driver.currentRound().players().get(seat).currentHand();
                List<TheMahjongTile> choice=List.of();
                for(var suit:List.of(Suit.MANZU,Suit.PINZU,Suit.SOUZU)) {
                    var group=tiles.stream().filter(t->t.suit()==suit&&!com.themahjong.variants.Jokers.isJoker(t)).limit(3).toList();
                    if(group.size()==3) { choice=group;break; }
                }
                check(choice.size()==3,"missing Sichuan exchange suit");
                for(var selected:choice)check(driver.toggleExchangeTile(seat,selected),"Sichuan exchange tile failed");
                check(driver.confirmExchange(seat),"Sichuan exchange confirmation failed");
            }
            check(driver.chooseVoidSuit(0,Suit.MANZU),"Sichuan void suit selection failed");
            check(last(replay).restore(REGISTRIES).sichuanState().voidSuit(0)==Suit.MANZU,
                    "Sichuan void suit missing from replay frame");
        }
    }
    private static TheMahjongTile tile(Suit suit,int rank){return new TheMahjongTile(suit,rank,false);}
    private static List<TheMahjongTile> waiting() {
        var hand=new ArrayList<TheMahjongTile>();
        for(char n:"1234569".toCharArray())hand.add(tile(Suit.MANZU,n-'0'));
        for(char n:"234678".toCharArray())hand.add(tile(Suit.PINZU,n-'0'));
        return hand;
    }
    private static TheMahjongPlayer player(List<TheMahjongTile> hand) {
        return new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(),List.of(),List.of());
    }
    private static void sichuanBlood() {
        var complete=new ArrayList<>(waiting());complete.add(tile(Suit.MANZU,9));
        var base=SichuanRules.newMatch(2,false,false,true);
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,TheMahjongRound.State.TURN,
                0,-1,TheMahjongRound.ActiveTile.drawn(tile(Suit.MANZU,9)),Collections.nCopies(20,tile(Suit.MANZU,9)),
                List.of(),List.of(),List.of(),0,List.of(player(complete),player(waiting()),player(waiting()),player(waiting())),List.of());
        var match=new TheMahjongMatch(4,base.startingPoints(),base.targetPoints(),2,
                TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),round);
        var driver=new TheMahjongDriver(match,humans(),new Random(5),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
        driver.restoreSichuanState(new SichuanState.Snapshot(SichuanState.Stage.PLAY,1,
                List.of(List.of(),List.of(),List.of(),List.of()),Set.of(),List.of(2,2,2,2),
                List.of(),List.of(),false,-1));
        var replay=recorder(driver);
        var win=driver.legalActions(0).stream().filter(PlayerAction.DeclareTsumo.class::isInstance).findFirst().orElseThrow();
        driver.submitAction(0,win);driver.advance(.05);
        check(driver.sichuanState().wins().size()==1,"blood win did not resolve");
        var restored=last(replay).restore(REGISTRIES);
        check(restored.sichuanState().wins().equals(driver.sichuanState().wins()),"blood winner missing from replay frame");
        check(restored.currentRound().players().stream().map(TheMahjongPlayer::points).toList()
                .equals(driver.currentRound().players().stream().map(TheMahjongPlayer::points).toList()),
                "blood settlement changed in replay");
    }
    public static void main(String[] args) {
        exchange(false);exchange(true);sichuanBlood();
        System.out.println("PASS: exchange selections, Sichuan void suit and continuing blood winner survive replay capture and NBT restore");
    }
}
