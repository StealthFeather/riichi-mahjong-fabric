package com.riichimahjong.fabric;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** The table's owner stays independent of seat occupancy and temporary AI controllers. */
public final class TableOwnership {
    private UUID owner;

    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public boolean claimIfAbsent(UUID claimant) {
        if (owner != null || claimant == null) return false;
        owner = claimant;
        return true;
    }

    public boolean isHost(UUID player) {
        return player != null && (owner == null || owner.equals(player));
    }

    public boolean hasOfflineOwner(Predicate<UUID> online) {
        return owner != null && !online.test(owner);
    }

    public boolean takeOver(UUID claimant, UUID expectedOwner, Predicate<UUID> online) {
        if (claimant == null || owner == null || !owner.equals(expectedOwner)
                || owner.equals(claimant) || online.test(owner)) return false;
        owner = claimant;
        return true;
    }

    /** Only the expected current owner may explicitly hand authority to another player. */
    public boolean transfer(UUID actor, UUID expectedOwner, UUID target) {
        if (owner == null || actor == null || target == null || !owner.equals(expectedOwner)
                || !owner.equals(actor) || actor.equals(target)) return false;
        owner = target;
        return true;
    }

    /** Virtual table collisions synthesize a centre block hit; use the actual sight ray instead. */
    public static boolean hitsCentre(BlockPos master, Vec3 eye, Vec3 end) {
        double height = master.getY() + TableLayout.TABLE_TOP;
        double dy = end.y - eye.y;
        if (eye.y <= height || dy >= -1e-9) return false;
        double progress = (height - eye.y) / dy;
        if (progress < 0 || progress > 1) return false;
        var hit = eye.add(end.subtract(eye).scale(progress));
        return eye.distanceToSqr(hit) <= 64
                && hit.x >= master.getX() && hit.x < master.getX() + 1
                && hit.z >= master.getZ() && hit.z < master.getZ() + 1;
    }

    public void write(ValueOutput out) {
        if (owner != null) out.store("TableOwner",UUIDUtil.CODEC,owner);
    }

    public void read(ValueInput in, Optional<UUID> legacyOwner) {
        owner = in.read("TableOwner",UUIDUtil.CODEC).orElseGet(() -> legacyOwner.orElse(null));
    }
}
