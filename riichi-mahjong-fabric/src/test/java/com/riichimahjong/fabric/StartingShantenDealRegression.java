package com.riichimahjong.fabric;

import com.riichimahjong.ai.FallbackPolicy;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTileSet;
import com.themahjong.compat.StartingShantenDeal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static com.riichimahjong.fabric.RulesRegression.check;

public final class StartingShantenDealRegression {
    public static void main(String[] args) {
        var red = TheMahjongTileSet.standardRiichi(true);
        var plain = TheMahjongTileSet.standardRiichi(false);
        for (int code = 0; code < 7 * 7 * 7 * 7; code++) {
            var targets = new HashMap<Integer, Integer>();
            int digits = code;
            for (int seat = 0; seat < 4; seat++) {
                targets.put(seat, digits % 7);
                digits /= 7;
            }
            var tileSet = code % 2 == 0 ? red : plain;
            var random = new Random(code);
            var wall = StartingShantenDeal.arrange(tileSet.createShuffledWall(random), targets, random);
            var copies = new HashMap<TheMahjongTile, Integer>();
            for (var tile : wall) copies.merge(tile, 1, Integer::sum);
            check(copies.equals(tileSet.copiesPerTile()), "Physical tiles changed for " + targets);
            for (int seat = 0; seat < 4; seat++) {
                check(distance(wall, seat) == targets.get(seat), "Wrong starting shanten for seat " + seat + " in " + targets);
            }
        }

        var original = red.createShuffledWall(new Random(42));
        var random = new Random(42);
        check(StartingShantenDeal.arrange(original, Map.of(), random) == original
                && random.nextLong() == new Random(42).nextLong(), "Unspecified deal consumed randomness or changed the wall");
        var targets = Map.of(1, 0, 3, 6);
        var again = StartingShantenDeal.arrange(original, targets, new Random(9));
        check(again.equals(StartingShantenDeal.arrange(original, targets, new Random(9))), "Fixed seed was not repeatable");
        var tenpaiHands = new HashSet<List<TheMahjongTile>>();
        var freeDistances = new HashSet<Integer>();
        for (int seed = 0; seed < 32; seed++) {
            var wall = StartingShantenDeal.arrange(original, targets, new Random(seed));
            check(distance(wall, 1) == 0 && distance(wall, 3) == 6, "Partially specified targets changed seats");
            tenpaiHands.add(wall.subList(13, 26).stream().sorted(TheMahjongTile.DISPLAY_ORDER).toList());
            freeDistances.add(distance(wall, 0));
        }
        check(tenpaiHands.size() > 1 && freeDistances.size() > 1, "Configured or unconfigured hands never varied");
        System.out.println("PASS: every 0..6 four-seat shanten combination, physical tile conservation, partial and repeatable deals");
    }

    private static int distance(List<TheMahjongTile> wall, int seat) {
        int[] counts = new int[34];
        for (var tile : wall.subList(13 * seat, 13 * (seat + 1))) {
            int index = switch (tile.suit()) {
                case MANZU -> tile.rank() - 1;
                case PINZU -> tile.rank() + 8;
                case SOUZU -> tile.rank() + 17;
                case WIND -> tile.rank() + 26;
                case DRAGON -> tile.rank() + 30;
            };
            counts[index]++;
        }
        // The AI's independent solver checks standard hands, seven pairs and thirteen orphans.
        return FallbackPolicy.shanten(counts, 0);
    }
}
