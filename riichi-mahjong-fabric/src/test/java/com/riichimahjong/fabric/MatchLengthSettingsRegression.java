package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.replay.ReplayRecord;
import com.riichimahjong.replay.ReplayRecorder;
import com.riichimahjong.themahjongcompat.MatchNbt;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Persisted table settings and older table-record matches must agree on scheduled winds. */
public final class MatchLengthSettingsRegression {
    private static ValueInput input(CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), tag);
    }

    private static CompoundTag save(MatchLength length) {
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        length.write(out);
        return out.buildResult();
    }

    private static void idleSettings() {
        for (var length : MatchLength.values()) {
            var tag = save(length);
            check(tag.getStringOr("MatchLength", "").equals(length.name()),
                    "the table setting did not use its stable top-level NBT key");
            check(MatchLength.read(input(tag), null) == length,
                    "idle " + length + " did not survive a real NBT round trip");
        }

        // World loads and record imports assign every read result to the same table field.
        var selection = MatchLength.read(input(save(MatchLength.EAST)), null);
        check(selection == MatchLength.EAST, "the previous table setting was not east-only");
        selection = MatchLength.read(input(new CompoundTag()), null);
        check(selection == MatchLength.SOUTH,
                "an older idle snapshot retained a previously selected east-only setting");

        for (String malformed : List.of("", "east", "WEST", "SOUTH ")) {
            var tag = new CompoundTag();
            tag.putString("MatchLength", malformed);
            check(MatchLength.read(input(tag), null) == MatchLength.SOUTH,
                    "invalid idle setting did not use the legacy south default: " + malformed);
        }
        var wrongType = new CompoundTag();
        wrongType.putInt("MatchLength", 1);
        check(MatchLength.read(input(wrongType), null) == MatchLength.SOUTH,
                "a non-string match-length tag did not use the legacy south default");
    }

    private static TheMahjongMatch matchAt(RuleSetPreset preset, MatchLength length,
            TheMahjongTile.Wind wind, boolean terminal) {
        var base = preset.newMatch(length);
        int dealer = terminal ? base.playerCount() - 1 : 0;
        var round = TheMahjongRound.start(base.playerCount(), base.startingPoints(),
                base.tileSet().createOrderedWall(), wind, dealer + 1, 0, 0, dealer);
        if (terminal) round = round.exhaustiveDraw(base.ruleSet());
        return new TheMahjongMatch(base.playerCount(), base.startingPoints(), base.targetPoints(),
                base.roundCount(), terminal ? TheMahjongMatch.State.ENDED : TheMahjongMatch.State.IN_ROUND,
                base.tileSet(), base.ruleSet(), terminal ? List.of(round) : List.of(), round).validate();
    }

    private static void recordedMatchAuthority() {
        for (var preset : List.of(RuleSetPreset.WRC_4P, RuleSetPreset.TENHOU_SANMA_3P)) {
            for (var length : MatchLength.values()) {
                for (boolean terminal : new boolean[]{false, true}) {
                    var wind = length == MatchLength.EAST ? TheMahjongTile.Wind.EAST : TheMahjongTile.Wind.SOUTH;
                    var match = matchAt(preset, length, wind, terminal);
                    check((match.state() == TheMahjongMatch.State.ENDED) == terminal,
                            "the saved-match fixture does not have the intended lifecycle state");
                    var conflicting = length == MatchLength.EAST ? MatchLength.SOUTH : MatchLength.EAST;
                    checkRecorded(match, null, length);
                    checkRecorded(match, conflicting, length);
                }
            }
        }
    }

    private static void checkRecorded(TheMahjongMatch match, MatchLength storedSetting,
            MatchLength expected) {
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        if (storedSetting != null) storedSetting.write(out);
        // This is the actual nested match payload used by table saves and RecordVault snapshots.
        MatchNbt.writeMatch(out.child("Driver").child("match"), match);
        var tag = out.buildResult();
        check(storedSetting != null || !tag.contains("MatchLength"),
                "the legacy record fixture unexpectedly contains the new setting");
        var in = input(tag);
        var restored = MatchNbt.readMatch(in.childOrEmpty("Driver").childOrEmpty("match"));
        check(restored.equals(match), "the record match changed during NBT serialization");
        check(MatchLength.read(in, restored) == expected,
                "an active or terminal record did not retain its own scheduled winds");
        check(restored.roundCount() == match.roundCount(),
                "reading table settings rewrote the saved match length");

        tag.putString("MatchLength", "INVALID");
        check(MatchLength.read(input(tag), restored) == expected,
                "damaged menu metadata overrode the recorded match length");
    }

    private static void extensionsAndCustomRecords() {
        var extendedEast = matchAt(RuleSetPreset.TENHOU_4P, MatchLength.EAST,
                TheMahjongTile.Wind.SOUTH, false);
        check(extendedEast.roundCount() == 1
                        && extendedEast.currentRound().orElseThrow().roundWind() == TheMahjongTile.Wind.SOUTH,
                "the east-only extension fixture did not reach the south wind");
        checkRecorded(extendedEast, null, MatchLength.EAST);
        checkRecorded(extendedEast, MatchLength.SOUTH, MatchLength.EAST);

        var extendedSouth = matchAt(RuleSetPreset.TENHOU_SANMA_3P, MatchLength.SOUTH,
                TheMahjongTile.Wind.WEST, false);
        checkRecorded(extendedSouth, MatchLength.EAST, MatchLength.SOUTH);

        // Previously imported custom matches remain intact even when the menu offers only two modes.
        for (int count : new int[]{3, 4}) {
            checkRecorded(extendedSouth.withRoundCount(count), MatchLength.EAST, MatchLength.SOUTH);
        }
    }

    private static void replayLengthPersistence() {
        var registries = HolderLookup.Provider.create(Stream.empty());
        for (var length : MatchLength.values()) {
            // The same south-wind frame represents an east-only extension or a scheduled south round.
            var match = matchAt(RuleSetPreset.TENHOU_4P, length, TheMahjongTile.Wind.SOUTH, false);
            var paused = IntStream.range(0, match.playerCount())
                    .mapToObj(seat -> (MahjongPlayerInterface)(request, seconds) -> Optional.empty()).toList();
            var driver = new TheMahjongDriver(match, paused, new Random(29),
                    new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDraw(0, false), false, OptionalInt.empty()));
            var participants = IntStream.range(0, match.playerCount())
                    .mapToObj(seat -> new ReplayRecord.Participant(seat, null, "BOT", 25000)).toList();
            var recording = new ReplayRecorder(RuleSetPreset.TENHOU_4P.name(), participants);
            recording.capture(driver);
            recording = ReplayRecorder.fromNbt(recording.toNbt());
            var record = ReplayRecord.decode(ReplayRecord.fromNbt(recording.toNbt()).encode());
            check(record.frames().size() == 1, "replay restart or compression lost the captured frame");
            var restored = record.frames().getFirst().restore(registries);
            check(restored.match().roundCount() == length.roundCount(),
                    "replay persistence changed the selected scheduled winds");
            check(restored.currentRound().roundWind() == TheMahjongTile.Wind.SOUTH,
                    "replay persistence lost the south-wind frame");
            var conflicting = length == MatchLength.EAST ? MatchLength.SOUTH : MatchLength.EAST;
            check(MatchLength.read(input(save(conflicting)), restored.match()) == length,
                    "a restored replay inherited table metadata or mistook an east-only extension for south-length");
        }
    }

    public static void main(String[] args) {
        idleSettings();
        System.out.println("PASS: east/south table settings survive NBT, while missing or malformed idle metadata resets to the legacy south default");
        recordedMatchAuthority();
        System.out.println("PASS: 3p/4p active and terminal records retain their own scheduled winds despite missing, conflicting or damaged menu settings");
        extensionsAndCustomRecords();
        System.out.println("PASS: extension winds do not change the selected match mode, and older custom round counts remain intact");
        replayLengthPersistence();
        System.out.println("PASS: replay recording, restart, compressed payload and frame restoration preserve east/south scheduled winds, including an east-only south extension");
    }
}
