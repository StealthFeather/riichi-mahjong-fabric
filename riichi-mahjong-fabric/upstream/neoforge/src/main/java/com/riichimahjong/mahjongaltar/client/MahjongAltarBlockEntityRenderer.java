package com.riichimahjong.mahjongaltar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riichimahjong.mahjongaltar.MahjongAltarBlockEntity;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class MahjongAltarBlockEntityRenderer
        implements BlockEntityRenderer<MahjongAltarBlockEntity, MahjongAltarRenderState> {

    private final ItemModelResolver itemModelResolver;

    public MahjongAltarBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public MahjongAltarRenderState createRenderState() {
        return new MahjongAltarRenderState();
    }

    @Override
    public void extractRenderState(
            MahjongAltarBlockEntity altar,
            MahjongAltarRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(altar, state, partialTicks, cameraPosition, breakProgress);

        List<ItemStack> stacks = altar.renderStacks();
        long gameTime = altar.getLevel() != null ? altar.getLevel().getGameTime() : 0L;
        state.time = gameTime + partialTicks;
        state.items.clear();
        state.blockItem.clear();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            ItemStackRenderState itemState = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(
                    itemState, stack, ItemDisplayContext.GROUND, altar.getLevel(), null, i);
            state.items.add(itemState);
            state.blockItem.add(stack.getItem() instanceof BlockItem);
        }
    }

    @Override
    public void submit(
            MahjongAltarRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        int count = state.items.size();
        for (int i = 0; i < count; i++) {
            poseStack.pushPose();
            float angle = count == 1 ? 0.0f : ((float) (Math.PI * 2.0) * i / (float) count);
            float radius = count == 1 ? 0.0f : 0.22f;
            double x = 0.5 + (Math.cos(angle) * radius);
            double z = 0.5 + (Math.sin(angle) * radius);
            double bob = Math.sin((state.time + (i * 13.0f)) / 8.0f) * 0.03;
            poseStack.translate(x, 1.03 + bob, z);
            float scale = state.blockItem.get(i) ? 0.48f : 0.38f;
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees((state.time * 3.0f) + (i * 45.0f)));
            state.items.get(i).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
