package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.themahjongcompat.MatchNbt;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.sichuan.SichuanRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Rule-family selection and real match NBT retain the selected Sichuan variants and schedule. */
public final class SichuanSettingsRegression {
    private static ValueInput input(CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), tag);
    }

    private static CompoundTag save(TheMahjongMatch match) {
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        MatchNbt.writeMatch(out, match);
        return out.buildResult();
    }

    private static TheMahjongMatch reload(TheMahjongMatch match) {
        var restored = MatchNbt.readMatch(input(save(match)));
        check(restored.equals(match), "real match NBT changed the rules, variants, tiles or round: " + match);
        return restored;
    }

    private static void ruleFamilies() {
        var families = RuleSetPreset.Family.values();
        check(Stream.of(families).map(RuleSetPreset.Family::label).toList()
                        .equals(List.of("雀魂", "天凤", "WRC", "川麻", "公式战", "M-League")),
                "the first rule row must contain the six rule families in order");
        for (var family : families) {
            check(family.preset(4).family() == family && family.preset(4).playerCount() == 4,
                    "a four-player family selection changed the selected rule family");
            var threeSelection = family.preset(3);
            check(threeSelection.family() == family,
                    "switching player count changed the selected rule family");
            check(threeSelection.playerCount() == (family.supportsThreePlayers() ? 3 : 4),
                    "a family selected an unsupported three-player ruleset: " + family);
        }
        check(!RuleSetPreset.Family.SICHUAN.supportsThreePlayers()
                        && RuleSetPreset.Family.SICHUAN.preset(3) == RuleSetPreset.SICHUAN_4P,
                "switching from sanma to Sichuan must restore four-player mode");
        for(var family:List.of(RuleSetPreset.Family.JPML,RuleSetPreset.Family.M_LEAGUE))
            check(!family.supportsThreePlayers()&&family.preset(3).playerCount()==4,
                    "switching from sanma to a four-player competition rule must restore four seats");
        for (var preset : RuleSetPreset.values()) {
            check(preset.family().preset(preset.playerCount()) == preset,
                    "separating family and player count lost an existing rules preset");
            if (preset.family() != RuleSetPreset.Family.SICHUAN) {
                for (var length : MatchLength.values()) {
                    var variant = preset.newMatch(length, true, true);
                    check(variant.ruleSet().exchangeThree() && variant.ruleSet().jokersEnabled()
                                    && !variant.ruleSet().sichuan() && variant.playerCount() == preset.playerCount(),
                            "shared options did not reach an existing riichi preset: " + preset);
                    check(preset.newMatch(length, false, false).equals(preset.newMatch(length)),
                            "disabled variants changed an existing riichi preset: " + preset);
                }
            }
        }
    }

    private static void variantMatrix() {
        var defaults = RuleSetPreset.SICHUAN_4P.newMatch();
        check(defaults.ruleSet().sichuan() && !defaults.ruleSet().exchangeThree()
                        && !defaults.ruleSet().jokersEnabled(),
                "a new Sichuan table must default to no exchange and no jokers");
        for (var length : MatchLength.values()) for (boolean exchange : new boolean[]{false, true})
                for (boolean jokers : new boolean[]{false, true}) {
            var match = RuleSetPreset.SICHUAN_4P.newMatch(length, exchange, jokers).validate();
            check(match.playerCount() == 4 && match.roundCount() == length.roundCount(),
                    "Sichuan variants changed player count or the selected East/South schedule");
            check(match.ruleSet().sichuan() && match.ruleSet().exchangeThree() == exchange
                            && match.ruleSet().jokersEnabled() == jokers && match.ruleSet().chiDisabled(),
                    "the factory lost a selected Sichuan option or enabled chi");
            check(!match.ruleSet().suddenDeathRound(), "Sichuan unexpectedly enabled a riichi target extension");
            var wall = match.tileSet().createOrderedWall();
            var redDragon = new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 3, false);
            check(wall.size() == (jokers ? 112 : 108), "Sichuan has the wrong wall size");
            check(wall.stream().filter(redDragon::equals).count() == (jokers ? 4 : 0),
                    "joker selection did not add exactly four red dragons");
            check(wall.stream().noneMatch(tile -> tile.redDora()
                            || tile.suit() == TheMahjongTile.Suit.WIND
                            || tile.suit() == TheMahjongTile.Suit.DRAGON && !tile.equals(redDragon)),
                    "Sichuan included riichi honors or red fives");
            check(match.tileSet().copiesPerTile().values().stream().allMatch(copies -> copies == 4),
                    "Sichuan tile copies changed when selecting a variant");
            reload(match);
            var started = reload(prepare(match.startRound(new Random(17))));
            var round = started.currentRound().orElseThrow();
            check(round.players().size() == 4 && round.players().stream().allMatch(player -> player.currentHand().size() == 13),
                    "Sichuan starting deal did not retain four thirteen-tile hands");
            check(round.rinshanTiles().isEmpty() && round.doraIndicators().isEmpty()
                            && round.uraDoraIndicators().isEmpty() && round.revealedDoraCount() == 0,
                    "Sichuan starting deal retained a riichi dead wall or dora");
            check(round.liveWall().size() == (jokers ? 60 : 56)
                            && round.liveWall().stream().noneMatch(redDragon::equals),
                    "Sichuan must reserve each joker for an initial hand, leaving an ordinary-only wall");
            for (var player : round.players()) {
                check(player.currentHand().stream().filter(redDragon::equals).count() == (jokers ? 1 : 0),
                        "Sichuan must deal exactly one joker per player when the variant is enabled");
                check(player.currentHand().stream().filter(tile -> tile.suit().isNumber()).count() == (jokers ? 12 : 13),
                        "Sichuan did not deal the required twelve/thirteen ordinary initial tiles");
            }
            var dealt = new ArrayList<>(round.liveWall());
            round.players().forEach(player -> dealt.addAll(player.currentHand()));
            check(dealt.size() == wall.size()
                            && match.tileSet().tiles().stream().allMatch(tile ->
                                    dealt.stream().filter(tile::equals).count() == match.tileSet().copiesPerTile().get(tile)),
                    "starting a Sichuan game lost or duplicated wall tiles");
        }
    }

    private static TheMahjongMatch prepare(TheMahjongMatch match) {
        if (match.state() != TheMahjongMatch.State.IN_ROUND) return match;
        return new TheMahjongMatch(match.playerCount(), match.startingPoints(), match.targetPoints(),
                match.roundCount(), match.state(), match.tileSet(), match.ruleSet(), match.completedRounds(),
                SichuanRules.prepareRound(match.currentRound().orElseThrow()));
    }

    private static TheMahjongMatch ended(TheMahjongMatch match) {
        var r = match.currentRound().orElseThrow();
        var round = new TheMahjongRound(r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(),
                r.dealerSeat(), TheMahjongRound.State.ENDED, r.currentTurnSeat(), -1,
                TheMahjongRound.ActiveTile.none(), r.liveWall(), r.rinshanTiles(),
                r.doraIndicators(), r.uraDoraIndicators(), r.revealedDoraCount(),
                r.pendingKanDoraReveals(), r.players(), List.of());
        return new TheMahjongMatch(match.playerCount(), match.startingPoints(), match.targetPoints(),
                match.roundCount(), TheMahjongMatch.State.IN_ROUND, match.tileSet(), match.ruleSet(),
                match.completedRounds(), round);
    }

    private static void scheduledWinds() {
        for (var length : MatchLength.values()) {
            var random = new Random(26);
            var match = prepare(RuleSetPreset.SICHUAN_4P.newMatch(length, true, true).startRound(random));
            int hands = length.roundCount() * 4;
            for (int hand = 0; hand < hands; hand++) {
                var round = match.currentRound().orElseThrow();
                check(round.roundWind().ordinal() == hand / 4 && round.dealerSeat() == hand % 4
                                && round.handNumber() == hand % 4 + 1,
                        "Sichuan advanced to the wrong scheduled wind, dealer or hand");
                match = prepare(reload(ended(match)).advanceRound(random, false, 0));
                check(match.state() == (hand == hands - 1 ? TheMahjongMatch.State.ENDED : TheMahjongMatch.State.IN_ROUND),
                        "Sichuan did not finish at its selected East/South boundary");
                check(match.completedRounds().size() == hand + 1,
                        "Sichuan advancement lost or duplicated a completed hand");
                check(match.ruleSet().exchangeThree() && match.ruleSet().jokersEnabled(),
                        "advancing Sichuan discarded its selected variants");
            }
            reload(match);
        }
    }

    private static void legacyRiichiRecords() {
        var legacy = RuleSetPreset.MAHJONG_SOUL_4P.newMatch(MatchLength.EAST).startRound(new Random(8));
        var tag = save(legacy);
        tag.remove("sichuan");
        tag.remove("exchangeThree");
        tag.remove("jokersEnabled");
        var restored = MatchNbt.readMatch(input(tag));
        check(restored.equals(legacy) && !restored.ruleSet().sichuan()
                        && !restored.ruleSet().exchangeThree() && !restored.ruleSet().jokersEnabled(),
                "an older record without Sichuan fields acquired Sichuan options");
    }

    public static void main(String[] args) {
        ruleFamilies();
        variantMatrix();
        scheduledWinds();
        legacyRiichiRecords();
        System.out.println("PASS: six ordered rule families preserve existing presets and keep competition rules four-player only");
        System.out.println("PASS: all Sichuan exchange/joker combinations preserve 108/112 tiles and flags through real match NBT");
        System.out.println("PASS: Sichuan East/South games advance through their scheduled winds and finish without riichi extensions");
        System.out.println("PASS: legacy riichi records default all Sichuan-specific flags off");
    }
}
