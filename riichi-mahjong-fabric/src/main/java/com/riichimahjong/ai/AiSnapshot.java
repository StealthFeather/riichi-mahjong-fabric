package com.riichimahjong.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import java.util.HashSet;
import java.util.Set;

/** Explicit allowlist for AI input. No opponent hand, wall order or unrevealed indicator is serialized. */
public final class AiSnapshot {
    private AiSnapshot() {}

    public static JsonObject create(DecisionRequest request, BotDifficulty difficulty) {
        return create(request, difficulty, !request.rules().equals(TheMahjongRuleSet.wrc()));
    }

    public static JsonObject create(DecisionRequest request, BotDifficulty difficulty, boolean redFives) {
        var round = request.round();
        if (round.players().size() != 4) throw new IllegalArgumentException("Kobalab AI supports four players only");
        var out = new JsonObject();
        out.addProperty("difficulty", difficulty.name());
        out.addProperty("seat", request.seat());
        out.addProperty("dealer", round.dealerSeat());
        out.addProperty("roundWind", round.roundWind().ordinal());
        out.addProperty("handNumber", round.handNumber());
        out.addProperty("honba", round.honba());
        out.addProperty("riichiSticks", round.riichiSticks());
        out.addProperty("remaining", round.liveWall().size());
        out.addProperty("phase", request.phase() instanceof MatchPhase.AwaitingClaims ? "claim" : "discard");
        out.add("hand", tiles(round.players().get(request.seat()).currentHand()));
        if (round.currentTurnSeat() == request.seat() && round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn draw)
            out.addProperty("drawn", tile(draw.tile()));
        else out.add("drawn", JsonNull.INSTANCE);

        var points = new JsonArray();
        var riichi = new JsonArray();
        var melds = new JsonArray();
        Set<String> called = new HashSet<>();
        for (var player : round.players()) {
            points.add(player.points());
            riichi.add(player.riichi());
            var list = new JsonArray();
            for (var meld : player.melds()) {
                var serialized = meld(meld);
                list.add(serialized);
                if (serialized.has("sourceSeat")) called.add(serialized.get("sourceSeat").getAsInt() + ":" + serialized.get("sourceDiscardIndex").getAsInt());
            }
            melds.add(list);
        }
        out.add("points", points);
        out.add("riichi", riichi);
        out.add("melds", melds);
        var rivers = new JsonArray();
        for (int seat = 0; seat < 4; seat++) {
            var river = new JsonArray();
            var discards = round.players().get(seat).discards();
            for (int index = 0; index < discards.size(); index++) {
                var discard = discards.get(index);
                var entry = new JsonObject();
                entry.addProperty("tile", tile(discard.tile()));
                entry.addProperty("riichi", discard.riichiDeclared());
                // The rule engine does not preserve tsumogiri history. Do not invent it.
                entry.addProperty("tsumogiri", false);
                entry.addProperty("called", called.contains(seat + ":" + index));
                river.add(entry);
            }
            rivers.add(river);
        }
        out.add("rivers", rivers);
        out.add("doraIndicators", tiles(round.doraIndicators().subList(0, round.revealedDoraCount())));
        if (request.phase() instanceof MatchPhase.AwaitingClaims claims) {
            var last = new JsonObject();
            last.addProperty("seat", round.claimSourceSeat().orElse(round.currentTurnSeat()));
            last.addProperty("tile", tile(claims.heldTile()));
            out.add("lastDiscard", last);
            out.addProperty("claimSource", claims.source().name());
        } else out.add("lastDiscard", JsonNull.INSTANCE);

        var rules = request.rules();
        var options = new JsonObject();
        options.addProperty("openTanyao", true);
        options.addProperty("redFives", redFives);
        options.addProperty("kiriageMangan", rules.kiriageMangan());
        options.addProperty("doubleWindPairFu4", rules.doubleWindPairFu4());
        options.addProperty("renhouAllowed", rules.renhouAllowed());
        options.addProperty("abortiveDrawsAllowed", rules.abortiveDrawsAllowed());
        options.addProperty("nagashiManganAllowed", rules.nagashiManganAllowed());
        options.addProperty("doubleRonAllowed", rules.doubleRonAllowed());
        options.addProperty("kuikaeForbidden", rules.kuikaeForbidden());
        options.addProperty("strictRiichiAnkan", rules.strictRiichiAnkan());
        options.addProperty("openKanDoraDelayedReveal", rules.openKanDoraDelayedReveal());
        options.addProperty("bustingEndsGame", rules.bustingEndsGame());
        options.addProperty("agariYameAllowed", rules.agariYameAllowed());
        options.addProperty("suddenDeathRound", rules.suddenDeathRound());
        out.add("rules", options);
        var legal = new JsonArray();
        for (int index = 0; index < request.legalActions().size(); index++) {
            var action = request.legalActions().get(index);
            var entry = new JsonObject();
            entry.addProperty("index", index);
            entry.addProperty("type", action.getClass().getSimpleName());
            if (action instanceof PlayerAction.Discard discard) entry.addProperty("tile", tile(discard.tile()));
            else if (action instanceof PlayerAction.DiscardWithRiichi discard) entry.addProperty("tile", tile(discard.tile()));
            else if (action instanceof PlayerAction.Chi chi) entry.add("handTiles", tiles(chi.handTiles()));
            else if (action instanceof PlayerAction.Pon pon) entry.add("handTiles", tiles(pon.handTiles()));
            else if (action instanceof PlayerAction.Daiminkan kan) entry.add("handTiles", tiles(kan.handTiles()));
            else if (action instanceof PlayerAction.Ankan kan) entry.add("handTiles", tiles(kan.handTiles()));
            else if (action instanceof PlayerAction.Kakan kan) {
                entry.addProperty("tile", tile(kan.addedTile()));
                entry.add("upgradedFrom", meld(kan.upgradedFrom()));
            } else if (action instanceof PlayerAction.DeclareKita kita) entry.addProperty("tile", tile(kita.tile()));
            // WinResult intentionally omitted: it is the authoritative engine's private result object.
            legal.add(entry);
        }
        out.add("legal", legal);
        return out;
    }

