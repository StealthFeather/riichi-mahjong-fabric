package com.riichimahjong.cuterenderer.editor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.riichimahjong.common.client.ClientCompat;
import com.riichimahjong.cuterenderer.HoverHighlightRenderer;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CuteEditor {
    private static final Map<String, LayoutEntry> ENTRIES = new LinkedHashMap<>();
    private static boolean overlayActive = false;
    @Nullable private static String selected;

    private static final Deque<Snapshot> UNDO = new ArrayDeque<>();
    private static final int MAX_UNDO = 64;

    private CuteEditor() {}

    public static LayoutEntry layout(String name, Vector3f defPos, float defScale) {
        return ENTRIES.computeIfAbsent(name, n -> new LayoutEntry(n, defPos, defScale));
    }

    public static Collection<LayoutEntry> entries() {
        return ENTRIES.values();
    }

    public static boolean isActive() {
        return overlayActive;
    }

    public static void toggle() {
        overlayActive = !overlayActive;
        Minecraft mc = Minecraft.getInstance();
        if (overlayActive) {
            if (selected == null && !ENTRIES.isEmpty()) {
                selected = ENTRIES.keySet().iterator().next();
            }
            ClientCompat.setScreen(mc, new CuteEditorScreen());
        } else if (ClientCompat.currentScreen(mc) instanceof CuteEditorScreen) {
            ClientCompat.setScreen(mc, null);
        }
    }

    @Nullable
    public static String selectedName() { return selected; }

    public static void select(@Nullable String name) { selected = name; }

    @Nullable
    public static LayoutEntry selectedEntry() {
        return selected == null ? null : ENTRIES.get(selected);
    }

    public static void resetAll() {
        for (LayoutEntry e : ENTRIES.values()) e.clearOverrides();
    }

    public static void resetSelected() {
        LayoutEntry e = selectedEntry();
        if (e != null) e.clearOverrides();
    }

    public static void pushUndo() {
        Snapshot snap = Snapshot.capture(ENTRIES.values());
        if (!UNDO.isEmpty() && UNDO.peek().equals(snap)) return;
        UNDO.push(snap);
        while (UNDO.size() > MAX_UNDO) UNDO.pollLast();
    }

    public static boolean undo() {
        Snapshot snap = UNDO.poll();
        if (snap == null) return false;
        snap.restore(ENTRIES);
        return true;
    }

    public static int undoDepth() { return UNDO.size(); }

    public static void clearUndo() { UNDO.clear(); }

    private record Snapshot(Map<String, EntrySnap> entries) {
        static Snapshot capture(Collection<LayoutEntry> values) {
            Map<String, EntrySnap> m = new HashMap<>();
            for (LayoutEntry e : values) m.put(e.name, EntrySnap.of(e));
            return new Snapshot(m);
        }

        void restore(Map<String, LayoutEntry> live) {
            for (var entry : entries.entrySet()) {
                LayoutEntry e = live.get(entry.getKey());
                if (e == null) continue;
                entry.getValue().applyTo(e);
            }
        }
    }

    private record EntrySnap(
            @Nullable Vector3f pos,
            @Nullable Float scale,
            Map<String, Float> floats,
            Map<String, Integer> ints) {
        static EntrySnap of(LayoutEntry e) {
            return new EntrySnap(
                    e.posOverride == null ? null : new Vector3f(e.posOverride),
                    e.scaleOverride,
                    new HashMap<>(e.floatOverrides),
                    new HashMap<>(e.intOverrides));
        }

        void applyTo(LayoutEntry e) {
            e.posOverride = pos == null ? null : new Vector3f(pos);
            e.scaleOverride = scale;
            e.floatOverrides.clear();
            e.floatOverrides.putAll(floats);
            e.intOverrides.clear();
            e.intOverrides.putAll(ints);
        }
    }

    private static final float TRIAD_LEN = 1f / 8f;

    public static void drawTriad(PoseStack pose, SubmitNodeCollector collector,
                                 double x, double y, double z, boolean isSelected) {
        float ax = (float) x, ay = (float) y, az = (float) z;
        collector.submitCustomGeometry(pose, RenderTypes.lines(), (entry, vc) -> {
        float i = isSelected ? 1.0f : 0.7f;
        HoverHighlightRenderer.line(vc, entry, ax, ay, az, ax + TRIAD_LEN, ay, az,        i, 0f, 0f, 1f);
        HoverHighlightRenderer.line(vc, entry, ax, ay, az, ax, ay + TRIAD_LEN, az,        0f, i, 0f, 1f);
        HoverHighlightRenderer.line(vc, entry, ax, ay, az, ax, ay, az + TRIAD_LEN,        0f, 0f, i, 1f);
        if (isSelected) {
            float c = TRIAD_LEN * 0.4f;
            HoverHighlightRenderer.line(vc, entry, ax - c, ay, az, ax + c, ay, az, 1f, 1f, 0f, 1f);
            HoverHighlightRenderer.line(vc, entry, ax, ay - c, az, ax, ay + c, az, 1f, 1f, 0f, 1f);
            HoverHighlightRenderer.line(vc, entry, ax, ay, az - c, ax, ay, az + c, 1f, 1f, 0f, 1f);
            }
        });
    }

    public static void drawGizmos(PoseStack pose, SubmitNodeCollector collector, LayoutEntry... entries) {
        if (!overlayActive) return;
        for (LayoutEntry e : entries) {
            Vector3f p = e.pos();
            drawTriad(pose, collector, p.x, p.y, p.z, e.name.equals(selected));
        }
    }

    public static String exportToJava() {
        StringBuilder sb = new StringBuilder();
        sb.append("// CuteEditor overrides — paste into source, fold values into layout() defaults.\n");
        for (LayoutEntry e : ENTRIES.values()) {
            if (!e.hasOverrides()) continue;
            sb.append("// ").append(e.name).append('\n');
            if (e.posOverride != null || e.scaleOverride != null) {
                sb.append(String.format(java.util.Locale.ROOT,
                        "CuteEditor.layout(\"%s\", new Vector3f(%sf, %sf, %sf), %sf);%n",
                        e.name,
                        fmt(e.pos().x), fmt(e.pos().y), fmt(e.pos().z),
                        fmt(e.scale())));
            }
            for (var f : e.floatOverrides.entrySet()) {
                sb.append(String.format(java.util.Locale.ROOT,
                        "//   .floatField(\"%s\", %sf)%n", f.getKey(), fmt(f.getValue())));
            }
            for (var ie : e.intOverrides.entrySet()) {
                sb.append(String.format(java.util.Locale.ROOT,
                        "//   .intField(\"%s\", %d)%n", ie.getKey(), ie.getValue()));
            }
        }
        if (sb.length() == 0
                || sb.indexOf("//", sb.indexOf("\n") + 1) == sb.indexOf("\n") + 1
                && !ENTRIES.values().stream().anyMatch(LayoutEntry::hasOverrides)) {
            sb.append("// (no overrides set)\n");
        }
        return sb.toString();
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }

    public static void setPos(LayoutEntry e, float x, float y, float z) {
        if (e.posOverride == null) e.posOverride = new Vector3f();
        e.posOverride.set(x, y, z);
    }

    public static void setScale(LayoutEntry e, float scale) { e.scaleOverride = scale; }
    public static void setFloat(LayoutEntry e, String key, float v) { e.floatOverrides.put(key, v); }
    public static void setInt(LayoutEntry e, String key, int v)     { e.intOverrides.put(key, v); }
}
