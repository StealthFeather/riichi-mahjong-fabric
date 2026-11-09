package com.riichimahjong.fabric;

import net.minecraft.core.*;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import net.minecraft.world.phys.Vec3;

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
    }
    private static Vec3 world(Vec3 local,Vec3 at,float scale,Quaternionf q){var v=q.transform(new Vector3f((float)local.x,(float)local.y,(float)local.z));return at.add(v.x*scale,v.y*scale,v.z*scale);}
}
