package com.riichimahjong.cuterenderer.net;

import com.mojang.logging.LogUtils;
import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.cuterenderer.CuteClickHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

public final class CuteNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CuteNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar(RiichiMahjong.MOD_ID);
            registrar.playToServer(
                    C2SCuteClickPacket.TYPE,
                    C2SCuteClickPacket.STREAM_CODEC,
                    (msg, context) -> context.enqueueWork(() -> {
                        if (!(context.player() instanceof ServerPlayer sender)) return;
                        ServerLevel level = sender.level();
                        if (!level.dimension().equals(msg.dim())) return;
                        BlockEntity be = level.getBlockEntity(msg.masterPos());
                        if (be instanceof CuteClickHandler handler) {
                            handler.onCuteClick(sender, msg.key());
                        } else {
                            LOGGER.debug("Cute click target at {} is not a CuteClickHandler", msg.masterPos());
                        }
                    }));
        });
    }
}
