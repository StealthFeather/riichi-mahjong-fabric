package com.riichimahjong.themahjongcompat;

import com.themahjong.*;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MatchNbt {
    private static final int VERSION = 1;

    private MatchNbt() {}

    public static void writeMatch(ValueOutput out, TheMahjongMatch match) {
        out.putInt("v", VERSION);
        out.putInt("playerCount", match.playerCount());
        out.putInt("startingPoints", match.startingPoints());
        out.putInt("targetPoints", match.targetPoints());
        out.putInt("roundCount", match.roundCount());
        out.putString("state", match.state().name());
        writeTileSet(out.child("tileSet"), match.tileSet());
        out.putBoolean("kiriageMangan", match.ruleSet().kiriageMangan());
        out.putBoolean("doubleWindPairFu4", match.ruleSet().doubleWindPairFu4());
        out.putBoolean("renhouAllowed", match.ruleSet().renhouAllowed());
        out.putBoolean("paoOnSuukantsu", match.ruleSet().paoOnSuukantsu());
        out.putBoolean("abortiveDrawsAllowed", match.ruleSet().abortiveDrawsAllowed());
        out.putBoolean("nagashiManganAllowed", match.ruleSet().nagashiManganAllowed());
        out.putBoolean("doubleRonAllowed", match.ruleSet().doubleRonAllowed());
        out.putBoolean("bustingEndsGame", match.ruleSet().bustingEndsGame());
        out.putBoolean("riichiRequires1000Points", match.ruleSet().riichiRequires1000Points());
        out.putBoolean("depositsToFirstAtEnd", match.ruleSet().depositsToFirstAtEnd());
        out.putBoolean("agariYameAllowed", match.ruleSet().agariYameAllowed());
        out.putBoolean("suddenDeathRound", match.ruleSet().suddenDeathRound());
        out.putBoolean("openKanDoraDelayedReveal", match.ruleSet().openKanDoraDelayedReveal());
        out.putBoolean("kuikaeForbidden", match.ruleSet().kuikaeForbidden());
        out.putBoolean("strictRiichiAnkan", match.ruleSet().strictRiichiAnkan());
        out.putBoolean("chiDisabled", match.ruleSet().chiDisabled());
        out.putBoolean("kitaCountsAsDora", match.ruleSet().kitaCountsAsDora());
        out.putBoolean("tsumoLoss", match.ruleSet().tsumoLoss());
        out.putBoolean("kitaDeclineCausesFuriten", match.ruleSet().kitaDeclineCausesFuriten());
        out.putBoolean("sanchahouAbortive", match.ruleSet().sanchahouAbortive());

        ValueOutput.ValueOutputList completedRounds = out.childrenList("completedRounds");
        for (TheMahjongRound round : match.completedRounds()) {
            writeRound(completedRounds.addChild(), round);
        }

        match.currentRound().ifPresent(round -> writeRound(out.child("currentRound"), round));
    }

    public static TheMahjongMatch readMatch(ValueInput in) {
        int v = in.getIntOr("v", 0);
        if (v != VERSION) {
            throw new IllegalArgumentException("Unsupported MatchNbt version: " + v);
        }

        int playerCount = in.getIntOr("playerCount", 0);
        int startingPoints = in.getIntOr("startingPoints", 0);
        int targetPoints = in.getIntOr("targetPoints", 0);
        int roundCount = in.getIntOr("roundCount", 0);
        TheMahjongMatch.State state = TheMahjongMatch.State.valueOf(in.getStringOr("state", ""));
        TheMahjongTileSet tileSet = readTileSet(in.childOrEmpty("tileSet"));
        TheMahjongRuleSet ruleSet = new TheMahjongRuleSet(
                in.getBooleanOr("kiriageMangan", false),
                in.getBooleanOr("doubleWindPairFu4", false),
                in.getBooleanOr("renhouAllowed", false),
                in.getBooleanOr("paoOnSuukantsu", false),
                in.getBooleanOr("abortiveDrawsAllowed", false),
                in.getBooleanOr("nagashiManganAllowed", false),
                in.getBooleanOr("doubleRonAllowed", false),
                in.getBooleanOr("bustingEndsGame", false),
                in.getBooleanOr("riichiRequires1000Points", false),
                in.getBooleanOr("depositsToFirstAtEnd", false),
                in.getBooleanOr("agariYameAllowed", false),
                in.getBooleanOr("suddenDeathRound", false),
                in.getBooleanOr("openKanDoraDelayedReveal", false),
                in.getBooleanOr("kuikaeForbidden", false),
                in.getBooleanOr("strictRiichiAnkan", false),
                in.getBooleanOr("chiDisabled", false),
                in.getBooleanOr("kitaCountsAsDora", false),
                in.getBooleanOr("tsumoLoss", false),
                in.getBooleanOr("kitaDeclineCausesFuriten", false),
                in.getBooleanOr("sanchahouAbortive", false));

        List<TheMahjongRound> completedRounds = new ArrayList<>();
        for (ValueInput round : in.childrenListOrEmpty("completedRounds")) {
            completedRounds.add(readRound(round));
        }

        TheMahjongRound currentRound = in.child("currentRound").map(MatchNbt::readRound).orElse(null);

        return new TheMahjongMatch(playerCount, startingPoints, targetPoints, roundCount, state, tileSet, ruleSet, completedRounds, currentRound);
    }

    private static void writeTileSet(ValueOutput out, TheMahjongTileSet tileSet) {
        ValueOutput.ValueOutputList entries = out.childrenList("entries");
        for (TheMahjongTile tile : tileSet.tiles()) {
            ValueOutput entry = entries.addChild();
            writeTile(entry.child("tile"), tile);
            entry.putInt("copies", tileSet.copiesPerTile().get(tile));
        }
    }

    private static TheMahjongTileSet readTileSet(ValueInput in) {
        List<TheMahjongTile> tiles = new ArrayList<>();
        Map<TheMahjongTile, Integer> copies = new LinkedHashMap<>();
        for (ValueInput entry : in.childrenListOrEmpty("entries")) {
            TheMahjongTile tile = readTile(entry.childOrEmpty("tile"));
            tiles.add(tile);
            copies.put(tile, entry.getIntOr("copies", 0));
        }
        return new TheMahjongTileSet(tiles, copies);
    }

    static void writeRound(ValueOutput out, TheMahjongRound round) {
        out.putString("roundWind", round.roundWind().name());
        out.putInt("handNumber", round.handNumber());
        out.putInt("honba", round.honba());
        out.putInt("riichiSticks", round.riichiSticks());
        out.putInt("dealerSeat", round.dealerSeat());
        out.putString("state", round.state().name());
        out.putInt("currentTurnSeat", round.currentTurnSeat());
        out.putInt("claimSourceSeat", round.claimSourceSeat().orElse(-1));
        writeActiveTile(out.child("activeTile"), round.activeTile());
        writeTileList(out, "liveWall", round.liveWall());
        writeTileList(out, "rinshanTiles", round.rinshanTiles());
        writeTileList(out, "doraIndicators", round.doraIndicators());
        writeTileList(out, "uraDoraIndicators", round.uraDoraIndicators());
        out.putInt("revealedDoraCount", round.revealedDoraCount());
        out.putInt("pendingKanDoraReveals", round.pendingKanDoraReveals());

        ValueOutput.ValueOutputList players = out.childrenList("players");
        for (TheMahjongPlayer player : round.players()) {
            writePlayer(players.addChild(), player);
        }

        int[] pendingDeltas = new int[round.pendingDeltas().size()];
        for (int i = 0; i < pendingDeltas.length; i++) {
            pendingDeltas[i] = round.pendingDeltas().get(i);
        }
        out.putIntArray("pendingDeltas", pendingDeltas);
    }

    static TheMahjongRound readRound(ValueInput in) {
        TheMahjongTile.Wind roundWind = TheMahjongTile.Wind.valueOf(in.getStringOr("roundWind", ""));
        int handNumber = in.getIntOr("handNumber", 0);
        int honba = in.getIntOr("honba", 0);
        int riichiSticks = in.getIntOr("riichiSticks", 0);
        int dealerSeat = in.getIntOr("dealerSeat", 0);
        TheMahjongRound.State state = TheMahjongRound.State.valueOf(in.getStringOr("state", ""));
        int currentTurnSeat = in.getIntOr("currentTurnSeat", 0);
        int claimSourceSeat = in.getIntOr("claimSourceSeat", -1);
        TheMahjongRound.ActiveTile activeTile = readActiveTile(in.childOrEmpty("activeTile"));
        List<TheMahjongTile> liveWall = readTileList(in, "liveWall");
        List<TheMahjongTile> rinshanTiles = readTileList(in, "rinshanTiles");
        List<TheMahjongTile> doraIndicators = readTileList(in, "doraIndicators");
        List<TheMahjongTile> uraDoraIndicators = readTileList(in, "uraDoraIndicators");
        int revealedDoraCount = in.getIntOr("revealedDoraCount", 0);
        int pendingKanDoraReveals = in.getIntOr("pendingKanDoraReveals", 0);

        List<TheMahjongPlayer> players = new ArrayList<>();
        for (ValueInput player : in.childrenListOrEmpty("players")) {
            players.add(readPlayer(player));
        }

        List<Integer> pendingDeltas = new ArrayList<>();
        for (int delta : in.getIntArray("pendingDeltas").orElse(new int[0])) {
            pendingDeltas.add(delta);
        }

        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                state, currentTurnSeat, claimSourceSeat, activeTile,
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators,
                revealedDoraCount, pendingKanDoraReveals, players, pendingDeltas);
    }

    private static void writeActiveTile(ValueOutput out, TheMahjongRound.ActiveTile activeTile) {
        if (activeTile instanceof TheMahjongRound.ActiveTile.None) {
            out.putString("type", "NONE");
        } else if (activeTile instanceof TheMahjongRound.ActiveTile.Drawn drawn) {
            out.putString("type", "DRAWN");
            writeTile(out.child("tile"), drawn.tile());
        } else if (activeTile instanceof TheMahjongRound.ActiveTile.HeldDiscard held) {
            out.putString("type", "HELD_DISCARD");
            writeTile(out.child("tile"), held.tile());
        } else if (activeTile instanceof TheMahjongRound.ActiveTile.HeldKita held) {
            out.putString("type", "HELD_KITA");
            writeTile(out.child("tile"), held.tile());
        } else if (activeTile instanceof TheMahjongRound.ActiveTile.HeldKakan held) {
            out.putString("type", "HELD_KAKAN");
            writeTile(out.child("tile"), held.tile());
        }
    }

    private static TheMahjongRound.ActiveTile readActiveTile(ValueInput in) {
        String type = in.getStringOr("type", "");
        return switch (type) {
            case "NONE" -> TheMahjongRound.ActiveTile.none();
            case "DRAWN" -> TheMahjongRound.ActiveTile.drawn(readTile(in.childOrEmpty("tile")));
            case "HELD_DISCARD" -> TheMahjongRound.ActiveTile.heldDiscard(readTile(in.childOrEmpty("tile")));
            case "HELD_KITA" -> TheMahjongRound.ActiveTile.heldKita(readTile(in.childOrEmpty("tile")));
            case "HELD_KAKAN" -> TheMahjongRound.ActiveTile.heldKakan(readTile(in.childOrEmpty("tile")));
            default -> throw new IllegalArgumentException("Unknown ActiveTile type: " + type);
        };
    }

    private static void writePlayer(ValueOutput out, TheMahjongPlayer player) {
        out.putInt("points", player.points());
        out.putString("riichiState", player.riichiState().name());
        out.putBoolean("ippatsuEligible", player.ippatsuEligible());
        writeTileList(out, "currentHand", player.currentHand());
        writeMeldList(out, "melds", player.melds());
        writeDiscardList(out, "discards", player.discards());
        writeTileList(out, "temporaryFuritenTiles", player.temporaryFuritenTiles());
        out.putBoolean("riichiPermanentFuriten", player.riichiPermanentFuriten());
        out.putInt("kitaCount", player.kitaCount());
    }

    private static TheMahjongPlayer readPlayer(ValueInput in) {
        return new TheMahjongPlayer(
                in.getIntOr("points", 0),
                TheMahjongPlayer.RiichiState.valueOf(in.getStringOr("riichiState", "")),
                in.getBooleanOr("ippatsuEligible", false),
                readTileList(in, "currentHand"),
                readMeldList(in, "melds"),
                readDiscardList(in, "discards"),
                readTileList(in, "temporaryFuritenTiles"),
                in.getBooleanOr("riichiPermanentFuriten", false),
                in.getIntOr("kitaCount", 0));
    }

    private static void writeMeldList(ValueOutput out, String key, List<TheMahjongMeld> melds) {
        ValueOutput.ValueOutputList list = out.childrenList(key);
        for (TheMahjongMeld meld : melds) {
            writeMeld(list.addChild(), meld);
        }
    }

    private static List<TheMahjongMeld> readMeldList(ValueInput in, String key) {
        List<TheMahjongMeld> melds = new ArrayList<>();
        for (ValueInput meld : in.childrenListOrEmpty(key)) {
            melds.add(readMeld(meld));
        }
        return melds;
    }

    private static void writeMeld(ValueOutput out, TheMahjongMeld meld) {
        if (meld instanceof TheMahjongMeld.Chi chi) {
            out.putString("type", "CHI");
            writeTileList(out, "tiles", chi.tiles());
            out.putInt("claimedTileIndex", chi.claimedTileIndex());
            out.putInt("sourceSeat", chi.sourceSeat());
            out.putInt("sourceDiscardIndex", chi.sourceDiscardIndex());
        } else if (meld instanceof TheMahjongMeld.Pon pon) {
            out.putString("type", "PON");
            writeTileList(out, "tiles", pon.tiles());
            out.putInt("claimedTileIndex", pon.claimedTileIndex());
            out.putInt("sourceSeat", pon.sourceSeat());
            out.putInt("sourceDiscardIndex", pon.sourceDiscardIndex());
        } else if (meld instanceof TheMahjongMeld.Daiminkan daiminkan) {
            out.putString("type", "DAIMINKAN");
            writeTileList(out, "tiles", daiminkan.tiles());
            out.putInt("claimedTileIndex", daiminkan.claimedTileIndex());
            out.putInt("sourceSeat", daiminkan.sourceSeat());
            out.putInt("sourceDiscardIndex", daiminkan.sourceDiscardIndex());
        } else if (meld instanceof TheMahjongMeld.Kakan kakan) {
            out.putString("type", "KAKAN");
            writeMeld(out.child("upgradedFrom"), kakan.upgradedFrom());
            writeTile(out.child("addedTile"), kakan.addedTile());
        } else if (meld instanceof TheMahjongMeld.Ankan ankan) {
            out.putString("type", "ANKAN");
            writeTileList(out, "tiles", ankan.tiles());
        }
    }

    private static TheMahjongMeld readMeld(ValueInput in) {
        String type = in.getStringOr("type", "");
        return switch (type) {
            case "CHI" -> new TheMahjongMeld.Chi(
                    readTileList(in, "tiles"),
                    in.getIntOr("claimedTileIndex", 0),
                    in.getIntOr("sourceSeat", 0),
                    in.getIntOr("sourceDiscardIndex", 0));
            case "PON" -> new TheMahjongMeld.Pon(
                    readTileList(in, "tiles"),
                    in.getIntOr("claimedTileIndex", 0),
                    in.getIntOr("sourceSeat", 0),
                    in.getIntOr("sourceDiscardIndex", 0));
            case "DAIMINKAN" -> new TheMahjongMeld.Daiminkan(
                    readTileList(in, "tiles"),
                    in.getIntOr("claimedTileIndex", 0),
                    in.getIntOr("sourceSeat", 0),
                    in.getIntOr("sourceDiscardIndex", 0));
            case "KAKAN" -> {
                TheMahjongMeld base = readMeld(in.childOrEmpty("upgradedFrom"));
                if (!(base instanceof TheMahjongMeld.Pon pon))
                    throw new IllegalArgumentException("Kakan upgradedFrom must be a Pon, got: " + base.getClass().getSimpleName());
                yield new TheMahjongMeld.Kakan(pon, readTile(in.childOrEmpty("addedTile")));
            }
            case "ANKAN" -> new TheMahjongMeld.Ankan(readTileList(in, "tiles"));
            default -> throw new IllegalArgumentException("Unknown meld type: " + type);
        };
    }

    private static void writeDiscardList(ValueOutput out, String key, List<TheMahjongDiscard> discards) {
        ValueOutput.ValueOutputList list = out.childrenList(key);
        for (TheMahjongDiscard discard : discards) {
            ValueOutput entry = list.addChild();
            writeTile(entry.child("tile"), discard.tile());
            entry.putBoolean("riichiDeclared", discard.riichiDeclared());
        }
    }

    private static List<TheMahjongDiscard> readDiscardList(ValueInput in, String key) {
        List<TheMahjongDiscard> discards = new ArrayList<>();
        for (ValueInput entry : in.childrenListOrEmpty(key)) {
            discards.add(new TheMahjongDiscard(
                    readTile(entry.childOrEmpty("tile")),
                    entry.getBooleanOr("riichiDeclared", false)));
        }
        return discards;
    }

    public static void writeTile(ValueOutput out, TheMahjongTile tile) {
        out.putString("suit", tile.suit().name());
        out.putInt("rank", tile.rank());
        out.putBoolean("redDora", tile.redDora());
    }

    public static TheMahjongTile readTile(ValueInput in) {
        return new TheMahjongTile(
                TheMahjongTile.Suit.valueOf(in.getStringOr("suit", "")),
                in.getIntOr("rank", 0),
                in.getBooleanOr("redDora", false));
    }

    public static void writeTileList(ValueOutput out, String key, List<TheMahjongTile> tiles) {
        ValueOutput.ValueOutputList list = out.childrenList(key);
        for (TheMahjongTile tile : tiles) {
            writeTile(list.addChild(), tile);
        }
    }

    public static List<TheMahjongTile> readTileList(ValueInput in, String key) {
        List<TheMahjongTile> tiles = new ArrayList<>();
        for (ValueInput tile : in.childrenListOrEmpty(key)) {
            tiles.add(readTile(tile));
        }
        return tiles;
    }
}
