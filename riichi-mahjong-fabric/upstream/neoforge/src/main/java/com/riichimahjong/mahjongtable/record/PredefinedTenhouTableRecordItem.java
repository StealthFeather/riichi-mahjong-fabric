package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

public class PredefinedTenhouTableRecordItem extends MahjongTableRecordSimpleEastHandItem {
    public PredefinedTenhouTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(2), m(3),
                p(1), p(2), p(3),
                s(1), s(2), s(3),
                s(7), s(8), s(9),
                p(5),
                p(5));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of();
    }

    @Override
    protected boolean breakFirstRoundUninterrupted() {
        return false;
    }
}
