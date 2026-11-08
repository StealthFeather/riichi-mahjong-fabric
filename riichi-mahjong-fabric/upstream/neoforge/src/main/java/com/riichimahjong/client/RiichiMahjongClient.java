package com.riichimahjong.client;

import com.riichimahjong.common.client.HoverHintOverlay;
import com.riichimahjong.cuterenderer.CuteClickInputHandler;
import com.riichimahjong.cuterenderer.editor.CuteEditorKeyHandler;
import com.riichimahjong.mahjongaltar.client.MahjongAltarBlockEntityRenderer;
import com.riichimahjong.mahjongsolitaire.client.MahjongSolitaireHoverHintClient;
import com.riichimahjong.mahjongsolitaire.client.MahjongSolitaireRenderer;
import com.riichimahjong.mahjongtable.client.MahjongTableHoverHintClient;
import com.riichimahjong.mahjongtable.client.MahjongTableRenderer;
import com.riichimahjong.registry.ModBlockEntities;
import com.riichimahjong.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class RiichiMahjongClient {

    private RiichiMahjongClient() {}

    public static void initClient(IEventBus modBus) {
        CuteClickInputHandler.register();
        CuteEditorKeyHandler.register(modBus);
        HoverHintOverlay.registerTickHandler();
        MahjongTableHoverHintClient.register();
        MahjongSolitaireHoverHintClient.register();
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.MAHJONG_TABLE_BLOCK_ENTITY.get(),
                MahjongTableRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get(),
                MahjongSolitaireRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.MAHJONG_ALTAR_BLOCK_ENTITY.get(),
                MahjongAltarBlockEntityRenderer::new);
        event.registerEntityRenderer(
                ModEntities.MAHJONG_RIICHI_STICK_ENTITY.get(),
                ThrownItemRenderer::new);
    }
}
