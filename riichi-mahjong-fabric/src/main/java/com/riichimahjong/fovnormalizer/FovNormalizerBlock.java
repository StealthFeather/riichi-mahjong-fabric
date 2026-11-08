// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.fovnormalizer;

import com.mojang.serialization.MapCodec;
import com.riichimahjong.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("deprecation")
public class FovNormalizerBlock extends BaseEntityBlock implements com.riichimahjong.fabric.VisibleBlock {


    public FovNormalizerBlock(Properties properties) {
        super(properties);
    }



    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FovNormalizerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(
                type,
                ModBlockEntities.FOV_NORMALIZER_BLOCK_ENTITY.get(),
                FovNormalizerBlockEntity::serverTick);
    }
}
