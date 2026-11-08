package com.themahjong;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheMahjongRoundTest {
    @Test
    void initialRoundUsesSeatZeroAsDealer() {
        TheMahjongRound round = TheMahjongRound.start(4, 25000);

        assertEquals(0, round.dealerSeat());
        assertTrue(round.dealer(0));
        assertFalse(round.dealer(1));
        assertEquals(TheMahjongTile.Wind.EAST, round.seatWind(0));
        assertEquals(TheMahjongTile.Wind.SOUTH, round.seatWind(1));
        assertEquals(TheMahjongTile.Wind.WEST, round.seatWind(2));
        assertEquals(TheMahjongTile.Wind.NORTH, round.seatWind(3));
        assertEquals(4, round.rinshanTiles().size());
        assertEquals(5, round.doraIndicators().size());
        assertEquals(5, round.uraDoraIndicators().size());
        assertEquals(1, round.revealedDoraCount());
        assertEquals(70, round.liveWall().size());
        assertEquals(13, round.players().get(0).currentHand().size());
        assertEquals(13, round.players().get(1).currentHand().size());
        assertEquals(13, round.players().get(2).currentHand().size());
        assertEquals(13, round.players().get(3).currentHand().size());
    }

    @Test
    void seatWindIsDerivedRelativeToDealerSeat() {
        TheMahjongPlayer seat0 = TheMahjongPlayer.initial(25000);
        TheMahjongPlayer seat1 = TheMahjongPlayer.initial(25000);
        TheMahjongPlayer seat2 = TheMahjongPlayer.initial(25000);
        TheMahjongPlayer seat3 = TheMahjongPlayer.initial(25000);
        TheMahjongTile dummyTile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        List<TheMahjongTile> fiveTiles = Collections.nCopies(5, dummyTile);

        TheMahjongRound round = new TheMahjongRound(
                TheMahjongTile.Wind.EAST,
                2,
                0,
                0,
                2,
                TheMahjongRound.State.TURN,
                2,
                -1,
                TheMahjongRound.ActiveTile.none(),
                List.of(),
                List.of(),
                fiveTiles,
                fiveTiles,
                1,
                List.of(seat0, seat1, seat2, seat3), List.of());

        assertEquals(TheMahjongTile.Wind.WEST, round.seatWind(0));
        assertEquals(TheMahjongTile.Wind.NORTH, round.seatWind(1));
        assertEquals(TheMahjongTile.Wind.EAST, round.seatWind(2));
        assertEquals(TheMahjongTile.Wind.SOUTH, round.seatWind(3));
        assertTrue(round.dealer(2));
    }

    @Test
    void roundRejectsDealerSeatOutsidePlayerRange() {
        TheMahjongPlayer seat0 = TheMahjongPlayer.initial(25000);

        TheMahjongTile dummyTile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        List<TheMahjongTile> fiveTiles = Collections.nCopies(5, dummyTile);

        assertThrows(IllegalArgumentException.class, () -> new TheMahjongRound(
                TheMahjongTile.Wind.EAST,
                1,
                0,
                0,
                1,
                TheMahjongRound.State.SETUP,
                0,
                -1,
                TheMahjongRound.ActiveTile.none(),
                List.of(),
                List.of(),
                fiveTiles,
                fiveTiles,
                1,
                List.of(seat0), List.of()));
    }

    @Test
    void roundStoresExplicitRinshanTiles() {
        TheMahjongTile rinshanTile = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, false);
        TheMahjongPlayer seat0 = TheMahjongPlayer.initial(25000);

        TheMahjongTile dummyTile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        List<TheMahjongTile> fiveTiles = Collections.nCopies(5, dummyTile);

        TheMahjongRound round = new TheMahjongRound(
                TheMahjongTile.Wind.EAST,
                1,
                0,
                0,
                0,
                TheMahjongRound.State.TURN,
                0,
                -1,
                TheMahjongRound.ActiveTile.none(),
                List.of(),
                List.of(rinshanTile),
                fiveTiles,
                fiveTiles,
                1,
                List.of(seat0), List.of());

        assertEquals(List.of(rinshanTile), round.rinshanTiles());
    }

    @Test
    void fullOverloadRespectsDealerSeatAndSetsTurnSeat() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound round = TheMahjongRound.start(4, 25000, wall,
                TheMahjongTile.Wind.EAST, 1, 0, 0, 2);
        assertEquals(2, round.dealerSeat());
        assertEquals(2, round.currentTurnSeat());
        assertTrue(round.dealer(2));
        assertEquals(TheMahjongTile.Wind.EAST, round.seatWind(2));
    }

    @Test
    void fullOverloadRespectsSouthRoundAndHonba() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound round = TheMahjongRound.start(4, 25000, wall,
                TheMahjongTile.Wind.SOUTH, 3, 2, 1, 1);
        assertEquals(TheMahjongTile.Wind.SOUTH, round.roundWind());
        assertEquals(3, round.handNumber());
        assertEquals(2, round.honba());
        assertEquals(1, round.riichiSticks());
        assertEquals(1, round.dealerSeat());
        assertEquals(1, round.currentTurnSeat());
    }

    @Test
    void fullOverloadRejectsNullRoundWind() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        assertThrows(IllegalArgumentException.class,
                () -> TheMahjongRound.start(4, 25000, wall, null, 1, 0, 0, 0));
    }

    @Test
    void fullOverloadRejectsDealerSeatOutOfRange() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        assertThrows(IllegalArgumentException.class,
                () -> TheMahjongRound.start(2, 25000, wall, TheMahjongTile.Wind.EAST, 1, 0, 0, 2));
    }

    @Test
    void equalsAndHashCode() {
        TheMahjongRound a = TheMahjongRound.start(4, 25000);
        TheMahjongRound b = TheMahjongRound.start(4, 25000);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toStringContainsState() {
        assertTrue(TheMahjongRound.start(4, 25000).toString().contains("SETUP"));
    }

    @Test
    void roundSetupRejectsTileSetWithoutEnoughTiles() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongTileSet tileSet = new TheMahjongTileSet(List.of(tile), java.util.Map.of(tile, 10));

        assertThrows(IllegalArgumentException.class, () -> TheMahjongRound.start(4, 25000, tileSet));
    }

    @Test
    void draw_transitionsSetupToTurn() {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        TheMahjongTile expectedTile = r0.liveWall().get(0);

        TheMahjongRound r1 = r0.draw();

        assertEquals(TheMahjongRound.State.TURN, r1.state());
        assertInstanceOf(TheMahjongRound.ActiveTile.Drawn.class, r1.activeTile());
        assertEquals(expectedTile, ((TheMahjongRound.ActiveTile.Drawn) r1.activeTile()).tile());
        assertEquals(14, r1.players().get(r1.currentTurnSeat()).currentHand().size());
        assertEquals(r0.liveWall().size() - 1, r1.liveWall().size());
        assertEquals(r0.currentTurnSeat(), r1.currentTurnSeat());
    }

    @Test
    void draw_preservesIppatsu() {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        TheMahjongPlayer dealer = r0.players().get(r0.dealerSeat());
        TheMahjongPlayer dealerWithIppatsu = new TheMahjongPlayer(
                dealer.points(), TheMahjongPlayer.RiichiState.RIICHI, true,
                dealer.currentHand(), dealer.melds(), dealer.discards(), dealer.temporaryFuritenTiles());
        List<TheMahjongPlayer> players = new ArrayList<>(r0.players());
        players.set(r0.dealerSeat(), dealerWithIppatsu);
        TheMahjongRound withIppatsu = new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), r0.riichiSticks(), r0.dealerSeat(),
                TheMahjongRound.State.SETUP, r0.currentTurnSeat(), -1,
                TheMahjongRound.ActiveTile.none(),
                r0.liveWall(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                r0.revealedDoraCount(), players, List.of());

        assertTrue(withIppatsu.draw().players().get(r0.dealerSeat()).ippatsuEligible());
    }

    @Test
    void draw_requiresSetupState() {
        TheMahjongRound r1 = TheMahjongRound.start(4, 25000).draw();
        assertThrows(IllegalStateException.class, r1::draw);
    }

    @Test
    void draw_throwsOnEmptyWall() {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        TheMahjongRound emptyWall = new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), r0.riichiSticks(), r0.dealerSeat(),
                TheMahjongRound.State.SETUP, r0.currentTurnSeat(), -1,
                TheMahjongRound.ActiveTile.none(),
                List.of(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                r0.revealedDoraCount(), r0.players(), List.of());

        assertThrows(IllegalStateException.class, emptyWall::draw);
    }

    @Test
    void discard_movesToClaimWindow() {
        TheMahjongRound r1 = TheMahjongRound.start(4, 25000).draw();
        TheMahjongTile tileToDiscard = r1.players().get(r1.currentTurnSeat()).currentHand().get(0);

        TheMahjongRound r2 = r1.discard(tileToDiscard);

        assertEquals(TheMahjongRound.State.CLAIM_WINDOW, r2.state());
        assertInstanceOf(TheMahjongRound.ActiveTile.HeldDiscard.class, r2.activeTile());
        assertEquals(tileToDiscard, ((TheMahjongRound.ActiveTile.HeldDiscard) r2.activeTile()).tile());
        assertEquals(13, r2.players().get(r2.currentTurnSeat()).currentHand().size());
        assertEquals(1, r2.players().get(r2.currentTurnSeat()).discards().size());
        assertEquals(tileToDiscard, r2.players().get(r2.currentTurnSeat()).discards().get(0).tile());
        assertEquals(r2.currentTurnSeat(), r2.claimSourceSeat().getAsInt());
    }

    @Test
    void discard_clearsTempFuriten() {
        TheMahjongRound r1 = TheMahjongRound.start(4, 25000).draw();
        TheMahjongTile furitenTile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongPlayer p = r1.players().get(r1.currentTurnSeat());
        TheMahjongPlayer withFuriten = new TheMahjongPlayer(
                p.points(), p.riichiState(), p.ippatsuEligible(),
                p.currentHand(), p.melds(), p.discards(), List.of(furitenTile));
        List<TheMahjongPlayer> ps = new ArrayList<>(r1.players());
        ps.set(r1.currentTurnSeat(), withFuriten);
        TheMahjongRound withFuritenRound = new TheMahjongRound(
                r1.roundWind(), r1.handNumber(), r1.honba(), r1.riichiSticks(), r1.dealerSeat(),
                TheMahjongRound.State.TURN, r1.currentTurnSeat(), -1,
                r1.activeTile(),
                r1.liveWall(), r1.rinshanTiles(), r1.doraIndicators(), r1.uraDoraIndicators(),
                r1.revealedDoraCount(), ps, List.of());

        TheMahjongTile tileToDiscard = withFuritenRound.players().get(withFuritenRound.currentTurnSeat()).currentHand().get(0);
        TheMahjongRound r2 = withFuritenRound.discard(tileToDiscard);

        assertTrue(r2.players().get(r2.currentTurnSeat()).temporaryFuritenTiles().isEmpty());
    }

    @Test
    void discard_requiresTurnState() {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        TheMahjongTile tile = r0.liveWall().get(0);
        assertThrows(IllegalStateException.class, () -> r0.discard(tile));
    }

    @Test
    void discard_requiresTileInHand() {
        TheMahjongRound r1 = TheMahjongRound.start(4, 25000).draw();
        TheMahjongTile notInHand = new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 3, false);
        assertThrows(IllegalArgumentException.class, () -> r1.discard(notInHand));
    }

    @Test
    void discard_rejectsNull() {
        TheMahjongRound r1 = TheMahjongRound.start(4, 25000).draw();
        assertThrows(IllegalArgumentException.class, () -> r1.discard(null));
    }

    @Test
    void skipClaims_advancesToNextPlayer() {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        int dealerSeat = r0.dealerSeat();
        TheMahjongRound r1 = r0.draw();
        TheMahjongTile tile = r1.players().get(r1.currentTurnSeat()).currentHand().get(0);
        TheMahjongRound r2 = r1.discard(tile).skipClaims();

        assertEquals(TheMahjongRound.State.SETUP, r2.state());
        assertEquals((dealerSeat + 1) % 4, r2.currentTurnSeat());
        assertTrue(r2.claimSourceSeat().isEmpty());
        assertInstanceOf(TheMahjongRound.ActiveTile.None.class, r2.activeTile());
    }

    @Test
    void skipClaims_requiresClaimWindowState() {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        assertThrows(IllegalStateException.class, r0::skipClaims);
    }

    @Test
    void declineRon_setsTemporaryFuritenAndDoesNotChangeState() {
        TheMahjongTile heldDiscard = m(1);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(java.util.Collections.nCopies(13, p(9)), heldDiscard);
        TheMahjongPlayer seat1 = playerWithHand(java.util.Collections.nCopies(13, p(9)));
        TheMahjongPlayer seat2 = playerWithHand(new java.util.ArrayList<>(java.util.Arrays.asList(
                m(2), m(3), m(4), m(5), m(6), m(7), m(8), m(9),
                p(1), p(1), p(1), p(2), p(2))));
        TheMahjongPlayer seat3 = playerWithHand(java.util.Collections.nCopies(13, p(9)));
        TheMahjongRound r = claimWindow(List.of(seat0, seat1, seat2, seat3), 0, heldDiscard, List.of());

        TheMahjongRound after = r.declineRon(2, TheMahjongRuleSet.tenhou());

        assertEquals(TheMahjongRound.State.CLAIM_WINDOW, after.state());
        assertTrue(after.players().get(2).temporaryFuriten());
        assertEquals(List.of(m(1)), after.players().get(2).temporaryFuritenTiles());
        assertFalse(after.players().get(2).riichiPermanentFuriten());
    }

    @Test
    void declineRon_inRiichi_setsPermanentFuriten() {
        TheMahjongTile heldDiscard = m(1);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(java.util.Collections.nCopies(13, p(9)), heldDiscard);
        TheMahjongPlayer seat1 = playerWithHand(java.util.Collections.nCopies(13, p(9)));
        TheMahjongPlayer seat2Riichi = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.RIICHI, true,
                new java.util.ArrayList<>(java.util.Arrays.asList(
                        m(2), m(3), m(4), m(5), m(6), m(7), m(8), m(9),
                        p(1), p(1), p(1), p(2), p(2))),
                List.of(), List.of(), List.of());
        TheMahjongPlayer seat3 = playerWithHand(java.util.Collections.nCopies(13, p(9)));
        TheMahjongRound r = claimWindow(List.of(seat0, seat1, seat2Riichi, seat3), 0, heldDiscard, List.of());

        TheMahjongRound after = r.declineRon(2, TheMahjongRuleSet.tenhou());

        assertTrue(after.players().get(2).temporaryFuriten());
        assertTrue(after.players().get(2).riichiPermanentFuriten());
    }

    @Test
    void tempFuriten_clearedByOwnDiscard_butNotByDraw() {
        TheMahjongTile filler = m(1);
        TheMahjongPlayer p0 = playerWithHand(java.util.Collections.nCopies(13, p(9)));
        TheMahjongPlayer p1 = playerWithHand(java.util.Collections.nCopies(13, p(9)));

        TheMahjongPlayer p2 = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                java.util.Collections.nCopies(13, p(9)), List.of(), List.of(),
                List.of(m(1)), false);
        TheMahjongPlayer p3 = playerWithHand(java.util.Collections.nCopies(13, p(9)));

        TheMahjongRound r = new TheMahjongRound(
                TheMahjongTile.Wind.EAST, 1, 0, 0, 0,
                TheMahjongRound.State.SETUP, 2, -1,
                TheMahjongRound.ActiveTile.none(),
                List.of(filler, filler, filler, filler, filler),
                List.of(),
                List.of(filler, filler, filler, filler, filler),
                List.of(filler, filler, filler, filler, filler),
                1,
                List.of(p0, p1, p2, p3), List.of());

        TheMahjongRound afterDraw = r.draw();

        assertTrue(afterDraw.players().get(2).temporaryFuriten());

        TheMahjongTile toDiscard = afterDraw.players().get(2).currentHand().get(0);
        TheMahjongRound afterDiscard = afterDraw.discard(toDiscard);
        assertFalse(afterDiscard.players().get(2).temporaryFuriten());
    }

    @Test
    void riichiPermanentFuriten_survivesDrawAndDiscard() {
        TheMahjongTile filler = m(1);
        TheMahjongPlayer p0 = playerWithHand(java.util.Collections.nCopies(13, p(9)));
        TheMahjongPlayer p1 = playerWithHand(java.util.Collections.nCopies(13, p(9)));
        TheMahjongPlayer p2 = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.RIICHI, false,
                java.util.Collections.nCopies(13, p(9)), List.of(), List.of(),
                List.of(m(1)), true);
        TheMahjongPlayer p3 = playerWithHand(java.util.Collections.nCopies(13, p(9)));

        TheMahjongRound r = new TheMahjongRound(
                TheMahjongTile.Wind.EAST, 1, 0, 0, 0,
                TheMahjongRound.State.SETUP, 2, -1,
                TheMahjongRound.ActiveTile.none(),
                List.of(filler, filler, filler, filler, filler),
                List.of(),
                List.of(filler, filler, filler, filler, filler),
                List.of(filler, filler, filler, filler, filler),
                1,
                List.of(p0, p1, p2, p3), List.of());

        TheMahjongRound afterDraw = r.draw();
        TheMahjongTile toDiscard = afterDraw.players().get(2).currentHand().get(0);
        TheMahjongRound afterDiscard = afterDraw.discard(toDiscard);

        assertFalse(afterDiscard.players().get(2).temporaryFuriten());
        assertTrue(afterDiscard.players().get(2).riichiPermanentFuriten());
    }

    @Test
    void declineRon_rejectsDiscarder() {
        TheMahjongTile heldDiscard = m(1);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(java.util.Collections.nCopies(13, p(9)), heldDiscard);
        TheMahjongPlayer other = playerWithHand(java.util.Collections.nCopies(13, p(9)));
        TheMahjongRound r = claimWindow(List.of(seat0, other, other, other), 0, heldDiscard, List.of());

        assertThrows(IllegalArgumentException.class, () -> r.declineRon(0, TheMahjongRuleSet.tenhou()));
    }

    @Test
    void declineRon_rejectsSeatThatCannotRon() {
        TheMahjongTile heldDiscard = p(9);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(java.util.Collections.nCopies(13, m(1)), heldDiscard);
        TheMahjongPlayer seat1 = playerWithHand(java.util.Collections.nCopies(13, m(1)));
        TheMahjongRound r = claimWindow(List.of(seat0, seat1, seat1, seat1), 0, heldDiscard, List.of());

        assertThrows(IllegalArgumentException.class, () -> r.declineRon(1, TheMahjongRuleSet.tenhou()));
    }

    @Test
    void fullCycle_drawDiscardSkip() {
        TheMahjongRound r = TheMahjongRound.start(4, 25000);
        int seat0 = r.currentTurnSeat();

        r = r.draw();
        assertEquals(TheMahjongRound.State.TURN, r.state());
        assertEquals(seat0, r.currentTurnSeat());

        TheMahjongTile discard = r.players().get(r.currentTurnSeat()).currentHand().get(0);
        r = r.discard(discard);
        assertEquals(TheMahjongRound.State.CLAIM_WINDOW, r.state());

        r = r.skipClaims();
        assertEquals(TheMahjongRound.State.SETUP, r.state());
        assertEquals((seat0 + 1) % 4, r.currentTurnSeat());

        r = r.draw();
        assertEquals(TheMahjongRound.State.TURN, r.state());
        assertEquals(14, r.players().get(r.currentTurnSeat()).currentHand().size());
    }

    private static TheMahjongTile m(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.MANZU, rank, false);
    }

    private static TheMahjongTile p(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false);
    }

    private static TheMahjongTile s(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false);
    }

    private static TheMahjongTile w(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.WIND, rank, false);
    }

    private static TheMahjongTile d(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.DRAGON, rank, false);
    }

    private static TheMahjongRound claimWindow(
            List<TheMahjongPlayer> players,
            int discardingPlayerSeat,
            TheMahjongTile heldDiscard,
            List<TheMahjongTile> rinshanTiles) {
        TheMahjongTile filler = m(1);
        return new TheMahjongRound(
                TheMahjongTile.Wind.EAST, 1, 0, 0,
                0,
                TheMahjongRound.State.CLAIM_WINDOW,
                discardingPlayerSeat, discardingPlayerSeat,
                TheMahjongRound.ActiveTile.heldDiscard(heldDiscard),
                List.of(),
                rinshanTiles,
                List.of(filler, filler, filler, filler, filler),
                List.of(filler, filler, filler, filler, filler),
                1,
                players, List.of());
    }

    private static TheMahjongPlayer playerWithHandAndDiscard(
            List<TheMahjongTile> hand, TheMahjongTile discard) {
        return new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                hand, List.of(),
                List.of(new TheMahjongDiscard(discard, false)),
                List.of());
    }

    private static TheMahjongPlayer playerWithHand(List<TheMahjongTile> hand) {
        return new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                hand, List.of(), List.of(), List.of());
    }

    @Test
    void claimChi_transitionsToTurnForNextPlayer() {
        TheMahjongTile discarded = m(5);
        TheMahjongTile h1 = m(4), h2 = m(6);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);
        TheMahjongPlayer seat1 = playerWithHand(
                new ArrayList<>(List.of(h1, h2, m(7), m(7), m(7), m(7), m(8), m(8), m(8), m(8), m(9), m(9), m(9))));
        TheMahjongPlayer seat2 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(1)));

        TheMahjongRound r = claimWindow(List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of());
        TheMahjongRound r2 = r.claimChi(1, List.of(h1, h2));

        assertEquals(TheMahjongRound.State.TURN, r2.state());
        assertEquals(1, r2.currentTurnSeat());
        assertTrue(r2.claimSourceSeat().isEmpty());
        assertInstanceOf(TheMahjongRound.ActiveTile.None.class, r2.activeTile());
        assertEquals(11, r2.players().get(1).currentHand().size());
        assertEquals(1, r2.players().get(1).melds().size());
        assertInstanceOf(TheMahjongMeld.Chi.class, r2.players().get(1).melds().get(0));
    }

    @Test
    void claimChi_rejectsWrongSeat() {
        TheMahjongTile discarded = m(5);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);
        TheMahjongPlayer seat1 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat2 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(List.of(m(4), m(6), m(7), m(7), m(7), m(8), m(8), m(8), m(9), m(9), m(9), p(1), p(1)));

        TheMahjongRound r = claimWindow(List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of());

        assertThrows(IllegalArgumentException.class, () -> r.claimChi(3, List.of(m(4), m(6))));
    }

    @Test
    void claimChi_cannotBeCalledFromNonClaimWindow() {
        TheMahjongRound r = TheMahjongRound.start(4, 25000);
        assertThrows(IllegalStateException.class, () -> r.claimChi(1, List.of(m(4), m(6))));
    }

    @Test
    void claimChi_clearsIppatsuForAllPlayers() {
        TheMahjongTile discarded = m(5);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);

        TheMahjongPlayer seat2ippatsu = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.RIICHI, true,
                Collections.nCopies(13, m(1)), List.of(), List.of(), List.of());
        TheMahjongPlayer seat1 = playerWithHand(
                new ArrayList<>(List.of(m(4), m(6), m(7), m(7), m(7), m(7), m(8), m(8), m(8), m(8), m(9), m(9), m(9))));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(1)));

        TheMahjongRound r = claimWindow(List.of(seat0, seat1, seat2ippatsu, seat3), 0, discarded, List.of());
        TheMahjongRound r2 = r.claimChi(1, List.of(m(4), m(6)));

        assertFalse(r2.players().get(2).ippatsuEligible());
    }

    @Test
    void claimPon_transitionsToTurnForClaimant() {
        TheMahjongTile discarded = m(3);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);
        TheMahjongPlayer seat1 = playerWithHand(
                new ArrayList<>(List.of(m(3), m(3), m(4), m(4), m(4), m(4), m(5), m(5), m(5), m(5), m(6), m(6), m(6))));
        TheMahjongPlayer seat2 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(1)));

        TheMahjongRound r = claimWindow(List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of());
        TheMahjongRound r2 = r.claimPon(1, List.of(m(3), m(3)));

        assertEquals(TheMahjongRound.State.TURN, r2.state());
        assertEquals(1, r2.currentTurnSeat());
        assertEquals(11, r2.players().get(1).currentHand().size());
        assertEquals(1, r2.players().get(1).melds().size());
        assertInstanceOf(TheMahjongMeld.Pon.class, r2.players().get(1).melds().get(0));
        assertTrue(r2.claimSourceSeat().isEmpty());
    }

    @Test
    void claimPon_allowsNonAdjacentSeat() {
        TheMahjongTile discarded = m(3);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);
        TheMahjongPlayer seat1 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat2 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(
                new ArrayList<>(List.of(m(3), m(3), m(4), m(4), m(4), m(4), m(5), m(5), m(5), m(5), m(6), m(6), m(6))));

        TheMahjongRound r = claimWindow(List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of());
        TheMahjongRound r2 = r.claimPon(3, List.of(m(3), m(3)));

        assertEquals(3, r2.currentTurnSeat());
        assertInstanceOf(TheMahjongMeld.Pon.class, r2.players().get(3).melds().get(0));
    }

    @Test
    void claimPon_rejectsDiscardingSeat() {
        TheMahjongTile discarded = m(3);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(
                new ArrayList<>(List.of(m(3), m(3), m(4), m(4), m(4), m(4), m(5), m(5), m(5), m(5), m(6), m(6), m(6))),
                discarded);
        TheMahjongPlayer seat1 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat2 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(1)));

        TheMahjongRound r = claimWindow(List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of());
        assertThrows(IllegalArgumentException.class, () -> r.claimPon(0, List.of(m(3), m(3))));
    }

    @Test
    void claimDaiminkan_transitionsToRinshanDraw() {
        TheMahjongTile discarded = m(3);
        TheMahjongTile rinshanTile = p(9);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);
        TheMahjongPlayer seat2 = playerWithHand(
                new ArrayList<>(List.of(m(3), m(3), m(3), m(4), m(4), m(4), m(4), m(5), m(5), m(5), m(5), m(6), m(6))));
        TheMahjongPlayer seat1 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(1)));

        TheMahjongRound r = claimWindow(
                List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of(rinshanTile, m(1), m(1), m(1)));
        TheMahjongRound r2 = r.claimDaiminkan(2, List.of(m(3), m(3), m(3)), TheMahjongRuleSet.wrc());

        assertEquals(TheMahjongRound.State.RINSHAN_DRAW, r2.state());
        assertEquals(2, r2.currentTurnSeat());
        assertEquals(2, r2.revealedDoraCount());
        assertInstanceOf(TheMahjongMeld.Daiminkan.class, r2.players().get(2).melds().get(0));
        assertEquals(10, r2.players().get(2).currentHand().size());
    }

    @Test
    void claimDaiminkan_drawRinshanThenDiscard() {
        TheMahjongTile discarded = m(3);
        TheMahjongTile rinshanTile = p(9);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);
        TheMahjongPlayer seat2 = playerWithHand(
                new ArrayList<>(List.of(m(3), m(3), m(3), m(4), m(4), m(4), m(4), m(5), m(5), m(5), m(5), m(6), m(6))));
        TheMahjongPlayer seat1 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(1)));

        TheMahjongRound r = claimWindow(
                List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of(rinshanTile, m(1), m(1), m(1)));
        TheMahjongRound afterKan = r.claimDaiminkan(2, List.of(m(3), m(3), m(3)), TheMahjongRuleSet.wrc());

        TheMahjongRound afterDraw = afterKan.draw();
        assertEquals(TheMahjongRound.State.TURN, afterDraw.state());
        assertEquals(2, afterDraw.currentTurnSeat());
        assertEquals(11, afterDraw.players().get(2).currentHand().size());
        assertEquals(rinshanTile, ((TheMahjongRound.ActiveTile.Drawn) afterDraw.activeTile()).tile());
        assertEquals(3, afterDraw.rinshanTiles().size());

        TheMahjongRound afterDiscard = afterDraw.discard(rinshanTile);
        assertEquals(TheMahjongRound.State.CLAIM_WINDOW, afterDiscard.state());
    }

    @Test
    void claimDaiminkan_delayedReveal_doesNotIncrementDoraCount() {
        TheMahjongTile discarded = m(3);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);
        TheMahjongPlayer seat2 = playerWithHand(
                new ArrayList<>(List.of(m(3), m(3), m(3), m(4), m(4), m(4), m(4), m(5), m(5), m(5), m(5), m(6), m(6))));
        TheMahjongPlayer seat1 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(1)));

        TheMahjongRound r = claimWindow(
                List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of(p(9), m(1), m(1), m(1)));
        TheMahjongRound afterKan = r.claimDaiminkan(2, List.of(m(3), m(3), m(3)), TheMahjongRuleSet.tenhou());

        assertEquals(TheMahjongRound.State.RINSHAN_DRAW, afterKan.state());
        assertEquals(1, afterKan.revealedDoraCount());
    }

    @Test
    void claimDaiminkan_immediateReveal_incrementsDoraCount() {
        TheMahjongTile discarded = m(3);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);
        TheMahjongPlayer seat2 = playerWithHand(
                new ArrayList<>(List.of(m(3), m(3), m(3), m(4), m(4), m(4), m(4), m(5), m(5), m(5), m(5), m(6), m(6))));
        TheMahjongPlayer seat1 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(1)));

        TheMahjongRound r = claimWindow(
                List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of(p(9), m(1), m(1), m(1)));
        TheMahjongRound afterKan = r.claimDaiminkan(2, List.of(m(3), m(3), m(3)), TheMahjongRuleSet.wrc());

        assertEquals(2, afterKan.revealedDoraCount());
    }

    @Test
    void delayedKanDora_autoRevealedOnPostRinshanDiscard() {
        TheMahjongTile discarded = m(3);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);
        TheMahjongPlayer seat2 = playerWithHand(
                new ArrayList<>(List.of(m(3), m(3), m(3), m(4), m(4), m(4), m(4), m(5), m(5), m(5), m(5), m(6), m(6))));
        TheMahjongPlayer seat1 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(1)));

        TheMahjongRound r = claimWindow(
                List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of(p(9), m(1), m(1), m(1)));
        TheMahjongRound afterKan = r.claimDaiminkan(2, List.of(m(3), m(3), m(3)), TheMahjongRuleSet.tenhou());

        assertEquals(1, afterKan.revealedDoraCount());
        assertEquals(1, afterKan.pendingKanDoraReveals());

        TheMahjongRound afterDraw = afterKan.draw();

        assertEquals(1, afterDraw.pendingKanDoraReveals());

        TheMahjongTile rinshanTile = afterDraw.players().get(2).currentHand()
                .get(afterDraw.players().get(2).currentHand().size() - 1);
        TheMahjongRound afterDiscard = afterDraw.discard(rinshanTile);

        assertEquals(TheMahjongRound.State.CLAIM_WINDOW, afterDiscard.state());
        assertEquals(2, afterDiscard.revealedDoraCount());
        assertEquals(0, afterDiscard.pendingKanDoraReveals());
    }

    @Test
    void declareKakan_delayedReveal_doesNotIncrementDoraCount() {
        TheMahjongTile ponTile = m(5);
        TheMahjongTile addedTile = m(5);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(
                List.of(ponTile, ponTile, ponTile), 2, 1, 0);
        TheMahjongPlayer player = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                new ArrayList<>(List.of(addedTile, m(1), m(1), m(1), m(1), m(1), m(1), m(1), m(1), m(1), m(1), m(1), m(1), m(1))),
                List.of(pon), List.of(), List.of());
        TheMahjongTile dummy = m(1);
        List<TheMahjongTile> fiveDummy = Collections.nCopies(5, dummy);
        TheMahjongRound r = new TheMahjongRound(
                TheMahjongTile.Wind.EAST, 1, 0, 0, 0,
                TheMahjongRound.State.TURN, 0, -1,
                TheMahjongRound.ActiveTile.drawn(addedTile),
                Collections.nCopies(70, dummy), List.of(dummy, dummy, dummy, dummy),
                fiveDummy, fiveDummy, 1,
                List.of(player, TheMahjongPlayer.initial(25000),
                        TheMahjongPlayer.initial(25000), TheMahjongPlayer.initial(25000)),
                List.of());

        TheMahjongRound afterKakan = r.declareKakan(pon, addedTile, TheMahjongRuleSet.tenhou());

        assertEquals(TheMahjongRound.State.KAKAN_CLAIM_WINDOW, afterKakan.state());
        assertEquals(1, afterKakan.revealedDoraCount());
        TheMahjongRound afterSkip = afterKakan.skipKakanClaims();
        assertEquals(TheMahjongRound.State.RINSHAN_DRAW, afterSkip.state());
        assertEquals(1, afterSkip.revealedDoraCount());
    }

    @Test
    void chankanRonFromKakanClaimWindowEndsRound() {
        TheMahjongTile addedTile = m(5);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(
                List.of(m(5), m(5), m(5)), 2, 1, 0);
        TheMahjongPlayer kakanPlayer = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                new ArrayList<>(List.of(addedTile, m(1))),
                List.of(pon), List.of(), List.of());

        TheMahjongPlayer winnerPlayer = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                List.of(m(4), m(6), p(1), p(2), p(3), p(5), p(5), p(5), p(7), p(8), p(9), s(1), s(1)),
                List.of(), List.of(), List.of());

        TheMahjongTile dummy = m(1);
        List<TheMahjongTile> fiveDummy = Collections.nCopies(5, dummy);
        TheMahjongRound r = new TheMahjongRound(
                TheMahjongTile.Wind.EAST, 1, 0, 0, 0,
                TheMahjongRound.State.TURN, 0, -1,
                TheMahjongRound.ActiveTile.drawn(addedTile),
                Collections.nCopies(70, dummy), List.of(dummy, dummy, dummy, dummy),
                fiveDummy, fiveDummy, 1,
                List.of(kakanPlayer, winnerPlayer,
                        TheMahjongPlayer.initial(25000), TheMahjongPlayer.initial(25000)),
                List.of());

        TheMahjongRound afterKakan = r.declareKakan(pon, addedTile, TheMahjongRuleSet.tenhou());
        assertEquals(TheMahjongRound.State.KAKAN_CLAIM_WINDOW, afterKakan.state());
        assertInstanceOf(TheMahjongRound.ActiveTile.HeldKakan.class, afterKakan.activeTile());

        assertTrue(com.themahjong.yaku.Furiten.canRon(
                afterKakan.players().get(1), m(5)));

        com.themahjong.yaku.WinResult result = com.themahjong.driver.WinResultBuilder
                .tryChankan(afterKakan, TheMahjongRuleSet.tenhou(), 1, m(5), 0)
                .orElseThrow();
        assertFalse(result.yaku().isEmpty() && result.yakuman().isEmpty(),
                "chankan must produce at least one yaku");

        TheMahjongRound ended = afterKakan.declareWin(1, 0, result);
        assertEquals(TheMahjongRound.State.ENDED, ended.state());

        assertTrue(ended.players().get(1).points() > 25000);
        assertTrue(ended.players().get(0).points() < 25000);
    }

    @Test
    void chankan_underImmediateKanDoraReveal_excludesPrematurelyRevealedIndicatorFromRobberScore() {
        TheMahjongTile addedTile = m(5);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(
                List.of(m(5), m(5), m(5)), 2, 1, 0);
        TheMahjongPlayer kakanPlayer = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                new ArrayList<>(List.of(addedTile, m(1))),
                List.of(pon), List.of(), List.of());

        TheMahjongPlayer winnerPlayer = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                List.of(m(4), m(6), p(1), p(2), p(3), p(5), p(5), p(5), p(7), p(8), p(9), s(1), s(1)),
                List.of(), List.of(), List.of());

        TheMahjongTile dummy = m(1);

        List<TheMahjongTile> doraInds = List.of(d(1), m(4), dummy, dummy, dummy);
        List<TheMahjongTile> uraInds = List.of(dummy, dummy, dummy, dummy, dummy);
        TheMahjongRound r = new TheMahjongRound(
                TheMahjongTile.Wind.EAST, 1, 0, 0, 0,
                TheMahjongRound.State.TURN, 0, -1,
                TheMahjongRound.ActiveTile.drawn(addedTile),
                Collections.nCopies(70, dummy), List.of(dummy, dummy, dummy, dummy),
                doraInds, uraInds, 1,
                List.of(kakanPlayer, winnerPlayer,
                        TheMahjongPlayer.initial(25000), TheMahjongPlayer.initial(25000)),
                List.of());

        TheMahjongRuleSet wrcRules = TheMahjongRuleSet.wrc();
        TheMahjongRound afterKakan = r.declareKakan(pon, addedTile, wrcRules);

        assertEquals(2, afterKakan.revealedDoraCount());

        com.themahjong.yaku.WinResult robberResult = com.themahjong.driver.WinResultBuilder
                .tryChankan(afterKakan, wrcRules, 1, m(5), 0)
                .orElseThrow();

        assertEquals(0, robberResult.doraCount());

        TheMahjongRound afterKakanTenhou = r.declareKakan(pon, addedTile, TheMahjongRuleSet.tenhou());
        assertEquals(1, afterKakanTenhou.revealedDoraCount());
        com.themahjong.yaku.WinResult tenhouResult = com.themahjong.driver.WinResultBuilder
                .tryChankan(afterKakanTenhou, TheMahjongRuleSet.tenhou(), 1, m(5), 0)
                .orElseThrow();
        assertEquals(0, tenhouResult.doraCount());
    }

    @Test
    void draw_fromRinshanRequiresRinshanDrawState() {
        TheMahjongRound r = TheMahjongRound.start(4, 25000).draw();

        assertThrows(IllegalStateException.class, r::draw);
    }

    @Test
    void discard_allowsNoneActiveTileAfterClaim() {
        TheMahjongTile discarded = m(3);
        TheMahjongPlayer seat0 = playerWithHandAndDiscard(Collections.nCopies(13, m(1)), discarded);
        TheMahjongPlayer seat1 = playerWithHand(
                new ArrayList<>(List.of(m(3), m(3), m(4), m(4), m(4), m(4), m(5), m(5), m(5), m(5), m(6), m(6), m(6))));
        TheMahjongPlayer seat2 = playerWithHand(Collections.nCopies(13, m(1)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(1)));

        TheMahjongRound r = claimWindow(List.of(seat0, seat1, seat2, seat3), 0, discarded, List.of());
        TheMahjongRound afterPon = r.claimPon(1, List.of(m(3), m(3)));
        assertEquals(TheMahjongRound.State.TURN, afterPon.state());
        assertInstanceOf(TheMahjongRound.ActiveTile.None.class, afterPon.activeTile());

        TheMahjongTile toDiscard = afterPon.players().get(1).currentHand().get(0);
        TheMahjongRound afterDiscard = afterPon.discard(toDiscard);
        assertEquals(TheMahjongRound.State.CLAIM_WINDOW, afterDiscard.state());
        assertEquals(10, afterDiscard.players().get(1).currentHand().size());
    }

    @Test
    void abortiveDraw_tenhou_transitionsToEnded() {
        TheMahjongRound r = TheMahjongRound.start(4, 25000).draw();
        assertEquals(TheMahjongRound.State.TURN, r.state());
        TheMahjongRound ended = r.abortiveDraw(TheMahjongRuleSet.tenhou());
        assertEquals(TheMahjongRound.State.ENDED, ended.state());
    }

    @Test
    void abortiveDraw_wrc_throwsNotAllowed() {
        TheMahjongRound r = TheMahjongRound.start(4, 25000).draw();
        assertThrows(IllegalStateException.class, () -> r.abortiveDraw(TheMahjongRuleSet.wrc()));
    }

    @Test
    void abortiveDraw_preservesRiichiSticks() {
        TheMahjongRound r = TheMahjongRound.start(4, 25000);
        TheMahjongRound withSticks = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), 2, r.dealerSeat(),
                TheMahjongRound.State.TURN, r.currentTurnSeat(), -1,
                TheMahjongRound.ActiveTile.none(),
                r.liveWall(), r.rinshanTiles(), r.doraIndicators(), r.uraDoraIndicators(),
                r.revealedDoraCount(), r.players(), List.of());
        assertEquals(2, withSticks.abortiveDraw(TheMahjongRuleSet.tenhou()).riichiSticks());
    }

    @Test
    void abortiveDraw_rejectsEndedState() {
        TheMahjongRound ended = TheMahjongRound.start(4, 25000).draw().exhaustiveDraw(TheMahjongRuleSet.wrc());
        assertThrows(IllegalStateException.class, () -> ended.abortiveDraw(TheMahjongRuleSet.tenhou()));
    }

    private TheMahjongRound turnRoundWithHand(List<TheMahjongTile> hand) {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        TheMahjongPlayer player = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                hand, List.of(), List.of(), List.of());
        List<TheMahjongPlayer> players = new ArrayList<>(r0.players());
        players.set(0, player);
        return new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), r0.riichiSticks(), 0,
                TheMahjongRound.State.TURN, 0, -1,
                TheMahjongRound.ActiveTile.drawn(hand.get(0)),
                r0.liveWall(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                r0.revealedDoraCount(), players, List.of());
    }

    @Test
    void isKyuushuEligible_trueWithNineDistinctTerminalHonourTypes() {
        List<TheMahjongTile> hand = List.of(
                m(1), m(9), p(1), p(9), s(1), s(9),
                w(1), w(2), w(3),
                m(2), m(3), m(4), m(5), m(6));
        assertTrue(turnRoundWithHand(hand).isKyuushuEligible());
    }

    @Test
    void isKyuushuEligible_falseWithOnlyEightTypes() {
        List<TheMahjongTile> hand = List.of(
                m(1), m(9), p(1), p(9), s(1), s(9),
                w(1), w(2),
                m(2), m(3), m(4), m(5), m(6), m(7));
        assertFalse(turnRoundWithHand(hand).isKyuushuEligible());
    }

    @Test
    void isKyuushuEligible_falseWhenPlayerAlreadyDiscarded() {
        List<TheMahjongTile> hand = List.of(
                m(1), m(9), p(1), p(9), s(1), s(9),
                w(1), w(2), w(3), d(1),
                m(2), m(3), m(4), m(5));
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        TheMahjongPlayer playerWithDiscard = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                hand, List.of(),
                List.of(new TheMahjongDiscard(m(5), false)),
                List.of());
        List<TheMahjongPlayer> players = new ArrayList<>(r0.players());
        players.set(0, playerWithDiscard);
        TheMahjongRound r = new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), r0.riichiSticks(), 0,
                TheMahjongRound.State.TURN, 0, -1,
                TheMahjongRound.ActiveTile.drawn(hand.get(0)),
                r0.liveWall(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                r0.revealedDoraCount(), players, List.of());
        assertFalse(r.isKyuushuEligible());
    }

    @Test
    void isKyuushuEligible_falseWhenAnyPlayerHasMeld() {
        List<TheMahjongTile> hand = List.of(
                m(1), m(9), p(1), p(9), s(1), s(9),
                w(1), w(2), w(3), d(1),
                m(2), m(3), m(4), m(5));
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        TheMahjongPlayer seat0 = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                hand, List.of(), List.of(), List.of());

        TheMahjongMeld pon = new TheMahjongMeld.Pon(List.of(m(3), m(3), m(3)), 0, 0, 0);
        TheMahjongPlayer seat1WithMeld = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                Collections.nCopies(10, m(2)), List.of(pon), List.of(), List.of());
        List<TheMahjongPlayer> players = new ArrayList<>(r0.players());
        players.set(0, seat0);
        players.set(1, seat1WithMeld);
        TheMahjongRound r = new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), r0.riichiSticks(), 0,
                TheMahjongRound.State.TURN, 0, -1,
                TheMahjongRound.ActiveTile.drawn(hand.get(0)),
                r0.liveWall(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                r0.revealedDoraCount(), players, List.of());
        assertFalse(r.isKyuushuEligible());
    }

    @Test
    void isKyuushuEligible_falseInNonTurnState() {
        TheMahjongRound r = TheMahjongRound.start(4, 25000);
        assertFalse(r.isKyuushuEligible());
    }

    private TheMahjongPlayer riichiPlayer() {
        return new TheMahjongPlayer(
                24000, TheMahjongPlayer.RiichiState.RIICHI, false,
                Collections.nCopies(13, m(1)), List.of(),
                List.of(new TheMahjongDiscard(m(2), true)),
                List.of());
    }

    @Test
    void isSuuchaRiichi_trueWhenAllPlayersInRiichi() {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        List<TheMahjongPlayer> allRiichi = List.of(riichiPlayer(), riichiPlayer(), riichiPlayer(), riichiPlayer());
        TheMahjongRound r = new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), r0.riichiSticks(), 0,
                TheMahjongRound.State.CLAIM_WINDOW, 3, 3,
                TheMahjongRound.ActiveTile.heldDiscard(m(1)),
                r0.liveWall(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                r0.revealedDoraCount(), allRiichi, List.of());
        assertTrue(r.isSuuchaRiichi());
    }

    @Test
    void isSuuchaRiichi_falseWhenOnePlayerNotInRiichi() {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        List<TheMahjongPlayer> mixed = List.of(riichiPlayer(), riichiPlayer(), riichiPlayer(),
                playerWithHand(Collections.nCopies(13, m(1))));
        TheMahjongRound r = new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), r0.riichiSticks(), 0,
                TheMahjongRound.State.TURN, 3, -1,
                TheMahjongRound.ActiveTile.none(),
                r0.liveWall(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                r0.revealedDoraCount(), mixed, List.of());
        assertFalse(r.isSuuchaRiichi());
    }

    private TheMahjongPlayer playerWithFirstDiscard(TheMahjongTile discard) {
        return new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                Collections.nCopies(13, m(5)), List.of(),
                List.of(new TheMahjongDiscard(discard, false)),
                List.of());
    }

    private TheMahjongRound claimWindowWithPlayers(List<TheMahjongPlayer> players, TheMahjongTile heldDiscard) {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        return new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), r0.riichiSticks(), 0,
                TheMahjongRound.State.CLAIM_WINDOW, 3, 3,
                TheMahjongRound.ActiveTile.heldDiscard(heldDiscard),
                r0.liveWall(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                r0.revealedDoraCount(), players, List.of());
    }

    @Test
    void isSuufonRenta_trueWhenAllFirstDiscardsAreSameWind() {
        TheMahjongTile east = w(1);
        TheMahjongRound r = claimWindowWithPlayers(List.of(
                playerWithFirstDiscard(east),
                playerWithFirstDiscard(east),
                playerWithFirstDiscard(east),
                playerWithFirstDiscard(east)), east);
        assertTrue(r.isSuufonRenta());
    }

    @Test
    void isSuufonRenta_falseWhenLastDiscardDiffers() {
        TheMahjongRound r = claimWindowWithPlayers(List.of(
                playerWithFirstDiscard(w(1)),
                playerWithFirstDiscard(w(1)),
                playerWithFirstDiscard(w(1)),
                playerWithFirstDiscard(w(2))), w(2));
        assertFalse(r.isSuufonRenta());
    }

    @Test
    void isSuufonRenta_falseWhenDiscardIsNotWind() {
        TheMahjongRound r = claimWindowWithPlayers(List.of(
                playerWithFirstDiscard(m(1)),
                playerWithFirstDiscard(m(1)),
                playerWithFirstDiscard(m(1)),
                playerWithFirstDiscard(m(1))), m(1));
        assertFalse(r.isSuufonRenta());
    }

    @Test
    void isSuufonRenta_falseWhenSomePlayerHasTwoDiscards() {
        TheMahjongPlayer seat0TwoDiscards = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                Collections.nCopies(12, m(5)), List.of(),
                List.of(new TheMahjongDiscard(w(1), false), new TheMahjongDiscard(m(2), false)),
                List.of());
        TheMahjongRound r = claimWindowWithPlayers(List.of(
                seat0TwoDiscards,
                playerWithFirstDiscard(w(1)),
                playerWithFirstDiscard(w(1)),
                playerWithFirstDiscard(w(1))), w(1));
        assertFalse(r.isSuufonRenta());
    }

    @Test
    void isSuufonRenta_falseWhenPlayerHasMeld() {
        TheMahjongMeld pon = new TheMahjongMeld.Pon(List.of(m(3), m(3), m(3)), 0, 1, 0);
        TheMahjongPlayer seat0WithMeld = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                Collections.nCopies(10, m(5)), List.of(pon),
                List.of(new TheMahjongDiscard(w(1), false)),
                List.of());
        TheMahjongRound r = claimWindowWithPlayers(List.of(
                seat0WithMeld,
                playerWithFirstDiscard(w(1)),
                playerWithFirstDiscard(w(1)),
                playerWithFirstDiscard(w(1))), w(1));
        assertFalse(r.isSuufonRenta());
    }

    private TheMahjongMeld.Daiminkan daiminkan(TheMahjongTile t, int fromSeat) {
        return new TheMahjongMeld.Daiminkan(List.of(t, t, t, t), 0, fromSeat, 0);
    }

    private TheMahjongMeld.Ankan ankan(TheMahjongTile t) {
        return new TheMahjongMeld.Ankan(List.of(t, t, t, t));
    }

    private TheMahjongPlayer playerWithKans(TheMahjongMeld... kans) {
        return new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                Collections.nCopies(14 - kans.length * 3, m(5)),
                List.of(kans), List.of(), List.of());
    }

    private TheMahjongRound rinshanRoundWithPlayers(List<TheMahjongPlayer> players) {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        return new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), r0.riichiSticks(), 0,
                TheMahjongRound.State.RINSHAN_DRAW, 1, -1,
                TheMahjongRound.ActiveTile.none(),
                r0.liveWall(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                4, players, List.of());
    }

    @Test
    void isSuukanSanra_trueWhenFourKansSpreadAcrossPlayers() {
        TheMahjongPlayer seat0 = playerWithKans(daiminkan(m(1), 1), daiminkan(m(2), 2));
        TheMahjongPlayer seat1 = playerWithKans(daiminkan(m(3), 0));
        TheMahjongPlayer seat2 = playerWithKans(ankan(m(4)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(5)));
        assertTrue(rinshanRoundWithPlayers(List.of(seat0, seat1, seat2, seat3)).isSuukanSanra());
    }

    @Test
    void isSuukanSanra_falseWhenOnePlayerHoldsAllFourKans() {
        TheMahjongPlayer seat0 = playerWithKans(
                daiminkan(m(1), 1), daiminkan(m(2), 2), ankan(m(3)), ankan(m(4)));
        TheMahjongPlayer seat1 = playerWithHand(Collections.nCopies(13, m(5)));
        TheMahjongPlayer seat2 = playerWithHand(Collections.nCopies(13, m(5)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(5)));
        assertFalse(rinshanRoundWithPlayers(List.of(seat0, seat1, seat2, seat3)).isSuukanSanra());
    }

    @Test
    void isSuukanSanra_falseWithOnlyThreeKans() {
        TheMahjongPlayer seat0 = playerWithKans(daiminkan(m(1), 1), daiminkan(m(2), 2));
        TheMahjongPlayer seat1 = playerWithKans(ankan(m(3)));
        TheMahjongPlayer seat2 = playerWithHand(Collections.nCopies(13, m(5)));
        TheMahjongPlayer seat3 = playerWithHand(Collections.nCopies(13, m(5)));
        assertFalse(rinshanRoundWithPlayers(List.of(seat0, seat1, seat2, seat3)).isSuukanSanra());
    }

    private static TheMahjongPlayer playerWithDiscards(TheMahjongDiscard... discards) {
        return new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                Collections.nCopies(13, m(5)), List.of(), List.of(discards), List.of());
    }

    private static TheMahjongRound setupRoundWithPlayers(List<TheMahjongPlayer> players) {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        return new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), r0.riichiSticks(), 0,
                TheMahjongRound.State.SETUP, 1, -1,
                TheMahjongRound.ActiveTile.none(),
                r0.liveWall(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                r0.revealedDoraCount(), players, List.of());
    }

    @Test
    void isNagashiManganEligible_trueWhenAllDiscardsAreTerminalOrHonour() {
        TheMahjongPlayer eligible = playerWithDiscards(
                new TheMahjongDiscard(m(1), false),
                new TheMahjongDiscard(w(1), false),
                new TheMahjongDiscard(d(1), false));
        TheMahjongRound r = setupRoundWithPlayers(List.of(
                eligible,
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5)))));
        assertTrue(r.isNagashiManganEligible(0));
    }

    @Test
    void isNagashiManganEligible_falseWhenDiscardIsNotTerminalOrHonour() {
        TheMahjongPlayer ineligible = playerWithDiscards(
                new TheMahjongDiscard(m(1), false),
                new TheMahjongDiscard(m(5), false));
        TheMahjongRound r = setupRoundWithPlayers(List.of(
                ineligible,
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5)))));
        assertFalse(r.isNagashiManganEligible(0));
    }

    @Test
    void isNagashiManganEligible_falseWhenPlayerHasOpenMeld() {
        TheMahjongMeld pon = new TheMahjongMeld.Pon(List.of(m(9), m(9), m(9)), 0, 1, 0);
        TheMahjongPlayer ineligible = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                Collections.nCopies(10, m(5)), List.of(pon),
                List.of(new TheMahjongDiscard(m(1), false)), List.of());
        TheMahjongRound r = setupRoundWithPlayers(List.of(
                ineligible,
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5)))));
        assertFalse(r.isNagashiManganEligible(0));
    }

    @Test
    void isNagashiManganEligible_falseWhenADiscardWasClaimed() {
        TheMahjongPlayer target = playerWithDiscards(new TheMahjongDiscard(m(9), false));

        TheMahjongMeld pon = new TheMahjongMeld.Pon(List.of(m(9), m(9), m(9)), 0, 0, 0);
        TheMahjongPlayer claimant = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                Collections.nCopies(10, m(5)), List.of(pon), List.of(), List.of());
        TheMahjongRound r = setupRoundWithPlayers(List.of(
                target, claimant,
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5)))));
        assertFalse(r.isNagashiManganEligible(0));
    }

    @Test
    void isNagashiManganEligible_falseWhenNoDiscards() {
        TheMahjongRound r = setupRoundWithPlayers(List.of(
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5)))));
        assertFalse(r.isNagashiManganEligible(0));
    }

    @Test
    void exhaustiveDraw_tenhou_nonDealerNagashiPaysCorrectly() {
        TheMahjongPlayer seat1 = playerWithDiscards(new TheMahjongDiscard(m(9), false));
        TheMahjongRound r = setupRoundWithPlayers(List.of(
                playerWithHand(Collections.nCopies(13, m(5))),
                seat1,
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5)))));
        TheMahjongRound ended = r.exhaustiveDraw(TheMahjongRuleSet.tenhou());

        assertEquals(TheMahjongRound.State.ENDED, ended.state());
        assertEquals(25000 - 4000, ended.players().get(0).points());
        assertEquals(25000 + 8000, ended.players().get(1).points());
        assertEquals(25000 - 2000, ended.players().get(2).points());
        assertEquals(25000 - 2000, ended.players().get(3).points());
    }

    @Test
    void exhaustiveDraw_tenhou_dealerNagashiPaysCorrectly() {
        TheMahjongPlayer seat0 = playerWithDiscards(new TheMahjongDiscard(m(9), false));
        TheMahjongRound r = setupRoundWithPlayers(List.of(
                seat0,
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5)))));
        TheMahjongRound ended = r.exhaustiveDraw(TheMahjongRuleSet.tenhou());

        assertEquals(25000 + 12000, ended.players().get(0).points());
        assertEquals(25000 - 4000,  ended.players().get(1).points());
        assertEquals(25000 - 4000,  ended.players().get(2).points());
        assertEquals(25000 - 4000,  ended.players().get(3).points());
    }

    @Test
    void exhaustiveDraw_wrc_noNagashiPaymentWhenRuleDisabled() {
        TheMahjongPlayer seat1 = playerWithDiscards(new TheMahjongDiscard(m(9), false));
        TheMahjongRound r = setupRoundWithPlayers(List.of(
                playerWithHand(Collections.nCopies(13, m(5))),
                seat1,
                playerWithHand(Collections.nCopies(13, m(5))),
                playerWithHand(Collections.nCopies(13, m(5)))));
        TheMahjongRound ended = r.exhaustiveDraw(TheMahjongRuleSet.wrc());

        for (TheMahjongPlayer p : ended.players()) {
            assertEquals(25000, p.points());
        }
    }

    @Test
    void exhaustiveDraw_nagashi_riichiSticksPreserved() {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        TheMahjongPlayer seat1 = playerWithDiscards(new TheMahjongDiscard(m(9), false));
        TheMahjongRound withSticks = new TheMahjongRound(
                r0.roundWind(), r0.handNumber(), r0.honba(), 3, r0.dealerSeat(),
                TheMahjongRound.State.SETUP, 1, -1,
                TheMahjongRound.ActiveTile.none(),
                r0.liveWall(), r0.rinshanTiles(), r0.doraIndicators(), r0.uraDoraIndicators(),
                r0.revealedDoraCount(),
                List.of(playerWithHand(Collections.nCopies(13, m(5))), seat1,
                        playerWithHand(Collections.nCopies(13, m(5))),
                        playerWithHand(Collections.nCopies(13, m(5)))), List.of());
        assertEquals(3, withSticks.exhaustiveDraw(TheMahjongRuleSet.tenhou()).riichiSticks());
    }

    @Test
    void exhaustiveDraw_tenhou_twoWinnersStackDeltas() {
        TheMahjongPlayer seat1 = playerWithDiscards(new TheMahjongDiscard(m(9), false));
        TheMahjongPlayer seat2 = playerWithDiscards(new TheMahjongDiscard(p(9), false));
        TheMahjongRound r = setupRoundWithPlayers(List.of(
                playerWithHand(Collections.nCopies(13, m(5))),
                seat1, seat2,
                playerWithHand(Collections.nCopies(13, m(5)))));
        TheMahjongRound ended = r.exhaustiveDraw(TheMahjongRuleSet.tenhou());

        assertEquals(25000 - 4000 - 4000, ended.players().get(0).points());
        assertEquals(25000 + 8000 - 2000, ended.players().get(1).points());
        assertEquals(25000 + 8000 - 2000, ended.players().get(2).points());
        assertEquals(25000 - 2000 - 2000, ended.players().get(3).points());
    }

    private static com.themahjong.yaku.WinResult fakeWinResult(int... deltas) {
        List<Integer> deltaList = new ArrayList<>();
        for (int d : deltas) deltaList.add(d);
        return new com.themahjong.yaku.WinResult(
                List.of(com.themahjong.yaku.NonYakuman.RIICHI), List.of(), 1, 30, 0, deltaList);
    }

    private static TheMahjongRound ronReadyRound(int... tenpaiSeats) {
        TheMahjongTile m1 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongRound r = TheMahjongRound.start(4, 25000).draw();
        List<TheMahjongTile> tenpaiHand = new ArrayList<>();
        for (int rank = 1; rank <= 9; rank++)
            tenpaiHand.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false));
        for (int rank = 2; rank <= 4; rank++)
            tenpaiHand.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false));
        tenpaiHand.add(m1);
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(r.players());
        for (int seat : tenpaiSeats) {
            TheMahjongPlayer p = newPlayers.get(seat);
            newPlayers.set(seat, new TheMahjongPlayer(
                    p.points(), p.riichiState(), p.ippatsuEligible(),
                    tenpaiHand, p.melds(), p.discards(), p.temporaryFuritenTiles(),
                    p.riichiPermanentFuriten()));
        }
        TheMahjongRound withTenpai = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(), r.dealerSeat(),
                r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(),
                r.liveWall(), r.rinshanTiles(), r.doraIndicators(), r.uraDoraIndicators(),
                r.revealedDoraCount(), newPlayers, List.of());
        return withTenpai.discard(m1);
    }

    @Test
    void beginRon_transitionsClaimWindowToResolution() {
        TheMahjongRound r = ronReadyRound(1);
        TheMahjongRound res = r.beginRon(1, fakeWinResult(-3000, 3000, 0, 0), TheMahjongRuleSet.tenhou());
        assertEquals(TheMahjongRound.State.RESOLUTION, res.state());
        assertEquals(0, res.riichiSticks());
        assertEquals(4, res.pendingDeltas().size());
    }

    @Test
    void beginRon_requiresClaimWindowState() {
        TheMahjongRound turn = TheMahjongRound.start(4, 25000).draw();
        assertThrows(IllegalStateException.class,
                () -> turn.beginRon(1, fakeWinResult(-3000, 3000, 0, 0), TheMahjongRuleSet.tenhou()));
    }

    @Test
    void beginRon_rejectsDiscarder() {
        TheMahjongRound r0 = TheMahjongRound.start(4, 25000);
        TheMahjongRound turn = r0.draw();
        TheMahjongTile tile = turn.players().get(turn.currentTurnSeat()).currentHand().get(0);
        TheMahjongRound r = turn.discard(tile);
        int discarder = r.claimSourceSeat().getAsInt();
        assertThrows(IllegalArgumentException.class,
                () -> r.beginRon(discarder, fakeWinResult(-3000, 3000, 0, 0), TheMahjongRuleSet.tenhou()));
    }

    @Test
    void addRon_accumulatesDeltas() {
        TheMahjongRound cw = ronReadyRound(1, 2);

        TheMahjongRound res1 = cw.beginRon(1, fakeWinResult(-3000, 3000, 0, 0), TheMahjongRuleSet.tenhou());
        TheMahjongRound res2 = res1.addRon(2, fakeWinResult(-2000, 0, 2000, 0));

        assertEquals(TheMahjongRound.State.RESOLUTION, res2.state());
        assertEquals(-5000, (int) res2.pendingDeltas().get(0));
        assertEquals(3000,  (int) res2.pendingDeltas().get(1));
        assertEquals(2000,  (int) res2.pendingDeltas().get(2));
        assertEquals(0,     (int) res2.pendingDeltas().get(3));
    }

    @Test
    void resolveRons_appliesDeltasAndTransitionsToEnded() {
        TheMahjongRound cw = ronReadyRound(1, 2);

        TheMahjongRound ended = cw
                .beginRon(1, fakeWinResult(-3000, 3000, 0, 0), TheMahjongRuleSet.tenhou())
                .addRon(2, fakeWinResult(-2000, 0, 2000, 0))
                .resolveRons();

        assertEquals(TheMahjongRound.State.ENDED, ended.state());
        assertEquals(25000 - 5000, ended.players().get(0).points());
        assertEquals(25000 + 3000, ended.players().get(1).points());
        assertEquals(25000 + 2000, ended.players().get(2).points());
        assertEquals(25000,        ended.players().get(3).points());
        assertTrue(ended.pendingDeltas().isEmpty());
    }

    @Test
    void resolveRons_requiresResolutionState() {
        TheMahjongRound turn = TheMahjongRound.start(4, 25000).draw();
        TheMahjongTile tile = turn.players().get(turn.currentTurnSeat()).currentHand().get(0);
        TheMahjongRound r = turn.discard(tile);

        assertThrows(IllegalStateException.class, r::resolveRons);
    }

    @Test
    void beginRon_withRuleSet_allowedByTenhou_succeeds() {
        TheMahjongRound r = ronReadyRound(1);
        TheMahjongRound res = r.beginRon(1, fakeWinResult(-3000, 3000, 0, 0), TheMahjongRuleSet.tenhou());
        assertEquals(TheMahjongRound.State.RESOLUTION, res.state());
    }

    @Test
    void beginRon_withRuleSet_disallowedByWrc_throws() {
        TheMahjongRound turn = TheMahjongRound.start(4, 25000).draw();
        TheMahjongTile tile = turn.players().get(turn.currentTurnSeat()).currentHand().get(0);
        TheMahjongRound r = turn.discard(tile);
        assertThrows(IllegalStateException.class,
                () -> r.beginRon(1, fakeWinResult(-3000, 3000, 0, 0), TheMahjongRuleSet.wrc()));
    }

    @Test
    void singleRonViaBeginRonThenResolve_matchesDeclareWinPoints() {
        TheMahjongRound cw = ronReadyRound(1);

        com.themahjong.yaku.WinResult result = fakeWinResult(-3000, 3000, 0, 0);
        TheMahjongRound via2step = cw.beginRon(1, result, TheMahjongRuleSet.tenhou()).resolveRons();
        TheMahjongRound via1step = cw.declareWin(1, 0, result);

        assertEquals(via1step.players().get(0).points(), via2step.players().get(0).points());
        assertEquals(via1step.players().get(1).points(), via2step.players().get(1).points());
    }

    @Test
    void declareRiichi_withRule_blocksWhenBelow1000() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound turn = TheMahjongRound.start(4, 500, wall).draw();

        assertThrows(IllegalStateException.class, () -> turn.declareRiichi(TheMahjongRuleSet.tenhou()));
    }

    @Test
    void declareRiichi_withRule_allowsWhenAtExactly1000() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound turn = TheMahjongRound.start(4, 1000, wall).draw();

        TheMahjongRound result = turn.declareRiichi(TheMahjongRuleSet.tenhou());
        assertEquals(0, result.players().get(0).points());
    }

    @Test
    void declareRiichi_withoutRule_allowsBelow1000() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound turn = TheMahjongRound.start(4, 500, wall).draw();

        TheMahjongRound result = turn.declareRiichi(TheMahjongRuleSet.wrc());
        assertEquals(-500, result.players().get(0).points());
    }

    @Test
    void declareRiichiIntent_withRule_blocksWhenBelow1000() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound turn = TheMahjongRound.start(4, 500, wall).draw();

        assertThrows(IllegalStateException.class, () -> turn.declareRiichiIntent(TheMahjongRuleSet.tenhou()));
    }

    @Test
    void declareRiichiIntent_withoutRule_allowsBelow1000() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound turn = TheMahjongRound.start(4, 500, wall).draw();

        TheMahjongRound result = turn.declareRiichiIntent(TheMahjongRuleSet.wrc());
        assertTrue(result.players().get(0).riichi());
    }

    @Test
    void commitRiichiDeposit_withRule_blocksWhenBelow1000() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();

        TheMahjongRound turn = TheMahjongRound.start(4, 500, wall).draw();
        TheMahjongRound afterIntent = turn.declareRiichiIntent();

        TheMahjongTile discard = afterIntent.players().get(0).currentHand().get(0);
        TheMahjongRound cw = afterIntent.discard(discard);

        assertThrows(IllegalStateException.class, () -> cw.commitRiichiDeposit(TheMahjongRuleSet.tenhou()));
    }

    @Test
    void commitRiichiDeposit_withoutRule_allowsBelow1000() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound turn = TheMahjongRound.start(4, 500, wall).draw();
        TheMahjongRound afterIntent = turn.declareRiichiIntent();
        TheMahjongTile discard = afterIntent.players().get(0).currentHand().get(0);
        TheMahjongRound cw = afterIntent.discard(discard);

        TheMahjongRound result = cw.commitRiichiDeposit(TheMahjongRuleSet.wrc());
        assertEquals(-500, result.players().get(0).points());
    }

    @Test
    void claimChi_blockedWhileClaimantInRiichi() {
        TheMahjongRound r = ronReadyRound(1);

        TheMahjongPlayer p1 = r.players().get(1);
        TheMahjongPlayer riichiP1 = new TheMahjongPlayer(
                p1.points(), TheMahjongPlayer.RiichiState.RIICHI, p1.ippatsuEligible(),
                p1.currentHand(), p1.melds(), p1.discards(), p1.temporaryFuritenTiles(),
                p1.riichiPermanentFuriten());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(r.players());
        newPlayers.set(1, riichiP1);
        TheMahjongRound r2 = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(), r.dealerSeat(),
                r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(), r.liveWall(), r.rinshanTiles(), r.doraIndicators(),
                r.uraDoraIndicators(), r.revealedDoraCount(), newPlayers, List.of());

        assertThrows(IllegalStateException.class,
                () -> r2.claimChi(1, List.of(
                        new TheMahjongTile(TheMahjongTile.Suit.MANZU, 2, false),
                        new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false))));
    }

    @Test
    void claimPon_blockedWhileClaimantInRiichi() {
        TheMahjongRound r = ronReadyRound();
        TheMahjongPlayer p2 = r.players().get(2);
        TheMahjongPlayer riichiP2 = new TheMahjongPlayer(
                p2.points(), TheMahjongPlayer.RiichiState.RIICHI, p2.ippatsuEligible(),
                p2.currentHand(), p2.melds(), p2.discards(), p2.temporaryFuritenTiles(),
                p2.riichiPermanentFuriten());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(r.players());
        newPlayers.set(2, riichiP2);
        TheMahjongRound r2 = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(), r.dealerSeat(),
                r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(), r.liveWall(), r.rinshanTiles(), r.doraIndicators(),
                r.uraDoraIndicators(), r.revealedDoraCount(), newPlayers, List.of());
        assertThrows(IllegalStateException.class,
                () -> r2.claimPon(2, List.of(
                        new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false),
                        new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false))));
    }

    @Test
    void declareWin_ron_blockedWhenWinnerInDiscardRiverFuriten() {
        TheMahjongRound r = ronReadyRound(1);
        TheMahjongPlayer p1 = r.players().get(1);
        List<TheMahjongDiscard> discards = new ArrayList<>(p1.discards());
        discards.add(new TheMahjongDiscard(
                new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false), false));
        TheMahjongPlayer furitenP1 = new TheMahjongPlayer(
                p1.points(), p1.riichiState(), p1.ippatsuEligible(),
                p1.currentHand(), p1.melds(), discards, p1.temporaryFuritenTiles(),
                p1.riichiPermanentFuriten());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(r.players());
        newPlayers.set(1, furitenP1);
        TheMahjongRound r2 = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(), r.dealerSeat(),
                r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(), r.liveWall(), r.rinshanTiles(), r.doraIndicators(),
                r.uraDoraIndicators(), r.revealedDoraCount(), newPlayers, List.of());
        assertThrows(IllegalStateException.class,
                () -> r2.declareWin(1, 0, fakeWinResult(-3000, 3000, 0, 0)));
    }

    @Test
    void declareWin_ron_blockedWhenTileNotInWinnerWaits() {
        TheMahjongRound r = ronReadyRound();
        assertThrows(IllegalStateException.class,
                () -> r.declareWin(1, 0, fakeWinResult(-3000, 3000, 0, 0)));
    }

    @Test
    void declareWin_tsumo_skipsRonValidation() {
        TheMahjongRound turn = TheMahjongRound.start(4, 25000).draw();

        TheMahjongRound ended = turn.declareWin(0, 0, fakeWinResult(8000, -3000, -3000, -2000));
        assertEquals(TheMahjongRound.State.ENDED, ended.state());
    }

    @Test
    void declareWin_yakulessResult_rejected() {
        TheMahjongRound turn = TheMahjongRound.start(4, 25000).draw();
        com.themahjong.yaku.WinResult yakuless = new com.themahjong.yaku.WinResult(
                List.of(), List.of(), 0, 30, 0, List.of(8000, -3000, -3000, -2000));
        assertThrows(IllegalStateException.class, () -> turn.declareWin(0, 0, yakuless));
    }

    @Test
    void beginRon_yakulessResult_rejected() {
        TheMahjongRound r = ronReadyRound(1);
        com.themahjong.yaku.WinResult yakuless = new com.themahjong.yaku.WinResult(
                List.of(), List.of(), 0, 30, 0, List.of(-3000, 3000, 0, 0));
        assertThrows(IllegalStateException.class,
                () -> r.beginRon(1, yakuless, TheMahjongRuleSet.tenhou()));
    }

    @Test
    void addRon_yakulessResult_rejected() {
        TheMahjongRound cw = ronReadyRound(1, 2);
        TheMahjongRound res1 = cw.beginRon(1, fakeWinResult(-3000, 3000, 0, 0), TheMahjongRuleSet.tenhou());
        com.themahjong.yaku.WinResult yakuless = new com.themahjong.yaku.WinResult(
                List.of(), List.of(), 0, 30, 0, List.of(-2000, 0, 2000, 0));
        assertThrows(IllegalStateException.class, () -> res1.addRon(2, yakuless));
    }

    @Test
    void sanchahou_msPath_tripleRonViaRepeatedAddRon() {
        TheMahjongRound cw = ronReadyRound(1, 2, 3);
        TheMahjongRound ended = cw
                .beginRon(1, fakeWinResult(-3000, 3000, 0, 0), TheMahjongRuleSet.mahjongSoul())
                .addRon(2, fakeWinResult(-2000, 0, 2000, 0))
                .addRon(3, fakeWinResult(-1000, 0, 0, 1000))
                .resolveRons();
        assertEquals(TheMahjongRound.State.ENDED, ended.state());
        assertEquals(25000 - 6000, ended.players().get(0).points());
        assertEquals(25000 + 3000, ended.players().get(1).points());
        assertEquals(25000 + 2000, ended.players().get(2).points());
        assertEquals(25000 + 1000, ended.players().get(3).points());
    }

    @Test
    void sanchahou_tenhouPath_abortiveDrawFromClaimWindow() {
        TheMahjongRound cw = ronReadyRound(1, 2, 3);
        TheMahjongRound ended = cw.abortiveDraw(TheMahjongRuleSet.tenhou());
        assertEquals(TheMahjongRound.State.ENDED, ended.state());

        for (int seat = 0; seat < 4; seat++) {
            assertEquals(25000, ended.players().get(seat).points());
        }
    }

    @Test
    void sanchahouAbortive_flag_distinguishesRulesetIntent() {
        assertTrue(TheMahjongRuleSet.tenhou().sanchahouAbortive());
        assertFalse(TheMahjongRuleSet.mahjongSoul().sanchahouAbortive());
        assertTrue(TheMahjongRuleSet.tenhouSanma().sanchahouAbortive());
        assertFalse(TheMahjongRuleSet.mahjongSoulSanma().sanchahouAbortive());
    }

    @Test
    void declareWin_yakumanOnlyResult_accepted() {
        TheMahjongRound turn = TheMahjongRound.start(4, 25000).draw();
        com.themahjong.yaku.WinResult yakumanOnly = new com.themahjong.yaku.WinResult(
                List.of(),
                List.of(com.themahjong.yaku.Yakuman.SUUANKOU),
                13, 50, 0, List.of(48000, -16000, -16000, -16000));
        TheMahjongRound ended = turn.declareWin(0, 0, yakumanOnly);
        assertEquals(TheMahjongRound.State.ENDED, ended.state());
    }

    @Test
    void discard_kuikae_pon_forbidsSameKindFollowupDiscard() {
        TheMahjongRound r = TheMahjongRound.start(4, 25000).draw();
        TheMahjongTile m1 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongRound cw = r.discard(m1);

        TheMahjongPlayer p1 = cw.players().get(1);
        List<TheMahjongTile> hand = new ArrayList<>();
        hand.add(m1);
        hand.add(m1);
        for (int rank = 2; rank <= 9; rank++)
            hand.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false));
        for (int rank = 2; rank <= 4; rank++)
            hand.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false));
        TheMahjongPlayer p1Pon = new TheMahjongPlayer(
                p1.points(), p1.riichiState(), p1.ippatsuEligible(),
                hand, p1.melds(), p1.discards(), p1.temporaryFuritenTiles(),
                p1.riichiPermanentFuriten());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(cw.players());
        newPlayers.set(1, p1Pon);
        TheMahjongRound cw2 = new TheMahjongRound(
                cw.roundWind(), cw.handNumber(), cw.honba(), cw.riichiSticks(), cw.dealerSeat(),
                cw.state(), cw.currentTurnSeat(), cw.claimSourceSeat().orElse(-1),
                cw.activeTile(), cw.liveWall(), cw.rinshanTiles(), cw.doraIndicators(),
                cw.uraDoraIndicators(), cw.revealedDoraCount(), newPlayers, List.of());
        TheMahjongRound afterPon = cw2.claimPon(1, List.of(m1, m1));
        assertEquals(TheMahjongRound.State.TURN, afterPon.state());

        assertThrows(IllegalStateException.class, () -> afterPon.discard(m1, TheMahjongRuleSet.tenhou()));

        afterPon.discard(new TheMahjongTile(TheMahjongTile.Suit.PINZU, 2, false), TheMahjongRuleSet.tenhou());
    }

    @Test
    void discard_kuikae_chi_forbidsClaimedAndSujiSwap() {
        TheMahjongRound r = TheMahjongRound.start(4, 25000).draw();
        TheMahjongTile m1 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongRound cw = r.discard(m1);

        TheMahjongPlayer p1 = cw.players().get(1);
        TheMahjongTile m2 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 2, false);
        TheMahjongTile m3 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false);
        TheMahjongTile m4 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 4, false);
        List<TheMahjongTile> hand = new ArrayList<>();
        hand.add(m2);
        hand.add(m3);
        hand.add(m4);
        for (int rank = 2; rank <= 9; rank++)
            hand.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false));
        hand.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 2, false));
        hand.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 3, false));
        TheMahjongPlayer p1Chi = new TheMahjongPlayer(
                p1.points(), p1.riichiState(), p1.ippatsuEligible(),
                hand, p1.melds(), p1.discards(), p1.temporaryFuritenTiles(),
                p1.riichiPermanentFuriten());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(cw.players());
        newPlayers.set(1, p1Chi);
        TheMahjongRound cw2 = new TheMahjongRound(
                cw.roundWind(), cw.handNumber(), cw.honba(), cw.riichiSticks(), cw.dealerSeat(),
                cw.state(), cw.currentTurnSeat(), cw.claimSourceSeat().orElse(-1),
                cw.activeTile(), cw.liveWall(), cw.rinshanTiles(), cw.doraIndicators(),
                cw.uraDoraIndicators(), cw.revealedDoraCount(), newPlayers, List.of());
        TheMahjongRound afterChi = cw2.claimChi(1, List.of(m2, m3));

        assertThrows(IllegalStateException.class, () -> afterChi.discard(m1, TheMahjongRuleSet.tenhou()));

        assertThrows(IllegalStateException.class, () -> afterChi.discard(m4, TheMahjongRuleSet.tenhou()));

        afterChi.discard(new TheMahjongTile(TheMahjongTile.Suit.PINZU, 2, false), TheMahjongRuleSet.tenhou());
    }

    @Test
    void discard_kuikae_skippedWhenRuleSetAllowsIt() {
        TheMahjongRuleSet permissive = new TheMahjongRuleSet(
                false, true, false, false, true, true, true, true, true, true, true, true, true,
                false , true ,
                false, false, false, true, true);
        TheMahjongRound r = TheMahjongRound.start(4, 25000).draw();
        TheMahjongTile m1 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongRound cw = r.discard(m1);
        TheMahjongPlayer p1 = cw.players().get(1);
        List<TheMahjongTile> hand = new ArrayList<>();
        hand.add(m1);
        hand.add(m1);
        hand.add(m1);
        for (int rank = 2; rank <= 9; rank++)
            hand.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false));
        hand.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 2, false));
        hand.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 3, false));
        TheMahjongPlayer p1Pon = new TheMahjongPlayer(
                p1.points(), p1.riichiState(), p1.ippatsuEligible(),
                hand, p1.melds(), p1.discards(), p1.temporaryFuritenTiles(),
                p1.riichiPermanentFuriten());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(cw.players());
        newPlayers.set(1, p1Pon);
        TheMahjongRound cw2 = new TheMahjongRound(
                cw.roundWind(), cw.handNumber(), cw.honba(), cw.riichiSticks(), cw.dealerSeat(),
                cw.state(), cw.currentTurnSeat(), cw.claimSourceSeat().orElse(-1),
                cw.activeTile(), cw.liveWall(), cw.rinshanTiles(), cw.doraIndicators(),
                cw.uraDoraIndicators(), cw.revealedDoraCount(), newPlayers, List.of());
        TheMahjongRound afterPon = cw2.claimPon(1, List.of(m1, m1));

        TheMahjongRound afterDiscard = afterPon.discard(m1, permissive);
        assertEquals(TheMahjongRound.State.CLAIM_WINDOW, afterDiscard.state());
    }
}
