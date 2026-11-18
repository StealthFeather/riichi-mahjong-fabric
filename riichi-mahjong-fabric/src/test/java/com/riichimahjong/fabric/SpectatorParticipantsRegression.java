package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.SpectatorParticipants;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Participation must outlive leaving, seat replacement and a real table NBT reload. */
public final class SpectatorParticipantsRegression {
    public static void main(String[] args) {
        UUID original = UUID.randomUUID(), replacement = UUID.randomUUID(), spectator = UUID.randomUUID();
        var history = new SpectatorParticipants();
        history.begin(List.of(original));
        history.remember(replacement);
        check(history.contains(original) && history.contains(replacement),
                "replacing a participant discarded the former occupant");
        check(!history.contains(spectator), "an ordinary spectator became a participant");

        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        history.write(output);
        var input = TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), output.buildResult());
        var restored = new SpectatorParticipants();
        restored.read(input);
        check(restored.contains(original) && restored.contains(replacement) && !restored.contains(spectator),
                "reloading a table lost its participants");
        restored.begin(List.of(spectator));
        check(!restored.contains(original) && !restored.contains(replacement) && restored.contains(spectator),
                "starting a new match retained identities from a previous game");
        restored.read(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), new CompoundTag()));
        check(!restored.contains(spectator), "legacy NBT retained prior in-memory history");
        restored.remember(original);
        check(restored.contains(original), "known seats could not be restored from an old active table");
        restored.clear();
        check(!restored.contains(original), "ending a match retained spectator restrictions");
        System.out.println("PASS: spectator participant history survives replacement and NBT reload, and resets between matches");
    }
}
