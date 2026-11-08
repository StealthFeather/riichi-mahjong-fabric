package com.themahjong.yaku;

import com.themahjong.TheMahjongTile;

import java.util.List;

public final class ShantenCalculator {
    private static final int TILE_KIND_COUNT = 34;

    private static final int[] TERMINALS_AND_HONORS_INDEX = {0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33};

    private ShantenCalculator() {}

    public static int shanten(List<TheMahjongTile> hand) {
        int[] counts = new int[TILE_KIND_COUNT];
        for (TheMahjongTile t : hand) {
            int code = codeOf(t);
            if (code >= 0 && code < TILE_KIND_COUNT) {
                counts[code]++;
            }
        }
        return shanten(counts);
    }

    public static int shanten(int[] counts) {
        int[] work = counts.clone();
        int std = standardShanten(work);
        int chii = chiitoiShanten(counts);
        int kok = kokushiShanten(counts);
        return Math.min(std, Math.min(chii, kok));
    }

    private static int chiitoiShanten(int[] c) {
        int pairs = 0;
        int kinds = 0;
        for (int v : c) {
            if (v >= 1) kinds++;
            if (v >= 2) pairs++;
        }
        if (pairs > 7) pairs = 7;
        int singlesNeeded = Math.max(0, 7 - kinds - pairs);
        return 6 - pairs + singlesNeeded;
    }

    private static int kokushiShanten(int[] c) {
        int kinds = 0;
        boolean hasPair = false;
        for (int idx : TERMINALS_AND_HONORS_INDEX) {
            if (c[idx] >= 1) kinds++;
            if (c[idx] >= 2) hasPair = true;
        }
        return 13 - kinds - (hasPair ? 1 : 0);
    }

    private static int standardShanten(int[] c) {
        int best = 8;
        for (int i = 0; i < TILE_KIND_COUNT; i++) {
            if (c[i] >= 2) {
                c[i] -= 2;
                int withPair = 7 - bestSetScore(c, 0, 0, 0, 4);
                if (withPair < best) best = withPair;
                c[i] += 2;
            }
        }
        int noPair = 8 - bestSetScore(c, 0, 0, 0, 4);
        if (noPair < best) best = noPair;
        return Math.max(-1, best);
    }

    private static int bestSetScore(int[] c, int idx, int sets, int partials, int maxGroups) {
        while (idx < TILE_KIND_COUNT && c[idx] == 0) idx++;
        if (idx >= TILE_KIND_COUNT) {
            return 2 * sets + partials;
        }
        if (sets + partials >= maxGroups) {
            return 2 * sets + partials;
        }
        int best = 2 * sets + partials;

        if (c[idx] >= 3) {
            c[idx] -= 3;
            best = Math.max(best, bestSetScore(c, idx, sets + 1, partials, maxGroups));
            c[idx] += 3;
        }
        if (idx < 27 && (idx % 9) <= 6 && c[idx + 1] >= 1 && c[idx + 2] >= 1) {
            c[idx]--; c[idx + 1]--; c[idx + 2]--;
            best = Math.max(best, bestSetScore(c, idx, sets + 1, partials, maxGroups));
            c[idx]++; c[idx + 1]++; c[idx + 2]++;
        }
        if (c[idx] >= 2) {
            c[idx] -= 2;
            best = Math.max(best, bestSetScore(c, idx, sets, partials + 1, maxGroups));
            c[idx] += 2;
        }
        if (idx < 27) {
            int rank = idx % 9;
            if (rank <= 7 && c[idx + 1] >= 1) {
                c[idx]--; c[idx + 1]--;
                best = Math.max(best, bestSetScore(c, idx, sets, partials + 1, maxGroups));
                c[idx]++; c[idx + 1]++;
            }
            if (rank <= 6 && c[idx + 2] >= 1) {
                c[idx]--; c[idx + 2]--;
                best = Math.max(best, bestSetScore(c, idx, sets, partials + 1, maxGroups));
                c[idx]++; c[idx + 2]++;
            }
        }
        c[idx]--;
        best = Math.max(best, bestSetScore(c, idx, sets, partials, maxGroups));
        c[idx]++;

        return best;
    }

    private static int codeOf(TheMahjongTile t) {
        return switch (t.suit()) {
            case MANZU -> t.rank() - 1;
            case PINZU -> 9 + t.rank() - 1;
            case SOUZU -> 18 + t.rank() - 1;
            case WIND -> 27 + t.rank() - 1;
            case DRAGON -> 31 + t.rank() - 1;
        };
    }
}
