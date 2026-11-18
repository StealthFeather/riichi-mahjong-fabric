package com.themahjong.competition;

import com.themahjong.TheMahjongMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** Tournament points are derived from settled scores; calculating them never moves point sticks. */
public final class CompetitiveStandings {
    public record Result(int seat,int rank,int points,int rankTenths,int totalTenths) {
        public String formatted() { return String.format(Locale.ROOT,"%+.1f",totalTenths/10.0); }
    }
    private CompetitiveStandings() {}

    public static List<Result> forMatch(TheMahjongMatch match) {
        if(match==null||match.state()!=TheMahjongMatch.State.ENDED
                ||match.ruleSet().competitiveProfile()==CompetitiveRules.Profile.STANDARD)return List.of();
        var settled=match.currentRound().orElseThrow().players().stream().map(p->p.points()).toList();
        // Fractional deposit splitting must not break a tie that existed at the end of play.
        var ranking=match.completedRounds().isEmpty()?settled
                :match.completedRounds().getLast().players().stream().map(p->p.points()).toList();
        return calculate(match.ruleSet().competitiveProfile(),ranking,settled);
    }

    public static List<Result> calculate(CompetitiveRules.Profile profile,List<Integer> ranking,List<Integer> settled) {
        if(profile==CompetitiveRules.Profile.STANDARD)return List.of();
        if(ranking.size()!=4||settled.size()!=4)throw new IllegalArgumentException("tournaments require four scores");
        int[] bonuses;
        if(profile==CompetitiveRules.Profile.M_LEAGUE)bonuses=new int[]{500,100,-100,-300};
        else {
            int afloat=(int)ranking.stream().filter(points->points>=30000).count();
            bonuses=switch(afloat) {
                case 1->new int[]{120,-10,-30,-80};
                case 2->new int[]{80,40,-40,-80};
                case 3->new int[]{80,30,10,-120};
                default->new int[]{0,0,0,0};
            };
        }
        var seats=IntStream.range(0,4).boxed().sorted(Comparator.<Integer>comparingInt(ranking::get)
                .reversed().thenComparingInt(Integer::intValue)).toList();
        var results=new ArrayList<Result>();
        for(int first=0;first<4;) {
            int end=first+1;while(end<4&&ranking.get(seats.get(end)).equals(ranking.get(seats.get(first))))end++;
            int total=0;for(int i=first;i<end;i++)total+=bonuses[i];
            int count=end-first,share=Math.floorDiv(total,count),extra=Math.floorMod(total,count);
            for(int i=first;i<end;i++) {
                int seat=seats.get(i),rankBonus=share+(i-first<extra?1:0);
                results.add(new Result(seat,first+1,settled.get(seat),rankBonus,
                        Math.toIntExact(Math.round((settled.get(seat)-30000)/100.0))+rankBonus));
            }
            first=end;
        }
        return List.copyOf(results);
    }
}
