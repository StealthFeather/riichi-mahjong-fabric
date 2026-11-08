package com.riichimahjong.cuterenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public final class WorldButtonNode extends CuteNode {
    private static final float DEFAULT_TEXT_SCALE = 0.0078f;

    private static final float DEFAULT_PAD_X = 3.0f;
    private static final float DEFAULT_PAD_Y = 1.2f;

    private static final float DEFAULT_DEPTH = 2.1f;

    private static final int DEFAULT_HOVER_BRIGHTEN = 90;

    private static final int DEFAULT_SIDE_DARKEN = 52;

    private static final float DEFAULT_HOVER_SCALE = 1.0f;

    private static final float TEXT_LIFT_PX = 0.6f;

    private Component label;
    private int textColor = 0xFFE8E8E8;
    private int hoverTextColor = 0xFFFFFFFF;
    private int bgColor = 0xB0444A58;
    private int sideColor = darken(0xB0444A58, DEFAULT_SIDE_DARKEN);
    private boolean sideColorExplicit = false;
    private float textScale = DEFAULT_TEXT_SCALE;
    private float padX = DEFAULT_PAD_X;
    private float padY = DEFAULT_PAD_Y;
    private float depth = DEFAULT_DEPTH;
    private float hoverScale = DEFAULT_HOVER_SCALE;
    private int hoverBrighten = DEFAULT_HOVER_BRIGHTEN;

    private boolean pulsing = false;

    private float extraRightPad = 0f;

    public WorldButtonNode(Component label) {
        this.label = label;

        setHoverLiftScale(0.25f);
    }

    public WorldButtonNode setLabel(Component c) { this.label = c; return this; }
    public WorldButtonNode setTextColor(int argb) { this.textColor = argb; return this; }
    public WorldButtonNode setHoverTextColor(int argb) { this.hoverTextColor = argb; return this; }
    public WorldButtonNode setBgColor(int argb) {
        this.bgColor = argb;
        if (!sideColorExplicit) this.sideColor = darken(argb, DEFAULT_SIDE_DARKEN);
        return this;
    }
    public WorldButtonNode setSideColor(int argb) {
        this.sideColor = argb;
        this.sideColorExplicit = true;
        return this;
    }
    public WorldButtonNode setTextScale(float s) { this.textScale = s; return this; }
    public WorldButtonNode setPlatePadding(float x, float y) { this.padX = x; this.padY = y; return this; }
    public WorldButtonNode setDepth(float d) { this.depth = d; return this; }
    public WorldButtonNode setHoverScale(float s) { this.hoverScale = s; return this; }
    public WorldButtonNode setHoverBrighten(int delta) { this.hoverBrighten = delta; return this; }
    public WorldButtonNode setPulsing(boolean p) { this.pulsing = p; return this; }

    public WorldButtonNode setExtraRightPad(float fontPx) { this.extraRightPad = Math.max(0f, fontPx); return this; }

    public float plateHalfWidthWorld() {
        Font font = Minecraft.getInstance().font;
        return (font.width(label) * 0.5f + padX) * textScale;
    }

    public float textScaleWorld() { return textScale; }

    public WorldButtonNode makeClickable(InteractKey key) {
        setInteractive(Interactive.button(key, naturalLocalAabb()));
        return this;
    }

    public Component label() { return label; }
    public float textScale() { return textScale; }

    @Override
    protected AABB naturalLocalAabbOrNull() { return naturalLocalAabb(); }

    public AABB naturalLocalAabb() {
        Font font = Minecraft.getInstance().font;
        float halfW = (font.width(label) * 0.5f + padX) * textScale;
        float extraRight = extraRightPad * textScale;
        float halfH = (font.lineHeight * 0.5f + padY) * textScale;

        float depthLocal = depth * textScale;
        float frontPad   = TEXT_LIFT_PX * textScale;
        return new AABB(-halfW, -halfH, -frontPad, halfW + extraRight, halfH, depthLocal);
    }

    @Override
    protected void submitSelf(PoseStack pose, SubmitNodeCollector collector,
                              int packedLight, int packedOverlay) {
        Font font = Minecraft.getInstance().font;
        int width = font.width(label);
        float halfW = width * 0.5f + padX;
        float halfH = font.lineHeight * 0.5f + padY;
        float plateLeft  = -halfW;
        float plateRight = halfW + extraRightPad;
        float plateZTop    = 0f;
        float plateZBottom = depth;
        float textY0 = -font.lineHeight * 0.34f;

        float pulseT = pulsing ? sinePulse() : 0f;
        float scale = textScale
                * (isHovered ? hoverScale : 1f)
                * (1f + 0.10f * pulseT);
        int brighten = (isHovered ? hoverBrighten : 0) + Math.round(50f * pulseT);
        int topArgb  = brighten > 0 ? brighten(bgColor, brighten) : bgColor;
        int sideArgb = brighten > 0 ? brighten(sideColor, brighten) : sideColor;
        int textArgb = isHovered ? hoverTextColor : textColor;
        float depthScale = 1f + 0.18f * pulseT;

        pose.pushPose();
        pose.scale(scale, scale, scale);
        float zb = plateZBottom * depthScale;
        float plateL = plateLeft;
        float plateR = plateRight;
        float plateTop = plateZTop;
        int topColor = topArgb;
        int sideColor2 = sideArgb;

        collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (entry, quads) -> {
            Matrix4f m = entry.pose();
        emitQuad(quads, m, plateL, -halfH, plateTop,  plateR, -halfH, plateTop,
                            plateR,  halfH, plateTop, plateL,  halfH, plateTop, topColor);

        emitQuad(quads, m, plateL,  halfH, plateTop, plateR,  halfH, plateTop,
                            plateR,  halfH, zb,        plateL,  halfH, zb, sideColor2);

        emitQuad(quads, m, plateL, -halfH, zb,         plateR, -halfH, zb,
                            plateR, -halfH, plateTop, plateL, -halfH, plateTop, sideColor2);

        emitQuad(quads, m, plateL, -halfH, plateTop, plateL,  halfH, plateTop,
                            plateL,  halfH, zb,        plateL, -halfH, zb, sideColor2);

        emitQuad(quads, m,  plateR, -halfH, zb,        plateR,  halfH, zb,
                            plateR,  halfH, plateTop, plateR, -halfH, plateTop, sideColor2);
        });

        pose.pushPose();
        pose.translate(0f, 0f, -TEXT_LIFT_PX);
        collector.submitText(
                pose,
                -width * 0.5f,
                textY0,
                label.getVisualOrderText(),
                false,
                Font.DisplayMode.POLYGON_OFFSET,
                packedLight,
                textArgb,
                0,
                0);
        pose.popPose();
        pose.popPose();
    }

    private static void emitQuad(
            VertexConsumer vc, Matrix4f m,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>>  8) & 0xFF;
        int b =  argb         & 0xFF;
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x4, y4, z4).setColor(r, g, b, a);
    }

    private static float sinePulse() {
        double t = (System.nanoTime() / 1_000_000_000.0) * 2.4;
        return (float) ((Math.sin(t) + 1.0) * 0.5);
    }

    private static int brighten(int color, int delta) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + delta);
        int g = Math.min(255, ((color >>>  8) & 0xFF) + delta);
        int b = Math.min(255, ( color         & 0xFF) + delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, int delta) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.max(0, ((color >>> 16) & 0xFF) - delta);
        int g = Math.max(0, ((color >>>  8) & 0xFF) - delta);
        int b = Math.max(0, ( color         & 0xFF) - delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
