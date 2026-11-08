package com.riichimahjong.fabric;

import java.util.*;
import java.util.function.Consumer;
import com.riichimahjong.mahjongtable.MahjongTableBlock;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlock;
import com.riichimahjong.mixin.DisplayArmorStandAccessor;
import com.riichimahjong.mixin.DisplayMobAccessor;
import com.riichimahjong.mixin.DisplayShulkerAccessor;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** One virtual shulker per table, with identical client and server player collision. */
public final class TableCollision {
    private static final Map<ServerLevel,Map<Long,Set<Registration>>> INDEX=new HashMap<>();
    private static Set<Long> chunks(AABB box){
        var keys=new HashSet<Long>();
        for(int x=net.minecraft.util.Mth.floor(box.minX)>>4;x<=net.minecraft.util.Mth.floor(box.maxX)>>4;x++)
            for(int z=net.minecraft.util.Mth.floor(box.minZ)>>4;z<=net.minecraft.util.Mth.floor(box.maxZ)>>4;z++)keys.add(ChunkPos.pack(x,z));
        return keys;
    }
    static final class Registration {
        final ElementHolder holder;final ServerLevel level;final BlockPos pos;final BlockState state;
        final AABB bounds;final VoxelShape shape;final Set<Long> buckets;
        Registration(ElementHolder holder,ServerLevel level,BlockPos pos,BlockState state,Vec3 feet,float size){
            this.holder=holder;this.level=level;this.pos=pos.immutable();this.state=state;
            bounds=Shulker.getProgressAabb(size,Direction.UP,0,feet);shape=Shapes.create(bounds);buckets=chunks(bounds);
            var index=INDEX.computeIfAbsent(level,ignored->new HashMap<>());
            for(long key:buckets)index.computeIfAbsent(key,ignored->new HashSet<>()).add(this);
        }
        boolean active(){
            if(holder.getAttachment()==null||holder.getAttachment().isRemoved())return false;
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            return chunk!=null&&chunk.getBlockState(pos)==state;
        }
        void destroy(){
            var index=INDEX.get(level);if(index==null)return;
            for(long key:buckets){var bucket=index.get(key);if(bucket==null)continue;bucket.remove(this);if(bucket.isEmpty())index.remove(key);}
            if(index.isEmpty())INDEX.remove(level);
        }
    }
    public static List<VoxelShape> append(ServerLevel level,Entity entity,AABB query,List<VoxelShape> original){
        if(!(entity instanceof ServerPlayer)||entity.isSpectator())return original;
        var index=INDEX.get(level);if(index==null)return original;
        var candidates=new HashSet<Registration>();
        for(long key:chunks(query)){var bucket=index.get(key);if(bucket!=null)candidates.addAll(bucket);}
        List<VoxelShape> result=null;
        for(var collider:candidates)if(collider.bounds.intersects(query)&&collider.active()){
            if(result==null)result=new ArrayList<>(original);result.add(collider.shape);
        }
        return result==null?original:List.copyOf(result);
    }
    static Registration add(ElementHolder holder,ServerLevel level,BlockPos pos,BlockState state){
        var handler=new VirtualElement.InteractionHandler(){
            @Override public void interact(ServerPlayer player,InteractionHand hand,Vec3 location,boolean sneaking){
                if(hand!=InteractionHand.MAIN_HAND||!PortViews.near(player,level,pos)||level.getBlockState(pos)!=state)return;
                boolean previous=player.isShiftKeyDown();player.setShiftKeyDown(sneaking);
                try{
                    if(state.getBlock() instanceof MahjongTableBlock table)table.dispatchUse(state,level,pos,player,hand,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
                    else if(state.getBlock() instanceof MahjongSolitaireBlock solitaire)solitaire.dispatchUse(state,level,pos,player,hand);
                }finally{player.setShiftKeyDown(previous);}
            }
            @Override public void attack(ServerPlayer player){
                if(PortViews.near(player,level,pos)&&level.getBlockState(pos)==state)player.gameMode.destroyBlock(pos);
            }
        };
        double top=state.getBlock() instanceof MahjongTableBlock?9.0/16:1.0/16;
        float size=state.getBlock() instanceof MahjongTableBlock?TableLayout.TABLE_WIDTH:2;
        var feet=new Vec3(pos.getX()+.5,pos.getY()+top-size,pos.getZ()+.5);
        var anchor=new SimpleEntityElement(EntityTypes.ARMOR_STAND);
        anchor.setInvisible(true);anchor.setSilent(true);anchor.setNoGravity(true);
        anchor.getSyncedData().set(DisplayArmorStandAccessor.riichi$flags(),(byte)0x10);
        anchor.setOverridePos(feet);holder.addElement(anchor);
        var collider=new Collider(anchor.getEntityId(),size);collider.setOverridePos(feet);
        collider.setInteractionHandler(handler);holder.addElement(collider);
        return new Registration(holder,level,pos,state,feet,size);
    }
    private static final class Collider extends SimpleEntityElement {
        final int anchor;final float size;
        Collider(int anchor,float size){
            super(EntityTypes.SHULKER);this.anchor=anchor;this.size=size;
            // SimpleSynchedEntityData requires the ORIGINAL accessor instance;
            // recreating an accessor from its numeric id silently discards the write.
            setInvisible(true);setSilent(true);setNoGravity(true);
            getSyncedData().set(DisplayMobAccessor.riichi$flags(),(byte)1);
            getSyncedData().set(DisplayShulkerAccessor.riichi$peek(),(byte)0);
            getSyncedData().set(DisplayShulkerAccessor.riichi$face(),Direction.DOWN);
        }
        @Override public void startWatching(ServerPlayer player,Consumer<Packet<ClientGamePacketListener>> packets){
            super.startWatching(player,packets);
            var scale=new AttributeInstance(Attributes.SCALE,ignored->{});scale.setBaseValue(size);
            packets.accept(new ClientboundUpdateAttributesPacket(getEntityId(),List.of(scale)));
            // Riding a marker prevents the client shulker from snapping back to the block grid.
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try{buffer.writeVarInt(anchor);buffer.writeVarIntArray(new int[]{getEntityId()});packets.accept(ClientboundSetPassengersPacket.STREAM_CODEC.decode(buffer));}
            finally{buffer.release();}
        }
    }
}
