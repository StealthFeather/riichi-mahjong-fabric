package com.riichimahjong.fabric;

import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Vanilla interaction entities have square footprints; overlapping squares fill each rectangular tile exactly. */
final class TileInteractionLayout {
    record Cell(Vec3 feet,float width,float height) {
        void apply(InteractionElement element){element.setOverridePos(feet);element.setSize(width,height);}
        AABB bounds(){return new AABB(feet.x-width/2,feet.y,feet.z-width/2,
                feet.x+width/2,feet.y+height,feet.z+width/2);}
    }
    static List<Cell> cells(Vec3 at,float scale,Quaternionf rotation){
        var bounds=TableHitTest.bounds(at,scale,rotation);
        double x=bounds.maxX-bounds.minX,z=bounds.maxZ-bounds.minZ;
        boolean alongX=x>=z;
        double length=Math.max(x,z);
        float width=(float)Math.min(x,z),height=(float)(bounds.maxY-bounds.minY);
        int count=Math.max(1,(int)Math.ceil(length/width-1e-6));
        var cells=new ArrayList<Cell>(count);
        for(int index=0;index<count;index++){
            double offset=count==1?0:-length/2+width/2+index*(length-width)/(count-1);
            cells.add(new Cell(new Vec3((bounds.minX+bounds.maxX)/2+(alongX?offset:0),bounds.minY,
                    (bounds.minZ+bounds.maxZ)/2+(alongX?0:offset)),width,height));
        }
        return List.copyOf(cells);
    }
}
