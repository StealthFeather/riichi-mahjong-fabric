package com.riichimahjong.fabric;

import com.riichimahjong.ai.RandomThinkingBot;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity.SeatInfo;
import com.riichimahjong.mahjongtable.OfflineSeats;
import com.riichimahjong.mahjongtable.ReturningSeats;
import com.themahjong.driver.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Offline-seat administration must not revoke online users or change accepted game decisions. */
public final class OfflineSeatRegression {
    private static final UUID OFFLINE = new UUID(0,1);
    private static final UUID ONLINE = new UUID(0,2);
    private static final UUID RETURNING = new UUID(0,3);

    private static void candidatesAndReservations() {
        var reservations = new ReturningSeats();
        reservations.remember(OFFLINE,0);
        reservations.remember(RETURNING,2);
        var seats = List.of(SeatInfo.claimed(OFFLINE),SeatInfo.claimed(ONLINE),SeatInfo.open(),SeatInfo.open());
        var online = new HashSet<>(Set.of(ONLINE));
        var active = OfflineSeats.candidates(seats,reservations,4,online::contains);
        check(active.size()==2 && active.get(0).seat()==0 && !active.get(0).reserved()
                        && active.get(1).seat()==2 && active.get(1).reserved(),
                "candidate list omitted an offline occupant/reservation or included an online player");
        var lobby = OfflineSeats.candidates(seats,reservations,-1,online::contains);
        check(lobby.size()==1 && lobby.getFirst().occupant().equals(OFFLINE),
                "lobby candidate list omitted its offline occupant or exposed a stale in-game reservation");
        // Presence comes from the server-wide player list: another dimension is still online.
        online.add(OFFLINE);online.add(RETURNING);
        check(OfflineSeats.candidates(seats,reservations,4,online::contains).isEmpty(),
                "an online player became replaceable after leaving this dimension/table");
        online.clear();
        check(OfflineSeats.candidates(List.of(SeatInfo.closed(),SeatInfo.open(),SeatInfo.open(),SeatInfo.claimed(OFFLINE)),
                        reservations,3,online::contains).stream().noneMatch(candidate -> candidate.seat()==0 || candidate.seat()==3),
                "a disabled/non-playing fourth seat entered a three-player match's replacement list");
        check(!reservations.forget(RETURNING,1) && reservations.seat(RETURNING).orElse(-1)==2,
                "an obsolete seat snapshot revoked a newer reservation");
        check(reservations.forget(RETURNING,2),"the selected offline reservation could not be revoked");
        check(reservations.seat(RETURNING).isEmpty() && reservations.allows(RETURNING,1)
                        && reservations.allows(ONLINE,2),
                "replaced player retained an automatic original-seat claim or blocked ordinary joining");
        var restored = new ReturningSeats();reservations.snapshot().forEach(restored::remember);
        check(restored.seat(RETURNING).isEmpty() && restored.allows(ONLINE,2),
                "saving remaining reservations brought back the revoked original seat");
    }

    private static void staleMenusAndClaims() {
        var first = new ArrayList<>(waiting(0));first.add(tile(4,2));
        var caller = new ArrayList<>(waiting(1));caller.set(0,tile(4,2));caller.set(1,tile(4,2));
        var driver = driver(List.of(player(first),player(caller),player(caller),player(waiting(0))),
                Collections.nCopies(20,tile(4,2)));
        var sameGame = driver(List.of(player(first),player(caller),player(caller),player(waiting(0))),
                Collections.nCopies(20,tile(4,2)));
        var reservations = new ReturningSeats();reservations.remember(OFFLINE,1);
        var seats = List.of(SeatInfo.claimed(ONLINE),SeatInfo.claimed(OFFLINE),SeatInfo.open(),SeatInfo.open());
        var candidates = OfflineSeats.candidates(seats,reservations,4,ONLINE::equals);
        check(OfflineSeats.matches(driver,7,driver,7,1,OFFLINE,candidates),"current offline snapshot was rejected");
        check(!OfflineSeats.matches(driver,8,driver,7,1,OFFLINE,candidates),"a new hand/seat/host revision retained an old menu");
        check(!OfflineSeats.matches(sameGame,7,driver,7,1,OFFLINE,candidates),"a replacement driver accepted an old menu");
        check(!OfflineSeats.matches(driver,7,driver,7,1,RETURNING,candidates),"a changed occupant accepted the old target UUID");
        check(!OfflineSeats.matches(driver,7,driver,7,1,OFFLINE,List.of()),"an online/returned player could still be replaced");
        check(OfflineSeats.matches(null,7,null,7,1,OFFLINE,candidates),"a valid lobby snapshot cannot clear an offline occupant");

        act(driver,0,new PlayerAction.DiscardWithRiichi(tile(4,2)));
        var pon = driver.legalActions(1).stream().filter(action -> action instanceof PlayerAction.Pon).findFirst().orElseThrow();
        act(driver,1,pon);
        check(driver.currentPhase() instanceof MatchPhase.AwaitingClaims,"claim fixture was already resolved");
        var before = driver.match();var round = driver.currentRound();var phase = driver.currentPhase();
        driver.replacePlayer(1,new RandomThinkingBot());
        check(driver.match()==before && driver.currentRound()==round && driver.currentPhase()==phase,
                "switching only the controller changed the hand, scores, deposits or turn");
        check(driver.legalActions(1).isEmpty(),"a previously accepted pon was erased by the AI controller replacement");
        act(driver,2,PlayerAction.Pass.INSTANCE);passClaims(driver);
        check(driver.currentRound().players().get(1).melds().size()==1,
                "accepted pon did not resolve for the replacement controller");
        check(driver.currentRound().riichiSticks()==1 && driver.currentRound().players().get(0).points()==24000,
                "AI replacement lost/refunded a declaration's accepted riichi deposit");
    }

    public static void main(String[] args) {
        candidatesAndReservations();
        staleMenusAndClaims();
        System.out.println("PASS: lobby/active offline candidates include reserved AI seats and exclude online, disabled and non-playing seats");
        System.out.println("PASS: exact driver/revision/UUID guards expire stale menus; revoked reservations stay revoked after save/restore");
        System.out.println("PASS: replacing a controller preserves hand, phase, accepted pon and riichi score/deposit settlement");
    }
}
