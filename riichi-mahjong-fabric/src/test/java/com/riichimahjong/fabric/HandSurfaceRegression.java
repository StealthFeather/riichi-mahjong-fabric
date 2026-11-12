package com.riichimahjong.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Check support against shipped model vertices, independently of the layout's bounding constants. */
public final class HandSurfaceRegression {
    private static JsonObject model(String name) throws Exception {
        var stream=HandSurfaceRegression.class.getResourceAsStream("/assets/riichi_mahjong/models/block/"+name+".json");
        if(stream==null)throw new AssertionError("Missing model "+name);
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static void close(double actual,double expected){
        if(Math.abs(actual-expected)>1e-6)throw new AssertionError(actual+" != "+expected);
    }
    public static void main(String[] args) throws Exception {
        var tile=model("tile").getAsJsonArray("elements").get(0).getAsJsonObject();
        var from=tile.getAsJsonArray("from");var to=tile.getAsJsonArray("to");
        double felt=model("mahjong_table").getAsJsonArray("elements").get(1).getAsJsonObject()
                .getAsJsonArray("to").get(1).getAsDouble()/16;
        close(felt,TableLayout.TABLE_TOP);
        int checked=0;
        for(var pos:List.of(new BlockPos(10,64,-20),new BlockPos(-17,-60,15)))
            for(var dir:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))
                for(boolean flat:new boolean[]{false,true})for(int slot=0;slot<14;slot++){
                    double along=TableLayout.handAlong(slot,14,true);
                    var at=TableLayout.handAnchor(pos,dir,along,flat);
                    var rotation=new Quaternionf(PortViews.rotation(dir,flat,false))
                            .mul(new Quaternionf().rotationY((float)Math.PI));
                    double bottom=Double.POSITIVE_INFINITY,top=Double.NEGATIVE_INFINITY;
                    for(int x=0;x<2;x++)for(int y=0;y<2;y++)for(int z=0;z<2;z++){
                        var vertex=new Vector3f(
                                (x==0?from:to).get(0).getAsFloat()/16-.5f,
                                (y==0?from:to).get(1).getAsFloat()/16-.5f,
                                (z==0?from:to).get(2).getAsFloat()/16-.5f);
                        rotation.transform(vertex).mul(TableLayout.TILE_SCALE);
                        bottom=Math.min(bottom,at.y+vertex.y);top=Math.max(top,at.y+vertex.y);
                        if(Math.abs(at.x+vertex.x-pos.getX()-.5)>TableLayout.TABLE_WIDTH/2
                                ||Math.abs(at.z+vertex.z-pos.getZ()-.5)>TableLayout.TABLE_WIDTH/2)
                            throw new AssertionError("Hand vertex outside tabletop");
                    }
                    close(bottom,pos.getY()+felt);
                    if(!flat){
                        // Match the owner's interaction feet and confirm a low ray still selects the tile.
                        close(at.y-.05*TableLayout.TILE_ENLARGEMENT,bottom);
                        if(at.y-.05*TableLayout.TILE_ENLARGEMENT+.12*TableLayout.TILE_ENLARGEMENT<top)
                            throw new AssertionError("Interaction does not cover standing hand");
                        var end=new Vec3(at.x,bottom+.01,at.z);
                        var start=end.add(dir.getStepX()*.5,0,dir.getStepZ()*.5);
                        if(TableHitTest.tile(start,end,at,TableLayout.TILE_SCALE,PortViews.rotation(dir,false,false)).isEmpty())
                            throw new AssertionError("Low ray misses grounded hand");
                    }
                    checked++;
                }
        System.out.println("PASS: "+checked+" standing/revealed hand positions touch the shipped tabletop; owner hitboxes and low rays match");
    }
}
