package com.riichimahjong.mahjongsolitaire;

import com.mojang.serialization.MapCodec;
import com.riichimahjong.common.BaseMultipartBlock;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

@SuppressWarnings("deprecation")
public class MahjongSolitaireBlock extends BaseMultipartBlock {
    private static final List<double[]> MODEL_BOXES = List.of(
            new double[] {-8, 0, -8, 24, 1, 24},
            new double[] {23, 1, -8, 24, 2, 24},
            new double[] {-8, 1, -8, -7, 2, 24},
            new double[] {-7, 1, 23, 23, 2, 24},
            new double[] {-7, 1, -8, 23, 2, -7});

    public static final MapCodec<MahjongSolitaireBlock> CODEC = simpleCodec(MahjongSolitaireBlock::new);

    public MahjongSolitaireBlock(Properties properties) {
        super(properties, MODEL_BOXES);
    }

    @Override
    protected MapCodec<MahjongSolitaireBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean isRotatable() {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new MahjongSolitaireBlockEntity(pos, state) : null;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof MahjongSolitaireBlockEntity table)) return 0;
        int total = table.slotCount();
        int remaining = table.remainingTiles();
        if (total == 0 || remaining == 0) return 0;
        return Math.max(1, Math.min(15, (15 * remaining) / total));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return dispatchUse(state, level, pos, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return dispatchUse(state, level, pos, player, InteractionHand.MAIN_HAND);
    }

    private InteractionResult dispatchUse(BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof MahjongSolitaireBlockEntity table)) {
            return InteractionResult.PASS;
        }
        boolean shift = player.isShiftKeyDown();
        boolean emptyHand = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
        if (shift && emptyHand && isMaster(state) && table.isBoardEmpty()) {
            table.resetBoard();
            return InteractionResult.CONSUME;
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (!isMaster(state)) {
            return List.of();
        }
        ItemStack stack = new ItemStack(this);
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof MahjongSolitaireBlockEntity && be.getLevel() instanceof ServerLevel sl) {
            CompoundTag beTag = be.saveCustomOnly(sl.registryAccess());
            if (!beTag.isEmpty()) {
                stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(be.getType(), beTag));
            }
        }
        return List.of(stack);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block fromBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, fromBlock, orientation, movedByPiston);
        if (level.isClientSide()) return;
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof MahjongSolitaireBlockEntity table)) {
            return;
        }

        boolean nowPowered = anyPartPowered(level, master);
        if (nowPowered && !table.wasPowered()) {
            table.resetBoard();
        }
        table.setPowered(nowPowered);
    }

    private static boolean anyPartPowered(Level level, BlockPos master) {
        for (int dx = -CENTER; dx <= CENTER; dx++) {
            for (int dz = -CENTER; dz <= CENTER; dz++) {
                if (level.hasNeighborSignal(master.offset(dx, 0, dz))) {
                    return true;
                }
            }
        }
        return false;
    }
}
