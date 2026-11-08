package com.riichimahjong.mixin;

import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Shulker.class)
public interface DisplayShulkerAccessor {
    @Accessor("DATA_PEEK_ID")
    static EntityDataAccessor<Byte> riichi$peek(){throw new AssertionError();}
    @Accessor("DATA_ATTACH_FACE_ID")
    static EntityDataAccessor<Direction> riichi$face(){throw new AssertionError();}
}
