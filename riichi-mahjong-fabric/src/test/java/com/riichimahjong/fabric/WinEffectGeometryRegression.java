package com.riichimahjong.fabric;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static com.riichimahjong.fabric.RulesRegression.check;

/** The pushing hand must touch the felt at every intermediate rotation, on every seat. */
public final class WinEffectGeometryRegression {
    public static void main(String[] args) {
        var pos=new BlockPos(15,64,-20);
        for(var dir:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            for(int frame=0;frame<=100;frame++) {
                float progress=frame/100f;
                var rotation=PortViews.rotation(dir,false,false).slerp(PortViews.rotation(dir,true,false),progress);
                var at=TableLayout.handAnchor(pos,dir,.2,rotation);
                var transform=new Quaternionf(rotation).rotateY((float)Math.PI);
                double bottom=Double.POSITIVE_INFINITY;
                for(double x:new double[]{TableLayout.TILE_MODEL_BOUNDS.minX,TableLayout.TILE_MODEL_BOUNDS.maxX})
                    for(double y:new double[]{TableLayout.TILE_MODEL_BOUNDS.minY,TableLayout.TILE_MODEL_BOUNDS.maxY})
                        for(double z:new double[]{TableLayout.TILE_MODEL_BOUNDS.minZ,TableLayout.TILE_MODEL_BOUNDS.maxZ}) {
                            var corner=transform.transform(new Vector3f((float)x,(float)y,(float)z));
                            bottom=Math.min(bottom,at.y+corner.y*TableLayout.TILE_SCALE);
                        }
                check(Math.abs(bottom-pos.getY()-TableLayout.TABLE_TOP)<1e-7,"tilting hand clips or floats: "+dir+" frame "+frame);
                var next=TableLayout.handAnchor(pos,dir,.2+TableLayout.HAND_STEP,rotation);
                check(Math.abs(at.distanceTo(next)-TableLayout.HAND_STEP*TableLayout.TILE_ENLARGEMENT)<1e-7,
                        "pushing changed tile spacing: "+dir+" frame "+frame);
            }
            for(boolean flat:new boolean[]{false,true}) {
                var old=TableLayout.handAnchor(pos,dir,.2,flat);
                var current=TableLayout.handAnchor(pos,dir,.2,PortViews.rotation(dir,flat,false));
                check(old.distanceTo(current)<1e-7,"animation endpoint differs from static hand: "+dir+" flat="+flat);
            }
        }
        System.out.println("PASS: all four seats keep pushing tiles grounded at 101 intermediate angles and retain spacing/endpoints");
    }
}
