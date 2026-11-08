package com.riichimahjong.fabric;

import com.themahjong.*;
import java.util.*;

/** Presentation order is relative to the caller, independent of the tile's sorted index. */
final class MeldLayout {
    record Tile(TheMahjongTile tile,boolean sideways,boolean stacked,boolean concealed){}
    static List<Tile> arrange(TheMahjongMeld meld,int seat,int playerCount){
        if(meld instanceof TheMahjongMeld.Ankan){
            var out=new ArrayList<Tile>();
            for(int i=0;i<4;i++)out.add(new Tile(meld.tiles().get(i),false,false,i==0||i==3));
            return List.copyOf(out);
        }
        TheMahjongMeld base=meld instanceof TheMahjongMeld.Kakan k?k.upgradedFrom():meld;
        int source=switch(base){case TheMahjongMeld.Chi m->m.sourceSeat();case TheMahjongMeld.Pon m->m.sourceSeat();case TheMahjongMeld.Daiminkan m->m.sourceSeat();default->throw new IllegalArgumentException("not an open meld");};
        int relative=Math.floorMod(source-seat,playerCount);
        int target=relative==playerCount-1?0:relative==1?base.tiles().size()-1:1;
        var ordered=new ArrayList<>(base.tiles());
        var called=ordered.remove(base.sidewaysTileIndex().orElseThrow());ordered.add(target,called);
        var out=new ArrayList<Tile>();
        for(int i=0;i<ordered.size();i++){
            out.add(new Tile(ordered.get(i),i==target,false,false));
            if(i==target&&meld instanceof TheMahjongMeld.Kakan k)out.add(new Tile(k.addedTile(),true,true,false));
        }
        return List.copyOf(out);
    }
}
