package com.riichimahjong.clickers;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public class TsumoClickerItem extends BlockItem {
    public TsumoClickerItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("riichi_mahjong.tooltip.tsumo_clicker.line1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("riichi_mahjong.tooltip.tsumo_clicker.line2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
