package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.yaku.TenpaiChecker;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Reveal every ready hand at a draw, without exposing any concealed hand during play. */
public final class RoundRevealRegression {
    private static TheMahjongPlayer withMelds(List<TheMahjongTile> hand,List<TheMahjongMeld> melds){
        return new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,melds,List.of(),List.of());
    }
    private static boolean revealed(TheMahjongPlayer candidate){
        var round=driver(List.of(candidate,player(waiting(1)),player(waiting(2))),List.of()).currentRound().exhaustiveDraw(TheMahjongRuleSet.mahjongSoul());
        return PublicTable.revealedSeats(new MatchPhase.RoundEnded(List.of()),round).contains(0);
    }
    private static void fifthCopyAndMeldBoundaries(){
        var fifthCopy=player(List.of(tile(0,1),tile(0,1),tile(0,1),tile(0,1),tile(1,2),tile(1,3),tile(1,4),tile(1,5),tile(1,6),tile(1,7),tile(2,7),tile(2,8),tile(2,9)));
        check(TenpaiChecker.inTenpai(fifthCopy),"fixture must exercise the upstream fifth-copy shape false positive");
        check(!revealed(fifthCopy),"a hand waiting only for its own fifth copy was exposed");
        var mixedWait=player(List.of(tile(0,1),tile(0,1),tile(0,1),tile(0,1),tile(0,2),tile(0,3),tile(0,4),tile(1,5),tile(1,6),tile(1,7),tile(2,7),tile(2,8),tile(2,9)));
        check(revealed(mixedWait),"one impossible wait must not hide another valid wait");

        var threeGroups=List.of(tile(1,2),tile(1,3),tile(1,4),tile(1,5),tile(1,6),tile(1,7),tile(2,7),tile(2,8),tile(2,9));
        var openHand=new ArrayList<>(threeGroups);openHand.add(tile(0,1));
        var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,tile(0,1)),0,1,0);
        check(!revealed(withMelds(openHand,List.of(pon))),"self-owned copies in a pon were ignored");
        openHand.set(openHand.size()-1,tile(0,2));
        check(revealed(withMelds(openHand,List.of(pon))),"ordinary open tenpai hand was hidden");
        check(revealed(withMelds(openHand,List.of(new TheMahjongMeld.Ankan(Collections.nCopies(4,tile(0,1)))))),"a concealed kan broke a valid wait of another kind");

        var redFive=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);
        var redPon=new TheMahjongMeld.Pon(List.of(redFive,tile(0,5),tile(0,5)),0,1,0);
        var redWait=new ArrayList<>(threeGroups);redWait.add(tile(0,5));
        check(!revealed(withMelds(redWait,List.of(redPon))),"red and ordinary fives were counted as different kinds");

        List<TheMahjongMeld> fourMelds=new ArrayList<>();
        for(int rank=1;rank<=4;rank++)fourMelds.add(new TheMahjongMeld.Pon(Collections.nCopies(3,tile(0,rank)),0,1,0));
        check(revealed(withMelds(List.of(tile(0,5)),fourMelds)),"one concealed tile with four melds must still be tanki tenpai");
        check(!revealed(withMelds(List.of(tile(0,1)),fourMelds)),"one concealed tile waiting for a fifth copy was exposed");

        var externalPon=new TheMahjongMeld.Pon(Collections.nCopies(3,tile(4,1)),0,0,0);
        var external=withMelds(openHand,List.of(externalPon));
        var other=new ArrayList<>(waiting(2));other.set(other.size()-1,tile(4,2));
        var emptyWait=driver(List.of(player(waiting(0)),external,player(other)),List.of()).currentRound().exhaustiveDraw(TheMahjongRuleSet.mahjongSoul());
        check(PublicTable.revealedSeats(new MatchPhase.RoundEnded(List.of()),emptyWait).contains(0),"a wait exhausted by another player's exposed tiles must remain tenpai");
    }
    public static void main(String[] args){
        var unready=new ArrayList<>(waiting(0));
        unready.set(0,tile(4,2));
        check(!TenpaiChecker.inTenpai(player(unready)),"fixture must be noten");
        var hands=List.of(player(waiting(0)),player(unready),player(waiting(1)),player(waiting(2)));
        var round=driver(hands,List.of()).currentRound();
        check(PublicTable.revealedSeats(new MatchPhase.AwaitingDiscard(0),round).isEmpty(),"live concealed hands leaked");
        check(PublicTable.revealedSeats(new MatchPhase.AwaitingClaims(Set.of(1),tile(4,1),MatchPhase.ClaimSource.DISCARD),round).isEmpty(),"claim phase leaked concealed hands");
        var ended=round.exhaustiveDraw(TheMahjongRuleSet.mahjongSoul());
        check(PublicTable.revealedSeats(new MatchPhase.RoundEnded(List.of()),ended).equals(Set.of(0,2,3)),"must reveal all tenpai players, including players who did not declare riichi");
        check(PublicTable.revealedSeats(new MatchPhase.BetweenRounds(),ended).isEmpty(),"reveal continued outside result phase");
        var three=driver(hands.subList(0,3),List.of()).currentRound().exhaustiveDraw(TheMahjongRuleSet.mahjongSoul());
        check(PublicTable.revealedSeats(new MatchPhase.RoundEnded(List.of()),three).equals(Set.of(0,2)),"three-player draw reveal incorrect");
        check(PublicTable.revealedSeats(new MatchPhase.RoundEnded(List.of()),null).isEmpty(),"empty table reveal incorrect");
        fifthCopyAndMeldBoundaries();
        System.out.println("PASS: all tenpai hands revealed at draws in 3/4-player games; noten and live hands remain concealed");
        System.out.println("PASS: fifth-copy waits stay private; open/closed melds, red fives, one-tile waits and exhausted external waits are handled");
    }
}
