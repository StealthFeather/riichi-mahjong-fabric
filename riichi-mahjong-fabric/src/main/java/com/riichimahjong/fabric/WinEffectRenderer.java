package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.*;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import java.util.ArrayList;
import java.util.List;
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
    private final ElementHolder holder;
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final List<SmallFangsDisplay> temporary=new ArrayList<>();
    private WinEffectSession session;
    private Tile source;
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
        if(next!=null&&!sourceConsumed)source=previousSource==null?sourceFromBoard(next.context().source(),board):
                new Tile(next.context().source().tileCode(),previousSource.position(),previousSource.scale(),new Quaternionf(previousSource.rotation()));
    }
    void render(PublicTable board,TileSink sink) {
        if(session==null)return;
        var pose=session.frame().source();
        boolean finalSelfDraw=!session.effect().consumesSource()&&session.context().source().slot().startsWith("hand/");
        if(source!=null&&pose.visible()&&!finalSelfDraw)sink.draw("effect/source",source.code(),source.position().add(pose.x(),pose.y(),pose.z()),
                source.scale()*pose.scale(),new Quaternionf(source.rotation()).rotateX((float)(Math.PI/2*pose.flip())));
        int tick=session.elapsedTicks();
        if(source!=null)for(var cue:session.effect().cues())
            if(cue.anchor()==WinEffectCue.Anchor.SOURCE && cue.tick()>lastCueTick && cue.tick()<=tick)
                cue(cue,source.position(),tick);
        for(var winner:session.context().winners()) {
            var seat=board.seats().get(winner.seat());
            int last=seat.handCount()-1;
            if(last<0)continue;
            for(var cue:session.effect(winner.seat()).cues())
                if(cue.anchor()==WinEffectCue.Anchor.WINNER && cue.tick()>lastCueTick && cue.tick()<=tick)
                    cue(cue,handPosition(winner.seat(),seat,last,0),tick);
        }
        lastCueTick=tick;
        temporary.removeIf(t->!t.update(tick));
    }
    void winningTile(int seat,PublicTable.Seat hand,int index,int code,TileSink sink) {
        var pose=session.frame(seat).winningTile();
        if(pose.visible())sink.draw("effect/winning/"+seat,code,
                handPosition(seat,hand,index,pose.flip()).add(pose.x(),pose.y(),pose.z()),
                TableLayout.TILE_SCALE*pose.scale(),handRotation(seat,pose.flip()));
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
            var rotation=PortViews.rotation(dir,true,false);if(sideways)rotation.rotateLocalY((float)Math.PI/2);
            return new Tile(source.tileCode(),TableLayout.anchor(pos,dir,(index%6-2.5)*.073,.59,.26+(index/6)*.09),TableLayout.TILE_SCALE,rotation);
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
        temporary.clear();session=null;source=null;
    }
}
