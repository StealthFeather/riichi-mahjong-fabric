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

/** Replay controls use exactly the live hand's shared edge-button implementation. */
final class ReplayView {
    final ServerPlayer owner;
    private final MahjongTableBlockEntity table;
    private final ReplaySession session;
    private final long revision;
    private final ElementHolder holder=new ElementHolder();
    private final List<TableEdgeButtons> edges=new ArrayList<>();
    private final List<TextDisplayElement> markers=new ArrayList<>();
    private boolean destroyed;
    private ReplayAnalysis.Result shown;

    ReplayView(MahjongTableBlockEntity table,ServerPlayer owner){
        this.table=table;this.owner=owner;session=table.replay();revision=session.revision();
        new ManualAttachment(holder,owner.level(),()->Vec3.atCenterOf(table.getBlockPos()));
        addEdge(0,List.of(ReplaySession.Control.FIRST,ReplaySession.Control.PREVIOUS,ReplaySession.Control.NEXT,ReplaySession.Control.LAST),
                List.of("开局","上一步","下一步","终局"));
        addEdge(1,List.of(ReplaySession.Control.PREVIOUS_ROUND,ReplaySession.Control.PLAY_PAUSE,ReplaySession.Control.NEXT_ROUND,ReplaySession.Control.SPEED),
                List.of("上一局",session.playing()?"暂停":"播放","下一局",session.speed()+"倍速"));
        addEdge(2,List.of(ReplaySession.Control.PERSPECTIVE,ReplaySession.Control.REVEAL,ReplaySession.Control.ANALYSIS,ReplaySession.Control.EXIT),
                List.of("视角："+TableSeating.wind(session.perspective()),session.showAll()?"隐藏他家":"显示全部",session.analysisEnabled()?"分析：开":"分析：关","退出回放"));
        holder.startWatching(owner);
        if(session.analysisEnabled()){
            ReplayAnalysis.analyzeAll(session.record(),result->{});
            ReplayAnalysis.request(session.record(),session.index(),table.driver(),result->
                    owner.level().getServer().execute(()->{if(!destroyed&&sameState())showAnalysis(result);}));
        }
    }

    private void addEdge(int seat,List<ReplaySession.Control> controls,List<String> labels){
        edges.add(new TableEdgeButtons(holder,table,owner,seat,labels,this::valid,
                index->table.controlReplay(owner,controls.get(index))));
    }
    boolean sameState(){return table.replay()==session&&session.revision()==revision;}
    private boolean valid(){return sameState()&&!table.isRemoved()&&table.getLevel()==owner.level()&&table.isHost(owner.getUUID())
            &&PortViews.near(owner,owner.level(),table.getBlockPos());}
    boolean handleButtonHit(Vec3 start,Vec3 end){
        if(!valid())return false;
        for(var edge:edges)if(edge.handleHit(start,end))return true;
        return false;
    }
    void tick(){
        if(session.analysisEnabled()){
            var result=ReplayAnalysis.cached(session.record().id(),session.index()).orElse(null);
            if(result!=null&&!result.equals(shown))showAnalysis(result);
        }
        holder.tick();
    }
    private void showAnalysis(ReplayAnalysis.Result result){
        if(!valid()||!session.analysisEnabled()||result.frame()!=session.index())return;
        shown=result;markers.forEach(holder::removeElement);markers.clear();
        var driver=table.driver();if(driver==null||driver.match().currentRound().isEmpty())return;
        int seat=result.seat();if(seat<0||seat>=driver.match().playerCount())return;
        var hand=table.handDisplayOrder(seat);var slots=table.handDisplaySlots(seat);var gaps=table.handDisplayGapStarts(seat);
        var dir=PortViews.outward(seat,table.getBlockState());
        Set<Integer> marked=new HashSet<>();int rank=0;
        for(var candidate:result.candidates()){
            if(candidate.score()<1||rank>=3)continue;
            int index=-1;
            for(int i=0;i<hand.size();i++)if(!marked.contains(i)&&AiSnapshot.tile(hand.get(i)).equals(candidate.tile())){index=i;break;}
            if(index<0)continue;
            marked.add(index);var color=rank++==0?ChatFormatting.GREEN:ChatFormatting.YELLOW;
            var at=TableLayout.handAnchor(table.getBlockPos(),dir,TableLayout.handAlong(slots.get(index),gaps),true);
            marker("▼",at.add(0,.15*TableLayout.TILE_ENLARGEMENT,0),.18f*TableLayout.TILE_ENLARGEMENT,color);
            String score=Integer.toString(Math.clamp(candidate.score(),0,99));
            // Use uniform glyph metrics to make the score background exactly one tile wide.
            float scale=(float)(TableLayout.TILE_WIDTH/(.025*(TableButtonLayout.textWidth(score)+1)));
            marker(score,at.add(0,.25*TableLayout.TILE_ENLARGEMENT,0),scale,color);
        }
        String status=result.error()==null||result.error().isBlank()
                ?"AI 分析"+(result.chosenScore()>=0?" · 实战出牌 "+result.chosenScore()+"分":"")
                :"分析暂不可用";
        owner.sendSystemMessage(Component.literal(status).withStyle(ChatFormatting.YELLOW),true);
    }
    private void marker(String text,Vec3 pos,float scale,ChatFormatting color){
        var display=new TextDisplayElement(Component.literal(text).withStyle(style->style.withColor(color)
                .withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("uniform")))));
        display.setOverridePos(pos);display.setScale(new Vector3f(scale));display.setLineWidth(1000);
        display.setBillboardMode(Display.BillboardConstraints.CENTER);display.setBackground(text.equals("▼")?0:0x80333333);
        display.setDisplaySize(2,2);display.setVisibilityPredicate(p->p==owner);holder.addElement(display);markers.add(display);
    }
    void destroy(){destroyed=true;holder.destroy();}
}
