package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.yaku.WinResult;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Voluntary visibility must not become a rule action or expose another player's concealed hand. */
public final class ShowHandRegression {
    private static TheMahjongPlayer noten() {
        var hand = new ArrayList<>(waiting(1));
        hand.set(0, tile(4,2));
        return player(hand);
    }

    private static List<MahjongPlayerInterface> humans(int count) {
        return java.util.stream.IntStream.range(0,count)
                .mapToObj(seat -> (MahjongPlayerInterface)new MahjongTableHumanPlayer()).toList();
    }

    private static TheMahjongRound round(int count, boolean last) {
        var indicators = Collections.nCopies(5,tile(4,3));
        int dealer = last ? count-1 : 0;
        return new TheMahjongRound(last ? TheMahjongTile.Wind.SOUTH : TheMahjongTile.Wind.EAST,
                last ? count : 1,2,last ? 2 : 0,dealer,TheMahjongRound.State.TURN,dealer,-1,
                TheMahjongRound.ActiveTile.none(),List.of(),Collections.nCopies(TheMahjongRound.rinshanTileCountFor(count),tile(4,2)),
                indicators,indicators,1,Collections.nCopies(count,noten()),List.of());
    }

    private static TheMahjongDriver driver(TheMahjongRound round, MatchPhase phase) {
        int count = round.players().size();
        var match = new TheMahjongMatch(count,25000,25000,2,
                phase instanceof MatchPhase.MatchEnded ? TheMahjongMatch.State.ENDED : TheMahjongMatch.State.IN_ROUND,
                count==3 ? TheMahjongTileSet.standardSanma(false) : TheMahjongTileSet.standardRiichi(false),
                count==3 ? TheMahjongRuleSet.mahjongSoulSanma() : TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        return new TheMahjongDriver(match,humans(count),new Random(2),
                new TheMahjongDriver.Snapshot(phase,false,OptionalInt.empty()));
    }

    private static Set<Integer> visible(TheMahjongDriver driver, ResultHandReveals choices) {
        return PublicTable.revealedSeats(driver.currentPhase(),driver.currentRound(),choices.shownSeats(driver));
    }

    private static TheMahjongDriver reload(TheMahjongDriver driver, ResultHandReveals choices) {
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(out.child("Driver"),driver);
        choices.write(out.child("ShownHands"),driver);
        var in = TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),out.buildResult());
        var restored = DriverNbt.readDriver(in.childOrEmpty("Driver"),humans(driver.match().playerCount()),new Random(2));
        choices.read(in.childOrEmpty("ShownHands"),restored);
        return restored;
    }

    private static void voluntaryAndNextHand() {
        for (int count : new int[]{3,4}) {
            var initial = round(count,false);
            var live = driver(initial,new MatchPhase.AwaitingDiscard(0));
            var choices = new ResultHandReveals();
            check(!choices.show(live,initial,1) && choices.shownSeats(live).isEmpty(),"live play accepted a reveal");
            check(PublicTable.revealedSeats(live.currentPhase(),initial,Set.of(0,1)).isEmpty(),
                    "untrusted reveal seats exposed a live concealed hand");
            var ended = initial.exhaustiveDraw(live.rules());
            var result = driver(ended,new MatchPhase.RoundEnded(List.of()));
            check(visible(result,choices).isEmpty(),"noten hands became public before an explicit choice");
            check(!choices.show(result,initial,1),"stale pre-result hand authorized a reveal");
            check(!choices.show(result,ended,-1) && !choices.show(result,ended,count),"invalid seat revealed a hand");
            check(choices.show(result,ended,1),"own-seat visibility choice was not accepted");
            check(visible(result,choices).equals(Set.of(1)),"one reveal exposed other concealed hands");
            check(!choices.show(result,ended,1),"duplicate reveal changed state");
            check(result.currentRound()==ended && result.currentPhase() instanceof MatchPhase.RoundEnded,
                    "a display choice changed the rule round or phase");
            result = reload(result,choices);
            check(visible(result,choices).equals(Set.of(1)),"result save/reload erased or enlarged the public choice");
            var stale = result.currentRound();
            result.advanceRound(false,3);
            check(choices.shownSeats(result).isEmpty(),"the next real hand inherited an earlier public choice");
            check(!choices.show(result,stale,1),"a result button remained valid in the next hand");
        }
    }

    private static void terminalAndPersistence() {
        for (int count : new int[]{3,4}) {
            var initial = round(count,true);
            var rules = count==3 ? TheMahjongRuleSet.mahjongSoulSanma() : TheMahjongRuleSet.mahjongSoul();
            var ended = initial.exhaustiveDraw(rules);
            var result = driver(ended,new MatchPhase.RoundEnded(List.of()));
            var choices = new ResultHandReveals();
            check(choices.show(result,ended,0),"last-hand result did not accept a reveal");
            result.advanceRound(false,3);
            check(result.currentPhase() instanceof MatchPhase.MatchEnded,"fixture did not end the match");
            check(result.currentRound()!=ended,"fixture did not exercise final-deposit round replacement");
            check(visible(result,choices).equals(Set.of(0)),"final deposits erased the last hand's public choice");
            check(!choices.show(result,ended,1),"a stale pre-settlement button remained valid");
            check(choices.show(result,result.currentRound(),1),"terminal match result did not accept a reveal");
            result = reload(result,choices);
            check(result.currentPhase() instanceof MatchPhase.MatchEnded && visible(result,choices).equals(Set.of(0,1)),
                    "terminal save/reload erased or enlarged public choices");
            choices.clear();
            check(visible(result,choices).isEmpty(),"reset retained voluntary visibility");
            check(!ResultHandReveals.canReveal(new MatchPhase.MatchEnded(),initial),
                    "a malformed live hand became revealable in MatchEnded");
        }
    }

    private static void automaticVisibilityAndButtons() {
        var ended = round(4,false).exhaustiveDraw(TheMahjongRuleSet.mahjongSoul());
        var win = new WinResult(List.of(),List.of(),1,30,0,List.of(1000,-1000,0,0));
        var phase = new MatchPhase.RoundEnded(List.of(win));
        check(PublicTable.revealedSeats(phase,ended,Set.of(2)).equals(Set.of(0,2)),
                "a losing player's explicit choice replaced the automatic winner or exposed another loser");
        check(PublicTable.revealedSeats(phase,ended,Set.of(-1,4)).equals(Set.of(0)),"invalid stored seats entered the allowlist");
        var button = new MahjongTableHumanPlayer.TableButton.ShowHand(ended);
        check(PrivateHand.label(button).equals("展示手牌"),"result button has an incorrect player-facing label");
        var result = driver(ended,phase);
        check(!((MahjongTableHumanPlayer)result.playerAt(2)).selectTableButton(button,result,2,null),
                "a human controller bypassed the table's ownership and lifecycle authorization");
    }

    public static void main(String[] args) {
        voluntaryAndNextHand();
        terminalAndPersistence();
        automaticVisibilityAndButtons();
        System.out.println("PASS: 3p/4p result choices expose only selected hands, expire for new hands and preserve rule state");
        System.out.println("PASS: terminal final-deposit settlement and save/reload retain the last hand's explicit public choices");
        System.out.println("PASS: automatic result visibility and private button authorization remain intact");
    }
}
