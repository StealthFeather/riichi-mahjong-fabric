package com.riichimahjong.fabric;

import com.google.gson.JsonObject;
import com.riichimahjong.ai.MortalRuntime;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.replay.*;
import com.themahjong.TheMahjongRound;
import com.themahjong.driver.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.riichimahjong.fabric.RulesRegression.check;

/** Exercise replay routing, historical perspective, raw weights, and recovery when the model is unavailable. */
public final class MortalReplayRegression {
    private static final List<MahjongPlayerInterface> PAUSED = Collections.nCopies(4,(r,dt)->Optional.empty());

    private static ReplayRecord record(RuleSetPreset preset,MatchLength length,boolean exchange,boolean legacy) {
        for (int seed = 0; seed < 60; seed++) {
            var driver=new TheMahjongDriver(preset.newMatch(length,exchange,false),PAUSED,new Random(seed));
            var recorder=new ReplayRecorder(preset.name(),List.of());
            driver.setStateObserver(recorder::capture);
            driver.startMatch();
            if (exchange) return ReplayRecord.fromNbt(recorder.toNbt());
            driver.submitAction(0,PlayerAction.Draw.INSTANCE);
            driver.advance(.05);
            var tile=((TheMahjongRound.ActiveTile.Drawn)driver.currentRound().activeTile()).tile();
            if(Collections.frequency(driver.currentRound().players().getFirst().currentHand(),tile)<2) continue;
            recorder.markHumanDiscard(0,false);
            driver.submitAction(0,new PlayerAction.Discard(tile));
            driver.advance(.05);
            while(driver.currentPhase() instanceof MatchPhase.AwaitingClaims claims){
                for(int seat:claims.pendingSeats())driver.submitAction(seat,PlayerAction.Pass.INSTANCE);
                driver.advance(.05);
            }
            int next=((MatchPhase.AwaitingDraw)driver.currentPhase()).seat();
            driver.submitAction(next,PlayerAction.Draw.INSTANCE);
            driver.advance(.05);
            var discard=driver.legalActions(next).stream().filter(PlayerAction.Discard.class::isInstance).findFirst().orElseThrow();
            driver.submitAction(next,discard);
            driver.advance(.05);
            var frames=ReplayRecord.fromNbt(recorder.toNbt());
            return legacy?new ReplayRecord(frames.id(),0,0,preset.name(),List.of(),frames.frames().stream()
                    .map(f->new ReplayRecord.Frame(f.roundIndex(),f.label(),f.driver())).toList()):frames;
        }
        throw new AssertionError("No seed with matching draw");
    }

