package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

public class PredefinedKazoeYakumanTableRecordItem extends MahjongTableRecordSimpleEastHandItem {
    public PredefinedKazoeYakumanTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(2), m(2),
                m(3), m(3),
                m(4), m(4),
                m(5), m(5),
                m(6), m(6),
                m(7), m(7),
                m(8),
                m(8));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(m(7));
    }

    @Override
    protected TheMahjongTile doraIndicator() {
        return m(2);
    }
}
