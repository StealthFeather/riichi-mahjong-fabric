package com.riichimahjong.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riichimahjong.ai.AiRuntime;
import com.riichimahjong.ai.AiSnapshot;
import com.riichimahjong.ai.FallbackPolicy;
import com.riichimahjong.ai.MortalRuntime;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
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
    public static final int CACHE_VERSION=4;
    public static final String POLICY="mortal-v4-kind-softmax-t1-and-modeled-v3";
    private static final String KOBALAB_POLICY="modeled-kind-softmax-v3";
    private static final String MORTAL_ENGINE="Mortal/v4-kind-softmax-t1";
    private static final String UNSCORABLE="Mortal 无法还原此帧的历史局面";
    public record Candidate(String tile,int probability,int rank,double policyProbability,double value,double risk) {
        public Candidate { tile=tileKind(tile);checkProbability(probability,rank,policyProbability);if(!Double.isFinite(value)||!Double.isFinite(risk))throw new IllegalArgumentException("Invalid policy utility"); }
        /** Compatibility accessor; this is an integer policy percentage, never the former 0–99 quality score. */
        public int score(){return probability;}
        public TheMahjongTile mahjongTile(){return parseTile(tile);}
    }
    public record ActionProbability(int index,String type,String tile,String label,int probability,int rank,double policyProbability) {
        public ActionProbability { if(index<0||type==null||label==null)throw new IllegalArgumentException("Invalid policy action");if(tile!=null)parseTile(tile);checkProbability(probability,rank,policyProbability); }
    }
    public record Decision(int seat,List<Candidate> candidates,List<ActionProbability> actions,String chosenTile,int chosenProbability,int chosenRank,int chosenActionIndex) {
        public Decision { candidates=List.copyOf(candidates);actions=List.copyOf(actions);if(chosenTile!=null)chosenTile=tileKind(chosenTile);if(seat<0||seat>3)throw new IllegalArgumentException("Invalid decision seat"); }
    }
    public record Result(int frame,int seat,List<Candidate> candidates,int chosenScore,
                         Map<Integer,Map<Integer,Integer>> roundScores,String engine,String error,
                         List<ActionProbability> actions,String chosenTile,int chosenRank,Map<Integer,Decision> decisions) {
        public Result { candidates=List.copyOf(candidates);actions=List.copyOf(actions);roundScores=Map.copyOf(roundScores);decisions=Map.copyOf(decisions); }
        public Result(int frame,int seat,List<Candidate> candidates,int chosenScore,Map<Integer,Map<Integer,Integer>> roundScores,String engine,String error){this(frame,seat,candidates,chosenScore,roundScores,engine,error,List.of(),null,-1,Map.of());}
        public boolean available(){return error.isEmpty();}
        public boolean excluded(){return UNSCORABLE.equals(error);}
        public int chosenProbability(){return chosenScore;}
        public Optional<Decision> decisionForSeat(int wanted){return Optional.ofNullable(decisions.get(wanted));}
    }
    private static void checkProbability(int probability,int rank,double modeled){if(probability<0||probability>100||rank<1||!Double.isFinite(modeled)||modeled<0||modeled>1)throw new IllegalArgumentException("Invalid modeled policy probability");}
    private record Evaluated(Result result,int round){}
    private record Entry(CompletableFuture<Evaluated> future,long created){}
    private static final class RecordCache {
        final Map<Integer,Entry> frames=new ConcurrentHashMap<>();
        final Map<String,Double> actualChoices=new ConcurrentHashMap<>();
        volatile Map<Integer,Map<Integer,Integer>> summary=Map.of();
        volatile Map<Integer,Integer> matchSummary=Map.of();
        ReplayTimeline timeline;
        synchronized ReplayTimeline timeline(ReplayRecord record){if(timeline==null)timeline=new ReplayTimeline(record);return timeline;}
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

    public static boolean usesMortal(ReplayRecord record) {
        if (record.frames().isEmpty()) return false;
        var match = record.frames().getFirst().driver().getCompoundOrEmpty("match");
        int winds = match.getIntOr("roundCount", 0);
        if (winds != 1 && winds != 2) return false;
        try {
            var preset = RuleSetPreset.valueOf(record.preset());
            return !match.getBooleanOr("sichuan", false) && preset.supportsMortal(match.getIntOr("playerCount",0),
                    winds == 1 ? MatchLength.EAST : MatchLength.SOUTH, match.getBooleanOr("exchangeThree",false),
                    match.getBooleanOr("jokersEnabled",false), match.getBooleanOr("bloodBattle",false));
        } catch (IllegalArgumentException unknownPreset) {
            return false;
        }
    }

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
    public static boolean analysisComplete(UUID id){return COMPLETED.contains(id);}
    /** Verify every required recorded decision, including frames rejected by a full queue. */
    public static boolean analysisComplete(ReplayRecord record){
        RecordCache current=findCache(record.id());if(current==null)return false;
        for(int frame=0;frame<record.frames().size();frame++)if(analysisFrame(record.frames().get(frame).driver())){
            Entry entry=current.frames.get(frame);
            if(entry==null||!entry.future().isDone()||entry.future().isCompletedExceptionally()||entry.future().isCancelled())return false;
            Evaluated value=entry.future().getNow(null);if(value==null||(!value.result().available()&&!value.result().excluded()))return false;
        }
        return true;
    }
    /** Page-local persisted score read; does not populate/evict the interactive analysis cache. */
    public static Map<Integer,Integer> completedMatchScores(UUID id,JsonObject json){
        if(json==null||!json.has("version")||json.get("version").getAsInt()!=CACHE_VERSION||!json.has("policy")||!POLICY.equals(json.get("policy").getAsString())||!json.has("record")||!id.toString().equals(json.get("record").getAsString()))throw new IllegalArgumentException("Unsupported analysis score cache");
        if(!json.has("complete")||!json.get("complete").getAsBoolean())return Map.of();
        Map<Integer,double[]> totals=new LinkedHashMap<>();var choices=json.getAsJsonObject("actualChoices");
        if(choices==null)return Map.of();
        choices.entrySet().forEach(entry->{String key=entry.getKey();double score=entry.getValue().getAsDouble();if(!key.matches("[0-9]+:[0-3]:[0-9]+")||!Double.isFinite(score)||score<0||score>100)throw new IllegalArgumentException("Invalid cached consistency score");int seat=Integer.parseInt(key.split(":")[1]);double[] sum=totals.computeIfAbsent(seat,n->new double[2]);sum[0]+=score;sum[1]++;});
        Map<Integer,Integer> scores=new LinkedHashMap<>();totals.forEach((seat,sum)->scores.put(seat,(int)Math.round(sum[0]/sum[1])));return Map.copyOf(scores);
    }
    public static Map<Integer,Integer> matchScores(UUID id){RecordCache current=findCache(id);return current==null?Map.of():current.matchSummary;}
    /** Rebuilt only on background score changes; rendering reads the immutable summary in O(1). */
    private static void updateSummary(RecordCache cache){
        Map<Integer,Map<Integer,double[]>> totals=new LinkedHashMap<>();Map<Integer,double[]> match=new LinkedHashMap<>();
        cache.actualChoices.forEach((key,score)->{
            String[] parts=key.split(":");int round=Integer.parseInt(parts[0]),seat=Integer.parseInt(parts[1]);
            double[] accumulator=totals.computeIfAbsent(round,n->new LinkedHashMap<>()).computeIfAbsent(seat,n->new double[2]);accumulator[0]+=score;accumulator[1]++;
            double[] overall=match.computeIfAbsent(seat,n->new double[2]);overall[0]+=score;overall[1]++;
        });
        Map<Integer,Map<Integer,Integer>> result=new LinkedHashMap<>();
        totals.forEach((round,seats)->{Map<Integer,Integer> scores=new LinkedHashMap<>();seats.forEach((seat,sum)->scores.put(seat,(int)Math.round(sum[0]/sum[1])));result.put(round,Map.copyOf(scores));});
        Map<Integer,Integer> overall=new LinkedHashMap<>();match.forEach((seat,sum)->overall.put(seat,(int)Math.round(sum[0]/sum[1])));
        cache.summary=Map.copyOf(result);cache.matchSummary=Map.copyOf(overall);
    }
    /** One frame is enqueued at a time, allowing interactive requests to jump ahead of background work. */
    public static void analyzeAll(ReplayRecord record,Consumer<Result> callback){
        if(closed)return;
        if(COMPLETED.contains(record.id())){if(analysisComplete(record))return;COMPLETED.remove(record.id());}
        if(!BATCHES.add(record.id()))return;
        UUID viewer=record.participants().stream().map(ReplayRecord.Participant::uuid).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if(viewer==null){scheduleNext(record,0,callback);return;}
        ReplayStore.loadAnalysis(viewer,record.id()).whenComplete((json,error)->{
            if(json!=null)try{importResults(record.id(),json);}catch(RuntimeException ignored){}
            if(closed){BATCHES.remove(record.id());return;}
            if(COMPLETED.contains(record.id())){if(analysisComplete(record)){BATCHES.remove(record.id());return;}COMPLETED.remove(record.id());}
            scheduleNext(record,0,callback);
        });
    }
    public static int analyzedFrames(UUID id){RecordCache current=findCache(id);return current==null?0:(int)current.frames.values().stream().filter(e->e.future().isDone()&&!e.future().isCompletedExceptionally()&&e.future().getNow(null).result().available()).count();}
    public static void shutdown(){
        closed=true;BATCHES.clear();COMPLETED.clear();WORKER.kill();MortalRuntime.closeReplay();QUEUE.clear();EXECUTOR.shutdownNow();WATCHDOG.shutdownNow();
        synchronized(CACHES){CACHES.values().forEach(c->c.frames.values().forEach(e->e.future().cancel(false)));CACHES.clear();}
    }
    /** Only kind distributions are cached; older physical-tile policies are deliberately rejected. */
    public static JsonObject exportResults(UUID id){
        var json=new JsonObject();json.addProperty("version",CACHE_VERSION);json.addProperty("policy",POLICY);json.addProperty("record",id.toString());json.addProperty("complete",COMPLETED.contains(id));
        var frames=new JsonArray();RecordCache cache=cache(id);
        cache.frames.forEach((index,entry)->{
            if(!entry.future().isDone()||entry.future().isCompletedExceptionally()||entry.future().isCancelled())return;
            Evaluated value=entry.future().getNow(null);if(!value.result().available()&&!value.result().excluded())return;
            var frame=new JsonObject();
            if(value.result().excluded()){frame.addProperty("excluded",true);frame.addProperty("frame",index);frame.addProperty("round",value.round());frames.add(frame);return;}
            Result result=value.result();frame.addProperty("frame",index);frame.addProperty("round",value.round());frame.addProperty("primarySeat",result.seat());frame.addProperty("engine",result.engine());
            var decisions=new JsonArray();result.decisions().values().forEach(decision->{var data=new JsonObject();data.addProperty("seat",decision.seat());data.addProperty("chosenTile",decision.chosenTile());data.addProperty("chosenProbability",decision.chosenProbability());data.addProperty("chosenRank",decision.chosenRank());data.addProperty("chosenActionIndex",decision.chosenActionIndex());data.add("candidates",candidatesJson(decision.candidates()));data.add("actions",actionsJson(decision.actions()));decisions.add(data);});
            frame.add("decisions",decisions);frames.add(frame);
        });
        json.add("frames",frames);var choices=new JsonObject();cache.actualChoices.forEach(choices::addProperty);json.add("actualChoices",choices);return json;
    }
    public static void importResults(UUID id,JsonObject json){
        if(json==null||!json.has("version")||json.get("version").getAsInt()!=CACHE_VERSION||!json.has("policy")||!POLICY.equals(json.get("policy").getAsString())||!id.toString().equals(json.get("record").getAsString()))throw new IllegalArgumentException("Unsupported analysis cache; recompute modeled probabilities");
        JsonArray frames=json.getAsJsonArray("frames");if(frames==null||frames.size()>100_000)throw new IllegalArgumentException("Invalid analysis cache");
        Map<Integer,Entry> imported=new LinkedHashMap<>();Map<String,Double> actual=new LinkedHashMap<>();
        for(var element:frames){var frame=element.getAsJsonObject();int index=frame.get("frame").getAsInt(),round=frame.get("round").getAsInt();
            if(frame.has("excluded") && frame.get("excluded").getAsBoolean()){
                if(index<0||round<0)throw new IllegalArgumentException("Invalid excluded frame");
                imported.put(index,new Entry(CompletableFuture.completedFuture(excluded(index,round)),System.nanoTime()));continue;
            }
            int primary=frame.get("primarySeat").getAsInt();
            if(index<0||round<0||primary< -1||primary>3)throw new IllegalArgumentException("Invalid cached analysis frame");
            Map<Integer,Decision> decisions=new LinkedHashMap<>();var values=frame.getAsJsonArray("decisions");if(values.size()>4)throw new IllegalArgumentException("Invalid cached seats");
            for(var value:values){var data=value.getAsJsonObject();int seat=data.get("seat").getAsInt();List<Candidate> candidates=cachedCandidates(data.getAsJsonArray("candidates"));List<ActionProbability> actions=cachedActions(data.getAsJsonArray("actions"));String chosen=data.get("chosenTile").isJsonNull()?null:data.get("chosenTile").getAsString();int probability=data.get("chosenProbability").getAsInt(),rank=data.get("chosenRank").getAsInt(),action=data.get("chosenActionIndex").getAsInt();
                if(chosen!=null){Candidate match=candidates.stream().filter(c->c.tile().equals(chosen)).findFirst().orElseThrow();if(match.probability()!=probability||match.rank()!=rank)throw new IllegalArgumentException("Invalid actual probability");}
                else if(probability!=-1||rank!=-1)throw new IllegalArgumentException("Missing actual discard");
                if(decisions.put(seat,new Decision(seat,candidates,actions,chosen,probability,rank,action))!=null)throw new IllegalArgumentException("Duplicate cached seat");
            }
            Result result=assemble(index,primary,decisions,frame.get("engine").getAsString());imported.put(index,new Entry(CompletableFuture.completedFuture(new Evaluated(result,round)),System.nanoTime()));
        }
        var choices=json.getAsJsonObject("actualChoices");if(choices!=null)choices.entrySet().forEach(entry->{double score=entry.getValue().getAsDouble();if(!entry.getKey().matches("[0-9]+:[0-3]:[0-9]+")||!Double.isFinite(score)||score<0||score>100)throw new IllegalArgumentException("Invalid cached actual consistency score");actual.put(entry.getKey(),score);});
        RecordCache cache=cache(id);imported.forEach(cache.frames::putIfAbsent);cache.actualChoices.putAll(actual);updateSummary(cache);if(json.has("complete")&&json.get("complete").getAsBoolean())COMPLETED.add(id);
    }
    private static JsonArray candidatesJson(List<Candidate> candidates){var array=new JsonArray();for(var c:candidates){var v=new JsonObject();v.addProperty("tile",c.tile());v.addProperty("probability",c.probability());v.addProperty("rank",c.rank());v.addProperty("policyProbability",c.policyProbability());v.addProperty("value",c.value());v.addProperty("risk",c.risk());array.add(v);}return array;}
    private static JsonArray actionsJson(List<ActionProbability> actions){var array=new JsonArray();for(var a:actions){var v=new JsonObject();v.addProperty("index",a.index());v.addProperty("type",a.type());v.addProperty("tile",a.tile());v.addProperty("label",a.label());v.addProperty("probability",a.probability());v.addProperty("rank",a.rank());v.addProperty("policyProbability",a.policyProbability());array.add(v);}return array;}
    private static List<Candidate> cachedCandidates(JsonArray array){
        if(array==null||array.size()>14)throw new IllegalArgumentException("Invalid cached candidates");List<Candidate> result=new ArrayList<>();Set<String> tiles=new java.util.HashSet<>();
        for(var e:array){var v=e.getAsJsonObject();String kind=v.get("tile").getAsString();if(!kind.equals(tileKind(kind)))throw new IllegalArgumentException("Cached candidate must use the canonical tile kind");Candidate c=new Candidate(kind,v.get("probability").getAsBigDecimal().intValueExact(),v.get("rank").getAsInt(),v.get("policyProbability").getAsDouble(),v.get("value").getAsDouble(),v.get("risk").getAsDouble());if(!tiles.add(c.tile()))throw new IllegalArgumentException("Duplicate cached candidate");result.add(c);}
        validateDistribution(result.stream().map(Candidate::probability).toList(),result.stream().map(Candidate::policyProbability).toList(),result.stream().map(Candidate::rank).toList());result.sort(Comparator.comparingInt(Candidate::rank));return List.copyOf(result);
    }
    private static List<ActionProbability> cachedActions(JsonArray array){
        if(array==null||array.size()>100)throw new IllegalArgumentException("Invalid cached actions");List<ActionProbability> result=new ArrayList<>();Set<Integer> indices=new java.util.HashSet<>();
        for(var e:array){var v=e.getAsJsonObject();ActionProbability a=new ActionProbability(v.get("index").getAsInt(),v.get("type").getAsString(),v.get("tile").isJsonNull()?null:v.get("tile").getAsString(),v.get("label").getAsString(),v.get("probability").getAsBigDecimal().intValueExact(),v.get("rank").getAsInt(),v.get("policyProbability").getAsDouble());if(!indices.add(a.index()))throw new IllegalArgumentException("Duplicate cached action");result.add(a);}
        validateDistribution(result.stream().map(ActionProbability::probability).toList(),result.stream().map(ActionProbability::policyProbability).toList(),result.stream().map(ActionProbability::rank).toList());result.sort(Comparator.comparingInt(ActionProbability::rank));return List.copyOf(result);
    }
    private static void validateDistribution(List<Integer> probabilities,List<Double> modeled,List<Integer> ranks){
        if(probabilities.isEmpty())return;if(probabilities.stream().mapToInt(Integer::intValue).sum()!=100||Math.abs(modeled.stream().mapToDouble(Double::doubleValue).sum()-1)>1e-6)throw new IllegalArgumentException("Policy must sum to one / 100 percent");
        Set<Integer> unique=new java.util.HashSet<>(ranks);for(int rank=1;rank<=ranks.size();rank++)if(!unique.contains(rank))throw new IllegalArgumentException("Invalid policy ranks");
    }
    private static void persist(UUID id){ReplayStore.saveAnalysis(id,exportResults(id)).exceptionally(error->null);}
    private static Result withSummary(UUID id,Result result){return new Result(result.frame(),result.seat(),result.candidates(),result.chosenScore(),roundScores(id),result.engine(),result.error(),result.actions(),result.chosenTile(),result.chosenRank(),result.decisions());}
    private static RecordCache findCache(UUID id){synchronized(CACHES){return CACHES.get(id);}}
    private static RecordCache cache(UUID id){synchronized(CACHES){RecordCache result=CACHES.computeIfAbsent(id,n->new RecordCache());while(CACHES.size()>8){UUID oldest=CACHES.keySet().iterator().next();BATCHES.remove(oldest);COMPLETED.remove(oldest);RecordCache evicted=CACHES.remove(oldest);evicted.frames.values().forEach(e->e.future().cancel(false));}return result;}}
    private static void scheduleNext(ReplayRecord record,int start,Consumer<Result> callback){
        if(closed||!BATCHES.contains(record.id())){BATCHES.remove(record.id());return;}
        int index=start;
        while(index<record.frames().size()&&!analysisFrame(record.frames().get(index).driver()))index++;
        if(index>=record.frames().size()){
            if(analysisComplete(record))COMPLETED.add(record.id());else COMPLETED.remove(record.id());
            BATCHES.remove(record.id());persist(record.id());return;
        }
        int next=index+1;
        requestInternal(record,index,false).whenComplete((value,error)->{
            if(value!=null&&callback!=null)try{callback.accept(withSummary(record.id(),value.result()));}catch(RuntimeException ignored){}
            if(value!=null&&!value.result().available()&&!value.result().excluded()){
                BATCHES.remove(record.id());persist(record.id());return;
            }
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
            boolean retry = previous.future().isCompletedExceptionally();
            if (!retry && previous.future().isDone()) {
                var result = previous.future().getNow(null).result();
                retry = !result.available() && !result.excluded();
            }
            if(!retry)return previous.future();
            cache.frames.remove(index,previous);
        }
        var future=new CompletableFuture<Evaluated>();var entry=new Entry(future,System.nanoTime());
        previous=cache.frames.putIfAbsent(index,entry);if(previous!=null)return previous.future();
        Runnable task=()->{try{future.complete(evaluate(record,index,cache));}
            catch(Exception error){future.complete(failed(index,(usesMortal(record)?"Mortal 分析暂不可用 · ":"")+error.getClass().getSimpleName()));}};
        EXECUTOR.prestartCoreThread();
        boolean accepted=priority?QUEUE.offerFirst(task):QUEUE.offerLast(task);
        if(!accepted){cache.frames.remove(index,entry);future.complete(failed(index,"分析队列已满，请稍后重试"));}
        return future;
    }
    private static Evaluated excluded(int frame,int round){
        return new Evaluated(new Result(frame,-1,List.of(),-1,Map.of(),MORTAL_ENGINE,UNSCORABLE),round);
    }
    private static Evaluated failed(int frame,String error){return new Evaluated(new Result(frame,-1,List.of(),-1,Map.of(),"",error),-1);}
    private static boolean analysisFrame(CompoundTag tag){String type=tag.getCompoundOrEmpty("phase").getStringOr("type","");return type.equals("AWAITING_DISCARD")||type.equals("AWAITING_CLAIMS")||type.equals("AWAITING_DRAW");}
    private static Result assemble(int frame,int primary,Map<Integer,Decision> decisions,String engine){
        Decision decision=decisions.get(primary);return new Result(frame,primary,decision==null?List.of():decision.candidates(),decision==null?-1:decision.chosenProbability(),Map.of(),engine,"",decision==null?List.of():decision.actions(),decision==null?null:decision.chosenTile(),decision==null?-1:decision.chosenRank(),decisions);
    }
    private static Evaluated evaluate(ReplayRecord record,int index,RecordCache cache)throws Exception{
        var frame=record.frames().get(index);TheMahjongDriver driver=decode(frame.driver());Map<Integer,Decision> decisions=new LinkedHashMap<>();String engine="";boolean changed=false;
        boolean mortal=usesMortal(record);
        boolean red=driver.match().tileSet().tiles().stream().anyMatch(TheMahjongTile::redDora);
        for(int seat=0;seat<driver.match().playerCount();seat++){
            var legal=driver.legalActions(seat);if(legal.isEmpty())continue;
            var request=new DecisionRequest(seat,driver.currentPhase(),legal,driver.currentRound(),driver.rules());JsonObject snapshot=AiSnapshot.createAnalysis(request,red);List<Candidate> candidates;List<ActionProbability> actions;
            if (mortal) {
                PolicyResult policy;
                if (legal.size() == 1 && (legal.getFirst() instanceof PlayerAction.Draw || legal.getFirst() instanceof PlayerAction.Pass)) {
                    var only = snapshot.getAsJsonArray("legal").get(0).getAsJsonObject();
                    String type = only.get("type").getAsString(), tile = only.has("tile") ? only.get("tile").getAsString() : null;
                    policy = new PolicyResult(List.of(),List.of(new ActionProbability(0,type,tile,actionLabel(type),100,1,1)));
                } else {
                    var events = MortalEvents.forReplayFrame(record.frames(), index, seat);
                    if (events.isEmpty()) return excluded(index, frame.roundIndex());
                    long draws = events.asList().stream().filter(e -> e.getAsJsonObject().get("type").getAsString().equals("tsumo")).count();
                    // Old rules could expose another live draw or allow melds after 70 draws following a kan.
                    if (draws > 70 || (draws == 70 && driver.currentPhase() instanceof MatchPhase.AwaitingClaims
                            && legal.stream().anyMatch(a -> a instanceof PlayerAction.Chi || a instanceof PlayerAction.Pon
                            || a instanceof PlayerAction.Daiminkan))) return excluded(index, frame.roundIndex());
                    var reply = MortalRuntime.analyzeReplay(snapshot, events, MatchLength.fromMatch(driver.match()));
                    policy = mortalPolicy(reply,snapshot);
                }
                candidates=policy.candidates();actions=policy.actions();engine=MORTAL_ENGINE;
            } else if(driver.match().playerCount()==4){
                snapshot.addProperty("mode","analysis");snapshot.addProperty("id",IDS.incrementAndGet());JsonObject reply=WORKER.request(snapshot);
                if(!reply.has("policy")||!KOBALAB_POLICY.equals(reply.getAsJsonObject("policy").get("name").getAsString()))throw new IOException("Unsupported worker policy");
                candidates=parseCandidates(reply.getAsJsonArray("candidates"),snapshot);actions=parseActions(reply.getAsJsonArray("actions"),snapshot);engine=reply.get("engine").getAsString()+"/"+KOBALAB_POLICY;
            }else{PolicyResult policy=threePlayer(driver,snapshot);candidates=policy.candidates();actions=policy.actions();engine="existing-bot/shanten-ukeire-sanma/"+KOBALAB_POLICY;}
            var actual=cache.timeline(record).discardForDecision(index,seat);String actualTile=actual.map(ReplayTimeline.Discard::tile).orElse(null);String chosen=actualTile==null?null:tileKind(actualTile);int probability=-1,rank=-1,actionIndex=-1;
            if(chosen!=null){var match=candidates.stream().filter(c->c.tile().equals(chosen)).findFirst();if(match.isPresent()){probability=match.get().probability();rank=match.get().rank();String type=actual.orElseThrow().riichi()?"DiscardWithRiichi":"Discard";actionIndex=actions.stream().filter(a->a.type().equals(type)&&actualTile.equals(a.tile())).mapToInt(ActionProbability::index).findFirst().orElse(-1);String key=frame.roundIndex()+":"+seat+":"+actual.orElseThrow().riverIndex();double maximum=candidates.stream().mapToDouble(Candidate::policyProbability).max().orElse(0);double score=consistencyScore(match.get().policyProbability(),maximum);if(index==actual.orElseThrow().discardFrame()-1){Double old=cache.actualChoices.put(key,score);changed|=old==null||Double.compare(old,score)!=0;}}}
            decisions.put(seat,new Decision(seat,candidates,actions,chosen,probability,rank,actionIndex));
        }
        if(changed)updateSummary(cache);int primary=driver.currentPhase() instanceof MatchPhase.AwaitingDiscard d?d.seat():driver.currentPhase() instanceof MatchPhase.AwaitingDraw d?d.seat():decisions.keySet().stream().mapToInt(Integer::intValue).findFirst().orElse(-1);
        return new Evaluated(assemble(index,primary,decisions,engine),frame.roundIndex());
    }
    /** Both raw distributions are independently normalized. Only percentages use integer rounding. */
    private static PolicyResult mortalPolicy(JsonObject reply,JsonObject snapshot) throws IOException {
        if (!"Mortal".equals(reply.get("engine").getAsString())) throw new IOException("Unexpected replay engine");
        var weights = reply.getAsJsonObject("weights");
        var legal = snapshot.getAsJsonArray("legal");
        var actionWeights = reply.getAsJsonArray("actions");
        if(weights==null || actionWeights==null || legal.size()!=actionWeights.size())
            throw new IOException("Missing Mortal policy");
        var allowed = new java.util.TreeSet<String>();
        for (var element : legal) {
            var move=element.getAsJsonObject();
            if(move.get("type").getAsString().startsWith("Discard")) allowed.add(tileKind(move.get("tile").getAsString()));
        }
        if (!allowed.equals(weights.keySet())) throw new IOException("Mortal policy must cover exactly the legal discard kinds");
        var kinds = List.copyOf(allowed);
        double[] discard = kinds.stream().mapToDouble(tile -> weights.get(tile).getAsDouble()).toArray();
        int[] percentages = largestRemainders(discard);
        var candidates = new ArrayList<Candidate>();
        int rank=0;
        for (int i : probabilityOrder(discard)) candidates.add(new Candidate(kinds.get(i),percentages[i],++rank,discard[i],0,0));
        double[] actions = new double[legal.size()];
        java.util.Arrays.fill(actions,Double.NaN);
        for (var element : actionWeights) {
            var item=element.getAsJsonObject();int i=item.get("index").getAsBigDecimal().intValueExact();
            if(i<0 || i>=actions.length || !Double.isNaN(actions[i])) throw new IOException("Invalid Mortal action index");
            actions[i]=item.get("probability").getAsDouble();
        }
        percentages=largestRemainders(actions);
        var result=new ArrayList<ActionProbability>();rank=0;
        for(int i : probabilityOrder(actions)){
            var move=legal.get(i).getAsJsonObject();String type=move.get("type").getAsString();
            result.add(new ActionProbability(i,type,move.has("tile")?move.get("tile").getAsString():null,
                    actionLabel(type),percentages[i],++rank,actions[i]));
        }
        return new PolicyResult(List.copyOf(candidates),List.copyOf(result));
    }
    private static List<Integer> probabilityOrder(double[] weights) throws IOException {
        if(weights.length>0 && Math.abs(java.util.Arrays.stream(weights).sum()-1)>1e-6)
            throw new IOException("Mortal probabilities must sum to one");
        return java.util.stream.IntStream.range(0,weights.length).boxed()
                .sorted((a,b)->Double.compare(weights[b],weights[a])).toList();
    }

    private static List<Candidate> parseCandidates(JsonArray array,JsonObject snapshot)throws IOException{
        List<Candidate> result;try{result=cachedCandidates(array);}catch(RuntimeException error){throw new IOException("Invalid discard distribution",error);}
        Set<String> allowed=new java.util.HashSet<>();for(var value:snapshot.getAsJsonArray("legal")){var a=value.getAsJsonObject();if(a.has("tile")&&a.get("type").getAsString().startsWith("Discard"))allowed.add(tileKind(a.get("tile").getAsString()));}
        if(!allowed.equals(result.stream().map(Candidate::tile).collect(java.util.stream.Collectors.toSet())))throw new IOException("Discard distribution omitted or invented legal tiles");return result;
    }
    private static List<ActionProbability> parseActions(JsonArray array,JsonObject snapshot)throws IOException{
        // Worker action labels are localized here; the Node protocol carries only authoritative identities.
        if(array==null)throw new IOException("Missing action distribution");for(var value:array){var action=value.getAsJsonObject();action.addProperty("label",actionLabel(action.get("type").getAsString()));}
        List<ActionProbability> result;try{result=cachedActions(array);}catch(RuntimeException error){throw new IOException("Invalid action distribution",error);}
        if(result.size()!=snapshot.getAsJsonArray("legal").size())throw new IOException("Incomplete action distribution");
        for(var action:result){boolean found=false;for(var value:snapshot.getAsJsonArray("legal")){var legal=value.getAsJsonObject();String tile=legal.has("tile")?legal.get("tile").getAsString():null;if(action.index()==legal.get("index").getAsInt()&&action.type().equals(legal.get("type").getAsString())&&java.util.Objects.equals(action.tile(),tile))found=true;}if(!found)throw new IOException("Action distribution invented a legal action");}return result;
    }
    public static String actionLabel(String type){return switch(type){case "Discard"->"出牌";case "DiscardWithRiichi"->"立直";case "Chi"->"吃";case "Pon"->"碰";case "Daiminkan"->"大明杠";case "Ankan"->"暗杠";case "Kakan"->"加杠";case "DeclareKita"->"拔北";case "DeclareRon"->"荣和";case "DeclareTsumo"->"自摸";case "DeclareChankan"->"抢杠和";case "KyuushuAbort"->"九种九牌";case "Pass"->"跳过";case "Draw"->"摸牌";default->type;};}
    private static TheMahjongDriver decode(CompoundTag data){
        int count=data.getCompoundOrEmpty("match").getIntOr("playerCount",0);
        List<MahjongPlayerInterface> players=new ArrayList<>();for(int i=0;i<count;i++)players.add((request,delta)->Optional.empty());
        return DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,HolderLookup.Provider.create(Stream.empty()),data),players,new Random(0));
    }
    public static TheMahjongTile parseTile(String code){
        if(code==null||!code.matches("(?:[mps][0-9]|z[1-7])"))throw new IllegalArgumentException("Invalid analysis tile");int number=code.charAt(1)-'0';
        return switch(code.charAt(0)){case 'm'->new TheMahjongTile(TheMahjongTile.Suit.MANZU,number==0?5:number,number==0);case 'p'->new TheMahjongTile(TheMahjongTile.Suit.PINZU,number==0?5:number,number==0);case 's'->new TheMahjongTile(TheMahjongTile.Suit.SOUZU,number==0?5:number,number==0);default->new TheMahjongTile(number<=4?TheMahjongTile.Suit.WIND:TheMahjongTile.Suit.DRAGON,number<=4?number:number-4,false);};
    }
    /** Full precision consistency with the most probable discard kind; the best choice is 100. */
    public static double consistencyScore(double chosen,double maximum){
        if(!Double.isFinite(chosen)||!Double.isFinite(maximum)||chosen<0||maximum<=0||chosen>maximum+1e-12||maximum>1)
            throw new IllegalArgumentException("Invalid choice consistency probabilities");
        return Math.clamp(100*chosen/maximum,0,100);
    }
    /** Score/display identity: red and ordinary fives share one of the 34 tile kinds. */
    public static String tileKind(String tile){parseTile(tile);return tile.charAt(1)=='0'?tile.charAt(0)+"5":tile;}
    private static int kind(String tile){int n=tile.charAt(1)-'0';if(n==0)n=5;return switch(tile.charAt(0)){case 'm'->n-1;case 'p'->n+8;case 's'->n+17;default->n+26;};}
    private record PolicyResult(List<Candidate> candidates,List<ActionProbability> actions){}
    /** Largest-remainder percentages: the complete distribution always sums to exactly 100. */
    public static int[] largestRemainders(double[] probabilities){
        if(probabilities.length==0)return new int[0];double total=0;for(double p:probabilities){if(!Double.isFinite(p)||p<0)throw new IllegalArgumentException("Invalid policy weight");total+=p;}if(total<=0)throw new IllegalArgumentException("Empty policy mass");
        int[] result=new int[probabilities.length];double[] fractions=new double[probabilities.length];List<Integer> order=new ArrayList<>();int sum=0;
        for(int i=0;i<probabilities.length;i++){double percent=100*probabilities[i]/total;result[i]=(int)Math.floor(percent);fractions[i]=percent-result[i];sum+=result[i];order.add(i);}
        order.sort((a,b)->{int compared=Double.compare(fractions[b],fractions[a]);if(compared==0)compared=Double.compare(probabilities[b],probabilities[a]);return compared==0?Integer.compare(a,b):compared;});
        for(int i=0;i<100-sum;i++)result[order.get(i)]++;return result;
    }
    private static double[] softmax(double[] logits,int recommended){
        if(logits.length==0)return new double[0];double[] values=logits.clone();double max=java.util.Arrays.stream(values).max().orElseThrow();if(recommended>=0)values[recommended]=max+.5;
        max=java.util.Arrays.stream(values).max().orElseThrow();double[] probabilities=new double[values.length];double sum=0;for(int i=0;i<values.length;i++){probabilities[i]=Math.exp((values[i]-max)/.75);sum+=probabilities[i];}for(int i=0;i<values.length;i++)probabilities[i]/=sum;return probabilities;
    }
    /** The three-player live BOT has no native probabilities; model its legal actions using existing hand efficiency. */
    private static PolicyResult threePlayer(TheMahjongDriver driver,JsonObject snapshot){
        int[] hand=new int[34],visible=new int[34],copies=new int[34];Set<String> allowed=new java.util.LinkedHashSet<>();
        for(var tile:driver.match().tileSet().tiles())copies[kind(AiSnapshot.tile(tile))]+=driver.match().tileSet().copiesPerTile().get(tile);
        for(var tile:snapshot.getAsJsonArray("hand")){hand[kind(tile.getAsString())]++;visible[kind(tile.getAsString())]++;}
        for(var tile:snapshot.getAsJsonArray("doraIndicators"))visible[kind(tile.getAsString())]++;
        for(var river:snapshot.getAsJsonArray("rivers"))for(var value:river.getAsJsonArray()){var t=value.getAsJsonObject();if(!t.get("called").getAsBoolean())visible[kind(t.get("tile").getAsString())]++;}
        for(var melds:snapshot.getAsJsonArray("melds"))for(var meld:melds.getAsJsonArray())for(var tile:meld.getAsJsonObject().getAsJsonArray("tiles"))visible[kind(tile.getAsString())]++;
        int seat=snapshot.get("seat").getAsInt(),open=snapshot.getAsJsonArray("melds").get(seat).getAsJsonArray().size();
        for(var value:snapshot.getAsJsonArray("legal")){var a=value.getAsJsonObject();if(a.get("type").getAsString().startsWith("Discard"))allowed.add(tileKind(a.get("tile").getAsString()));}
        record Choice(String tile,int shanten,int ukeire,double value){}List<Choice> choices=new ArrayList<>();int min=99;
        for(String tile:allowed){int k=kind(tile);hand[k]--;int shanten=FallbackPolicy.shanten(hand,open),ukeire=ukeire(hand,open,copies,visible,shanten);hand[k]++;choices.add(new Choice(tile,shanten,ukeire,ukeire+1));min=Math.min(min,shanten);}
        final int baseline=min;double[] discardLogits=choices.stream().mapToDouble(c->Math.log1p(c.value()*Math.exp(-3*(c.shanten()-baseline)))).toArray();int best=-1;for(int i=0;i<discardLogits.length;i++)if(best<0||discardLogits[i]>discardLogits[best])best=i;
        double[] discardProbabilities=softmax(discardLogits,best);int[] discardIntegers=largestRemainders(discardProbabilities);List<Candidate> candidates=new ArrayList<>();List<Integer> order=new ArrayList<>();for(int i=0;i<choices.size();i++)order.add(i);order.sort((a,b)->Double.compare(discardProbabilities[b],discardProbabilities[a]));
        for(int rank=0;rank<order.size();rank++){int i=order.get(rank);var c=choices.get(i);candidates.add(new Candidate(c.tile(),discardIntegers[i],rank+1,discardProbabilities[i],c.value(),0));}
        var legal=driver.legalActions(seat);var request=new DecisionRequest(seat,driver.currentPhase(),legal,driver.currentRound(),driver.rules());PlayerAction bot=new com.themahjong.driver.bots.StupidActiveBot().chooseAction(request,1).orElse(null);int recommended=legal.indexOf(bot);
        JsonArray list=snapshot.getAsJsonArray("legal");double[] actionLogits=new double[list.size()];double maximum=java.util.Arrays.stream(discardLogits).max().orElse(1);int currentShanten=FallbackPolicy.shanten(hand,open);
        for(int i=0;i<list.size();i++){var action=list.get(i).getAsJsonObject();String type=action.get("type").getAsString();String tile=action.has("tile")?action.get("tile").getAsString():null;double logit=1;
            if(type.startsWith("Discard")){for(int j=0;j<choices.size();j++)if(choices.get(j).tile().equals(tileKind(tile)))logit=discardLogits[j];if(type.equals("DiscardWithRiichi"))logit+=.15;}
            else if(type.equals("DeclareRon")||type.equals("DeclareTsumo")||type.equals("DeclareChankan"))logit=maximum+2;
            else if(action.has("handTiles")){int[] next=hand.clone();for(var removed:action.getAsJsonArray("handTiles"))next[kind(removed.getAsString())]--;int nextOpen=open+1;int shanten=FallbackPolicy.shanten(next,nextOpen);double utility=(ukeire(next,nextOpen,copies,visible,shanten)+1)*Math.exp(-3*Math.max(0,shanten-currentShanten));logit=Math.log1p(utility);}
            else if(type.equals("Pass"))logit=Math.log1p(ukeire(hand,open,copies,visible,currentShanten)+1);
            else if(type.equals("KyuushuAbort"))logit=0;actionLogits[i]=logit;
        }
        double[] probabilities=softmax(actionLogits,recommended);int[] integers=largestRemainders(probabilities);List<ActionProbability> actions=new ArrayList<>();order=new ArrayList<>();for(int i=0;i<list.size();i++)order.add(i);order.sort((a,b)->Double.compare(probabilities[b],probabilities[a]));
        for(int rank=0;rank<order.size();rank++){int i=order.get(rank);var action=list.get(i).getAsJsonObject();String type=action.get("type").getAsString();actions.add(new ActionProbability(action.get("index").getAsInt(),type,action.has("tile")?action.get("tile").getAsString():null,actionLabel(type),integers[i],rank+1,probabilities[i]));}
        return new PolicyResult(List.copyOf(candidates),List.copyOf(actions));
    }
    private static int ukeire(int[] hand,int open,int[] copies,int[] visible,int shanten){int ukeire=0;for(int draw=0;draw<34;draw++){int left=Math.max(0,copies[draw]-visible[draw]);if(left==0||hand[draw]>=4)continue;hand[draw]++;if(FallbackPolicy.shanten(hand,open)<shanten)ukeire+=left;hand[draw]--;}return ukeire;}
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
