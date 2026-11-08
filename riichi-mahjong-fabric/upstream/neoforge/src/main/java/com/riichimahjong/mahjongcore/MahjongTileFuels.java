package com.riichimahjong.mahjongcore;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;

public final class MahjongTileFuels {
    private MahjongTileFuels() {}

    private static final int COAL_BURN_TICKS = 1600;
    private static final int SOUZU_BASE_CODE = 18;
    private static final Map<Item, Integer> BURN_TICKS_BY_ITEM = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MahjongTileFuels::onFuelBurnTime);
    }

    private static void onFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        if (BURN_TICKS_BY_ITEM.isEmpty()) {
            populateBurnTimes();
        }
        Integer ticks = BURN_TICKS_BY_ITEM.get(event.getItemStack().getItem());
        if (ticks != null) {
            event.setBurnTime(ticks);
        }
    }

    private static void populateBurnTimes() {
        for (int rank = 1; rank <= 9; rank++) {
            int burnTicks = rank * COAL_BURN_TICKS;
            MahjongTileItems.itemSupplierForCode(SOUZU_BASE_CODE + rank - 1)
                    .ifPresent(supplier -> BURN_TICKS_BY_ITEM.put(supplier.get(), burnTicks));
        }
        MahjongTileItems.itemSupplierForCode(MahjongTileItems.CODE_SOU_5_AKA)
                .ifPresent(supplier -> BURN_TICKS_BY_ITEM.put(supplier.get(), 5 * COAL_BURN_TICKS));
    }
}