    private static void run() throws Exception {
        for(var preset:RuleSetPreset.values()){
            if(preset.playerCount()!=4 || preset.family()==RuleSetPreset.Family.SICHUAN)continue;
            for(var length:MatchLength.values()){
                var record=record(preset,length,false,false);
                check(ReplayAnalysis.usesMortal(record)==Set.of(RuleSetPreset.MAHJONG_SOUL_4P,RuleSetPreset.TENHOU_4P,
                        RuleSetPreset.M_LEAGUE_4P).contains(preset),"Mortal format gate "+preset+" "+length);
            }
        }
        check(!ReplayAnalysis.usesMortal(record(RuleSetPreset.TENHOU_4P,MatchLength.EAST,true,false)),"variant score route");
        var record=record(RuleSetPreset.TENHOU_4P,MatchLength.EAST,false,true);
        int seat=1;
        var timeline=new ReplayTimeline(record);
        int decision=timeline.turnFrames(seat).getFirst();
        var actual=ReplayAnalysis.tileKind(timeline.discardForDecision(decision,seat).orElseThrow().tile());
        long liveBefore=MortalRuntime.metrics().get("submitted");
        var work=Files.createTempDirectory(Path.of("build").toAbsolutePath(), "mortal-replay-policy-");
        Path worker=work.resolve("worker");
        System.setProperty("riichi.mortal.worker",worker.toString());
        var missing=ReplayAnalysis.request(record,decision,null,null).get(15,TimeUnit.SECONDS);
        check(!missing.available()&&!missing.excluded()&&ReplayAnalysis.matchScores(record.id()).isEmpty(),
                "failed Mortal request generated a grade or permanently excluded a frame");
        // Deliberately use two almost equal raw weights, which display as 50 and 50.
        Files.writeString(worker,"""
                #!/usr/bin/env python3
                import sys,json
                for line in sys.stdin:
                    r=json.loads(line)
                    assert r["mode"]=="analysis" and r["length"]=="EAST"
                    assert set(r)<= {"id","mode","length","rules","events","legal","seat"}
                    view=r["seat"]
                    for event in r["events"]:
                        if event["type"]=="start_kyoku":
                            assert all(all(t=="?" for t in h) for seat,h in enumerate(event["tehais"]) if seat!=view)
                        if event["type"]=="tsumo" and event["actor"]!=view: assert event["pai"]=="?"
                    from pathlib import Path
                    discard=next(e for e in r["events"] if e["type"]=="dahai" and e["actor"]==0)
                    assert discard["tsumogiri"] == (not Path("modern").exists())
                    legal=r["legal"]
                    kinds=sorted(set(a["tile"].replace("0","5") for a in legal if a["type"].startswith("Discard")))
                    actual="ACTUAL"
                    other=next(k for k in kinds if k!=actual)
                    p={k: (0.499 if k==actual else 0.501 if k==other else 0) for k in kinds}
                    actions=[{"index":a["index"],"probability":1/len(legal)} for a in legal]
                    print(json.dumps({"id":r["id"],"engine":"Mortal","weights":p,"actions":actions}),flush=True)
                """.replace("ACTUAL",actual));
        check(worker.toFile().setExecutable(true),"make test worker executable");
        var scored=ReplayAnalysis.request(record,decision,null,null).get(15,TimeUnit.SECONDS);
        check(scored.available()&&scored.engine().startsWith("Mortal"),"missing worker recovery or old equal-copy replay: "+scored.error());
        var chosen=scored.candidates().stream().filter(c->c.tile().equals(actual)).findFirst().orElseThrow();
        check(chosen.probability()==50&&chosen.policyProbability()==.499,"preserve unrounded Mortal weight");
        check(scored.candidates().getFirst().probability()==50&&scored.chosenRank()==2,"rank unrounded choices");
        var json=ReplayAnalysis.exportResults(record.id());
        double grade=json.getAsJsonObject("actualChoices").entrySet().iterator().next().getValue().getAsDouble();
        check(Math.abs(grade-100*.499/.501)<1e-9,"use original probabilities for scoring");
        check(MortalRuntime.metrics().get("submitted")==liveBefore,"replay policy ran on live queue");

        Files.createFile(work.resolve("modern"));
        var current=record(RuleSetPreset.TENHOU_4P,MatchLength.EAST,false,false);
        var modern=ReplayAnalysis.request(current,decision,null,null).get(15,TimeUnit.SECONDS);
        check(modern.available() && modern.candidates().equals(scored.candidates()), "new archive ignored the explicit hand-discard flag");
        var v3=ReplayAnalysis.exportResults(record.id());v3.addProperty("version",3);v3.addProperty("policy","modeled-kind-softmax-v3");
        try { ReplayAnalysis.importResults(record.id(),v3); throw new AssertionError("reused v3 heuristic grades"); }
        catch(IllegalArgumentException expected) {}
        var partial=new ReplayRecord(UUID.randomUUID(),0,0,record.preset(),List.of(),record.frames().subList(decision,decision+2));
        var skipped=ReplayAnalysis.request(partial,0,null,null).get(15,TimeUnit.SECONDS);
        check(skipped.excluded() && !skipped.available() && ReplayAnalysis.matchScores(partial.id()).isEmpty(),"incomplete history got a grade");
        var cached=ReplayAnalysis.exportResults(partial.id());
        check(cached.getAsJsonArray("frames").get(0).getAsJsonObject().get("excluded").getAsBoolean(),"persist timeline gap");
        ReplayAnalysis.importResults(partial.id(),cached);
        System.out.println("PASS Mortal replay format routing, retry, old equal-copy history, raw versus displayed weighting and excluded-frame cache");
    }
    public static void main(String[] args)throws Exception {
        try { run(); } finally { ReplayAnalysis.shutdown(); }
    }
}
