package com.riichimahjong.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.themahjong.driver.MatchPhase;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** One authorized viewer, face-up copies only, and no interaction entities or gameplay callbacks. */
final class SpectatorHandView {
    record Hand(int seat,List<Integer> codes,List<Integer> slots,List<Integer> gaps){}
    record Snapshot(Object driver,List<Hand> hands,Set<Integer> glint){}
    final ServerPlayer owner;
    private final MahjongTableBlockEntity table;
    private final Snapshot snapshot;
    private final ElementHolder holder=new ElementHolder();
    private boolean destroyed;

    static Snapshot snapshot(MahjongTableBlockEntity table,PublicTable board){
        var hands=new ArrayList<Hand>();var publicFaces=PublicTable.revealedSeats(table);var driver=table.driver();
        double fraction=board.stage()==MatchPhase.Stage.WALL_BUILDING?0:board.stage()==MatchPhase.Stage.DEALING_HANDS?board.progress():1;
        if(driver!=null)for(int seat=0;seat<driver.match().playerCount();seat++){
            if(publicFaces.contains(seat))continue;
            var order=table.handDisplayOrder(seat);int count=(int)Math.round(order.size()*fraction);
            if(count==0)continue;
            hands.add(new Hand(seat,order.stream().limit(count).map(PublicTable::code).toList(),
                    List.copyOf(table.handDisplaySlots(seat).subList(0,count)),List.copyOf(table.handDisplayGapStarts(seat))));
        }
        return new Snapshot(driver,List.copyOf(hands),Set.copyOf(board.glowingTiles()));
    }
    SpectatorHandView(MahjongTableBlockEntity table,ServerPlayer viewer,Snapshot snapshot){
        this.table=table;this.owner=viewer;this.snapshot=snapshot;
        if(!valid()){destroy();return;}
        var pos=table.getBlockPos();
        new ManualAttachment(holder,viewer.level(),()->Vec3.atCenterOf(pos));
        for(var hand:snapshot.hands()){
            var dir=PortViews.outward(hand.seat(),table.getBlockState());
            for(int i=0;i<hand.codes().size();i++){
                int code=hand.codes().get(i);
                var at=TableLayout.handAnchor(pos,dir,TableLayout.handAlong(hand.slots().get(i),hand.gaps()),true);
                // The audience predicate is installed before addElement, and this holder has no chunk audience.
                PortViews.displayForViewer(holder,PortViews.tileModel(code),at,TableLayout.TILE_SCALE,
                        PortViews.rotation(dir,true,false),viewer,snapshot.glint().contains(code));
            }
        }
        if(valid())holder.startWatching(viewer);else destroy();
    }
    boolean sameVisuals(Snapshot other){return snapshot.equals(other);}
    boolean valid(){return !destroyed&&snapshot.driver()==table.driver()&&SpectatorVisibility.canView(table,owner);}
    void tick(){if(!valid()){destroy();return;}holder.tick();}
    void destroy(){if(!destroyed){destroyed=true;holder.destroy();}}
}
