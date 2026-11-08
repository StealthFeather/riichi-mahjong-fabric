package com.themahjong.yaku;

import com.themahjong.TheMahjongPlayer.RiichiState;
import com.themahjong.TheMahjongTile;

public record WinContext(WinType winType, boolean dealer, boolean uninterruptedFirstRound,
                         RiichiState riichiState, boolean ippatsuEligible,
                         TheMahjongTile winningTile,
                         TheMahjongTile.Wind seatWind, TheMahjongTile.Wind roundWind,
                         boolean lastTile, boolean rinshanDraw) {
    public enum WinType { TSUMO, RON, CHANKAN }

    public boolean tsumo() { return winType == WinType.TSUMO; }

    public static WinContext tsumo(boolean dealer, boolean uninterruptedFirstRound,
                                   RiichiState riichiState, boolean ippatsuEligible,
                                   TheMahjongTile winningTile,
                                   TheMahjongTile.Wind seatWind, TheMahjongTile.Wind roundWind,
                                   boolean lastTile, boolean rinshanDraw) {
        return new WinContext(WinType.TSUMO, dealer, uninterruptedFirstRound, riichiState, ippatsuEligible,
                winningTile, seatWind, roundWind, lastTile, rinshanDraw);
    }

    public static WinContext ron(boolean dealer, boolean uninterruptedFirstRound,
                                 RiichiState riichiState, boolean ippatsuEligible,
                                 TheMahjongTile winningTile,
                                 TheMahjongTile.Wind seatWind, TheMahjongTile.Wind roundWind,
                                 boolean lastTile, boolean rinshanDraw) {
        return new WinContext(WinType.RON, dealer, uninterruptedFirstRound, riichiState, ippatsuEligible,
                winningTile, seatWind, roundWind, lastTile, rinshanDraw);
    }

    public static WinContext chankan(boolean dealer, boolean uninterruptedFirstRound,
                                     RiichiState riichiState, boolean ippatsuEligible,
                                     TheMahjongTile winningTile,
                                     TheMahjongTile.Wind seatWind, TheMahjongTile.Wind roundWind,
                                     boolean lastTile) {
        return new WinContext(WinType.CHANKAN, dealer, uninterruptedFirstRound, riichiState, ippatsuEligible,
                winningTile, seatWind, roundWind, lastTile, false);
    }
}
