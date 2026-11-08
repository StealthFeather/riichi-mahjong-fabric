package com.riichimahjong.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Shared coordinates for public tiles and the owner's private click targets. */
final class TableLayout {
    static final float TABLE_WIDTH=3;
    static final float TABLE_HORIZONTAL_SCALE=1.5f;
    static final float TILE_ENLARGEMENT=1.2f;
    static final float TILE_SCALE=.12f;
    static final double TABLE_TOP=9.0/16;
    static Vec3 anchor(BlockPos pos,Direction dir,double along,double height,double distance){
        return PortViews.anchor(pos,dir,along*TILE_ENLARGEMENT,
                TABLE_TOP+(height-TABLE_TOP)*TILE_ENLARGEMENT,distance*TABLE_HORIZONTAL_SCALE);
    }
}
