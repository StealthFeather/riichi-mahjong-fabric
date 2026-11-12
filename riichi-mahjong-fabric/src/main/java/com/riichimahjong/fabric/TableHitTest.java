package com.riichimahjong.fabric;

import net.minecraft.world.phys.*;
import org.joml.*;
import java.util.Optional;

/** Intersect the actual tile model before allowing a click to reach the table below it. */
final class TableHitTest {
    private static final AABB TILE=TableLayout.TILE_MODEL_BOUNDS;
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
