package com.riichimahjong.fabric;

import com.riichimahjong.ai.AiRuntime;
import com.riichimahjong.ai.AiSnapshot;
import com.riichimahjong.replay.ReplayAnalysis;
import com.riichimahjong.replay.ReplayRecord;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.driver.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Actual engine analysis, private-information boundary, replay grading, sanma and failed-worker behavior. */
public final class ReplayAnalysisRegression {
    private static ReplayRecord.Frame frame(TheMahjongDriver driver){
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);DriverNbt.writeDriver(out,driver);
        return new ReplayRecord.Frame(0,"test",out.buildResult());
    }
    private static TheMahjongDriver rig(int count){
        List<TheMahjongPlayer> players=new ArrayList<>();
        for(int seat=0;seat<count;seat++){
            var hand=new ArrayList<>(count==3?List.of(tile(1,1),tile(1,2),tile(1,3),tile(1,4),tile(1,5),tile(1,6),tile(2,1),tile(2,2),tile(2,3),tile(2,4),tile(2,5),tile(2,6),tile(4,1)):waiting(0));
            if(seat==0)hand.add(tile(4,2));players.add(player(hand));
        }
        var indicators=Collections.nCopies(5,tile(4,3));var wall=Collections.nCopies(count==3?20:40,tile(4,2));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,TheMahjongRound.State.TURN,0,-1,
                TheMahjongRound.ActiveTile.drawn(tile(4,2)),wall,Collections.nCopies(TheMahjongRound.rinshanTileCountFor(count),tile(4,3)),indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(count,25000,30000,2,TheMahjongMatch.State.IN_ROUND,count==3?TheMahjongTileSet.standardSanma(false):TheMahjongTileSet.standardRiichi(false),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        return new TheMahjongDriver(match,java.util.stream.IntStream.range(0,count).mapToObj(i->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),new Random(1),new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
    }
    private static ReplayRecord record(TheMahjongDriver driver){
        var before=frame(driver);act(driver,0,new PlayerAction.Discard(tile(4,2)));var after=frame(driver);
        return new ReplayRecord(UUID.randomUUID(),1,2,"MAHJONG_SOUL_4P",List.of(new ReplayRecord.Participant(0,UUID.randomUUID(),"Test",25000)),List.of(before,after));
    }
    private static Path worker(String script)throws Exception{var path=Files.createTempDirectory("riichi-replay-worker-").resolve("worker.cjs");Files.writeString(path,script);return path;}
    private static void scenario(String mode)throws Exception{
        if(mode.equals("missing"))System.setProperty("riichi.ai.node","missing-riichi-analysis-node");
        if(mode.equals("timeout")){System.setProperty("riichi.replay.analysisTimeoutMillis","100");System.setProperty("riichi.ai.worker",worker("require('node:readline').createInterface({input:process.stdin}).on('line',()=>{});setInterval(()=>{},1000);").toString());}
        if(mode.equals("identity"))System.setProperty("riichi.ai.worker",worker("require('node:readline').createInterface({input:process.stdin}).on('line',line=>console.log(JSON.stringify({id:JSON.parse(line).id+1,candidates:[],scores:[]})));").toString());
        var driver=rig(4);var record=record(driver);
        long started=System.nanoTime();long live=AIRuntimeMetrics();
        var pending=ReplayAnalysis.request(record,0,driver,null);
        check(System.nanoTime()-started<TimeUnit.SECONDS.toNanos(1),"replay request blocked its caller");
        var result=pending.get(15,TimeUnit.SECONDS);
        check(AIRuntimeMetrics()==live,"replay analysis occupied live BOT runtime");
        if(!mode.equals("valid")){check(!result.available()&&result.candidates().isEmpty()&&result.chosenScore()==-1,"failed worker fabricated a decision quality score");return;}
        check(result.available(),"real replay analysis failed: "+result.error());
        check(result.candidates().size()>0&&result.candidates().size()<=3,"missing top-three choices");
        check(result.candidates().getFirst().tile().equals("z6")&&result.candidates().getFirst().score()==99,"AI recommendation lost its own relative maximum");
        check(result.chosenScore()==99,"actual recorded discard was not graded from its original window");
        check(result.roundScores().get(0).get(0)==99,"per-round mean is missing");
        check(ReplayAnalysis.cached(record.id(),0).orElseThrow().chosenScore()==99,"frame result not cached");
        check(ReplayAnalysis.request(record,0,driver,null).get(2,TimeUnit.SECONDS).equals(result),"same frame analysis is not stable");
        var json=ReplayAnalysis.exportResults(record.id());ReplayAnalysis.importResults(record.id(),json);
        check(ReplayAnalysis.roundScores(record.id()).get(0).get(0)==99,"cache import changed the aggregate");
        try{ReplayAnalysis.importResults(UUID.randomUUID(),json);throw new AssertionError("foreign analysis cache accepted");}catch(IllegalArgumentException expected){}
        var three=rig(3);var threeRecord=record(three);
        var sanma=ReplayAnalysis.request(threeRecord,0,three,null).get(15,TimeUnit.SECONDS);
        check(sanma.available()&&sanma.engine().contains("sanma"),"three-player analysis did not use existing Java efficiency evaluator");
        check(sanma.candidates().size()>0&&sanma.candidates().size()<=3&&sanma.chosenScore()>=0,"sanma scoring missing");
        for(var candidate:sanma.candidates())check(candidate.score()>0&&candidate.score()<=99,"sanma candidate range wrong");
        check(ReplayAnalysis.parseTile("p0").redDora()&&ReplayAnalysis.parseTile("z7").rank()==3,"analysis tile decoding lost red/honor identity");
    }
    private static long AIRuntimeMetrics(){return AiRuntime.metrics().get("submitted");}
    public static void main(String[] args)throws Exception{
        if(args.length>0){try{scenario(args[0]);System.out.println("PASS replay analysis "+args[0]);}finally{ReplayAnalysis.shutdown();AiRuntime.shutdown();}return;}
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java").toString();
        for(String mode:List.of("valid","missing","timeout","identity")){
            var process=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),ReplayAnalysisRegression.class.getName(),mode).inheritIO().start();
            if(!process.waitFor(35,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Replay analysis scenario timed out: "+mode);}check(process.exitValue()==0,"Replay analysis scenario failed: "+mode);
        }
        System.out.println("PASS: actual four-player AI analysis, separate worker, frame grading/cache, sanma, timeout and response identity validation");
    }
}
