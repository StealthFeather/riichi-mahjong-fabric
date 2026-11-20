package com.riichimahjong.fabric;

import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.registry.ModBlocks;
import com.themahjong.TheMahjongTile;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** Seats are shown as compass points, with each AI setting immediately outside its seat. */
final class SeatMenus {
    private SeatMenus() {}
    private static final int[] SEATS = {23, 31, 21, 13};
    private static final int[] DIFFICULTIES = {24, 40, 20, 4};
    private static final int[] OFFLINE_ACTIONS = {46, 47, 51, 52};

    static void open(ServerPlayer player, MahjongTableBlockEntity table) {
        if (table.replay() != null) return;
        PortMenus.openTablePage(player, table, "立直麻将 · 牌桌座次", menu -> {
            var driver = table.driver();
            long revision = table.matchRevision();
            menu.button(22, PortMenus.icon(ModBlocks.MAHJONG_TABLE_ITEM.get(), "麻将桌"), () -> {});
            var seats = table.seats();
            String lock = difficultyLock(player, table);
            for (int i = 0; i < Math.min(SEATS.length, seats.size()); i++) {
                final int seat = i;
                var info = seats.get(seat);
                String wind = TableSeating.wind(seat) + "位";
                UUID occupant = info.occupant().orElse(null);
                UUID assignee = occupant != null ? occupant : table.returningPlayerAtSeat(seat).orElse(null);
                boolean own = player.getUUID().equals(assignee);
                ItemStack icon;
                if (assignee == null) {
                    icon = PortMenus.tileOption(TheMahjongTile.Suit.WIND, seat + 1,
                            wind + (info.enabled() ? " · 空位" : " · 未启用"),
                            info.enabled() ? "点击入座" : "当前人数下该席位关闭");
                } else {
                    var online = player.level().getServer().getPlayerList().getPlayer(assignee);
                    String name = online == null ? "离线玩家" : online.getName().getString();
                    String action = occupant == null ? (own ? " · 点击接回" : " · 保留座位")
                            : own ? " · 点击离座"
                            : online != null && PortMenus.host(player, table) ? " · 点击移交桌主" : "";
                    icon = PortMenus.icon(Items.PLAYER_HEAD, wind + " · " + name
                            + (table.owner().filter(assignee::equals).isPresent() ? " · 桌主" : "") + action);
                    icon.set(DataComponents.PROFILE, online == null ? ResolvableProfile.createUnresolved(assignee)
                            : ResolvableProfile.createResolved(online.getGameProfile()));
                }
                menu.button(SEATS[seat], icon, () -> {
                    if (!info.enabled()) return;
                    if (occupant == null) {
                        if (assignee == null || own) TableSeating.join(player, table, seat);
                    } else if (own) {
                        TableSeating.leave(player, table);
                    } else if (PortMenus.host(player, table)
                            && player.level().getServer().getPlayerList().getPlayer(occupant) != null) {
                        PortMenus.confirmHostTransfer(player, table, seat, occupant);
                        return;
                    }
                    open(player, table);
                });
                String difficulty = table.botDifficultyOverride(seat).map(BotDifficulty::label)
                        .orElse("跟随默认（" + table.defaultBotDifficulty().label() + "）");
                String strategy = table.usesMortalBots() ? "Mortal · 备用电将：" + difficulty : difficulty;
                menu.button(DIFFICULTIES[seat], PortMenus.icon(Items.REPEATER, wind + " AI：" + strategy + lock), () -> {
                    if (info.enabled() && difficultyLock(player, table).isEmpty()) table.cycleSeatBotDifficulty(seat);
                    open(player, table);
                });
            }
            if (PortMenus.host(player, table)) for (var candidate : table.offlineSeatCandidates()) {
                int seat = candidate.seat();
                if (seat >= OFFLINE_ACTIONS.length) continue;
                menu.button(OFFLINE_ACTIONS[seat], PortMenus.icon(Items.LEVER, TableSeating.wind(seat) + "位 · 用 AI 接替离线玩家"), () -> {
                    if (table.replaceOfflineWithAI(player, seat, candidate.occupant(), driver, revision))
                        player.sendSystemMessage(Component.literal("已用 AI 接替该座位。"), true);
                    open(player, table);
                });
            }
            PortMenus.backToTable(menu, player, table);
        });
    }

    private static String difficultyLock(ServerPlayer player, MahjongTableBlockEntity table) {
        if (table.hasActiveMatch()) return " · 对局中锁定";
        if (table.effectivePreset().playerCount() != 4) return " · 三麻使用本地 BOT";
        if (table.preset().family() == RuleSetPreset.Family.SICHUAN || table.jokersEnabled() || table.bloodBattle())
            return " · 此规则使用本地 BOT";
        if (!PortMenus.host(player, table) || player.isSpectator()) return " · 仅桌主可调";
        return "";
    }
}
