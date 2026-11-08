// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.fovnormalizer;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class NormalizeFovMobEffect extends MobEffect implements eu.pb4.polymer.core.api.other.PolymerMobEffect {
    @Override public MobEffect getPolymerReplacement(MobEffect effect,net.fabricmc.fabric.api.networking.v1.context.PacketContext context){return null;}
    public NormalizeFovMobEffect() {
        super(MobEffectCategory.NEUTRAL, 0x7A7A7A);
    }
}
