package com.riichimahjong.fabric;

import java.util.UUID;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import static com.riichimahjong.fabric.RulesRegression.check;

/** A live spectator choice must never grant concealed information to an ordinary nearby player. */
public final class SpectatorAudienceRegression {
    public static void main(String[] args) {
        var audience = new SpectatorSeats();
        var spectator = UUID.randomUUID();
        check(!audience.contains(spectator) && audience.add(spectator), "spectator did not explicitly join");
        check(audience.contains(spectator) && !audience.add(spectator), "duplicate spectator membership");
        var snapshot = audience.players();
        audience.remove(spectator);
        check(snapshot.contains(spectator) && audience.players().isEmpty(), "spectator snapshot was mutable");
        audience.add(spectator); audience.clear();
        check(!audience.contains(spectator), "unloaded table kept its live spectator audience");
        for (boolean registered : new boolean[]{false,true})
            for (boolean visible : new boolean[]{false,true})
                for (boolean seated : new boolean[]{false,true})
                    for (boolean near : new boolean[]{false,true}) {
                        check(SpectatorSeats.canSeeHands(registered, visible, seated, near)
                                        == (registered && visible && !seated && near),
                                "concealed hand audience leaked across observer/option/seat/range boundary");
                        check(SpectatorSeats.receivesHud(true, seated, registered, near)
                                        == (near && (seated || registered)),
                                "spectator and seated HUD audiences differ");
                        check(!SpectatorSeats.receivesHud(false, seated, registered, near),
                                "idle table shows a stale battle HUD");
                    }
        var table = new BlockPos(15,70,-22);
        for (var direction : List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))
            for (int slot=0;slot<14;slot++) {
                var at=SpectatorHands.tilePosition(table,direction,slot,List.of(13));
                var rotation=PortViews.rotation(direction,true,false).rotateY((float)Math.PI);
                var bounds=TableLayout.TILE_MODEL_BOUNDS;
                double bottom=Double.POSITIVE_INFINITY;
                for(double x:new double[]{bounds.minX,bounds.maxX})
                    for(double y:new double[]{bounds.minY,bounds.maxY})
                        for(double z:new double[]{bounds.minZ,bounds.maxZ}) {
                            var vertex=rotation.transform(new Vector3f((float)x,(float)y,(float)z)).mul(TableLayout.TILE_SCALE);
                            bottom=Math.min(bottom,at.y+vertex.y);
                            check(Math.abs(at.x+vertex.x-table.getX()-.5)<=TableLayout.TABLE_WIDTH/2
                                            &&Math.abs(at.z+vertex.z-table.getZ()-.5)<=TableLayout.TABLE_WIDTH/2,
                                    "a spectator tile extends outside the tabletop");
                        }
                check(Math.abs(bottom-table.getY()-TableLayout.TABLE_TOP)<1e-6,
                        "spectator hands penetrate or float above the table");
            }
        System.out.println("PASS: only explicit nearby unseated spectators receive optional concealed hands; spectator hands stay grounded, and observers share player HUD membership and unload cleanup");
    }
}
