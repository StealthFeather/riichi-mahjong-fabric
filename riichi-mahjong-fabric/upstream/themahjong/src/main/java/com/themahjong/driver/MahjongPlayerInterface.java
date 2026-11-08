package com.themahjong.driver;

import java.util.Optional;

public interface MahjongPlayerInterface {
    Optional<PlayerAction> chooseAction(DecisionRequest request, double deltaSeconds);

    default void onEvent(MatchEvent event) {}
}
