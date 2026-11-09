package com.riichimahjong.fabric;

import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Presentation only: leave the drawn tile on the right for twenty ticks after discarding. */
public final class HandDisplayState {
    public static final long SORT_DELAY_TICKS = 20;
    private record RoundKey(TheMahjongTile.Wind wind,int hand,int honba,int dealer) {}
    private static final class Seat {
        List<TheMahjongTile> order = List.of();
        int discards;
        long sortAt;
        TheMahjongTile separated;
    }
    private final Map<Integer,Seat> seats = new HashMap<>();
    private RoundKey key;
    private TheMahjongRound lastRound;
    private long lastTick=Long.MIN_VALUE;

    public void clear(){seats.clear();key=null;lastRound=null;lastTick=Long.MIN_VALUE;}

    public void update(TheMahjongRound round,long tick){
        if(lastRound==round&&lastTick==tick)return;
        var nextKey=new RoundKey(round.roundWind(),round.handNumber(),round.honba(),round.dealerSeat());
        if(!nextKey.equals(key)){clear();key=nextKey;}
        lastRound=round;lastTick=tick;
        for(int index=0;index<round.players().size();index++){
            var player=round.players().get(index);
            var canonical=round.handDisplayOrder(index);
            var state=seats.get(index);
            boolean drawing=index==round.currentTurnSeat()&&round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn;
            TheMahjongTile drawn=drawing?((TheMahjongRound.ActiveTile.Drawn)round.activeTile()).tile():null;
            if(state==null){
                state=new Seat();state.order=canonical;state.discards=player.discards().size();state.separated=drawn;seats.put(index,state);continue;
            }
            if(player.discards().size()>state.discards){
                state.sortAt=tick+SORT_DELAY_TICKS;
                var discarded=player.discards().getLast().tile();
                // Equal copies have the same legal action. Treat a discard of the drawn identity as tsumogiri.
                if(discarded.equals(state.separated))state.separated=null;
                state.order=reconcile(state.order,canonical);
            }
            state.discards=player.discards().size();
            if(round.state()==TheMahjongRound.State.ENDED||tick>=state.sortAt){
                state.order=canonical;state.separated=drawn;state.sortAt=0;
            }else{
                state.order=reconcile(state.order,canonical);
                if(drawn!=null){state.separated=drawn;state.order=moveLast(state.order,drawn);}
                if(state.separated!=null&&(state.order.isEmpty()||!state.order.getLast().equals(state.separated)))state.separated=null;
            }
        }
    }

    /** Preserve surviving tiles in their previous order; new draws are appended at the right. */
    private static List<TheMahjongTile> reconcile(List<TheMahjongTile> previous,List<TheMahjongTile> current){
        var available=new ArrayList<>(current);var out=new ArrayList<TheMahjongTile>();
        for(var tile:previous){int at=available.indexOf(tile);if(at>=0){out.add(tile);available.remove(at);}}
        out.addAll(available);return List.copyOf(out);
    }

    private static List<TheMahjongTile> moveLast(List<TheMahjongTile> order,TheMahjongTile tile){
        var out=new ArrayList<>(order);int at=out.lastIndexOf(tile);if(at>=0)out.add(out.remove(at));return List.copyOf(out);
    }

    public List<TheMahjongTile> hand(int seat){return seats.get(seat).order;}
    public boolean separated(int seat){var state=seats.get(seat);return state!=null&&state.separated!=null;}

    /** Translate the displayed slot to the rule library's sorted slot without changing any rule state. */
    public int canonicalIndex(int seat,int displayed,TheMahjongRound round){
        var order=hand(seat);return displayed<0||displayed>=order.size()?-1:round.handDisplayOrder(seat).indexOf(order.get(displayed));
    }
}
