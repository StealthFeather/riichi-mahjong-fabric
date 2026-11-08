package com.riichimahjong.fabric;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public interface VisibleBlock extends PolymerBlock {
    VisibleBlock INSTANCE=new VisibleBlock(){};
    @Override default BlockState getPolymerBlockState(BlockState state,PacketContext context){
        if(state.getBlock() instanceof com.riichimahjong.common.BaseMultipartBlock)return Blocks.AIR.defaultBlockState();
        return state.getBlock() instanceof LiquidBlock?Blocks.WATER.defaultBlockState():Blocks.BARRIER.defaultBlockState();
    }
    @Override default void onPolymerBlockSend(BlockState state,BlockPos.MutableBlockPos pos,ServerPlayer player){PortViews.ensure(player,pos.immutable());}
    // Multipart tables use the virtual shulker on both sides, including the rim.
    @Override default boolean overridePlayerCollisionsWithPolymer(net.minecraft.world.level.BlockGetter level,BlockPos pos,BlockState state,ServerPlayer player){return state.getBlock() instanceof com.riichimahjong.common.BaseMultipartBlock;}
}
