package com.riichimahjong.fabric;

import com.google.gson.JsonArray;
import com.riichimahjong.ai.AiRuntime;
import com.riichimahjong.ai.AiSnapshot;
import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.ai.MortalRuntime;
import com.riichimahjong.ai.KobalabBot;
import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.PlayerAction;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.riichimahjong.fabric.RulesRegression.check;

public final class MortalRuntimeRegression {
    private static void voluntaryWins() {
        var original = AiControllerRegression.request();
        for (var win : List.of(new PlayerAction.DeclareTsumo(null), new PlayerAction.DeclareRon(null),
                new PlayerAction.DeclareChankan(null))) {
            PlayerAction alternative = win instanceof PlayerAction.DeclareTsumo
                    ? new PlayerAction.Discard(original.round().players().getFirst().currentHand().getFirst()) : PlayerAction.Pass.INSTANCE;
            var request = new DecisionRequest(0, original.phase(), List.of(win, alternative), original.round(), original.rules());
            try (var mortal = new KobalabBot(BotDifficulty.NORMAL, true, () -> true,
                    snapshot -> CompletableFuture.completedFuture(1));
                 var kobalab = new KobalabBot(BotDifficulty.NORMAL)) {
                check(kobalab.chooseAction(request, 2).orElseThrow().equals(win), "Kobalab did not accept offered win");
                var selected = mortal.chooseAction(request, 2);
                if (selected.isEmpty()) selected = mortal.chooseAction(request, 2);
                check(selected.orElseThrow().equals(alternative), "Mortal was denied its choice for " + win);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        voluntaryWins();
        var supported = Set.of(RuleSetPreset.MAHJONG_SOUL_4P, RuleSetPreset.TENHOU_4P, RuleSetPreset.M_LEAGUE_4P);
        for (var preset : RuleSetPreset.values()) for (var length : MatchLength.values()) {
            check(preset.supportsMortal(preset.playerCount(), length, false, false, false) == supported.contains(preset),
                    "Mortal routing for " + preset + " " + length);
            check(!preset.supportsMortal(3, length, false, false, false), "Mortal on a three-seat table");
            check(!preset.supportsMortal(4, length, true, false, false), "Mortal used with exchange");
            check(!preset.supportsMortal(4, length, false, true, false), "Mortal used with joker");
            check(!preset.supportsMortal(4, length, false, false, true), "Mortal used with blood battle");
        }
        System.setProperty("riichi.mortal.worker", Path.of("missing-mortal-regression").toAbsolutePath().toString());
        System.setProperty("riichi.ai.timeoutMillis", "5000");
        try {
            var request = AiControllerRegression.request();
            var snapshot = AiSnapshot.create(request, BotDifficulty.HARD);
            var events = new JsonArray();
            for (var length : MatchLength.values()) {
                int index = MortalRuntime.choose(snapshot, events.deepCopy(), length).get(10, TimeUnit.SECONDS);
                check(index >= 0 && index < request.legalActions().size(), "fallback produced an invalid action");
                events.add(new com.google.gson.JsonObject());
            }
            var metrics = AiRuntime.mortalMetrics();
            check(metrics.get("engineSuccess") == 0 && metrics.get("fallback") == 2 && metrics.get("missingHistory") == 1,
                    "missing journal and missing model accounting " + metrics);
            check(AiRuntime.metrics().get("engineHard") == 2, "fallback did not use configured Kobalab difficulty");
        } finally { AiRuntime.shutdown(); }
        System.out.println("PASS: six Mortal table modes, exclusion of variants, journal and process fallback to configured AI");
    }
}
