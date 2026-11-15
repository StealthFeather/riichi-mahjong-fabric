package com.riichimahjong.mahjongtable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Per-table setting and durable human participation for the current live match. */
public final class SpectatorAccess {
    private boolean enabled;
    private final Set<UUID> participants = new HashSet<>();

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void begin(Collection<UUID> players) { participants.clear(); participants.addAll(players); }
    public void remember(UUID player) { if (player != null) participants.add(player); }
    public void clearMatch() { participants.clear(); }
    public Set<UUID> participants() { return Set.copyOf(participants); }

    public boolean canSee(UUID viewer, boolean liveMatch, boolean seated, boolean reserved) {
        return viewer != null && enabled && liveMatch && !seated && !reserved && !participants.contains(viewer);
    }

    public void write(ValueOutput out, boolean liveMatch) {
        out.putBoolean("SpectatorHandsEnabled", enabled);
        if (liveMatch) {
            var people = out.childrenList("SpectatorMatchParticipants");
            participants.forEach(uuid -> people.addChild().store("uuid", UUIDUtil.CODEC, uuid));
        }
    }
    public void read(ValueInput in) {
        enabled = in.getBooleanOr("SpectatorHandsEnabled", false);
        participants.clear();
        for (var entry : in.childrenListOrEmpty("SpectatorMatchParticipants"))
            entry.read("uuid", UUIDUtil.CODEC).ifPresent(participants::add);
    }
}
