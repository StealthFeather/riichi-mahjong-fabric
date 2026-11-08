package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import org.joml.Vector3fc;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class BlockModelNode extends CuteNode {
    private static final int[] NO_TINTS = new int[0];

    private final BlockState state;
    @Nullable private List<BlockStateModelPart> cachedParts;
    @Nullable private AABB cachedNaturalAabb;
    private final RandomSource rng = RandomSource.create();

    public BlockModelNode(BlockState state) {
        this.state = state;
    }

    public BlockState state() {
        return state;
    }

    public BlockModelNode makeClickable(InteractKey key) {
        setInteractive(Interactive.button(key, naturalLocalAabb()));
        return this;
    }

    @Override
    protected AABB naturalLocalAabbOrNull() { return naturalLocalAabb(); }

    public AABB naturalLocalAabb() {
        if (cachedNaturalAabb != null) return cachedNaturalAabb;
        float[] bounds = {
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        boolean[] any = {false};
        forEachQuad(q -> {
            for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                Vector3fc v = q.position(i);
                float x = v.x(), y = v.y(), z = v.z();
                if (x < bounds[0]) bounds[0] = x; if (x > bounds[3]) bounds[3] = x;
                if (y < bounds[1]) bounds[1] = y; if (y > bounds[4]) bounds[4] = y;
                if (z < bounds[2]) bounds[2] = z; if (z > bounds[5]) bounds[5] = z;
                any[0] = true;
            }
        });
        cachedNaturalAabb = any[0]
                ? new AABB(bounds[0] - 0.5, bounds[1] - 0.5, bounds[2] - 0.5,
                           bounds[3] - 0.5, bounds[4] - 0.5, bounds[5] - 0.5)
                : new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
        return cachedNaturalAabb;
    }

    private void forEachQuad(Consumer<BakedQuad> consumer) {
        for (BlockStateModelPart part : ensureParts()) {
            for (Direction dir : Direction.values()) {
                for (BakedQuad q : part.getQuads(dir)) consumer.accept(q);
            }
            for (BakedQuad q : part.getQuads(null)) consumer.accept(q);
        }
    }

    private List<BlockStateModelPart> ensureParts() {
        if (cachedParts == null) {
            BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
            List<BlockStateModelPart> parts = new ArrayList<>();
            rng.setSeed(state.getSeed(BlockPos.ZERO));
            model.collectParts(rng, parts);
            cachedParts = parts;
        }
        return cachedParts;
    }

    @Override
    protected void submitSelf(PoseStack pose, SubmitNodeCollector collector,
                              int packedLight, int packedOverlay) {
        pose.pushPose();
        pose.translate(-0.5, -0.5, -0.5);
        collector.submitBlockModel(pose, RenderTypes.entityCutoutCull(TextureAtlas.LOCATION_BLOCKS), ensureParts(),
                NO_TINTS, packedLight, packedOverlay, 0);
        pose.popPose();
    }
}
