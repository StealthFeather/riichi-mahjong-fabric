package com.riichimahjong.fabric;

import java.util.ArrayList;
import java.util.List;

/** The rendered meld and a replay marker share the exact same tile centres. */
final class MeldGeometry {
    record Placed(int meld,int index,PublicTable.Tile tile,double along,double height,double distance){}
    static List<Placed> arrange(List<PublicTable.Meld> melds){
        var out=new ArrayList<Placed>();double cursor=0;
        for(int m=0;m<melds.size();m++){
            var meld=melds.get(m);double width=meld.tiles().stream().filter(t->!t.stacked())
                    .mapToDouble(t->t.sideways()?.090:.073).sum();
            double offset=0,last=0;
            for(int i=0;i<meld.tiles().size();i++){
                var tile=meld.tiles().get(i);double step=tile.sideways()?.090:.073;
                double along=tile.stacked()?last:.90-cursor-width+offset+step/2;
                out.add(new Placed(m,i,tile,along,.59+(tile.stacked()?.058:0),.89));
                if(!tile.stacked()){last=along;offset+=step;}
            }
            cursor+=width+.020;
        }
        return List.copyOf(out);
    }
}
