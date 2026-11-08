package com.riichimahjong.fabric;

import net.minecraft.core.*;
import org.joml.Vector3f;

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
    }
}
