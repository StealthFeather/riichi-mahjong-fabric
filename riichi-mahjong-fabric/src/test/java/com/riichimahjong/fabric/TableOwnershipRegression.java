package com.riichimahjong.fabric;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Ownership remains stable across leaving seats, reconnects and persisted legacy tables. */
public final class TableOwnershipRegression {
    private static final UUID FIRST = UUID.fromString("0cc4f872-646b-4d61-9c74-cefa8ed6a1ce");
    private static final UUID SECOND = UUID.fromString("e490cc5d-0e78-46f9-95dc-112833fb6a47");
    private static final UUID OUTSIDER = UUID.fromString("0838be6d-8e8b-4b15-a7d8-6cde193ae26a");

    private static ValueInput input(CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),tag);
    }

    private static CompoundTag save(TableOwnership ownership) {
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        ownership.write(output);
        return output.buildResult();
    }

    private static void explicitOfflineTransfer() {
        var ownership = new TableOwnership();
        check(ownership.owner().isEmpty() && ownership.isHost(FIRST) && ownership.isHost(SECOND),
                "an unclaimed new table did not preserve its existing open settings behavior");
        check(!ownership.hasOfflineOwner(id -> false) && !ownership.takeOver(SECOND,FIRST,id -> false),
                "an ownerless table turned an ordinary start gesture into a takeover");
        check(ownership.claimIfAbsent(FIRST) && !ownership.claimIfAbsent(SECOND),
                "a later seated player replaced the initial owner");
        check(ownership.isHost(FIRST) && !ownership.isHost(SECOND),"owner-only permissions followed another seat");
        check(!ownership.hasOfflineOwner(Set.of(FIRST)::contains)
                        && !ownership.takeOver(SECOND,FIRST,Set.of(FIRST)::contains),
                "a second player took a table whose owner remains online");
        check(!ownership.takeOver(SECOND,OUTSIDER,id -> false),"a stale owner expectation allowed takeover");
        check(!ownership.takeOver(FIRST,FIRST,id -> false),"the owner reclaimed their own table as another player");
        check(ownership.hasOfflineOwner(id -> false) && ownership.takeOver(SECOND,FIRST,id -> false),
                "an explicit offline-owner takeover was rejected");
        check(ownership.owner().equals(Optional.of(SECOND)) && !ownership.isHost(FIRST)
                        && ownership.isHost(SECOND),"takeover did not move the authority to the new owner");
        check(!ownership.claimIfAbsent(FIRST) && ownership.owner().equals(Optional.of(SECOND)),
                "the former owner reconnecting or retaking a seat automatically recovered ownership");
        check(!ownership.takeOver(FIRST,FIRST,id -> false),"a repeated stale takeover replaced the new owner");
        check(!ownership.takeOver(FIRST,SECOND,Set.of(SECOND)::contains),"the old owner displaced an online new owner");
        check(ownership.takeOver(OUTSIDER,SECOND,id -> false) && ownership.isHost(OUTSIDER),
                "a nearby unseated player could not become host after the current host left");
    }

    private static void persistedAndLegacyOwnership() {
        var ownership = new TableOwnership();
        ownership.claimIfAbsent(FIRST);
        var restored = new TableOwnership();
        restored.read(input(save(ownership)),Optional.of(SECOND));
        check(restored.owner().equals(Optional.of(FIRST)),"stored ownership was overridden by seat order on reload");
        check(restored.takeOver(SECOND,FIRST,id -> false),"restored offline owner could not be replaced");
        ownership.read(input(save(restored)),Optional.of(FIRST));
        check(ownership.owner().equals(Optional.of(SECOND)),"new ownership did not survive a second save/reload");

        var legacy = new TableOwnership();
        legacy.read(input(new CompoundTag()),Optional.of(FIRST));
        check(legacy.owner().equals(Optional.of(FIRST)),"legacy first-occupied-seat ownership was not migrated");
        legacy.read(input(new CompoundTag()),Optional.empty());
        check(legacy.owner().isEmpty(),"loading an ownerless legacy table retained a previous table's owner");
        var malformed = new CompoundTag();malformed.putString("TableOwner","not-a-uuid");
        legacy.read(input(malformed),Optional.of(SECOND));
        check(legacy.owner().equals(Optional.of(SECOND)),"invalid ownership metadata did not use the legacy fallback");
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        output.store("TableOwner",UUIDUtil.CODEC,OUTSIDER);
        legacy.read(input(output.buildResult()),Optional.empty());
        check(legacy.owner().equals(Optional.of(OUTSIDER)),"an owner without a reserved seat was not preserved");
    }

    private static void actualCentreRay() {
        var master = new BlockPos(12,64,-9);
        double top = master.getY() + TableLayout.TABLE_TOP;
        var centre = new Vec3(master.getX()+.5,top,master.getZ()+.5);
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var eye = centre.add(direction.getStepX()*2,1.2,direction.getStepZ()*2);
            var end = eye.add(centre.subtract(eye).normalize().scale(8));
            check(TableOwnership.hitsCentre(master,eye,end),"a genuine centre click from "+direction+" was rejected");
            var edge = centre.add(direction.getStepX(),0,direction.getStepZ());
            end = eye.add(edge.subtract(eye).normalize().scale(8));
            check(!TableOwnership.hitsCentre(master,eye,end),"a synthetic centre hit accepted a table-edge sight ray");
            check(!TableOwnership.hitsCentre(master,eye,eye.add(eye.subtract(centre).normalize().scale(8))),
                    "a player facing away could take ownership through a synthetic hit");
        }
        var eye = centre.add(0,2,2);
        check(!TableOwnership.hitsCentre(master,eye,eye.add(2,0,0)),"a horizontal sight ray hit the tabletop centre");
        check(!TableOwnership.hitsCentre(master,centre.add(0,-1,0),centre.add(0,1,0)),
                "an underside click was treated as a tabletop centre click");
        var distant = centre.add(0,1,9);
        check(!TableOwnership.hitsCentre(master,distant,distant.add(centre.subtract(distant).scale(2))),
                "a centre beyond eight blocks was accepted");
        check(!TableOwnership.hitsCentre(master,eye,eye.add(centre.subtract(eye).scale(.5))),
                "an unfinished sight segment reached the centre");
    }

    public static void main(String[] args) {
        explicitOfflineTransfer();
        persistedAndLegacyOwnership();
        actualCentreRay();
        System.out.println("PASS: explicit offline-owner takeover, stable authority across seat changes and reconnects, and stale/online-owner rejection");
        System.out.println("PASS: independent owner UUID persistence, ownerless tables and legacy first-seat fallback");
        System.out.println("PASS: real centre sight rays from every side reject virtual table edges, facing away and out-of-range clicks");
    }
}
