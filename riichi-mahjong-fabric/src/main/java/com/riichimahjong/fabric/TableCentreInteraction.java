package com.riichimahjong.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Resolve the real centre wind plate before table-wide synthetic interaction hits. */
public final class TableCentreInteraction {
    public enum Action { NONE, MENU, JOIN, ADVANCE }

    private static final double PLATE_TOP = 9.5 / 16;
    private static final double PLATE_HALF_WIDTH = 2.0 / 16 * TableLayout.TABLE_HORIZONTAL_SCALE;

    private TableCentreInteraction() {}

    public static boolean hitsWindPlate(BlockPos master, Vec3 eye, Vec3 end) {
        double height = master.getY() + PLATE_TOP;
        double dy = end.y - eye.y;
        if (eye.y <= height || dy >= -1e-9) return false;
        double progress = (height - eye.y) / dy;
        if (progress < 0 || progress > 1) return false;
        var hit = eye.add(end.subtract(eye).scale(progress));
        return eye.distanceToSqr(hit) <= 64
                && Math.abs(hit.x - master.getX() - .5) <= PLATE_HALF_WIDTH + 1e-8
                && Math.abs(hit.z - master.getZ() - .5) <= PLATE_HALF_WIDTH + 1e-8;
    }

    public static Action resolve(boolean windPlate, boolean sneaking, boolean active, boolean seated) {
        if (!windPlate) return Action.NONE;
        if (sneaking) return Action.MENU;
        return active && seated ? Action.ADVANCE : Action.JOIN;
    }
}
