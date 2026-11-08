package com.riichimahjong.mahjongaltar.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class MahjongAltarRenderState extends BlockEntityRenderState {
    public final List<ItemStackRenderState> items = new ArrayList<>();
    public final List<Boolean> blockItem = new ArrayList<>();
    public float time;
}
