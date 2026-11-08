package com.themahjong.driver;

import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongRuleSet;

import java.util.List;

public record DecisionRequest(
        int seat,
        MatchPhase phase,
        List<PlayerAction> legalActions,
        TheMahjongRound round,
        TheMahjongRuleSet rules) {
    public DecisionRequest {
        if (legalActions == null) throw new IllegalArgumentException("legalActions cannot be null");
        if (legalActions.isEmpty()) throw new IllegalArgumentException("legalActions cannot be empty");
        if (round == null) throw new IllegalArgumentException("round cannot be null");
        if (rules == null) throw new IllegalArgumentException("rules cannot be null");
        if (phase == null) throw new IllegalArgumentException("phase cannot be null");
        legalActions = List.copyOf(legalActions);
    }
}
