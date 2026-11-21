package com.themahjong.compat;

import java.util.List;
import java.util.function.ObjIntConsumer;

/** Collect gross settlement payments during one game advance. Riichi sticks are exempt. */
public final class PointPayments {
    private static final ScopedValue<int[]> TOTALS = ScopedValue.newInstance();

    private PointPayments() {}

    /** Deliver the debits after the rule engine finishes, since losing health can change seat controllers. */
    public static void collect(Runnable advance, ObjIntConsumer<Integer> onPayment) {
        int[] totals = new int[4];
        try {
            ScopedValue.where(TOTALS, totals).run(advance);
        } finally {
            for (int seat = 0; seat < totals.length; seat++) {
                if (totals[seat] > 0) onPayment.accept(seat, totals[seat]);
            }
        }
    }

    public static void paid(int seat, int amount) {
        if (amount > 0 && TOTALS.isBound()) TOTALS.get()[seat] += amount;
    }

    public static void fromDeltas(List<Integer> deltas) {
        if (!TOTALS.isBound()) return;
        for (int seat = 0; seat < deltas.size(); seat++) paid(seat, -deltas.get(seat));
    }
}
