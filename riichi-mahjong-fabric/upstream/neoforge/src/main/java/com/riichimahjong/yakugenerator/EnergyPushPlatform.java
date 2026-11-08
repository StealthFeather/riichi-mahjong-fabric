package com.riichimahjong.yakugenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public final class EnergyPushPlatform {
    private EnergyPushPlatform() {}

    public static int tickPush(YakuGeneratorBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel serverLevel)) return 0;
        BlockCapabilityCache<EnergyHandler, Direction>[] caches = caches(be, serverLevel);
        int totalDrained = 0;
        for (BlockCapabilityCache<EnergyHandler, Direction> cache : caches) {
            int available = be.getEnergyStored();
            if (available <= 0) break;
            EnergyHandler receiver = cache.getCapability();
            if (receiver == null) continue;
            int accepted;
            try (Transaction transaction = Transaction.openRoot()) {
                accepted = receiver.insert(available, transaction);
                if (accepted > 0) {
                    transaction.commit();
                }
            }
            if (accepted > 0) {
                be.extractEnergy(accepted, false);
                totalDrained += accepted;
            }
        }
        return totalDrained;
    }

    @SuppressWarnings("unchecked")
    private static BlockCapabilityCache<EnergyHandler, Direction>[] caches(
            YakuGeneratorBlockEntity be, ServerLevel level) {
        Object existing = be.loaderEnergyPushState;
        if (existing != null) {
            return (BlockCapabilityCache<EnergyHandler, Direction>[]) existing;
        }
        BlockCapabilityCache<EnergyHandler, Direction>[] fresh =
                (BlockCapabilityCache<EnergyHandler, Direction>[]) new BlockCapabilityCache<?, ?>[6];
        BlockPos source = be.getBlockPos();
        for (Direction dir : Direction.values()) {
            fresh[dir.ordinal()] = BlockCapabilityCache.create(
                    Capabilities.Energy.BLOCK,
                    level,
                    source.relative(dir),
                    dir.getOpposite());
        }
        be.loaderEnergyPushState = fresh;
        return fresh;
    }
}
