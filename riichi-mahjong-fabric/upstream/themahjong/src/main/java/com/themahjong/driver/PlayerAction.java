package com.themahjong.driver;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.WinResult;

import java.util.List;

public sealed interface PlayerAction
        permits PlayerAction.Draw, PlayerAction.Discard, PlayerAction.DiscardWithRiichi,
                PlayerAction.Chi, PlayerAction.Pon, PlayerAction.Daiminkan,
                PlayerAction.Ankan, PlayerAction.Kakan, PlayerAction.DeclareKita,
                PlayerAction.DeclareTsumo, PlayerAction.DeclareRon, PlayerAction.DeclareChankan,
                PlayerAction.KyuushuAbort, PlayerAction.Pass {
    record Draw() implements PlayerAction {
        public static final Draw INSTANCE = new Draw();
    }

    record Discard(TheMahjongTile tile) implements PlayerAction {}

    record DiscardWithRiichi(TheMahjongTile tile) implements PlayerAction {}

    record Chi(ChiShape shape, List<TheMahjongTile> handTiles) implements PlayerAction {}

    enum ChiShape { RYANMEN_LOW, RYANMEN_HIGH, KANCHAN, PENCHAN }

    record Pon(List<TheMahjongTile> handTiles) implements PlayerAction {}

    record Daiminkan(List<TheMahjongTile> handTiles) implements PlayerAction {}

    record Ankan(List<TheMahjongTile> handTiles) implements PlayerAction {}

    record Kakan(TheMahjongMeld.Pon upgradedFrom, TheMahjongTile addedTile) implements PlayerAction {}

    record DeclareKita(TheMahjongTile tile) implements PlayerAction {}

    record DeclareTsumo(WinResult result) implements PlayerAction {}

    record DeclareRon(WinResult result) implements PlayerAction {}

    record DeclareChankan(WinResult result) implements PlayerAction {}

    record KyuushuAbort() implements PlayerAction {
        public static final KyuushuAbort INSTANCE = new KyuushuAbort();
    }

    record Pass() implements PlayerAction {
        public static final Pass INSTANCE = new Pass();
    }
}
