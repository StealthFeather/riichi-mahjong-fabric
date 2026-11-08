package com.riichimahjong.mahjongaltar;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record AltarRecipeInput(List<ItemStack> items) implements RecipeInput {
    @Override
    public ItemStack getItem(int slot) {
        return slot < 0 || slot >= items.size() ? ItemStack.EMPTY : items.get(slot);
    }

    @Override
    public int size() {
        return items.size();
    }
}
