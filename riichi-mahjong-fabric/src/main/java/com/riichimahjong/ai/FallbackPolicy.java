package com.riichimahjong.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/** Small deterministic policy used only when the external engine is unavailable. */
public final class FallbackPolicy {
    private FallbackPolicy() {}

    public static int chooseIndex(JsonObject snapshot) {
        JsonArray legal = snapshot.getAsJsonArray("legal");
        int automatic = automatic(legal);
        if (automatic >= 0) return automatic;
        int[] hand = counts(snapshot.getAsJsonArray("hand"));
        int[] visible = visible(snapshot);
        int open = snapshot.getAsJsonArray("melds").get(snapshot.get("seat").getAsInt()).getAsJsonArray().size();
        int best = -1, bestShanten = Integer.MAX_VALUE, bestUkeire = -1;
        double bestRetained = -Double.MAX_VALUE;
        for (var element : legal) {
            var action = element.getAsJsonObject();
            String type = action.get("type").getAsString();
            if (!type.equals("Discard") && !type.equals("DiscardWithRiichi")) continue;
            String tile = action.get("tile").getAsString();
            int kind = kind(tile);
            hand[kind]--;
            int shanten = shanten(hand, open);
            int ukeire = 0;
            if (shanten <= bestShanten) {
                for (int draw = 0; draw < 34; draw++) {
                    int remaining = Math.max(0, 4 - visible[draw]);
                    if (remaining == 0 || hand[draw] >= 4) continue;
                    hand[draw]++;
                    if (shanten(hand, open) < shanten) ukeire += remaining;
                    hand[draw]--;
                }
            }
            double retained = -discardValue(tile, snapshot);
            if (type.equals("DiscardWithRiichi")) retained += 0.25;
            hand[kind]++;
            if (shanten < bestShanten || shanten == bestShanten && (ukeire > bestUkeire || ukeire == bestUkeire && retained > bestRetained)) {
                best = action.get("index").getAsInt(); bestShanten = shanten; bestUkeire = ukeire; bestRetained = retained;
            }
        }
        return best >= 0 ? best : cheapIndex(snapshot);
    }

    /** Bounded, non-searching emergency choice, safe to use when the worker queue is full. */
    public static int cheapIndex(JsonObject snapshot) {
        JsonArray legal = snapshot.getAsJsonArray("legal");
        int automatic = automatic(legal);
        if (automatic >= 0) return automatic;
        int[] hand = counts(snapshot.getAsJsonArray("hand"));
        double lowest = Double.POSITIVE_INFINITY;
        int best = -1;
        for (var element : legal) {
            var action = element.getAsJsonObject();
            String type = action.get("type").getAsString();
            if (!type.equals("Discard") && !type.equals("DiscardWithRiichi")) continue;
            String tile = action.get("tile").getAsString();
            int kind = kind(tile), rank = kind % 9;
            double connected = (hand[kind] - 1) * 4 + discardValue(tile, snapshot);
            if (kind < 27) {
                if (rank > 0) connected += hand[kind - 1] * 2;
                if (rank < 8) connected += hand[kind + 1] * 2;
                if (rank > 1) connected += hand[kind - 2];
                if (rank < 7) connected += hand[kind + 2];
            }
            if (type.equals("DiscardWithRiichi")) connected -= 0.25;
            if (connected < lowest) { lowest = connected; best = action.get("index").getAsInt(); }
        }
        return best >= 0 ? best : legal.get(0).getAsJsonObject().get("index").getAsInt();
    }

    private static int automatic(JsonArray legal) {
        for (var element : legal) {
            var a = element.getAsJsonObject();
            String type = a.get("type").getAsString();
            if (type.equals("DeclareRon") || type.equals("DeclareTsumo") || type.equals("DeclareChankan") || type.equals("Draw")) return a.get("index").getAsInt();
        }
        boolean discard = false;
        for (var element : legal) if (element.getAsJsonObject().get("type").getAsString().startsWith("Discard")) discard = true;
        if (!discard) for (var element : legal) {
            var a = element.getAsJsonObject();
            if (a.get("type").getAsString().equals("Pass")) return a.get("index").getAsInt();
        }
        return -1;
    }

