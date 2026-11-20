package com.riichimahjong.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

/** Projects this hand's accepted replay transitions into one seat's MJAI observations. */
final class MortalEvents {
    private MortalEvents() {}

    static JsonArray forSeat(List<ReplayRecord.Frame> frames, int seat) {
        return forSeat(frames, seat, false);
    }

    /** Older archives displayed an equal-kind discarded copy as the drawn tile. */
    static JsonArray forReplayFrame(List<ReplayRecord.Frame> frames, int index, int seat) {
        return forSeat(frames.subList(0, index + 1), seat, true);
    }

    private static JsonArray forSeat(List<ReplayRecord.Frame> frames, int seat, boolean historical) {
        if (frames.isEmpty() || seat < 0 || seat > 3) return new JsonArray();
        int start = frames.size() - 1;
        int roundIndex = frames.get(start).roundIndex();
        while (start > 0 && frames.get(start - 1).roundIndex() == roundIndex) start--;

        var first = frames.get(start).driver();
        var match = first.getCompoundOrEmpty("match");
        if (match.getIntOr("playerCount", 0) != 4 || match.getBooleanOr("sichuan", false)
                || match.getBooleanOr("bloodBattle", false) || match.getBooleanOr("exchangeThree", false)
                || match.getBooleanOr("jokersEnabled", false)) return new JsonArray();
        try {
            var events = new JsonArray();
            var game = event("start_game");
            game.addProperty("id", seat);
            events.add(game);
            var before = round(first);
            require(phase(first).equals("AWAITING_DRAW") && before.getIntOr("currentTurnSeat", -1) == before.getIntOr("dealerSeat", -2));
            events.add(startKyoku(before, seat));
            for (int i = start + 1; i < frames.size(); i++) {
                var previous = frames.get(i - 1).driver();
                var current = frames.get(i).driver();
                var after = round(current);
                transition(events, previous, current, before, after, seat, frames.get(i).tsumogiri() == null && historical ? Boolean.TRUE : frames.get(i).tsumogiri());
                before = after;
            }
            return events;
        } catch (IllegalArgumentException incomplete) {
            // Imported positions and old saves can begin mid-hand or lack the origin of an equal-copy discard.
            return new JsonArray();
        }
    }

    private static JsonObject startKyoku(CompoundTag round, int seat) {
        var start = event("start_kyoku");
        String wind = round.getStringOr("roundWind", "");
        start.addProperty("bakaze", switch (wind) {
            case "EAST" -> "E"; case "SOUTH" -> "S"; case "WEST" -> "W"; case "NORTH" -> "N";
            default -> throw new IllegalArgumentException("Unknown round wind");
        });
        int hand = round.getIntOr("handNumber", 0);
        require(hand >= 1 && hand <= 4 && round.getIntOr("revealedDoraCount", 0) == 1);
        start.addProperty("kyoku", hand);
        start.addProperty("honba", round.getIntOr("honba", 0));
        start.addProperty("kyotaku", round.getIntOr("riichiSticks", 0));
        start.addProperty("oya", round.getIntOr("dealerSeat", -1));
        start.addProperty("dora_marker", tile(round.getListOrEmpty("doraIndicators").getCompoundOrEmpty(0)));
        var points = new JsonArray();
        var hands = new JsonArray();
        require(round.getListOrEmpty("players").size() == 4);
        for (int actor = 0; actor < 4; actor++) {
            var player = player(round, actor);
            require(player.getListOrEmpty("discards").isEmpty() && player.getListOrEmpty("melds").isEmpty());
            var handTiles = player.getListOrEmpty("currentHand");
            require(handTiles.size() == 13);
            points.add(player.getIntOr("points", 0));
            var concealed = new JsonArray();
            for (int i = 0; i < 13; i++) concealed.add(actor == seat ? tile(handTiles.getCompoundOrEmpty(i)) : "?");
            hands.add(concealed);
        }
        start.add("scores", points);
        start.add("tehais", hands);
        return start;
    }

