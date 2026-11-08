// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongtable;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.mahjongtable.record.MahjongTableRecordItem;
import com.riichimahjong.fabric.PortEvents;


import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

@SuppressWarnings("deprecation")
public class MahjongTableBlock extends BaseMultipartBlock {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final TagKey<Item> WRENCH_TAG_FORGE =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("forge", "tools/wrench"));

    private static final TagKey<Item> WRENCH_TAG_COMMON =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/wrench"));

    private static final List<double[]> MODEL_BOXES = List.of(
            new double[] {0, 0, 0, 16, 8, 16},
            new double[] {-8, 8, -8, 24, 9, 24},
            new double[] {23, 9, -8, 24, 10, 24},
            new double[] {-8, 9, -8, -7, 10, 24},
            new double[] {-7, 9, 23, 23, 10, 24},
            new double[] {-7, 9, -8, 23, 10, -7},
            new double[] {6, 8.5, 6, 10, 9.5, 10});


    public MahjongTableBlock(Properties properties) {
        // The model spans -0.5 .. 1.5 around the centre block on both axes.
        super(properties, MODEL_BOXES);
    }



    @Override
    protected boolean isRotatable() {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new MahjongTableBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !isMaster(state)) {
            return null;
        }
        return MahjongTableBlockEntity.serverTicker();
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
        return dispatchUse(state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit) {
        return dispatchUse(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
    }

    public InteractionResult dispatchUse(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof MahjongTableBlockEntity table)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown() && player instanceof ServerPlayer sp
                && table.seatOfPlayer(sp.getUUID()).isPresent()
                && table.hasActiveMatch()) {
            com.riichimahjong.fabric.TableSeating.leave(sp, table);
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof MahjongTableRecordItem record && player instanceof ServerPlayer sp) {
            if (table.state() != MahjongTableBlockEntity.State.IDLE) {
                sp.sendSystemMessage(
                        Component.literal("End the current match before using a table record."), true);
                return InteractionResult.CONSUME;
            }
            boolean filled = record.isPredefinedFixtureForRouting()
                    || MahjongTableRecordItem.hasRecordedTableState(held);
            if (filled) {
                record.applyToTable(sp, table, held);
            } else {
                record.recordFromTable(sp, table, held);
            }
            return InteractionResult.CONSUME;
        }

        if (table.state() == MahjongTableBlockEntity.State.GAME) {
            if (holdsWrench(player) && isMaster(state)) {
                table.endGame();
                return InteractionResult.CONSUME;
            }

            if (table.isInResultPhase()) {
                if (player instanceof ServerPlayer sp && !holdsWrench(player)) {
                    if (isMaster(state)) {
                        table.advanceRoundAfterResult();
                    } else {
                        sp.sendSystemMessage(
                                Component.literal("Click the table centre to start the next round."),
                                true);
                    }
                }
                return InteractionResult.CONSUME;
            }

            if (table.isDriverTerminal() && isMaster(state) && player.isShiftKeyDown()
                    && !holdsWrench(player)) {
                table.endGame();
                MahjongTableBlockEntity.StartMatchResult result =
                        table.tryStartMatchWithAutoSeating(new Random().nextLong());
                Component warning = warningFor(result, table);
                if (warning != null) {
                    player.sendSystemMessage(warning);
                }
                return InteractionResult.CONSUME;
            }

            if (!holdsWrench(player) && player instanceof ServerPlayer sp) {
                table.onTableRightClick(sp);
            }
            return InteractionResult.CONSUME;
        }

        int partX = state.getValue(PART_X);
        int partZ = state.getValue(PART_Z);
        boolean center = isMaster(state);
        boolean wrench = holdsWrench(player);
        boolean shift = player.isShiftKeyDown();

        if (wrench) {
            if (center) {
                if (player instanceof ServerPlayer serverPlayer) {
                    com.riichimahjong.fabric.PortMenus.settings(serverPlayer,table);
                }
                return InteractionResult.CONSUME;
            }
            OptionalInt seat = seatForCell(partX, partZ, state.getValue(FACING));
            if (seat.isPresent()) {
                table.toggleSeatEnabled(seat.getAsInt());
            }
            return InteractionResult.CONSUME;
        }

        if (shift && center) {
                MahjongTableBlockEntity.StartMatchResult result =
                        table.tryStartMatchWithAutoSeating(new Random().nextLong());
                Component warning = warningFor(result, table);
                if (warning != null) {
                    player.sendSystemMessage(warning);
                }
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer sp) {
            if(center){
                com.riichimahjong.fabric.TableSeating.joinNearest(sp,table);
            }else{
                int seat=seatForCell(partX,partZ,state.getValue(FACING)).orElseGet(()->com.riichimahjong.fabric.TableSeating.sideOfPlayer(sp,table));
                com.riichimahjong.fabric.TableSeating.join(sp,table,seat);
            }
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
        if (be instanceof MahjongTableBlockEntity && be.getLevel() instanceof ServerLevel sl) {
            CompoundTag beTag = be.saveCustomOnly(sl.registryAccess());
            if (!beTag.isEmpty()) {
                stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(be.getType(), beTag));
            }
        }
        return List.of(stack);
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block fromBlock,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, fromBlock, orientation, movedByPiston);
        if (level.isClientSide()) {
            return;
        }
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof MahjongTableBlockEntity table)) {
            return;
        }
        table.onRedstone(anyPartPowered(level, master));
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

    public static boolean holdsWrench(Player player) {
        return isWrench(player.getMainHandItem()) || isWrench(player.getOffhandItem());
    }

    private static boolean isWrench(ItemStack stack) {
        return stack.is(com.riichimahjong.registry.ModItems.MAHJONG_WRENCH.get()) || stack.is(WRENCH_TAG_FORGE) || stack.is(WRENCH_TAG_COMMON);
    }

    @Nullable
    private static Component warningFor(
            MahjongTableBlockEntity.StartMatchResult result,
            MahjongTableBlockEntity table) {
        return switch (result) {
            case STARTED, NOT_IDLE -> null;
            case SEATS_MISMATCH_PRESET -> Component.translatable(
                    "riichi_mahjong.warn.table.seats_mismatch_preset",
                    Component.translatable(table.effectivePreset().langKey()));
        };
    }

    public static OptionalInt seatForCell(int partX, int partZ, Direction facing) {
        Direction cellDir = directionForCell(partX, partZ);
        if (cellDir == null) return OptionalInt.empty();
        return OptionalInt.of(seatForDirection(cellDir, facing));
    }

    public static int seatForDirection(Direction cellDir, Direction facing) {
        return Math.floorMod(ccwIndex(cellDir) - ccwIndex(facing), 4);
    }

    @Nullable
    public static Direction directionForCell(int partX, int partZ) {
        if (partX == CENTER && partZ == 0) return Direction.NORTH;
        if (partX == GRID_SIZE - 1 && partZ == CENTER) return Direction.EAST;
        if (partX == CENTER && partZ == GRID_SIZE - 1) return Direction.SOUTH;
        if (partX == 0 && partZ == CENTER) return Direction.WEST;
        return null;
    }

    private static int ccwIndex(Direction d) {
        return switch (d) {
            case NORTH -> 0;
            case WEST -> 1;
            case SOUTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    public static void registerEvents() {
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!(player instanceof ServerPlayer)||player.isSpectator())return InteractionResult.PASS;
            var state=level.getBlockState(hit.getBlockPos());if(!(state.getBlock() instanceof MahjongTableBlock block))return InteractionResult.PASS;
            return block.dispatchUse(state,level,hit.getBlockPos(),player,hand,hit);
        });
    }
}
