package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public abstract class CuteNode {
    public final Transform transform = new Transform();
    @Nullable CuteNode parent;
    private final List<CuteNode> children = new ArrayList<>(0);
    private boolean visible = true;

    @Nullable private Interactive interactive;

    float hoverLift = 0f;

    float hoverLiftTarget = 0f;

    private float hoverLiftScale = 1.0f;
    public CuteNode setHoverLiftScale(float s) { this.hoverLiftScale = s; return this; }
    public float hoverLiftScale() { return hoverLiftScale; }

    boolean isHovered;

    private float extraLift = 0f;

    public CuteNode setExtraLift(float v) { this.extraLift = v; return this; }
    public float extraLift() { return extraLift; }
    public float totalVisualLift() { return hoverLift + extraLift; }

    public CuteNode setVisible(boolean v) { this.visible = v; return this; }
    public boolean visible() { return visible; }

    public CuteNode setInteractive(@Nullable Interactive i) { this.interactive = i; return this; }
    @Nullable public Interactive interactive() { return interactive; }

    public List<CuteNode> children() { return children; }
    @Nullable public CuteNode parent() { return parent; }

    public <T extends CuteNode> T addChild(T child) {
        if (child.parent != null) {
            child.parent.children.remove(child);
        }
        child.parent = this;
        children.add(child);
        onChildAdded(child);
        return child;
    }

    public void removeChild(CuteNode child) {
        if (children.remove(child)) {
            child.parent = null;
        }
    }

    protected void onChildAdded(CuteNode child) {}

    public void advance(float dtSeconds) {
        transform.advance(dtSeconds);

        float t = 1f - (float) Math.exp(-22f * dtSeconds);
        hoverLift = hoverLift + (hoverLiftTarget - hoverLift) * t;
        for (CuteNode c : children) {
            c.advance(dtSeconds);
        }
    }

    public final void submit(PoseStack pose, SubmitNodeCollector collector, int packedLight,
                             int packedOverlay) {
        if (!visible) return;
        pose.pushPose();

        float lift = hoverLift + extraLift;
        if (lift != 0f) {
            pose.translate(0f, lift, 0f);
        }
        transform.applyTo(pose);
        submitSelf(pose, collector, packedLight, packedOverlay);
        for (CuteNode c : children) {
            c.submit(pose, collector, packedLight, packedOverlay);
        }
        pose.popPose();
    }

    protected abstract void submitSelf(PoseStack pose, SubmitNodeCollector collector,
                                       int packedLight, int packedOverlay);

    final Matrix4f composeWorldMatrix(Matrix4f out) {
        out.identity();
        composeInto(out);
        return out;
    }

    private void composeInto(Matrix4f out) {
        if (parent != null) parent.composeInto(out);

        transform.applyTo(out);
    }

    @Nullable
    protected AABB naturalLocalAabbOrNull() { return null; }

    @Nullable
    public AABB worldBoundsOrNull() {
        if (!visible) return null;
        AABB lb = interactive != null ? interactive.localBounds() : naturalLocalAabbOrNull();
        if (lb == null) return null;
        Matrix4f m = composeWorldMatrix(new Matrix4f());

        Vector4f c = new Vector4f();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            float x = ((i & 1) == 0) ? (float) lb.minX : (float) lb.maxX;
            float y = ((i & 2) == 0) ? (float) lb.minY : (float) lb.maxY;
            float z = ((i & 4) == 0) ? (float) lb.minZ : (float) lb.maxZ;
            c.set(x, y, z, 1f);
            m.transform(c);
            if (c.x < minX) minX = c.x; if (c.x > maxX) maxX = c.x;
            if (c.y < minY) minY = c.y; if (c.y > maxY) maxY = c.y;
            if (c.z < minZ) minZ = c.z; if (c.z > maxZ) maxZ = c.z;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    final void collectInteractive(List<CuteNode> sink) {
        if (!visible) return;
        if (interactive != null) {
            sink.add(this);
        }
        for (CuteNode c : children) c.collectInteractive(sink);
    }

    public final Vector3f worldAnchor(Vector3f out) {
        Matrix4f m = composeWorldMatrix(new Matrix4f());
        Vector4f v = new Vector4f(0, 0, 0, 1);
        m.transform(v);
        return out.set(v.x, v.y, v.z);
    }
}
