// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.fovnormalizer;

import com.riichimahjong.registry.ModBlockEntities;
import com.riichimahjong.registry.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FovNormalizerBlockEntity extends BlockEntity {
    private static final int EFFECT_DURATION_TICKS = 20;

    public FovNormalizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FOV_NORMALIZER_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FovNormalizerBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        int radius = sl.getBestNeighborSignal(pos);
        if (radius <= 0) {
            return;
        }
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double r2 = (double) radius * radius;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(cx, cy, cz) <= r2) {
                p.addEffect(new MobEffectInstance(
                        sl.registryAccess()
                                .lookupOrThrow(net.minecraft.core.registries.Registries.MOB_EFFECT)
                                .wrapAsHolder(ModMobEffects.NORMALIZE_FOV_EFFECT.get()),
                        EFFECT_DURATION_TICKS,
                        0,
                        true,
                        false,
                        false));
            }
        }
    }
}
