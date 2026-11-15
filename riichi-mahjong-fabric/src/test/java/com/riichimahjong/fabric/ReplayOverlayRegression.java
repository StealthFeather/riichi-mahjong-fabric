package com.riichimahjong.fabric;

import com.riichimahjong.replay.ReplayAnalysis;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.riichimahjong.replay.ReplaySession;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Screen-independent checks for the screenshot's two river-side control areas. */
public final class ReplayOverlayRegression {
    private static AABB buttonBounds(TableButtonLayout.Button button,BlockPos pos,Direction direction){
        var centre=button.centre(pos,direction);var along=direction.getCounterClockWise();
        double halfWidth=button.width()*TableLayout.TILE_ENLARGEMENT/2;
        double halfDepth=button.depth()*TableLayout.TILE_ENLARGEMENT/2;
        double x=Math.abs(along.getStepX())*halfWidth+Math.abs(direction.getStepX())*halfDepth;
        double z=Math.abs(along.getStepZ())*halfWidth+Math.abs(direction.getStepZ())*halfDepth;
        return new AABB(centre.x-x,centre.y-.001,centre.z-z,centre.x+x,centre.y+.001,centre.z+z);
    }
    private static boolean separateOnFelt(AABB first,AABB second){
        double gap=1e-6;
        return first.maxX+gap<second.minX||second.maxX+gap<first.minX
                ||first.maxZ+gap<second.minZ||second.maxZ+gap<first.minZ;
    }
    private static void clearOfTile(AABB button,BlockPos pos,Direction seat,double along,double height,double distance,boolean down,boolean sideways,String reason){
        var tile=TableLayout.anchor(pos,seat,along,height,distance);
        var bounds=TableHitTest.bounds(tile,TableLayout.TILE_SCALE,PortViews.rotation(seat,true,down).rotateLocalY(sideways?(float)Math.PI/2:0));
        check(separateOnFelt(button,bounds),reason+"; seat="+seat+", along="+along+", distance="+distance+", sideways="+sideways+", button="+button+", tile="+bounds);
    }
    private static void controlBindings(){
        var progress=ReplayView.progressControls();var other=ReplayView.otherControls();
        check(progress.equals(List.of(ReplaySession.Control.PREVIOUS_TURN,ReplaySession.Control.PREVIOUS,
                ReplaySession.Control.PLAY_PAUSE,ReplaySession.Control.NEXT_TURN,ReplaySession.Control.NEXT,ReplaySession.Control.SPEED)),"progress controls are not two requested rows of three");
        check(other.equals(List.of(ReplaySession.Control.FIRST,ReplaySession.Control.PREVIOUS_ROUND,
                ReplaySession.Control.NEXT_ROUND,ReplaySession.Control.LAST,ReplaySession.Control.PERSPECTIVE,
                ReplaySession.Control.REVEAL,ReplaySession.Control.ANALYSIS,ReplaySession.Control.EXIT)),"other controls lost their requested left-column order");
        var all=new ArrayList<>(progress);all.addAll(other);
        check(all.size()==ReplaySession.Control.values().length&&EnumSet.copyOf(all).equals(EnumSet.allOf(ReplaySession.Control.class)),"a replay control was omitted or bound more than once");
        check(!progress.contains(ReplaySession.Control.REVEAL)&&other.stream().filter(c->c==ReplaySession.Control.REVEAL).count()==1,"other players' hand toggle must occur once on the left");
        check(ReplayView.revealLabel(false).equals("显示他家手牌")&&ReplayView.revealLabel(true).equals("隐藏他家手牌"),"other-hand visibility label does not reflect its toggle state");
    }
    private static void panelGeometry(){
        controlBindings();
        for(boolean alternate:List.of(false,true)){
            var rightLabels=List.of("上一巡","上一步",alternate?"暂停":"播放","下一巡","下一步",alternate?"4.0倍速":"0.5倍速");
            var leftLabels=List.of("开局","上一局","下一局","终局","视角：东",ReplayView.revealLabel(alternate),"分析：开","退出回放");
            var right=TableButtonLayout.replayPanel(rightLabels,true);var left=TableButtonLayout.replayPanel(leftLabels,false);
            check(right.size()==6&&left.size()==8,"requested progress/other control count changed");
            check(right.stream().map(TableButtonLayout.Button::distance).distinct().count()==2,"progress controls are not exactly two rows");
            check(right.stream().map(TableButtonLayout.Button::along).distinct().count()==3,"progress controls are not exactly three columns");
            check(left.stream().map(TableButtonLayout.Button::along).distinct().count()==1&&left.stream().map(TableButtonLayout.Button::distance).distinct().count()==8,"other controls are not one column of eight");
            for(int i=0;i<right.size();i++){
                check(right.get(i).index()==i&&right.get(i).text().equals(rightLabels.get(i)),"right action index/label mapping changed");
                check(right.get(i).distance()==right.get(i/3*3).distance(),"right row breaks the caller's three-button order");
                check(right.get(i).along()==right.get(i%3).along(),"right columns do not line up");
            }
            for(int i=0;i<left.size();i++)check(left.get(i).index()==i&&left.get(i).text().equals(leftLabels.get(i)),"left action index/label mapping changed");
            for(var direction:Direction.Plane.HORIZONTAL){
                var pos=new BlockPos(-10683,244,-6691);var origin=TableLayout.anchor(pos,direction,0,TableLayout.TABLE_TOP,0);
                var buttons=new ArrayList<>(left);buttons.addAll(right);
                for(int n=0;n<buttons.size();n++){
                    var button=buttons.get(n);var centre=button.centre(pos,direction);var along=direction.getCounterClockWise();
                    double worldAlong=Math.abs((centre.x-origin.x)*along.getStepX()+(centre.z-origin.z)*along.getStepZ());
                    double worldDistance=(centre.x-origin.x)*direction.getStepX()+(centre.z-origin.z)*direction.getStepZ();
                    double halfWidth=button.width()*TableLayout.TILE_ENLARGEMENT/2,halfDepth=button.depth()*TableLayout.TILE_ENLARGEMENT/2;
                    check(worldAlong-halfWidth>.25*TableLayout.TILE_ENLARGEMENT,"actual control rectangle overlaps own river corridor");
                    check(worldAlong+halfWidth<.67*TableLayout.TILE_ENLARGEMENT,"actual control rectangle overlaps side wall corridor");
                    check(worldDistance-halfDepth>.23*TableLayout.TILE_ENLARGEMENT&&worldDistance+halfDepth<.67*TableLayout.TILE_ENLARGEMENT,"actual control rectangle leaves the free felt corner");
                    var bounds=buttonBounds(button,pos,direction);var start=centre.add(0,3,0);var end=centre.add(0,-1,0);
                    check(button.hit(pos,direction,start,end).isPresent(),"visible control centre cannot be clicked");
                    for(int m=0;m<buttons.size();m++)if(m!=n){
                        var other=buttons.get(m);
                        check(separateOnFelt(bounds,buttonBounds(other,pos,direction)),"actual scaled button rectangles overlap, despite distinct centres");
                        check(other.hit(pos,direction,start,end).isEmpty(),"one click activates two controls");
                    }
                    check(centre.y>pos.getY()+TableLayout.TABLE_TOP&&centre.y<pos.getY()+.60,"control is on the rim rather than the felt");
                    for(var seat:Direction.Plane.HORIZONTAL){
                        for(int north=0;north<4;north++)clearOfTile(bounds,pos,seat,-.53+north*.073,.59,.57,false,false,"one of four seats' north tiles overlaps a control");
                        for(int river=0;river<36;river++)for(boolean sideways:List.of(false,true))
                            clearOfTile(bounds,pos,seat,(river%6-2.5)*.073,.59,.26+(river/6)*.09,false,sideways,"river tile overlaps a control");
                        for(int wall=0;wall<34;wall++)clearOfTile(bounds,pos,seat,-.59+(wall/2)*.073,.60+(wall%2)*.055,.70,true,false,"wall tile overlaps a control");
                        for(int stack=0;stack<7;stack++)for(double distance:List.of(.70,.6875,.6045))
                            clearOfTile(bounds,pos,seat,.59-stack*.073,.60,distance,false,false,"dead-wall/dora/ura tile overlaps a control");
                    }
                }
            }
        }
        for(String text:List.of("00","01","09","10","33","99")){
            double width=.025*ReplayOverlayLayout.scoreScale(text)*(TableButtonLayout.textWidth(text)+1);
            check(Math.abs(width-TableLayout.TILE_WIDTH)<1e-7,"two-digit score width differs from tile width");
        }
        check(ReplayOverlayLayout.scoreText(0).equals("00")&&ReplayOverlayLayout.scoreText(1).equals("01")&&ReplayOverlayLayout.scoreText(9).equals("09"),"single-digit score lacks its leading zero");
        check(ReplayOverlayLayout.scoreText(99).equals("99")&&ReplayOverlayLayout.scoreText(100).equals("99"),"display score must remain exactly two digits");
        check(TableButtonLayout.textWidth("01")==7&&TableButtonLayout.textWidth("09")==8,"two-digit font advances are wrong");
        check(TableButtonLayout.textWidth("100%") == 15,"percent glyph advance is wrong");
        check(TableButtonLayout.textWidth("0.5") == 10,"decimal glyph advance is wrong");
        check(ReplayOverlayLayout.showProbability(new ReplayAnalysis.Candidate("m1",1,2,.009,0,0)),"allocated integer 1 was hidden using its unrounded probability");
        check(!ReplayOverlayLayout.showProbability(new ReplayAnalysis.Candidate("m2",0,3,.004,0,0)),"integer 0 was shown as a candidate");
        check(!ReplayOverlayLayout.showProbability(new ReplayAnalysis.Candidate("m3",4,4,.04,0,0)),"fourth candidate displayed a score");
        check(ReplayOverlayLayout.sameKind("m0","m5")&&ReplayOverlayLayout.sameKind("p5","p0"),"red and normal fives should share a single displayed score");
        check(!ReplayOverlayLayout.sameKind("m5","p5"),"different tile suits were grouped");
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
        check(ReplayOverlayLayout.showPlayedProbability(decision(candidates,2)),"third chosen tile lost its score");
        check(!ReplayOverlayLayout.showPlayedProbability(decision(candidates,3)),"fourth chosen tile showed an extra score");
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
        check(labels.stream().anyMatch(s->s.startsWith("碰 ")&&s.endsWith("70")),"historical pon button lost its probability");
        check(labels.contains("跳过 30"),"historical pass button lost its probability");
        check(driver.match()==before,"displaying historical actions modified the match");
        var raw=List.of("吃","碰","杠","跳过","立直","无评分");
        var ranked=ReplayActionLabels.rankedLabels(raw,Arrays.asList(
                new ReplayActionLabels.Score(20,.200),new ReplayActionLabels.Score(30,.300),
                new ReplayActionLabels.Score(10,.100),new ReplayActionLabels.Score(40,.400),
                new ReplayActionLabels.Score(0,.004),null));
        check(ranked.equals(List.of("吃 20","碰 30","杠","跳过 40","立直","无评分")),"top three scores or unscored button order incorrect");
        var layout=TableButtonLayout.arrange(ranked);
        for(int i=0;i<raw.size();i++)check(layout.get(i).index()==i,"action button index changed after ranking");
        check(labels.stream().noneMatch(s->s.contains("%"))&&ranked.stream().noneMatch(s->s.contains("%")),"action scores include a percent sign");
    }
    public static void main(String[] args){panelGeometry();colors();actionLabels();System.out.println("PASS: river-side controls in every orientation, probability widths, actual-discard colors and read-only claim labels");}
}
