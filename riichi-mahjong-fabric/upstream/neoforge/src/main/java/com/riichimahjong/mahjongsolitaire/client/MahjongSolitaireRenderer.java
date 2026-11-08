package com.riichimahjong.mahjongsolitaire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.registry.ModSounds;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.cuterenderer.BlockModelNode;
import com.riichimahjong.cuterenderer.CuteNode;
import com.riichimahjong.cuterenderer.CuteRenderer;
import com.riichimahjong.cuterenderer.HoverHighlightRenderer;
import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.cuterenderer.Interactive;
import com.riichimahjong.cuterenderer.WorldButtonNode;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBoard;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public final class MahjongSolitaireRenderer
        implements BlockEntityRenderer<MahjongSolitaireBlockEntity, MahjongSolitaireRenderState> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final float SELECTION_R = 1.00f;
    private static final float SELECTION_G = 0.55f;
    private static final float SELECTION_B = 0.10f;
    private static final float SELECTION_A = 0.85f;

    private static final double TABLETOP_TOP_Y = 1.0 / 16.0;

    private static final double GRID_UNIT_WORLD_X = 1.0 / 17.0;

    private static final double TILE_NATIVE_WIDTH  = 11.0 / 16.0;
    private static final double TILE_NATIVE_DEPTH  = 14.0 / 16.0;
    private static final double TILE_NATIVE_HEIGHT =  9.0 / 16.0;

    private static final double TILE_SCALE =
            GRID_UNIT_WORLD_X * MahjongSolitaireBoard.TILE_GRID / TILE_NATIVE_WIDTH;

    private static final double GRID_UNIT_WORLD_Z =
            TILE_NATIVE_DEPTH * TILE_SCALE / MahjongSolitaireBoard.TILE_GRID;

    private static final DustParticleOptions TILE_DUST =
            new DustParticleOptions(0xF2EBC7, 0.3f);

    public MahjongSolitaireRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    static final class State {
        final CuteRenderer cute;
        final Map<Integer, BlockModelNode> nodes = new HashMap<>();
        final Map<Integer, SlotPos> canonicalPos = new HashMap<>();
        @Nullable String lastBoardId;
        int lastSlotCount = -1;
        long lastRandomSeed = 0L;
        boolean haveRebuiltOnce = false;

        State(MahjongSolitaireBlockEntity be) {
            cute = new CuteRenderer(be.getLevel().dimension(), be.getBlockPos());
            cute.setHoverLift(0.04f);
            cute.setHoverSound(ModSounds.TILE_HOVER_TILE_SOUND.get(), 0.55f, 1.0f);
            cute.attach();
        }
    }

    private static State state(MahjongSolitaireBlockEntity be) {
        if (!(be.clientRenderState instanceof State existing)) {
            State fresh = new State(be);
            be.clientRenderState = fresh;
            addHintButton(fresh);
            return fresh;
        }
        return existing;
    }

    @Override
    public MahjongSolitaireRenderState createRenderState() {
        return new MahjongSolitaireRenderState();
    }

    @Override
    public void extractRenderState(MahjongSolitaireBlockEntity be, MahjongSolitaireRenderState renderState,
                                   float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, renderState, partialTicks, cameraPosition, breakProgress);
        if (be.getLevel() == null) return;

        State state = state(be);
        renderState.scene = state.cute;
        renderState.selectedNode = null;

        MahjongSolitaireBoard board = be.board();
        if (board != null) {
            BlockState blockState = be.getBlockState();
            Direction facing = blockState.hasProperty(BaseMultipartBlock.FACING)
                    ? blockState.getValue(BaseMultipartBlock.FACING)
                    : Direction.NORTH;
            state.cute.setFacing(facing);
            rebuildSceneIfBoardChanged(state, be, board);
            syncTilesToBoardState(state, be, board);
            renderState.selectedNode = selectedNode(state, be);
        }
        state.cute.extract();
    }

    @Override
    public void submit(MahjongSolitaireRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (renderState.scene == null) return;
        renderState.scene.submit(poseStack, collector, renderState.lightCoords, OverlayTexture.NO_OVERLAY);
        if (renderState.selectedNode != null) {
            HoverHighlightRenderer.drawNodeOutline(poseStack, collector, renderState.selectedNode,
                    SELECTION_R, SELECTION_G, SELECTION_B, SELECTION_A);
        }
    }

    @Nullable
    private static BlockModelNode selectedNode(State state, MahjongSolitaireBlockEntity be) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        int selectedSlot = be.selectionFor(mc.player.getUUID());
        if (selectedSlot < 0) return null;
        int tileId = be.tileIdAt(selectedSlot);
        if (tileId < 0) return null;
        return state.nodes.get(tileId);
    }

    private static void rebuildSceneIfBoardChanged(State state, MahjongSolitaireBlockEntity be, MahjongSolitaireBoard board) {
        long seed = be.randomSeed();
        if (state.haveRebuiltOnce
                && board.id().equals(state.lastBoardId)
                && board.slots().size() == state.lastSlotCount
                && seed == state.lastRandomSeed) {
            return;
        }

        for (BlockModelNode tile : state.nodes.values()) state.cute.root().removeChild(tile);
        state.nodes.clear();
        state.canonicalPos.clear();
        state.lastBoardId = board.id();
        state.lastSlotCount = board.slots().size();
        state.lastRandomSeed = seed;
        state.haveRebuiltOnce = true;

        float scale = (float) TILE_SCALE;
        double layerHeight = TILE_NATIVE_HEIGHT * scale;

        double originX = 0.5 - board.width() * GRID_UNIT_WORLD_X * 0.5;
        double originZ = 0.5 - board.depth() * GRID_UNIT_WORLD_Z * 0.5;

        for (int i = 0; i < board.slots().size(); i++) {
            state.canonicalPos.put(i, computeCentroidPos(board.slots().get(i), board, layerHeight, originX, originZ));
        }
    }

    private static void addHintButton(State state) {
        WorldButtonNode btn = new WorldButtonNode(Component.translatable(
                "riichi_mahjong.solitaire.button.hint"))
                .setTextColor(0xFFFFE066)
                .setBgColor(0xC0202020)
                .setTextScale(0.012f)
                .makeClickable(new InteractKey.Named("HINT"));
        btn.transform.layFlatReadableFrom(Direction.NORTH);
        btn.transform.setPos(0.5, TABLETOP_TOP_Y + 0.001, -0.35);
        state.cute.root().addChild(btn);
    }

    private static void syncTilesToBoardState(State state, MahjongSolitaireBlockEntity be, MahjongSolitaireBoard board) {
        HashSet<Integer> presentIds = new HashSet<>();
        float scale = (float) TILE_SCALE;
        for (int slot = 0; slot < be.slotCount(); slot++) {
            com.themahjong.TheMahjongTile tile = be.tileAt(slot);
            if (tile == null) continue;
            int id = be.tileIdAt(slot);
            if (id < 0) continue;
            presentIds.add(id);

            SlotPos rest = state.canonicalPos.get(slot);
            if (rest == null) continue;

            BlockModelNode node = state.nodes.get(id);
            boolean fresh = node == null;
            if (fresh) {
                node = makeTileNode(tile, scale);
                state.nodes.put(id, node);
                state.cute.root().addChild(node);
            }

            var aabb = node.naturalLocalAabb();
            double yMidLocal = (aabb.minY + aabb.maxY) * 0.5;
            double pz = rest.z() - yMidLocal * scale;
            if (fresh) {
                node.transform.setPos(rest.x(), rest.y(), pz);
            } else {
                node.transform.targetPos(rest.x(), rest.y(), pz);
            }
            boolean free = be.isFree(slot);
            node.setInteractive(free
                    ? Interactive.button(new InteractKey.Slot(slot), aabb)
                    : null);
        }

        state.nodes.entrySet().removeIf(entry -> {
            if (!presentIds.contains(entry.getKey())) {
                spawnTileRemovalSparkle(be, entry.getValue());
                state.cute.root().removeChild(entry.getValue());
                return true;
            }
            return false;
        });
    }

    private static void spawnTileRemovalSparkle(MahjongSolitaireBlockEntity be, BlockModelNode node) {
        var box = node.worldBoundsOrNull();
        if (box == null || be.getLevel() == null) return;
        var level = be.getLevel();
        var master = be.getBlockPos();
        double cx = master.getX() + (box.minX + box.maxX) * 0.5;
        double cy = master.getY() + (box.minY + box.maxY) * 0.5;
        double cz = master.getZ() + (box.minZ + box.maxZ) * 0.5;
        for (int i = 0; i < 5; i++) {
            double vx = (level.getRandom().nextDouble() - 0.5) * 0.08;
            double vy = level.getRandom().nextDouble() * 0.05 + 0.01;
            double vz = (level.getRandom().nextDouble() - 0.5) * 0.08;
            level.addParticle(TILE_DUST, cx, cy, cz, vx, vy, vz);
        }
    }

    private static BlockModelNode makeTileNode(com.themahjong.TheMahjongTile tile, float scale) {
        Block tileBlock = MahjongTileItems.blockForTile(tile);
        BlockState state;
        if (tileBlock != null) {
            state = tileBlock.defaultBlockState();
        } else {
            LOGGER.warn("Solitaire: no tile block registered for tile {}; rendering STONE placeholder", tile);
            state = Blocks.STONE.defaultBlockState();
        }
        BlockModelNode node = new BlockModelNode(state);
        node.transform.setScale(scale);

        node.transform.layFlatReadableFrom(Direction.SOUTH);
        return node;
    }

    private static SlotPos computeCentroidPos(MahjongSolitaireBoard.Slot slot, MahjongSolitaireBoard board,
                                       double layerHeight, double originX, double originZ) {
        double cx = originX + ((slot.x() - board.minX()) + MahjongSolitaireBoard.TILE_GRID * 0.5) * GRID_UNIT_WORLD_X;
        double cz = originZ + ((slot.z() - board.minZ()) + MahjongSolitaireBoard.TILE_GRID * 0.5) * GRID_UNIT_WORLD_Z;
        double cy = TABLETOP_TOP_Y + slot.y() * layerHeight + layerHeight * 0.5;
        return new SlotPos(cx, cy, cz);
    }

    private record SlotPos(double x, double y, double z) {}
}
