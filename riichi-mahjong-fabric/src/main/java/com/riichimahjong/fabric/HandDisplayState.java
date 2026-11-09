package com.riichimahjong.fabric;

import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Presentation only: retain the discarded tile's empty slot for ten ticks before sorting. */
public final class HandDisplayState {
    public static final long SORT_DELAY_TICKS = 10;
    private record RoundKey(TheMahjongTile.Wind wind,int hand,int honba,int dealer) {}
    private record PendingDiscard(TheMahjongRound round,int discards,int slot,TheMahjongTile tile) {}
    private static final class Seat {
        List<TheMahjongTile> order=List.of();
        List<Integer> slots=List.of(),gaps=List.of();
        int discards,nextSlot,separatedSlot=-1,wallSize,rinshanSize;
        long sortAt;
        boolean drawing;
        TheMahjongTile drawn;
        PendingDiscard pending;
    }
    private final Map<Integer,Seat> seats=new HashMap<>();
    private RoundKey key;
    private TheMahjongRound lastRound;
    private long lastTick=Long.MIN_VALUE;

    public void clear(){seats.clear();key=null;lastRound=null;lastTick=Long.MIN_VALUE;}

    public void update(TheMahjongRound round,long tick){
        if(lastRound==round&&lastTick==tick)return;
        var previousRound=lastRound;
        var nextKey=new RoundKey(round.roundWind(),round.handNumber(),round.honba(),round.dealerSeat());
        if(!nextKey.equals(key)){clear();key=nextKey;previousRound=null;}
        lastRound=round;lastTick=tick;
        for(int index=0;index<round.players().size();index++){
            var player=round.players().get(index);
            var canonical=round.handDisplayOrder(index);
            boolean drawing=index==round.currentTurnSeat()&&round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn;
            TheMahjongTile drawn=drawing?((TheMahjongRound.ActiveTile.Drawn)round.activeTile()).tile():null;
            var state=seats.get(index);
            if(state==null){
                state=new Seat();compact(state,canonical,drawing);state.discards=player.discards().size();
                rememberDraw(state,round,drawing,drawn);seats.put(index,state);continue;
            }
            int addedDiscards=player.discards().size()-state.discards;
            boolean newDraw=drawing&&(!state.drawing||addedDiscards>0||!drawn.equals(state.drawn)
                    ||round.liveWall().size()<state.wallSize||round.rinshanTiles().size()<state.rinshanSize);
            if(addedDiscards>0){
                state.sortAt=tick+SORT_DELAY_TICKS;
                for(int discard=state.discards;discard<player.discards().size();discard++){
                    var tile=player.discards().get(discard).tile();
                    int remove=-1;
                    var pending=state.pending;
                    if(addedDiscards==1&&pending!=null&&pending.round()==previousRound
                            &&pending.discards()==state.discards&&pending.tile().equals(tile)){
                        int selected=state.slots.indexOf(pending.slot());
                        if(selected>=0&&state.order.get(selected).equals(tile))remove=selected;
                    }
                    if(remove<0){
                        // A BOT action has no physical slot. Prefer the displayed draw when its identity matches.
                        int separated=state.slots.indexOf(state.separatedSlot);
                        remove=separated>=0&&state.order.get(separated).equals(tile)?separated:state.order.indexOf(tile);
                    }
                    if(remove>=0)removeAt(state,remove);
                }
                state.pending=null;
            }else if(state.pending!=null&&round!=state.pending.round())state.pending=null;
            state.discards=player.discards().size();
            if(round.state()==TheMahjongRound.State.ENDED||state.sortAt==0||tick>=state.sortAt){
                compact(state,canonical,drawing);state.sortAt=0;
            }else{
                var remainder=new ArrayList<>(canonical);
                if(newDraw)remainder.remove(drawn);
                reconcile(state,remainder);
                if(newDraw){
                    int slot=state.nextSlot++;
                    var order=new ArrayList<>(state.order);order.add(drawn);state.order=List.copyOf(order);
                    var slots=new ArrayList<>(state.slots);slots.add(slot);state.slots=List.copyOf(slots);
                    var gaps=new ArrayList<>(state.gaps);if(slot>0)gaps.add(slot);state.gaps=List.copyOf(gaps);
                    state.separatedSlot=slot;
                }
                if(!state.slots.contains(state.separatedSlot))state.separatedSlot=-1;
                int lastSlot=state.slots.isEmpty()?-1:state.slots.getLast();
                state.gaps=state.gaps.stream().filter(gap->gap<=lastSlot).toList();
            }
            rememberDraw(state,round,drawing,drawn);
        }
    }