    public static String tile(TheMahjongTile tile) {
        return switch (tile.suit()) {
            case MANZU -> "m" + (tile.redDora() ? 0 : tile.rank());
            case PINZU -> "p" + (tile.redDora() ? 0 : tile.rank());
            case SOUZU -> "s" + (tile.redDora() ? 0 : tile.rank());
            case WIND -> "z" + tile.rank();
            case DRAGON -> "z" + (tile.rank() + 4);
        };
    }

    private static JsonArray tiles(java.util.List<TheMahjongTile> tiles) {
        var out = new JsonArray();
        for (var tile : tiles) out.add(tile(tile));
        return out;
    }

    private static JsonObject meld(TheMahjongMeld meld) {
        var out = new JsonObject();
        out.addProperty("type", meld.getClass().getSimpleName());
        out.add("tiles", tiles(meld.tiles()));
        TheMahjongMeld source = meld instanceof TheMahjongMeld.Kakan kan ? kan.upgradedFrom() : meld;
        int sourceSeat = -1, sourceDiscard = -1, claimed = -1;
        if (source instanceof TheMahjongMeld.Chi chi) { sourceSeat = chi.sourceSeat(); sourceDiscard = chi.sourceDiscardIndex(); claimed = chi.claimedTileIndex(); }
        else if (source instanceof TheMahjongMeld.Pon pon) { sourceSeat = pon.sourceSeat(); sourceDiscard = pon.sourceDiscardIndex(); claimed = pon.claimedTileIndex(); }
        else if (source instanceof TheMahjongMeld.Daiminkan kan) { sourceSeat = kan.sourceSeat(); sourceDiscard = kan.sourceDiscardIndex(); claimed = kan.claimedTileIndex(); }
        if (sourceSeat >= 0) {
            out.addProperty("sourceSeat", sourceSeat);
            out.addProperty("sourceDiscardIndex", sourceDiscard);
            out.addProperty("claimedTileIndex", claimed);
            out.addProperty("calledTile", tile(source.tiles().get(claimed)));
        }
        if (meld instanceof TheMahjongMeld.Kakan kan) out.addProperty("addedTile", tile(kan.addedTile()));
        return out;
    }
}
