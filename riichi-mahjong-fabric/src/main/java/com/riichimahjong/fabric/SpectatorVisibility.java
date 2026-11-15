package com.riichimahjong.fabric;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import net.minecraft.server.level.ServerPlayer;

/** Audience decisions never add concealed faces to the public table snapshot. */
final class SpectatorVisibility {
    private SpectatorVisibility(){}
    static boolean inRange(boolean sameWorld,boolean online,double distanceSquared){
        return sameWorld&&online&&Double.isFinite(distanceSquared)&&distanceSquared>=0&&distanceSquared<=64;
    }
    static boolean canView(MahjongTableBlockEntity table,ServerPlayer viewer){
        return !table.isRemoved()&&!viewer.isRemoved()
                &&inRange(table.getLevel()==viewer.level(),
                        viewer.level().getServer().getPlayerList().getPlayer(viewer.getUUID())==viewer,
                        table.getBlockPos().distToCenterSqr(viewer.position()))
                &&table.spectatorHandsVisibleTo(viewer.getUUID());
    }
    /** Public result faces remain public; concealed backs are replaced only for their allowed viewers. */
    static Set<UUID> hiddenFrom(UUID owner,boolean publiclyRevealed,Collection<UUID> authorizedSpectators){
        if(publiclyRevealed)return Set.of();
        var hidden=new HashSet<UUID>();if(owner!=null)hidden.add(owner);
        for(var viewer:authorizedSpectators)if(viewer!=null)hidden.add(viewer);
        return Set.copyOf(hidden);
    }
    /** A predicate alone cannot retract an entity already sent by Polymer. */
    static boolean mustReplace(Set<UUID> before,Set<UUID> after){return !Objects.equals(before,after);}
}
