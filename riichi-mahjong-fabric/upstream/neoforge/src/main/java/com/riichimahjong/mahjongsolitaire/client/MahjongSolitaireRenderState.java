package com.riichimahjong.mahjongsolitaire.client;

import com.riichimahjong.cuterenderer.BlockModelNode;
import com.riichimahjong.cuterenderer.CuteRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import org.jetbrains.annotations.Nullable;

public class MahjongSolitaireRenderState extends BlockEntityRenderState {
    @Nullable public CuteRenderer scene;
    @Nullable public BlockModelNode selectedNode;
}
