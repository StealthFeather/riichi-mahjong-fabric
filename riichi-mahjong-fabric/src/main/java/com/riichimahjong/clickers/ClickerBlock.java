// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.clickers;

import com.mojang.serialization.MapCodec;
import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

@SuppressWarnings("deprecation")
public abstract class ClickerBlock extends DirectionalBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    private static final int TRIGGER_DELAY_TICKS = 2;

    protected ClickerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TRIGGERED, false));
    }



    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TRIGGERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        boolean signal = level.hasNeighborSignal(pos);
        boolean triggered = state.getValue(TRIGGERED);
        if (signal && !triggered) {
            level.scheduleTick(pos, this, TRIGGER_DELAY_TICKS);
            level.setBlock(pos, state.setValue(TRIGGERED, Boolean.TRUE), Block.UPDATE_CLIENTS);
        } else if (!signal && triggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, Boolean.FALSE), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        BlockPos targetPos = pos.relative(facing);
        BlockEntity be = level.getBlockEntity(targetPos);
        if (be instanceof YakuGeneratorBlockEntity gen) {
            performClick(level, pos, gen);
        }
    }

    protected abstract void performClick(ServerLevel level, BlockPos pos, YakuGeneratorBlockEntity target);
}
