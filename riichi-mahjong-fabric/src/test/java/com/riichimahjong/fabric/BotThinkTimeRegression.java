package com.riichimahjong.fabric;

import com.riichimahjong.ai.BotDifficulty;
import com.riichimahjong.ai.BotThinkTime;
import com.riichimahjong.ai.KobalabBot;
import com.riichimahjong.ai.RandomThinkingBot;
import com.themahjong.driver.*;
import com.themahjong.yaku.WinResult;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

public final class BotThinkTimeRegression {
    private static DecisionRequest request(PlayerAction action){
        var original=AiControllerRegression.request();
        return new DecisionRequest(0,original.phase(),List.of(action),original.round(),original.rules());
    }
    private static void boundaries(){
        var draw=request(PlayerAction.Draw.INSTANCE);var pass=request(PlayerAction.Pass.INSTANCE);
        int[] samples={0};
        var shortest=new BotThinkTime(()->{samples[0]++;return 0;});
        check(!shortest.ready(draw,.499),"action before 0.5 seconds");
        check(shortest.ready(draw,.001),"minimum endpoint exceeded");
        check(samples[0]==1,"delay was resampled while polling a decision");
        check(!shortest.ready(pass,.499),"old decision's elapsed time carried into a new request");
        check(shortest.ready(pass,.001)&&samples[0]==2,"new request did not get its own delay");
        var longest=new BotThinkTime(()->1);
        check(!longest.ready(draw,1.499)&&longest.ready(draw,.001),"maximum endpoint is not 1.5 seconds");
        check(!longest.ready(pass,Double.NaN)&&!longest.ready(pass,Double.POSITIVE_INFINITY)
                &&!longest.ready(pass,-10),"invalid delta bypassed thinking time");
        check(!longest.ready(pass,1.49)&&longest.ready(pass,.01),"invalid delta changed elapsed time");
    }
    private static void controllers(){
        var result=new WinResult(List.of(),List.of(),1,30,0,List.of(1000,-1000,0,0));
        var tile=tile(4,1);
        var actions=List.<PlayerAction>of(PlayerAction.Draw.INSTANCE,new PlayerAction.Discard(tile),PlayerAction.Pass.INSTANCE,
                new PlayerAction.DiscardWithRiichi(tile),new PlayerAction.DeclareTsumo(result),new PlayerAction.DeclareRon(result),
                new PlayerAction.DeclareChankan(result),new PlayerAction.Pon(List.of(tile,tile)));
        var bots=new ArrayList<MahjongPlayerInterface>();
        for(var difficulty:BotDifficulty.values())bots.add(new KobalabBot(difficulty));
        bots.add(new RandomThinkingBot());
        for(var bot:bots){
            var observed=new HashSet<Integer>();
            for(int repetition=0;repetition<5;repetition++)for(var action:actions){
                var request=request(action);
                check(bot.chooseAction(request,.499).isEmpty(),"controller submitted "+action+" before half a second");
                int step=0;
                for(;step<=100;step++){
                    var selected=bot.chooseAction(request,.01);
                    if(selected.isPresent()){
                        check(selected.get().equals(action),"timing changed the selected action");break;
                    }
                }
                check(step<=100,"controller exceeded 1.5 seconds with a ready decision");observed.add(step);
            }
            check(observed.size()>1,"controller reused a fixed thinking delay");
            if(bot instanceof KobalabBot ai)ai.close();
        }
    }
    public static void main(String[] args){
        boundaries();controllers();
        System.out.println("PASS: each AI difficulty and three-player BOT randomize every decision within 0.5–1.5 seconds; endpoints, invalid delta and phase resets match");
    }
}
