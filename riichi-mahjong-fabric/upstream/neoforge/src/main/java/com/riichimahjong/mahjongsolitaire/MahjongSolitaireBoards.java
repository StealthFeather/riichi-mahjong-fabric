package com.riichimahjong.mahjongsolitaire;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;

public final class MahjongSolitaireBoards {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE_PATH =
            "/data/riichi_mahjong/solitaire/boards.json";

    private static List<MahjongSolitaireBoard> cached;

    private MahjongSolitaireBoards() {}

    public static synchronized List<MahjongSolitaireBoard> all() {
        if (cached == null) {
            cached = loadFromClasspath();
        }
        return cached;
    }

    private static List<MahjongSolitaireBoard> loadFromClasspath() {
        try (InputStream in = MahjongSolitaireBoards.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.warn("solitaire boards resource not found at {}", RESOURCE_PATH);
                return Collections.emptyList();
            }
            JsonElement root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) {
                LOGGER.warn("solitaire boards root is not a JSON array");
                return Collections.emptyList();
            }
            ArrayList<MahjongSolitaireBoard> out = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray()) {
                MahjongSolitaireBoard board = parseBoard(e.getAsJsonObject());
                if (board != null) {
                    out.add(board);
                }
            }
            LOGGER.info("loaded {} solitaire boards", out.size());
            return Collections.unmodifiableList(out);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("failed to load solitaire boards", e);
            return Collections.emptyList();
        }
    }

    private static MahjongSolitaireBoard parseBoard(JsonObject obj) {
        String id = optString(obj, "id", "");
        String name = optString(obj, "name", id);
        String cat = optString(obj, "cat", "");
        if (!obj.has("map") || !obj.get("map").isJsonArray()) {
            return null;
        }
        ArrayList<MahjongSolitaireBoard.Slot> slots = new ArrayList<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int maxLayer = 0;
        for (JsonElement layerElem : obj.getAsJsonArray("map")) {
            JsonArray layer = layerElem.getAsJsonArray();
            int y = layer.get(0).getAsInt();
            maxLayer = Math.max(maxLayer, y);
            JsonElement rowsElem = layer.get(1);
            if (!rowsElem.isJsonArray()) {
                continue;
            }
            for (JsonElement rowElem : rowsElem.getAsJsonArray()) {
                JsonArray row = rowElem.getAsJsonArray();
                int z = row.get(0).getAsInt();
                JsonElement items = row.get(1);
                parseRowItems(items, y, z, slots);
            }
        }
        for (var s : slots) {
            if (s.x() < minX) minX = s.x();
            if (s.x() > maxX) maxX = s.x();
            if (s.z() < minZ) minZ = s.z();
            if (s.z() > maxZ) maxZ = s.z();
        }
        if (slots.isEmpty()) {
            return null;
        }
        return new MahjongSolitaireBoard(
                id, name, cat, List.copyOf(slots), minX, maxX, minZ, maxZ, maxLayer);
    }

    private static void parseRowItems(
            JsonElement items, int y, int z, List<MahjongSolitaireBoard.Slot> sink) {
        if (items.isJsonPrimitive()) {
            sink.add(new MahjongSolitaireBoard.Slot(items.getAsInt(), y, z));
            return;
        }
        if (!items.isJsonArray()) {
            return;
        }
        for (JsonElement item : items.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                sink.add(new MahjongSolitaireBoard.Slot(item.getAsInt(), y, z));
            } else if (item.isJsonArray()) {
                JsonArray run = item.getAsJsonArray();
                if (run.size() < 2) continue;
                int startX = run.get(0).getAsInt();
                int length = run.get(1).getAsInt();
                for (int i = 0; i < length; i++) {
                    sink.add(new MahjongSolitaireBoard.Slot(
                            startX + i * MahjongSolitaireBoard.TILE_GRID, y, z));
                }
            }
        }
    }

    private static String optString(JsonObject obj, String key, String dflt) {
        return obj.has(key) && obj.get(key).isJsonPrimitive()
                ? obj.get(key).getAsString() : dflt;
    }
}
