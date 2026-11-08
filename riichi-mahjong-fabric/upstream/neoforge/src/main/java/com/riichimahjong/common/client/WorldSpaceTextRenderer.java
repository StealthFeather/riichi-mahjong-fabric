package com.riichimahjong.common.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class WorldSpaceTextRenderer {
    private static final float BACKGROUND_PAD = 1.0f;

    public record Style(float scale, float lineGapRatio, int textColor, int bgColor) {}

    private WorldSpaceTextRenderer() {}

    public static void drawOutwardLines(
            PoseStack pose,
            SubmitNodeCollector collector,
            int packedLight,
            Font font,
            double localX,
            double localY,
            double localZ,
            Direction outward,
            Style style,
            List<Component> lines) {
        if (lines.isEmpty()) return;

        pose.pushPose();
        pose.translate(localX, localY, localZ);
        pose.mulPose(Axis.YP.rotationDegrees(yawForOutwardFacing(outward)));
        pose.scale(-style.scale(), -style.scale(), style.scale());

        float rowPitch = font.lineHeight * style.lineGapRatio();
        for (int i = 0; i < lines.size(); i++) {
            drawCentered(pose, collector, packedLight, font, lines.get(i), i * rowPitch, style);
        }
        pose.popPose();
    }

    private static void drawCentered(
            PoseStack pose,
            SubmitNodeCollector collector,
            int packedLight,
            Font font,
            Component text,
            float yOffset,
            Style style) {
        FormattedCharSequence line = text.getVisualOrderText();
        submitLine(pose, collector, packedLight, font, line,
                -font.width(line) / 2.0f, yOffset, style.textColor(), style.bgColor());
    }

    public static void submitLine(
            PoseStack pose,
            SubmitNodeCollector collector,
            int packedLight,
            Font font,
            FormattedCharSequence line,
            float x,
            float y,
            int textColor,
            int bgColor) {
        if (bgColor != 0) {
            float x0 = x - BACKGROUND_PAD;
            float x1 = x + font.width(line) + BACKGROUND_PAD;
            float y0 = y - BACKGROUND_PAD;
            float y1 = y + font.lineHeight;
            collector.submitCustomGeometry(pose, RenderTypes.textBackground(), (entry, buffer) -> {
                buffer.addVertex(entry, x0, y0, 0f).setColor(bgColor).setLight(packedLight);
                buffer.addVertex(entry, x0, y1, 0f).setColor(bgColor).setLight(packedLight);
                buffer.addVertex(entry, x1, y1, 0f).setColor(bgColor).setLight(packedLight);
                buffer.addVertex(entry, x1, y0, 0f).setColor(bgColor).setLight(packedLight);
            });
        }
        collector.order(bgColor != 0 ? 1 : 0).submitText(
                pose, x, y, line, false, Font.DisplayMode.POLYGON_OFFSET,
                packedLight, textColor, 0, 0);
    }

    public static float yawForOutwardFacing(Direction outward) {
        return switch (outward) {
            case NORTH -> 0.0f;
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }
}
