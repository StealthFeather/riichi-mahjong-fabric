package com.riichimahjong.fabric;

import com.riichimahjong.analysis.DecisionConsistency;
import com.riichimahjong.analysis.LocalConsistencyPolicy;
import com.riichimahjong.ai.AiRuntime;
import com.riichimahjong.ai.AiSnapshot;
import com.riichimahjong.ai.BotDifficulty;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.PlayerAction;
import com.themahjong.variants.VariantRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Analysis failures must be absent samples, and callbacks must stay on the table thread. */
public final class DecisionConsistencyRegression {
    private static Path worker(String source) throws Exception {
        Path file = Files.createTempDirectory("riichi-consistency-").resolve("worker.cjs");
        Files.writeString(file, source);
        return file;
    }

    private static void waitFor(DecisionConsistency tracker) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (tracker.pendingCount() > 0 && System.nanoTime() < until) {
            Thread.sleep(10);
            tracker.poll();
        }
        check(tracker.pendingCount() == 0, "analysis did not reach its deadline");
    }

    private static void scenario(String mode) throws Exception {
        var request = AiControllerRegression.request();
        PlayerAction chosen = request.legalActions().getFirst();
        if (mode.equals("different")) chosen = request.legalActions().stream()
                .filter(action -> !DecisionConsistency.equivalent(action, request.legalActions().getFirst()))
                .filter(action -> action instanceof PlayerAction.Discard || action instanceof PlayerAction.DiscardWithRiichi)
                .findFirst().orElseThrow();
        if (mode.equals("missing")) System.setProperty("riichi.ai.node", "missing-riichi-analysis-node");
        else if (mode.equals("timeout")) {
            System.setProperty("riichi.ai.timeoutMillis", "100");
            System.setProperty("riichi.ai.worker", worker("require('node:readline').createInterface({input:process.stdin}).on('line',()=>{});setInterval(()=>{},1000);\n").toString());
        } else {
            System.setProperty("riichi.ai.timeoutMillis", "3000");
            String index = mode.equals("invalid") ? "999999" : "0";
            System.setProperty("riichi.ai.worker", worker("require('node:readline').createInterface({input:process.stdin}).on('line',line=>{const s=JSON.parse(line);setTimeout(()=>console.log(JSON.stringify({id:s.id,index:" + index + "})),150);});\n").toString());
        }
        var values = new ArrayList<Boolean>();
        Thread tableThread = Thread.currentThread();
        try (var tracker = new DecisionConsistency()) {
            check(tracker.record(request, chosen, false, value -> {
                check(Thread.currentThread() == tableThread, "analysis callback ran off the table thread");
                values.add(value);
            }), "eligible human discard was not sampled");
            check(values.isEmpty(), "analysis blocked until a recommendation was available");
            if (mode.equals("cancel")) {
                tracker.close(); Thread.sleep(250); tracker.poll();
                check(values.isEmpty(), "cancelled analysis delivered a stale sample");
            } else {
                waitFor(tracker);
                check(mode.equals("valid") ? values.equals(List.of(true)) :
                        mode.equals("different") ? values.equals(List.of(false)) : values.isEmpty(),
                        "failed AI was counted as a consistency sample: " + mode + "/" + values);
            }
        }
        if (!mode.equals("valid") && !mode.equals("different") && !mode.equals("cancel")) {
            int trusted = AiRuntime.analyze(AiSnapshot.create(request, BotDifficulty.HARD)).get(8, TimeUnit.SECONDS);
            check(trusted == -1, "analysis exposed fallback as a trusted recommendation");
        }
    }

    private static void equivalenceAndEligibility() {
        var normal = new PlayerAction.Discard(tile(0,5));
        var same = new PlayerAction.Discard(new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,false));
        var red = new PlayerAction.Discard(new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true));
        check(DecisionConsistency.equivalent(normal,same), "different physical normal copies were treated as different choices");
        check(!DecisionConsistency.equivalent(normal,red), "red discard was treated as a normal five");
        check(!DecisionConsistency.equivalent(normal,new PlayerAction.DiscardWithRiichi(tile(0,5))), "riichi declaration was lost");
        var original = AiControllerRegression.request();
        var forced = new DecisionRequest(0,original.phase(),List.of(normal,same),original.round(),original.rules());
        check(!DecisionConsistency.eligible(forced,normal), "duplicate copies made a forced discard count");
        var options = VariantRules.withOptions(com.themahjong.TheMahjongMatch.defaultMahjongSoul(),true,false,false);
        var exchanged = new DecisionRequest(original.seat(),original.phase(),original.legalActions(),original.round(),options.ruleSet());
        check(DecisionConsistency.eligible(exchanged,original.legalActions().getFirst()), "entertainment discard was not eligible for local analysis");
        var three = driver(List.of(player(waiting(0)),player(waiting(1)),player(waiting(2))),List.of(tile(4,1)));
        var sanma = new DecisionRequest(0,three.currentPhase(),List.of(normal,red),three.currentRound(),three.rules());
        check(DecisionConsistency.eligible(sanma,normal), "three-player discard was not eligible for local analysis");
    }

    private static void localBenchmarks() throws Exception {
        var hand=new ArrayList<>(waiting(1));hand.add(tile(4,2));
        var driver=driver(List.of(player(hand),player(waiting(2)),player(waiting(1))),java.util.Collections.nCopies(20,tile(4,2)));
        var request=new DecisionRequest(0,driver.currentPhase(),driver.legalActions(0),driver.currentRound(),
                com.themahjong.TheMahjongMatch.defaultMahjongSoulSanma().ruleSet());
        var snapshot=LocalConsistencyPolicy.snapshot(request);
        int index=LocalConsistencyPolicy.chooseIndex(snapshot);
        check(index>=0&&index<request.legalActions().size(),"sanma benchmark recommended an illegal action");
        check(request.legalActions().get(index) instanceof PlayerAction.DiscardWithRiichi d && d.tile().equals(tile(4,2)),
                "sanma shanten/ukeire benchmark did not discard the isolated dragon and riichi");
        var players=new ArrayList<>(request.round().players());
        for(int seat=1;seat<players.size();seat++)players.set(seat,player(java.util.Collections.nCopies(13,tile(4,3))));
        var round=request.round();var indicators=new ArrayList<>(round.doraIndicators());
        for(int i=round.revealedDoraCount();i<indicators.size();i++)indicators.set(i,tile(0,9));
        var hidden=AiControllerRegression.copy(round,players,java.util.Collections.nCopies(round.liveWall().size(),tile(0,1)),
                java.util.Collections.nCopies(round.rinshanTiles().size(),tile(1,1)),indicators,java.util.Collections.nCopies(round.uraDoraIndicators().size(),tile(2,1)));
        var changed=new DecisionRequest(0,request.phase(),request.legalActions(),hidden,request.rules());
        check(snapshot.equals(LocalConsistencyPolicy.snapshot(changed)),"local analysis observed concealed opponent tiles or hidden wall/indicators");
        long before=AiRuntime.metrics().get("submitted");var outcomes=new ArrayList<Boolean>();
        try(var tracker=new DecisionConsistency()) {
            check(tracker.record(request,request.legalActions().get(index),true,outcomes::add),"sanma analysis was skipped");
            waitFor(tracker);check(outcomes.equals(List.of(true)),"sanma asynchronous sample missing");
        }
        check(AiRuntime.metrics().get("submitted")==before,"sanma was sent to the incompatible four-player engine");
        for(boolean sichuan:List.of(false,true)) {
            var joker=sichuan?tile(4,3):com.themahjong.variants.Jokers.TILE;
            var jokerHand=List.of(joker,tile(0,1),tile(0,2),tile(0,3),tile(0,4),tile(0,5),tile(0,6),
                    tile(1,2),tile(1,2),tile(1,2),tile(2,3),tile(2,4),tile(2,5),tile(1,9));
            var original=AiControllerRegression.request();var originalRound=original.round();
            var variants=new ArrayList<>(originalRound.players());variants.set(0,player(jokerHand));
            var localRound=AiControllerRegression.copy(originalRound,variants,originalRound.liveWall(),originalRound.rinshanTiles(),
                    originalRound.doraIndicators(),originalRound.uraDoraIndicators());
            var legal=List.<PlayerAction>of(new PlayerAction.Discard(joker),new PlayerAction.Discard(tile(1,9)),new PlayerAction.Discard(tile(0,3)));
            var rules=sichuan?com.themahjong.sichuan.SichuanRules.newMatch(2,true,true,true).ruleSet():
                    VariantRules.withOptions(com.themahjong.TheMahjongMatch.defaultMahjongSoul(),true,true,true).ruleSet();
            var variant=new DecisionRequest(0,original.phase(),legal,localRound,rules);
            var local=LocalConsistencyPolicy.snapshot(variant);
            check(local.getAsJsonArray("hand").get(0).getAsString().equals("joker"),"mode-specific joker identity was lost");
            check(LocalConsistencyPolicy.chooseIndex(local)==1,"wildcard benchmark discarded a flexible joker or connected tile");
            outcomes.clear();
            try(var tracker=new DecisionConsistency()) {
                check(tracker.record(variant,legal.get(1),false,outcomes::add),"variant analysis was skipped");
                waitFor(tracker);check(outcomes.equals(List.of(true)),"variant asynchronous sample missing");
            }
        }
        check(!DecisionConsistency.equivalent(new PlayerAction.Discard(com.themahjong.variants.Jokers.TILE),new PlayerAction.Discard(tile(4,3))),
                "ordinary red dragon was treated as the independent riichi joker");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            try { scenario(args[0]); } finally { AiRuntime.shutdown(); }
            return;
        }
        equivalenceAndEligibility(); localBenchmarks();
        String java = Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java").toString();
        for (String mode : List.of("valid","different","missing","invalid","timeout","cancel")) {
            var process = new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),DecisionConsistencyRegression.class.getName(),mode).inheritIO().start();
            if (!process.waitFor(20,TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("analysis scenario timed out: " + mode); }
            check(process.exitValue() == 0, "analysis scenario failed: " + mode);
        }
        System.out.println("PASS: trusted asynchronous consistency, absent failed samples, forced exclusion and mode-compatible analysis, callback thread and cancellation");
    }
}
