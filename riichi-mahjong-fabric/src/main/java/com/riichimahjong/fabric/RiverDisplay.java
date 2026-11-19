package com.riichimahjong.fabric;

import com.themahjong.TheMahjongPlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Transfer a called declaration's sideways marker to the next visible discard. */
final class RiverDisplay {
    private RiverDisplay() {}

    static Vec3 position(BlockPos table, Direction outward, int index) {
        return TableLayout.anchor(table, outward, (index % 6 - 2.5) * .073, .59, .26 + (index / 6) * .09);
    }

    static Quaternionf rotation(Direction outward, boolean sideways) {
        var rotation = PortViews.rotation(outward, true, false);
        if (sideways) rotation.rotateLocalY((float) Math.PI / 2);
        return rotation;
    }

    static List<PublicTable.Tile> capture(List<TheMahjongPlayer> players, int seat) {
        var discards = players.get(seat).discards();
        var claimed = PublicTable.claimed(players, seat);
        var river = new ArrayList<PublicTable.Tile>();
        boolean sideways = false;
        for (int index = 0; index < discards.size(); index++) {
            var discard = discards.get(index);
            sideways |= discard.riichiDeclared();
            if (claimed.contains(index)) continue;
            river.add(new PublicTable.Tile(PublicTable.code(discard.tile()), sideways));
            sideways = false;
        }
        return List.copyOf(river);
    }
}
