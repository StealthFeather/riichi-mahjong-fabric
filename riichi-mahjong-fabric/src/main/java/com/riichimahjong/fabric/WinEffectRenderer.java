package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.*;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Presentation owned by the table's chunk holder. No entity is added to the server world. */
final class WinEffectRenderer {
    record Tile(int code,Vec3 position,float scale,Quaternionf rotation) {}
    interface TileSink { void draw(String key,int code,Vec3 position,float scale,Quaternionf rotation); }
    private record ActorKey(WinEffectFrame.Actor kind,int winner) {}
    private final ElementHolder holder;
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final List<SmallFangsDisplay> temporary=new ArrayList<>();
    private final Map<ActorKey,RonActorDisplay> actors=new HashMap<>();
    private WinEffectSession session;
    private Tile source;
    private boolean sourceConsumed;
    private int lastCueTick;

    WinEffectRenderer(ElementHolder holder,ServerLevel level,BlockPos pos,BlockState state) {
        this.holder=holder;this.level=level;this.pos=pos;this.state=state;
    }
    boolean active() { return session!=null; }
    boolean controls(int seat) { return session!=null&&session.context().includes(seat); }
    boolean hides(String slot) { return session!=null&&session.context().source().slot().equals(slot); }
    double reveal(int seat) { return controls(seat)?session.frame(seat).handReveal():1; }

