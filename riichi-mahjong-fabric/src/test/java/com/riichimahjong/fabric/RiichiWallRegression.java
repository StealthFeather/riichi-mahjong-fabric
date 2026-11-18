package com.riichimahjong.fabric;

import com.themahjong.driver.MatchPhase;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Wall visuals must conserve ordinary tiles when jokers replace one opening draw per seat. */
public final class RiichiWallRegression {
    public static void main(String[] args) {
        for (int players : new int[]{3, 4}) for (boolean jokers : new boolean[]{false, true}) {
            int total = players == 3 ? 108 : 136;
            int opening = players * (jokers ? 12 : 13);
            int live = total - 14 - opening;
            for (int stack = 0; stack <= total / 2; stack++) {
                double progress = (stack + .001) / (total / 2);
                var build = RiichiWallLayout.capture(total, live, opening, MatchPhase.Stage.WALL_BUILDING, progress);
                check(build.liveTiles() + build.deadWall() == stack * 2,
                        "wall building loses or invents ordinary tiles");
                check(build.deadWall() <= 14, "wall building creates an oversized dead wall");
            }
            for (int dealt = 0; dealt <= opening; dealt++) {
                var deal = RiichiWallLayout.capture(total, live, opening, MatchPhase.Stage.DEALING_HANDS,
                        (double)dealt / opening);
                check(deal.liveTiles() == total - 14 - dealt, "opening hand animation uses the wrong ordinary draw count");
                check(deal.deadWall() == 14, "dealing consumes the dead wall");
            }
            var flipped = RiichiWallLayout.capture(total, live, opening, MatchPhase.Stage.DORA_FLIP, 1);
            check(flipped.liveTiles() == live && flipped.deadWall() == 14, "dora flip restores an incorrect opening wall");
            for (int remaining = 0; remaining <= live; remaining++) {
                var wall = RiichiWallLayout.capture(total, remaining, opening, null, 1);
                check(wall.liveTiles() == remaining, "live wall visual count differs from the rules");
                check(wall.deadWall() == 14, "live draws consume the fixed dead-wall slots");
                check(wall.sides().stream().allMatch(n -> n >= 0), "a wall side has a negative tile count");
            }
        }
        var legacy = RiichiWallLayout.capture(136, 70, 52, null, 1);
        check(legacy.sides().equals(java.util.List.of(0, 34, 34, 2)), "standard four-player opening wall changed");
        System.out.println("PASS: three/four-player ordinary walls conserve every tile through building, joker/normal dealing and live draws");
    }
}
