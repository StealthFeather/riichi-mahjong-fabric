package com.riichimahjong;

import com.riichimahjong.cuterenderer.net.CuteNetwork;
import com.riichimahjong.mahjongaltar.MahjongAltarBlockEntity;
import com.riichimahjong.mahjongcore.MahjongTileFuels;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.mahjongtable.MahjongTableBlock;
import com.riichimahjong.registry.ModBlockEntities;
import com.riichimahjong.registry.ModBlocks;
import com.riichimahjong.registry.ModCreativeTabs;
import com.riichimahjong.registry.ModEntities;
import com.riichimahjong.registry.ModFluids;
import com.riichimahjong.registry.ModItems;
import com.riichimahjong.registry.ModMenus;
import com.riichimahjong.registry.ModMobEffects;
import com.riichimahjong.registry.ModRecipeSerializers;
import com.riichimahjong.registry.ModRecipeTypes;
import com.riichimahjong.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;

public final class RiichiMahjong {
    public static final String MOD_ID = "riichi_mahjong";

    private RiichiMahjong() {}

    public static void init(IEventBus modBus) {
        MahjongTileItems.register(ModBlocks.BLOCKS, ModItems.ITEMS);

        ModFluids.FLUID_TYPES.register(modBus);
        ModFluids.FLUIDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenus.MENU_TYPES.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);
        ModMobEffects.MOB_EFFECTS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modBus);
        ModRecipeTypes.RECIPE_TYPES.register(modBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBus);

        CuteNetwork.register(modBus);
        MahjongTableBlock.registerEvents();
        MahjongAltarBlockEntity.registerEvents();
        MahjongTileFuels.register();
    }
}
