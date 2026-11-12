package com.riichimahjong.fabric;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/** Compare the actual vanilla interaction volumes with vertices read independently from the shipped tile model. */
public final class TileInteractionRegression {
    private static final double EPS=1e-7;
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    private static void close(double actual,double expected){check(Math.abs(actual-expected)<EPS,actual+" != "+expected);}
    private static AABB modelBounds(Vec3 at,float scale,Quaternionf left) throws Exception {
        try(var reader=new InputStreamReader(TileInteractionRegression.class.getResourceAsStream(
                "/assets/riichi_mahjong/models/block/tile.json"),StandardCharsets.UTF_8)){
            var elements=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements");
            var points=new ArrayList<Vec3>();
            var transform=new Quaternionf(left).mul(new Quaternionf().rotationY((float)Math.PI));
            for(var value:elements){var element=value.getAsJsonObject();
                var from=element.getAsJsonArray("from");var to=element.getAsJsonArray("to");
                for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
                    var vertex=transform.transform(new Vector3d((x==0?from:to).get(0).getAsDouble()/16-.5,
                            (y==0?from:to).get(1).getAsDouble()/16-.5,(z==0?from:to).get(2).getAsDouble()/16-.5)).mul(scale);
                    points.add(at.add(vertex.x,vertex.y,vertex.z));
                }
            }
            return new AABB(points.stream().mapToDouble(v->v.x).min().orElseThrow(),points.stream().mapToDouble(v->v.y).min().orElseThrow(),points.stream().mapToDouble(v->v.z).min().orElseThrow(),
                    points.stream().mapToDouble(v->v.x).max().orElseThrow(),points.stream().mapToDouble(v->v.y).max().orElseThrow(),points.stream().mapToDouble(v->v.z).max().orElseThrow());
        }
    }
    private static boolean covered(List<TileInteractionLayout.Cell> cells,Vec3 point){
        return cells.stream().anyMatch(cell->cell.bounds().inflate(EPS).contains(point));
    }
    private static void volume(Vec3 at,float scale,Quaternionf rotation) throws Exception {
        var model=modelBounds(at,scale,rotation);var cells=TileInteractionLayout.cells(at,scale,rotation);
        check(!cells.isEmpty(),"no client interaction volume");
        for(var cell:cells){
            var box=cell.bounds();
            check(box.minX>=model.minX-EPS&&box.maxX<=model.maxX+EPS&&box.minZ>=model.minZ-EPS&&box.maxZ<=model.maxZ+EPS,
                    "interaction extends beyond the visible tile");
            close(box.minY,model.minY);close(box.maxY,model.maxY);
        }
        // Sample all faces, corners, interior and seams; a rectangular display must have no missing strip.
        for(int x=0;x<=12;x++)for(int y=0;y<=4;y++)for(int z=0;z<=12;z++){
            var point=new Vec3(model.minX+(model.maxX-model.minX)*x/12,model.minY+(model.maxY-model.minY)*y/4,
                    model.minZ+(model.maxZ-model.minZ)*z/12);
            check(covered(cells,point),"visible surface has no interaction: "+point);
        }
        var centre=model.getCenter();
        for(var outside:List.of(new Vec3(model.minX-.0001,centre.y,centre.z),new Vec3(model.maxX+.0001,centre.y,centre.z),
                new Vec3(centre.x,model.minY-.0001,centre.z),new Vec3(centre.x,model.maxY+.0001,centre.z),
                new Vec3(centre.x,centre.y,model.minZ-.0001),new Vec3(centre.x,centre.y,model.maxZ+.0001))){
            check(!covered(cells,outside),"empty space outside tile is clickable");
            var axis=outside.subtract(centre);var perpendicular=Math.abs(axis.x)>0?new Vec3(0,0,1):new Vec3(1,0,0);
            check(TableHitTest.tile(outside.add(perpendicular),outside.subtract(perpendicular),at,scale,rotation).isEmpty(),
                    "ray outside visible tile accepted");
        }
    }
    private static void gaps() {
        var pos=new BlockPos(-78000,64,49000);
        for(var dir:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
            var rotation=PortViews.rotation(dir,false,false);var cells=new ArrayList<TileInteractionLayout.Cell>();
            var boxes=new ArrayList<AABB>();
            for(int index=0;index<14;index++){
                var at=TableLayout.handAnchor(pos,dir,TableLayout.handAlong(index,14,true),false);
                cells.addAll(TileInteractionLayout.cells(at,TableLayout.TILE_SCALE,rotation));boxes.add(TableHitTest.bounds(at,TableLayout.TILE_SCALE,rotation));
            }
            for(int index=1;index<14;index++){
                var first=boxes.get(index-1).getCenter();var second=boxes.get(index).getCenter();
                check(!covered(cells,first.add(second).scale(.5)),"ordinary/drawn tile gap has an interaction");
            }
            // The old 0.18-block-tall box accepted this point above a standing tile.
            var first=boxes.getFirst();check(!covered(cells,new Vec3(first.getCenter().x,first.maxY+.01,first.getCenter().z)),"old oversized headroom still clickable");
        }
    }
    public static void main(String[] args) throws Exception {
        int checked=0;
        for(float scale:new float[]{TableLayout.TILE_SCALE,(float)((2.0/17)/(11.0/16))})
            for(var dir:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))
                for(boolean flat:new boolean[]{false,true})for(boolean down:new boolean[]{false,true})for(boolean sideways:new boolean[]{false,true}){
                    var rotation=PortViews.rotation(dir,flat,down);if(sideways)rotation.rotateLocalY((float)Math.PI/2);
                    volume(new Vec3(-78000.6,64.6375,49000.7),scale,rotation);checked++;
                }
        gaps();
        System.out.println("PASS: "+checked+" tile transforms have client interaction volumes equal to shipped model bounds; all faces covered, outside/gaps rejected");
    }
}
