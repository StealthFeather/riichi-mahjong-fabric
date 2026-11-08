package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Camera;
import com.riichimahjong.common.client.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Vector3fc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CuteRenderer {
    private static final Map<Key, CuteRenderer> LIVE = new HashMap<>();

    private final Key key;
    private final GroupNode root = new GroupNode();
    @Nullable private CuteNode hovered;
    @Nullable private CuteNode lastHovered;

    @Nullable private InteractKey lastHoveredKey;
    private long lastFrameNanos = -1L;
    private float hoverLiftAmount = 0.06f;
    @Nullable private SoundEvent hoverSound;
    private float hoverSoundVolume = 0.55f;
    private float hoverSoundPitch = 1.0f;

    public record Key(ResourceKey<Level> dim, BlockPos masterPos) {}

    public CuteRenderer(ResourceKey<Level> dim, BlockPos masterPos) {
        this.key = new Key(dim, masterPos);
    }

    public void attach() {
        LIVE.put(key, this);
    }

    public void detach() {
        LIVE.remove(key, this);
    }

    public Key key() { return key; }
    public GroupNode root() { return root; }

    public void setFacing(Direction facing) {
        float yawDeg = switch (facing) {
            case EAST  -> -90f;
            case SOUTH -> -180f;
            case WEST  -> 90f;
            default    -> 0f;
        };
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(yawDeg));
        Vector3f rotatedCenter = rot.transform(new Vector3f(0.5f, 0f, 0.5f));
        Vector3f offset = new Vector3f(0.5f, 0f, 0.5f).sub(rotatedCenter);
        root.transform.setPos(offset.x, offset.y, offset.z);
        root.transform.setRotation(rot);
    }

    public CuteRenderer setHoverLift(float worldUnits) { this.hoverLiftAmount = worldUnits; return this; }

    public CuteRenderer setHoverSound(@Nullable SoundEvent s, float volume, float pitch) {
        this.hoverSound = s; this.hoverSoundVolume = volume; this.hoverSoundPitch = pitch;
        return this;
    }

    @Nullable public CuteNode hovered() { return hovered; }

    public void extract() {
        long now = System.nanoTime();
        float dt = lastFrameNanos < 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;

        List<CuteNode> interactives = new ArrayList<>();
        root.collectInteractive(interactives);
        updateHover(interactives);
        applyHoverEffects(interactives);
        root.advance(dt);

        InteractKey hoveredKey = hovered != null && hovered.interactive() != null
                ? hovered.interactive().key() : null;
        if (hovered != null && !java.util.Objects.equals(hoveredKey, lastHoveredKey)) {
            playHoverEnter(hovered);
        }
        lastHovered = hovered;
        lastHoveredKey = hoveredKey;
    }

    public void submit(PoseStack pose, SubmitNodeCollector collector, int packedLight, int packedOverlay) {
        root.submit(pose, collector, packedLight, packedOverlay);

        if (hovered != null) {
            HoverHighlightRenderer.drawNodeOutline(pose, collector, hovered, 0f, 0f, 0f, 0.4f);
        }
    }

    private void updateHover(List<CuteNode> candidates) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = ClientCompat.mainCamera(mc);
        if (mc.player == null || mc.level == null) { hovered = null; return; }
        if (!mc.level.dimension().equals(key.dim)) { hovered = null; return; }

        Vec3 origin = cam.position();
        Vector3fc forward = cam.forwardVector();
        Vec3 dir = new Vec3(forward.x(), forward.y(), forward.z());

        Vec3 localOrigin = origin.subtract(
                key.masterPos.getX(), key.masterPos.getY(), key.masterPos.getZ());

        Vec3 endpoint = localOrigin.add(dir.scale(8.0));

        CuteNode best = null;
        double bestT = Double.POSITIVE_INFINITY;
        for (CuteNode n : candidates) {
            AABB world = n.worldBoundsOrNull();
            if (world == null) continue;
            var hit = world.clip(localOrigin, endpoint);
            if (hit.isEmpty()) continue;
            double t = hit.get().distanceTo(localOrigin);
            if (t < bestT) {
                bestT = t;
                best = n;
            }
        }
        hovered = best;
    }

    private void applyHoverEffects(List<CuteNode> all) {
        for (CuteNode n : all) {
            boolean h = (n == hovered);
            n.isHovered = h;
            Interactive i = n.interactive();
            if (i != null && i.policy() == Interactive.HoverPolicy.LIFT_AND_CLICK) {
                n.hoverLiftTarget = h ? hoverLiftAmount * n.hoverLiftScale() : 0f;
            } else {
                n.hoverLiftTarget = 0f;
            }
        }
    }

    private void playHoverEnter(CuteNode n) {
        Interactive i = n.interactive();
        if (i == null || i.policy() != Interactive.HoverPolicy.LIFT_AND_CLICK) return;
        if (hoverSound == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(hoverSound, hoverSoundVolume, hoverSoundPitch);
        }
    }

    @Nullable
    public static CuteRenderer find(ResourceKey<Level> dim, BlockPos masterPos) {
        return LIVE.get(new Key(dim, masterPos));
    }
}
