package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Transform {
    public final Vector3f pos = new Vector3f();

    public final Quaternionf rot = new Quaternionf();

    public final Vector3f scale = new Vector3f(1f, 1f, 1f);

    private final Vector3f targetPos = new Vector3f();
    private final Quaternionf targetRot = new Quaternionf();
    private final Vector3f targetScale = new Vector3f(1f, 1f, 1f);

    public float easeRate = 14f;

    public Transform() {}

    public Transform setPos(double x, double y, double z) {
        pos.set((float) x, (float) y, (float) z);
        targetPos.set(pos);
        return this;
    }

    public Transform setRotY(float yawDegrees) {
        rot.identity().rotateY((float) Math.toRadians(yawDegrees));
        targetRot.set(rot);
        return this;
    }

    public Transform setRotation(Quaternionf q) {
        rot.set(q);
        targetRot.set(q);
        return this;
    }

    public Transform layFlatReadableFrom(Direction viewer) {
        float yawDeg = switch (viewer) {
            case NORTH -> 180f;
            case EAST  ->  90f;
            case WEST  -> -90f;
            default    ->   0f;
        };
        return setRotation(new Quaternionf()
                .rotateX((float) Math.toRadians(90))
                .rotateLocalY((float) Math.toRadians(yawDeg)));
    }

    public Transform setScale(float s) {
        scale.set(s, s, s);
        targetScale.set(scale);
        return this;
    }

    public Transform setScale(float sx, float sy, float sz) {
        scale.set(sx, sy, sz);
        targetScale.set(scale);
        return this;
    }

    public Transform targetPos(double x, double y, double z) {
        targetPos.set((float) x, (float) y, (float) z);
        return this;
    }

    public Transform targetRotY(float yawDegrees) {
        targetRot.identity().rotateY((float) Math.toRadians(yawDegrees));
        return this;
    }

    public Transform targetRotation(Quaternionf q) {
        targetRot.set(q);
        return this;
    }

    public Transform targetScale(float s) {
        targetScale.set(s, s, s);
        return this;
    }

    public void snapToTarget() {
        pos.set(targetPos);
        rot.set(targetRot);
        scale.set(targetScale);
    }

    public void advance(float dtSeconds) {
        if (easeRate <= 0f) {
            snapToTarget();
            return;
        }
        float t = 1f - (float) Math.exp(-easeRate * dtSeconds);
        pos.lerp(targetPos, t);
        rot.slerp(targetRot, t);
        scale.lerp(targetScale, t);
    }

    public void applyTo(PoseStack poseStack) {
        poseStack.translate(pos.x, pos.y, pos.z);
        poseStack.mulPose(rot);
        poseStack.scale(scale.x, scale.y, scale.z);
    }

    public void applyTo(Matrix4f m) {
        m.translate(pos);
        m.rotate(rot);
        m.scale(scale);
    }

    public Vector3f targetPos() {
        return targetPos;
    }
}
