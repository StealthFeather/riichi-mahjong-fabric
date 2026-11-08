package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.fovnormalizer.NormalizeFovMobEffect;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, RiichiMahjong.MOD_ID);

    public static final Supplier<MobEffect> NORMALIZE_FOV_EFFECT =
            MOB_EFFECTS.register("normalize_fov", NormalizeFovMobEffect::new);

    private ModMobEffects() {}
}
