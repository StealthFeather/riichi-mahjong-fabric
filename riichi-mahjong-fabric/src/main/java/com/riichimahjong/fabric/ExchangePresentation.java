package com.riichimahjong.fabric;

import com.themahjong.TheMahjongTile;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.sichuan.SichuanState;
import com.themahjong.variants.Jokers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Transient display of an already committed simultaneous exchange; never a second rules mutation. */
public final class ExchangePresentation {
    public static final int DURATION_TICKS = 52;
    public static final int LIFT_END = 10;
    public static final int TRAVEL_END = 42;

    /** Exact physical identity stays server-side and is routed by the renderer's private audience. */
    public record Flight(int sourceSeat, int sourceIndex, int targetSeat, int targetIndex,
                         TheMahjongTile tile, boolean exchanged) {}

    public static final class Session {
        private final int playerCount, direction;
        private final boolean sichuan;
        private final List<List<TheMahjongTile>> before, after;
        private final List<Flight> flights;
        private int elapsedTicks;

        private Session(int playerCount, int direction, boolean sichuan,
                List<List<TheMahjongTile>> before, List<List<TheMahjongTile>> after, List<Flight> flights) {
            this.playerCount=playerCount; this.direction=direction; this.sichuan=sichuan;
            this.before=before.stream().map(List::copyOf).toList();
            this.after=after.stream().map(List::copyOf).toList(); this.flights=List.copyOf(flights);
        }
        public int playerCount() { return playerCount; }
        public int direction() { return direction; }
        public boolean sichuan() { return sichuan; }
        public int elapsedTicks() { return elapsedTicks; }
        public List<TheMahjongTile> beforeOrder(int seat) { return before.get(seat); }
        public List<TheMahjongTile> afterOrder(int seat) { return after.get(seat); }
        public List<Flight> flights() { return flights; }
        public boolean advance() { return ++elapsedTicks >= DURATION_TICKS; }
        public String directionLabel() { return ExchangePresentation.directionLabel(playerCount,direction); }
    }

    private ExchangePresentation() {}

    public static boolean pending(TheMahjongDriver driver) {
        return driver!=null && (driver.exchangeInProgress() || driver.sichuanState()!=null
                && driver.sichuanState().stage()==SichuanState.Stage.EXCHANGE);
    }

    /** Call only when an actual pending exchange has just completed, never while restoring a saved hand. */
    public static Session captureAfterExchange(TheMahjongDriver driver) {
        if(driver==null || pending(driver) || driver.match().currentRound().isEmpty()) return null;
        boolean sichuan=driver.sichuanState()!=null;
        int count=driver.match().playerCount(), direction;
        List<List<TheMahjongTile>> selections;
        if(sichuan) {
            var saved=driver.sichuanState().snapshot();
            if(!driver.rules().exchangeThree() || saved.stage()==SichuanState.Stage.EXCHANGE
                    || saved.confirmed().size()!=count) return null;
            direction=saved.exchangeDirection(); selections=saved.selections();
        } else {
            var state=driver.exchangeState();
            if(state==null || !state.complete()) return null;
            var saved=state.snapshot(); direction=saved.direction(); selections=saved.selections();
        }
        return create(count,direction,sichuan,driver.rules().jokersEnabled(),selections,
                driver.currentRound().players().stream().map(player->player.currentHand()).toList());
    }

    /** Reconstruct pre-exchange hands from the accepted selections and final hand, preserving duplicates. */
    static Session create(int count,int direction,boolean sichuan,boolean jokers,
            List<List<TheMahjongTile>> selections,List<List<TheMahjongTile>> acceptedHands) {
        if(count<3 || count>4 || direction<1 || direction>=count || selections.size()!=count
                || acceptedHands.size()!=count) throw new IllegalArgumentException("invalid exchange presentation");
        var before=new ArrayList<List<TheMahjongTile>>();
        var after=new ArrayList<List<TheMahjongTile>>();
        for(int seat=0;seat<count;seat++) {
            if(selections.get(seat).size()!=3 || acceptedHands.get(seat).size()!=13)
                throw new IllegalArgumentException("exchange must precede the first draw");
            var hand=new ArrayList<>(acceptedHands.get(seat));
            for(var incoming:selections.get(Math.floorMod(seat-direction,count)))
                if(!hand.remove(incoming)) throw new IllegalArgumentException("accepted exchange lost an incoming tile");
            hand.addAll(selections.get(seat));
            before.add(order(hand,sichuan,jokers)); after.add(order(acceptedHands.get(seat),sichuan,jokers));
        }
        var selectedSlots=new ArrayList<List<Integer>>();
        var usedAfter=new boolean[count][13];
        var flights=new ArrayList<Flight>();
        // Reserve incoming identities first so equal stationary copies cannot consume their target slots.
        for(int seat=0;seat<count;seat++) {
            var remaining=new ArrayList<>(selections.get(seat));
            var indices=new ArrayList<Integer>();
            int target=(seat+direction)%count;
            for(int index=0;index<13;index++) {
                var tile=before.get(seat).get(index);
                if(!remaining.remove(tile)) continue;
                indices.add(index);
                flights.add(new Flight(seat,index,target,take(after.get(target),usedAfter[target],tile),tile,true));
            }
            if(!remaining.isEmpty() || indices.size()!=3) throw new IllegalArgumentException("exchange selection no longer owned");
            selectedSlots.add(List.copyOf(indices));
        }
        for(int seat=0;seat<count;seat++) for(int index=0;index<13;index++) {
            if(selectedSlots.get(seat).contains(index)) continue;
            var tile=before.get(seat).get(index);
            flights.add(new Flight(seat,index,seat,take(after.get(seat),usedAfter[seat],tile),tile,false));
        }
        return new Session(count,direction,sichuan,before,after,flights);
    }

    private static List<TheMahjongTile> order(List<TheMahjongTile> source,boolean sichuan,boolean jokers) {
        var sorted=new ArrayList<>(source); sorted.sort(TheMahjongTile.DISPLAY_ORDER);
        if(jokers) sorted.sort(Comparator.comparingInt(tile->
                (sichuan?tile.suit()==TheMahjongTile.Suit.DRAGON&&tile.rank()==3:Jokers.isJoker(tile))?0:1));
        return List.copyOf(sorted);
    }
    private static int take(List<TheMahjongTile> hand,boolean[] used,TheMahjongTile tile) {
        for(int index=0;index<hand.size();index++) if(!used[index] && tile.equals(hand.get(index))) {
            used[index]=true; return index;
        }
        throw new IllegalArgumentException("exchange presentation does not conserve exact physical identities");
    }
    public static String directionLabel(int count,int direction) {
        if(count<3 || count>4 || direction<1 || direction>=count) throw new IllegalArgumentException("exchange direction");
        if(count==4 && direction==2) return "对家互换";
        return direction==1?"逆时针":"顺时针";
    }
}
