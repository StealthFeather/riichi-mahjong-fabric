package com.riichimahjong.fabric;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Match the shipped wind plate and keep menu/start/advance gestures distinct. */
public final class TableCentreRegression {
    private static AABB modelPlate(BlockPos master) throws Exception {
        try (var reader = new InputStreamReader(TableCentreRegression.class.getResourceAsStream(
                "/assets/riichi_mahjong/models/block/mahjong_table.json"),StandardCharsets.UTF_8)) {
            var elements = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements");
            // The raised central wind plate is the only box centred at (8,8) with a 4px footprint.
            for (var value : elements) {
                var element = value.getAsJsonObject();
                var from = element.getAsJsonArray("from");var to = element.getAsJsonArray("to");
                if (from.get(0).getAsDouble()!=6 || from.get(2).getAsDouble()!=6
                        || to.get(0).getAsDouble()!=10 || to.get(2).getAsDouble()!=10) continue;
                double scale = TableLayout.TABLE_HORIZONTAL_SCALE;
                return new AABB(master.getX()+.5+(from.get(0).getAsDouble()/16-.5)*scale,
                        master.getY()+from.get(1).getAsDouble()/16,
                        master.getZ()+.5+(from.get(2).getAsDouble()/16-.5)*scale,
                        master.getX()+.5+(to.get(0).getAsDouble()/16-.5)*scale,
                        master.getY()+to.get(1).getAsDouble()/16,
                        master.getZ()+.5+(to.get(2).getAsDouble()/16-.5)*scale);
            }
        }
        throw new AssertionError("shipped table has no central wind plate");
    }

    private static boolean aims(BlockPos master, Vec3 eye, Vec3 point) {
        return TableCentreInteraction.hitsWindPlate(master,eye,eye.add(point.subtract(eye).normalize().scale(8)));
    }

    private static void geometry() throws Exception {
        for (var master : List.of(new BlockPos(0,64,0),new BlockPos(-78000,63,49000))) {
            var plate = modelPlate(master);
            for (var side : Direction.Plane.HORIZONTAL) {
                var eye = new Vec3(master.getX()+.5+side.getStepX()*2,
                        master.getY()+1.35,master.getZ()+.5+side.getStepZ()*2);
                for (int x=0;x<=4;x++) for (int z=0;z<=4;z++) {
                    var point = new Vec3(plate.minX+(plate.maxX-plate.minX)*x/4,plate.maxY,
                            plate.minZ+(plate.maxZ-plate.minZ)*z/4);
                    check(aims(master,eye,point),"a visible wind-plate surface point was missed from "+side);
                }
                var centre = new Vec3(master.getX()+.5,plate.maxY,master.getZ()+.5);
                for (var outside : List.of(new Vec3(plate.minX-.001,plate.maxY,centre.z),
                        new Vec3(plate.maxX+.001,plate.maxY,centre.z),
                        new Vec3(centre.x,plate.maxY,plate.minZ-.001),
                        new Vec3(centre.x,plate.maxY,plate.maxZ+.001))) {
                    check(!aims(master,eye,outside),"cloth next to the wind plate was treated as the menu target");
                }
                check(!aims(master,eye,centre.add(side.getStepX(),0,side.getStepZ())),
                        "the table-wide collider's synthetic centre hit opened a wind-plate action from its edge");
                check(!TableCentreInteraction.hitsWindPlate(master,eye,
                                eye.add(eye.subtract(centre).normalize().scale(8))),
                        "a player facing away could activate the wind plate");
            }
            var centre = new Vec3(master.getX()+.5,plate.maxY,master.getZ()+.5);
            var far = centre.add(0,1,9);
            check(!TableCentreInteraction.hitsWindPlate(master,far,far.add(centre.subtract(far).scale(2))),
                    "an out-of-range wind plate was accepted");
            check(!TableCentreInteraction.hitsWindPlate(master,centre.add(0,-1,0),centre.add(0,1,0)),
                    "an underside hit was treated as the wind plate's top");
            check(!TableCentreInteraction.hitsWindPlate(master,centre.add(0,1,0),centre.add(1,1,0)),
                    "a parallel ray activated the wind plate");
        }
    }

    public static void main(String[] args) throws Exception {
        geometry();
        System.out.println("PASS: centre interactions match the shipped raised wind plate; edges, synthetic hits, back-facing and distant rays are rejected");
    }
}
