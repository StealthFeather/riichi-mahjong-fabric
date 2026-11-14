package com.riichimahjong.mahjongtable;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity.OfflineSeatCandidate;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity.SeatInfo;
import com.themahjong.driver.TheMahjongDriver;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Snapshot offline assignments without treating a player in another dimension as disconnected. */
public final class OfflineSeats {
    private OfflineSeats() {}

    /** A negative active-player count denotes the lobby, where no reservation owns an AI hand. */
    public static List<OfflineSeatCandidate> candidates(List<SeatInfo> seats, ReturningSeats returning,
            int activePlayerCount, Predicate<UUID> online) {
        var candidates = new ArrayList<OfflineSeatCandidate>();
        for (int seat = 0; seat < seats.size(); seat++) {
            var info = seats.get(seat);
            if (!info.enabled() || activePlayerCount >= 0 && seat >= activePlayerCount) continue;
            UUID occupant = info.occupant().orElse(null);
            UUID reservation = activePlayerCount < 0 ? null : returning.playerAt(seat).orElse(null);
            // A conflicting assignment is not safe to revoke from a menu snapshot.
            if (occupant != null && reservation != null && !occupant.equals(reservation)) continue;
            UUID player = occupant != null ? occupant : reservation;
            if (player != null && !online.test(player)) {
                candidates.add(new OfflineSeatCandidate(seat, player, occupant == null));
            }
        }
        return List.copyOf(candidates);
    }

    public static boolean matches(TheMahjongDriver currentDriver, long currentRevision,
            TheMahjongDriver expectedDriver, long expectedRevision, int seat, UUID expectedOccupant,
            List<OfflineSeatCandidate> candidates) {
        return currentDriver == expectedDriver && currentRevision == expectedRevision
                && expectedOccupant != null && candidates.stream().anyMatch(candidate ->
                        candidate.seat() == seat && candidate.occupant().equals(expectedOccupant));
    }
}
