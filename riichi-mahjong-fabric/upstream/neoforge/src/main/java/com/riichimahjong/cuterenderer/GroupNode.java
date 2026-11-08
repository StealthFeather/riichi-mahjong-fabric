package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

public final class GroupNode extends CuteNode {
    @Override
    protected void submitSelf(PoseStack pose, SubmitNodeCollector collector,
                              int packedLight, int packedOverlay) {
    }
}
