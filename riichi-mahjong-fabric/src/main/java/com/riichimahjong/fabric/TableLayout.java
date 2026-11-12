package com.riichimahjong.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/** Shared coordinates for public tiles and the owner's private click targets. */
public final class TableLayout {
    static final float TABLE_WIDTH=3;
    public static final float TABLE_HORIZONTAL_SCALE=TABLE_WIDTH/2;
    static final float TILE_ENLARGEMENT=TABLE_HORIZONTAL_SCALE;
    static final float TILE_SCALE=.10f*TILE_ENLARGEMENT;
    static final double TILE_WIDTH=(11.0/16)*TILE_SCALE;
    static final double HAND_STEP=.073;
    static final double TABLE_TOP=9.0/16;
    // Item displays centre model coordinates around (8,8,8), including the 180-degree right rotation.
    static final AABB TILE_MODEL_BOUNDS=new AABB(-5.5/16,-8.0/16,-4.5/16,5.5/16,6.0/16,4.5/16);
    /** The closed virtual shulker and click occlusion share this exact volume. */
    static AABB surface(BlockPos pos){
        double half=TABLE_WIDTH/2;
        return new AABB(pos.getX()+.5-half,pos.getY()+TABLE_TOP-TABLE_WIDTH,pos.getZ()+.5-half,
                pos.getX()+.5+half,pos.getY()+TABLE_TOP,pos.getZ()+.5+half);
    }
    /** Positive along is the seated player's right. The last tile is the actual draw. */
    static double handAlong(int index,int count,boolean drawn){
        return -.44+index*HAND_STEP+(drawn&&index==count-1&&count>1?TILE_WIDTH/TILE_ENLARGEMENT*4/3-HAND_STEP:0);
    }
    /** Sparse slots keep every surviving tile at its old position during the discard delay. */
    static double handAlong(int slot,java.util.List<Integer> gapStarts){
        return -.44+slot*HAND_STEP+gapStarts.stream().filter(start->start<=slot).count()*(TILE_WIDTH/TILE_ENLARGEMENT*4/3-HAND_STEP);
    }
    static Quaternionf textRotation(Direction dir){return PortViews.rotation(dir,false,false).rotateX(-(float)Math.PI/2).rotateZ((float)Math.PI);}
    /** Position the actual model bottom on the felt for both standing and revealed hands. */
    static Vec3 handAnchor(BlockPos pos,Direction dir,double along,boolean flat){
        double support=flat?TILE_MODEL_BOUNDS.maxZ:-TILE_MODEL_BOUNDS.minY;
        return anchor(pos,dir,along,TABLE_TOP,.85).add(0,support*TILE_SCALE,0);
    }
    static Vec3 anchor(BlockPos pos,Direction dir,double along,double height,double distance){
        return PortViews.anchor(pos,dir,along*TILE_ENLARGEMENT,
                TABLE_TOP+(height-TABLE_TOP)*TILE_ENLARGEMENT,distance*TABLE_HORIZONTAL_SCALE);
    }
}
