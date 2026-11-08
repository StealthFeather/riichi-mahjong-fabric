package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;

public final class HoverHighlightRenderer {
    private static final float LINE_WIDTH = 2.0f;

    private HoverHighlightRenderer() {}

    public static void drawNodeOutline(PoseStack pose, SubmitNodeCollector collector, CuteNode node,
                                       float r, float g, float b, float a) {
        AABB box = node.worldBoundsOrNull();
        if (box == null) return;
        float lift = node.totalVisualLift();
        if (lift != 0f) box = box.move(0, lift, 0);
        drawAabb(pose, collector, box, r, g, b, a);
    }

    public static void drawAabb(PoseStack pose, SubmitNodeCollector collector, AABB box,
                                float r, float g, float b, float a) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        collector.submitCustomGeometry(pose, RenderTypes.lines(), (entry, vc) -> {

        line(vc, entry, x0,y0,z0, x1,y0,z0, r,g,b,a);
        line(vc, entry, x1,y0,z0, x1,y0,z1, r,g,b,a);
        line(vc, entry, x1,y0,z1, x0,y0,z1, r,g,b,a);
        line(vc, entry, x0,y0,z1, x0,y0,z0, r,g,b,a);

        line(vc, entry, x0,y1,z0, x1,y1,z0, r,g,b,a);
        line(vc, entry, x1,y1,z0, x1,y1,z1, r,g,b,a);
        line(vc, entry, x1,y1,z1, x0,y1,z1, r,g,b,a);
        line(vc, entry, x0,y1,z1, x0,y1,z0, r,g,b,a);

        line(vc, entry, x0,y0,z0, x0,y1,z0, r,g,b,a);
        line(vc, entry, x1,y0,z0, x1,y1,z0, r,g,b,a);
        line(vc, entry, x1,y0,z1, x1,y1,z1, r,g,b,a);
        line(vc, entry, x0,y0,z1, x0,y1,z1, r,g,b,a);
        });
    }

    public static void line(VertexConsumer vc, PoseStack.Pose entry,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float r, float g, float b, float a) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-6f) { dx /= len; dy /= len; dz /= len; }
        vc.addVertex(entry, ax, ay, az).setColor(r, g, b, a).setNormal(entry, dx, dy, dz).setLineWidth(LINE_WIDTH);
        vc.addVertex(entry, bx, by, bz).setColor(r, g, b, a).setNormal(entry, dx, dy, dz).setLineWidth(LINE_WIDTH);
    }
}
