package com.riichimahjong.mahjongaltar;

import com.mojang.serialization.MapCodec;
import com.riichimahjong.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class MahjongAltarBlock extends BaseEntityBlock {
    public static final MapCodec<MahjongAltarBlock> CODEC = simpleCodec(MahjongAltarBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(
            box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0),
            box(3.0, 1.0, 3.0, 13.0, 10.0, 13.0),
            box(4.0, 10.0, 4.0, 12.0, 11.0, 12.0));

    public MahjongAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<MahjongAltarBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof MahjongAltarBlockEntity altar)) return InteractionResult.TRY_WITH_EMPTY_HAND;

        boolean changed = altar.insertOneFromPlayer(serverPlayer, hand);
        return changed ? InteractionResult.CONSUME : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof MahjongAltarBlockEntity altar)) return InteractionResult.PASS;

        boolean changed = player.isShiftKeyDown()
                ? altar.extractAllToPlayer(serverPlayer)
                : altar.extractOneToPlayer(serverPlayer);
        return changed ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MahjongAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(
                type,
                ModBlockEntities.MAHJONG_ALTAR_BLOCK_ENTITY.get(),
                MahjongAltarBlockEntity::serverTick);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MahjongAltarBlockEntity altar) {
            Containers.dropContents(level, pos, altar);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
