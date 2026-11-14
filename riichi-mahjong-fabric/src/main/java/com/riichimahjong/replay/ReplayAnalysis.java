package com.riichimahjong.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riichimahjong.ai.AiRuntime;
import com.riichimahjong.ai.AiSnapshot;
import com.riichimahjong.ai.FallbackPolicy;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import com.themahjong.driver.TheMahjongDriver;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/** Replay-only analysis. Its bounded queue and Node process never consume live BOT workers. */
public final class ReplayAnalysis {
    public record Candidate(String tile,int score,double value,double risk) {
        public Candidate { if(score<0||score>99||!Double.isFinite(value)||!Double.isFinite(risk))throw new IllegalArgumentException("Invalid analysis score"); }
        public TheMahjongTile mahjongTile() { return parseTile(tile); }
    }
    public record Result(int frame,int seat,List<Candidate> candidates,int chosenScore,
                         Map<Integer,Map<Integer,Integer>> roundScores,String engine,String error) {
        public Result { candidates=List.copyOf(candidates);roundScores=Map.copyOf(roundScores); }
        public boolean available(){return error.isEmpty();}
    }
    private record Evaluated(Result result,List<Candidate> all,int round){}
    private record Entry(CompletableFuture<Evaluated> future,long created){}
    private static final class RecordCache {
        final Map<Integer,Entry> frames=new ConcurrentHashMap<>();
        final Map<String,Integer> actualChoices=new ConcurrentHashMap<>();
        volatile Map<Integer,Map<Integer,Integer>> summary=Map.of();
    }
    private static final Map<UUID,RecordCache> CACHES=Collections.synchronizedMap(new LinkedHashMap<>(8,.75f,true));
    private static final Set<UUID> BATCHES=ConcurrentHashMap.newKeySet(),COMPLETED=ConcurrentHashMap.newKeySet();
    private static final LinkedBlockingDeque<Runnable> QUEUE=new LinkedBlockingDeque<>(64);
    private static final ThreadPoolExecutor EXECUTOR=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,QUEUE,r->{var t=new Thread(r,"riichi-replay-analysis");t.setDaemon(true);return t;});
    private static final ScheduledThreadPoolExecutor WATCHDOG=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"riichi-replay-analysis-timeout");t.setDaemon(true);return t;});
    private static final AtomicLong IDS=new AtomicLong();
    private static final Worker WORKER=new Worker();
    private static volatile boolean closed;
    static { WATCHDOG.setRemoveOnCancelPolicy(true); }
    private ReplayAnalysis(){}

    /** Callback runs off-thread; callers must use server.execute before changing a table or player menu. */
    public static CompletableFuture<Result> request(ReplayRecord record,int frameIndex,TheMahjongDriver driver,Consumer<Result> callback){
        var future=requestInternal(record,frameIndex,true).thenApply(value->withSummary(record.id(),value.result()));
        if(callback!=null)future.thenAccept(callback);
        return future;
    }
    public static Optional<Result> cached(UUID id,int frame){
        RecordCache current=findCache(id);if(current==null)return Optional.empty();Entry entry=current.frames.get(frame);
        if(entry==null||!entry.future().isDone()||entry.future().isCompletedExceptionally())return Optional.empty();
        return Optional.of(withSummary(id,entry.future().getNow(null).result()));
    }
    public static Map<Integer,Map<Integer,Integer>> roundScores(UUID id){RecordCache current=findCache(id);return current==null?Map.of():current.summary;}
    /** Rebuilt only on background score changes; rendering reads the immutable summary in O(1). */
    private static void updateSummary(RecordCache cache){
        Map<Integer,Map<Integer,long[]>> totals=new LinkedHashMap<>();
        cache.actualChoices.forEach((key,score)->{
            String[] parts=key.split(":");int round=Integer.parseInt(parts[0]),seat=Integer.parseInt(parts[1]);
            long[] accumulator=totals.computeIfAbsent(round,n->new LinkedHashMap<>()).computeIfAbsent(seat,n->new long[2]);accumulator[0]+=score;accumulator[1]++;
        });
        Map<Integer,Map<Integer,Integer>> result=new LinkedHashMap<>();
        totals.forEach((round,seats)->{Map<Integer,Integer> scores=new LinkedHashMap<>();seats.forEach((seat,sum)->scores.put(seat,(int)Math.round((double)sum[0]/sum[1])));result.put(round,Map.copyOf(scores));});
        cache.summary=Map.copyOf(result);
    }
    /** One frame is enqueued at a time, allowing interactive requests to jump ahead of background work. */
    public static void analyzeAll(ReplayRecord record,Consumer<Result> callback){
        if(closed||COMPLETED.contains(record.id())||!BATCHES.add(record.id()))return;
        UUID viewer=record.participants().stream().map(ReplayRecord.Participant::uuid).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if(viewer==null){scheduleNext(record,0,callback);return;}
        ReplayStore.loadAnalysis(viewer,record.id()).whenComplete((json,error)->{
            if(json!=null)try{importResults(record.id(),json);}catch(RuntimeException ignored){}
            if(closed){BATCHES.remove(record.id());return;}
            if(COMPLETED.contains(record.id())){BATCHES.remove(record.id());return;}
            scheduleNext(record,0,callback);
        });
    }
    public static int analyzedFrames(UUID id){RecordCache current=findCache(id);return current==null?0:(int)current.frames.values().stream().filter(e->e.future().isDone()&&!e.future().isCompletedExceptionally()&&e.future().getNow(null).result().available()).count();}
    public static void shutdown(){
        closed=true;BATCHES.clear();COMPLETED.clear();WORKER.kill();QUEUE.clear();EXECUTOR.shutdownNow();WATCHDOG.shutdownNow();
        synchronized(CACHES){CACHES.values().forEach(c->c.frames.values().forEach(e->e.future().cancel(false)));CACHES.clear();}
    }
    /** Versioned numeric cache; full hands and hidden information are not duplicated here. */
    public static JsonObject exportResults(UUID id){
        var json=new JsonObject();json.addProperty("version",1);json.addProperty("record",id.toString());json.addProperty("complete",COMPLETED.contains(id));
        var frames=new JsonArray();RecordCache cache=cache(id);
        cache.frames.forEach((index,entry)->{
            if(!entry.future().isDone()||entry.future().isCompletedExceptionally()||entry.future().isCancelled())return;
            Evaluated value=entry.future().getNow(null);if(!value.result().available())return;
            var frame=new JsonObject();Result result=value.result();frame.addProperty("frame",index);frame.addProperty("round",value.round());frame.addProperty("seat",result.seat());
            frame.addProperty("chosenScore",result.chosenScore());frame.addProperty("engine",result.engine());frame.add("candidates",candidatesJson(result.candidates()));frame.add("scores",candidatesJson(value.all()));frames.add(frame);
        });
        json.add("frames",frames);var choices=new JsonObject();cache.actualChoices.forEach(choices::addProperty);json.add("actualChoices",choices);return json;
    }
    public static void importResults(UUID id,JsonObject json){
        if(json==null||!json.has("version")||json.get("version").getAsInt()!=1||!id.toString().equals(json.get("record").getAsString()))throw new IllegalArgumentException("Unsupported analysis cache");
        JsonArray frames=json.getAsJsonArray("frames");if(frames==null||frames.size()>100_000)throw new IllegalArgumentException("Invalid analysis cache");
        Map<Integer,Entry> imported=new LinkedHashMap<>();Map<String,Integer> actual=new LinkedHashMap<>();
        for(var element:frames){var frame=element.getAsJsonObject();int index=frame.get("frame").getAsInt(),round=frame.get("round").getAsInt(),seat=frame.get("seat").getAsInt(),chosen=frame.get("chosenScore").getAsInt();
            if(index<0||round<0||seat< -1||seat>3||chosen< -1||chosen>99)throw new IllegalArgumentException("Invalid cached analysis frame");
            List<Candidate> candidates=cachedCandidates(frame.getAsJsonArray("candidates")),all=cachedCandidates(frame.getAsJsonArray("scores"));
            if(candidates.size()>3)throw new IllegalArgumentException("Invalid cached recommendation count");
            var result=new Result(index,seat,candidates,chosen,Map.of(),frame.get("engine").getAsString(),"");
            imported.put(index,new Entry(CompletableFuture.completedFuture(new Evaluated(result,all,round)),System.nanoTime()));
        }
        var choices=json.getAsJsonObject("actualChoices");if(choices!=null)choices.entrySet().forEach(entry->{int score=entry.getValue().getAsInt();if(!entry.getKey().matches("[0-9]+:[0-3]:[0-9]+")||score<0||score>99)throw new IllegalArgumentException("Invalid cached actual score");actual.put(entry.getKey(),score);});
        RecordCache cache=cache(id);imported.forEach(cache.frames::putIfAbsent);cache.actualChoices.putAll(actual);updateSummary(cache);
        if(json.has("complete")&&json.get("complete").getAsBoolean())COMPLETED.add(id);
    }
    private static JsonArray candidatesJson(List<Candidate> candidates){var array=new JsonArray();for(var candidate:candidates){var value=new JsonObject();value.addProperty("tile",candidate.tile());value.addProperty("score",candidate.score());value.addProperty("value",candidate.value());value.addProperty("risk",candidate.risk());array.add(value);}return array;}
    private static List<Candidate> cachedCandidates(JsonArray array){
        if(array==null||array.size()>14)throw new IllegalArgumentException("Invalid cached candidates");List<Candidate> result=new ArrayList<>();
        for(var element:array){var value=element.getAsJsonObject();String tile=value.get("tile").getAsString();parseTile(tile);result.add(new Candidate(tile,value.get("score").getAsBigDecimal().intValueExact(),value.get("value").getAsDouble(),value.get("risk").getAsDouble()));}return List.copyOf(result);
    }
    private static void persist(UUID id){ReplayStore.saveAnalysis(id,exportResults(id)).exceptionally(error->null);}
    private static Result withSummary(UUID id,Result result){return new Result(result.frame(),result.seat(),result.candidates(),result.chosenScore(),roundScores(id),result.engine(),result.error());}
    private static RecordCache findCache(UUID id){synchronized(CACHES){return CACHES.get(id);}}
    private static RecordCache cache(UUID id){synchronized(CACHES){RecordCache result=CACHES.computeIfAbsent(id,n->new RecordCache());while(CACHES.size()>8){UUID oldest=CACHES.keySet().iterator().next();BATCHES.remove(oldest);COMPLETED.remove(oldest);RecordCache evicted=CACHES.remove(oldest);evicted.frames.values().forEach(e->e.future().cancel(false));}return result;}}
    private static void scheduleNext(ReplayRecord record,int start,Consumer<Result> callback){
        if(closed||!BATCHES.contains(record.id())){BATCHES.remove(record.id());return;}
        int index=start;
        while(index<record.frames().size()&&!discardFrame(record.frames().get(index).driver()))index++;
        if(index>=record.frames().size()){
            if(cache(record.id()).frames.values().stream().allMatch(e->e.future().isDone()&&!e.future().isCompletedExceptionally()&&e.future().getNow(null).result().available()))COMPLETED.add(record.id());
            BATCHES.remove(record.id());persist(record.id());return;
        }
        int next=index+1;
        requestInternal(record,index,false).whenComplete((value,error)->{
            if(value!=null&&callback!=null)try{callback.accept(withSummary(record.id(),value.result()));}catch(RuntimeException ignored){}
            if(next%50==0)persist(record.id());
            // Enqueue the continuation even for a cache hit, avoiding recursive traversal on the tick thread.
            if(!closed&&!QUEUE.offerLast(()->scheduleNext(record,next,callback)))BATCHES.remove(record.id());
            EXECUTOR.prestartCoreThread();
        });
    }
    private static CompletableFuture<Evaluated> requestInternal(ReplayRecord record,int index,boolean priority){
        if(index<0||index>=record.frames().size())return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid replay frame"));
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Replay analysis stopped"));
        RecordCache cache=cache(record.id());
        Entry previous=cache.frames.get(index);
        if(previous!=null){
            boolean retry=previous.future().isDone()&&!previous.future().isCompletedExceptionally()&&!previous.future().getNow(null).result().available()&&System.nanoTime()-previous.created()>TimeUnit.SECONDS.toNanos(5);
            if(!retry)return previous.future();
            cache.frames.remove(index,previous);
        }
        var future=new CompletableFuture<Evaluated>();var entry=new Entry(future,System.nanoTime());
        previous=cache.frames.putIfAbsent(index,entry);if(previous!=null)return previous.future();
        Runnable task=()->{try{future.complete(evaluate(record,index,cache));}catch(Exception error){future.complete(failed(index,error.getClass().getSimpleName()));}};
        EXECUTOR.prestartCoreThread();
        boolean accepted=priority?QUEUE.offerFirst(task):QUEUE.offerLast(task);
        if(!accepted){cache.frames.remove(index,entry);future.complete(failed(index,"分析队列已满，请稍后重试"));}
        return future;
    }
    private static Evaluated failed(int frame,String error){return new Evaluated(new Result(frame,-1,List.of(),-1,Map.of(),"",error),List.of(),-1);}
    private static boolean discardFrame(CompoundTag tag){return tag.getCompoundOrEmpty("phase").getStringOr("type","").equals("AWAITING_DISCARD");}
    private static Evaluated evaluate(ReplayRecord record,int index,RecordCache cache)throws Exception{
        var frame=record.frames().get(index);TheMahjongDriver driver=decode(frame.driver());
        if(!(driver.currentPhase() instanceof MatchPhase.AwaitingDiscard discard))return new Evaluated(new Result(index,-1,List.of(),-1,Map.of(),"",""),List.of(),frame.roundIndex());
        int seat=discard.seat();var legal=driver.legalActions(seat);
        if(legal.stream().noneMatch(a->a instanceof PlayerAction.Discard||a instanceof PlayerAction.DiscardWithRiichi))return new Evaluated(new Result(index,seat,List.of(),-1,Map.of(),"",""),List.of(),frame.roundIndex());
        var request=new DecisionRequest(seat,driver.currentPhase(),legal,driver.currentRound(),driver.rules());
        boolean red=driver.match().tileSet().tiles().stream().anyMatch(TheMahjongTile::redDora);
        JsonObject snapshot=AiSnapshot.createAnalysis(request,red);
        List<Candidate> all,top;String engine;
        if(driver.match().playerCount()==4){
            snapshot.addProperty("mode","analysis");snapshot.addProperty("id",IDS.incrementAndGet());
            JsonObject reply=WORKER.request(snapshot);
            all=parseCandidates(reply.getAsJsonArray("scores"),snapshot);top=parseCandidates(reply.getAsJsonArray("candidates"),snapshot);
            if(top.size()>3||top.isEmpty())throw new IOException("Invalid recommendation count");engine=reply.get("engine").getAsString();
        }else{all=threePlayer(driver,snapshot);double maximum=all.stream().mapToDouble(Candidate::value).max().orElse(1);top=all.stream().filter(c->maximum>0&&99*c.value()/maximum>=1).limit(3).toList();engine="existing-bot/shanten-ukeire-sanma";}
        String chosen=actualDiscard(record,index,seat,driver.currentRound());
        int chosenScore=chosen==null?-1:all.stream().filter(c->c.tile().equals(chosen)).mapToInt(Candidate::score).findFirst().orElse(0);
        if(chosenScore>=0){int discardNumber=driver.currentRound().players().get(seat).discards().size();String key=frame.roundIndex()+":"+seat+":"+discardNumber;Integer old=cache.actualChoices.put(key,chosenScore);if(old==null||old!=chosenScore)updateSummary(cache);}
        return new Evaluated(new Result(index,seat,top,chosenScore,Map.of(),engine,""),all,frame.roundIndex());
    }
    private static List<Candidate> parseCandidates(JsonArray array,JsonObject snapshot)throws IOException{
        if(array==null||array.size()>14)throw new IOException("Invalid analysis candidates");
        List<Candidate> result=new ArrayList<>();Set<String> seen=new java.util.HashSet<>();
        for(var element:array){var value=element.getAsJsonObject();String tile=value.get("tile").getAsString();boolean allowed=false;
            for(var action:snapshot.getAsJsonArray("legal")){var a=action.getAsJsonObject();if(a.has("tile")&&a.get("type").getAsString().startsWith("Discard")&&a.get("tile").getAsString().equals(tile))allowed=true;}
            if(!allowed||!seen.add(tile))throw new IOException("Analysis returned an illegal tile");
            int score=value.get("score").getAsBigDecimal().intValueExact();result.add(new Candidate(tile,score,value.get("value").getAsDouble(),value.get("risk").getAsDouble()));
        }return List.copyOf(result);
    }
    private static TheMahjongDriver decode(CompoundTag data){
        int count=data.getCompoundOrEmpty("match").getIntOr("playerCount",0);
        List<MahjongPlayerInterface> players=new ArrayList<>();for(int i=0;i<count;i++)players.add((request,delta)->Optional.empty());
        return DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,HolderLookup.Provider.create(Stream.empty()),data),players,new Random(0));
    }
    private static String actualDiscard(ReplayRecord record,int index,int seat,TheMahjongRound before){
        int size=before.players().get(seat).discards().size(),round=record.frames().get(index).roundIndex();
        for(int i=index+1;i<record.frames().size();i++){
            var frame=record.frames().get(i);if(frame.roundIndex()!=round)return null;
            var state=frame.driver().getCompoundOrEmpty("match").getCompoundOrEmpty("currentRound");
            var players=state.getListOrEmpty("players");if(seat>=players.size())continue;
            var river=players.getCompoundOrEmpty(seat).getListOrEmpty("discards");
            if(river.size()>size){var tile=river.getCompoundOrEmpty(size).getCompoundOrEmpty("tile");return tileCode(tile);}
        }return null;
    }
    private static String tileCode(CompoundTag tag){
        String suit=tag.getStringOr("suit","");int rank=tag.getIntOr("rank",0);boolean red=tag.getBooleanOr("redDora",false);
        return switch(suit){case "MANZU"->"m"+(red?0:rank);case "PINZU"->"p"+(red?0:rank);case "SOUZU"->"s"+(red?0:rank);case "WIND"->"z"+rank;case "DRAGON"->"z"+(rank+4);default->null;};
    }
    public static TheMahjongTile parseTile(String code){
        if(code==null||!code.matches("(?:[mps][0-9]|z[1-7])"))throw new IllegalArgumentException("Invalid analysis tile");int number=code.charAt(1)-'0';
        return switch(code.charAt(0)){case 'm'->new TheMahjongTile(TheMahjongTile.Suit.MANZU,number==0?5:number,number==0);case 'p'->new TheMahjongTile(TheMahjongTile.Suit.PINZU,number==0?5:number,number==0);case 's'->new TheMahjongTile(TheMahjongTile.Suit.SOUZU,number==0?5:number,number==0);default->new TheMahjongTile(number<=4?TheMahjongTile.Suit.WIND:TheMahjongTile.Suit.DRAGON,number<=4?number:number-4,false);};
    }
    private static int kind(String tile){int n=tile.charAt(1)-'0';if(n==0)n=5;return switch(tile.charAt(0)){case 'm'->n-1;case 'p'->n+8;case 's'->n+17;default->n+26;};}
    /** The live three-player BOT has no EV API; reuse its existing fallback hand-efficiency evaluator. */
    private static List<Candidate> threePlayer(TheMahjongDriver driver,JsonObject snapshot){
        int[] hand=new int[34],visible=new int[34],copies=new int[34];Set<String> allowed=new java.util.LinkedHashSet<>();
        for(var tile:driver.match().tileSet().tiles())copies[kind(AiSnapshot.tile(tile))]+=driver.match().tileSet().copiesPerTile().get(tile);
        for(var tile:snapshot.getAsJsonArray("hand")){hand[kind(tile.getAsString())]++;visible[kind(tile.getAsString())]++;}
        for(var tile:snapshot.getAsJsonArray("doraIndicators"))visible[kind(tile.getAsString())]++;
        for(var river:snapshot.getAsJsonArray("rivers"))for(var value:river.getAsJsonArray()){var t=value.getAsJsonObject();if(!t.get("called").getAsBoolean())visible[kind(t.get("tile").getAsString())]++;}
        for(var melds:snapshot.getAsJsonArray("melds"))for(var meld:melds.getAsJsonArray())for(var tile:meld.getAsJsonObject().getAsJsonArray("tiles"))visible[kind(tile.getAsString())]++;
        int seat=snapshot.get("seat").getAsInt(),open=snapshot.getAsJsonArray("melds").get(seat).getAsJsonArray().size();
        for(var value:snapshot.getAsJsonArray("legal")){var a=value.getAsJsonObject();if(a.get("type").getAsString().startsWith("Discard"))allowed.add(a.get("tile").getAsString());}
        record Choice(String tile,int shanten,int ukeire,double value){}List<Choice> choices=new ArrayList<>();int min=99;
        for(String tile:allowed){int k=kind(tile);hand[k]--;int shanten=FallbackPolicy.shanten(hand,open),ukeire=0;for(int draw=0;draw<34;draw++){int left=Math.max(0,copies[draw]-visible[draw]);if(left==0)continue;hand[draw]++;if(FallbackPolicy.shanten(hand,open)<shanten)ukeire+=left;hand[draw]--;}hand[k]++;choices.add(new Choice(tile,shanten,ukeire,ukeire+1));min=Math.min(min,shanten);}
        final int baseline=min;double max=choices.stream().mapToDouble(c->c.value()*Math.exp(-3*(c.shanten()-baseline))).max().orElse(1);
        List<Candidate> result=new ArrayList<>();for(var c:choices){double value=c.value()*Math.exp(-3*(c.shanten()-baseline));result.add(new Candidate(c.tile(),(int)Math.round(99*value/max),value,0));}
        result.sort(Comparator.comparingInt(Candidate::score).reversed().thenComparing(Candidate::tile));return List.copyOf(result);
    }
    private static final class Worker {
        private volatile Process process;private BufferedReader output;private BufferedWriter input;private long active=-1;
        JsonObject request(JsonObject snapshot)throws Exception{
            long id=snapshot.get("id").getAsLong();
            synchronized(this){active=id;}
            long timeout=Math.max(100,Math.min(30_000,Long.getLong("riichi.replay.analysisTimeoutMillis",10_000L)));
            var alarm=WATCHDOG.schedule(()->{synchronized(this){if(active==id)kill();}},timeout,TimeUnit.MILLISECONDS);
            try{
                if(process==null||!process.isAlive()){
                    var worker=AiRuntime.analysisWorkerPath();String node=System.getProperty("riichi.ai.node",System.getenv().getOrDefault("RIICHI_AI_NODE","node"));
                    var started=new ProcessBuilder(node,worker.toString()).directory(worker.getParent().toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    synchronized(this){if(active!=id||closed){started.destroyForcibly();throw new IOException("Analysis cancelled");}process=started;output=new BufferedReader(new InputStreamReader(started.getInputStream(),StandardCharsets.UTF_8));input=new BufferedWriter(new OutputStreamWriter(started.getOutputStream(),StandardCharsets.UTF_8));}
                }
                input.write(snapshot.toString());input.newLine();input.flush();var line=new StringBuilder();int c;
                while((c=output.read())>=0&&c!='\n'){if(c!='\r')line.append((char)c);if(line.length()>65_536)throw new IOException("Analysis response too large");}
                if(line.isEmpty())throw new IOException("Analysis worker exited");var reply=JsonParser.parseString(line.toString()).getAsJsonObject();
                if(reply.has("error")||!reply.has("id")||reply.get("id").getAsBigDecimal().longValueExact()!=id)throw new IOException("Invalid analysis response");return reply;
            }catch(Exception error){kill();throw error;}finally{alarm.cancel(false);synchronized(this){if(active==id)active=-1;}}
        }
        synchronized void kill(){active=-1;Process current=process;process=null;if(current!=null)current.destroyForcibly();}
    }
}
