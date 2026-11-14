package com.riichimahjong.mixin;

import com.riichimahjong.fabric.TableMining;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also guard programmatic mining that bypasses the initial attack callback. */
@Mixin(ServerPlayerGameMode.class)
public abstract class TableMiningGameModeMixin {
    @Shadow @Final protected ServerPlayer player;

    @Inject(method="destroyBlock(Lnet/minecraft/core/BlockPos;)Z",at=@At("HEAD"),cancellable=true)
    private void riichi$requireTablePickaxe(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!TableMining.allow(player,pos,player.level().getBlockState(pos))) cir.setReturnValue(false);
    }
}
