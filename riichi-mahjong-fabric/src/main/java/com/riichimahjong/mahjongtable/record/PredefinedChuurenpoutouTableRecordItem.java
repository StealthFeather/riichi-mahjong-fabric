// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.mahjongtable.record;

import com.themahjong.TheMahjongTile;
import java.util.List;

public class PredefinedChuurenpoutouTableRecordItem extends MahjongTableRecordSimpleEastHandItem {
    public PredefinedChuurenpoutouTableRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    protected List<TheMahjongTile> handTiles() {
        return List.of(
                m(1), m(1), m(1),
                m(2), m(3), m(4), m(5), m(6), m(7), m(8),
                m(9), m(9), m(9),
                w(TheMahjongTile.Wind.EAST));
    }

    @Override
    protected List<TheMahjongTile> wallTiles() {
        return List.of(m(1), m(5));
    }
}
