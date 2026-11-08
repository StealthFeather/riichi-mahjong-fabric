package com.riichimahjong.neoforge.caps;

import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

public final class YakuGenEnergyHandler implements EnergyHandler {
    private final YakuGeneratorBlockEntity be;

    public YakuGenEnergyHandler(YakuGeneratorBlockEntity be) {
        this.be = be;
    }

    @Override
    public long getAmountAsLong() {
        return be.getEnergyStored();
    }

    @Override
    public long getCapacityAsLong() {
        return be.getMaxEnergyStored();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return be.extractEnergy(amount, false);
    }
}
