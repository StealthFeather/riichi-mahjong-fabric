package com.riichimahjong.fabric;

import com.riichimahjong.analysis.AnalysisMode;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.registry.ModItems;
import com.riichimahjong.replay.ReplayMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class HistoryMenus {
    private HistoryMenus() {}

    static void open(ServerPlayer player, MahjongTableBlockEntity table) {
        if (!PortMenus.tableMenuValid(player, table)) {
            player.closeContainer();
            return;
        }
        PortMenus.open(player, "历史战绩", () -> PortMenus.tableMenuValid(player, table), menu -> {
            Runnable back = () -> open(player, table);
            menu.button(20, PortMenus.icon(Items.WRITTEN_BOOK, "我的牌谱 · 按日期查看"),
                    () -> ReplayMenus.calendar(player, back));
            menu.button(24, PortMenus.icon(Items.WRITABLE_BOOK, "我的统计分析"), () ->
                    AnalysisMenus.open(player, AnalysisMode.of(table.effectivePreset(), table.matchLength(),
                            table.exchangeThreeTiles(), table.jokersEnabled(), table.bloodBattle()), back));
            menu.button(40, PortMenus.icon(Items.BOOK, "领取空白牌谱"), () -> {
                var record = new ItemStack(ModItems.MAHJONG_TABLE_RECORD.get());
                if (!player.getInventory().add(record)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("背包已满，请先腾出一个空位。"), true);
                    return;
                }
                player.closeContainer();
            });
            menu.button(49, PortMenus.icon(Items.ARROW, "返回牌桌菜单"), () -> PortMenus.settings(player, table));
        });
    }
}
