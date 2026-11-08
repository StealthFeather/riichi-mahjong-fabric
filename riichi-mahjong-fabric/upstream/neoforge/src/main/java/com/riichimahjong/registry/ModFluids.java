package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, RiichiMahjong.MOD_ID);

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, RiichiMahjong.MOD_ID);

    public static final Supplier<FluidType> LIQUID_EXPERIENCE_TYPE = FLUID_TYPES.register(
            "liquid_experience",
            () -> new FluidType(FluidType.Properties.create()
                    .density(800)
                    .viscosity(1500)
                    .lightLevel(7)));

    public static final Supplier<BaseFlowingFluid.Source> LIQUID_EXPERIENCE = FLUIDS.register(
            "liquid_experience",
            () -> new BaseFlowingFluid.Source(properties()));

    public static final Supplier<BaseFlowingFluid.Flowing> LIQUID_EXPERIENCE_FLOWING = FLUIDS.register(
            "liquid_experience_flowing",
            () -> new BaseFlowingFluid.Flowing(properties()));

    private static BaseFlowingFluid.Properties propertiesCache;

    private static BaseFlowingFluid.Properties properties() {
        if (propertiesCache == null) {
            propertiesCache = new BaseFlowingFluid.Properties(
                    LIQUID_EXPERIENCE_TYPE,
                    LIQUID_EXPERIENCE,
                    LIQUID_EXPERIENCE_FLOWING)
                    .bucket(ModItems.LIQUID_EXPERIENCE_BUCKET)
                    .block(ModBlocks.LIQUID_EXPERIENCE_BLOCK);
        }
        return propertiesCache;
    }

    private ModFluids() {}
}