    private static void compact(Seat state,List<TheMahjongTile> canonical,boolean drawing){
        if(state.pending!=null){
            // A queued discard can be accepted on the same tick an older hole expires.
            // Carry the selected equal-copy occurrence through that presentation-only sort.
            var pending=state.pending;int selected=state.slots.indexOf(pending.slot()),occurrence=0,rebased=-1;
            if(selected>=0&&state.order.get(selected).equals(pending.tile())){
                for(int index=0;index<selected;index++)if(state.order.get(index).equals(pending.tile()))occurrence++;
                for(int index=0;index<canonical.size();index++)if(canonical.get(index).equals(pending.tile())&&occurrence--==0){rebased=index;break;}
            }
            state.pending=rebased<0?null:new PendingDiscard(pending.round(),pending.discards(),rebased,pending.tile());
        }
        state.order=canonical;state.slots=IntStream.range(0,canonical.size()).boxed().toList();
        state.nextSlot=canonical.size();state.separatedSlot=drawing&&!canonical.isEmpty()?canonical.size()-1:-1;
        state.gaps=state.separatedSlot>0?List.of(state.separatedSlot):List.of();
    }

    private static void rememberDraw(Seat state,TheMahjongRound round,boolean drawing,TheMahjongTile drawn){
        state.drawing=drawing;state.drawn=drawn;state.wallSize=round.liveWall().size();state.rinshanSize=round.rinshanTiles().size();
    }

    private static void removeAt(Seat state,int index){
        var order=new ArrayList<>(state.order);order.remove(index);state.order=List.copyOf(order);
        var slots=new ArrayList<>(state.slots);int removed=slots.remove(index);state.slots=List.copyOf(slots);
        if(state.separatedSlot==removed)state.separatedSlot=-1;
    }

    /** Keep each surviving tile in its old physical slot; never fill a hole before the deadline. */
    private static void reconcile(Seat state,List<TheMahjongTile> current){
        var available=new ArrayList<>(current);var order=new ArrayList<TheMahjongTile>();var slots=new ArrayList<Integer>();
        for(int index=0;index<state.order.size();index++){
            var tile=state.order.get(index);int at=available.indexOf(tile);
            if(at>=0){order.add(tile);slots.add(state.slots.get(index));available.remove(at);}
        }
        for(var tile:available){order.add(tile);slots.add(state.nextSlot++);}
        state.order=List.copyOf(order);state.slots=List.copyOf(slots);
    }

    public List<TheMahjongTile> hand(int seat){return seats.get(seat).order;}
    public List<Integer> slots(int seat){return seats.get(seat).slots;}
    /** Each start adds one drawn-tile spacing increment to that slot and every slot to its right. */
    public List<Integer> gapStarts(int seat){return seats.get(seat).gaps;}
    public int separatedSlot(int seat){var state=seats.get(seat);return state==null?-1:state.separatedSlot;}
    public boolean separated(int seat){return separatedSlot(seat)>=0;}

    /** Call only after a displayed-slot click successfully queues an own-turn discard. */
    public void recordDiscardSlot(int seat,int displayedIndex){
        var state=seats.get(seat);
        if(state==null||displayedIndex<0||displayedIndex>=state.order.size())return;
        state.pending=new PendingDiscard(lastRound,state.discards,state.slots.get(displayedIndex),state.order.get(displayedIndex));
    }

    /** Translate the compact displayed-list index to the rule library's canonical tile index. */
    public int canonicalIndex(int seat,int displayed,TheMahjongRound round){
        var order=hand(seat);return displayed<0||displayed>=order.size()?-1:round.handDisplayOrder(seat).indexOf(order.get(displayed));
    }
}
