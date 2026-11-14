package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableBlock;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** All nine table parts require a pickaxe in the main hand, including in creative mode. */
public final class TableMining {
    private static final long HINT_INTERVAL_TICKS = 20;
    private static final Map<ServerPlayer,Long> LAST_HINT = new WeakHashMap<>();

    private TableMining() {}

    static boolean matches(BlockState state, ItemStack mainHand) {
        return !(state.getBlock() instanceof MahjongTableBlock) || mainHand.is(ItemTags.PICKAXES);
    }

    public static boolean allow(ServerPlayer player, BlockPos pos, BlockState state) {
        if (matches(state,player.getMainHandItem())) return true;

        long now = player.level().getGameTime();
        Long last = LAST_HINT.get(player);
        if (last == null || now < last || now - last >= HINT_INTERVAL_TICKS) {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.literal("需要主手持镐才能拆除麻将桌")));
            LAST_HINT.put(player,now);
        }
        // Correct a vanilla client's predicted creative-mode removal as well.
        player.connection.send(new ClientboundBlockUpdatePacket(pos,state));
        return false;
    }

    public static void register() {
        AttackBlockCallback.EVENT.register((player,level,hand,pos,direction) ->
                player instanceof ServerPlayer serverPlayer
                        && !allow(serverPlayer,pos,level.getBlockState(pos))
                        ? InteractionResult.FAIL : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,entity) ->
                !(player instanceof ServerPlayer serverPlayer) || allow(serverPlayer,pos,state));
    }
}
