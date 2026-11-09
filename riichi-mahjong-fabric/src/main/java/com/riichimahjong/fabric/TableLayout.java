package com.riichimahjong.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Shared coordinates for public tiles and the owner's private click targets. */
final class TableLayout {
    static final float TABLE_WIDTH=2;
    static final float TABLE_HORIZONTAL_SCALE=1;
    static final float TILE_ENLARGEMENT=1;
    static final float TILE_SCALE=.10f;
    static final double TILE_WIDTH=(11.0/16)*TILE_SCALE;
    static final double HAND_STEP=.073;
    static final double TABLE_TOP=9.0/16;
    /** Positive along is the seated player's right. The last tile is the actual draw. */
    static double handAlong(int index,int count,boolean drawn){
        return -.44+index*HAND_STEP+(drawn&&index==count-1&&count>1?TILE_WIDTH*4/3-HAND_STEP:0);
    }
    /** Sparse slots keep every surviving tile at its old position during the discard delay. */
    static double handAlong(int slot,java.util.List<Integer> gapStarts){
        return -.44+slot*HAND_STEP+gapStarts.stream().filter(start->start<=slot).count()*(TILE_WIDTH*4/3-HAND_STEP);
    }
    static Quaternionf textRotation(Direction dir){return PortViews.rotation(dir,false,false).rotateX(-(float)Math.PI/2).rotateZ((float)Math.PI);}
    static Vec3 anchor(BlockPos pos,Direction dir,double along,double height,double distance){
        return PortViews.anchor(pos,dir,along*TILE_ENLARGEMENT,
                TABLE_TOP+(height-TABLE_TOP)*TILE_ENLARGEMENT,distance*TABLE_HORIZONTAL_SCALE);
    }
}
