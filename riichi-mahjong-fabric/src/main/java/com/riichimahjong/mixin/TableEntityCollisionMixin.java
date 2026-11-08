package com.riichimahjong.mixin;

import com.riichimahjong.fabric.TableCollision;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Match server player movement to the closed shulker shown to vanilla clients. */
@Mixin(EntityGetter.class)
interface TableEntityCollisionMixin {
    @Inject(method="getEntityCollisions",at=@At("RETURN"),cancellable=true)
    private void riichi$tableCollision(Entity entity,AABB query,CallbackInfoReturnable<List<VoxelShape>> cir){
        if((Object)this instanceof ServerLevel level)
            cir.setReturnValue(TableCollision.append(level,entity,query,cir.getReturnValue()));
    }
}
