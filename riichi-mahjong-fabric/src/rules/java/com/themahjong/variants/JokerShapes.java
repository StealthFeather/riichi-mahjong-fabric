package com.themahjong.variants;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.HandShape;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Expand wildcard assignments only for shape/score evaluation; the physical hand never changes. */
public final class JokerShapes {
    private JokerShapes() {}
    private record Key(List<TheMahjongTile> tiles, List<TheMahjongMeld> melds) {}
    private static final Map<Key,List<HandShape>> CACHE = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key,List<HandShape>> entry) { return size() > 2048; }
    };
    private static final List<TheMahjongTile> ORDINARY = ordinary();

    public static List<TheMahjongTile> ordinaryTiles() { return ORDINARY; }

    private static List<TheMahjongTile> ordinary() {
        var out = new ArrayList<TheMahjongTile>();
        for (var suit : TheMahjongTile.Suit.values()) for (int rank = 1; rank <= suit.maxRank(); rank++)
            out.add(new TheMahjongTile(suit, rank, false));
        return List.copyOf(out);
    }

    public static List<HandShape> decompose(List<TheMahjongTile> hand, List<TheMahjongMeld> melds) {
        var key = new Key(hand.stream().sorted(TheMahjongTile.DISPLAY_ORDER).toList(), List.copyOf(melds));
        synchronized (CACHE) { var saved = CACHE.get(key); if (saved != null) return saved; }
        int wild = (int) hand.stream().filter(Jokers::isJoker).count();
        if (wild == 0) return HandShape.decompose(hand, melds);
        // Valid deals have one fixed joker per player; reject malformed records with impossible counts.
        if (wild > 4 || melds.stream().flatMap(meld -> meld.tiles().stream()).anyMatch(Jokers::isJoker)) return List.of();
        var physical = new ArrayList<>(hand.stream().filter(tile -> !Jokers.isJoker(tile)).toList());
        var out = new LinkedHashSet<HandShape>();
        assign(physical, melds, wild, 0, out);
        var result = List.copyOf(out);
        synchronized (CACHE) { CACHE.put(key, result); }
        return result;
    }

    private static void assign(ArrayList<TheMahjongTile> hand, List<TheMahjongMeld> melds,
            int remaining, int first, LinkedHashSet<HandShape> out) {
        if (remaining == 0) { out.addAll(HandShape.decompose(hand, melds)); return; }
        for (int kind = first; kind < ORDINARY.size(); kind++) {
            hand.add(ORDINARY.get(kind));
            assign(hand, melds, remaining - 1, kind, out);
            hand.removeLast();
        }
    }
}
