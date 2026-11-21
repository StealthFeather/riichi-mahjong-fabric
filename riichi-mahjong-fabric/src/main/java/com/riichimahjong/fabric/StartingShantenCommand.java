package com.riichimahjong.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;

final class StartingShantenCommand {
    private static final Set<MahjongTableBlockEntity> tables = new HashSet<>();

    private StartingShantenCommand() {}

    static void trackLoadedTables() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof MahjongTableBlockEntity table) tables.add(table);
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof MahjongTableBlockEntity table) tables.remove(table);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> tables.clear());
    }

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("shanten")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(playerArgument(1));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> playerArgument(int number) {
        var player = Commands.argument("player" + number, EntityArgument.player());
        var shanten = Commands.argument("shanten" + number, IntegerArgumentType.integer(0, 6))
                .executes(context -> schedule(context, number));
        if (number < 4) shanten.then(playerArgument(number + 1));
        if (number == 1) player.then(Commands.literal("clear").executes(StartingShantenCommand::clear));
        return player.then(shanten);
    }

    private static int schedule(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        var targets = new HashMap<UUID, Integer>();
        var summary = new StringJoiner("、");
        for (int i = 1; i <= count; i++) {
            var player = EntityArgument.getPlayer(context, "player" + i);
            int shanten = IntegerArgumentType.getInteger(context, "shanten" + i);
            if (targets.put(player.getUUID(), shanten) != null) throw error("同一条指令中，每位玩家只能指定一次。");
            summary.add(player.getGameProfile().name() + " " + shanten + " 向听");
        }
        var table = findTable(context.getSource(), targets.keySet());
        if (!table.setNextShanten(targets)) throw error("牌桌或座位状态已变化，请重新指定。");
        context.getSource().sendSuccess(() -> Component.literal("已指定下一局的 13 张起手牌：" + summary + "。"), false);
        return count;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player1");
        var table = findTable(context.getSource(), Set.of(player.getUUID()));
        if (!table.clearNextShanten(player.getUUID())) throw error("该玩家没有待生效的向听设置。");
        context.getSource().sendSuccess(() -> Component.literal("已撤销 " + player.getGameProfile().name() + " 的下一局向听设置。"), false);
        return 1;
    }

    private static MahjongTableBlockEntity findTable(CommandSourceStack source, Set<UUID> players) throws CommandSyntaxException {
        var matches = tables.stream().filter(table -> !table.isRemoved() && table.getLevel().getServer() == source.getServer()
                        && table.canScheduleNextShanten() && players.stream().allMatch(id -> table.seatOfPlayer(id).isPresent()))
                .limit(2).toList();
        if (matches.isEmpty()) throw error("玩家须在同一张进行中的标准日麻四人牌桌入座，并关闭换三张、癞子、血战和抽血模式。");
        if (matches.size() > 1) throw error("指定玩家同时在多张牌桌入座，无法确定要设置的牌桌。");
        return matches.getFirst();
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
