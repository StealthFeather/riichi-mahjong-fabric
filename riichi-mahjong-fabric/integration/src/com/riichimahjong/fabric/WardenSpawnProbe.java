package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.WinEffectContext;
import com.riichimahjong.fabric.wineffect.WinEffectFrame;
import com.riichimahjong.fabric.wineffect.WinEffectSession;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.EntityElement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Verify both the first tracker packet and every subsequent join during a vertical emergence. */
final class WardenSpawnProbe {
    static void run(ServerLevel level, ServerPlayer viewer) {
        var table = new BlockPos(-6, 70, 8);
        var river = RiverDisplay.position(table, Direction.EAST, 13);
        var felt = new Vec3(river.x, table.getY() + TableLayout.TABLE_TOP, river.z);
        var facing = new Vec3(.6, 0, .8);
        var feet = felt.subtract(facing.scale(TableLayout.TILE_WIDTH));
        var session = new WinEffectSession(new WinEffectContext("riichi_mahjong:warden",
                new WinEffectContext.Source("river/0/13", 34), List.of(new WinEffectContext.Winner(1, 34))));
        var holder = new ElementHolder();
        RonActorDisplay display = null;
        int checked = 0;
        try {
            do {
                var pose = session.frame().actors().stream()
                        .filter(actor -> actor.kind() == WinEffectFrame.Actor.WARDEN
                                && actor.action() == WinEffectFrame.Action.EMERGE)
                        .findFirst();
                if (pose.isEmpty()) continue;
                if (display == null) {
                    display = RonActorDisplay.create(holder, level, pose.get(), felt, facing, river);
                } else {
                    display.update(pose.get());
                }
                var element = (EntityElement<?>) holder.getElements().getFirst();
                var entity = element.entity();
                if (Math.hypot(entity.getX() - feet.x, entity.getZ() - feet.z) > 1e-6)
                    throw new AssertionError("Warden must emerge vertically one tile width behind the discard");
                // The first packet is emitted as soon as the element is added, before the first holder tick.
                if (checked == 0) verifySpawn(element, viewer, entity.position());
                // The probe uses a detached holder. Advance its tracker directly, as the table holder does.
                element.tick();
                verifySpawn(element, viewer, entity.position());
                checked++;
            } while (!session.advance());
            if (checked < 2) throw new AssertionError("Warden emergence did not run");
        } finally {
            if (display != null) display.destroy();
            holder.destroy();
        }
    }

    private static void verifySpawn(EntityElement<?> element, ServerPlayer viewer, Vec3 expected) {
        var spawns = new ArrayList<ClientboundAddEntityPacket>();
        element.startWatching(viewer, packet -> {
            if (packet instanceof ClientboundAddEntityPacket spawn) spawns.add(spawn);
        });
        if (spawns.size() != 1) throw new AssertionError("Missing or duplicated Warden pairing packet");
        var spawn = spawns.getFirst();
        if (Math.hypot(spawn.getX() - expected.x, spawn.getZ() - expected.z) > 1e-6
                || Math.abs(spawn.getY() - expected.y) > .004)
            throw new AssertionError("Warden pairing position " + new Vec3(spawn.getX(), spawn.getY(), spawn.getZ())
                    + " disagrees with its offset and emergence height " + expected);
    }
}
