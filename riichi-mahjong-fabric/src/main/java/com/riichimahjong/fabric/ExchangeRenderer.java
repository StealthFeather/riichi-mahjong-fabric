package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Public backs and audience-filtered faces for one transient accepted exchange. */
final class ExchangeRenderer {
    record Pose(Vec3 position,Quaternionf rotation,float scale) {}
    private static final class Pair {
        ItemDisplayElement back,face;
        Set<UUID> faceAudience=Set.of();
    }
    private final ElementHolder holder;
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final List<Pair> tiles=new ArrayList<>();
    private TextDisplayElement label;
    private ExchangePresentation.Session session;

    ExchangeRenderer(ElementHolder holder,ServerLevel level,BlockPos pos,BlockState state) {
        this.holder=holder;this.level=level;this.pos=pos;this.state=state;
    }
    boolean active() { return session!=null; }

    void update(ExchangePresentation.Session next,MahjongTableBlockEntity table) {
        if(next!=session) {
            destroy();session=next;
            if(next==null) return;
            for(var ignored:next.flights())tiles.add(new Pair());
            label=new TextDisplayElement(Component.literal("换三张 · "+next.directionLabel()));
            label.setOverridePos(Vec3.atCenterOf(pos).add(0,.65,0));
            label.setScale(new Vector3f(.16f));label.setBillboardMode(Display.BillboardConstraints.CENTER);
            label.setDisplaySize(3,2);label.setBackground(0xA0222222);holder.addElement(label);
        }
        if(session==null)return;
        var spectators=new HashSet<UUID>();
        if(table.spectatorHandsVisible())for(var uuid:table.spectators()) {
            var player=level.getServer().getPlayerList().getPlayer(uuid);
            if(player!=null&&table.seatOfPlayer(uuid).isEmpty()&&PortViews.viewingNear(player,level,pos))spectators.add(uuid);
        }
        for(int index=0;index<tiles.size();index++) {
            var flight=session.flights().get(index);var pair=tiles.get(index);
            var audience=new HashSet<>(spectators);
            int visibleSeat=faceSeat(flight,session.elapsedTicks());
            if(visibleSeat<table.seats().size())table.seats().get(visibleSeat).occupant().ifPresent(uuid->{
                var player=level.getServer().getPlayerList().getPlayer(uuid);
                if(player!=null&&PortViews.viewingNear(player,level,pos))audience.add(uuid);
            });
            var allowed=Set.copyOf(audience);
            var pose=pose(session,flight,pos,state,session.elapsedTicks());
            if(pair.back==null||!allowed.equals(pair.faceAudience)) {
                remove(pair);pair.faceAudience=allowed;
                pair.back=create("bridge/tile_back",pose);
                pair.back.setVisibilityPredicate(player->!allowed.contains(player.getUUID()));
                pair.face=create(PortViews.tileModel(PublicTable.code(flight.tile()),session.sichuan()),pose);
                pair.face.setVisibilityPredicate(player->allowed.contains(player.getUUID()));
                // Set both allowlists before adding to a watched holder: hidden identities never spawn publicly.
                holder.addElement(pair.back);holder.addElement(pair.face);
            }
            apply(pair.back,pose);apply(pair.face,pose);
        }
    }

    static int faceSeat(ExchangePresentation.Flight flight,int tick) {
        return flight.exchanged()&&tick>=(ExchangePresentation.LIFT_END+ExchangePresentation.TRAVEL_END)/2
                ?flight.targetSeat():flight.sourceSeat();
    }
    private ItemDisplayElement create(String model,Pose pose) {
        var display=new ItemDisplayElement(PortViews.model(model));display.setItemDisplayContext(ItemDisplayContext.NONE);
        display.setRightRotation(new Quaternionf().rotationY((float)Math.PI));
        display.setDisplaySize(3,3);display.setInterpolationDuration(1);display.setTeleportDuration(1);
        apply(display,pose);return display;
    }
    private static void apply(ItemDisplayElement display,Pose pose) {
        display.setOverridePos(pose.position());display.setLeftRotation(pose.rotation());
        display.setScale(new Vector3f(pose.scale()));display.startInterpolationIfDirty();
    }
    private void remove(Pair pair) {
        if(pair.back!=null)holder.removeElement(pair.back);
        if(pair.face!=null)holder.removeElement(pair.face);
        pair.back=null;pair.face=null;
    }

