package com.themahjong.yaku;

import java.util.List;

public record WinResult(
        List<NonYakuman> yaku,
        List<Yakuman> yakuman,
        int han,
        int fu,
        int doraCount,
        List<Integer> pointDeltas
) {}
