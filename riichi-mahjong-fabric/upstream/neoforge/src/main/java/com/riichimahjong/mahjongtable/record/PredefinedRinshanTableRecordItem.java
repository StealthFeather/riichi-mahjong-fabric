package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

public class PredefinedRinshanTableRecordItem extends MahjongTableRecordSimpleEastHandItem {
    public PredefinedRinshanTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(1), m(1), m(1),
                p(2), p(3), p(4), p(5), p(6), p(7), p(8), p(9),
                w(TheMahjongTile.Wind.EAST),
                w(TheMahjongTile.Wind.EAST));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of();
    }

    @Override
    protected List<TheMahjongTile> rinshanTiles() {
        return List.of(p(7));
    }
}
