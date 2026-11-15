package com.riichimahjong.fabric;

import java.util.*;
import com.riichimahjong.ai.AiSnapshot;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.replay.*;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Controls follow the selected river; the rim displays the historical legal operations. */
final class ReplayView {
    final ServerPlayer owner;
    private final MahjongTableBlockEntity table;
    private final ReplaySession session;
    private final long revision;
    private final ElementHolder holder=new ElementHolder();
    private final List<TableEdgeButtons> controls=new ArrayList<>(),actions=new ArrayList<>();
    private final List<TextDisplayElement> markers=new ArrayList<>();
    private List<List<String>> actionLabels=List.of();
    private Map<Integer,ReplayAnalysis.Decision> shownCurrent=Map.of();
    private ReplayAnalysis.Decision shownPlayed;
    private boolean destroyed;
    private String lastStatus="";

    ReplayView(MahjongTableBlockEntity table,ServerPlayer owner){
        this.table=table;this.owner=owner;session=table.replay();revision=session.revision();
        new ManualAttachment(holder,owner.level(),()->Vec3.atCenterOf(table.getBlockPos()));
        addPanel(true,progressControls(),List.of("上一巡","上一步",session.playing()?"暂停":"播放",
                "下一巡","下一步",session.speed()+"倍速"));
        addPanel(false,otherControls(),List.of("开局","上一局","下一局","终局",
                "视角："+TableSeating.wind(Math.floorMod(session.perspective()-table.driver().currentRound().dealerSeat(),table.driver().match().playerCount())),
                revealLabel(session.showAll()),session.analysisEnabled()?"分析：开":"分析：关","退出回放"));
        updateActions(Map.of());holder.startWatching(owner);
        if(session.analysisEnabled()){
            ReplayAnalysis.analyzeAll(session.record(),result->{});
            request(session.index());
            currentDiscard().ifPresent(d->{
                if(d.decisionFrame()!=session.index())request(d.decisionFrame());
            });
        }
        refresh();
    }
    static List<ReplaySession.Control> progressControls(){
        return List.of(ReplaySession.Control.PREVIOUS_TURN,ReplaySession.Control.PREVIOUS,ReplaySession.Control.PLAY_PAUSE,
                ReplaySession.Control.NEXT_TURN,ReplaySession.Control.NEXT,ReplaySession.Control.SPEED);
    }
    static List<ReplaySession.Control> otherControls(){
        return List.of(ReplaySession.Control.FIRST,ReplaySession.Control.PREVIOUS_ROUND,ReplaySession.Control.NEXT_ROUND,
                ReplaySession.Control.LAST,ReplaySession.Control.PERSPECTIVE,ReplaySession.Control.REVEAL,
                ReplaySession.Control.ANALYSIS,ReplaySession.Control.EXIT);
    }
    static String revealLabel(boolean showAll){return showAll?"隐藏他家手牌":"显示他家手牌";}
    private void addPanel(boolean right,List<ReplaySession.Control> buttons,List<String> labels){
        if(buttons.size()!=labels.size())throw new IllegalArgumentException("Mismatched replay controls and labels");
        controls.add(new TableEdgeButtons(holder,table,owner,session.perspective(),
                TableButtonLayout.replayPanel(labels,right),this::valid,
                index->table.controlReplay(owner,buttons.get(index)),true));
    }
    private Optional<ReplayTimeline.Discard> currentDiscard(){
        return session.timeline().discardForDecision(session.timeline().decisionFrame(session.index(),session.perspective()),session.perspective());
    }
    private void request(int frame){
        ReplayAnalysis.request(session.record(),frame,null,result->
                owner.level().getServer().execute(()->{if(!destroyed&&valid())refresh();}));
    }
    boolean sameState(){return table.replay()==session&&session.revision()==revision;}
    private boolean valid(){return sameState()&&!table.isRemoved()&&table.getLevel()==owner.level()
            &&table.isHost(owner.getUUID())&&PortViews.near(owner,owner.level(),table.getBlockPos());}
    boolean handleButtonHit(Vec3 start,Vec3 end){
        if(!valid())return false;
        for(var control:controls)if(control.handleHit(start,end))return true;
        return false;
    }
    void tick(){refresh();holder.tick();}
    private void refresh(){
        if(!valid())return;
        var current=session.analysisEnabled()?ReplayAnalysis.cached(session.record().id(),session.index()).orElse(null):null;
        Map<Integer,ReplayAnalysis.Decision> decisions=current!=null&&current.available()?current.decisions():Map.of();
        updateActions(decisions);
        var played=currentDiscard();
        ReplayAnalysis.Decision actual=null;
        if(session.analysisEnabled()&&played.isPresent()){
            var cached=ReplayAnalysis.cached(session.record().id(),played.get().decisionFrame()).orElse(null);
            if(cached!=null&&cached.available())actual=cached.decisionForSeat(session.perspective()).orElse(null);
        }
        if(!Objects.equals(shownCurrent,decisions)||!Objects.equals(shownPlayed,actual)){
            shownCurrent=decisions;shownPlayed=actual;
            markers.forEach(holder::removeElement);markers.clear();
            var selected=decisions.get(session.perspective());
            if(selected!=null)showProbabilities(selected,actual);
            if(actual!=null&&played.isPresent())showPlayed(actual);
        }
        String status="";
        if(session.analysisEnabled()){
            if(current!=null&&!current.available())status="分析暂不可用";
        }
        if(!status.equals(lastStatus)){owner.sendSystemMessage(Component.literal(status).withStyle(ChatFormatting.YELLOW),true);lastStatus=status;}
    }
    private void updateActions(Map<Integer,ReplayAnalysis.Decision> decisions){
        var driver=table.driver();if(driver==null)return;
        var labels=new ArrayList<List<String>>();
        for(int seat=0;seat<driver.match().playerCount();seat++)labels.add(ReplayActionLabels.labels(driver,seat,decisions.get(seat)));
        if(labels.equals(actionLabels))return;
        actionLabels=List.copyOf(labels);actions.forEach(TableEdgeButtons::destroy);actions.clear();
        for(int seat=0;seat<labels.size();seat++)if(!labels.get(seat).isEmpty())
            actions.add(new TableEdgeButtons(holder,table,owner,seat,TableButtonLayout.arrange(labels.get(seat)),this::valid,index->{},false));
    }
    private void showProbabilities(ReplayAnalysis.Decision decision,ReplayAnalysis.Decision actual){
        int seat=decision.seat();
        if(!PublicTable.revealedSeats(table).contains(seat))return;
        var hand=table.handDisplayOrder(seat);var slots=table.handDisplaySlots(seat);var gaps=table.handDisplayGapStarts(seat);
        var dir=PortViews.outward(seat,table.getBlockState());
        for(var candidate:decision.candidates()){
            if(!ReplayOverlayLayout.showProbability(candidate))continue;
            var location=session.timeline().locationAt(session.index(),session.perspective());
            if(actual!=null&&(location.zone()==ReplayTimeline.Zone.HAND||location.zone()==ReplayTimeline.Zone.DRAWN)
                    &&ReplayOverlayLayout.showPlayedProbability(actual)&&ReplayOverlayLayout.sameKind(candidate.tile(),actual.chosenTile()))continue;
            int index=-1;
            for(int i=0;i<hand.size();i++)if(ReplayOverlayLayout.sameKind(AiSnapshot.tile(hand.get(i)),candidate.tile())){index=i;break;}
            if(index<0)continue;
            var at=TableLayout.handAnchor(table.getBlockPos(),dir,TableLayout.handAlong(slots.get(index),gaps),true);
            String text=ReplayOverlayLayout.scoreText(candidate.probability());
            marker(text,at.add(0,.19*TableLayout.TILE_ENLARGEMENT,0),ReplayOverlayLayout.scoreScale(text),ChatFormatting.WHITE);
        }
    }
    private void showPlayed(ReplayAnalysis.Decision decision){
        if(decision.chosenTile()==null||decision.chosenProbability()<0)return;
        var location=session.timeline().locationAt(session.index(),session.perspective());
        var at=ReplayOverlayLayout.playedTile(table,location);if(at.isEmpty())return;
        var color=ReplayOverlayLayout.color(decision);
        marker("▼",at.get().add(0,.11*TableLayout.TILE_ENLARGEMENT,0),.18f*TableLayout.TILE_ENLARGEMENT,color);
        if(ReplayOverlayLayout.showPlayedProbability(decision)){
            String score=ReplayOverlayLayout.scoreText(decision.chosenProbability());
            marker(score,at.get().add(0,.21*TableLayout.TILE_ENLARGEMENT,0),ReplayOverlayLayout.scoreScale(score),color);
        }
    }
    private void marker(String text,Vec3 pos,float scale,ChatFormatting color){
        var display=new TextDisplayElement(Component.literal(text).withStyle(style->style.withColor(color)
                .withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("uniform")))));
        display.setOverridePos(pos);display.setScale(new Vector3f(scale));display.setLineWidth(1000);
        display.setBillboardMode(Display.BillboardConstraints.CENTER);display.setBackground(text.equals("▼")?0:0x80333333);
        display.setDisplaySize(2,2);display.setVisibilityPredicate(p->p==owner);holder.addElement(display);markers.add(display);
    }
    void destroy(){destroyed=true;if(!lastStatus.isEmpty())owner.sendSystemMessage(Component.empty(),true);holder.destroy();}
}
