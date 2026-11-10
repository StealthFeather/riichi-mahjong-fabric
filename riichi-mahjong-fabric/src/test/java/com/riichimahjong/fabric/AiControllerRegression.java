package com.riichimahjong.fabric;

import com.google.gson.JsonObject;
import com.riichimahjong.ai.AiRuntime;
import com.riichimahjong.ai.AiSnapshot;
import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.ai.FallbackPolicy;
import com.riichimahjong.ai.KobalabBot;
import com.themahjong.*;
import com.themahjong.driver.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** AI information boundary and open-hand fallback checks independent of a Minecraft server. */
public final class AiControllerRegression {
    static DecisionRequest request() {
        var hand = new ArrayList<>(waiting(0)); hand.add(tile(4,2));
        var d = driver(List.of(player(hand), player(waiting(1)), player(waiting(2)), player(waiting(0))), Collections.nCopies(20, tile(4,2)));
        return new DecisionRequest(0, d.currentPhase(), d.legalActions(0), d.currentRound(), d.rules());
    }

    static TheMahjongRound copy(TheMahjongRound r, List<TheMahjongPlayer> players, List<TheMahjongTile> wall, List<TheMahjongTile> rinshan, List<TheMahjongTile> dora, List<TheMahjongTile> ura) {
        return new TheMahjongRound(r.roundWind(),r.handNumber(),r.honba(),r.riichiSticks(),r.dealerSeat(),r.state(),r.currentTurnSeat(),r.claimSourceSeat().orElse(-1),r.activeTile(),wall,rinshan,dora,ura,r.revealedDoraCount(),r.pendingKanDoraReveals(),players,r.pendingDeltas());
    }

    private static void privacy() {
        var original = request(); var r = original.round();
        var players = new ArrayList<>(r.players());
        for (int seat = 1; seat < 4; seat++) players.set(seat, player(Collections.nCopies(13,tile(4,3))));
        var dora = new ArrayList<>(r.doraIndicators());
        for (int i = 1; i < dora.size(); i++) dora.set(i,tile(0,8));
        var changed = copy(r,players,Collections.nCopies(r.liveWall().size(),tile(1,8)),Collections.nCopies(4,tile(0,9)),dora,Collections.nCopies(5,tile(2,9)));
        var next = new DecisionRequest(0,original.phase(),original.legalActions(),changed,original.rules());
        JsonObject a = AiSnapshot.create(original,BotDifficulty.HARD), b = AiSnapshot.create(next,BotDifficulty.HARD);
        check(a.equals(b),"AI snapshot changed when only hidden opponent hands, wall order or unrevealed indicators changed");
        check(a.getAsJsonArray("doraIndicators").size()==1,"AI received unrevealed indicators");
        check(a.getAsJsonArray("hand").size()==14,"AI did not receive its own complete hand");
        check(!a.has("wall")&&!a.has("uraDoraIndicators")&&!a.has("hands"),"AI snapshot contains private zones");
        check(AiSnapshot.tile(new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true)).equals("m0"),"red five notation lost");
        check(AiSnapshot.tile(tile(4,1)).equals("z5"),"white dragon notation wrong");
    }

    private static void calledTile() {
        var request = request(); var r = request.round();
        var players = new ArrayList<>(r.players());
        var red = new TheMahjongTile(TheMahjongTile.Suit.PINZU,5,true); var normal = tile(1,5);
        players.set(1,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.RIICHI,false,waiting(1),List.of(),List.of(new TheMahjongDiscard(red,true)),List.of()));
        players.set(2,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,waiting(2),List.of(new TheMahjongMeld.Pon(List.of(normal,red,normal),1,1,0)),List.of(),List.of()));
        var round = copy(r,players,r.liveWall(),r.rinshanTiles(),r.doraIndicators(),r.uraDoraIndicators());
        var snapshot = AiSnapshot.create(new DecisionRequest(0,request.phase(),request.legalActions(),round,request.rules()),BotDifficulty.NORMAL);
        var discard = snapshot.getAsJsonArray("rivers").get(1).getAsJsonArray().get(0).getAsJsonObject();
        check(discard.get("called").getAsBoolean()&&discard.get("riichi").getAsBoolean(),"called riichi river tile not marked");
        var meld = snapshot.getAsJsonArray("melds").get(2).getAsJsonArray().get(0).getAsJsonObject();
        check(meld.get("claimedTileIndex").getAsInt()==1&&meld.get("calledTile").getAsString().equals("p0"),"called red identity lost");
    }

    private static void fallback() {
        int[] oneOpen = new int[34];
        for(int kind:new int[]{0,1,2,9,10,11,18,19,20,31,31})oneOpen[kind]++;
        check(FallbackPolicy.shanten(oneOpen,1)==-1,"fallback shanten ignores an open meld");
        oneOpen[31]--;
        check(FallbackPolicy.shanten(oneOpen,1)==0,"open tenpai incorrectly evaluated");
        int[] pairs = new int[34]; for(int i=0;i<7;i++)pairs[i]=2;
        check(FallbackPolicy.shanten(pairs,0)==-1,"seven pairs not recognized");
        var request = request(); var snapshot = AiSnapshot.create(request,BotDifficulty.EASY);
        int chosen = FallbackPolicy.chooseIndex(snapshot);
        check(chosen>=0&&chosen<request.legalActions().size(),"fallback action is not legal");
        var a = request.legalActions().get(chosen);
        check(a instanceof PlayerAction.DiscardWithRiichi riichi && riichi.tile().equals(tile(4,2)),"fallback failed to discard the isolated green dragon and declare legal riichi");
    }

    private static void lifecycle() {
        check(BotDifficulty.parse("hard")==BotDifficulty.HARD&&BotDifficulty.parse("old-invalid")==BotDifficulty.NORMAL,"difficulty compatibility wrong");
        check(BotDifficulty.EASY.next()==BotDifficulty.NORMAL&&BotDifficulty.HARD.next()==BotDifficulty.EASY,"difficulty cycling wrong");
        var original = request();
        var draw = new DecisionRequest(0,new MatchPhase.AwaitingDraw(0,false),List.of(PlayerAction.Draw.INSTANCE),original.round(),original.rules());
        var bot = new KobalabBot(BotDifficulty.NORMAL);
        long submitted = AiRuntime.metrics().get("submitted");
        check(bot.chooseAction(draw,.1).isEmpty(),"automatic draw did not preserve thinking delay");
        check(bot.chooseAction(draw,.25).orElseThrow() instanceof PlayerAction.Draw,"automatic draw did not finish");
        bot.close();
        check(bot.isClosed()&&bot.chooseAction(original,10).isEmpty(),"replaced controller can still submit an action");
        check(AiRuntime.metrics().get("submitted")==submitted,"automatic draw or closed bot launched the AI");
    }

    public static void main(String[] args) {
        privacy(); calledTile(); fallback(); lifecycle(); AiRuntime.shutdown();
        System.out.println("PASS: AI hidden-information boundary, red/called tiles, open-meld fallback, difficulty and controller lifecycle");
    }
}
