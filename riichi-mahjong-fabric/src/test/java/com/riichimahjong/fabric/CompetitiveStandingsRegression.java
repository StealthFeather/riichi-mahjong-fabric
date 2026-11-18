package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.competition.CompetitiveRules;
import com.themahjong.competition.CompetitiveRules.Profile;
import com.themahjong.competition.CompetitiveStandings;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Official published point examples, floating uma tables and tied final-deposit boundaries. */
public final class CompetitiveStandingsRegression {
    private static void totals(Profile profile,List<Integer> scores,List<Integer> expected) {
        var results=CompetitiveStandings.calculate(profile,scores,scores);
        for(var result:results)check(result.totalTenths()==expected.get(result.seat()),"wrong league points for seat "+result.seat()+": "+result);
        check(results.stream().mapToInt(CompetitiveStandings.Result::totalTenths).sum()==0,"league standings lost point conservation");
    }
    private static TheMahjongMatch terminalMLeague(List<Integer> points,int sticks) {
        var base=CompetitiveRules.mLeague();var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<4;seat++)players.add(new TheMahjongPlayer(points.get(seat),TheMahjongPlayer.RiichiState.NONE,false,waiting(seat%3),List.of(),List.of(),List.of()));
        var indicators=Collections.nCopies(5,tile(4,1));
        var round=new TheMahjongRound(TheMahjongTile.Wind.SOUTH,4,0,sticks,3,TheMahjongRound.State.ENDED,0,-1,
                TheMahjongRound.ActiveTile.none(),List.of(),Collections.nCopies(4,tile(4,1)),indicators,indicators,1,players,List.of());
        return new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),round)
                .advanceRound(new Random(1),false,0).applyFinalDeposits();
    }
    public static void main(String[] args) {
        totals(Profile.M_LEAGUE,List.of(35800,41600,15000,7600),List.of(158,616,-250,-524));
        totals(Profile.JPML_A,List.of(50000,29000,25000,16000),List.of(320,-20,-80,-220));
        totals(Profile.JPML_A,List.of(40000,35000,25000,20000),List.of(180,90,-90,-180));
        totals(Profile.JPML_A,List.of(35000,34000,33000,18000),List.of(130,70,40,-240));
        totals(Profile.JPML_A,List.of(40000,30000,26000,24000),List.of(180,40,-80,-140));
        totals(Profile.JPML_A,List.of(30000,30000,30000,30000),List.of(0,0,0,0));
        var tied=CompetitiveStandings.calculate(Profile.M_LEAGUE,List.of(35000,35000,15000,15000),List.of(35000,35000,15000,15000));
        check(tied.stream().map(CompetitiveStandings.Result::totalTenths).toList().equals(List.of(350,350,-350,-350)),"tied uma did not split combined positions");
        for(int sticks:List.of(1,2,3,4,5,6,7)) {
            var scores=List.of(33000,33000,33000,1000-sticks*1000);
            var match=terminalMLeague(scores,sticks);
            check(match.currentRound().orElseThrow().riichiSticks()==0,"final deposits still on table after payout");
            check(match.completedRounds().getLast().players().stream().map(TheMahjongPlayer::points).toList().equals(scores),"terminal history lost the original tied ranking");
            var results=CompetitiveStandings.forMatch(match);
            check(results.stream().map(CompetitiveStandings.Result::rank).toList().equals(List.of(1,1,1,4)),"deposit rounding broke a tied tournament rank");
            check(results.stream().map(CompetitiveStandings.Result::rankTenths).toList().equals(List.of(167,167,166,-300)),"three-way uma remainder did not follow original seat order");
            check(results.stream().mapToInt(CompetitiveStandings.Result::totalTenths).sum()==0,"final deposits were omitted or counted twice in standings");
            check(CompetitiveStandings.forMatch(match.applyFinalDeposits()).equals(results),"recalculating standings paid deposits twice");
        }
        check(CompetitiveStandings.forMatch(CompetitiveRules.mLeague()).isEmpty(),"active/non-started match exposes final standings");
        System.out.println("PASS: JPML floating uma and M-League official examples, ties, original-seat remainders and final deposits produce exact tournament standings");
    }
}