    private static void transition(JsonArray events, CompoundTag previous, CompoundTag current,
                                   CompoundTag before, CompoundTag after, int seat, Boolean physicalDiscard) {
        var previousPhase = phase(previous);
        var currentPhase = phase(current);
        int actor = before.getIntOr("currentTurnSeat", -1);
        require(actor >= 0 && actor < 4);
        var oldRiver = player(before, actor).getListOrEmpty("discards");
        var newRiver = player(after, actor).getListOrEmpty("discards");
        boolean discarded = newRiver.size() == oldRiver.size() + 1;
        if (discarded) {
            require(previousPhase.equals("AWAITING_DISCARD"));
            var discard = newRiver.getCompoundOrEmpty(oldRiver.size());
            // Delayed kan-dora becomes public on the discard that opponents can call.
            if (discard.getBooleanOr("riichiDeclared", false)) events.add(action("reach", actor));
            dora(events, before, after);
            var dahai = action("dahai", actor);
            dahai.addProperty("pai", tile(discard.getCompoundOrEmpty("tile")));
            dahai.addProperty("tsumogiri", tsumogiri(before, actor, discard.getCompoundOrEmpty("tile"), physicalDiscard));
            events.add(dahai);
        }
        if (after.getIntOr("riichiSticks", 0) == before.getIntOr("riichiSticks", 0) + 1) {
            // When a declaration discard is called, the deposit precedes the accepted meld.
            int declarer = previous.getIntOr("pendingRiichiCommit", actor);
            events.add(action("reach_accepted", declarer));
        }
        for (int caller = 0; caller < 4; caller++) {
            var oldMelds = player(before, caller).getListOrEmpty("melds");
            var newMelds = player(after, caller).getListOrEmpty("melds");
            require(newMelds.size() >= oldMelds.size() && newMelds.size() <= oldMelds.size() + 1);
            for (int i = 0; i < newMelds.size(); i++) {
                var meld = newMelds.getCompoundOrEmpty(i);
                if (i == oldMelds.size() || !meld.equals(oldMelds.getCompoundOrEmpty(i))) events.add(meld(caller, meld));
            }
        }
        if (!discarded) dora(events, before, after);
        if (previousPhase.equals("AWAITING_DRAW") && currentPhase.equals("AWAITING_DISCARD")) {
            var active = after.getCompoundOrEmpty("activeTile");
            require(active.getStringOr("type", "").equals("DRAWN") && after.getIntOr("currentTurnSeat", -1) == actor);
            var draw = action("tsumo", actor);
            draw.addProperty("pai", actor == seat ? tile(active.getCompoundOrEmpty("tile")) : "?");
            events.add(draw);
        }
    }

    /** Old replays can resolve every unambiguous discard even without the optional physical-slot flag. */
    private static boolean tsumogiri(CompoundTag before, int actor, CompoundTag discarded, Boolean physicalDiscard) {
        var active = before.getCompoundOrEmpty("activeTile");
        if (!active.getStringOr("type", "").equals("DRAWN") || !discarded.equals(active.getCompoundOrEmpty("tile"))) return false;
        var prior = player(before, actor);
        if (!prior.getStringOr("riichiState", "NONE").equals("NONE")) return true;
        if (physicalDiscard != null) return physicalDiscard;
        var hand = prior.getListOrEmpty("currentHand");
        for (int i = 0; i < hand.size() - 1; i++) require(!discarded.equals(hand.getCompoundOrEmpty(i)));
        return true;
    }

    private static void dora(JsonArray events, CompoundTag before, CompoundTag after) {
        int oldCount = before.getIntOr("revealedDoraCount", 0);
        int newCount = after.getIntOr("revealedDoraCount", 0);
        var indicators = after.getListOrEmpty("doraIndicators");
        require(newCount >= oldCount && newCount <= indicators.size());
        for (int i = oldCount; i < newCount; i++) {
            var dora = event("dora");
            dora.addProperty("dora_marker", tile(indicators.getCompoundOrEmpty(i)));
            events.add(dora);
        }
    }

    private static JsonObject meld(int actor, CompoundTag meld) {
        String type = meld.getStringOr("type", "");
        var event = action(type.toLowerCase(Locale.ROOT), actor);
        var consumed = new JsonArray();
        if (type.equals("KAKAN")) {
            event.addProperty("pai", tile(meld.getCompoundOrEmpty("addedTile")));
            var ponTiles = meld.getCompoundOrEmpty("upgradedFrom").getListOrEmpty("tiles");
            require(ponTiles.size() == 3);
            for (int i = 0; i < 3; i++) consumed.add(tile(ponTiles.getCompoundOrEmpty(i)));
        } else {
            var tiles = meld.getListOrEmpty("tiles");
            int claimed = -1;
            if (type.equals("ANKAN")) {
                require(tiles.size() == 4);
            } else {
                require(type.equals("CHI") || type.equals("PON") || type.equals("DAIMINKAN"));
                require(tiles.size() == (type.equals("DAIMINKAN") ? 4 : 3));
                claimed = meld.getIntOr("claimedTileIndex", -1);
                int target = meld.getIntOr("sourceSeat", -1);
                require(claimed >= 0 && claimed < tiles.size() && target >= 0 && target < 4 && target != actor);
                event.addProperty("target", target);
                event.addProperty("pai", tile(tiles.getCompoundOrEmpty(claimed)));
            }
            for (int i = 0; i < tiles.size(); i++) if (i != claimed) consumed.add(tile(tiles.getCompoundOrEmpty(i)));
        }
        event.add("consumed", consumed);
        return event;
    }

    private static CompoundTag round(CompoundTag driver) { return driver.getCompoundOrEmpty("match").getCompoundOrEmpty("currentRound"); }
    private static CompoundTag player(CompoundTag round, int seat) { return round.getListOrEmpty("players").getCompoundOrEmpty(seat); }
    private static String phase(CompoundTag driver) { return driver.getCompoundOrEmpty("phase").getStringOr("type", ""); }
    private static JsonObject event(String type) { var event = new JsonObject(); event.addProperty("type", type); return event; }
    private static JsonObject action(String type, int seat) { var action = event(type); action.addProperty("actor", seat); return action; }
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException("Incomplete MJAI hand"); }

    private static String tile(CompoundTag tag) {
        String code = ReplayTimeline.tileCode(tag);
        require(code != null);
        char suit = code.charAt(0), rank = code.charAt(1);
        if (suit == 'z') return String.valueOf("ESWNPFC".charAt(rank - '1'));
        return rank == '0' ? "5" + suit + "r" : "" + rank + suit;
    }
}
