package com.riichimahjong.ai;

import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.PlayerAction;
import com.themahjong.driver.bots.StupidActiveBot;
import java.util.Optional;

/** Keep the original three-player policy, with the same random timing as four-player AI. */
public final class RandomThinkingBot implements MahjongPlayerInterface {
    private final StupidActiveBot policy=new StupidActiveBot();
    private final BotThinkTime thinking=new BotThinkTime();
    @Override public Optional<PlayerAction> chooseAction(DecisionRequest request,double deltaSeconds){
        if(request.legalActions().isEmpty())return Optional.empty();
        boolean ready=thinking.ready(request,deltaSeconds);
        var action=policy.chooseAction(request,0);
        return ready?action:Optional.empty();
    }
}
