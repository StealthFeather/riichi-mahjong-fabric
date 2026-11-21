package com.riichimahjong.mixin;

import com.riichimahjong.mahjongtable.BloodDrawHealth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class BloodDrawHealingMixin {
    @Inject(method = "setHealth(F)V", at = @At("HEAD"), cancellable = true)
    private void riichi$blockBloodDrawHealing(float updatedHealth, CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayer player) {
            float currentHealth = player.getHealth();
            // Vanilla also uses the setter to revive a dying entity with a totem.
            if (currentHealth > 0 && updatedHealth > currentHealth && BloodDrawHealth.blocksHealing(player)) {
                callback.cancel();
            }
        }
    }
}
