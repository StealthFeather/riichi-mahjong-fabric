package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

public class PredefinedChankanTableRecordItem extends MahjongTableRecordSimpleEastHandItem {
    public PredefinedChankanTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(4), m(4), m(4),
                m(5), m(5), m(5),
                m(6), m(6), m(6),
                m(7), m(8),
                p(7), p(7),
                s(4));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(s(5), m(9), s(5), s(5), s(5), m(9));
    }
}
