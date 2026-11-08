package com.riichimahjong.neoforge.caps;

import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import com.riichimahjong.registry.ModFluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class SolitaireFluidHandler implements ResourceHandler<FluidResource> {
    private final MahjongSolitaireBlockEntity be;

    public SolitaireFluidHandler(MahjongSolitaireBlockEntity be) {
        this.be = be;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return be.getLiquidXpStored() > 0
                ? FluidResource.of(ModFluids.LIQUID_EXPERIENCE.get())
                : FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return be.getLiquidXpStored();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return MahjongSolitaireBlockEntity.LIQUID_XP_CAPACITY_MB;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return resource.is(ModFluids.LIQUID_EXPERIENCE.get());
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || !isValid(index, resource)) return 0;
        return Math.max(0, be.extractLiquidXp(amount, false));
    }
}
