package com.riichimahjong.cuterenderer;

import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.cuterenderer.net.C2SCuteClickPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class CuteClickInputHandler {
    private CuteClickInputHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CuteClickInputHandler::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockPos pos = event.getPos();
        BlockState clickedState = mc.level.getBlockState(pos);
        BlockPos lookupPos = clickedState.getBlock() instanceof BaseMultipartBlock
                ? BaseMultipartBlock.masterPos(pos, clickedState)
                : pos;
        CuteRenderer renderer = CuteRenderer.find(mc.level.dimension(), lookupPos);
        if (renderer == null) return;
        CuteNode hovered = renderer.hovered();
        if (hovered == null) return;
        Interactive interactive = hovered.interactive();
        if (interactive == null) return;

        ClientPacketDistributor.sendToServer(new C2SCuteClickPacket(
                mc.level.dimension(),
                renderer.key().masterPos(),
                interactive.key()));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
