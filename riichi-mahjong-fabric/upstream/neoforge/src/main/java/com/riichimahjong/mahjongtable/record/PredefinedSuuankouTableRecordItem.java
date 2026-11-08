package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

public class PredefinedSuuankouTableRecordItem extends MahjongTableRecordSimpleEastHandItem {
    public PredefinedSuuankouTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(1), m(1),
                m(2), m(2), m(2),
                m(3), m(3), m(3),
                m(4), m(4), m(4),
                m(5),
                m(6));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(m(5), m(6));
    }
}
