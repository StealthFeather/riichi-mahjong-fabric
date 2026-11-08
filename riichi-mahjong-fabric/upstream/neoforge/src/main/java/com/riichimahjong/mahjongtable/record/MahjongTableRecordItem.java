package com.riichimahjong.mahjongtable.record;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity.StartMatchResult;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class MahjongTableRecordItem extends Item {
    public static final String TABLE_STATE_TAG = "MahjongTableState";
    private static final String EMPTY_NAME_TRANSLATION_KEY = "item.riichi_mahjong.mahjong_table_record_empty";

    public MahjongTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPredefinedFixture() || hasRecordedTableState(stack) || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (!isPredefinedFixture() && !hasRecordedTableState(stack)) {
            return Component.translatable(EMPTY_NAME_TRANSLATION_KEY);
        }
        return super.getName(stack);
    }

    public static boolean hasRecordedTableState(ItemStack stack) {
        return recordedTableStateOrNull(stack) != null;
    }

    public static void writeRecordedTableState(ItemStack stack, CompoundTag tableState) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag ->
                tag.put(TABLE_STATE_TAG, tableState.copy()));
    }

    public static void clearRecordedTableState(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TABLE_STATE_TAG)) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(TABLE_STATE_TAG));
    }

    public static CompoundTag recordedTableStateOrNull(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TABLE_STATE_TAG)) return null;
        CompoundTag full = data.copyTag();
        return full.getCompoundOrEmpty(TABLE_STATE_TAG).copy();
    }

    public boolean applyToTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        CompoundTag snapshot = recordedTableStateOrNull(stack);
        if (snapshot == null) return false;
        StartMatchResult result = table.tryApplyRecordSnapshot(snapshot, player.getUUID());
        if (result == StartMatchResult.NOT_IDLE) {
            player.sendSystemMessage(
                    Component.literal("Cannot load record: end the current match first."), true);
            return false;
        }
        return true;
    }

    public boolean recordFromTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        if (hasRecordedTableState(stack)) {
            player.sendSystemMessage(
                    Component.literal("Record is already filled — drop the snapshot first."), true);
            return false;
        }
        writeRecordedTableState(stack, table.exportRecordSnapshot(player.level().registryAccess()));
        return true;
    }

    protected boolean isPredefinedFixture() {
        return false;
    }

    public boolean isPredefinedFixtureForRouting() {
        return isPredefinedFixture();
    }
}
