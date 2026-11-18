package com.riichimahjong.analysis;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.riichimahjong.ai.AiSnapshot;
import com.riichimahjong.ai.FallbackPolicy;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.PlayerAction;
import com.themahjong.variants.Jokers;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Local legal-action benchmark for rules unsupported by the four-player external engine. */
public final class LocalConsistencyPolicy {
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), task -> { var thread = new Thread(task,"riichi-analysis-local"); thread.setDaemon(true); return thread; });
    private LocalConsistencyPolicy() {}

    /** Explicit allowlist: own concealed hand, public tile counts and authoritative legal actions only. */
    public static JsonObject snapshot(DecisionRequest request) {
        var round=request.round();
        var out=new JsonObject();
        out.addProperty("playerCount",round.players().size());
        out.addProperty("seatWind",Math.floorMod(request.seat()-round.dealerSeat(),round.players().size()));
        out.addProperty("roundWind",round.roundWind().ordinal());
        out.addProperty("sichuan",request.rules().sichuan());
        out.addProperty("jokers",request.rules().jokersEnabled());
        var own=round.players().get(request.seat());
        out.addProperty("openMelds",own.melds().size());
        var hand=new JsonArray();
        int[] visible=new int[34];
        for(var tile:own.currentHand()) { hand.add(code(tile,request)); add(visible,tile,request); }
        out.add("hand",hand);
        Set<String> called=new HashSet<>();
        for(var player:round.players()) for(var meld:player.melds()) {
            var source=meld instanceof TheMahjongMeld.Kakan kan?kan.upgradedFrom():meld;
            if(source instanceof TheMahjongMeld.Chi chi) called.add(chi.sourceSeat()+":"+chi.sourceDiscardIndex());
            else if(source instanceof TheMahjongMeld.Pon pon) called.add(pon.sourceSeat()+":"+pon.sourceDiscardIndex());
            else if(source instanceof TheMahjongMeld.Daiminkan kan) called.add(kan.sourceSeat()+":"+kan.sourceDiscardIndex());
            for(var tile:meld.tiles())add(visible,tile,request);
        }
        for(int seat=0;seat<round.players().size();seat++) {
            var player=round.players().get(seat);
            for(int index=0;index<player.discards().size();index++)
                if(!called.contains(seat+":"+index))add(visible,player.discards().get(index).tile(),request);
            visible[30]+=player.kitaCount();
        }
        var indicators=new JsonArray();
        if(!request.rules().sichuan()) for(var tile:round.doraIndicators().subList(0,round.revealedDoraCount())) {
            indicators.add(code(tile,request)); add(visible,tile,request);
        }
        out.add("doraIndicators",indicators);
        var publicCounts=new JsonArray();for(int count:visible)publicCounts.add(count);
        out.add("visible",publicCounts);
        var legal=new JsonArray();
        for(int index=0;index<request.legalActions().size();index++) {
            var action=request.legalActions().get(index);var entry=new JsonObject();
            entry.addProperty("index",index);entry.addProperty("type",action.getClass().getSimpleName());
            if(action instanceof PlayerAction.Discard discard)entry.addProperty("tile",code(discard.tile(),request));
            else if(action instanceof PlayerAction.DiscardWithRiichi discard)entry.addProperty("tile",code(discard.tile(),request));
            legal.add(entry);
        }
        out.add("legal",legal);
        return out;
    }

    public static CompletableFuture<Integer> analyze(JsonObject snapshot) {
        var result=new CompletableFuture<Integer>();var immutable=snapshot.deepCopy();
        try { EXECUTOR.execute(()->{
            if(result.isDone())return;
            try { result.complete(chooseIndex(immutable)); }
            catch(RuntimeException error) { result.complete(-1); }
        }); } catch(RejectedExecutionException full) { result.complete(-1); }
        return result;
    }

    /** Minimize shanten, maximize visible remaining improving tiles, then retain valuable tiles. */
    public static int chooseIndex(JsonObject snapshot) {
        var legal=snapshot.getAsJsonArray("legal");
        for(var element:legal) {
            var action=element.getAsJsonObject();
            if(action.get("type").getAsString().equals("DeclareTsumo"))return action.get("index").getAsInt();
        }
        int[] hand=new int[34];
        for(var tile:snapshot.getAsJsonArray("hand"))if(!tile.getAsString().equals("joker"))hand[kind(tile.getAsString())]++;
        int open=snapshot.get("openMelds").getAsInt();
        int best=-1,bestShanten=Integer.MAX_VALUE,bestUkeire=-1;
        double bestValue=-Double.MAX_VALUE;
        boolean jokerRules=snapshot.get("jokers").getAsBoolean();
        for(var element:legal) {
            var action=element.getAsJsonObject();String type=action.get("type").getAsString();
            if(!type.equals("Discard")&&!type.equals("DiscardWithRiichi"))continue;
            String tile=action.get("tile").getAsString();
            int shanten,ukeire=0;double value;
            if(tile.equals("joker")) { shanten=100;value=-10000; }
            else {
                int discard=kind(tile);hand[discard]--;
                shanten=jokerRules?0:FallbackPolicy.shanten(hand,open);
                if(!jokerRules && shanten<=bestShanten)for(int draw=0;draw<34;draw++) {
                    if(snapshot.get("sichuan").getAsBoolean()&&draw>=27)continue;
                    if(snapshot.get("playerCount").getAsInt()==3 && draw>=1&&draw<=7)continue;
                    int remaining=Math.max(0,4-snapshot.getAsJsonArray("visible").get(draw).getAsInt());
                    if(remaining==0||hand[draw]>=4)continue;
                    hand[draw]++;
                    if(FallbackPolicy.shanten(hand,open)<shanten)ukeire+=remaining;
                    hand[draw]--;
                }
                value=-tileValue(tile,snapshot);
                if(jokerRules) {
                    // Wildcards lack a calibrated shanten engine. Retain pairs, sequences and flexible jokers.
                    value-=hand[discard]*4;
                    if(discard<27)for(int other=Math.max(0,discard-2);other<=Math.min(26,discard+2);other++)
                        if(other/9==discard/9 && other!=discard)value-=hand[other]*(Math.abs(other-discard)==1?2:1);
                }
                if(type.equals("DiscardWithRiichi"))value+=jokerRules?1000:.25;
                hand[discard]++;
            }
            if(shanten<bestShanten || shanten==bestShanten && (ukeire>bestUkeire || ukeire==bestUkeire && value>bestValue)) {
                best=action.get("index").getAsInt();bestShanten=shanten;bestUkeire=ukeire;bestValue=value;
            }
        }
        return best;
    }

    private static double tileValue(String tile,JsonObject snapshot) {
        int candidate=kind(tile);double value=tile.charAt(1)=='0'?3:0;
        if(candidate<27)value+=(5-Math.abs(candidate%9+1-5))*.01;
        if(candidate>=31||candidate==27+snapshot.get("roundWind").getAsInt()
                ||candidate==27+snapshot.get("seatWind").getAsInt())value+=.5;
        for(var indicator:snapshot.getAsJsonArray("doraIndicators")) {
            if(indicator.getAsString().equals("joker"))continue;
            int dora=kind(indicator.getAsString());
            dora=dora<27?dora/9*9+(dora%9+1)%9:dora<31?27+(dora-27+1)%4:31+(dora-31+1)%3;
            if(snapshot.get("playerCount").getAsInt()==3&&dora==1)dora=8;
            if(candidate==dora)value+=2;
        }
        return value;
    }

    private static boolean isJoker(TheMahjongTile tile,DecisionRequest request) {
        return request.rules().jokersEnabled() && (Jokers.isJoker(tile)
                || request.rules().sichuan() && tile.suit()==TheMahjongTile.Suit.DRAGON && tile.rank()==3);
    }
    private static String code(TheMahjongTile tile,DecisionRequest request) { return isJoker(tile,request)?"joker":AiSnapshot.tile(tile); }
    private static void add(int[] counts,TheMahjongTile tile,DecisionRequest request) { if(!isJoker(tile,request))counts[kind(AiSnapshot.tile(tile))]++; }
    private static int kind(String tile) {
        int rank=tile.charAt(1)-'0';if(rank==0)rank=5;
        return switch(tile.charAt(0)) { case 'm'->rank-1;case 'p'->rank+8;case 's'->rank+17;default->rank+26; };
    }
}
