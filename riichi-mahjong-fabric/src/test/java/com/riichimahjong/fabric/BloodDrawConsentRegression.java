package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity.SeatInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BloodDrawConsentRegression {
    private static final List<UUID> PLAYERS = List.of(new UUID(0, 1), new UUID(0, 2),
            new UUID(0, 3), new UUID(0, 4));

    private static ArrayList<SeatInfo> unanimous() {
        var seats = new ArrayList<SeatInfo>();
        for (UUID id : PLAYERS) seats.add(new SeatInfo(true, Optional.of(id), true));
        return seats;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var seats = unanimous();
        check(MahjongTableBlockEntity.consumeBloodDrawConsent(seats, PLAYERS::contains).equals(PLAYERS),
                "unanimous voters must map to their original seat order");
        check(MahjongTableBlockEntity.consumeBloodDrawConsent(seats, PLAYERS::contains).isEmpty(),
                "a second match inherited old consent");

        for (var replacement : List.of(SeatInfo.open(), SeatInfo.closed(), SeatInfo.claimed(PLAYERS.getLast()),
                SeatInfo.claimed(new UUID(0, 5)), unanimous().getFirst())) {
            seats = unanimous();
            seats.set(3, replacement);
            check(MahjongTableBlockEntity.consumeBloodDrawConsent(seats, id -> true).isEmpty(),
                    "an absent, unconsenting, returning or duplicated seat authorized the mode");
            check(seats.stream().noneMatch(SeatInfo::bloodDrawConsent), "partial votes carried over to a new match");
        }
        seats = unanimous();
        check(MahjongTableBlockEntity.consumeBloodDrawConsent(seats, id -> !id.equals(PLAYERS.get(1))).isEmpty(),
                "an unavailable voter counted at actual start");
        System.out.println("PASS: only four distinct available seat owners can authorize a single match");
    }
}
