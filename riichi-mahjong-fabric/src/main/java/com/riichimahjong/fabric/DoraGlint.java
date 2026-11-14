package com.riichimahjong.fabric;

import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.themahjong.TheMahjongRound;
import com.themahjong.driver.MatchPhase;
import com.themahjong.yaku.DoraIndicators;
import java.util.HashSet;
import java.util.Set;

/** Only publicly revealed indicators may affect tile appearance. */
final class DoraGlint {
    private DoraGlint() {}

    static boolean uraVisible(TheMahjongRound round, MatchPhase phase) {
        if (round == null || !(phase instanceof MatchPhase.RoundEnded ended)) return false;
        for (var win : ended.winResults()) {
            for (int seat = 0; seat < round.players().size(); seat++) {
                if (win.pointDeltas().get(seat) > 0 && round.players().get(seat).riichi()) return true;
            }
        }
        return false;
    }

    static Set<Integer> tiles(TheMahjongRound round, MatchPhase phase) {
        if (round == null) return Set.of();
        var codes = new HashSet<>(Set.of(MahjongTileItems.CODE_MAN_5_AKA,
                MahjongTileItems.CODE_PIN_5_AKA, MahjongTileItems.CODE_SOU_5_AKA));
        if (!(phase instanceof MatchPhase.Dealing dealing)
                || dealing.stage() != MatchPhase.Stage.WALL_BUILDING) {
            int count = round.revealedDoraCount();
            for (var indicator : round.doraIndicators().subList(0, count)) {
                codes.add(MahjongTileItems.codeForTile(DoraIndicators.dora(indicator, round.players().size())));
            }
            if (uraVisible(round, phase)) {
                for (var indicator : round.uraDoraIndicators().subList(0, count)) {
                    codes.add(MahjongTileItems.codeForTile(DoraIndicators.dora(indicator, round.players().size())));
                }
            }
        }
        return Set.copyOf(codes);
    }
}
