package com.themahjong;

import com.themahjong.yaku.Furiten;
import com.themahjong.yaku.TenpaiChecker;
import com.themahjong.yaku.WinResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public final class TheMahjongRound {
    public enum State {
        SETUP,
        RINSHAN_DRAW,
        TURN,
        CLAIM_WINDOW,

        KITA_WINDOW,

        KAKAN_CLAIM_WINDOW,
        RESOLUTION,
        ENDED
    }

    public sealed interface ActiveTile
            permits ActiveTile.None, ActiveTile.Drawn, ActiveTile.HeldDiscard,
                    ActiveTile.HeldKita, ActiveTile.HeldKakan {
        record None() implements ActiveTile {}
        record Drawn(TheMahjongTile tile) implements ActiveTile {
            public Drawn {
                if (tile == null) throw new IllegalArgumentException("tile cannot be null");
            }
        }
        record HeldDiscard(TheMahjongTile tile) implements ActiveTile {
            public HeldDiscard {
                if (tile == null) throw new IllegalArgumentException("tile cannot be null");
            }
        }

        record HeldKita(TheMahjongTile tile) implements ActiveTile {
            public HeldKita {
                if (tile == null) throw new IllegalArgumentException("tile cannot be null");
            }
        }

        record HeldKakan(TheMahjongTile tile) implements ActiveTile {
            public HeldKakan {
                if (tile == null) throw new IllegalArgumentException("tile cannot be null");
            }
        }

        static ActiveTile none() { return new None(); }
        static ActiveTile drawn(TheMahjongTile tile) { return new Drawn(tile); }
        static ActiveTile heldDiscard(TheMahjongTile tile) { return new HeldDiscard(tile); }
        static ActiveTile heldKita(TheMahjongTile tile) { return new HeldKita(tile); }
        static ActiveTile heldKakan(TheMahjongTile tile) { return new HeldKakan(tile); }
    }

    private final TheMahjongTile.Wind roundWind;
    private final int handNumber;
    private final int honba;
    private final int riichiSticks;
    private final int dealerSeat;
    private final State state;
    private final int currentTurnSeat;
    private final int claimSourceSeat;
    private final ActiveTile activeTile;
    private final List<TheMahjongTile> liveWall;
    private final List<TheMahjongTile> rinshanTiles;
    private final List<TheMahjongTile> doraIndicators;
    private final List<TheMahjongTile> uraDoraIndicators;
    private final int revealedDoraCount;

    private final int pendingKanDoraReveals;
    private final List<TheMahjongPlayer> players;

    private final List<Integer> pendingDeltas;

    public static final int MAX_DORA_INDICATORS = 5;
    public static final int INITIAL_HAND_SIZE = 13;

    public static final int RINSHAN_TILE_COUNT = 4;

    public static final int SANMA_RINSHAN_TILE_COUNT = 8;

    public static int rinshanTileCountFor(int playerCount) {
        return playerCount == 3 ? SANMA_RINSHAN_TILE_COUNT : RINSHAN_TILE_COUNT;
    }

    public TheMahjongRound(
            TheMahjongTile.Wind roundWind,
            int handNumber,
            int honba,
            int riichiSticks,
            int dealerSeat,
            State state,
            int currentTurnSeat,
            int claimSourceSeat,
            ActiveTile activeTile,
            List<TheMahjongTile> liveWall,
            List<TheMahjongTile> rinshanTiles,
            List<TheMahjongTile> doraIndicators,
            List<TheMahjongTile> uraDoraIndicators,
            int revealedDoraCount,
            List<TheMahjongPlayer> players,
            List<Integer> pendingDeltas) {
        this(roundWind, handNumber, honba, riichiSticks, dealerSeat,
                state, currentTurnSeat, claimSourceSeat, activeTile,
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators,
                revealedDoraCount, 0, players, pendingDeltas);
    }

    public TheMahjongRound(
            TheMahjongTile.Wind roundWind,
            int handNumber,
            int honba,
            int riichiSticks,
            int dealerSeat,
            State state,
            int currentTurnSeat,
            int claimSourceSeat,
            ActiveTile activeTile,
            List<TheMahjongTile> liveWall,
            List<TheMahjongTile> rinshanTiles,
            List<TheMahjongTile> doraIndicators,
            List<TheMahjongTile> uraDoraIndicators,
            int revealedDoraCount,
            int pendingKanDoraReveals,
            List<TheMahjongPlayer> players,
            List<Integer> pendingDeltas) {
        if (roundWind == null) {
            throw new IllegalArgumentException("roundWind cannot be null");
        }
        if (handNumber < 1) {
            throw new IllegalArgumentException("handNumber must be positive");
        }
        if (honba < 0) {
            throw new IllegalArgumentException("honba cannot be negative");
        }
        if (riichiSticks < 0) {
            throw new IllegalArgumentException("riichiSticks cannot be negative");
        }
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        if (activeTile == null) {
            throw new IllegalArgumentException("activeTile cannot be null; use ActiveTile.none()");
        }
        if (liveWall == null || rinshanTiles == null || doraIndicators == null || uraDoraIndicators == null) {
            throw new IllegalArgumentException("tile zone lists cannot be null");
        }
        if (doraIndicators.size() != MAX_DORA_INDICATORS) {
            throw new IllegalArgumentException("doraIndicators must have exactly " + MAX_DORA_INDICATORS + " tiles");
        }
        if (uraDoraIndicators.size() != MAX_DORA_INDICATORS) {
            throw new IllegalArgumentException("uraDoraIndicators must have exactly " + MAX_DORA_INDICATORS + " tiles");
        }
        if (revealedDoraCount < 1 || revealedDoraCount > MAX_DORA_INDICATORS) {
            throw new IllegalArgumentException("revealedDoraCount must be between 1 and " + MAX_DORA_INDICATORS);
        }
        if (pendingKanDoraReveals < 0) {
            throw new IllegalArgumentException("pendingKanDoraReveals cannot be negative");
        }
        if (revealedDoraCount + pendingKanDoraReveals > MAX_DORA_INDICATORS) {
            throw new IllegalArgumentException("revealedDoraCount + pendingKanDoraReveals exceeds " + MAX_DORA_INDICATORS);
        }
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("players cannot be null or empty");
        }
        if (pendingDeltas == null) {
            throw new IllegalArgumentException("pendingDeltas cannot be null; use List.of()");
        }

        List<TheMahjongPlayer> normalizedPlayers = List.copyOf(players);
        if (dealerSeat < 0 || dealerSeat >= normalizedPlayers.size()) {
            throw new IllegalArgumentException("dealerSeat is out of range");
        }
        if (currentTurnSeat < 0 || currentTurnSeat >= normalizedPlayers.size()) {
            throw new IllegalArgumentException("currentTurnSeat is out of range");
        }
        if (claimSourceSeat < -1 || claimSourceSeat >= normalizedPlayers.size()) {
            throw new IllegalArgumentException("claimSourceSeat is out of range");
        }
        if (!pendingDeltas.isEmpty() && pendingDeltas.size() != normalizedPlayers.size()) {
            throw new IllegalArgumentException("pendingDeltas size must match player count");
        }

        this.roundWind = roundWind;
        this.handNumber = handNumber;
        this.honba = honba;
        this.riichiSticks = riichiSticks;
        this.dealerSeat = dealerSeat;
        this.state = state;
        this.currentTurnSeat = currentTurnSeat;
        this.claimSourceSeat = claimSourceSeat;
        this.activeTile = activeTile;
        this.liveWall = List.copyOf(liveWall);
        this.rinshanTiles = List.copyOf(rinshanTiles);
        this.doraIndicators = List.copyOf(doraIndicators);
        this.uraDoraIndicators = List.copyOf(uraDoraIndicators);
        this.revealedDoraCount = revealedDoraCount;
        this.pendingKanDoraReveals = pendingKanDoraReveals;
        this.players = normalizedPlayers;
        this.pendingDeltas = List.copyOf(pendingDeltas);
    }

    public static TheMahjongRound start(int playerCount, int startingPoints) {
        return start(playerCount, startingPoints, TheMahjongTileSet.standardRiichi(false));
    }

    public static TheMahjongRound start(int playerCount, int startingPoints, TheMahjongTileSet tileSet) {
        return start(playerCount, startingPoints, tileSet.createOrderedWall());
    }

    public static TheMahjongRound start(int playerCount, int startingPoints, List<TheMahjongTile> wallTiles) {
        return start(playerCount, startingPoints, wallTiles, TheMahjongTile.Wind.EAST, 1, 0, 0, 0);
    }

    public static TheMahjongRound start(
            int playerCount,
            int startingPoints,
            List<TheMahjongTile> wallTiles,
            TheMahjongTile.Wind roundWind,
            int handNumber,
            int honba,
            int riichiSticks,
            int dealerSeat) {
        if (playerCount < 1 || playerCount > TheMahjongTile.Wind.values().length) {
            throw new IllegalArgumentException("playerCount must be between 1 and 4");
        }
        if (startingPoints < 0) {
            throw new IllegalArgumentException("startingPoints cannot be negative");
        }
        if (wallTiles == null) {
            throw new IllegalArgumentException("wallTiles cannot be null");
        }
        if (roundWind == null) {
            throw new IllegalArgumentException("roundWind cannot be null");
        }
        if (dealerSeat < 0 || dealerSeat >= playerCount) {
            throw new IllegalArgumentException("dealerSeat must be between 0 and playerCount-1");
        }

        List<TheMahjongTile> wall = new ArrayList<>(wallTiles);
        int rinshanCount = rinshanTileCountFor(playerCount);
        int requiredTileCount = playerCount * INITIAL_HAND_SIZE + rinshanCount + MAX_DORA_INDICATORS + MAX_DORA_INDICATORS;

        if (wall.size() < requiredTileCount) {
            throw new IllegalArgumentException("tile set does not contain enough tiles for round setup");
        }

        List<TheMahjongPlayer> players = new ArrayList<>(playerCount);
        int wallIndex = 0;
        for (int seat = 0; seat < playerCount; seat++) {
            List<TheMahjongTile> hand = List.copyOf(wall.subList(wallIndex, wallIndex + INITIAL_HAND_SIZE));
            wallIndex += INITIAL_HAND_SIZE;
            players.add(new TheMahjongPlayer(
                    startingPoints,
                    TheMahjongPlayer.RiichiState.NONE,
                    false,
                    hand,
                    List.of(),
                    List.of(),
                    List.of()));
        }

        List<TheMahjongTile> rinshanTiles = List.copyOf(wall.subList(wallIndex, wallIndex + rinshanCount));
        wallIndex += rinshanCount;
        List<TheMahjongTile> doraIndicators = List.copyOf(wall.subList(wallIndex, wallIndex + MAX_DORA_INDICATORS));
        wallIndex += MAX_DORA_INDICATORS;
        List<TheMahjongTile> uraDoraIndicators = List.copyOf(wall.subList(wallIndex, wallIndex + MAX_DORA_INDICATORS));
        wallIndex += MAX_DORA_INDICATORS;
        List<TheMahjongTile> liveWall = List.copyOf(wall.subList(wallIndex, wall.size()));

        return new TheMahjongRound(
                roundWind,
                handNumber,
                honba,
                riichiSticks,
                dealerSeat,
                State.SETUP,
                dealerSeat,
                -1,
                ActiveTile.none(),
                liveWall,
                rinshanTiles,
                doraIndicators,
                uraDoraIndicators,
                1,
                players,
                List.of());
    }

    public static TheMahjongRound start(
            List<Integer> playerPoints,
            List<TheMahjongTile> wallTiles,
            TheMahjongTile.Wind roundWind,
            int handNumber,
            int honba,
            int riichiSticks,
            int dealerSeat) {
        if (playerPoints == null || playerPoints.isEmpty())
            throw new IllegalArgumentException("playerPoints cannot be null or empty");
        int playerCount = playerPoints.size();
        if (playerCount > TheMahjongTile.Wind.values().length)
            throw new IllegalArgumentException("playerCount must not exceed " + TheMahjongTile.Wind.values().length);
        if (wallTiles == null) throw new IllegalArgumentException("wallTiles cannot be null");
        if (roundWind == null) throw new IllegalArgumentException("roundWind cannot be null");
        if (dealerSeat < 0 || dealerSeat >= playerCount)
            throw new IllegalArgumentException("dealerSeat must be between 0 and playerCount-1");

        List<TheMahjongTile> wall = new ArrayList<>(wallTiles);
        int rinshanCount = rinshanTileCountFor(playerCount);
        int requiredTileCount = playerCount * INITIAL_HAND_SIZE + rinshanCount + MAX_DORA_INDICATORS + MAX_DORA_INDICATORS;
        if (wall.size() < requiredTileCount)
            throw new IllegalArgumentException("tile set does not contain enough tiles for round setup");

        List<TheMahjongPlayer> players = new ArrayList<>(playerCount);
        int wallIndex = 0;
        for (int seat = 0; seat < playerCount; seat++) {
            List<TheMahjongTile> hand = List.copyOf(wall.subList(wallIndex, wallIndex + INITIAL_HAND_SIZE));
            wallIndex += INITIAL_HAND_SIZE;
            players.add(new TheMahjongPlayer(
                    playerPoints.get(seat),
                    TheMahjongPlayer.RiichiState.NONE,
                    false,
                    hand,
                    List.of(),
                    List.of(),
                    List.of()));
        }

        List<TheMahjongTile> rinshanTiles = List.copyOf(wall.subList(wallIndex, wallIndex + rinshanCount));
        wallIndex += rinshanCount;
        List<TheMahjongTile> doraIndicators = List.copyOf(wall.subList(wallIndex, wallIndex + MAX_DORA_INDICATORS));
        wallIndex += MAX_DORA_INDICATORS;
        List<TheMahjongTile> uraDoraIndicators = List.copyOf(wall.subList(wallIndex, wallIndex + MAX_DORA_INDICATORS));
        wallIndex += MAX_DORA_INDICATORS;
        List<TheMahjongTile> liveWall = List.copyOf(wall.subList(wallIndex, wall.size()));

        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.SETUP, dealerSeat, -1, ActiveTile.none(),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators,
                1, players, List.of());
    }

    public TheMahjongTile.Wind roundWind() {
        return roundWind;
    }

    public int handNumber() {
        return handNumber;
    }

    public int honba() {
        return honba;
    }

    public int riichiSticks() {
        return riichiSticks;
    }

    public int dealerSeat() {
        return dealerSeat;
    }

    public State state() {
        return state;
    }

    public int currentTurnSeat() {
        return currentTurnSeat;
    }

    public OptionalInt claimSourceSeat() {
        return claimSourceSeat == -1 ? OptionalInt.empty() : OptionalInt.of(claimSourceSeat);
    }

    public ActiveTile activeTile() {
        return activeTile;
    }

    public List<TheMahjongTile> liveWall() {
        return liveWall;
    }

    public List<TheMahjongTile> rinshanTiles() {
        return rinshanTiles;
    }

    public List<TheMahjongTile> doraIndicators() {
        return doraIndicators;
    }

    public List<TheMahjongTile> uraDoraIndicators() {
        return uraDoraIndicators;
    }

    public int revealedDoraCount() {
        return revealedDoraCount;
    }

    public int pendingKanDoraReveals() {
        return pendingKanDoraReveals;
    }

    public List<TheMahjongPlayer> players() {
        return players;
    }

    public List<TheMahjongTile> handDisplayOrder(int seatIndex) {
        if (seatIndex < 0 || seatIndex >= players.size()) {
            throw new IndexOutOfBoundsException("seatIndex out of range: " + seatIndex);
        }
        List<TheMahjongTile> sorted = new ArrayList<>(players.get(seatIndex).currentHand());
        sorted.sort(TheMahjongTile.DISPLAY_ORDER);
        if (seatIndex == currentTurnSeat && activeTile instanceof ActiveTile.Drawn drawn) {
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).equals(drawn.tile())) {
                    TheMahjongTile t = sorted.remove(i);
                    sorted.add(t);
                    break;
                }
            }
        }
        return List.copyOf(sorted);
    }

    public List<Integer> pendingDeltas() {
        return pendingDeltas;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TheMahjongRound other)) return false;
        return handNumber == other.handNumber
                && honba == other.honba
                && riichiSticks == other.riichiSticks
                && dealerSeat == other.dealerSeat
                && currentTurnSeat == other.currentTurnSeat
                && claimSourceSeat == other.claimSourceSeat
                && revealedDoraCount == other.revealedDoraCount
                && roundWind == other.roundWind
                && state == other.state
                && activeTile.equals(other.activeTile)
                && liveWall.equals(other.liveWall)
                && rinshanTiles.equals(other.rinshanTiles)
                && doraIndicators.equals(other.doraIndicators)
                && uraDoraIndicators.equals(other.uraDoraIndicators)
                && players.equals(other.players)
                && pendingDeltas.equals(other.pendingDeltas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roundWind, handNumber, honba, riichiSticks, dealerSeat, state,
                currentTurnSeat, claimSourceSeat, activeTile, liveWall, rinshanTiles,
                doraIndicators, uraDoraIndicators, revealedDoraCount, players, pendingDeltas);
    }

    @Override
    public String toString() {
        return "TheMahjongRound{state=" + state
                + ", roundWind=" + roundWind
                + ", handNumber=" + handNumber
                + ", honba=" + honba
                + ", riichiSticks=" + riichiSticks
                + ", dealerSeat=" + dealerSeat
                + ", currentTurnSeat=" + currentTurnSeat
                + ", players=" + players.size()
                + ", liveWall=" + liveWall.size()
                + '}';
    }

    public TheMahjongRound draw() {
        if (state != State.SETUP && state != State.RINSHAN_DRAW) {
            throw new IllegalStateException("draw() requires state SETUP or RINSHAN_DRAW, was " + state);
        }
        boolean fromRinshan = state == State.RINSHAN_DRAW;
        List<TheMahjongTile> sourcePile = fromRinshan ? rinshanTiles : liveWall;
        if (sourcePile.isEmpty()) {
            throw new IllegalStateException("cannot draw: " + (fromRinshan ? "rinshan pile" : "live wall") + " is empty");
        }
        TheMahjongTile drawn = sourcePile.get(0);
        List<TheMahjongTile> newLiveWall = fromRinshan ? liveWall : liveWall.subList(1, liveWall.size());
        List<TheMahjongTile> newRinshanTiles = fromRinshan ? rinshanTiles.subList(1, rinshanTiles.size()) : rinshanTiles;

        TheMahjongPlayer drawingPlayer = players.get(currentTurnSeat);
        List<TheMahjongTile> newHand = new ArrayList<>(drawingPlayer.currentHand());
        newHand.add(drawn);
        TheMahjongPlayer updatedPlayer = new TheMahjongPlayer(
                drawingPlayer.points(),
                drawingPlayer.riichiState(),
                drawingPlayer.ippatsuEligible(),
                newHand,
                drawingPlayer.melds(),
                drawingPlayer.discards(),
                drawingPlayer.temporaryFuritenTiles(),
                drawingPlayer.riichiPermanentFuriten(),
                drawingPlayer.kitaCount());

        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        newPlayers.set(currentTurnSeat, updatedPlayer);

        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.TURN, currentTurnSeat, -1,
                ActiveTile.drawn(drawn),
                newLiveWall, newRinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                pendingKanDoraReveals,
                newPlayers, List.of());
    }

    public TheMahjongRound discard(TheMahjongTile tile, TheMahjongRuleSet rules) {
        if (rules.kuikaeForbidden() && state == State.TURN
                && activeTile instanceof ActiveTile.None) {
            validateNotKuikae(tile);
        }
        return discard(tile);
    }

    public TheMahjongRound discard(TheMahjongTile tile) {
        if (tile == null) throw new IllegalArgumentException("tile cannot be null");
        if (state != State.TURN) {
            throw new IllegalStateException("discard() requires state TURN, was " + state);
        }
        TheMahjongPlayer discardingPlayer = players.get(currentTurnSeat);
        List<TheMahjongTile> newHand = new ArrayList<>(discardingPlayer.currentHand());
        int index = newHand.indexOf(tile);
        if (index == -1) {
            throw new IllegalArgumentException("tile not in hand: " + tile);
        }
        newHand.remove(index);

        boolean isRiichiDiscard = discardingPlayer.riichi()
                && discardingPlayer.discards().stream().noneMatch(TheMahjongDiscard::riichiDeclared);
        List<TheMahjongDiscard> newDiscards = new ArrayList<>(discardingPlayer.discards());
        newDiscards.add(new TheMahjongDiscard(tile, isRiichiDiscard));

        boolean keepIppatsu = discardingPlayer.ippatsuEligible() && isRiichiDiscard;
        TheMahjongPlayer updatedPlayer = new TheMahjongPlayer(
                discardingPlayer.points(),
                discardingPlayer.riichiState(),
                keepIppatsu,
                newHand,
                discardingPlayer.melds(),
                newDiscards,
                List.of(),
                discardingPlayer.riichiPermanentFuriten(),
                discardingPlayer.kitaCount());

        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        newPlayers.set(currentTurnSeat, updatedPlayer);

        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.CLAIM_WINDOW, currentTurnSeat, currentTurnSeat,
                ActiveTile.heldDiscard(tile),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators,
                revealedDoraCount + pendingKanDoraReveals,
                0,
                newPlayers, List.of());
    }

    public TheMahjongRound skipClaims() {
        if (state != State.CLAIM_WINDOW) {
            throw new IllegalStateException("skipClaims() requires state CLAIM_WINDOW, was " + state);
        }
        int nextSeat = (currentTurnSeat + 1) % players.size();
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.SETUP, nextSeat, -1,
                ActiveTile.none(),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                players, List.of());
    }

    public TheMahjongRound declineRon(int seat, TheMahjongRuleSet rules) {
        if (state != State.CLAIM_WINDOW && state != State.KITA_WINDOW
                && state != State.KAKAN_CLAIM_WINDOW) {
            throw new IllegalStateException(
                    "declineRon() requires state CLAIM_WINDOW, KITA_WINDOW, or KAKAN_CLAIM_WINDOW, was " + state);
        }
        validateSeat(seat);
        requireNotDiscarder(seat, "declineRon");
        TheMahjongTile target;
        boolean isKita;
        if (activeTile instanceof ActiveTile.HeldDiscard hd) {
            target = hd.tile();
            isKita = false;
        } else if (activeTile instanceof ActiveTile.HeldKita hk) {
            target = hk.tile();
            isKita = true;
        } else if (activeTile instanceof ActiveTile.HeldKakan hk) {
            target = hk.tile();
            isKita = false;
        } else {
            throw new IllegalStateException("declineRon() requires a held discard, kita, or kakan tile");
        }
        TheMahjongPlayer player = players.get(seat);
        if (!com.themahjong.yaku.Furiten.canRon(player, target)) {
            throw new IllegalArgumentException(
                    "seat " + seat + " cannot ron on " + target + " — nothing to decline");
        }

        boolean applyFuriten = !isKita || rules.kitaDeclineCausesFuriten();
        TheMahjongPlayer updated;
        if (applyFuriten) {
            List<TheMahjongTile> newTempFuriten = new ArrayList<>(player.temporaryFuritenTiles());
            newTempFuriten.add(target);
            updated = new TheMahjongPlayer(
                    player.points(), player.riichiState(), player.ippatsuEligible(),
                    player.currentHand(), player.melds(), player.discards(),
                    newTempFuriten,
                    player.riichiPermanentFuriten() || player.riichi(),
                    player.kitaCount());
        } else {
            updated = player;
        }

        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        newPlayers.set(seat, updated);
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                state, currentTurnSeat, claimSourceSeat,
                activeTile,
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                pendingKanDoraReveals,
                newPlayers, List.of());
    }

    public TheMahjongRound claimChi(int claimantSeat, List<TheMahjongTile> handTiles, TheMahjongRuleSet rules) {
        if (rules.chiDisabled()) {
            throw new IllegalStateException("claimChi() not allowed by this TheMahjongRuleSet (chiDisabled=true)");
        }
        requireClaimWindowState("claimChi");
        validateSeat(claimantSeat);
        requireNotRiichi(claimantSeat, "claimChi");
        int expectedSeat = (claimSourceSeat + 1) % players.size();
        if (claimantSeat != expectedSeat) {
            throw new IllegalArgumentException(
                    "Chi can only be claimed by seat " + expectedSeat + ", not " + claimantSeat);
        }
        TheMahjongTile claimed = heldDiscardOrThrow();
        List<TheMahjongTile> allTiles = new ArrayList<>(handTiles);
        allTiles.add(claimed);
        int sourceDiscardIndex = players.get(claimSourceSeat).discards().size() - 1;
        TheMahjongMeld meld = new TheMahjongMeld.Chi(
                allTiles, allTiles.size() - 1, claimSourceSeat, sourceDiscardIndex);
        return applyClaim(claimantSeat, handTiles, meld, State.TURN, revealedDoraCount, pendingKanDoraReveals);
    }

    public TheMahjongRound claimChi(int claimantSeat, List<TheMahjongTile> handTiles) {
        return claimChi(claimantSeat, handTiles, TheMahjongRuleSet.wrc());
    }

    public TheMahjongRound claimPon(int claimantSeat, List<TheMahjongTile> handTiles) {
        requireClaimWindowState("claimPon");
        validateSeat(claimantSeat);
        requireNotDiscarder(claimantSeat, "claimPon");
        requireNotRiichi(claimantSeat, "claimPon");
        TheMahjongTile claimed = heldDiscardOrThrow();
        List<TheMahjongTile> allTiles = new ArrayList<>(handTiles);
        allTiles.add(claimed);
        int sourceDiscardIndex = players.get(claimSourceSeat).discards().size() - 1;
        TheMahjongMeld meld = new TheMahjongMeld.Pon(
                allTiles, allTiles.size() - 1, claimSourceSeat, sourceDiscardIndex);
        return applyClaim(claimantSeat, handTiles, meld, State.TURN, revealedDoraCount, pendingKanDoraReveals);
    }

    public TheMahjongRound claimDaiminkan(int claimantSeat, List<TheMahjongTile> handTiles, TheMahjongRuleSet rules) {
        requireClaimWindowState("claimDaiminkan");
        validateSeat(claimantSeat);
        requireNotDiscarder(claimantSeat, "claimDaiminkan");
        requireNotRiichi(claimantSeat, "claimDaiminkan");
        if (revealedDoraCount + pendingKanDoraReveals >= MAX_DORA_INDICATORS) {
            throw new IllegalStateException("cannot reveal more dora indicators: already at max");
        }
        TheMahjongTile claimed = heldDiscardOrThrow();
        List<TheMahjongTile> allTiles = new ArrayList<>(handTiles);
        allTiles.add(claimed);
        int sourceDiscardIndex = players.get(claimSourceSeat).discards().size() - 1;
        TheMahjongMeld meld = new TheMahjongMeld.Daiminkan(
                allTiles, allTiles.size() - 1, claimSourceSeat, sourceDiscardIndex);
        int newRevealed = rules.openKanDoraDelayedReveal() ? revealedDoraCount : revealedDoraCount + 1;
        int newPending  = rules.openKanDoraDelayedReveal() ? pendingKanDoraReveals + 1 : pendingKanDoraReveals;
        return applyClaim(claimantSeat, handTiles, meld, State.RINSHAN_DRAW, newRevealed, newPending);
    }

    private TheMahjongTile heldDiscardOrThrow() {
        if (!(activeTile instanceof ActiveTile.HeldDiscard hd)) {
            throw new IllegalStateException("no held discard available for claiming");
        }
        return hd.tile();
    }

    private void requireClaimWindowState(String method) {
        if (state != State.CLAIM_WINDOW) {
            throw new IllegalStateException(method + "() requires state CLAIM_WINDOW, was " + state);
        }
    }

    private void requireNotDiscarder(int claimantSeat, String method) {
        if (claimantSeat == claimSourceSeat) {
            throw new IllegalArgumentException(method + "(): claimantSeat cannot be the discarding seat");
        }
    }

    private void requireNotRiichi(int claimantSeat, String method) {
        if (players.get(claimantSeat).riichi()) {
            throw new IllegalStateException(method + "(): seat " + claimantSeat + " is in riichi");
        }
    }

    private void validateNotKuikae(TheMahjongTile tile) {
        TheMahjongPlayer player = players.get(currentTurnSeat);
        List<TheMahjongMeld> melds = player.melds();
        if (melds.isEmpty()) return;
        TheMahjongMeld last = melds.get(melds.size() - 1);
        if (last instanceof TheMahjongMeld.Pon pon) {
            TheMahjongTile claimed = pon.tiles().get(pon.claimedTileIndex());
            if (tile.matchesSuitRank(claimed)) {
                throw new IllegalStateException(
                        "kuikae forbidden: cannot discard " + tile + " of the just-Pon'd kind");
            }
        } else if (last instanceof TheMahjongMeld.Chi chi) {
            TheMahjongTile claimed = chi.tiles().get(chi.claimedTileIndex());
            if (tile.matchesSuitRank(claimed)) {
                throw new IllegalStateException(
                        "kuikae forbidden: cannot discard the just-claimed Chi tile " + tile);
            }

            int swapRank = -1;
            if (chi.claimedTileIndex() == 0) {
                swapRank = claimed.rank() + 3;
            } else if (chi.claimedTileIndex() == 2) {
                swapRank = claimed.rank() - 3;
            }
            if (swapRank >= 1 && swapRank <= 9
                    && tile.suit() == claimed.suit() && tile.rank() == swapRank) {
                throw new IllegalStateException(
                        "kuikae forbidden: cannot discard " + tile + " (suji swap of just-claimed Chi)");
            }
        }
    }

    private static void requireYakuPresent(WinResult result, String method) {
        if (result.yaku().isEmpty() && result.yakuman().isEmpty()) {
            throw new IllegalStateException(
                    method + "(): WinResult has no yaku — yakuless wins are not allowed");
        }
    }

    private void requireRonLegal(int winnerSeat, String method) {
        TheMahjongTile target;
        if (activeTile instanceof ActiveTile.HeldDiscard hd) {
            target = hd.tile();
        } else if (activeTile instanceof ActiveTile.HeldKita hk) {
            target = hk.tile();
        } else if (activeTile instanceof ActiveTile.HeldKakan hk) {
            target = hk.tile();
        } else {
            throw new IllegalStateException(method + "(): no held discard, kita, or kakan tile available for ron");
        }
        if (!Furiten.canRon(players.get(winnerSeat), target)) {
            throw new IllegalStateException(
                    method + "(): seat " + winnerSeat + " cannot ron on " + target
                            + " (not in waits or in furiten)");
        }
    }

    private TheMahjongRound applyClaim(
            int claimantSeat,
            List<TheMahjongTile> handTiles,
            TheMahjongMeld meld,
            State nextState,
            int newRevealedDoraCount,
            int newPendingKanDoraReveals) {
        TheMahjongPlayer claimant = players.get(claimantSeat);
        List<TheMahjongTile> newHand = new ArrayList<>(claimant.currentHand());
        for (TheMahjongTile t : handTiles) {
            int idx = newHand.indexOf(t);
            if (idx == -1) throw new IllegalArgumentException("hand tile not in claimant's hand: " + t);
            newHand.remove(idx);
        }
        List<TheMahjongMeld> newMelds = new ArrayList<>(claimant.melds());
        newMelds.add(meld);

        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        for (int i = 0; i < newPlayers.size(); i++) {
            TheMahjongPlayer p = newPlayers.get(i);
            if (i == claimantSeat) {
                newPlayers.set(i, new TheMahjongPlayer(
                        p.points(), p.riichiState(), false,
                        newHand, newMelds, p.discards(), p.temporaryFuritenTiles(),
                        p.riichiPermanentFuriten(), p.kitaCount()));
            } else if (p.ippatsuEligible()) {
                newPlayers.set(i, new TheMahjongPlayer(
                        p.points(), p.riichiState(), false,
                        p.currentHand(), p.melds(), p.discards(), p.temporaryFuritenTiles(),
                        p.riichiPermanentFuriten(), p.kitaCount()));
            }
        }

        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                nextState, claimantSeat, -1,
                ActiveTile.none(),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, newRevealedDoraCount,
                newPendingKanDoraReveals,
                newPlayers, List.of());
    }

    public TheMahjongRound declareRiichiIntent(TheMahjongRuleSet rules) {
        if (state == State.TURN && rules.riichiRequires1000Points()
                && players.get(currentTurnSeat).points() < 1000)
            throw new IllegalStateException("cannot declare riichi: fewer than 1000 points");
        return declareRiichiIntent();
    }

    public TheMahjongRound declareRiichiIntent() {
        if (state != State.TURN) {
            throw new IllegalStateException("declareRiichiIntent() requires state TURN, was " + state);
        }
        TheMahjongPlayer player = players.get(currentTurnSeat);
        if (player.handOpen()) {
            throw new IllegalStateException("cannot declare riichi with an open hand");
        }
        if (player.riichi()) {
            throw new IllegalStateException("player is already in riichi");
        }
        boolean noMeldsAnyPlayer = players.stream().allMatch(p -> p.melds().isEmpty());
        TheMahjongPlayer.RiichiState newState = (player.discards().isEmpty() && noMeldsAnyPlayer)
                ? TheMahjongPlayer.RiichiState.DOUBLE_RIICHI
                : TheMahjongPlayer.RiichiState.RIICHI;
        TheMahjongPlayer updated = new TheMahjongPlayer(
                player.points(),
                newState,
                true,
                player.currentHand(), player.melds(), player.discards(), player.temporaryFuritenTiles(),
                player.riichiPermanentFuriten(),
                player.kitaCount());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        newPlayers.set(currentTurnSeat, updated);
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.TURN, currentTurnSeat, -1,
                activeTile,
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                pendingKanDoraReveals,
                newPlayers, List.of());
    }

    public TheMahjongRound commitRiichiDeposit(TheMahjongRuleSet rules) {
        if (state == State.CLAIM_WINDOW && rules.riichiRequires1000Points()
                && players.get(claimSourceSeat).points() < 1000)
            throw new IllegalStateException("cannot commit riichi deposit: fewer than 1000 points");
        return commitRiichiDeposit();
    }

    public TheMahjongRound commitRiichiDeposit() {
        if (state != State.CLAIM_WINDOW) {
            throw new IllegalStateException("commitRiichiDeposit() requires state CLAIM_WINDOW, was " + state);
        }
        TheMahjongPlayer depositor = players.get(claimSourceSeat);
        if (!depositor.riichi()) {
            throw new IllegalStateException("claimSourceSeat player is not in riichi");
        }
        TheMahjongPlayer updated = new TheMahjongPlayer(
                depositor.points() - 1000,
                depositor.riichiState(),
                depositor.ippatsuEligible(),
                depositor.currentHand(), depositor.melds(), depositor.discards(),
                depositor.temporaryFuritenTiles(),
                depositor.riichiPermanentFuriten(),
                depositor.kitaCount());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        newPlayers.set(claimSourceSeat, updated);
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks + 1, dealerSeat,
                State.CLAIM_WINDOW, currentTurnSeat, claimSourceSeat,
                activeTile,
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                newPlayers, List.of());
    }

    public TheMahjongRound declareRiichi(TheMahjongRuleSet rules) {
        if (state == State.TURN && rules.riichiRequires1000Points()
                && players.get(currentTurnSeat).points() < 1000)
            throw new IllegalStateException("cannot declare riichi: fewer than 1000 points");
        return declareRiichi();
    }

    public TheMahjongRound declareRiichi() {
        if (state != State.TURN) {
            throw new IllegalStateException("declareRiichi() requires state TURN, was " + state);
        }
        TheMahjongPlayer player = players.get(currentTurnSeat);
        if (player.handOpen()) {
            throw new IllegalStateException("cannot declare riichi with an open hand");
        }
        if (player.riichi()) {
            throw new IllegalStateException("player is already in riichi");
        }
        boolean noMeldsAnyPlayer = players.stream().allMatch(p -> p.melds().isEmpty());
        TheMahjongPlayer.RiichiState newState = (player.discards().isEmpty() && noMeldsAnyPlayer)
                ? TheMahjongPlayer.RiichiState.DOUBLE_RIICHI
                : TheMahjongPlayer.RiichiState.RIICHI;
        TheMahjongPlayer updated = new TheMahjongPlayer(
                player.points() - 1000,
                newState,
                true,
                player.currentHand(), player.melds(), player.discards(), player.temporaryFuritenTiles(),
                player.riichiPermanentFuriten(),
                player.kitaCount());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        newPlayers.set(currentTurnSeat, updated);
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks + 1, dealerSeat,
                State.TURN, currentTurnSeat, -1,
                activeTile,
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                pendingKanDoraReveals,
                newPlayers, List.of());
    }

    public TheMahjongRound declareKakan(TheMahjongMeld.Pon existingPon, TheMahjongTile addedTile, TheMahjongRuleSet rules) {
        if (addedTile == null) throw new IllegalArgumentException("addedTile cannot be null");
        if (state != State.TURN) {
            throw new IllegalStateException("declareKakan() requires state TURN, was " + state);
        }
        if (revealedDoraCount + pendingKanDoraReveals >= MAX_DORA_INDICATORS) {
            throw new IllegalStateException("cannot reveal more dora indicators: already at max");
        }
        TheMahjongPlayer player = players.get(currentTurnSeat);
        if (player.riichi()) {
            throw new IllegalStateException("declareKakan() not allowed while in riichi");
        }
        int ponIndex = player.melds().indexOf(existingPon);
        if (ponIndex == -1) throw new IllegalArgumentException("existingPon not found in player's melds");
        List<TheMahjongTile> newHand = new ArrayList<>(player.currentHand());
        int tileIndex = newHand.indexOf(addedTile);
        if (tileIndex == -1) throw new IllegalArgumentException("addedTile not in hand: " + addedTile);
        newHand.remove(tileIndex);
        List<TheMahjongMeld> newMelds = new ArrayList<>(player.melds());
        newMelds.set(ponIndex, new TheMahjongMeld.Kakan(existingPon, addedTile));
        TheMahjongPlayer updated = new TheMahjongPlayer(
                player.points(), player.riichiState(), false,
                newHand, newMelds, player.discards(), player.temporaryFuritenTiles(),
                player.riichiPermanentFuriten(),
                player.kitaCount());

        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        newPlayers.set(currentTurnSeat, updated);
        int newRevealed = rules.openKanDoraDelayedReveal() ? revealedDoraCount : revealedDoraCount + 1;
        int newPending  = rules.openKanDoraDelayedReveal() ? pendingKanDoraReveals + 1 : pendingKanDoraReveals;
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.KAKAN_CLAIM_WINDOW, currentTurnSeat, currentTurnSeat,
                ActiveTile.heldKakan(addedTile),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, newRevealed,
                newPending,
                newPlayers, List.of());
    }

    public TheMahjongRound skipKakanClaims() {
        if (state != State.KAKAN_CLAIM_WINDOW) {
            throw new IllegalStateException("skipKakanClaims() requires state KAKAN_CLAIM_WINDOW, was " + state);
        }

        List<TheMahjongPlayer> newPlayers = clearOthersIppatsu(currentTurnSeat);
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.RINSHAN_DRAW, currentTurnSeat, -1,
                ActiveTile.none(),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                pendingKanDoraReveals,
                newPlayers, List.of());
    }

    private void validateRiichiAnkan(TheMahjongPlayer player, TheMahjongMeld.Ankan ankan) {
        if (!(activeTile instanceof ActiveTile.Drawn drawn)) {
            throw new IllegalStateException(
                    "declareAnkan() during riichi requires the active tile to be the just-drawn tile");
        }
        TheMahjongTile kanKind = ankan.tiles().get(0);
        if (!drawn.tile().matchesSuitRank(kanKind)) {
            throw new IllegalStateException(
                    "declareAnkan() during riichi: kan tile must match the just-drawn tile");
        }

        List<TheMahjongTile> preHand = new ArrayList<>(player.currentHand());
        int drawnIdx = preHand.indexOf(drawn.tile());
        if (drawnIdx == -1) {
            throw new IllegalStateException("drawn tile not present in hand");
        }
        preHand.remove(drawnIdx);
        TheMahjongPlayer pre = new TheMahjongPlayer(
                player.points(), player.riichiState(), player.ippatsuEligible(),
                preHand, player.melds(), player.discards(), player.temporaryFuritenTiles(),
                player.riichiPermanentFuriten());

        List<TheMahjongTile> postHand = new ArrayList<>(player.currentHand());
        for (TheMahjongTile t : ankan.tiles()) {
            int i = postHand.indexOf(t);
            if (i == -1) {
                throw new IllegalArgumentException("ankan tile not in hand: " + t);
            }
            postHand.remove(i);
        }
        List<TheMahjongMeld> postMelds = new ArrayList<>(player.melds());
        postMelds.add(ankan);
        TheMahjongPlayer post = new TheMahjongPlayer(
                player.points(), player.riichiState(), player.ippatsuEligible(),
                postHand, postMelds, player.discards(), player.temporaryFuritenTiles(),
                player.riichiPermanentFuriten());
        if (!waitKeys(pre).equals(waitKeys(post))) {
            throw new IllegalStateException(
                    "declareAnkan() during riichi: kan would change the wait pattern");
        }
    }

    private static Set<String> waitKeys(TheMahjongPlayer player) {
        Set<String> keys = new HashSet<>();
        for (TheMahjongTile t : TenpaiChecker.winningTiles(player)) {
            keys.add(t.suit() + ":" + t.rank());
        }
        return keys;
    }

    private List<TheMahjongPlayer> clearOthersIppatsu(int exceptSeat) {
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        for (int i = 0; i < newPlayers.size(); i++) {
            if (i == exceptSeat) continue;
            TheMahjongPlayer p = newPlayers.get(i);
            if (p.ippatsuEligible()) {
                newPlayers.set(i, new TheMahjongPlayer(
                        p.points(), p.riichiState(), false,
                        p.currentHand(), p.melds(), p.discards(), p.temporaryFuritenTiles(),
                        p.riichiPermanentFuriten(), p.kitaCount()));
            }
        }
        return newPlayers;
    }

    public TheMahjongRound declareAnkan(List<TheMahjongTile> handTiles, TheMahjongRuleSet rules) {
        if (state != State.TURN) {
            throw new IllegalStateException("declareAnkan() requires state TURN, was " + state);
        }
        if (revealedDoraCount + pendingKanDoraReveals >= MAX_DORA_INDICATORS) {
            throw new IllegalStateException("cannot reveal more dora indicators: already at max");
        }
        TheMahjongMeld.Ankan ankan = new TheMahjongMeld.Ankan(handTiles);
        TheMahjongPlayer player = players.get(currentTurnSeat);
        if (player.riichi() && rules.strictRiichiAnkan()) {
            validateRiichiAnkan(player, ankan);
        }
        List<TheMahjongTile> newHand = new ArrayList<>(player.currentHand());
        for (TheMahjongTile t : handTiles) {
            int idx = newHand.indexOf(t);
            if (idx == -1) throw new IllegalArgumentException("ankan tile not in hand: " + t);
            newHand.remove(idx);
        }
        List<TheMahjongMeld> newMelds = new ArrayList<>(player.melds());
        newMelds.add(ankan);
        TheMahjongPlayer updated = new TheMahjongPlayer(
                player.points(), player.riichiState(), false,
                newHand, newMelds, player.discards(), player.temporaryFuritenTiles(),
                player.riichiPermanentFuriten(),
                player.kitaCount());
        List<TheMahjongPlayer> newPlayers = clearOthersIppatsu(currentTurnSeat);
        newPlayers.set(currentTurnSeat, updated);
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.RINSHAN_DRAW, currentTurnSeat, -1,
                ActiveTile.none(),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount + 1,
                pendingKanDoraReveals,
                newPlayers, List.of());
    }

    public TheMahjongRound declareKita(TheMahjongTile kitaTile) {
        if (kitaTile == null) throw new IllegalArgumentException("kitaTile cannot be null");
        if (kitaTile.suit() != TheMahjongTile.Suit.WIND || kitaTile.rank() <= players.size()) {
            throw new IllegalArgumentException(
                    "not a valid kita tile for " + players.size() + "-player game: " + kitaTile);
        }
        if (state != State.TURN) {
            throw new IllegalStateException("declareKita() requires state TURN, was " + state);
        }
        TheMahjongPlayer player = players.get(currentTurnSeat);
        List<TheMahjongTile> newHand = new ArrayList<>(player.currentHand());
        int idx = newHand.indexOf(kitaTile);
        if (idx == -1) throw new IllegalArgumentException("kitaTile not in hand: " + kitaTile);
        newHand.remove(idx);
        TheMahjongPlayer updated = new TheMahjongPlayer(
                player.points(), player.riichiState(), false,
                newHand, player.melds(), player.discards(), player.temporaryFuritenTiles(),
                player.riichiPermanentFuriten(),
                player.kitaCount() + 1);
        List<TheMahjongPlayer> newPlayers = clearOthersIppatsu(currentTurnSeat);
        newPlayers.set(currentTurnSeat, updated);
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.KITA_WINDOW, currentTurnSeat, currentTurnSeat,
                ActiveTile.heldKita(kitaTile),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                pendingKanDoraReveals,
                newPlayers, List.of());
    }

    public TheMahjongRound skipKitaClaims() {
        if (state != State.KITA_WINDOW) {
            throw new IllegalStateException("skipKitaClaims() requires state KITA_WINDOW, was " + state);
        }
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.RINSHAN_DRAW, currentTurnSeat, -1,
                ActiveTile.none(),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                pendingKanDoraReveals,
                players, List.of());
    }

    public TheMahjongRound declareWin(int winner, int fromWho, WinResult result) {
        validateSeat(winner);
        validateSeat(fromWho);
        if (state != State.CLAIM_WINDOW && state != State.KITA_WINDOW
                && state != State.KAKAN_CLAIM_WINDOW
                && state != State.TURN && state != State.RINSHAN_DRAW) {
            throw new IllegalStateException(
                    "declareWin() requires state CLAIM_WINDOW, KITA_WINDOW, KAKAN_CLAIM_WINDOW, TURN, or RINSHAN_DRAW, was " + state);
        }
        if ((state == State.CLAIM_WINDOW || state == State.KITA_WINDOW
                || state == State.KAKAN_CLAIM_WINDOW) && winner != fromWho) {
            requireRonLegal(winner, "declareWin");
        }
        requireYakuPresent(result, "declareWin");
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        for (int i = 0; i < newPlayers.size(); i++) {
            int delta = result.pointDeltas().get(i);
            if (delta != 0) {
                TheMahjongPlayer p = newPlayers.get(i);
                newPlayers.set(i, new TheMahjongPlayer(
                        p.points() + delta,
                        p.riichiState(), p.ippatsuEligible(),
                        p.currentHand(), p.melds(), p.discards(), p.temporaryFuritenTiles(),
                        p.riichiPermanentFuriten(), p.kitaCount()));
            }
        }
        return new TheMahjongRound(
                roundWind, handNumber, honba, 0, dealerSeat,
                State.ENDED, currentTurnSeat, -1,
                ActiveTile.none(),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                newPlayers, List.of());
    }

    public TheMahjongRound beginRon(int winnerSeat, WinResult result, TheMahjongRuleSet rules) {
        if (!rules.doubleRonAllowed()) {
            throw new IllegalStateException("beginRon() is not allowed by the current TheMahjongRuleSet (doubleRonAllowed=false)");
        }
        if (state != State.CLAIM_WINDOW && state != State.KAKAN_CLAIM_WINDOW) {
            throw new IllegalStateException(
                    "beginRon() requires state CLAIM_WINDOW or KAKAN_CLAIM_WINDOW, was " + state);
        }
        validateSeat(winnerSeat);
        requireNotDiscarder(winnerSeat, "beginRon");
        requireRonLegal(winnerSeat, "beginRon");
        requireYakuPresent(result, "beginRon");
        List<Integer> deltas = result.pointDeltas();
        if (deltas.size() != players.size()) {
            throw new IllegalArgumentException("WinResult deltas size does not match player count");
        }
        return new TheMahjongRound(
                roundWind, handNumber, honba, 0, dealerSeat,
                State.RESOLUTION, currentTurnSeat, claimSourceSeat,
                activeTile,
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                pendingKanDoraReveals,
                players, deltas);
    }

    public TheMahjongRound addRon(int winnerSeat, WinResult result) {
        if (state != State.RESOLUTION) {
            throw new IllegalStateException("addRon() requires state RESOLUTION, was " + state);
        }
        validateSeat(winnerSeat);
        requireNotDiscarder(winnerSeat, "addRon");
        requireRonLegal(winnerSeat, "addRon");
        requireYakuPresent(result, "addRon");
        List<Integer> incoming = result.pointDeltas();
        if (incoming.size() != players.size()) {
            throw new IllegalArgumentException("WinResult deltas size does not match player count");
        }
        List<Integer> newDeltas = new ArrayList<>(players.size());
        for (int i = 0; i < players.size(); i++) {
            newDeltas.add(pendingDeltas.get(i) + incoming.get(i));
        }
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.RESOLUTION, currentTurnSeat, claimSourceSeat,
                activeTile,
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                players, List.copyOf(newDeltas));
    }

    public TheMahjongRound resolveRons() {
        if (state != State.RESOLUTION) {
            throw new IllegalStateException("resolveRons() requires state RESOLUTION, was " + state);
        }
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(players);
        for (int i = 0; i < newPlayers.size(); i++) {
            int delta = pendingDeltas.get(i);
            if (delta != 0) {
                TheMahjongPlayer p = newPlayers.get(i);
                newPlayers.set(i, new TheMahjongPlayer(
                        p.points() + delta,
                        p.riichiState(), p.ippatsuEligible(),
                        p.currentHand(), p.melds(), p.discards(), p.temporaryFuritenTiles(),
                        p.riichiPermanentFuriten(), p.kitaCount()));
            }
        }
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.ENDED, currentTurnSeat, -1,
                ActiveTile.none(),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                newPlayers, List.of());
    }

    public TheMahjongRound exhaustiveDraw(TheMahjongRuleSet rules) {
        if (state == State.ENDED || state == State.RESOLUTION) {
            throw new IllegalStateException("exhaustiveDraw() cannot be called in state " + state);
        }
        List<TheMahjongPlayer> newPlayers = players;
        if (rules.nagashiManganAllowed()) {
            List<Integer> winners = new ArrayList<>();
            for (int i = 0; i < players.size(); i++) {
                if (isNagashiManganEligible(i)) winners.add(i);
            }
            if (!winners.isEmpty()) {
                newPlayers = applyNagashiManganDeltas(winners);
            }
        }
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.ENDED, currentTurnSeat, -1,
                ActiveTile.none(),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                newPlayers, List.of());
    }

    public boolean isNagashiManganEligible(int seat) {
        validateSeat(seat);
        TheMahjongPlayer player = players.get(seat);
        if (player.discards().isEmpty()) return false;
        if (player.melds().stream().anyMatch(TheMahjongMeld::open)) return false;
        for (TheMahjongDiscard d : player.discards()) {
            if (!d.tile().terminal() && !d.tile().honor()) return false;
        }
        for (int i = 0; i < players.size(); i++) {
            if (i == seat) continue;
            for (TheMahjongMeld m : players.get(i).melds()) {
                if (meldSourceSeat(m) == seat) return false;
            }
        }
        return true;
    }

    private static int meldSourceSeat(TheMahjongMeld m) {
        if (m instanceof TheMahjongMeld.Chi c)       return c.sourceSeat();
        if (m instanceof TheMahjongMeld.Pon p)       return p.sourceSeat();
        if (m instanceof TheMahjongMeld.Daiminkan d) return d.sourceSeat();
        if (m instanceof TheMahjongMeld.Kakan k)     return k.upgradedFrom().sourceSeat();
        return -1;
    }

    private List<TheMahjongPlayer> applyNagashiManganDeltas(List<Integer> winners) {
        int[] deltas = new int[players.size()];
        for (int winner : winners) {
            boolean winnerIsDealer = (winner == dealerSeat);
            for (int i = 0; i < players.size(); i++) {
                if (i == winner) continue;
                int pays = (winnerIsDealer || i == dealerSeat) ? (4000 + honba * 100) : (2000 + honba * 100);
                deltas[i] -= pays;
                deltas[winner] += pays;
            }
        }
        List<TheMahjongPlayer> result = new ArrayList<>(players.size());
        for (int i = 0; i < players.size(); i++) {
            TheMahjongPlayer p = players.get(i);
            result.add(deltas[i] == 0 ? p : new TheMahjongPlayer(
                    p.points() + deltas[i],
                    p.riichiState(), p.ippatsuEligible(),
                    p.currentHand(), p.melds(), p.discards(), p.temporaryFuritenTiles(),
                    p.riichiPermanentFuriten(), p.kitaCount()));
        }
        return result;
    }

    public TheMahjongRound abortiveDraw(TheMahjongRuleSet rules) {
        if (!rules.abortiveDrawsAllowed()) {
            throw new IllegalStateException("abortive draws are not allowed by this rule set");
        }
        if (state == State.ENDED || state == State.RESOLUTION) {
            throw new IllegalStateException("abortiveDraw() cannot be called in state " + state);
        }
        return new TheMahjongRound(
                roundWind, handNumber, honba, riichiSticks, dealerSeat,
                State.ENDED, currentTurnSeat, -1,
                ActiveTile.none(),
                liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount,
                players, List.of());
    }

    public boolean isKyuushuEligible() {
        if (state != State.TURN) return false;
        if (players.stream().anyMatch(p -> !p.melds().isEmpty())) return false;
        TheMahjongPlayer player = players.get(currentTurnSeat);
        if (!player.discards().isEmpty()) return false;
        long distinctTypes = player.currentHand().stream()
                .filter(t -> t.terminal() || t.honor())
                .mapToInt(t -> t.suit().ordinal() * 10 + t.rank())
                .distinct()
                .count();
        return distinctTypes >= 9;
    }

    public boolean isSuuchaRiichi() {
        return players.stream().allMatch(TheMahjongPlayer::riichi);
    }

    public boolean isSuufonRenta() {
        if (players.stream().anyMatch(p -> p.discards().size() != 1)) return false;
        if (players.stream().anyMatch(p -> !p.melds().isEmpty())) return false;
        TheMahjongTile ref = players.get(0).discards().get(0).tile();
        if (ref.suit() != TheMahjongTile.Suit.WIND) return false;
        return players.stream().allMatch(p -> p.discards().get(0).tile().matchesSuitRank(ref));
    }

    public boolean isSuukanSanra() {
        int total = 0;
        int maxByOne = 0;
        for (TheMahjongPlayer p : players) {
            int playerKans = 0;
            for (TheMahjongMeld m : p.melds()) {
                if (m instanceof TheMahjongMeld.Daiminkan
                        || m instanceof TheMahjongMeld.Kakan
                        || m instanceof TheMahjongMeld.Ankan) {
                    playerKans++;
                }
            }
            total += playerKans;
            if (playerKans > maxByOne) maxByOne = playerKans;
        }
        return total >= 4 && maxByOne < 4;
    }

    public boolean dealer(int seat) {
        validateSeat(seat);
        return seat == dealerSeat;
    }

    public TheMahjongTile.Wind seatWind(int seat) {
        validateSeat(seat);
        int relativeSeat = Math.floorMod(seat - dealerSeat, players.size());
        return TheMahjongTile.Wind.fromTileRank(relativeSeat + 1);
    }

    private void validateSeat(int seat) {
        if (seat < 0 || seat >= players.size()) {
            throw new IllegalArgumentException("seat is out of range");
        }
    }
}
