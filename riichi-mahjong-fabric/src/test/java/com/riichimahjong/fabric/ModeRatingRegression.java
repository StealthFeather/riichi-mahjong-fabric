package com.riichimahjong.fabric;

import com.riichimahjong.analysis.ModeRatingRules;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.themahjong.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Independent published scoring examples and the eligibility/zero-floor boundaries. */
public final class ModeRatingRegression {
    private static TheMahjongMatch ended(RuleSetPreset preset, List<Integer> scores, int deposits) {
        var base = preset.newMatch();
        var players = new ArrayList<TheMahjongPlayer>();
        for (int seat = 0; seat < scores.size(); seat++)
            players.add(new TheMahjongPlayer(scores.get(seat), TheMahjongPlayer.RiichiState.NONE, false,
                    waiting(seat % 3), List.of(), List.of(), List.of()));
        var indicators = Collections.nCopies(5, tile(4, 1));
        var round = new TheMahjongRound(TheMahjongTile.Wind.SOUTH, scores.size(), 0, deposits, scores.size() - 1,
                TheMahjongRound.State.ENDED, 0, -1, TheMahjongRound.ActiveTile.none(), List.of(),
                Collections.nCopies(4, tile(4, 1)), indicators, indicators, 1, players, List.of());
        return new TheMahjongMatch(scores.size(), base.startingPoints(), base.targetPoints(), base.roundCount(),
                TheMahjongMatch.State.ENDED, base.tileSet(), base.ruleSet(), List.of(round), round);
    }

    private static void expect(RuleSetPreset preset, List<Integer> scores, List<Integer> expected) {
        var results = ModeRatingRules.calculate(preset, ended(preset, scores, 0));
        check(results.size() == scores.size(), "missing scoring seats");
        for (var result : results)
            check(result.deltaTenths() == expected.get(result.seat()), "wrong match points: " + result);
        check(results.stream().mapToInt(ModeRatingRules.Result::deltaTenths).sum() == 0,
                "evaluation points must conserve zero before the personal floor");
    }

    public static void main(String[] args) {
        // Return 30,000; uma +15/+5/-5/-15; winner receives the 20-point oka.
        expect(RuleSetPreset.MAHJONG_SOUL_4P, List.of(40000, 30000, 20000, 10000), List.of(450, 50, -150, -350));
        // Return 40,000; uma +15/0/-15; winner receives the 15-point oka.
        expect(RuleSetPreset.MAHJONG_SOUL_SANMA_3P, List.of(50000, 35000, 20000), List.of(400, -50, -350));
        expect(RuleSetPreset.MAHJONG_SOUL_4P, List.of(25000, 25000, 25000, 25000), List.of(300, 0, -100, -200));
        expect(RuleSetPreset.MAHJONG_SOUL_SANMA_3P, List.of(35000, 35000, 35000), List.of(250, -50, -200));
        expect(RuleSetPreset.M_LEAGUE_4P, List.of(35800, 41600, 15000, 7600), List.of(158, 616, -250, -524));
        expect(RuleSetPreset.JPML_4P, List.of(50000, 29000, 25000, 16000), List.of(320, -20, -80, -220));
        expect(RuleSetPreset.JPML_4P, List.of(40000, 35000, 25000, 20000), List.of(180, 90, -90, -180));
        expect(RuleSetPreset.JPML_4P, List.of(35000, 34000, 33000, 18000), List.of(130, 70, 40, -240));

        var deposits = ended(RuleSetPreset.MAHJONG_SOUL_4P, List.of(39000, 30000, 20000, 10000), 1);
        var payout = ModeRatingRules.calculate(RuleSetPreset.MAHJONG_SOUL_4P, deposits);
        check(payout.getFirst().deltaTenths() == 450, "terminal riichi deposit omitted");
        check(payout.equals(ModeRatingRules.calculate(RuleSetPreset.MAHJONG_SOUL_4P, deposits.applyFinalDeposits())),
                "repeated evaluation paid riichi deposits twice");

        var tiedLeague = ended(RuleSetPreset.M_LEAGUE_4P, List.of(33000, 33000, 33000, 0), 1);
        var league = ModeRatingRules.calculate(RuleSetPreset.M_LEAGUE_4P, tiedLeague);
        check(league.stream().map(ModeRatingRules.Result::rank).toList().equals(List.of(1, 1, 1, 4)),
                "deposit remainder changed tied M-League ranks");
        check(league.stream().mapToInt(ModeRatingRules.Result::deltaTenths).sum() == 0,
                "tied final deposit or uma lost league points");

        check(ModeRatingRules.apply(0, -250).currentTenths() == 0, "zero rating was deducted");
        check(ModeRatingRules.apply(100, -250).currentTenths() == 0, "rating dropped below zero");
        check(ModeRatingRules.apply(100, -250).actualDeltaTenths() == -100, "displayed delta ignored zero floor");
        check(ModeRatingRules.apply(100, 158).currentTenths() == 258, "fractional match points lost precision");
        check(ModeRatingRules.apply(0, Integer.MIN_VALUE).currentTenths() == 0, "extreme deduction overflowed floor");

        for (var preset : RuleSetPreset.values()) {
            var base = preset.newMatch();
            check(ModeRatingRules.calculate(preset, base).isEmpty(), "unstarted match scored");
            if (!ModeRatingRules.supported(preset)) {
                check(!ModeRatingRules.eligible(preset, base), "unrequested mode eligible for rating");
                continue;
            }
            check(ModeRatingRules.eligible(preset, base), "ordinary supported match rejected");
            for (int flag = 0; flag < 3; flag++)
                check(!ModeRatingRules.eligible(preset,
                                preset.newMatch(MatchLength.EAST, flag == 0, flag == 1, flag == 2)),
                        "entertainment variant eligible for cumulative score");
        }
        check(!ModeRatingRules.eligible(RuleSetPreset.MAHJONG_SOUL_4P,
                        RuleSetPreset.MAHJONG_SOUL_SANMA_3P.newMatch()), "wrong player-count rating accepted");
        check(!ModeRatingRules.eligible(RuleSetPreset.MAHJONG_SOUL_4P,
                        RuleSetPreset.JPML_4P.newMatch()), "wrong saved competitive profile accepted");
        check(!ModeRatingRules.eligible(RuleSetPreset.JPML_4P,
                        RuleSetPreset.M_LEAGUE_4P.newMatch()), "different competitive profile scored as JPML");
        check(ModeRatingRules.calculate(null, null).isEmpty(), "missing match should have no rating");
        System.out.println("PASS: mode evaluation points, official uma/oka, final deposits, entertainment exclusions and zero floor");
    }
}
