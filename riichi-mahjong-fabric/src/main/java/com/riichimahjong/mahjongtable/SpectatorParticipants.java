package com.riichimahjong.mahjongtable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Remembers all human participants for the lifetime of a match, including replaced players. */
public final class SpectatorParticipants {
    private final Set<UUID> players = new HashSet<>();

    public void begin(Collection<UUID> participants) {
        players.clear();
        players.addAll(participants);
    }

    public void remember(UUID player) {
        if (player != null) players.add(player);
    }

    public boolean contains(UUID player) {
        return players.contains(player);
    }

    public void clear() {
        players.clear();
    }

    public void write(ValueOutput out) {
        var list = out.childrenList("SpectatorMatchParticipants");
        players.forEach(uuid -> list.addChild().store("uuid", UUIDUtil.CODEC, uuid));
    }

    public void read(ValueInput in) {
        players.clear();
        for (var entry : in.childrenListOrEmpty("SpectatorMatchParticipants")) {
            entry.read("uuid", UUIDUtil.CODEC).ifPresent(players::add);
        }
    }
}
