package com.riichimahjong.neoforge.caps;

import com.riichimahjong.registry.ModMobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class NormalizeFovEffectClientExtensions implements IClientMobEffectExtensions {
    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterClientExtensionsEvent event) ->
                event.registerMobEffect(
                        new NormalizeFovEffectClientExtensions(),
                        ModMobEffects.NORMALIZE_FOV_EFFECT.get()));
    }

    @Override
    public boolean isVisibleInInventory(MobEffectInstance instance) {
        return false;
    }

    @Override
    public boolean isVisibleInGui(MobEffectInstance instance) {
        return false;
    }
}
