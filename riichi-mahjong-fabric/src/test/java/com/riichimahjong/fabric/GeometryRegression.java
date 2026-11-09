package com.riichimahjong.fabric;

import net.minecraft.core.*;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public final class GeometryRegression {
    static void close(double actual,double expected){if(Math.abs(actual-expected)>1e-6)throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        var pos=new BlockPos(10,64,-20);
        close(TableLayout.TABLE_WIDTH,2);close(TableLayout.TABLE_HORIZONTAL_SCALE,1);
        // Model extent is -8..24 pixels: a half block, one centre block, a half block.
        close(pos.getX()+.5-TableLayout.TABLE_WIDTH/2,9.5);
        close(pos.getX()+.5+TableLayout.TABLE_WIDTH/2,11.5);
        for(var dir:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){
            var at=TableLayout.anchor(pos,dir,0,TableLayout.TABLE_TOP,1);
            close(at.x,pos.getX()+.5+dir.getStepX());close(at.z,pos.getZ()+.5+dir.getStepZ());close(at.y,64+9.0/16);
            var normal=TableLayout.textRotation(dir).transform(new Vector3f(0,0,1));
            close(normal.x,0);close(normal.y,1);close(normal.z,0);
            var top=TableLayout.textRotation(dir).transform(new Vector3f(0,1,0));
            close(top.x,-dir.getStepX());close(top.z,-dir.getStepZ());
        }
        System.out.println("PASS: centred 2 x 2 table and upward-facing wind labels in every rotation");
        for(var dir:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST})for(boolean flat:new boolean[]{false,true}){
            var rotation=PortViews.rotation(dir,flat,false);var at=new Vec3(4.5,65,-3.5);float scale=.1f;
            Vec3 start=world(new Vec3(0,2,0),at,scale,rotation),end=world(new Vec3(0,-2,0),at,scale,rotation);
            if(TableHitTest.tile(start,end,at,scale,rotation).isEmpty())throw new AssertionError("ray missed visible tile in "+dir+" flat="+flat);
            Vec3 missA=world(new Vec3(.45,2,0),at,scale,rotation),missB=world(new Vec3(.45,-2,0),at,scale,rotation);
            if(TableHitTest.tile(missA,missB,at,scale,rotation).isPresent())throw new AssertionError("empty table space treated as tile");
            var stopped=world(new Vec3(0,1,0),at,scale,rotation);
            if(TableHitTest.tile(start,stopped,at,scale,rotation).isPresent())throw new AssertionError("tile beyond actual table hit intercepted click");
        }
        System.out.println("PASS: tile rays, empty table space and occlusion in all standing/flat rotations");
        close(TableButtonLayout.textWidth("立直"),18);close(TableButtonLayout.textWidth("吃 1万 2万"),42);
        var buttons=TableButtonLayout.arrange(List.of("吃 1万 2万","吃 2万 4万","碰 3万 3万","大明杠 3万 3万 3万","荣和","跳过"));
        for(var dir:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){
            var line=dir.getCounterClockWise();
            for(var button:buttons){
                var centre=button.centre(pos,dir);var display=button.textPosition(pos,dir);var q=TableLayout.textRotation(dir);
                // Reconstruct the four corners from the vanilla renderer's pixel
                // transform, independently of the hit plane implementation.
                for(int px:new int[]{-1,button.pixels()})for(int py:new int[]{-1,9}){
                    double x=(px+1-button.pixels()/2.0)*button.pixelSize(),y=-(py-9)*button.pixelSize();
                    var offset=q.transform(new Vector3f((float)x,(float)y,0));var corner=display.add(offset.x,offset.y,offset.z);var delta=corner.subtract(centre);
                    close(Math.abs(delta.x*line.getStepX()+delta.z*line.getStepZ()),button.width()/2);
                    close(Math.abs(delta.x*dir.getStepX()+delta.z*dir.getStepZ()),button.depth()/2);
                    close(corner.y,centre.y);
                }
                if(button.hit(pos,dir,centre.add(0,2,0),centre.add(0,-1,0)).isEmpty())throw new AssertionError("button centre missed in "+dir);
                for(var outside:List.of(centre.add(line.getStepX()*(button.width()/2+.001),0,line.getStepZ()*(button.width()/2+.001)),centre.add(dir.getStepX()*(button.depth()/2+.001),0,dir.getStepZ()*(button.depth()/2+.001)))){
                    if(button.hit(pos,dir,outside.add(0,2,0),outside.add(0,-1,0)).isPresent())throw new AssertionError("button accepted empty space in "+dir);
                }
                if(button.hit(pos,dir,centre.add(0,-1,0),centre.add(0,2,0)).isPresent())throw new AssertionError("button clickable through table underside");
                if(Math.abs(button.along())+button.width()/2>TableButtonLayout.MAX_WIDTH/2+1e-6)throw new AssertionError("button beyond table edge span");
                if(TableButtonLayout.EDGE_DISTANCE+button.depth()/2>1)throw new AssertionError("button outside tabletop");
            }
            for(int count:new int[]{2,5,8,11,14}){
                double before=TableLayout.handAlong(count-2,count,true),draw=TableLayout.handAlong(count-1,count,true);
                close(draw-before-TableLayout.TILE_WIDTH,TableLayout.TILE_WIDTH/3);
                var beforePos=TableLayout.anchor(pos,dir,before,.65,.85);var drawPos=TableLayout.anchor(pos,dir,draw,.65,.85);
                var delta=drawPos.subtract(beforePos);if(delta.x*line.getStepX()+delta.z*line.getStepZ()<=0)throw new AssertionError("draw not on player's right in "+dir);
                close(TableLayout.handAlong(count-1,count,false)-TableLayout.handAlong(count-2,count,false),TableLayout.HAND_STEP);
            }
        }
        System.out.println("PASS: table-edge buttons match vanilla text backgrounds and drawn tiles have one-third-width gap in every rotation");
    }
    private static Vec3 world(Vec3 local,Vec3 at,float scale,Quaternionf q){var v=q.transform(new Vector3f((float)local.x,(float)local.y,(float)local.z));return at.add(v.x*scale,v.y*scale,v.z*scale);}
}
