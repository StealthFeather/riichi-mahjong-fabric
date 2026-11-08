package com.riichimahjong.neoforge;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.client.RiichiMahjongClient;
import com.riichimahjong.neoforge.caps.SolitaireFluidHandler;
import com.riichimahjong.neoforge.caps.SolitaireItemHandler;
import com.riichimahjong.neoforge.caps.YakuGenEnergyHandler;
import com.riichimahjong.registry.ModBlockEntities;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(RiichiMahjong.MOD_ID)
public final class RiichiMahjongNeoForge {
    public RiichiMahjongNeoForge(IEventBus modBus) {
        RiichiMahjong.init(modBus);

        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(
                    Capabilities.Item.BLOCK,
                    ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get(),
                    (be, side) -> new SolitaireItemHandler(be));
            event.registerBlockEntity(
                    Capabilities.Fluid.BLOCK,
                    ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get(),
                    (be, side) -> new SolitaireFluidHandler(be));
            event.registerBlockEntity(
                    Capabilities.Energy.BLOCK,
                    ModBlockEntities.YAKU_GENERATOR_BLOCK_ENTITY.get(),
                    (be, side) -> new YakuGenEnergyHandler(be));
        });

        modBus.addListener((InterModEnqueueEvent event) -> {
            for (String id : new String[]{"mahjong_table_new", "mahjong_solitaire"}) {
                String fullId = Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, id).toString();
                InterModComms.sendTo("carryon", "blacklistBlock", () -> fullId);
            }
        });

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            RiichiMahjongClient.initClient(modBus);
            modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                    RiichiMahjongClient.registerRenderers(event));
            modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) -> {
                event.register(com.riichimahjong.registry.ModMenus.MAHJONG_TABLE_MENU.get(),
                        com.riichimahjong.mahjongtable.client.MahjongTableScreen::new);
                event.register(com.riichimahjong.registry.ModMenus.MAHJONG_TABLE_SETTINGS_MENU.get(),
                        com.riichimahjong.mahjongtable.client.MahjongTableSettingsScreen::new);
                event.register(com.riichimahjong.registry.ModMenus.YAKU_GENERATOR_MENU.get(),
                        com.riichimahjong.yakugenerator.client.YakuGeneratorScreen::new);
            });

            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) -> {
                if (event.getCamera().entity() instanceof net.minecraft.world.entity.LivingEntity living) {
                    event.setFOV((float) com.riichimahjong.fovnormalizer.client.NormalizeFovBlend
                            .tickAndCompute(living, event.getFOV()));
                }
            });

            com.riichimahjong.neoforge.caps.LiquidExperienceClientExtensions.register(modBus);
            com.riichimahjong.neoforge.caps.NormalizeFovEffectClientExtensions.register(modBus);
        }
    }
}
