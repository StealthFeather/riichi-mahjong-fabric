package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.TheMahjongTile.Suit;
import com.themahjong.driver.*;
import com.themahjong.driver.bots.StupidActiveBot;
import com.themahjong.variants.ExchangeThreeState;
import com.themahjong.variants.Jokers;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Exercise the optional exchange at the real riichi driver boundary, including saved setup. */
public final class ExchangeThreeRegression {
    private static List<MahjongPlayerInterface> humans(int count) {
        return java.util.stream.IntStream.range(0,count)
                .mapToObj(seat->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList();
    }
    private static TheMahjongDriver started(RuleSetPreset preset,boolean jokers,int seed) {
        var driver=new TheMahjongDriver(preset.newMatch(MatchLength.SOUTH,true,jokers),
                humans(preset.playerCount()),new Random(seed));
        driver.startMatch();return driver;
    }
    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(out,driver);
        var input=TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),out.buildResult());
        return DriverNbt.readDriver(input,humans(driver.match().playerCount()),new Random(17));
    }
    private static List<TheMahjongTile> choice(TheMahjongRound round,int seat) {
        var hand=round.players().get(seat).currentHand();
        var picked=new ArrayList<TheMahjongTile>();
        for(var suit:Suit.values()) {
            var tile=hand.stream().filter(t->t.suit()==suit&&!Jokers.isJoker(t)).findFirst();
            if(tile.isPresent())picked.add(tile.get());
            if(picked.size()==3)return List.copyOf(picked);
        }
        var remaining=new ArrayList<>(hand);
        picked.forEach(remaining::remove);
        for(var tile:remaining)if(!Jokers.isJoker(tile)&&picked.size()<3)picked.add(tile);
        if(picked.size()==3)return List.copyOf(picked);
        throw new AssertionError("initial hand has no three exchangeable ordinary tiles");
    }
    private static Map<TheMahjongTile,Integer> bag(List<TheMahjongTile> tiles) {
        var counts=new HashMap<TheMahjongTile,Integer>();
        tiles.forEach(t->counts.merge(t,1,Integer::sum));return counts;
    }
    private static void preserved(TheMahjongRound before,TheMahjongRound after) {
        check(before.liveWall().equals(after.liveWall()),"exchange changed or drew from the live wall");
        check(before.rinshanTiles().equals(after.rinshanTiles()),"exchange changed the rinshan wall");
        check(before.doraIndicators().equals(after.doraIndicators())&&before.uraDoraIndicators().equals(after.uraDoraIndicators())
                &&before.revealedDoraCount()==after.revealedDoraCount()
                &&before.pendingKanDoraReveals()==after.pendingKanDoraReveals(),"exchange changed dora/dead-wall state");
        check(before.roundWind()==after.roundWind()&&before.handNumber()==after.handNumber()
                &&before.dealerSeat()==after.dealerSeat()&&before.currentTurnSeat()==after.currentTurnSeat()
                &&before.honba()==after.honba()&&before.riichiSticks()==after.riichiSticks()
                &&before.state()==after.state()&&before.claimSourceSeat().equals(after.claimSourceSeat())
                &&before.activeTile().equals(after.activeTile())&&before.pendingDeltas().equals(after.pendingDeltas()),
                "exchange changed round progression, points settlement or the active tile");
        for(int seat=0;seat<before.players().size();seat++) {
            var a=before.players().get(seat);var b=after.players().get(seat);
            check(a.points()==b.points()&&a.riichiState()==b.riichiState()&&a.ippatsuEligible()==b.ippatsuEligible()
                    &&a.melds().equals(b.melds())&&a.discards().equals(b.discards())
                    &&a.temporaryFuritenTiles().equals(b.temporaryFuritenTiles())
                    &&a.riichiPermanentFuriten()==b.riichiPermanentFuriten()&&a.kitaCount()==b.kitaCount(),
                    "exchange changed non-hand player state at seat "+seat);
        }
    }
    private static void presetFlagsAndInitialPause() {
        for(var preset:RuleSetPreset.values())for(var length:MatchLength.values())
            for(boolean exchange:new boolean[]{false,true})for(boolean jokers:new boolean[]{false,true}) {
                var match=preset.newMatch(length,exchange,jokers).validate();
                check(match.ruleSet().exchangeThree()==exchange&&match.ruleSet().jokersEnabled()==jokers,
                        "optional flags were not applied to "+preset);
                check(match.playerCount()==preset.playerCount()&&match.roundCount()==length.roundCount(),
                        "optional flags changed preset player count or match length");
                if(preset.family()==RuleSetPreset.Family.SICHUAN)continue; // Its setup is tested by SichuanDriverRegression.
                var driver=new TheMahjongDriver(match,humans(preset.playerCount()),new Random(41));
                driver.startMatch();
                check(driver.exchangeInProgress()==exchange,"driver did not honor exchange setting: "+preset);
                var original=driver.currentRound();
                if(exchange) {
                    for(int seat=0;seat<preset.playerCount();seat++) {
                        check(driver.pendingExchange(seat),"human seat was prematurely confirmed");
                        check(driver.legalActions(seat).isEmpty(),"ordinary actions leaked before simultaneous exchange");
                        check(original.players().get(seat).currentHand().size()==13,"exchange occurred after a first draw");
                    }
                    for(int frame=0;frame<3;frame++)driver.advance(30);
                    check(driver.currentRound().equals(original),"waiting humans advanced or drew during exchange");
                    check(driver.exchangeInProgress(),"exchange timed out without human confirmation");
                }
            }
    }
    private static void driverExchangeAndSave(RuleSetPreset preset,boolean jokers) {
        var driver=started(preset,jokers,53);
        var original=driver.currentRound();int count=preset.playerCount();
        var selections=new ArrayList<List<TheMahjongTile>>();
        for(int seat=0;seat<count;seat++)selections.add(choice(original,seat));
        var first=selections.getFirst().getFirst();
        check(!driver.confirmExchange(0),"empty human selection was confirmed");
        if(jokers)check(!driver.toggleExchangeTile(0,Jokers.TILE),"dedicated joker was accepted as the first exchange tile");
        check(driver.toggleExchangeTile(0,first),"owned tile could not be selected");
        var other=original.players().getFirst().currentHand().stream()
                .filter(t->t.suit()!=first.suit()&&!Jokers.isJoker(t)).findFirst();
        if(other.isPresent())check(driver.toggleExchangeTile(0,other.get()),"riichi mixed-suit selection was rejected");
        if(jokers)check(!driver.toggleExchangeTile(0,Jokers.TILE),"dedicated joker entered the exchange");
        var partial=driver.exchangeState().snapshot();
        driver=reload(driver);
        check(driver.exchangeState().snapshot().equals(partial)&&driver.currentRound().equals(original),
                "partially selected mixed-suit exchange did not survive DriverNbt");
        check(driver.clearExchangeSelection(0),"selection could not be cleared after restore");
        for(int seat=0;seat<count;seat++) {
            for(var tile:selections.get(seat))check(driver.toggleExchangeTile(seat,tile),"valid three-tile selection rejected");
            check(driver.confirmExchange(seat),"three selected human tiles did not confirm");
            check(!driver.pendingExchange(seat),"confirmed seat remained pending");
            check(!driver.toggleExchangeTile(seat,selections.get(seat).getFirst()),"confirmed seat could change its selection");
            if(seat<count-1) {
                check(driver.currentRound().equals(original),"another player's exchange was revealed before all confirmations");
                check(driver.exchangeInProgress(),"exchange finished before every seat confirmed");
                var saved=driver.exchangeState().snapshot();driver=reload(driver);
                check(driver.exchangeState().snapshot().equals(saved)&&driver.currentRound().equals(original),
                        "confirmed exchange or private pre-exchange hands changed during DriverNbt");
            }
        }
        check(!driver.exchangeInProgress()&&driver.exchangeState().complete(),"last confirmation did not finish exchange");
        int direction=driver.exchangeState().direction();
        for(int seat=0;seat<count;seat++) {
            var expected=new ArrayList<>(original.players().get(seat).currentHand());
            for(var t:selections.get(seat))check(expected.remove(t),"fixture selection was not owned");
            expected.addAll(selections.get(Math.floorMod(seat-direction,count)));
            var actual=driver.currentRound().players().get(seat).currentHand();
            check(actual.size()==13&&bag(actual).equals(bag(expected)),"wrong exchange direction or tile identity");
            if(jokers)check(actual.stream().filter(Jokers::isJoker).count()==1,"exchange lost a seat's fixed joker");
        }
        preserved(original,driver.currentRound());
        var exchanged=driver.currentRound();var completed=driver.exchangeState().snapshot();
        driver=reload(driver);
        check(driver.currentRound().equals(exchanged)&&driver.exchangeState().snapshot().equals(completed)
                &&!driver.exchangeInProgress(),"finished exchange was replayed/lost during DriverNbt");
        check(driver.legalActions(exchanged.currentTurnSeat()).contains(PlayerAction.Draw.INSTANCE),
                "normal first draw did not become available after the exchange");
        // Restore at a normal between-hands boundary and ensure the next hand starts a new exchange.
        var ended=new TheMahjongRound(exchanged.roundWind(),exchanged.handNumber(),exchanged.honba(),exchanged.riichiSticks(),
                exchanged.dealerSeat(),TheMahjongRound.State.ENDED,exchanged.currentTurnSeat(),-1,
                TheMahjongRound.ActiveTile.none(),exchanged.liveWall(),exchanged.rinshanTiles(),exchanged.doraIndicators(),
                exchanged.uraDoraIndicators(),exchanged.revealedDoraCount(),exchanged.pendingKanDoraReveals(),
                exchanged.players(),List.of());
        var base=driver.match();
        var endedMatch=new TheMahjongMatch(base.playerCount(),base.startingPoints(),base.targetPoints(),base.roundCount(),
                TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),base.completedRounds(),ended);
        var next=new TheMahjongDriver(endedMatch,humans(count),new Random(19),
                new TheMahjongDriver.Snapshot(new MatchPhase.RoundEnded(List.of()),false,OptionalInt.empty()));
        next.restoreExchangeState(completed);
        next.advanceRound(false,0);
        check(next.exchangeInProgress()&&!next.exchangeState().complete(),"next hand retained completed exchange");
        for(int seat=0;seat<count;seat++)check(next.pendingExchange(seat)&&next.exchangeSelection(seat).isEmpty(),
                "next hand retained old selections/confirmations");
    }
    private static void automaticBots() {
        for(var preset:List.of(RuleSetPreset.MAHJONG_SOUL_4P,RuleSetPreset.TENHOU_SANMA_3P)) {
            int count=preset.playerCount();var players=new ArrayList<MahjongPlayerInterface>(humans(count));
            for(int seat=1;seat<count;seat++)players.set(seat,new StupidActiveBot());
            var driver=new TheMahjongDriver(preset.newMatch(MatchLength.EAST,true,true),players,new Random(71));
            driver.startMatch();var original=driver.currentRound();driver.advance(1);
            check(driver.pendingExchange(0),"human exchange was auto-confirmed like a BOT");
            for(int seat=1;seat<count;seat++)check(!driver.pendingExchange(seat)&&driver.exchangeSelection(seat).size()==3,
                    "BOT did not automatically select and confirm its three tiles");
            check(driver.currentRound().equals(original),"BOT selections were applied before the human confirmed");
            for(var tile:choice(original,0))check(driver.toggleExchangeTile(0,tile),"human could not finish alongside BOTs");
            check(driver.confirmExchange(0)&&!driver.exchangeInProgress(),"mixed human/BOT exchange did not complete");
            preserved(original,driver.currentRound());
        }
    }
    private static void mixedSuitSnapshots() {
        var mixed=List.of(new TheMahjongTile(Suit.MANZU,1,false),new TheMahjongTile(Suit.PINZU,1,false),
                new TheMahjongTile(Suit.DRAGON,3,false));
        for(int count:List.of(3,4)) {
            var selections=new ArrayList<List<TheMahjongTile>>();
            for(int seat=0;seat<count;seat++)selections.add(seat==0?mixed:List.of());
            var saved=new ExchangeThreeState.Snapshot(count,1,List.copyOf(selections),Set.of(0),false);
            var restored=new ExchangeThreeState(saved);
            check(restored.snapshot().equals(saved)&&!restored.pending(0),
                    "confirmed mixed-suit riichi selection did not restore");
            var partial=new ExchangeThreeState.Snapshot(count,1,List.copyOf(selections),Set.of(),false);
            check(new ExchangeThreeState(partial).snapshot().equals(partial),
                    "unconfirmed mixed-suit riichi selection did not restore");
            var illegal=new ArrayList<>(selections);illegal.set(0,List.of(Jokers.TILE,mixed.get(0),mixed.get(1)));
            boolean rejected=false;
            try { new ExchangeThreeState(new ExchangeThreeState.Snapshot(count,1,illegal,Set.of(),false)); }
            catch(IllegalArgumentException expected) { rejected=true; }
            check(rejected,"saved mixed-suit selection unexpectedly permitted a dedicated joker");
        }
    }
    private static void duplicateAndRedIdentities() {
        var round=started(RuleSetPreset.MAHJONG_SOUL_4P,true,13).currentRound();
        var ordinary=new TheMahjongTile(Suit.MANZU,5,false);var red=new TheMahjongTile(Suit.MANZU,5,true);
        var old=round.players().getFirst();var hand=new ArrayList<>(old.currentHand());
        hand.set(0,ordinary);hand.set(1,ordinary);hand.set(2,red);
        var players=new ArrayList<>(round.players());
        players.set(0,new TheMahjongPlayer(old.points(),old.riichiState(),old.ippatsuEligible(),hand,
                old.melds(),old.discards(),old.temporaryFuritenTiles(),old.riichiPermanentFuriten(),old.kitaCount()));
        round=ExchangeThreeState.copy(round,players);
        var state=new ExchangeThreeState(4,1);
        check(state.toggle(round,0,ordinary)&&state.toggle(round,0,ordinary)&&state.toggle(round,0,red),
                "two identical ordinary tiles plus one red five were not selectable");
        check(bag(state.selection(0)).equals(bag(List.of(ordinary,ordinary,red))),"red five merged with an ordinary five");
        check(state.confirm(round,0),"duplicate/red-five selection failed confirmation");
        for(int seat=1;seat<4;seat++)state.automatic(round,seat);
        var exchanged=state.exchange(round);var expected=new ArrayList<>(round.players().get(1).currentHand());
        for(var tile:state.selection(1))check(expected.remove(tile),"BOT fixture selection missing");
        expected.addAll(List.of(ordinary,ordinary,red));
        check(bag(exchanged.players().get(1).currentHand()).equals(bag(expected)),"exchange lost duplicate count or red identity");
    }
    private static void everyDirection() {
        for(var preset:List.of(RuleSetPreset.MAHJONG_SOUL_4P,RuleSetPreset.MAHJONG_SOUL_SANMA_3P)) {
            var original=started(preset,false,7).currentRound();int count=preset.playerCount();
            for(int direction=1;direction<count;direction++) {
                var state=new ExchangeThreeState(count,direction);
                for(int seat=0;seat<count;seat++)state.automatic(original,seat);
                var exchanged=state.exchange(original);
                for(int seat=0;seat<count;seat++) {
                    var hand=new ArrayList<>(original.players().get(seat).currentHand());
                    for(var tile:state.selection(seat))check(hand.remove(tile),"direction fixture failed");
                    hand.addAll(state.selection(Math.floorMod(seat-direction,count)));
                    check(bag(hand).equals(bag(exchanged.players().get(seat).currentHand())),"wrong sender for direction "+direction);
                }
                preserved(original,exchanged);
                check(state.exchange(exchanged).equals(exchanged),"a completed exchange applied twice");
            }
        }
    }
    public static void main(String[] args) {
        mixedSuitSnapshots();
        presetFlagsAndInitialPause();
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN)
            for(boolean jokers:new boolean[]{false,true})driverExchangeAndSave(preset,jokers);
        automaticBots();duplicateAndRedIdentities();everyDirection();
        System.out.println("PASS: riichi accepts mixed-suit exchanges in 3p/4p; humans and BOTs exchange simultaneously, preserve exact tiles/dead walls/scores and survive mixed partial/completed NBT and next hands");
    }
}