    private static double discardValue(String tile, JsonObject snapshot) {
        int kind = kind(tile);
        double value = tile.charAt(1) == '0' ? 3 : 0;
        if (kind >= 31 || kind == 27 + snapshot.get("roundWind").getAsInt()
                || kind == 27 + Math.floorMod(snapshot.get("seat").getAsInt() - snapshot.get("dealer").getAsInt(), 4)) value += 0.5;
        for (var indicator : snapshot.getAsJsonArray("doraIndicators")) {
            int dora = kind(indicator.getAsString());
            dora = dora < 27 ? dora / 9 * 9 + (dora % 9 + 1) % 9 : dora < 31 ? 27 + (dora - 27 + 1) % 4 : 31 + (dora - 31 + 1) % 3;
            if (kind == dora) value += 2;
        }
        return value;
    }

    private static int[] counts(JsonArray tiles) {
        int[] counts = new int[34];
        for (var tile : tiles) counts[kind(tile.getAsString())]++;
        return counts;
    }

    private static int[] visible(JsonObject snapshot) {
        int[] counts = counts(snapshot.getAsJsonArray("hand"));
        for (var river : snapshot.getAsJsonArray("rivers")) for (var element : river.getAsJsonArray()) {
            var tile = element.getAsJsonObject();
            if (!tile.get("called").getAsBoolean()) counts[kind(tile.get("tile").getAsString())]++;
        }
        for (var melds : snapshot.getAsJsonArray("melds")) for (var meld : melds.getAsJsonArray())
            for (var tile : meld.getAsJsonObject().getAsJsonArray("tiles")) counts[kind(tile.getAsString())]++;
        for (var tile : snapshot.getAsJsonArray("doraIndicators")) counts[kind(tile.getAsString())]++;
        return counts;
    }

    private static int kind(String tile) {
        int rank = tile.charAt(1) - '0';
        if (rank == 0) rank = 5;
        return switch (tile.charAt(0)) { case 'm' -> rank - 1; case 'p' -> rank + 8; case 's' -> rank + 17; default -> rank + 26; };
    }

    /** Standard shanten with open melds, plus closed seven-pairs and thirteen-orphans. */
    public static int shanten(int[] counts, int openMelds) {
        int best = new Search(openMelds).run(counts.clone(), 0, 0, 0);
        if (openMelds == 0) {
            int pairs = 0, kinds = 0, terminals = 0; boolean terminalPair = false;
            for (int i = 0; i < 34; i++) {
                if (counts[i] > 0) kinds++;
                if (counts[i] >= 2) pairs++;
                if ((i >= 27 || i % 9 == 0 || i % 9 == 8) && counts[i] > 0) { terminals++; terminalPair |= counts[i] >= 2; }
            }
            best = Math.min(best, 6 - pairs + Math.max(0, 7 - kinds));
            best = Math.min(best, 13 - terminals - (terminalPair ? 1 : 0));
        }
        return best;
    }

    private record Key(long first, long second, int melds, int partials, int pair) {}
    private static final class Search {
        final int open;
        final Map<Key, Integer> cache = new HashMap<>();
        Search(int open) { this.open = open; }
        int run(int[] c, int melds, int partials, int pair) {
            int index = 0;
            while (index < 34 && c[index] == 0) index++;
            if (index == 34) return 8 - 2 * (open + melds) - Math.min(partials, 4 - open - melds) - pair;
            long first = 0, second = 0;
            for (int i = 0; i < 17; i++) first = first * 5 + c[i];
            for (int i = 17; i < 34; i++) second = second * 5 + c[i];
            var key = new Key(first, second, melds, partials, pair);
            Integer saved = cache.get(key);
            if (saved != null) return saved;
            c[index]--;
            int best = run(c, melds, partials, pair);
            c[index]++;
            if (open + melds < 4) {
                if (c[index] >= 3) { c[index] -= 3; best = Math.min(best, run(c, melds + 1, partials, pair)); c[index] += 3; }
                if (index < 27 && index % 9 <= 6 && c[index + 1] > 0 && c[index + 2] > 0) {
                    c[index]--; c[index + 1]--; c[index + 2]--;
                    best = Math.min(best, run(c, melds + 1, partials, pair));
                    c[index]++; c[index + 1]++; c[index + 2]++;
                }
            }
            if (c[index] >= 2) {
                c[index] -= 2;
                if (pair == 0) best = Math.min(best, run(c, melds, partials, 1));
                if (open + melds + partials < 4) best = Math.min(best, run(c, melds, partials + 1, pair));
                c[index] += 2;
            }
            if (index < 27 && open + melds + partials < 4) for (int gap = 1; gap <= 2; gap++) {
                if (index % 9 + gap > 8 || c[index + gap] == 0) continue;
                c[index]--; c[index + gap]--;
                best = Math.min(best, run(c, melds, partials + 1, pair));
                c[index]++; c[index + gap]++;
            }
            cache.put(key, best);
            return best;
        }
    }
}
