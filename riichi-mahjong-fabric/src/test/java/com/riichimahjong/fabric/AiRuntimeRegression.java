package com.riichimahjong.fabric;

import com.riichimahjong.ai.*;
import com.themahjong.driver.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Separate JVM scenarios ensure worker failures cannot be hidden by an already-running process. */
public final class AiRuntimeRegression {
    private static Path script(String source) throws Exception {
        Path directory = Files.createTempDirectory("riichi-ai-regression-");
        Path file = directory.resolve("worker.cjs");
        Files.writeString(file,source,StandardCharsets.UTF_8);
        return file;
    }

    private static void scenario(String mode) throws Exception {
        var request = AiControllerRegression.request();
        if (mode.equals("missing")) System.setProperty("riichi.ai.node",Path.of("missing-riichi-node-regression.exe").toAbsolutePath().toString());
        else if (mode.equals("timeout")) {
            System.setProperty("riichi.ai.timeoutMillis","100");
            System.setProperty("riichi.ai.worker",script("require('node:readline').createInterface({input:process.stdin}).on('line',()=>{});setInterval(()=>{},1000);\n").toString());
        } else if (mode.equals("invalid") || mode.equals("identity") || mode.equals("fractional") || mode.equals("malformed")) {
            String response = switch(mode) {
                case "invalid" -> "JSON.stringify({id:s.id,index:999999})";
                case "identity" -> "JSON.stringify({id:s.id+1,index:0})";
                case "fractional" -> "JSON.stringify({id:s.id,index:0.5})";
                default -> "'not json'";
            };
            System.setProperty("riichi.ai.worker",script("require('node:readline').createInterface({input:process.stdin}).on('line',line=>{const s=JSON.parse(line);console.log("+response+");});\n").toString());
        } else if (mode.equals("stale")) {
            System.setProperty("riichi.ai.timeoutMillis","3000");
            System.setProperty("riichi.ai.worker",script("require('node:readline').createInterface({input:process.stdin}).on('line',line=>{const s=JSON.parse(line);setTimeout(()=>console.log(JSON.stringify({id:s.id,index:0})),200);});\n").toString());
            var bot = new KobalabBot(BotDifficulty.HARD);
            check(bot.chooseAction(request,1).isEmpty(),"AI decision blocked the first polling call");
            var changed = new DecisionRequest(0,request.phase(),List.of(new PlayerAction.Discard(tile(4,2)),new PlayerAction.Discard(tile(0,1))),request.round(),request.rules());
            check(bot.chooseAction(changed,1).isEmpty(),"new decision reused the previous future immediately");
            Optional<PlayerAction> answer = Optional.empty();
            long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(answer.isEmpty()&&System.nanoTime()<deadline){Thread.sleep(10);answer=bot.chooseAction(changed,.05);}
            check(answer.orElseThrow().equals(new PlayerAction.Discard(tile(4,2))),"stale response was mapped to the new legal list");
            bot.close();
            check(bot.chooseAction(request,10).isEmpty(),"closed controller acted on an old response");
            check(AiRuntime.metrics().get("engineSuccess")==1,"cancelled request counted as an engine decision");
            return;
        }

        if (mode.equals("valid")) {
            System.setProperty("riichi.ai.timeoutMillis","5000");
            Path extractionRoot = Files.createTempDirectory("riichi-ai-extraction-");
            System.setProperty("user.dir",extractionRoot.toString());
            check(AiRuntime.class.getResource("/ai/runtime-files.txt")!=null,"packaged AI manifest missing from compiled resources");
            for (var level : BotDifficulty.values()) {
                var snapshot = AiSnapshot.create(request,level);
                int index = AiRuntime.choose(snapshot).get(8,TimeUnit.SECONDS);
                check(index>=0&&index<request.legalActions().size(),"real engine returned an illegal index");
            }
            var metrics = AiRuntime.metrics();
            check(metrics.get("engineSuccess")==3&&metrics.get("fallback")==0,"real AI decisions silently used fallback: "+metrics);
            check(metrics.get("engineEasy")==1&&metrics.get("engineNormal")==1&&metrics.get("engineHard")==1,"one difficulty did not execute its actual strategy");
            try(var extracted=Files.walk(extractionRoot)){check(extracted.anyMatch(p->p.getFileName().toString().equals("worker.cjs")),"AI worker did not use bundled runtime extraction");}
        } else {
            if (!mode.equals("timeout")) System.setProperty("riichi.ai.timeoutMillis","5000");
            int index = AiRuntime.choose(AiSnapshot.create(request,BotDifficulty.NORMAL)).get(8,TimeUnit.SECONDS);
            check(index>=0&&index<request.legalActions().size(),"failed AI did not return a legal fallback");
            check(AiRuntime.metrics().get("fallback")==1&&AiRuntime.metrics().get("engineSuccess")==0,"worker failure counted as AI success");
            check(AiRuntime.metrics().get("timeout")== (mode.equals("timeout")?1:0),"timeout accounting wrong: "+AiRuntime.metrics());
        }
    }

    public static void main(String[] args) throws Exception {
        if(args.length>0){try{scenario(args[0]);System.out.println("PASS AI runtime scenario "+args[0]);}finally{AiRuntime.shutdown();}return;}
        String java = Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java").toString();
        for(String mode:List.of("valid","missing","invalid","identity","fractional","malformed","timeout","stale")){
            Process process = new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),AiRuntimeRegression.class.getName(),mode).inheritIO().start();
            if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("AI runtime test timed out: "+mode);}
            check(process.exitValue()==0,"AI runtime scenario failed: "+mode);
        }
        System.out.println("PASS: real three-level Node bridge, missing runtime, malformed/id/index rejection, deadline and stale response cancellation");
    }
}
