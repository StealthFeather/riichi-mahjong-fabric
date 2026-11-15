package com.riichimahjong.fabric;

import java.util.ArrayList;
import java.util.List;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.replay.ReplayAnalysis;
import com.themahjong.driver.TheMahjongDriver;

/** The historical operation labels reuse the live hand's action names, without submitting actions. */
final class ReplayActionLabels {
    static List<String> labels(TheMahjongDriver driver,int seat,ReplayAnalysis.Decision decision){
        var legal=driver.legalActions(seat);var out=new ArrayList<String>();
        for(var button:MahjongTableHumanPlayer.tableButtons(driver,seat,false)){
            String text=PrivateHand.label(button);Integer probability=null;
            if(decision!=null){
                if(button instanceof MahjongTableHumanPlayer.TableButton.RiichiToggle)
                    probability=decision.actions().stream().filter(a->a.type().equals("DiscardWithRiichi")).mapToInt(ReplayAnalysis.ActionProbability::probability).sum();
                else if(button instanceof MahjongTableHumanPlayer.TableButton.SkipTsumo)
                    probability=decision.actions().stream().filter(a->!a.type().equals("DeclareTsumo")).mapToInt(ReplayAnalysis.ActionProbability::probability).sum();
                else if(button instanceof MahjongTableHumanPlayer.TableButton.SubmitAction submit){
                    int index=legal.indexOf(submit.action());
                    probability=decision.actions().stream().filter(a->a.index()==index).map(ReplayAnalysis.ActionProbability::probability).findFirst().orElse(null);
                }
            }
            out.add(probability==null?text:text+" "+probability+"%");
        }
        return List.copyOf(out);
    }
}
