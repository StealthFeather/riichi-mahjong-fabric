package com.riichimahjong.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ArmorStand.class)
public interface DisplayArmorStandAccessor {
    @Accessor("DATA_CLIENT_FLAGS")
    static EntityDataAccessor<Byte> riichi$flags(){throw new AssertionError();}
}
