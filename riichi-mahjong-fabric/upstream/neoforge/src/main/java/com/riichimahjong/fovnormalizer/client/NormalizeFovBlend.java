package com.riichimahjong.fovnormalizer.client;

import com.riichimahjong.registry.ModMobEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

public final class NormalizeFovBlend {
    private static final int DEFAULT_FOV = 70;
    private static final float TRANSITION_FACTOR = 0.1f;

    private static float normalizeBlend = 0.0f;

    private NormalizeFovBlend() {}

    public static double tickAndCompute(LivingEntity entity, double vanillaFov) {
        Holder<MobEffect> holder = entity.level().registryAccess()
                .lookupOrThrow(Registries.MOB_EFFECT)
                .wrapAsHolder(ModMobEffects.NORMALIZE_FOV_EFFECT.get());
        boolean hasEffect = entity.hasEffect(holder);
        float targetBlend = hasEffect ? 1.0f : 0.0f;
        normalizeBlend += (targetBlend - normalizeBlend) * TRANSITION_FACTOR;
        normalizeBlend = Mth.clamp(normalizeBlend, 0.0f, 1.0f);
        if (normalizeBlend < 0.001f && !hasEffect) {
            normalizeBlend = 0.0f;
            return vanillaFov;
        }
        return vanillaFov + (DEFAULT_FOV - vanillaFov) * normalizeBlend;
    }
}
