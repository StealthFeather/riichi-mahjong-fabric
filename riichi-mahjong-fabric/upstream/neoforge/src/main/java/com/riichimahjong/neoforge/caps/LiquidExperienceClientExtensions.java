package com.riichimahjong.neoforge.caps;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.registry.ModFluids;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

public final class LiquidExperienceClientExtensions {

    private static final Material STILL = new Material(
            Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "block/liquid_experience_still"));
    private static final Material FLOW = new Material(
            Identifier.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "block/liquid_experience_flow"));
    private static final int TINT = 0xFFC8FF40;

    private LiquidExperienceClientExtensions() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterFluidModelsEvent event) -> event.register(
                new FluidModel.Unbaked(STILL, FLOW, null, FluidTintSources.constant(TINT)),
                ModFluids.LIQUID_EXPERIENCE,
                ModFluids.LIQUID_EXPERIENCE_FLOWING));
    }
}