    /** Bind before the normal tiles are updated, preserving the exact source position on screen. */
    void bind(WinEffectSession next,PublicTable board,Tile previousSource) { bind(next,board,previousSource,false); }
    void bind(WinEffectSession next,PublicTable board,Tile previousSource,boolean sourceConsumed) {
        if(next==session)return;
        destroy();session=next;lastCueTick=next==null?-1:next.elapsedTicks()-1;
        this.sourceConsumed=sourceConsumed;
        if(next!=null)source=previousSource==null?sourceFromBoard(next.context().source(),board):
                new Tile(next.context().source().tileCode(),previousSource.position(),previousSource.scale(),new Quaternionf(previousSource.rotation()));
    }
    void render(PublicTable board,TileSink sink) {
        if(session==null)return;
        var frame=session.frame();
        var pose=frame.source();
        boolean finalSelfDraw=!session.effect().consumesSource()&&session.context().source().slot().startsWith("hand/");
        if(source!=null&&!sourceConsumed&&pose.visible()&&!finalSelfDraw)sink.draw("effect/source",source.code(),source.position().add(pose.x(),pose.y(),pose.z()),
                source.scale()*pose.scale(),new Quaternionf(source.rotation()).rotateX((float)(Math.PI/2*pose.flip())));
        int tick=session.elapsedTicks();
        if (source != null && !sourceConsumed) for (var cue : session.effect().cues()) {
            if (cue.anchor() == WinEffectCue.Anchor.SOURCE && cue.tick() > lastCueTick && cue.tick() <= tick)
                cue(cue,source.position(),tick);
        }
        for (var winner : session.context().winners()) {
            var hand = board.seats().get(winner.seat());
            int last = hand.handCount() - 1;
            if (last < 0) continue;
            for (var cue : session.effect(winner.seat()).cues()) {
                if (cue.anchor() == WinEffectCue.Anchor.WINNER && cue.tick() > lastCueTick && cue.tick() <= tick)
                    cue(cue,handPosition(winner.seat(),hand,last,0),tick);
            }
        }
        lastCueTick=tick;
        temporary.removeIf(t->!t.update(tick));
        renderActors(board);
    }
    void winningTile(int seat,PublicTable.Seat hand,int index,int code,TileSink sink) {
        var frame=session.frame(seat);
        var pose=frame.winningTile();
        if(!pose.visible())return;
        // A single discarded tile stays visible at impact. Split only once the flights diverge.
        if(frame.travel()==0 && (sourceConsumed || seat!=session.context().winners().getFirst().seat()))return;
        var position=handPosition(seat,hand,index,pose.flip());
        var rotation=handRotation(seat,pose.flip());
        if(source!=null && frame.travel()<1) {
            position=source.position().lerp(position,frame.travel());
            rotation=new Quaternionf(source.rotation()).slerp(rotation,(float)frame.travel());
        }
        sink.draw("effect/winning/"+seat,code,position.add(pose.x(),pose.y(),pose.z()),
                TableLayout.TILE_SCALE*pose.scale(),rotation);
    }
    private void renderActors(PublicTable board) {
        var visible=new HashSet<ActorKey>();
        for(var winner:session.context().winners()) {
            var hand=board.seats().get(winner.seat());
            if(hand.handCount()==0)continue;
            for(var pose:session.frame(winner.seat()).actors()) {
                boolean shared=pose.anchor()==WinEffectCue.Anchor.SOURCE;
                if(shared && (sourceConsumed || source==null || session.effect(winner.seat())!=session.effect()))continue;
                var key=new ActorKey(pose.kind(),shared?-1:winner.seat());
                if(!visible.add(key))continue;
                var end=handPosition(winner.seat(),hand,hand.handCount()-1,1);
                var start=source==null?Vec3.atCenterOf(pos):source.position();
                var anchor=shared?start:end;
                var delta=shared?end.subtract(start):start.subtract(end);
                var facing=new Vec3(delta.x,0,delta.z).normalize();
                var actor=actors.get(key);
                if(actor==null) {
                    var felt=new Vec3(anchor.x,pos.getY()+TableLayout.TABLE_TOP,anchor.z);
                    actors.put(key,RonActorDisplay.create(holder,level,pose,felt,facing,anchor));
                } else actor.update(pose);
            }
        }
        for(var it=actors.entrySet().iterator();it.hasNext();) {
            var entry=it.next();
            if(!visible.contains(entry.getKey())) {
                entry.getValue().destroy();
                it.remove();
            }
        }
    }
    Vec3 handPosition(int seat,PublicTable.Seat hand,int index,double reveal) {
        var dir=PortViews.outward(seat,state);
        double along=TableLayout.handAlong(hand.handSlots().get(index),hand.handGapStarts());
        return TableLayout.handAnchor(pos,dir,along,handRotation(seat,reveal));
    }
    Quaternionf handRotation(int seat,double reveal) {
        var dir=PortViews.outward(seat,state);
        return PortViews.rotation(dir,false,false).slerp(PortViews.rotation(dir,true,false),(float)reveal);
    }
    private Tile sourceFromBoard(WinEffectContext.Source source,PublicTable board) {
        var parts=source.slot().split("/");
        if(parts.length<2)return null;
        int seat=Integer.parseInt(parts[1]);
        if(seat<0||seat>=board.seats().size())return null;
        var hand=board.seats().get(seat);Direction dir=PortViews.outward(seat,state);
        if(parts[0].equals("river")&&parts.length==3) {
            int index=Integer.parseInt(parts[2]);
            boolean sideways=index<hand.river().size()&&hand.river().get(index).sideways();
            return new Tile(source.tileCode(),RiverDisplay.position(pos,dir,index),TableLayout.TILE_SCALE,
                    RiverDisplay.rotation(dir,sideways));
        }
        if(parts[0].equals("hand")&&parts.length==3) {
            int index=Integer.parseInt(parts[2]);
            if(index<hand.handSlots().size())return new Tile(source.tileCode(),handPosition(seat,hand,index,0),TableLayout.TILE_SCALE,handRotation(seat,0));
        }
        // A robbed kakan/kita tile is held outside the rule hand, beside its standing tiles.
        int index=hand.handCount();
        double along=TableLayout.handAlong(index,index+1,true);
        return new Tile(source.tileCode(),TableLayout.handAnchor(pos,dir,along,false),TableLayout.TILE_SCALE,PortViews.rotation(dir,false,false));
    }
    private void cue(WinEffectCue cue,Vec3 at,int tick) {
        if(cue.type().equals("sound")) {
            BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(cue.resource())).ifPresent(sound->
                    level.playSound(null,at.x,at.y,at.z,sound,SoundSource.BLOCKS,cue.volume(),cue.pitch()));
        } else if(cue.resource().equals("minecraft:evoker_fangs")) {
            var feet=new Vec3(at.x,pos.getY()+TableLayout.TABLE_TOP,at.z);
            temporary.add(new SmallFangsDisplay(holder,feet,tick));
        }
    }
    void destroy() {
        for(var t:temporary)t.destroy();
        actors.values().forEach(RonActorDisplay::destroy);
        actors.clear();
        temporary.clear();session=null;source=null;
    }
}
