package com.riichimahjong.fabric;

import net.minecraft.world.phys.*;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import java.util.Optional;

/** Intersect the actual tile model before allowing a click to reach the table below it. */
final class TableHitTest {
    private static final AABB TILE=TableLayout.TILE_MODEL_BOUNDS;
    static AABB bounds(Vec3 at,float scale,Quaternionf rotation){
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX;
        double maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
            var vertex=rotation.transform(new Vector3d(x==0?TILE.minX:TILE.maxX,
                    y==0?TILE.minY:TILE.maxY,z==0?TILE.minZ:TILE.maxZ)).mul(scale);
            minX=Math.min(minX,vertex.x);minY=Math.min(minY,vertex.y);minZ=Math.min(minZ,vertex.z);
            maxX=Math.max(maxX,vertex.x);maxY=Math.max(maxY,vertex.y);maxZ=Math.max(maxZ,vertex.z);
        }
        return new AABB(at.x+minX,at.y+minY,at.z+minZ,at.x+maxX,at.y+maxY,at.z+maxZ);
    }
    static Optional<Vec3> tile(Vec3 start,Vec3 end,Vec3 at,float scale,Quaternionf rotation){
        var inverse=new Quaternionf(rotation).invert();
        Vec3 a=local(start,at,scale,inverse),b=local(end,at,scale,inverse);
        if(TILE.contains(a))return Optional.of(start);
        return TILE.clip(a,b).map(hit->{var v=rotation.transform(new Vector3f((float)hit.x,(float)hit.y,(float)hit.z));return at.add(v.x*scale,v.y*scale,v.z*scale);});
    }
    private static Vec3 local(Vec3 point,Vec3 at,float scale,Quaternionf inverse){
        var v=inverse.transform(new Vector3f((float)((point.x-at.x)/scale),(float)((point.y-at.y)/scale),(float)((point.z-at.z)/scale)));
        return new Vec3(v.x,v.y,v.z);
    }
}
