package com.riichimahjong.fabric;

import java.util.*;
import com.riichimahjong.mahjongtable.*;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.themahjong.*;
import com.themahjong.driver.MatchPhase;

/** The sole allowlist passed to the public tile renderer. No round, wall tiles, seed or legal actions. */
public record PublicTable(List<Seat> seats,int liveWall,int rinshanDrawn,List<Integer> dora,List<Integer> ura,
                          String label,MatchPhase.Stage stage,double progress) {
    public record Tile(int code,boolean sideways){}
    public record Meld(List<Tile> tiles){}
    public record Seat(UUID owner,String name,int points,int handCount,List<Integer> revealed,List<Tile> river,List<Meld> melds,int kita,boolean riichi){}
    static int code(TheMahjongTile tile){return MahjongTileItems.codeForTile(tile);}
    static Set<Integer> claimed(List<TheMahjongPlayer> players,int seat){
        Set<Integer> out=new HashSet<>();
        for(var player:players)for(var meld:player.melds()){
            int source=-1,index=-1;
            switch(meld){
                case TheMahjongMeld.Chi m->{source=m.sourceSeat();index=m.sourceDiscardIndex();}
                case TheMahjongMeld.Pon m->{source=m.sourceSeat();index=m.sourceDiscardIndex();}
                case TheMahjongMeld.Daiminkan m->{source=m.sourceSeat();index=m.sourceDiscardIndex();}
                case TheMahjongMeld.Kakan m->{source=m.upgradedFrom().sourceSeat();index=m.upgradedFrom().sourceDiscardIndex();}
                default->{}
            }if(source==seat)out.add(index);
        }return out;
    }
    public static PublicTable capture(MahjongTableBlockEntity table){
        var driver=table.driver();var round=driver==null?null:driver.match().currentRound().orElse(null);
        var infos=table.seats();var out=new ArrayList<Seat>();
        MatchPhase phase=driver==null?null:driver.currentPhase();
        Set<Integer> winners=new HashSet<>();boolean ura=false;
        if(phase instanceof MatchPhase.RoundEnded ended&&!ended.winResults().isEmpty()&&round!=null){
            for(var win:ended.winResults())for(int i=0;i<win.pointDeltas().size();i++)if(win.pointDeltas().get(i)>0){winners.add(i);if(round.players().get(i).riichi())ura=true;}
        }
        for(int i=0;i<infos.size();i++){
            var info=infos.get(i);UUID owner=info.occupant().orElse(null);var player=owner==null?null:table.getLevel().getPlayerByUUID(owner);
            String name=player==null?(owner==null?(round==null?"空位":"BOT"):"离线玩家"):player.getName().getString();
            if(round==null||i>=round.players().size()){out.add(new Seat(owner,info.enabled()?name:"关闭",0,0,List.of(),List.of(),List.of(),0,false));continue;}
            var p=round.players().get(i);var river=new ArrayList<Tile>();Set<Integer> claimed=claimed(round.players(),i);
            for(int d=0;d<p.discards().size();d++)if(!claimed.contains(d)){var tile=p.discards().get(d);river.add(new Tile(code(tile.tile()),tile.riichiDeclared()));}
            var melds=new ArrayList<Meld>();for(var meld:p.melds()){
                var tiles=new ArrayList<Tile>();for(int t=0;t<meld.tiles().size();t++)tiles.add(new Tile(code(meld.tiles().get(t)),meld.sidewaysTileIndex().orElse(-1)==t));
                melds.add(new Meld(List.copyOf(tiles)));
            }
            // Read a concealed identity only for a winner whose result has already been accepted.
            List<Integer> revealed=winners.contains(i)?round.handDisplayOrder(i).stream().map(PublicTable::code).toList():List.of();
            int count=p.currentHand().size(); // The public value is only the count.
            out.add(new Seat(owner,name,p.points(),count,revealed,List.copyOf(river),List.copyOf(melds),p.kitaCount(),p.riichi()));
        }
        String label=round==null?"立直麻将 · 右键桌边或座位文字入座\n潜行右键中央开局 · /mahjong join 入座\n扳手右键中央设置":round.roundWind()+" "+round.handNumber()+"局  "+round.honba()+"本场  供托 "+round.riichiSticks()+"  余牌 "+round.liveWall().size();
        if(round==null)for(int i=0;i<out.size();i++)label+="\n"+TableSeating.wind(i)+"家 · "+out.get(i).name();
        if(round!=null){for(int i=0;i<out.size();i++){var p=out.get(i);label+="\n"+(round.currentTurnSeat()==i?"▶ ":"  ")+p.name()+" "+p.points()+(p.riichi()?" 立直":"");}}
        if(phase instanceof MatchPhase.RoundEnded ended){
            if(ended.winResults().isEmpty())label+="\n流局";
            for(var w:ended.winResults()){
                label+="\n"+w.han()+"番 "+w.fu()+"符  宝牌 "+w.doraCount();
                int limit=table.resultAnimStage()==MahjongTableBlockEntity.ResultAnimStage.SHOW_HEADER?0:table.resultAnimStage()==MahjongTableBlockEntity.ResultAnimStage.SHOW_YAKU_LINES?table.resultAnimYakuIdx()+1:Integer.MAX_VALUE;
                var yaku=new ArrayList<String>();w.yaku().forEach(y->yaku.add(y.toString()));w.yakuman().forEach(y->yaku.add(y.toString()));
                for(int j=0;j<Math.min(limit,yaku.size());j++)label+="\n"+yaku.get(j);
                if(table.resultAnimStage()==MahjongTableBlockEntity.ResultAnimStage.AWAITING_ADVANCE)label+="\n点击中央继续下一局";
            }
        }
        var dora=round==null?List.<Integer>of():round.doraIndicators().stream().limit(round.revealedDoraCount()).map(PublicTable::code).toList();
        var uras=round!=null&&ura?round.uraDoraIndicators().stream().limit(round.revealedDoraCount()).map(PublicTable::code).toList():List.<Integer>of();
        var stage=phase instanceof MatchPhase.Dealing d?d.stage():null;double progress=phase instanceof MatchPhase.Dealing d?Math.min(1,d.elapsed()/d.stage().buildDuration()):1;
        return new PublicTable(List.copyOf(out),round==null?0:round.liveWall().size(),round==null?0:Math.max(0,4-round.rinshanTiles().size()),dora,uras,label,stage,progress);
    }
}
