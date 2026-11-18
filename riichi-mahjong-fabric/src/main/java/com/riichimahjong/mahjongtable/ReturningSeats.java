package com.riichimahjong.mahjongtable;

import java.util.*;

/** Keep a player's original hand reserved while a BOT operates it. Saved with the match. */
public final class ReturningSeats {
    private final Map<UUID,Integer> seats=new HashMap<>();
    public OptionalInt seat(UUID player){var seat=seats.get(player);return seat==null?OptionalInt.empty():OptionalInt.of(seat);}
    public boolean allows(UUID player,int seat){
        var own=seats.get(player);
        return (own==null||own==seat)&&seats.entrySet().stream().noneMatch(e->e.getValue()==seat&&!e.getKey().equals(player));
    }
    public void remember(UUID player,int seat){if(!allows(player,seat))throw new IllegalArgumentException("seat belongs to another returning player");seats.put(player,seat);}
    public Optional<UUID> playerAt(int seat){return seats.entrySet().stream().filter(e->e.getValue()==seat).map(Map.Entry::getKey).findFirst();}
    /** Revoke only the reservation described by the current menu, never a newer seat assignment. */
    public boolean forget(UUID player,int expectedSeat){return seats.remove(player,expectedSeat);}
    public Map<UUID,Integer> snapshot(){return Map.copyOf(seats);}
    public void clear(){seats.clear();}
}
