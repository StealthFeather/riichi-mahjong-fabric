package com.riichimahjong.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.replay.ReplayAnalysis;
import com.themahjong.driver.TheMahjongDriver;

/** The historical operation labels reuse the live hand's action names, without submitting actions. */
final class ReplayActionLabels {
    record Score(int probability,double policyProbability) {}

    static List<String> labels(TheMahjongDriver driver,int seat,ReplayAnalysis.Decision decision){
        var legal=driver.legalActions(seat);var labels=new ArrayList<String>();var scores=new ArrayList<Score>();
        for(var button:MahjongTableHumanPlayer.tableButtons(driver,seat,false)){
            labels.add(PrivateHand.label(button));
            List<ReplayAnalysis.ActionProbability> matching=List.of();
            if(decision!=null){
                if(button instanceof MahjongTableHumanPlayer.TableButton.RiichiToggle)
                    matching=decision.actions().stream().filter(a->a.type().equals("DiscardWithRiichi")).toList();
                else if(button instanceof MahjongTableHumanPlayer.TableButton.SkipTsumo)
                    matching=decision.actions().stream().filter(a->!a.type().equals("DeclareTsumo")).toList();
                else if(button instanceof MahjongTableHumanPlayer.TableButton.SubmitAction submit){
                    int index=legal.indexOf(submit.action());
                    matching=decision.actions().stream().filter(a->a.index()==index).toList();
                }
            }
            scores.add(matching.isEmpty()?null:new Score(matching.stream().mapToInt(ReplayAnalysis.ActionProbability::probability).sum(),
                    matching.stream().mapToDouble(ReplayAnalysis.ActionProbability::policyProbability).sum()));
        }
        return rankedLabels(labels,scores);
    }

    /** Preserve every legal button and its order; only the three highest scored buttons get numbers. */
    static List<String> rankedLabels(List<String> labels,List<Score> scores){
        if(labels.size()!=scores.size())throw new IllegalArgumentException("Mismatched action labels and scores");
        var ranked=new ArrayList<Integer>();
        for(int i=0;i<scores.size();i++)if(scores.get(i)!=null&&scores.get(i).probability()>=1)ranked.add(i);
        ranked.sort(Comparator.<Integer>comparingDouble(i->scores.get(i).policyProbability()).reversed().thenComparingInt(i->i));
        var result=new ArrayList<String>(labels);
        for(int n=0;n<Math.min(3,ranked.size());n++){
            int i=ranked.get(n);result.set(i,labels.get(i)+" "+scores.get(i).probability());
        }
        return List.copyOf(result);
    }
}
