package com.riichimahjong;

import net.fabricmc.api.ModInitializer;
import com.riichimahjong.registry.*;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.fabric.*;

public final class RiichiMahjong implements ModInitializer {
    public static final String MOD_ID="riichi_mahjong";
    @Override public void onInitialize(){
        MahjongTileItems.register(ModBlocks.BLOCKS,ModItems.ITEMS);
        ModFluids.FLUIDS.flush();ModBlocks.BLOCKS.flush();ModItems.ITEMS.flush();
        ModBlockEntities.BLOCK_ENTITY_TYPES.flush();ModSounds.SOUND_EVENTS.flush();
        eu.pb4.polymer.core.api.block.PolymerBlockUtils.registerBlockEntity(
            ModBlockEntities.MAHJONG_TABLE_BLOCK_ENTITY.get(),ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get(),ModBlockEntities.MAHJONG_ALTAR_BLOCK_ENTITY.get(),ModBlockEntities.YAKU_GENERATOR_BLOCK_ENTITY.get(),ModBlockEntities.FOV_NORMALIZER_BLOCK_ENTITY.get());
        ModMobEffects.MOB_EFFECTS.flush();ModEntities.ENTITY_TYPES.flush();
        ModRecipeSerializers.RECIPE_SERIALIZERS.flush();ModRecipeTypes.RECIPE_TYPES.flush();
        com.riichimahjong.mahjongtable.MahjongTableBlock.registerEvents();
        com.riichimahjong.mahjongaltar.MahjongAltarBlockEntity.registerEvents();
        PortViews.register();PortMenus.register();
        FabricAutomation.register();com.riichimahjong.yakugenerator.EnergyPushPlatform.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                server -> com.riichimahjong.ai.AiRuntime.shutdown());
        eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.addModAssets(MOD_ID);
    }
}