    static Pose pose(ExchangePresentation.Session session,ExchangePresentation.Flight flight,
            BlockPos pos,BlockState state,int elapsed) {
        double tick=Math.clamp(elapsed,0,ExchangePresentation.DURATION_TICKS);
        var sourceDir=PortViews.outward(flight.sourceSeat(),state);
        var targetDir=PortViews.outward(flight.targetSeat(),state);
        double sourceAlong=TableLayout.handAlong(flight.sourceIndex(),List.of());
        double targetAlong=TableLayout.handAlong(flight.targetIndex(),List.of());
        var start=TableLayout.handAnchor(pos,sourceDir,sourceAlong,false);
        var end=TableLayout.handAnchor(pos,targetDir,targetAlong,false);
        if(!flight.exchanged()) {
            double sort=smooth(tick,ExchangePresentation.TRAVEL_END,ExchangePresentation.DURATION_TICKS);
            return new Pose(start.lerp(end,sort),PortViews.rotation(sourceDir,false,false),TableLayout.TILE_SCALE);
        }
        double lift=smooth(tick,0,ExchangePresentation.LIFT_END);
        double travel=smooth(tick,ExchangePresentation.LIFT_END,ExchangePresentation.TRAVEL_END);
        double land=smooth(tick,ExchangePresentation.TRAVEL_END,ExchangePresentation.DURATION_TICKS);
        double flip=tick<=ExchangePresentation.LIFT_END?lift:1-land;
        var sourceRotation=PortViews.rotation(sourceDir,false,false).slerp(PortViews.rotation(sourceDir,true,false),(float)flip);
        var targetRotation=PortViews.rotation(targetDir,false,false).slerp(PortViews.rotation(targetDir,true,false),(float)flip);
        var rotation=sourceRotation.slerp(targetRotation,(float)travel);
        double cx=pos.getX()+.5,cz=pos.getZ()+.5;
        double sx=start.x-cx,sz=start.z-cz,tx=end.x-cx,tz=end.z-cz;
        double angle=Math.atan2(sz,sx),finish=Math.atan2(tz,tx),delta=finish-angle;
        boolean clockwise=session.direction()==session.playerCount()-1 || session.playerCount()==4&&session.direction()==2;
        if(clockwise) { while(delta<0)delta+=Math.PI*2; }
        else { while(delta>0)delta-=Math.PI*2; }
        double radius=Math.sqrt(sx*sx+sz*sz)*(1-travel)+Math.sqrt(tx*tx+tz*tz)*travel-.40*Math.sin(Math.PI*travel);
        double x=cx+Math.cos(angle+delta*travel)*radius,z=cz+Math.sin(angle+delta*travel)*radius;
        double support=support(rotation);
        double y=pos.getY()+TableLayout.TABLE_TOP+support+.25*lift*(1-land)+.08*Math.sin(Math.PI*travel);
        return new Pose(new Vec3(x,y,z),rotation,TableLayout.TILE_SCALE);
    }
    private static double support(Quaternionf rotation) {
        var modelRotation=new Quaternionf(rotation).rotateY((float)Math.PI);double minimum=Double.POSITIVE_INFINITY;
        var b=TableLayout.TILE_MODEL_BOUNDS;
        for(double x:new double[]{b.minX,b.maxX})for(double y:new double[]{b.minY,b.maxY})for(double z:new double[]{b.minZ,b.maxZ})
            minimum=Math.min(minimum,modelRotation.transform(new Vector3f((float)x,(float)y,(float)z)).y);
        return -minimum*TableLayout.TILE_SCALE;
    }
    private static double smooth(double tick,int start,int end) {
        double t=Math.clamp((tick-start)/(end-start),0,1);return t*t*(3-2*t);
    }
    void destroy() {
        tiles.forEach(this::remove);tiles.clear();
        if(label!=null)holder.removeElement(label);label=null;session=null;
    }
}
