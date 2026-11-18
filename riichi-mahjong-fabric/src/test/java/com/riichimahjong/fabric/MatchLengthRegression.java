package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.riichimahjong.themahjongcompat.MatchNbt;
import com.themahjong.*;
import com.themahjong.driver.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Scheduled winds change match boundaries without changing scoring presets or their extensions. */
public final class MatchLengthRegression {
    private static List<MahjongPlayerInterface> humans(int count) {
        return java.util.stream.IntStream.range(0,count)
                .mapToObj(seat -> (MahjongPlayerInterface)new MahjongHumanPlayer()).toList();
    }

    private static ValueInput input(CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),tag);
    }

    private static TheMahjongMatch reload(TheMahjongMatch match) {
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        MatchNbt.writeMatch(out,match);
        return MatchNbt.readMatch(input(out.buildResult()));
    }

    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(out,driver);
        return DriverNbt.readDriver(input(out.buildResult()),humans(driver.match().playerCount()),new Random(7));
    }

    private static List<Integer> scores(TheMahjongMatch match, int leader) {
        var points = new ArrayList<Integer>();
        for (int seat=0;seat<match.playerCount();seat++) points.add(match.startingPoints()-5000);
        points.set(leader,match.startingPoints()+(match.playerCount()-1)*5000);
        check(points.get(leader)>=match.targetPoints(),"score fixture has no player at its target");
        return points;
    }

    private static TheMahjongDriver result(TheMahjongMatch base, TheMahjongTile.Wind wind,
            int dealer, List<Integer> points) {
        var players = new ArrayList<TheMahjongPlayer>();
        for (int seat=0;seat<base.playerCount();seat++) {
            players.add(new TheMahjongPlayer(points.get(seat),TheMahjongPlayer.RiichiState.NONE,false,
                    waiting(1+seat%2),List.of(),List.of(),List.of()));
        }
        var indicators = Collections.nCopies(5,tile(4,3));
        var round = new TheMahjongRound(wind,dealer+1,2,0,dealer,TheMahjongRound.State.ENDED,
                dealer,-1,TheMahjongRound.ActiveTile.none(),List.of(),
                Collections.nCopies(TheMahjongRound.rinshanTileCountFor(base.playerCount()),tile(4,2)),
                indicators,indicators,1,players,List.of());
        var match = new TheMahjongMatch(base.playerCount(),base.startingPoints(),base.targetPoints(),
                base.roundCount(),TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),round);
        return new TheMahjongDriver(match,humans(base.playerCount()),new Random(7),
                new TheMahjongDriver.Snapshot(new MatchPhase.RoundEnded(List.of()),false,OptionalInt.empty()));
    }

    private static void initialPresetsAndPersistence() {
        for (var preset : RuleSetPreset.values()) {
            if (preset.family() == RuleSetPreset.Family.SICHUAN) continue; // Covered by SichuanSettingsRegression.
            var original = preset.newMatch();
            check(original.roundCount()==2,"default match length changed from the existing South game");
            for (var length : MatchLength.values()) {
                var match = preset.newMatch(length).validate();
                int scheduledWinds = length==MatchLength.EAST ? 1 : 2;
                int starting = (preset==RuleSetPreset.WRC_4P || preset==RuleSetPreset.JPML_4P) ? 30000 : preset.playerCount()==3 ? 35000 : 25000;
                int target = preset.playerCount()==3 ? 40000 : 30000;
                check(match.roundCount()==scheduledWinds,"East/South selection did not set the scheduled wind count");
                check(match.playerCount()==preset.playerCount() && match.startingPoints()==starting
                                && match.targetPoints()==target,
                        "selecting match length changed players, initial scores or target: "+preset);
                check(match.tileSet().equals(original.tileSet()) && match.ruleSet().equals(original.ruleSet()),
                        "selecting match length changed red tiles, sanma tiles or scoring rules: "+preset);
                check(match.equals(reload(match)),"unstarted match serialization lost match length or preset fields");

                var started = match.startRound(new Random(5));
                var round = started.currentRound().orElseThrow();
                check(round.roundWind()==TheMahjongTile.Wind.EAST && round.handNumber()==1 && round.dealerSeat()==0,
                        "a South game must still begin at East one");
                check(round.players().size()==preset.playerCount()
                                && round.players().stream().allMatch(player -> player.points()==starting),
                        "selected length changed the actual starting deal's players or points");
                check(started.roundCount()==scheduledWinds && started.equals(reload(started)),
                        "active match serialization changed the scheduled winds");
                var driver = new TheMahjongDriver(started,humans(started.playerCount()),new Random(5),
                        new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDraw(0,false),false,OptionalInt.empty()));
                var restored = reload(driver);
                check(restored.match().equals(driver.match()) && restored.currentPhase().equals(driver.currentPhase()),
                        "driver serialization changed match length or the live deal");
            }
        }
    }

    private static void advance(TheMahjongDriver driver, boolean renchan, int honba, boolean ended,
            TheMahjongTile.Wind nextWind, int nextDealer, int nextHand) {
        var before = driver.currentRound();
        var scores = before.players().stream().map(TheMahjongPlayer::points).toList();
        int winds = driver.match().roundCount();
        driver.advanceRound(renchan,honba);
        check(driver.match().roundCount()==winds,"advancing a hand changed the selected match length");
        check(driver.match().completedRounds().size()==1
                        && driver.match().completedRounds().getFirst().equals(before),
                "advancing discarded the completed result or added it twice");
        check(driver.currentRound().players().stream().map(TheMahjongPlayer::points).toList().equals(scores),
                "match-length handling changed settled player scores");
        if (ended) {
            check(driver.currentPhase() instanceof MatchPhase.MatchEnded
                            && driver.match().state()==TheMahjongMatch.State.ENDED,
                    "scheduled match endpoint did not terminate the real driver");
            check(driver.currentRound().equals(before),"ending the match started an unwanted extra deal");
        } else {
            check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw
                            && driver.match().state()==TheMahjongMatch.State.IN_ROUND,
                    "an unfinished scheduled match terminated early");
            var after = driver.currentRound();
            check(after.roundWind()==nextWind && after.dealerSeat()==nextDealer
                            && after.handNumber()==nextHand && after.honba()==honba,
                    "next deal has incorrect wind/dealer/hand/honba");
        }
    }

    private static void scheduledEndpointsAndRenchan() {
        for (var preset : RuleSetPreset.values()) for (var length : MatchLength.values()) {
            if (preset.family() == RuleSetPreset.Family.SICHUAN) continue; // These assertions exercise riichi continuation/extension rules.
            var base = preset.newMatch(length);
            int last = base.playerCount()-1;
            var points = scores(base,0);
            var eastEnd = result(base,TheMahjongTile.Wind.EAST,last,points);
            check(eastEnd.match().isAllLast()==(length==MatchLength.EAST),
                    "all-last detection did not follow the selected East/South length");
            // The real driver must either stop after East's last dealer or enter South one.
            advance(reload(eastEnd),false,3,length==MatchLength.EAST,TheMahjongTile.Wind.SOUTH,0,1);

            var finalWind = length==MatchLength.EAST ? TheMahjongTile.Wind.EAST : TheMahjongTile.Wind.SOUTH;
            var finalHand = result(base,finalWind,last,points);
            check(finalHand.match().isAllLast(),"the selected scheduled final hand is not all-last");
            advance(reload(finalHand),false,3,true,null,-1,-1);
            advance(reload(finalHand),true,3,false,finalWind,last,base.playerCount());

            var dealerLeading = result(base,finalWind,last,scores(base,last));
            check(dealerLeading.match().isAgariYameEligible()==base.ruleSet().agariYameAllowed(),
                    "existing dealer-leading agari-yame eligibility moved away from the selected last hand");
            // Preserve the existing engine contract: eligibility is a query, not automatic termination.
            advance(dealerLeading,true,3,false,finalWind,last,base.playerCount());
            int low = base.startingPoints()-10000;
            int tiedFirst = (base.startingPoints()*base.playerCount()-low*(base.playerCount()-2))/2;
            var tied = new ArrayList<>(Collections.nCopies(base.playerCount(),low));
            tied.set(0,tiedFirst);tied.set(last,tiedFirst);
            check(!result(base,finalWind,last,tied).match().isAgariYameEligible(),
                    "a tied dealer incorrectly became agari-yame eligible");
            var prior = result(base,finalWind,last-1,scores(base,last-1));
            check(!prior.match().isAllLast() && !prior.match().isAgariYameEligible(),
                    "a dealer before the final hand became agari-yame eligible");
        }
    }

    private static void existingSuddenDeathBoundaries() {
        for (var preset : RuleSetPreset.values()) for (var length : MatchLength.values()) {
            if (preset.family() == RuleSetPreset.Family.SICHUAN) continue; // These assertions exercise riichi continuation/extension rules.
            var base = preset.newMatch(length);
            if (!base.ruleSet().suddenDeathRound()) continue;
            var low = Collections.nCopies(base.playerCount(),base.startingPoints());
            check(low.stream().allMatch(points -> points<base.targetPoints()),"extension fixture reached the target");
            int last = base.playerCount()-1;
            var finalWind = length==MatchLength.EAST ? TheMahjongTile.Wind.EAST : TheMahjongTile.Wind.SOUTH;
            var extensionWind = length==MatchLength.EAST ? TheMahjongTile.Wind.SOUTH : TheMahjongTile.Wind.WEST;
            var driver = result(base,finalWind,last,low);
            advance(driver,false,3,false,extensionWind,0,1);
            var restored = reload(driver);
            check(restored.match().roundCount()==base.roundCount()
                            && restored.currentRound().roundWind()==extensionWind
                            && !restored.match().isAllLast() && !restored.match().isAgariYameEligible(),
                    "extension reload confused current wind with the scheduled match length");
            // Remaining below target continues within the extension rather than starting a new scheduled game.
            advance(result(base,extensionWind,0,low),false,4,false,extensionWind,1,2);
            // Existing sudden death terminates as soon as anyone reaches target, even on a dealer continuation.
            advance(result(base,extensionWind,0,scores(base,0)),true,4,true,null,-1,-1);
            advance(result(base,TheMahjongTile.Wind.NORTH,last,low),false,4,true,null,-1,-1);
        }
    }

    public static void main(String[] args) {
        initialPresetsAndPersistence();
        scheduledEndpointsAndRenchan();
        existingSuddenDeathBoundaries();
        System.out.println("PASS: five scoring presets retain players, points, tiles and rules for East/South games; match/driver NBT round-trips preserve scheduled winds");
        System.out.println("PASS: real 3p/4p drivers stop or cross East/South boundaries correctly, preserving renchan and existing agari-yame eligibility");
        System.out.println("PASS: original South/West sudden-death extensions, target termination and North hard boundary remain intact after save/reload");
    }
}
