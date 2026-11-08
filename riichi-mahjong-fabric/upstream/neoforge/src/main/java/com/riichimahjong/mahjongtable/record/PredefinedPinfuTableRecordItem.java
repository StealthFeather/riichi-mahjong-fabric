package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

public class PredefinedPinfuTableRecordItem extends MahjongTableRecordSimpleEastHandItem {
    public PredefinedPinfuTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(2), m(3), m(4), m(5), m(6),
                p(3), p(4),
                p(6), p(7), p(8),
                s(2), s(2),
                w(TheMahjongTile.Wind.NORTH));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(
                w(TheMahjongTile.Wind.NORTH),
                w(TheMahjongTile.Wind.NORTH),
                w(TheMahjongTile.Wind.WEST),
                p(5));
    }
}
