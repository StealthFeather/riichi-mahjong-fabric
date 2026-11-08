package com.riichimahjong.neoforge.caps;

import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class SolitaireItemHandler implements ResourceHandler<ItemResource> {
    private static final int SLOT_LIMIT = 64;

    private final MahjongSolitaireBlockEntity be;

    public SolitaireItemHandler(MahjongSolitaireBlockEntity be) {
        this.be = be;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return 0L;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return SLOT_LIMIT;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return MahjongSolitaireBlockEntity.isHintItem(resource.toStack());
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) return 0;
        ItemStack stack = resource.toStack(amount);
        if (!MahjongSolitaireBlockEntity.isHintItem(stack)) return 0;
        return Math.max(0, be.tryInsertHintItem(stack, false));
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
