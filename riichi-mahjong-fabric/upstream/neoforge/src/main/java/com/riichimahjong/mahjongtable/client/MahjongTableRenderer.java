package com.riichimahjong.mahjongtable.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.registry.ModSounds;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.common.client.WorldSpaceTextRenderer;
import com.riichimahjong.cuterenderer.BlockModelNode;
import com.riichimahjong.cuterenderer.CuteNode;
import com.riichimahjong.cuterenderer.CuteRenderer;
import com.riichimahjong.cuterenderer.GroupNode;
import com.riichimahjong.cuterenderer.HoverHighlightRenderer;
import com.riichimahjong.cuterenderer.WorldButtonNode;
import com.riichimahjong.cuterenderer.editor.CuteEditor;
import com.riichimahjong.cuterenderer.editor.LayoutEntry;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableBlock;
import com.riichimahjong.mahjongcore.MahjongWinEffects;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity.ResultAnimStage;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.yaku.TenpaiChecker;
import com.themahjong.yaku.WinResult;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

public class MahjongTableRenderer implements BlockEntityRenderer<MahjongTableBlockEntity, MahjongTableRenderState> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final List<Direction> HORIZONTAL = List.of(
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    private static final String[] WIND_KEY_BY_SEAT = {
            "riichi_mahjong.result.wind.east",
            "riichi_mahjong.result.wind.south",
            "riichi_mahjong.result.wind.west",
            "riichi_mahjong.result.wind.north",
    };

    private static Component actionLabel(String key) {
        return Component.translatable("riichi_mahjong.button.action." + key);
    }

    private static final WorldSpaceTextRenderer.Style STYLE_NORMAL =
            new WorldSpaceTextRenderer.Style(0.0105f, 1.10f, 0xFFE8E8E8, 0x80101010);
    private static final WorldSpaceTextRenderer.Style STYLE_TURN =
            new WorldSpaceTextRenderer.Style(0.0105f, 1.10f, 0xFFF0D040, 0x80101010);

    private static final double EDGE_PUSH = 1.01;
    private static final double TEXT_Y = 0.55;

    private static final float HIGHLIGHT_R = 1.0f;
    private static final float HIGHLIGHT_G = 0.55f;
    private static final float HIGHLIGHT_B = 0.10f;
    private static final float HIGHLIGHT_A = 1.0f;

    private static final int HAND_MAX_TILES   = 14;
    private static final int WALL_DEFAULT     = 17;
    private static final int RIVER_COLS       = 6;
    private static final int RIVER_ROWS       = 4;
    private static final int MELDS_PER_SEAT   = 4;
    private static final int TILES_PER_MELD   = 3;
    private static final int KITA_DEFAULT     = 4;
    private static final TheMahjongTile NORTH_TILE =
            new TheMahjongTile(TheMahjongTile.Suit.WIND, TheMahjongTile.Wind.NORTH.tileRank(), false);

    private static final LayoutEntry HAND = CuteEditor.layout(
                    "hand",
                    new Vector3f(-0.44f, 0.65f, 0.85f), 0.10f)
            .floatField("spacing", 0.073f, 0f, 0.5f)
            .floatField("pivot",   0.0f,   0f, 1f)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,    0f,   1f);

    private static final LayoutEntry WALL = CuteEditor.layout(
                    "wall",
                    new Vector3f(-0.59f, 0.60f, 0.70f), 0.10f)
            .floatField("spacing",  0.073f, 0f, 0.5f)
            .floatField("pivot",    0.0f,   0f, 1f)
            .floatField("stackGap", 0.055f, 0f, 0.2f)
            .intField("count",       WALL_DEFAULT, 0, 36)
            .intField("stackHeight", 2, 1, 4)
            .intField("dealtTiles",  53, 0, 122)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,    0f,   1f);

    private static final LayoutEntry DEAD_WALL = CuteEditor.layout(
                    "deadWall",
                    new Vector3f(0.5900f, 0.6000f, 0.7000f), 0.1000f)
            .floatField("spacing",  0.073f, 0f, 0.5f)
            .floatField("pivot",    0.0f,   0f, 1f)
            .floatField("stackGap", 0.055f, 0f, 0.2f)
            .intField("count",        7,    0, 14)
            .intField("stackHeight",  2,    1, 4)
            .intField("kanDoras",     0,    0, 4)
            .intField("uraDora",      0,    0, 1)
            .intField("flipDir",      1,    0, 1)
            .intField("rinshanDrawn", 0,    0, 4)
            .floatField("uraDoraFraction", 0.95f, 0f, 1f)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,    0f,   1f);

    private static final LayoutEntry RIVER = CuteEditor.layout(
                    "river",
                    new Vector3f(0f, 0.59f, 0.26f), 0.10f)
            .floatField("spacing",    0.073f, 0f, 0.5f)
            .floatField("rowSpacing", 0.090f, 0f, 0.5f)
            .floatField("pivot",      0.5f,   0f, 1f)
            .intField("cols", RIVER_COLS, 0, 12)
            .intField("rows", RIVER_ROWS, 0, 8)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,   0f,   1f);

    private static final LayoutEntry MELDS = CuteEditor.layout(
                    "melds",
                    new Vector3f(0.90f, 0.59f, 0.89f), 0.10f)
            .floatField("spacing", 0.073f, 0f, 0.5f)
            .floatField("meldGap", 0.020f, 0f, 0.2f)
            .floatField("pivot",   0.0f,   0f, 1f)
            .intField("meldCount",    MELDS_PER_SEAT, 0, 4)
            .intField("tilesPerMeld", 4,              0, 4)
            .intField("flipDir",      1,              0, 1)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,   0f,   1f);

    private static final LayoutEntry KITA = CuteEditor.layout(
                    "kita",
                    new Vector3f(-0.53f, 0.59f, 0.57f), 0.10f)
            .floatField("spacing", 0.073f, 0f, 0.5f)
            .floatField("pivot",   0.0f,   0f, 1f)
            .intField("count", KITA_DEFAULT, 0, 8)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f)
            .floatField("fill",      1f,   0f,   1f);

    private static final LayoutEntry BUTTONS = CuteEditor.layout(
                    "buttons",
                    new Vector3f(0f, 0.71f, 0.70f), 0.007f)
            .floatField("colSpacing", 0.37f, 0f, 0.6f)
            .floatField("rowSpacing", 0.10f, 0f, 0.6f)
            .floatField("colPivot",   0.5f,  0f, 1f)
            .floatField("rowPivot",   0.0f,  0f, 1f)
            .intField("cols", 4, 1, 8)
            .intField("rows", 2, 1, 4)
            .floatField("tileYaw",     0f, -180f, 180f)
            .floatField("tilePitch",   0f, -180f, 180f)
            .floatField("tileRoll",  180f, -180f, 180f)

            .floatField("rightPadPerTile", 7f, 0f, 40f);

    private static final LayoutEntry BUTTON_GHOSTS = CuteEditor.layout(
                    "buttonGhosts",
                    new Vector3f(0.01f, 0.006f, 0f), 0.07f)
            .floatField("spacing",   0.05f, 0f, 0.3f)
            .floatField("tileYaw",   0f, -180f, 180f)
            .floatField("tilePitch", 0f, -180f, 180f)
            .floatField("tileRoll",  0f, -180f, 180f);

    private static final LayoutEntry[] ALL_ANCHORS = {HAND, WALL, DEAD_WALL, RIVER, MELDS, KITA, BUTTONS, BUTTON_GHOSTS};

    private static final LayoutEntry[] WORLD_ANCHORS = {HAND, WALL, DEAD_WALL, RIVER, MELDS, KITA, BUTTONS};

    private record PreviewBtn(Component label, int textColor, int bgColor, boolean pulsing) {}

    private static final PreviewBtn[] PREVIEW_BUTTONS = {
        new PreviewBtn(actionLabel("chi"),     0xFFFF8E8E, 0xB0444A58, false),
        new PreviewBtn(actionLabel("chi"),     0xFFE8E8E8, 0xB0444A58, false),
        new PreviewBtn(actionLabel("chi"),     0xFFE8E8E8, 0xB0444A58, false),
        new PreviewBtn(actionLabel("pon"),     0xFFE8E8E8, 0xB0444A58, false),
        new PreviewBtn(actionLabel("kan"),     0xFFE8E8E8, 0xB0444A58, false),
        new PreviewBtn(actionLabel("ron"),     0xFFFF5757, 0xC05A2222, true),
        new PreviewBtn(actionLabel("chankan"), 0xFFFF5757, 0xC05A2222, true),
        new PreviewBtn(actionLabel("pass"),    0xFFBFC6D2, 0xB0393D48, false),
    };

    private static final List<List<TheMahjongTile>> PREVIEW_GHOSTS = List.of(
        List.of(
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 4, false),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, true),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 6, false)),
        List.of(
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 4, false),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, false),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 6, false)),
        List.of(
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, false),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 6, false),
            new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 7, false)),
        List.of(
            new TheMahjongTile(TheMahjongTile.Suit.PINZU, 7, false),
            new TheMahjongTile(TheMahjongTile.Suit.PINZU, 7, false),
            new TheMahjongTile(TheMahjongTile.Suit.PINZU, 7, false)),
        List.of(
            new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false),
            new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false),
            new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false),
            new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false)),
        List.of(),
        List.of(),
        List.of()
    );

    private final Font font;

    private static final long CLICK_SOUND_MIN_INTERVAL_NANOS = 80_000_000L;

    static final class State {
        @Nullable CuteRenderer cute;

        final GroupNode[] seatRoots = new GroupNode[4];
        @Nullable TheMahjongMatch lastMatch;
        @Nullable Direction lastFacing;
        boolean lastEditorActive;
        final java.util.List<CuteNode> activeTileNodes = new java.util.ArrayList<>();
        final java.util.List<CuteNode> legalDiscardNodes = new java.util.ArrayList<>();
        final int[] lastSeenDiscardCount = new int[4];
        final int[] lastSeenMeldCount = new int[4];
        int lastSeenLiveWallSize = -1;
        int lastDiscarderSeat = -1;
        int lastLocalSeat = -1;
        boolean lastLocalDiscardTurn;
        boolean lastDrawnInLocalInv;
        @Nullable MatchPhase.Stage lastObservedDealingStage;
        long localDealingStageStartNanos;
        long lastClickSoundNanos;
        int lastFrameRingStacks;
        int lastFrameHandTiles;
        long lastParticleTick = Long.MIN_VALUE;
    }

    private static State stateFor(MahjongTableBlockEntity table) {
        Object obj = table.clientRenderState;
        if (obj instanceof State s) return s;
        State s = new State();
        s.cute = new CuteRenderer(table.getLevel().dimension(), table.getBlockPos());
        s.cute.setHoverSound(ModSounds.TILE_HOVER_ACTION_SOUND.get(), 0.55f, 1.0f);
        s.cute.attach();
        table.clientRenderState = s;
        return s;
    }

    public MahjongTableRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.font();
    }

    @Override
    public MahjongTableRenderState createRenderState() {
        return new MahjongTableRenderState();
    }

    @Override
    public void extractRenderState(MahjongTableBlockEntity table, MahjongTableRenderState renderState,
                                   float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(table, renderState, partialTicks, cameraPosition, breakProgress);
        Level level = table.getLevel();
        if (level == null) return;

        Direction facing = table.getBlockState().getValue(BaseMultipartBlock.FACING);
        List<MahjongTableBlockEntity.SeatInfo> seats = table.seats();
        int turnSeat = currentTurnSeat(table);

        renderState.facing = facing;
        renderState.seatLabels.clear();
        for (Direction cellDir : HORIZONTAL) {
            int seat = MahjongTableBlock.seatForDirection(cellDir, facing);
            if (seat >= seats.size()) continue;
            MahjongTableBlockEntity.SeatInfo info = seats.get(seat);
            List<Component> lines = info.enabled()
                    ? List.of(buildWindLine(seat, table), buildNameLine(info, level))
                    : List.of(Component.literal("CLOSED"));
            renderState.seatLabels.add(new SeatLabel(
                    cellDir, lines, seat == turnSeat ? STYLE_TURN : STYLE_NORMAL));
        }

        State state = stateFor(table);
        rebuildSceneIfStale(state, table, facing, CuteEditor.isActive());
        state.cute.extract();

        renderState.scene = state.cute;
        renderState.legalDiscardNodes = List.copyOf(state.legalDiscardNodes);
        renderState.activeTileNodes = List.copyOf(state.activeTileNodes);
        renderState.resultLines = table.isInResultPhase() ? buildResultLines(table) : List.of();
        renderState.editorActive = CuteEditor.isActive();

        if (table.driver() != null
                && !(table.driver().currentPhase() instanceof MatchPhase.Dealing)
                && !table.isInResultPhase()) {
            long gt = level.getGameTime();
            if (gt != state.lastParticleTick) {
                state.lastParticleTick = gt;
                emitSeatParticles(table, level, gt, facing);
            }
        }
    }

    @Override
    public void submit(MahjongTableRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (renderState.scene == null) return;
        int light = renderState.lightCoords;

        for (SeatLabel label : renderState.seatLabels) {
            Direction cellDir = label.cellDir();
            WorldSpaceTextRenderer.drawOutwardLines(
                    poseStack, collector, light, font,
                    0.5 + cellDir.getStepX() * EDGE_PUSH, TEXT_Y, 0.5 + cellDir.getStepZ() * EDGE_PUSH,
                    cellDir, label.style(), label.lines());
        }

        renderState.scene.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY);

        for (CuteNode node : renderState.legalDiscardNodes) {
            HoverHighlightRenderer.drawNodeOutline(poseStack, collector, node,
                    HIGHLIGHT_R, HIGHLIGHT_G, HIGHLIGHT_B, HIGHLIGHT_A);
        }
        for (CuteNode node : renderState.activeTileNodes) {
            HoverHighlightRenderer.drawNodeOutline(poseStack, collector, node,
                    HIGHLIGHT_R, HIGHLIGHT_G, HIGHLIGHT_B, HIGHLIGHT_A);
        }

        if (!renderState.resultLines.isEmpty()) {
            submitResultOverlay(renderState.resultLines, poseStack, collector, light, camera);
        }

        if (renderState.editorActive) {
            String selected = CuteEditor.selectedName();
            for (int seat = 0; seat < 4; seat++) {
                Direction outward = outwardForSeat(seat, renderState.facing);
                if (outward == null) continue;
                for (LayoutEntry e : WORLD_ANCHORS) {
                    double[] c = anchorWorldOrigin(e, outward);
                    CuteEditor.drawTriad(poseStack, collector, c[0], c[1], c[2],
                            e.name().equals(selected));
                }
            }
        }
    }

    record SeatLabel(Direction cellDir, List<Component> lines, WorldSpaceTextRenderer.Style style) {}

    private static final float RESULT_TEXT_SCALE = 0.0100f;

    private static final int RESULT_TEXT_BG = 0x80101010;

    private static final int COLOR_ROUND_TITLE = 0xFFE8E8E8;
    private static final int COLOR_VERDICT_NORMAL  = 0xFFFF6A6A;
    private static final int COLOR_VERDICT_YAKUMAN = 0xFFFFD700;
    private static final int COLOR_HEADER_DRAW     = 0xFFA0E8F7;
    private static final int COLOR_YAKU_LINE      = 0xFFE0E0E0;
    private static final int COLOR_DELTA_LINE     = 0xFFE8F7FF;

    private List<ResultLine> buildResultLines(MahjongTableBlockEntity table) {
        TheMahjongDriver driver = table.driver();
        if (driver == null) return List.of();
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded re)) return List.of();
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (round == null) return List.of();

        ResultAnimStage stage = table.resultAnimStage();
        int yakuRevealed = table.resultAnimYakuIdx();
        boolean showFinal = stage == ResultAnimStage.SHOW_FINAL || stage == ResultAnimStage.AWAITING_ADVANCE;

        java.util.List<ResultLine> lines = new java.util.ArrayList<>();

        lines.add(new ResultLine(buildRoundTitle(round), COLOR_ROUND_TITLE));

        if (re.winResults().isEmpty()) {
            lines.add(new ResultLine(
                    Component.translatable("riichi_mahjong.result.draw.no_winner"),
                    COLOR_HEADER_DRAW));
            if (showFinal) {
                lines.add(new ResultLine(
                        Component.translatable("riichi_mahjong.result.draw.all_zero"),
                        COLOR_HEADER_DRAW));
            }
        } else {
            WinResult primary = re.winResults().get(0);
            boolean yakuman = !primary.yakuman().isEmpty();

            int idx = 0;
            for (var y : primary.yakuman()) {
                if (idx++ >= yakuRevealed) break;
                lines.add(new ResultLine(yakuComponent(y.name()), COLOR_YAKU_LINE));
            }
            for (var y : primary.yaku()) {
                if (idx++ >= yakuRevealed) break;
                lines.add(new ResultLine(
                        Component.translatable("riichi_mahjong.result.yaku_line",
                                yakuComponent(y.name()),
                                Component.literal(String.valueOf(y.closedHan()))),
                        COLOR_YAKU_LINE));
            }

            if (!yakuman && primary.doraCount() > 0 && idx++ < yakuRevealed) {
                lines.add(new ResultLine(
                        Component.translatable("riichi_mahjong.result.dora_line",
                                Component.translatable("riichi_mahjong.yaku.dora"),
                                Component.literal(String.valueOf(primary.doraCount()))),
                        COLOR_YAKU_LINE));
            }

            if (showFinal) {
                lines.add(new ResultLine(verdictComponent(primary.han(), yakuman),
                        yakuman ? COLOR_VERDICT_YAKUMAN : COLOR_VERDICT_NORMAL));
                for (int i = 0; i < primary.pointDeltas().size(); i++) {
                    int d = primary.pointDeltas().get(i);
                    if (d == 0) continue;
                    String sign = d > 0 ? "+" : "";
                    lines.add(new ResultLine(
                            Component.translatable("riichi_mahjong.result.delta_line",
                                    seatName(table, i),
                                    Component.literal(sign),
                                    Component.literal(String.valueOf(d))),
                            COLOR_DELTA_LINE));
                }
            }
        }

        return lines;
    }

    private void submitResultOverlay(List<ResultLine> lines, PoseStack poseStack,
                                     SubmitNodeCollector collector, int packedLight,
                                     CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5, 1.65, 0.5);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-camera.yRot));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(camera.xRot));
        poseStack.scale(-RESULT_TEXT_SCALE, -RESULT_TEXT_SCALE, RESULT_TEXT_SCALE);
        float y = 0f;
        for (ResultLine line : lines) {
            int w = font.width(line.text());
            WorldSpaceTextRenderer.submitLine(poseStack, collector, packedLight, font,
                    line.text().getVisualOrderText(), -w / 2f, y, line.color(), RESULT_TEXT_BG);
            y += font.lineHeight + 1f;
        }
        poseStack.popPose();
    }

    record ResultLine(Component text, int color) {}

    private static int packedColor(org.joml.Vector3f color) {
        int r = Math.round(color.x() * 255f);
        int g = Math.round(color.y() * 255f);
        int b = Math.round(color.z() * 255f);
        return (r << 16) | (g << 8) | b;
    }

    private static int winnerSeatOf(WinResult wr) {
        for (int i = 0; i < wr.pointDeltas().size(); i++) {
            if (wr.pointDeltas().get(i) > 0) return i;
        }
        return -1;
    }

    private static List<TheMahjongTile> winnerDisplayOrder(List<TheMahjongTile> hand) {
        if (hand.size() <= 13) {
            List<TheMahjongTile> sorted = new java.util.ArrayList<>(hand);
            sorted.sort(TheMahjongTile.DISPLAY_ORDER);
            return sorted;
        }
        TheMahjongTile winning = hand.get(hand.size() - 1);
        List<TheMahjongTile> rest = new java.util.ArrayList<>(hand.subList(0, hand.size() - 1));
        rest.sort(TheMahjongTile.DISPLAY_ORDER);
        rest.add(winning);
        return rest;
    }

    private static boolean isWinningSeat(TheMahjongDriver driver, int seat) {
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded re)) return false;
        for (WinResult wr : re.winResults()) {
            if (winnerSeatOf(wr) == seat) return true;
        }
        return false;
    }

    private static boolean shouldRevealUraDora(TheMahjongDriver driver, List<TheMahjongPlayer> players) {
        if (!(driver.currentPhase() instanceof MatchPhase.RoundEnded re)) return false;
        for (WinResult wr : re.winResults()) {
            int seat = winnerSeatOf(wr);
            if (seat >= 0 && seat < players.size() && players.get(seat).riichi()) return true;
        }
        return false;
    }

    private record ActiveHighlight(int handSeat, int riverSeat) {
        static final ActiveHighlight NONE = new ActiveHighlight(-1, -1);
        static ActiveHighlight hand(int seat)  { return new ActiveHighlight(seat, -1); }
        static ActiveHighlight river(int seat) { return new ActiveHighlight(-1, seat); }
    }

    private static ActiveHighlight computeActiveHighlight(
            TheMahjongDriver driver,
            com.themahjong.TheMahjongRound round,
            int lastDiscarderSeat) {
        if (round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn) {
            return ActiveHighlight.hand(round.currentTurnSeat());
        }

        if (round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldDiscard
                || round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKita
                || round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKakan) {
            return lastDiscarderSeat >= 0 ? ActiveHighlight.river(lastDiscarderSeat) : ActiveHighlight.NONE;
        }

        if (driver.currentPhase() instanceof MatchPhase.RoundEnded re && !re.winResults().isEmpty()) {
            for (WinResult wr : re.winResults()) {
                int seat = winnerSeatOf(wr);
                if (seat >= 0 && seat < round.players().size()
                        && round.players().get(seat).currentHand().size() > 13) {
                    return ActiveHighlight.hand(seat);
                }
            }

            if (lastDiscarderSeat >= 0) return ActiveHighlight.river(lastDiscarderSeat);
        }
        return ActiveHighlight.NONE;
    }

    private static Component buildRoundTitle(TheMahjongRound round) {
        Component wind = Component.translatable(
                "riichi_mahjong.result.wind." + round.roundWind().name().toLowerCase(java.util.Locale.ROOT));
        Component handNum = Component.literal(String.valueOf(round.handNumber()));
        if (round.honba() > 0) {
            return Component.translatable("riichi_mahjong.result.round_label_honba",
                    wind, handNum, Component.literal(String.valueOf(round.honba())));
        }
        return Component.translatable("riichi_mahjong.result.round_label", wind, handNum);
    }

    private Component seatName(MahjongTableBlockEntity table, int seat) {
        var seats = table.seats();
        if (seat >= 0 && seat < seats.size()) {
            var occ = seats.get(seat).occupant();
            if (occ.isPresent()) {
                Level lvl = table.getLevel();
                if (lvl != null) {
                    Player p = lvl.getPlayerByUUID(occ.get());
                    if (p != null) return p.getDisplayName().copy();
                }
            }
        }

        String key = switch (seat) {
            case 0 -> "riichi_mahjong.result.wind.east";
            case 1 -> "riichi_mahjong.result.wind.south";
            case 2 -> "riichi_mahjong.result.wind.west";
            case 3 -> "riichi_mahjong.result.wind.north";
            default -> null;
        };
        return key != null ? Component.translatable(key) : Component.literal("Seat " + seat);
    }

    private static Component yakuComponent(String rawName) {
        String canonical = MahjongWinEffects.canonicalYakuKey(rawName);
        if (canonical == null || canonical.isEmpty()) {
            return Component.literal(MahjongWinEffects.humanizeYakuName(rawName));
        }
        return Component.translatableWithFallback(
                "riichi_mahjong.yaku." + canonical,
                MahjongWinEffects.humanizeYakuName(rawName));
    }

    private static Component verdictComponent(int han, boolean yakuman) {
        if (yakuman)   return Component.translatable("riichi_mahjong.result.verdict.yakuman");
        if (han >= 13) return Component.translatable("riichi_mahjong.result.verdict.kazoe_yakuman");
        if (han >= 11) return Component.translatable("riichi_mahjong.result.verdict.sanbaiman");
        if (han >= 8)  return Component.translatable("riichi_mahjong.result.verdict.baiman");
        if (han >= 6)  return Component.translatable("riichi_mahjong.result.verdict.haneman");
        if (han >= 5)  return Component.translatable("riichi_mahjong.result.verdict.mangan");
        return Component.translatable("riichi_mahjong.result.verdict.han",
                Component.literal(String.valueOf(han)));
    }

    private void rebuildSceneIfStale(State state, MahjongTableBlockEntity table, Direction facing, boolean editorActive) {
        TheMahjongDriver driver = table.driver();
        TheMahjongMatch match = driver == null ? null : driver.match();
        Level level = table.getLevel();

        boolean inDealing = driver != null && driver.currentPhase() instanceof MatchPhase.Dealing;
        int localSeat = localSeat(table);
        boolean localDiscardTurn = driver != null
                && driver.currentPhase() instanceof MatchPhase.AwaitingDiscard ad
                && ad.seat() == localSeat;

        boolean drawnInLocalInv = localSeat >= 0 && table.drawnTileDeliveredForSeat(localSeat);
        if (!editorActive && !inDealing
                && match == state.lastMatch
                && facing == state.lastFacing
                && editorActive == state.lastEditorActive
                && localSeat == state.lastLocalSeat
                && localDiscardTurn == state.lastLocalDiscardTurn
                && drawnInLocalInv == state.lastDrawnInLocalInv) {
            return;
        }
        state.lastMatch = match;
        state.lastFacing = facing;
        state.lastEditorActive = editorActive;
        state.lastLocalSeat = localSeat;
        state.lastLocalDiscardTurn = localDiscardTurn;
        state.lastDrawnInLocalInv = drawnInLocalInv;

        for (int seat = 0; seat < state.seatRoots.length; seat++) {
            if (state.seatRoots[seat] != null) {
                state.cute.root().removeChild(state.seatRoots[seat]);
                state.seatRoots[seat] = null;
            }
        }
        state.activeTileNodes.clear();
        state.legalDiscardNodes.clear();

        if (editorActive) {
            for (int seat = 0; seat < 4; seat++) {
                Direction outward = outwardForSeat(seat, facing);
                if (outward == null) continue;
                state.seatRoots[seat] = buildPreviewSeat(seat, outward);
                state.cute.root().addChild(state.seatRoots[seat]);
            }
            return;
        }

        if (driver == null) return;
        var roundOpt = match.currentRound();
        if (roundOpt.isEmpty()) return;
        TheMahjongRound round = roundOpt.get();
        List<TheMahjongPlayer> players = round.players();

        boolean playDiscardSound = false;
        boolean playMeldSound = false;
        boolean playDrawSound = false;
        for (int i = 0; i < players.size() && i < state.lastSeenDiscardCount.length; i++) {
            int discNow = players.get(i).discards().size();
            if (discNow > state.lastSeenDiscardCount[i]) {
                state.lastDiscarderSeat = i;
                playDiscardSound = true;
            }
            state.lastSeenDiscardCount[i] = discNow;
            int meldNow = players.get(i).melds().size();
            if (meldNow > state.lastSeenMeldCount[i]) playMeldSound = true;
            state.lastSeenMeldCount[i] = meldNow;
        }
        int liveWallNow = round.liveWall().size();
        if (state.lastSeenLiveWallSize >= 0 && liveWallNow < state.lastSeenLiveWallSize) {
            playDrawSound = true;
        }
        state.lastSeenLiveWallSize = liveWallNow;

        if (!(driver.currentPhase() instanceof MatchPhase.Dealing)) {
            if (playDiscardSound || playMeldSound) {
                playClickSound(state, level, table.getBlockPos(),
                        ModSounds.TILE_PLACE_SOUND.get(), 0.55f);
            } else if (playDrawSound) {
                playClickSound(state, level, table.getBlockPos(),
                        ModSounds.TILE_PLACE_SOUND.get(), 0.35f);
            }
        }

        MatchPhase.Stage dealingStage = null;

        double buildProgress = 1.0;
        if (driver.currentPhase() instanceof MatchPhase.Dealing dealing) {
            dealingStage = dealing.stage();

            if (state.lastObservedDealingStage != dealingStage) {
                state.lastObservedDealingStage = dealingStage;
                state.localDealingStageStartNanos = System.nanoTime();
                state.lastFrameRingStacks = 0;
                state.lastFrameHandTiles = 0;
                playStageEntrySound(level, table.getBlockPos(), dealingStage);
            }
            double localElapsed = (System.nanoTime() - state.localDealingStageStartNanos) / 1_000_000_000.0;
            double buildDuration = dealingStage.buildDuration();
            buildProgress = buildDuration <= 0 ? 1.0 : Math.min(1.0, localElapsed / buildDuration);
        } else {
            state.lastObservedDealingStage = null;
        }
        double handFraction  = handVisibleFraction(dealingStage, buildProgress);
        boolean doraFaceUp   = dealingStage == null || dealingStage == MatchPhase.Stage.DORA_FLIP
                || dealingStage.ordinal() > MatchPhase.Stage.DORA_FLIP.ordinal();

        double doraFlipProgress = dealingStage == MatchPhase.Stage.DORA_FLIP
                ? buildProgress
                : (doraFaceUp ? 1.0 : 0.0);

        ActiveHighlight highlight = computeActiveHighlight(driver, round, state.lastDiscarderSeat);

        TheMahjongTile faceDownPlaceholder = FACE_DOWN_PLACEHOLDER;
        int wallStackCount = WALL.i("count");
        int deadStackCount = DEAD_WALL.i("count");
        int wallStackHeight = Math.max(1, WALL.i("stackHeight"));

        int liveWallTotalTiles =
                (wallStackCount - deadStackCount) * wallStackHeight
                + 3 * wallStackCount * wallStackHeight;
        int dealtTiles = computeDealtTiles(dealingStage, buildProgress, liveWallTotalTiles, round);

        int totalRingStacks = wallStackCount * 4;
        int ringStacksBuilt = dealingStage == MatchPhase.Stage.WALL_BUILDING
                ? Math.min(totalRingStacks, (int) Math.floor(buildProgress * totalRingStacks))
                : totalRingStacks;

        if (dealingStage == MatchPhase.Stage.WALL_BUILDING && ringStacksBuilt > state.lastFrameRingStacks) {
            playClickSound(state, level, table.getBlockPos(),
                    ModSounds.TILE_PLACE_SOUND.get(), 0.45f);
            state.lastFrameRingStacks = ringStacksBuilt;
        }
        int seat0DeadMaxTiles = deadStackCount * wallStackHeight;

        int handTilesThisFrame = 0;
        for (int seat = 0; seat < 4; seat++) {
            Direction outward = outwardForSeat(seat, facing);
            if (outward == null) continue;
            GroupNode root = new GroupNode();

            int seatVisibleTiles;
            if (dealingStage == MatchPhase.Stage.WALL_BUILDING) {
                int seatStart = seat * wallStackCount;
                int seatStacksThisFrame = Math.max(0,
                        Math.min(wallStackCount, ringStacksBuilt - seatStart));
                if (seat == 0) {
                    int liveStacksMax = wallStackCount - deadStackCount;
                    int liveStacks = Math.min(seatStacksThisFrame, liveStacksMax);
                    seatVisibleTiles = liveStacks * wallStackHeight;
                } else {
                    seatVisibleTiles = seatStacksThisFrame * wallStackHeight;
                }
            } else {
                seatVisibleTiles = liveTilesAfterDeal(
                        seat, wallStackCount, deadStackCount, dealtTiles);
            }
            if (seatVisibleTiles > 0) {
                int positions = (seatVisibleTiles + wallStackHeight - 1) / wallStackHeight;
                int lastStackLayers = positions > 0
                        ? seatVisibleTiles - (positions - 1) * wallStackHeight
                        : -1;
                root.addChild(buildLineGroup(WALL,
                        placeholderTiles(positions, faceDownPlaceholder),
                        outward,  true,
                         -1,  -1,  0,
                         lastStackLayers));
            }

            if (seat == 0) {
                int deadVisibleTiles;
                if (dealingStage == MatchPhase.Stage.WALL_BUILDING) {
                    int liveStacksMax = wallStackCount - deadStackCount;
                    int seat0Stacks = Math.max(0, Math.min(wallStackCount, ringStacksBuilt));
                    int deadStacks = Math.max(0, seat0Stacks - liveStacksMax);
                    deadVisibleTiles = deadStacks * wallStackHeight;
                } else {
                    deadVisibleTiles = seat0DeadMaxTiles;
                }
                if (deadVisibleTiles > 0) {
                    boolean revealUra = shouldRevealUraDora(driver, players);
                    root.addChild(buildLiveDeadWall(DEAD_WALL, outward, faceDownPlaceholder,
                            doraFlipProgress, deadVisibleTiles, round, revealUra));
                }
            }

            if (seat < players.size() && handFraction > 0) {
                boolean revealWinnerFlat = table.isInResultPhase()
                        && isWinningSeat(driver, seat);

                List<TheMahjongTile> rendered;
                if (revealWinnerFlat) {
                    rendered = winnerDisplayOrder(players.get(seat).currentHand());
                } else {
                    rendered = round.handDisplayOrder(seat);
                }
                boolean drawnStripped = false;
                if (!revealWinnerFlat
                        && seat == localSeat
                        && driver.currentPhase() instanceof MatchPhase.AwaitingDiscard ad
                        && ad.seat() == seat
                        && round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn
                        && table.drawnTileDeliveredForSeat(seat)) {
                    rendered = rendered.subList(0, rendered.size() - 1);
                    drawnStripped = true;
                }
                int n = Math.max(0, Math.min(rendered.size(),
                        (int) Math.round(rendered.size() * handFraction)));
                if (n > 0) {
                    GroupNode handGroup = buildLineGroup(HAND,
                            rendered.subList(0, n), outward,  revealWinnerFlat);

                    java.util.Set<TheMahjongTile> legalDiscardTiles = java.util.Set.of();
                    if (seat == localSeat && localDiscardTurn) {
                        legalDiscardTiles = collectLegalDiscardTiles(
                                driver.legalActions(seat), table.riichiPendingForSeat(seat));
                        int tileIdx = 0;
                        int handChildIdx = 0;
                        for (com.riichimahjong.cuterenderer.CuteNode child : handGroup.children()) {
                            if (child instanceof BlockModelNode bm) {
                                bm.makeClickable(new com.riichimahjong.cuterenderer.InteractKey.SeatSlot(
                                        (byte) seat,
                                        com.riichimahjong.cuterenderer.InteractKey.SeatSlot.AREA_HAND,
                                        (short) tileIdx));
                                if (handChildIdx < n
                                        && legalDiscardTiles.contains(rendered.get(handChildIdx))) {
                                    state.legalDiscardNodes.add(bm);
                                }
                                tileIdx++;
                                handChildIdx++;
                            }
                        }
                    }

                    if (highlight.handSeat() == seat
                            && !drawnStripped
                            && n == rendered.size()) {
                        var children = handGroup.children();
                        if (!children.isEmpty()
                                && children.get(children.size() - 1) instanceof BlockModelNode last) {
                            state.activeTileNodes.add(last);
                        }
                    }
                    root.addChild(handGroup);
                }
                handTilesThisFrame += n;
            }

            if (dealingStage == null && seat == localSeat) {
                List<MahjongTableHumanPlayer.TableButton> buttons =
                        MahjongTableHumanPlayer.tableButtons(driver, seat, table.riichiPendingForSeat(seat));
                if (!buttons.isEmpty()) {
                    List<PreviewBtn> configs = new java.util.ArrayList<>(buttons.size());
                    List<List<TheMahjongTile>> ghosts = new java.util.ArrayList<>(buttons.size());
                    for (var b : buttons) {
                        configs.add(configForButton(b));
                        ghosts.add(ghostTilesForButton(b, round));
                    }
                    root.addChild(buildButtonsGroup(seat, outward, configs, ghosts,  true));
                }
            }

            if (dealingStage == null && seat < players.size()) {
                List<com.themahjong.TheMahjongDiscard> discards = players.get(seat).discards();
                if (!discards.isEmpty()) {
                    java.util.Set<Integer> claimed = claimedIndicesForSeat(players, seat);
                    List<TheMahjongTile> tiles = new java.util.ArrayList<>(discards.size());
                    for (int i = 0; i < discards.size(); i++) {
                        if (claimed.contains(i)) continue;
                        tiles.add(discards.get(i).tile());
                    }
                    if (!tiles.isEmpty()) {
                        GroupNode riverGroup = buildGridGroup(RIVER, RIVER.i("cols"), RIVER.i("rows"),
                                tiles, outward);

                        boolean actualLastUnclaimed = !claimed.contains(discards.size() - 1);

                        if (highlight.riverSeat() == seat && actualLastUnclaimed) {
                            var children = riverGroup.children();
                            if (!children.isEmpty()
                                    && children.get(children.size() - 1) instanceof BlockModelNode last) {
                                state.activeTileNodes.add(last);
                            }
                        }
                        root.addChild(riverGroup);
                    }
                }
            }

            if (dealingStage == null && seat < players.size()) {
                List<com.themahjong.TheMahjongMeld> melds = players.get(seat).melds();
                if (!melds.isEmpty()) {
                    root.addChild(buildMeldsLive(MELDS, melds, outward));
                }
                int kitaCount = players.get(seat).kitaCount();
                if (kitaCount > 0) {
                    root.addChild(buildLineGroup(KITA, placeholderTiles(kitaCount, NORTH_TILE), outward, true));
                }
            }

            state.seatRoots[seat] = root;
            state.cute.root().addChild(root);
        }

        if (dealingStage == MatchPhase.Stage.DEALING_HANDS && handTilesThisFrame > state.lastFrameHandTiles) {
            playClickSound(state, level, table.getBlockPos(),
                    ModSounds.TILE_PLACE_SOUND.get(), 0.4f);
            state.lastFrameHandTiles = handTilesThisFrame;
        }
    }

    private static final org.joml.Vector3f DUST_TENPAI = new org.joml.Vector3f(0.92f, 0.98f, 1.0f);
    private static final org.joml.Vector3f DUST_RIICHI = new org.joml.Vector3f(1.0f,  0.58f, 0.16f);
    private static final org.joml.Vector3f DUST_WIN    = new org.joml.Vector3f(1.0f,  0.20f, 0.20f);
    private static final float DUST_SCALE = 0.10f;
    private static final int   DUST_LIFETIME_TICKS = 10;
    private static final double DUST_RISE_SPEED = 0.02;

    private static final double DUST_Y_OFFSET = 0.06;

    private static int tickStrideFor(int mode) {
        return switch (mode) {
            case 0 -> 5;
            case 1 -> 2;
            case 2 -> 2;
            default -> Integer.MAX_VALUE;
        };
    }

    private static void emitSeatParticles(MahjongTableBlockEntity table, Level level,
                                          long gameTick, Direction facing) {
        TheMahjongDriver driver = table.driver();
        if (driver == null) return;
        TheMahjongRound round = driver.match().currentRound().orElse(null);
        if (round == null) return;
        List<TheMahjongPlayer> players = round.players();
        int localSeat = localSeat(table);

        for (int seat = 0; seat < players.size(); seat++) {
            Direction outward = outwardForSeat(seat, facing);
            if (outward == null) continue;
            TheMahjongPlayer player = players.get(seat);
            boolean isLocal = seat == localSeat;
            boolean winAvailable = isLocal && hasWinAction(driver.legalActions(seat));
            boolean riichiActive = player.riichi();
            boolean tenpai = isLocal && !winAvailable && !riichiActive
                    && playerIsTenpaiSafe(player);
            int mode;
            org.joml.Vector3f color;
            if (winAvailable) { mode = 2; color = DUST_WIN; }
            else if (riichiActive) { mode = 1; color = DUST_RIICHI; }
            else if (tenpai) { mode = 0; color = DUST_TENPAI; }
            else continue;
            int stride = tickStrideFor(mode);
            if ((gameTick + seat) % stride != 0) continue;

            int handTiles = Math.min(player.currentHand().size(), HAND_MAX_TILES);
            for (int i = 0; i < handTiles; i++) {
                spawnHandDust(table, level, outward, color, i);
            }
        }
    }

    private static boolean playerIsTenpaiSafe(TheMahjongPlayer player) {
        int expected = 13 - 3 * player.melds().size();
        if (player.currentHand().size() != expected) return false;
        return TenpaiChecker.inTenpai(player);
    }

    private static boolean hasWinAction(List<com.themahjong.driver.PlayerAction> legal) {
        for (var a : legal) {
            if (a instanceof com.themahjong.driver.PlayerAction.DeclareTsumo) return true;
            if (a instanceof com.themahjong.driver.PlayerAction.DeclareRon) return true;
            if (a instanceof com.themahjong.driver.PlayerAction.DeclareChankan) return true;
        }
        return false;
    }

    private static void spawnHandDust(MahjongTableBlockEntity table, Level level,
                                      Direction outward, org.joml.Vector3f color,
                                      int tileIndex) {
        BlockPos pos = table.getBlockPos();
        double[] anchor = anchorWorldOrigin(HAND, outward);
        Direction lineDir = outward.getCounterClockWise();
        double spacing = HAND.f("spacing");
        double t = tileIndex * spacing
                + (level.getRandom().nextDouble() - 0.5) * spacing * 0.6;
        double dx = lineDir.getStepX() * t;
        double dz = lineDir.getStepZ() * t;
        double x = pos.getX() + anchor[0] + dx;
        double y = pos.getY() + anchor[1] + DUST_Y_OFFSET;
        double z = pos.getZ() + anchor[2] + dz;
        var mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.particle.Particle particle = mc.particleEngine.createParticle(
                new net.minecraft.core.particles.DustParticleOptions(packedColor(color), DUST_SCALE),
                x, y, z, 0.0, DUST_RISE_SPEED, 0.0);
        if (particle != null) {
            particle.setParticleSpeed(0.0, DUST_RISE_SPEED, 0.0);
            particle.setLifetime(DUST_LIFETIME_TICKS);
        }
    }

    private void playStageEntrySound(Level level, BlockPos pos, MatchPhase.Stage stage) {
        SoundEvent sound = ModSounds.TILE_PLACE_SOUND.get();

        float pitch = stage == MatchPhase.Stage.DORA_FLIP ? 1.25f : 0.9f;
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                sound, SoundSource.BLOCKS, 0.6f, pitch, false);
    }

    private void playClickSound(State state, Level level, BlockPos pos, SoundEvent sound, float volume) {
        long now = System.nanoTime();
        if (now - state.lastClickSoundNanos < CLICK_SOUND_MIN_INTERVAL_NANOS) return;
        state.lastClickSoundNanos = now;
        float pitch = 0.9f + level.getRandom().nextFloat() * 0.25f;
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                sound, SoundSource.BLOCKS, volume, pitch, false);
    }

    private static int computeDealtTiles(
            @Nullable MatchPhase.Stage stage, double progress,
            int liveWallTotalTiles, TheMahjongRound round) {
        if (stage == MatchPhase.Stage.WALL_BUILDING) return 0;
        if (stage == MatchPhase.Stage.DEALING_HANDS) {
            return (int) Math.round(52 * Math.max(0, Math.min(1.0, progress)));
        }
        if (stage == MatchPhase.Stage.DORA_FLIP) return 52;

        return Math.max(0, liveWallTotalTiles - round.liveWall().size());
    }

    private static final TheMahjongTile FACE_DOWN_PLACEHOLDER =
            new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);

    private static double handVisibleFraction(@Nullable MatchPhase.Stage stage, double progress) {
        if (stage == null) return 1.0;
        return switch (stage) {
            case WALL_BUILDING -> 0.0;
            case DEALING_HANDS -> progress;
            case DORA_FLIP -> 1.0;
        };
    }

    private GroupNode buildPreviewSeat(int seat, Direction outward) {
        GroupNode root = new GroupNode();
        root.addChild(buildLineGroup(HAND, placeholderTiles(filled(HAND_MAX_TILES, HAND), placeholderFor(seat, 0)),
                outward,  false));

        int wallStackHeight = Math.max(1, WALL.i("stackHeight"));
        int wallTiles = liveTilesAfterDeal(seat, WALL.i("count"), DEAD_WALL.i("count"),
                WALL.i("dealtTiles"));
        int wallPositions = (wallTiles + wallStackHeight - 1) / wallStackHeight;
        int wallLastLayers = wallPositions > 0
                ? wallTiles - (wallPositions - 1) * wallStackHeight
                : -1;
        root.addChild(buildLineGroup(WALL,
                placeholderTiles(wallPositions, placeholderFor(seat, 1)),
                outward,  true,
                 -1,  -1,  0,
                 wallLastLayers));

        if (seat == 0) {
            root.addChild(buildDeadWallGroup(DEAD_WALL, outward, placeholderFor(seat, 5)));
        }
        int riverCells = RIVER.i("cols") * RIVER.i("rows");
        root.addChild(buildGridGroup(RIVER, RIVER.i("cols"), RIVER.i("rows"),
                filled(riverCells, RIVER), outward, placeholderFor(seat, 2)));
        int meldsTiles = MELDS.i("meldCount") * MELDS.i("tilesPerMeld");
        root.addChild(buildMeldsGroup(MELDS, filled(meldsTiles, MELDS), outward, placeholderFor(seat, 3)));
        root.addChild(buildLineGroup(KITA, placeholderTiles(filled(KITA.i("count"), KITA), placeholderFor(seat, 4)),
                outward,  true));
        root.addChild(buildButtonsGroup(seat, outward));
        return root;
    }

    private static int liveTilesAfterDeal(int seat, int wallCount, int deadCount, int dealtTiles) {
        int[] preTiles = {
                Math.max(0, wallCount - deadCount) * 2,
                wallCount * 2,
                wallCount * 2,
                wallCount * 2,
        };
        int[] dealOrder = {0, 3, 2, 1};
        int leftToDeal = Math.max(0, dealtTiles);
        int[] remainingTiles = {preTiles[0], preTiles[1], preTiles[2], preTiles[3]};
        for (int s : dealOrder) {
            int take = Math.min(leftToDeal, remainingTiles[s]);
            remainingTiles[s] -= take;
            leftToDeal -= take;
            if (leftToDeal <= 0) break;
        }
        if (seat < 0 || seat >= remainingTiles.length) return 0;
        return remainingTiles[seat];
    }

    private static int filled(int max, LayoutEntry e) {
        int n = Math.round(max * Math.max(0f, Math.min(1f, e.f("fill"))));
        return Math.max(0, Math.min(max, n));
    }

    private GroupNode buildLineGroup(LayoutEntry e, List<TheMahjongTile> tiles,
                                      Direction outward, boolean layFlat, int doraStackIdx,
                                      int splitAfterIdx, double splitGap, int lastStackLayers) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing  = e.f("spacing");
        float scale    = e.scale();
        float pivot    = e.f("pivot");
        int stackHeight = Math.max(1, e.i("stackHeight"));
        float stackGap  = e.f("stackGap");

        boolean faceDown = e.name().equals("wall") || e.name().equals("deadWall");

        Quaternionf rotFaceDown = baseTileRotation(e, outward, layFlat, faceDown);
        Quaternionf rotFaceUp   = faceDown ? baseTileRotation(e, outward, layFlat, false) : rotFaceDown;
        int n = tiles.size();

        double extraSpan = (splitAfterIdx >= 0 && splitAfterIdx < n - 1) ? splitGap : 0;
        double pivotShift = pivot * ((n - 1) * spacing + extraSpan);

        double doraShiftX = 0, doraShiftZ = 0;
        if (faceDown && doraStackIdx >= 0 && !tiles.isEmpty()) {
            var aabb = makeTileNode(tiles.get(0)).naturalLocalAabb();
            double yMidLocal = (aabb.minY + aabb.maxY) * 0.5;

            double along = 2 * yMidLocal * scale;
            doraShiftX = outward.getStepX() * along;
            doraShiftZ = outward.getStepZ() * along;
        }

        GroupNode group = new GroupNode();
        for (int i = 0; i < n; i++) {
            double splitOffset = (splitAfterIdx >= 0 && i > splitAfterIdx) ? splitGap : 0;
            double offset = i * spacing + splitOffset - pivotShift;
            double x = cx + lineDir.getStepX() * offset;
            double z = cz + lineDir.getStepZ() * offset;

            int layersHere = (i == n - 1 && lastStackLayers >= 0) ? lastStackLayers : stackHeight;
            for (int layer = 0; layer < layersHere; layer++) {
                BlockModelNode node = makeTileNode(tiles.get(i));

                boolean isDoraIndicator = (i == doraStackIdx) && (layer == stackHeight - 1);
                double tx = isDoraIndicator ? x + doraShiftX : x;
                double tz = isDoraIndicator ? z + doraShiftZ : z;
                node.transform.setPos(tx, cy + layer * stackGap, tz);
                node.transform.setRotation(isDoraIndicator ? rotFaceUp : rotFaceDown);
                node.transform.setScale(scale);
                group.addChild(node);
            }
        }
        return group;
    }

    private GroupNode buildLineGroup(LayoutEntry e, List<TheMahjongTile> tiles,
                                      Direction outward, boolean layFlat) {
        return buildLineGroup(e, tiles, outward, layFlat,
                 -1,  -1,  0,
                 -1);
    }

    private GroupNode buildLineGroup(LayoutEntry e, List<TheMahjongTile> tiles,
                                      Direction outward, boolean layFlat, int doraStackIdx) {
        return buildLineGroup(e, tiles, outward, layFlat,
                doraStackIdx,  -1,  0,
                 -1);
    }

    private GroupNode buildLiveDeadWall(LayoutEntry e, Direction outward,
                                         TheMahjongTile placeholder, double doraFlipProgress,
                                         int visibleTiles, com.themahjong.TheMahjongRound round,
                                         boolean revealUraDora) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing  = e.f("spacing");
        float scale    = e.scale();
        float pivot    = e.f("pivot");
        int stackHeight = Math.max(1, e.i("stackHeight"));
        float stackGap  = e.f("stackGap");
        int n           = e.i("count");
        int doraStackIdx = n - 3;
        int dirSign = e.i("flipDir") == 0 ? 1 : -1;
        float uraFraction = e.f("uraDoraFraction");

        int revealedDora = Math.max(1, Math.min(
                com.themahjong.TheMahjongRound.MAX_DORA_INDICATORS, round.revealedDoraCount()));
        int kanDoras = revealedDora - 1;
        int rinshanDrawn = Math.max(0, Math.min(4,
                4 - round.rinshanTiles().size()));
        int extraStacks = (rinshanDrawn + 1) / 2;

        Quaternionf rotFaceDown = baseTileRotation(e, outward,  true,  true);
        Quaternionf rotFaceUp   = baseTileRotation(e, outward,  true,  false);
        double pivotShift = pivot * (n - 1) * spacing;

        var aabb = makeTileNode(placeholder).naturalLocalAabb();
        double yMidLocal = (aabb.minY + aabb.maxY) * 0.5;
        double tileDepth = aabb.maxY - aabb.minY;
        double centroidAlong = 2 * yMidLocal * scale;
        double centroidShiftX = outward.getStepX() * centroidAlong;
        double centroidShiftZ = outward.getStepZ() * centroidAlong;
        double uraSlide = uraFraction * tileDepth * scale;

        java.util.Map<Integer, Integer> doraStackIdxToK = new java.util.HashMap<>();
        for (int k = 0; k <= kanDoras; k++) {
            int idx = doraStackIdx - k;
            if (idx >= 0 && idx < n) doraStackIdxToK.put(idx, k);
        }

        int fullCount = n * stackHeight;
        boolean partialBuild = visibleTiles < fullCount;

        GroupNode group = new GroupNode();

        if (partialBuild) {
            int totalVisibleTiles = Math.max(0, visibleTiles);
            int renderedStacks = (totalVisibleTiles + stackHeight - 1) / stackHeight;
            int lastStackLayers = renderedStacks > 0
                    ? totalVisibleTiles - (renderedStacks - 1) * stackHeight
                    : 0;

            for (int orderIdx = 0; orderIdx < renderedStacks; orderIdx++) {
                int i = (n - 1) - orderIdx;
                double offset = (i * spacing - pivotShift) * dirSign;
                double x = cx + lineDir.getStepX() * offset;
                double z = cz + lineDir.getStepZ() * offset;
                boolean isNewest = (orderIdx == renderedStacks - 1);
                int layersHere = isNewest ? lastStackLayers : stackHeight;
                for (int layer = 0; layer < layersHere; layer++) {
                    BlockModelNode node = makeTileNode(placeholder);
                    boolean isTop = (layer == stackHeight - 1);
                    boolean isInitialDora = (i == doraStackIdx) && isTop;
                    double tx = x;
                    double ty = cy + layer * stackGap;
                    double tz = z;
                    Quaternionf rot;
                    if (isInitialDora && doraFlipProgress > 0) {
                        rot = new Quaternionf(rotFaceDown).slerp(rotFaceUp, (float) doraFlipProgress);
                        double along = centroidAlong * doraFlipProgress;
                        tx += outward.getStepX() * along;
                        tz += outward.getStepZ() * along;
                    } else {
                        rot = rotFaceDown;
                    }
                    node.transform.setPos(tx, ty, tz);
                    node.transform.setRotation(rot);
                    node.transform.setScale(scale);
                    group.addChild(node);
                }
            }
            return group;
        }

        for (int i = -extraStacks; i < n; i++) {
            double offset = (i * spacing - pivotShift) * dirSign;
            double x = cx + lineDir.getStepX() * offset;
            double z = cz + lineDir.getStepZ() * offset;
            Integer kHere = i >= 0 ? doraStackIdxToK.get(i) : null;
            boolean isDoraStack = kHere != null;
            for (int layer = 0; layer < stackHeight; layer++) {
                if (i >= 0) {
                    int drawnIdx = -1;
                    if (i == n - 1 && layer == stackHeight - 1) drawnIdx = 1;
                    else if (i == n - 1 && layer == stackHeight - 2) drawnIdx = 2;
                    else if (i == n - 2 && layer == stackHeight - 1) drawnIdx = 3;
                    else if (i == n - 2 && layer == stackHeight - 2) drawnIdx = 4;
                    if (drawnIdx > 0 && rinshanDrawn >= drawnIdx) continue;
                } else {
                    int extIdx = -1 - i;
                    int presentIdx;
                    if (layer == 0) presentIdx = extIdx * 2 + 1;
                    else if (layer == 1) presentIdx = extIdx * 2 + 2;
                    else presentIdx = Integer.MAX_VALUE;
                    if (rinshanDrawn < presentIdx) continue;
                }

                boolean isTop = (layer == stackHeight - 1);
                TheMahjongTile tileToRender = placeholder;
                boolean tileFaceUp = false;
                double tx = x;
                double ty = cy + layer * stackGap;
                double tz = z;
                Quaternionf rot;

                if (isTop && isDoraStack) {
                    int k = kHere;
                    if (k < round.doraIndicators().size()) {
                        tileToRender = round.doraIndicators().get(k);
                    }
                    if (k == 0) {
                        rot = new Quaternionf(rotFaceDown).slerp(rotFaceUp, (float) doraFlipProgress);
                        double along = centroidAlong * doraFlipProgress;
                        tx += outward.getStepX() * along;
                        tz += outward.getStepZ() * along;
                        tileFaceUp = doraFlipProgress >= 1.0;
                    } else {
                        rot = rotFaceUp;
                        tx += centroidShiftX;
                        tz += centroidShiftZ;
                        tileFaceUp = true;
                    }
                } else if (!isTop && isDoraStack && revealUraDora) {
                    int k = kHere;
                    if (k < round.uraDoraIndicators().size()) {
                        tileToRender = round.uraDoraIndicators().get(k);
                    }
                    rot = rotFaceUp;
                    tx += -outward.getStepX() * uraSlide + centroidShiftX;
                    tz += -outward.getStepZ() * uraSlide + centroidShiftZ;
                    tileFaceUp = true;
                } else {
                    rot = rotFaceDown;
                }

                BlockModelNode node = makeTileNode(tileFaceUp ? tileToRender : placeholder);
                node.transform.setPos(tx, ty, tz);
                node.transform.setRotation(rot);
                node.transform.setScale(scale);
                group.addChild(node);
            }
        }
        return group;
    }

    private GroupNode buildDeadWallGroup(LayoutEntry e, Direction outward, TheMahjongTile placeholder) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing = e.f("spacing");
        float scale   = e.scale();
        float pivot   = e.f("pivot");
        int stackHeight = Math.max(1, e.i("stackHeight"));
        float stackGap  = e.f("stackGap");
        int n           = e.i("count");

        int doraStackIdx = n - 3;
        int kanDoras     = Math.max(0, Math.min(4, e.i("kanDoras")));
        boolean showUra      = e.i("uraDora") > 0;
        float uraFraction    = e.f("uraDoraFraction");

        int dirSign = e.i("flipDir") == 0 ? 1 : -1;
        int rinshanDrawn = Math.max(0, Math.min(4, e.i("rinshanDrawn")));

        Quaternionf rotFaceDown = baseTileRotation(e, outward,  true,  true);
        Quaternionf rotFaceUp   = baseTileRotation(e, outward,  true,  false);
        double pivotShift = pivot * (n - 1) * spacing;

        var aabb = makeTileNode(placeholder).naturalLocalAabb();
        double yMidLocal = (aabb.minY + aabb.maxY) * 0.5;
        double tileDepth = aabb.maxY - aabb.minY;
        double centroidAlong = 2 * yMidLocal * scale;
        double centroidShiftX = outward.getStepX() * centroidAlong;
        double centroidShiftZ = outward.getStepZ() * centroidAlong;
        double uraSlide = uraFraction * tileDepth * scale;

        java.util.Set<Integer> doraStacks = new java.util.HashSet<>();
        if (doraStackIdx >= 0 && doraStackIdx < n) doraStacks.add(doraStackIdx);
        for (int k = 1; k <= kanDoras; k++) {
            int idx = doraStackIdx - k;
            if (idx >= 0 && idx < n) doraStacks.add(idx);
        }

        int extraStacks = (rinshanDrawn + 1) / 2;
        int totalStacks = n + extraStacks;

        GroupNode group = new GroupNode();
        for (int i = 0; i < totalStacks; i++) {
            double offset = (i * spacing - pivotShift) * dirSign;
            double x = cx + lineDir.getStepX() * offset;
            double z = cz + lineDir.getStepZ() * offset;
            boolean isDoraStack = i < n && doraStacks.contains(i);
            for (int layer = 0; layer < stackHeight; layer++) {
                if (i < n) {
                    int drawnIdx = -1;
                    if (i == n - 1 && layer == stackHeight - 1) drawnIdx = 1;
                    else if (i == n - 1 && layer == stackHeight - 2) drawnIdx = 2;
                    else if (i == n - 2 && layer == stackHeight - 1) drawnIdx = 3;
                    else if (i == n - 2 && layer == stackHeight - 2) drawnIdx = 4;
                    if (drawnIdx > 0 && rinshanDrawn >= drawnIdx) continue;
                } else {
                    int extIdx = i - n;
                    int presentIdx;
                    if (layer == 0) presentIdx = extIdx * 2 + 1;
                    else if (layer == 1) presentIdx = extIdx * 2 + 2;
                    else presentIdx = Integer.MAX_VALUE;
                    if (rinshanDrawn < presentIdx) continue;
                }

                BlockModelNode node = makeTileNode(placeholder);
                boolean isTop = (layer == stackHeight - 1);
                boolean tileFaceUp = false;
                double tx = x;
                double ty = cy + layer * stackGap;
                double tz = z;

                if (isTop && isDoraStack) {
                    tileFaceUp = true;
                    tx += centroidShiftX;
                    tz += centroidShiftZ;
                } else if (!isTop && isDoraStack && showUra) {
                    tileFaceUp = true;
                    tx += -outward.getStepX() * uraSlide + centroidShiftX;
                    tz += -outward.getStepZ() * uraSlide + centroidShiftZ;
                }

                node.transform.setPos(tx, ty, tz);
                node.transform.setRotation(tileFaceUp ? rotFaceUp : rotFaceDown);
                node.transform.setScale(scale);
                group.addChild(node);
            }
        }
        return group;
    }

    private GroupNode buildGridGroup(LayoutEntry e, int cols, int rows,
                                     List<TheMahjongTile> tiles, Direction outward) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing    = e.f("spacing");
        float rowSpacing = e.f("rowSpacing");
        float scale      = e.scale();
        float pivot      = e.f("pivot");

        Quaternionf rot = baseTileRotation(e, outward,  true);
        double colPivotShift = pivot * (cols - 1) * spacing;
        int itemsToShow = Math.min(tiles.size(), cols * rows);

        GroupNode group = new GroupNode();
        int idx = 0;
        for (int row = 0; row < rows && idx < itemsToShow; row++) {
            for (int col = 0; col < cols && idx < itemsToShow; col++, idx++) {
                BlockModelNode node = makeTileNode(tiles.get(idx));
                double colOff = col * spacing - colPivotShift;
                double rowOff = row * rowSpacing;
                double x = cx + lineDir.getStepX() * colOff + outward.getStepX() * rowOff;
                double z = cz + lineDir.getStepZ() * colOff + outward.getStepZ() * rowOff;
                node.transform.setPos(x, cy, z);
                node.transform.setRotation(rot);
                node.transform.setScale(scale);
                group.addChild(node);
            }
        }
        return group;
    }

    private GroupNode buildGridGroup(LayoutEntry e, int cols, int rows, int itemsToShow,
                                     Direction outward, TheMahjongTile placeholder) {
        return buildGridGroup(e, cols, rows,
                placeholderTiles(itemsToShow, placeholder), outward);
    }

    private GroupNode buildMeldsLive(LayoutEntry e, List<com.themahjong.TheMahjongMeld> melds,
                                     Direction outward) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing = e.f("spacing");
        float meldGap = e.f("meldGap");
        float scale   = e.scale();
        float pivot   = e.f("pivot");
        int dirSign   = e.i("flipDir") == 0 ? 1 : -1;

        Quaternionf rot = baseTileRotation(e, outward,  true);
        Quaternionf sidewaysRot = new Quaternionf(rot)
                .rotateLocalY((float) Math.toRadians(90.0));

        double totalSpan = 0;
        for (int i = 0; i < melds.size(); i++) {
            totalSpan += melds.get(i).tiles().size() * spacing;
            if (i < melds.size() - 1) totalSpan += meldGap;
        }
        double pivotShift = pivot * Math.max(0, totalSpan - spacing);

        GroupNode group = new GroupNode();
        double cursor = 0;
        for (com.themahjong.TheMahjongMeld meld : melds) {
            List<TheMahjongTile> tiles = meld.tiles();
            java.util.OptionalInt sideways = meld.sidewaysTileIndex();
            for (int t = 0; t < tiles.size(); t++) {
                double offset = (cursor + t * spacing - pivotShift) * dirSign;
                double x = cx + lineDir.getStepX() * offset;
                double z = cz + lineDir.getStepZ() * offset;
                BlockModelNode node = makeTileNode(tiles.get(t));
                node.transform.setPos(x, cy, z);
                node.transform.setRotation(
                        sideways.isPresent() && sideways.getAsInt() == t ? sidewaysRot : rot);
                node.transform.setScale(scale);
                group.addChild(node);
            }
            cursor += tiles.size() * spacing + meldGap;
        }
        return group;
    }

    private GroupNode buildMeldsGroup(LayoutEntry e, int tilesToShow, Direction outward,
                                      TheMahjongTile placeholder) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(e, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        float spacing       = e.f("spacing");
        float meldGap       = e.f("meldGap");
        float scale         = e.scale();
        float pivot         = e.f("pivot");
        int meldCount       = e.i("meldCount");
        int tilesPerMeld    = e.i("tilesPerMeld");

        Quaternionf rot = baseTileRotation(e, outward,  true);

        double meldWidth = tilesPerMeld * spacing;
        double totalSpan = meldCount * meldWidth + Math.max(0, meldCount - 1) * meldGap;

        double pivotShift = pivot * (totalSpan - spacing);

        int dirSign = e.i("flipDir") == 0 ? 1 : -1;

        GroupNode group = new GroupNode();
        int idx = 0;
        for (int m = 0; m < meldCount && idx < tilesToShow; m++) {
            double meldStart = m * (meldWidth + meldGap);
            for (int t = 0; t < tilesPerMeld && idx < tilesToShow; t++, idx++) {
                BlockModelNode node = makeTileNode(placeholder);
                double offset = (meldStart + t * spacing - pivotShift) * dirSign;
                double x = cx + lineDir.getStepX() * offset;
                double z = cz + lineDir.getStepZ() * offset;
                node.transform.setPos(x, cy, z);
                node.transform.setRotation(rot);
                node.transform.setScale(scale);
                group.addChild(node);
            }
        }
        return group;
    }

    private GroupNode buildButtonsGroup(int seat, Direction outward,
                                        List<PreviewBtn> configs,
                                        @Nullable List<List<TheMahjongTile>> ghostTiles,
                                        boolean clickable) {
        Direction lineDir = outward.getCounterClockWise();
        double[] c = anchorWorldOrigin(BUTTONS, outward);
        double cx = c[0], cy = c[1], cz = c[2];
        int cols = Math.max(1, BUTTONS.i("cols"));
        int rows = Math.max(1, BUTTONS.i("rows"));
        float colSpacing = BUTTONS.f("colSpacing");
        float rowSpacing = BUTTONS.f("rowSpacing");
        float colPivot   = BUTTONS.f("colPivot");
        float rowPivot   = BUTTONS.f("rowPivot");
        float textScale  = BUTTONS.scale();
        int slotCount    = Math.min(configs.size(), cols * rows);

        Quaternionf rot = baseTileRotation(BUTTONS, outward,  true);

        Quaternionf tileRot = baseTileRotation(BUTTON_GHOSTS, outward,  true);
        float ghostScale = BUTTON_GHOSTS.scale();
        double ghostSpacing       = BUTTON_GHOSTS.f("spacing");
        double ghostLateral       = BUTTON_GHOSTS.pos().x;
        double ghostYLift         = BUTTON_GHOSTS.pos().y;
        double ghostOutwardOffset = BUTTON_GHOSTS.pos().z;
        float rightPadPerTilePx   = BUTTONS.f("rightPadPerTile");

        Direction plateRightDir = outward.getCounterClockWise();

        int[] perRowCount = new int[Math.max(rows, 1)];
        for (int r = 0; r < rows; r++) {
            int start = r * cols;
            perRowCount[r] = Math.max(0, Math.min(cols, slotCount - start));
        }
        double rowShift = rowPivot * (rows - 1) * rowSpacing;

        GroupNode group = new GroupNode();
        int idx = 0;
        outer: for (int r = 0; r < rows; r++) {
            int countInRow = perRowCount[r];
            if (countInRow == 0) break;

            double rowColShift = colPivot * (countInRow - 1) * colSpacing;
            for (int col = 0; col < countInRow; col++, idx++) {
                if (idx >= slotCount) break outer;
                double colOff = col * colSpacing - rowColShift;
                double rowOff = r * rowSpacing - rowShift;
                double x = cx + lineDir.getStepX() * colOff + outward.getStepX() * rowOff;
                double z = cz + lineDir.getStepZ() * colOff + outward.getStepZ() * rowOff;
                double y = cy;
                PreviewBtn cfg = configs.get(idx);
                int ghostCount = (ghostTiles != null && idx < ghostTiles.size())
                        ? ghostTiles.get(idx).size() : 0;
                float extraRightPad = rightPadPerTilePx * ghostCount;
                WorldButtonNode btn = new WorldButtonNode(cfg.label())
                        .setTextScale(textScale)
                        .setTextColor(cfg.textColor())
                        .setBgColor(cfg.bgColor())
                        .setPulsing(cfg.pulsing())
                        .setExtraRightPad(extraRightPad);
                if (clickable) {
                    btn.makeClickable(new com.riichimahjong.cuterenderer.InteractKey.SeatSlot(
                            (byte) seat,
                            com.riichimahjong.cuterenderer.InteractKey.SeatSlot.AREA_BUTTON,
                            (short) idx));
                }
                btn.transform.setPos(x, y, z);
                btn.transform.setRotation(rot);
                group.addChild(btn);

                if (ghostTiles != null && idx < ghostTiles.size()) {
                    List<TheMahjongTile> preview = ghostTiles.get(idx);
                    int n = preview.size();
                    if (n > 0) {
                        double plateRightEdgeWorld = btn.plateHalfWidthWorld();
                        double ox = -outward.getStepX() * ghostOutwardOffset;
                        double oz = -outward.getStepZ() * ghostOutwardOffset;
                        Quaternionf btnInvRot = new Quaternionf(rot).invert();
                        for (int i = 0; i < n; i++) {
                            double along = plateRightEdgeWorld + i * ghostSpacing + ghostLateral;
                            double tx = x + plateRightDir.getStepX() * along + ox;
                            double tz = z + plateRightDir.getStepZ() * along + oz;

                            Vector3f localOffset = new Vector3f(
                                    (float) (tx - x),
                                    (float) ghostYLift,
                                    (float) (tz - z));
                            btnInvRot.transform(localOffset);

                            Quaternionf localTileRot = new Quaternionf(btnInvRot).mul(tileRot);

                            BlockModelNode node = makeTileNode(preview.get(i));
                            node.transform.setPos(localOffset.x, localOffset.y, localOffset.z);
                            node.transform.setRotation(localTileRot);
                            node.transform.setScale(ghostScale);
                            btn.addChild(node);
                        }
                    }
                }
            }
        }
        return group;
    }

    private GroupNode buildButtonsGroup(int seat, Direction outward) {
        return buildButtonsGroup(seat, outward,
                java.util.Arrays.asList(PREVIEW_BUTTONS),
                PREVIEW_GHOSTS,
                 false);
    }

    private static PreviewBtn configForButton(MahjongTableHumanPlayer.TableButton btn) {
        if (btn instanceof MahjongTableHumanPlayer.TableButton.RiichiToggle r) {
            return r.active()
                    ? new PreviewBtn(actionLabel("riichi"), 0xFFFFE066, 0xC07A5A1A, true)
                    : new PreviewBtn(actionLabel("riichi"), 0xFFFFD700, 0xB05A4218, false);
        }
        if (btn instanceof MahjongTableHumanPlayer.TableButton.SubmitAction sa) {
            return configForAction(sa.action());
        }
        return new PreviewBtn(Component.literal("?"), 0xFFE8E8E8, 0xB0444A58, false);
    }

    private static List<TheMahjongTile> ghostTilesForButton(
            MahjongTableHumanPlayer.TableButton btn, com.themahjong.TheMahjongRound round) {
        if (btn instanceof MahjongTableHumanPlayer.TableButton.SubmitAction sa) {
            return ghostTilesForAction(sa.action(), round);
        }
        return List.of();
    }

    private static PreviewBtn configForAction(com.themahjong.driver.PlayerAction action) {
        if (action instanceof com.themahjong.driver.PlayerAction.DeclareRon)
            return new PreviewBtn(actionLabel("ron"),     0xFFFF5757, 0xC05A2222, true);
        if (action instanceof com.themahjong.driver.PlayerAction.DeclareChankan)
            return new PreviewBtn(actionLabel("chankan"), 0xFFFF5757, 0xC05A2222, true);
        if (action instanceof com.themahjong.driver.PlayerAction.DeclareTsumo)
            return new PreviewBtn(actionLabel("tsumo"),   0xFFFF6A6A, 0xC05A2626, true);
        if (action instanceof com.themahjong.driver.PlayerAction.Pon)
            return new PreviewBtn(actionLabel("pon"),     0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.Daiminkan)
            return new PreviewBtn(actionLabel("kan"),     0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.Ankan)
            return new PreviewBtn(actionLabel("ankan"),   0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.Kakan)
            return new PreviewBtn(actionLabel("kan"),     0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.Chi)
            return new PreviewBtn(actionLabel("chi"),     0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.DeclareKita)
            return new PreviewBtn(actionLabel("kita"),    0xFFE8E8E8, 0xB0444A58, false);
        if (action instanceof com.themahjong.driver.PlayerAction.KyuushuAbort)
            return new PreviewBtn(actionLabel("abort"),   0xFFE8E8E8, 0xB0393D48, false);
        if (action instanceof com.themahjong.driver.PlayerAction.Pass)
            return new PreviewBtn(actionLabel("pass"),    0xFFBFC6D2, 0xB0393D48, false);
        return new PreviewBtn(Component.literal("?"),     0xFFE8E8E8, 0xB0444A58, false);
    }

    private static List<TheMahjongTile> ghostTilesForAction(
            com.themahjong.driver.PlayerAction action,
            com.themahjong.TheMahjongRound round) {
        TheMahjongTile called = activeTileOrNull(round);
        if (action instanceof com.themahjong.driver.PlayerAction.Pon p) {
            return appendNullable(p.handTiles(), called);
        }
        if (action instanceof com.themahjong.driver.PlayerAction.Daiminkan dk) {
            return appendNullable(dk.handTiles(), called);
        }
        if (action instanceof com.themahjong.driver.PlayerAction.Chi c) {
            return appendNullable(c.handTiles(), called);
        }
        if (action instanceof com.themahjong.driver.PlayerAction.Ankan ak) {
            return ak.handTiles();
        }
        if (action instanceof com.themahjong.driver.PlayerAction.Kakan kk) {
            List<TheMahjongTile> out = new java.util.ArrayList<>(kk.upgradedFrom().tiles());
            out.add(kk.addedTile());
            return out;
        }
        if (action instanceof com.themahjong.driver.PlayerAction.DeclareKita dk) {
            return List.of(dk.tile());
        }
        return List.of();
    }

    private static List<TheMahjongTile> appendNullable(List<TheMahjongTile> base, @Nullable TheMahjongTile extra) {
        if (extra == null) return base;
        List<TheMahjongTile> out = new java.util.ArrayList<>(base.size() + 1);
        out.addAll(base);
        out.add(extra);
        return out;
    }

    @Nullable
    private static TheMahjongTile activeTileOrNull(com.themahjong.TheMahjongRound round) {
        var at = round.activeTile();
        if (at instanceof com.themahjong.TheMahjongRound.ActiveTile.Drawn d) return d.tile();
        if (at instanceof com.themahjong.TheMahjongRound.ActiveTile.HeldDiscard h) return h.tile();
        if (at instanceof com.themahjong.TheMahjongRound.ActiveTile.HeldKita h) return h.tile();
        if (at instanceof com.themahjong.TheMahjongRound.ActiveTile.HeldKakan h) return h.tile();
        return null;
    }

    private static java.util.Set<TheMahjongTile> collectLegalDiscardTiles(
            List<com.themahjong.driver.PlayerAction> legal, boolean riichiArmed) {
        java.util.Set<TheMahjongTile> out = new java.util.HashSet<>();
        for (com.themahjong.driver.PlayerAction a : legal) {
            if (riichiArmed) {
                if (a instanceof com.themahjong.driver.PlayerAction.DiscardWithRiichi r) {
                    out.add(r.tile());
                }
            } else {
                if (a instanceof com.themahjong.driver.PlayerAction.Discard d) out.add(d.tile());
                else if (a instanceof com.themahjong.driver.PlayerAction.DiscardWithRiichi r) out.add(r.tile());
            }
        }
        return out;
    }

    private static java.util.Set<Integer> claimedIndicesForSeat(
            List<TheMahjongPlayer> players, int targetSeat) {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (TheMahjongPlayer p : players) {
            for (com.themahjong.TheMahjongMeld meld : p.melds()) {
                int srcSeat = -1;
                int srcIdx = -1;
                if (meld instanceof com.themahjong.TheMahjongMeld.Chi c) {
                    srcSeat = c.sourceSeat(); srcIdx = c.sourceDiscardIndex();
                } else if (meld instanceof com.themahjong.TheMahjongMeld.Pon pn) {
                    srcSeat = pn.sourceSeat(); srcIdx = pn.sourceDiscardIndex();
                } else if (meld instanceof com.themahjong.TheMahjongMeld.Daiminkan dk) {
                    srcSeat = dk.sourceSeat(); srcIdx = dk.sourceDiscardIndex();
                } else if (meld instanceof com.themahjong.TheMahjongMeld.Kakan kk) {
                    srcSeat = kk.upgradedFrom().sourceSeat();
                    srcIdx = kk.upgradedFrom().sourceDiscardIndex();
                }
                if (srcSeat == targetSeat && srcIdx >= 0) out.add(srcIdx);
            }
        }
        return out;
    }

    private static int localSeat(MahjongTableBlockEntity table) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        UUID uuid = mc.player.getUUID();
        List<MahjongTableBlockEntity.SeatInfo> seats = table.seats();
        for (int i = 0; i < seats.size(); i++) {
            var info = seats.get(i);
            if (info.enabled() && info.occupant().isPresent() && info.occupant().get().equals(uuid)) {
                return i;
            }
        }
        return -1;
    }

    private static Quaternionf baseTileRotation(LayoutEntry e, Direction outward, boolean layFlat) {
        return baseTileRotation(e, outward, layFlat,  false);
    }

    private static Quaternionf baseTileRotation(LayoutEntry e, Direction outward,
                                                boolean layFlat, boolean faceDown) {
        Quaternionf q = new Quaternionf();
        if (layFlat) {
            float yaw = switch (outward) {
                case NORTH ->   0f;
                case EAST  -> -90f;
                case WEST  ->  90f;
                default    -> 180f;
            };
            q.rotateX((float) Math.toRadians(90f))
             .rotateLocalY((float) Math.toRadians(yaw));

            if (faceDown) q.rotateLocalX((float) Math.toRadians(180f));
        } else {
            float yaw = switch (outward) {
                case NORTH ->   0f;
                case EAST  -> -90f;
                case SOUTH -> 180f;
                case WEST  ->  90f;
                default    ->   0f;
            };
            q.rotateY((float) Math.toRadians(yaw));
        }

        float ty = e.f("tileYaw");
        float tp = e.f("tilePitch");
        float tr = e.f("tileRoll");
        if (ty != 0f) q.rotateY((float) Math.toRadians(ty));
        if (tp != 0f) q.rotateX((float) Math.toRadians(tp));
        if (tr != 0f) q.rotateZ((float) Math.toRadians(tr));
        return q;
    }

    private static double[] anchorWorldOrigin(LayoutEntry e, Direction outward) {
        Vector3f p = e.pos();
        Direction lineDir = outward.getCounterClockWise();
        double cx = 0.5 + outward.getStepX() * p.z + lineDir.getStepX() * p.x;
        double cz = 0.5 + outward.getStepZ() * p.z + lineDir.getStepZ() * p.x;
        return new double[] {cx, p.y, cz};
    }

    private static TheMahjongTile placeholderFor(int seat, int layoutKind) {
        return switch (seat) {
            case 0 -> new TheMahjongTile(TheMahjongTile.Suit.MANZU, layoutKind + 1, false);
            case 1 -> new TheMahjongTile(TheMahjongTile.Suit.SOUZU, layoutKind + 1, false);
            case 2 -> new TheMahjongTile(TheMahjongTile.Suit.PINZU, layoutKind + 1, false);
            case 3 -> layoutKind < 4
                    ? new TheMahjongTile(TheMahjongTile.Suit.WIND, layoutKind + 1, false)
                    : new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 1, false);
            default -> new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        };
    }

    private static List<TheMahjongTile> placeholderTiles(int count, TheMahjongTile tile) {
        TheMahjongTile[] arr = new TheMahjongTile[Math.max(0, count)];
        for (int i = 0; i < arr.length; i++) arr[i] = tile;
        return List.of(arr);
    }

    private static BlockModelNode makeTileNode(TheMahjongTile tile) {
        Block tileBlock = MahjongTileItems.blockForTile(tile);
        BlockState state;
        if (tileBlock != null) {
            state = tileBlock.defaultBlockState();
        } else {
            LOGGER.warn("Mahjong table: no tile block registered for {}; rendering STONE placeholder", tile);
            state = Blocks.STONE.defaultBlockState();
        }
        return new BlockModelNode(state);
    }

    @Nullable
    private static Direction outwardForSeat(int seat, Direction facing) {
        for (Direction cellDir : HORIZONTAL) {
            if (MahjongTableBlock.seatForDirection(cellDir, facing) == seat) {
                return cellDir;
            }
        }
        return null;
    }

    private static Component buildWindLine(int seat, MahjongTableBlockEntity table) {
        Component wind = seat >= 0 && seat < WIND_KEY_BY_SEAT.length
                ? Component.translatable(WIND_KEY_BY_SEAT[seat])
                : Component.literal("?");
        if (table.driver() == null) {
            return wind;
        }
        var roundOpt = table.driver().match().currentRound();
        if (roundOpt.isEmpty() || seat >= roundOpt.get().players().size()) {
            return wind;
        }
        return Component.translatable("riichi_mahjong.label.wind_points",
                wind, roundOpt.get().players().get(seat).points());
    }

    private static Component buildNameLine(MahjongTableBlockEntity.SeatInfo info, Level level) {
        return info.occupant().map(uuid -> {
            Player p = level.getPlayerByUUID(uuid);
            return Component.literal(p != null ? p.getName().getString() : "(offline)");
        }).orElse(Component.literal("BOT"));
    }

    private static int currentTurnSeat(MahjongTableBlockEntity table) {
        if (table.driver() == null) return -1;
        return table.driver().match().currentRound()
                .map(TheMahjongRound::currentTurnSeat)
                .orElse(-1);
    }
}
