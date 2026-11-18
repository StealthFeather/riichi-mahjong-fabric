package com.riichimahjong.analysis;

import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.themahjong.TheMahjongMatch;
import com.themahjong.competition.CompetitiveRules;
import com.themahjong.competition.CompetitiveStandings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** Match evaluation points, kept as integer tenths independently from the player's point sticks. */
public final class ModeRatingRules {
    public record Result(int seat, int rank, int deltaTenths) {
        public String formatted() { return String.format(Locale.ROOT, "%+.1f", deltaTenths / 10.0); }
    }

    /** actualDeltaTenths includes the zero floor; quotedDeltaTenths is the original match result. */
    public record Adjustment(long previousTenths, long currentTenths, long actualDeltaTenths,
                             int quotedDeltaTenths) {}

    private ModeRatingRules() {}

    public static boolean supported(RuleSetPreset preset) {
        return preset == RuleSetPreset.MAHJONG_SOUL_4P || preset == RuleSetPreset.MAHJONG_SOUL_SANMA_3P
                || preset == RuleSetPreset.JPML_4P || preset == RuleSetPreset.M_LEAGUE_4P;
    }

    /** Eligibility is derived from the actual saved match, rather than mutable lobby settings. */
    public static boolean eligible(RuleSetPreset preset, TheMahjongMatch match) {
        if (preset == null || match == null || !supported(preset)
                || match.playerCount() != preset.playerCount()) return false;
        var rules = match.ruleSet();
        var profile = switch (preset) {
            case JPML_4P -> CompetitiveRules.Profile.JPML_A;
            case M_LEAGUE_4P -> CompetitiveRules.Profile.M_LEAGUE;
            default -> CompetitiveRules.Profile.STANDARD;
        };
        return rules.competitiveProfile() == profile
                && !rules.sichuan() && !rules.exchangeThree() && !rules.jokersEnabled() && !rules.bloodBattle();
    }

    /** Returns no results until the whole match has finished. Individual hands never change the rating. */
    public static List<Result> calculate(RuleSetPreset preset, TheMahjongMatch match) {
        if (!eligible(preset, match) || match.state() != TheMahjongMatch.State.ENDED) return List.of();
        var finalMatch = match.applyFinalDeposits();
        if (preset == RuleSetPreset.JPML_4P || preset == RuleSetPreset.M_LEAGUE_4P) {
            return CompetitiveStandings.forMatch(finalMatch).stream()
                    .map(result -> new Result(result.seat(), result.rank(), result.totalTenths())).toList();
        }
        var scores = finalMatch.currentRound().orElseThrow().players().stream().map(player -> player.points()).toList();
        return mahjongSoul(scores);
    }

    /** Mahjong Soul's final evaluation: return points, uma, and the winner's oka. Ties use starting seats. */
    public static List<Result> mahjongSoul(List<Integer> scores) {
        int count = scores.size();
        if (count != 3 && count != 4) throw new IllegalArgumentException("Mahjong Soul requires three or four scores");
        int start = count == 3 ? 35000 : 25000;
        int target = count == 3 ? 40000 : 30000;
        int[] umaTenths = count == 3 ? new int[]{150, 0, -150} : new int[]{150, 50, -50, -150};
        int okaTenths = count * (target - start) / 100;
        var order = IntStream.range(0, count).boxed().sorted(Comparator.<Integer>comparingInt(scores::get)
                .reversed().thenComparingInt(Integer::intValue)).toList();
        var results = new ArrayList<Result>();
        for (int index = 0; index < count; index++) {
            int seat = order.get(index);
            int base = Math.toIntExact(Math.round((scores.get(seat) - (long) target) / 100.0));
            results.add(new Result(seat, index + 1, base + umaTenths[index] + (index == 0 ? okaTenths : 0)));
        }
        return List.copyOf(results);
    }

    public static Adjustment apply(long currentTenths, int deltaTenths) {
        if (currentTenths < 0) throw new IllegalArgumentException("current rating cannot be negative");
        long next = deltaTenths < 0 && currentTenths < -(long) deltaTenths
                ? 0 : Math.addExact(currentTenths, deltaTenths);
        return new Adjustment(currentTenths, next, next - currentTenths, deltaTenths);
    }
}
