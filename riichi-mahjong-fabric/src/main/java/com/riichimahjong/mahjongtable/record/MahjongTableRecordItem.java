// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongtable.record;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class MahjongTableRecordItem extends com.riichimahjong.fabric.PortItem {

    public MahjongTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isPredefinedFixture() || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        return isPredefinedFixture() ? super.getName(stack) : Component.literal("牌谱");
    }

    @Override
    public net.minecraft.world.InteractionResult use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (isPredefinedFixture()) return net.minecraft.world.InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer && hand == net.minecraft.world.InteractionHand.MAIN_HAND)
            com.riichimahjong.replay.ReplayMenus.overview(serverPlayer);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    public boolean applyToTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        com.riichimahjong.replay.ReplayMenus.overview(player);
        return true;
    }

    public boolean recordFromTable(ServerPlayer player, MahjongTableBlockEntity table, ItemStack stack) {
        com.riichimahjong.replay.ReplayMenus.overview(player);
        return true;
    }

    protected boolean isPredefinedFixture() {
        return false;
    }

    public boolean isPredefinedFixtureForRouting() {
        return isPredefinedFixture();
    }
}
