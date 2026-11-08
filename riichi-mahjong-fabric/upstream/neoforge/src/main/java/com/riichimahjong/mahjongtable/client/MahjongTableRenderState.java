package com.riichimahjong.mahjongtable.client;

import com.riichimahjong.cuterenderer.CuteNode;
import com.riichimahjong.cuterenderer.CuteRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class MahjongTableRenderState extends BlockEntityRenderState {
    @Nullable public CuteRenderer scene;
    public Direction facing = Direction.NORTH;
    public final List<MahjongTableRenderer.SeatLabel> seatLabels = new ArrayList<>();
    public List<CuteNode> legalDiscardNodes = List.of();
    public List<CuteNode> activeTileNodes = List.of();
    public List<MahjongTableRenderer.ResultLine> resultLines = List.of();
    public boolean editorActive;
}
