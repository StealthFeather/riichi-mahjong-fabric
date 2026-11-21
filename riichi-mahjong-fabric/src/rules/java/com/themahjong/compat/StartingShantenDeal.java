package com.themahjong.compat;

import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.ShantenCalculator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Arranges a standard four-player wall before the thirteen-tile hands are dealt. */
public final class StartingShantenDeal {
    // 123m 789m 234p 678s East is tenpai. Each successive substitution adds one shanten.
    private static final int[] READY_HAND = {0, 1, 2, 6, 7, 8, 10, 11, 12, 23, 24, 25, 27};
    private static final int[] SUBSTITUTE_WITH_HONOR = {0, 2, 3, 5, 6, 8};
    private static final int HAND_SIZE = TheMahjongRound.INITIAL_HAND_SIZE;
    private static final int PLAYER_COUNT = 4;

    private StartingShantenDeal() {}

    public static List<TheMahjongTile> arrange(List<TheMahjongTile> shuffledWall,
            Map<Integer, Integer> targets, Random random) {
        if (targets.isEmpty()) return shuffledWall;
        if (shuffledWall.size() != 136) throw new IllegalArgumentException("Expected a four-player riichi wall");
        for (var entry : targets.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= PLAYER_COUNT || entry.getValue() < 0 || entry.getValue() > 6) {
                throw new IllegalArgumentException("Expected seat 0..3 and shanten 0..6");
            }
        }

        var pool = new ArrayList<>(shuffledWall);
        var hands = new ArrayList<List<TheMahjongTile>>(PLAYER_COUNT);
        for (int seat = 0; seat < PLAYER_COUNT; seat++) {
            Integer target = targets.get(seat);
            hands.add(target == null ? List.of() : reserveHand(pool, target, random));
        }
        // Each seed uses at most one copy per kind. Reserving all seeds before swaps guarantees
        // there are enough physical tiles for any combination of targets.
        for (int seat = 0; seat < PLAYER_COUNT; seat++) {
            if (targets.containsKey(seat)) varyHand(hands.get(seat), pool, targets.get(seat), random);
        }
        Collections.shuffle(pool, random);
        var wall = new ArrayList<TheMahjongTile>(shuffledWall.size());
        int next = 0;
        for (var hand : hands) {
            if (hand.isEmpty()) {
                wall.addAll(pool.subList(next, next + HAND_SIZE));
                next += HAND_SIZE;
            } else {
                Collections.shuffle(hand, random);
                wall.addAll(hand);
            }
        }
        wall.addAll(pool.subList(next, pool.size()));
        return wall;
    }

    private static List<TheMahjongTile> reserveHand(List<TheMahjongTile> pool, int target, Random random) {
        int[] seed = READY_HAND.clone();
        for (int i = 0; i < target; i++) seed[SUBSTITUTE_WITH_HONOR[i]] = 28 + i;
        // Permuting suits/honors or reflecting the ranks of one suit preserves all three hand shapes.
        var suitOffsets = new ArrayList<>(List.of(0, 9, 18));
        var honors = new ArrayList<>(List.of(27, 28, 29, 30, 31, 32, 33));
        Collections.shuffle(suitOffsets, random);
        Collections.shuffle(honors, random);
        boolean[] reverse = {random.nextBoolean(), random.nextBoolean(), random.nextBoolean()};
        var hand = new ArrayList<TheMahjongTile>(HAND_SIZE);
        for (int kind : seed) {
            int wanted = kind >= 27 ? honors.get(kind - 27)
                    : suitOffsets.get(kind / 9) + (reverse[kind / 9] ? 8 - kind % 9 : kind % 9);
            int index = 0;
            while (index < pool.size() && kind(pool.get(index)) != wanted) index++;
            if (index == pool.size()) throw new IllegalArgumentException("Missing tile kind in four-player wall: " + wanted);
            hand.add(pool.remove(index));
        }
        return hand;
    }

    private static void varyHand(List<TheMahjongTile> hand, List<TheMahjongTile> pool, int target, Random random) {
        int[] counts = new int[34];
        for (var tile : hand) counts[kind(tile)]++;
        int distinct = HAND_SIZE;
        for (int attempt = 0; attempt < 4 * HAND_SIZE; attempt++) {
            int handIndex = random.nextInt(hand.size());
            int poolIndex = random.nextInt(pool.size());
            int oldKind = kind(hand.get(handIndex));
            int newKind = kind(pool.get(poolIndex));
            if (oldKind == newKind) continue;
            int newDistinct = distinct + (counts[newKind] == 0 ? 1 : 0) - (counts[oldKind] == 1 ? 1 : 0);
            // Seven or more kinds keep the seven-pairs distance unambiguous. Limiting repeated
            // kinds to three also excludes a standard hand that would require a fifth copy.
            if (newDistinct < 7 || counts[newKind] == 3) continue;
            counts[oldKind]--;
            counts[newKind]++;
            if (ShantenCalculator.shanten(counts) == target) {
                var outgoing = hand.set(handIndex, pool.get(poolIndex));
                pool.set(poolIndex, outgoing);
                distinct = newDistinct;
            } else {
                counts[oldKind]++;
                counts[newKind]--;
            }
        }
    }

    private static int kind(TheMahjongTile tile) {
        int offset = switch (tile.suit()) {
            case MANZU -> 0;
            case PINZU -> 9;
            case SOUZU -> 18;
            case WIND -> 27;
            case DRAGON -> 31;
        };
        return offset + tile.rank() - 1;
    }
}
