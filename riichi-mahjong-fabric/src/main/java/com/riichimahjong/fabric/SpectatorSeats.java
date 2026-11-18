package com.riichimahjong.fabric;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Explicit table spectators are a live session choice, never a saved claim on a seat. */
public final class SpectatorSeats {
    private final Set<UUID> players = new HashSet<>();

    public boolean add(UUID player) { return player != null && players.add(player); }
    public boolean remove(UUID player) { return players.remove(player); }
    public boolean contains(UUID player) { return players.contains(player); }
    public Set<UUID> players() { return Set.copyOf(players); }
    public void clear() { players.clear(); }

    static boolean canSeeHands(boolean registered, boolean handsVisible, boolean seated, boolean near) {
        return registered && handsVisible && !seated && near;
    }

    static boolean receivesHud(boolean active, boolean seated, boolean registered, boolean near) {
        return active && near && (seated || registered);
    }
}
