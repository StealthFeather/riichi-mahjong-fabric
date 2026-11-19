package com.riichimahjong.fabric;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Viewing a shared menu never grants the authority needed to confirm ownership transfer. */
public final class MenuAccessRegression {
    private static final UUID OWNER = UUID.fromString("715358a1-0852-43df-8851-e54f1af2daaf");
    private static final UUID TARGET = UUID.fromString("923c5a26-a832-4db6-a040-e1d8f7ea8172");
    private static final UUID VISITOR = UUID.fromString("d5f58a6d-464f-424c-950d-a290eb7a3b3e");

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static TableOwnership reload(TableOwnership ownership) {
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        ownership.write(out);
        var in = TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), out.buildResult());
        var restored = new TableOwnership();
        restored.read(in, Optional.of(OWNER));
        return restored;
    }

    private static void confirmedOwnershipTransfer() {
        var ownership = new TableOwnership();
        check(!ownership.transfer(OWNER, OWNER, TARGET), "an ownerless table accepted a transfer confirmation");
        check(ownership.claimIfAbsent(OWNER), "owner fixture could not claim the table");
        check(!ownership.claimIfAbsent(VISITOR) && ownership.owner().equals(Optional.of(OWNER)),
                "a visitor opening a menu replaced an existing owner");
        check(!ownership.transfer(VISITOR, OWNER, TARGET), "a visitor confirmed the owner's transfer");
        check(!ownership.transfer(OWNER, TARGET, VISITOR), "an obsolete confirmation changed a different owner");
        check(!ownership.transfer(OWNER, OWNER, OWNER), "self-transfer pretended to change authority");
        check(!ownership.transfer(null, OWNER, TARGET) && !ownership.transfer(OWNER, null, TARGET)
                        && !ownership.transfer(OWNER, OWNER, null),
                "a malformed transfer confirmation changed authority");
        check(ownership.owner().equals(Optional.of(OWNER)), "rejected confirmations still mutated the owner");
        check(ownership.transfer(OWNER, OWNER, TARGET), "the owner could not deliberately transfer authority");
        check(ownership.owner().equals(Optional.of(TARGET)) && ownership.isHost(TARGET)
                        && !ownership.isHost(OWNER) && !ownership.isHost(VISITOR),
                "the confirmed transfer did not revoke the former owner's settings authority");
        check(!ownership.transfer(OWNER, OWNER, VISITOR) && !ownership.transfer(OWNER, TARGET, VISITOR),
                "a former owner's open confirmation retained authority after transfer");
        ownership = reload(ownership);
        check(ownership.owner().equals(Optional.of(TARGET)) && !ownership.isHost(OWNER),
                "save/reload restored the former owner instead of the explicitly transferred owner");
        check(ownership.transfer(TARGET, TARGET, VISITOR), "the new owner could not transfer authority again");
        check(!ownership.transfer(TARGET, TARGET, OWNER) && ownership.owner().equals(Optional.of(VISITOR)),
                "a repeated old confirmation displaced the current owner");
    }

    public static void main(String[] args) {
        confirmedOwnershipTransfer();
        System.out.println("PASS: explicit transfers reject visitors, obsolete owners and repeated confirmations and survive NBT");
    }
}
