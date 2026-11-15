package com.riichimahjong.fabric;

import com.riichimahjong.replay.ReplayAnalysis;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Screen-independent checks for the screenshot's two river-side control areas. */
public final class ReplayOverlayRegression {
    private static void panelGeometry(){
        var labels=List.of("上一局","上一巡","上一步","播放","下一步","下一巡","下一局");
        for(boolean right:List.of(false,true))for(var direction:Direction.Plane.HORIZONTAL){
            var pos=new BlockPos(-10683,244,-6691);var buttons=TableButtonLayout.replayPanel(labels,right);
            check(buttons.size()==7,"one column lost controls");
            for(var button:buttons){
                check(Math.abs(button.along())-button.width()/2>.25,"control overlaps own river");
                check(Math.abs(button.along())+button.width()/2<.67,"control overlaps the side wall");
                check(button.distance()-button.depth()/2>.23&&button.distance()+button.depth()/2<.67,"control leaves the free corner between river and wall");
                var centre=button.centre(pos,direction);var start=centre.add(0,3,0);var end=centre.add(0,-1,0);
                check(button.hit(pos,direction,start,end).isPresent(),"control's visible centre cannot be clicked");
                for(var other:buttons)if(other.index()!=button.index())
                    check(other.hit(pos,direction,start,end).isEmpty(),"one click can activate two controls");
                check(centre.y>pos.getY()+TableLayout.TABLE_TOP&&centre.y<pos.getY()+.60,"control is on the rim rather than the felt");
                for(var otherSeat:Direction.Plane.HORIZONTAL)for(int north=0;north<4;north++){
                    var kita=TableLayout.anchor(pos,otherSeat,-.53+north*.073,.59,.57);
                    double halfWidth=button.width()*TableLayout.TILE_ENLARGEMENT/2;
                    double halfDepth=button.depth()*TableLayout.TILE_ENLARGEMENT/2;
                    var along=direction.getCounterClockWise();
                    for(double x:new double[]{-halfWidth,0,halfWidth})for(double z:new double[]{-halfDepth,0,halfDepth}){
                        var point=centre.add(along.getStepX()*x+direction.getStepX()*z,0,along.getStepZ()*x+direction.getStepZ()*z);
                        check(TableHitTest.tile(point.add(0,3,0),point,kita,TableLayout.TILE_SCALE,PortViews.rotation(otherSeat,true,false)).isEmpty(),"a seat's extracted north tile blocks a replay control");
                    }
                }
            }
        }
        for(String text:List.of("0%","1%","33%","99%","100%")){
            double width=.025*ReplayOverlayLayout.scoreScale(text)*(TableButtonLayout.textWidth(text)+1);
            check(Math.abs(width-TableLayout.TILE_WIDTH)<1e-7,"probability width differs from tile width");
        }
        check(TableButtonLayout.textWidth("100%") == 15,"percent glyph advance is wrong");
        check(TableButtonLayout.textWidth("0.5") == 10,"decimal glyph advance is wrong");
        check(ReplayOverlayLayout.showProbability(new ReplayAnalysis.Candidate("m1",1,2,.009,0,0)),"allocated integer 1% was hidden using its unrounded probability");
        check(!ReplayOverlayLayout.showProbability(new ReplayAnalysis.Candidate("m2",0,3,.004,0,0)),"integer 0% was shown as a candidate");
    }
    private static ReplayAnalysis.Decision decision(List<ReplayAnalysis.Candidate> candidates,int at){
        var actual=candidates.get(at);
        return new ReplayAnalysis.Decision(0,candidates,List.of(),actual.tile(),actual.probability(),actual.rank(),-1);
    }
    private static void colors(){
        var candidates=List.of(new ReplayAnalysis.Candidate("m1",60,1,.60,0,0),new ReplayAnalysis.Candidate("m2",20,2,.20,0,0),
                new ReplayAnalysis.Candidate("m3",15,3,.15,0,0),new ReplayAnalysis.Candidate("m4",5,4,.05,0,0));
        check(ReplayOverlayLayout.color(decision(candidates,0))==ChatFormatting.GREEN,"highest-probability actual discard is not green");
        check(ReplayOverlayLayout.color(decision(candidates,1))==ChatFormatting.YELLOW&&ReplayOverlayLayout.color(decision(candidates,2))==ChatFormatting.YELLOW,"second/third actual discard is not yellow");
        check(ReplayOverlayLayout.color(decision(candidates,3))==ChatFormatting.RED,"other actual discard is not red");
        var tied=List.of(new ReplayAnalysis.Candidate("m1",50,1,.5,0,0),new ReplayAnalysis.Candidate("m2",50,2,.5,0,0));
        check(ReplayOverlayLayout.color(decision(tied,1))==ChatFormatting.GREEN,"a tied maximum is not green");
    }
    private static void actionLabels(){
        var first=new ArrayList<>(waiting(0));first.add(tile(4,2));
        var caller=new ArrayList<>(waiting(1));caller.set(0,tile(4,2));caller.set(1,tile(4,2));
        var driver=driver(List.of(player(first),player(caller),player(waiting(2)),player(waiting(0))),Collections.nCopies(20,tile(4,2)));
        act(driver,0,new com.themahjong.driver.PlayerAction.Discard(tile(4,2)));
        var legal=driver.legalActions(1);var choices=new ArrayList<ReplayAnalysis.ActionProbability>();
        for(int i=0;i<legal.size();i++){
            var action=legal.get(i);int probability=action instanceof com.themahjong.driver.PlayerAction.Pon?70:action instanceof com.themahjong.driver.PlayerAction.Pass?30:0;
            choices.add(new ReplayAnalysis.ActionProbability(i,action.getClass().getSimpleName(),null,"",probability,i+1,probability/100.0));
        }
        var decision=new ReplayAnalysis.Decision(1,List.of(),choices,null,-1,-1,-1);
        var before=driver.match();var labels=ReplayActionLabels.labels(driver,1,decision);
        check(labels.stream().anyMatch(s->s.startsWith("碰 ")&&s.endsWith("70%")),"historical pon button lost its probability");
        check(labels.contains("跳过 30%"),"historical pass button lost its probability");
        check(driver.match()==before,"displaying historical actions modified the match");
    }
    public static void main(String[] args){panelGeometry();colors();actionLabels();System.out.println("PASS: river-side controls in every orientation, probability widths, actual-discard colors and read-only claim labels");}
}
