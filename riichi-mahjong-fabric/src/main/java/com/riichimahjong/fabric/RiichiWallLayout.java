package com.riichimahjong.fabric;

import com.themahjong.driver.MatchPhase;
import java.util.ArrayList;
import java.util.List;

/** Count ordinary wall tiles independently of fixed opening jokers and player count. */
final class RiichiWallLayout {
    static final int DEAD_WALL_TILES = 14;
    private static final int[] DRAW_ORDER = {0, 3, 2, 1};
    record Wall(List<Integer> sides, int deadWall) {
        int liveTiles() { return sides.stream().mapToInt(Integer::intValue).sum(); }
    }

    static Wall capture(int ordinaryTiles, int liveWall, int openingWallDraws,
                        MatchPhase.Stage stage, double progress) {
        if (ordinaryTiles < 56 || ordinaryTiles % 2 != 0)
            throw new IllegalArgumentException("Ordinary riichi wall needs even tile stacks on all four sides");
        int stacks = ordinaryTiles / 2;
        int[] full = new int[4];
        for (int seat = 0; seat < full.length; seat++)
            full[seat] = (stacks / 4 + (seat < stacks % 4 ? 1 : 0)) * 2;
        int[] live = full.clone();
        live[0] -= DEAD_WALL_TILES;
        double fraction = Math.clamp(progress, 0, 1);
        if (stage == MatchPhase.Stage.WALL_BUILDING) {
            int built = (int)Math.floor(stacks * fraction), before = 0;
            var visible = new ArrayList<Integer>(4);
            for (int seat = 0; seat < full.length; seat++) {
                visible.add(Math.min(live[seat] / 2, Math.max(0, built - before)) * 2);
                before += full[seat] / 2;
            }
            int dead = Math.min(DEAD_WALL_TILES / 2, Math.max(0, built - live[0] / 2)) * 2;
            return new Wall(List.copyOf(visible), dead);
        }
        int dealt = ordinaryTiles - DEAD_WALL_TILES - liveWall;
        if (stage == MatchPhase.Stage.DEALING_HANDS) dealt = (int)Math.round(openingWallDraws * fraction);
        else if (stage == MatchPhase.Stage.DORA_FLIP) dealt = openingWallDraws;
        dealt = Math.clamp(dealt, 0, ordinaryTiles - DEAD_WALL_TILES);
        for (int seat : DRAW_ORDER) {
            int taken = Math.min(dealt, live[seat]);
            live[seat] -= taken;
            dealt -= taken;
        }
        return new Wall(List.of(live[0], live[1], live[2], live[3]), DEAD_WALL_TILES);
    }
}
