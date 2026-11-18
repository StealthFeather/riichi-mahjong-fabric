package com.riichimahjong.analysis;

import com.google.gson.Gson;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.yaku.WinResult;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** One real match, including its original identity and any BOT action across save/reload. */
public final class MatchAnalysisSession implements AutoCloseable {
    private static final Gson JSON=new Gson();
    private static final class Counts { long matched,decisions; }
    private record AcceptedWin(UUID winner,UUID source,boolean selfDrawn,List<String> yaku) {}
    private static final class State {
        int version=1,hand;
        String id=UUID.randomUUID().toString(),mode;
        boolean botOperated,seatChanged,roundRecorded,matchRecorded;
        List<UUID> owners=new ArrayList<>();
        Map<Integer,UUID> actors=new HashMap<>();
        Map<UUID,Counts> players=new LinkedHashMap<>();
        Set<UUID> handPlayers=new LinkedHashSet<>();
        List<AcceptedWin> wins=new ArrayList<>();
    }
    public record Settlement(UUID player,Integer quotedTenths,long actualTenths,long totalTenths,boolean botOperated) {}
    private final State state;
    private final AnalysisMode mode;
    private DecisionConsistency consistency=new DecisionConsistency();
    private boolean closed;
    private TheMahjongDriver driver;
    private Runnable dirty=()->{};
    private Consumer<Settlement> notify=ignored->{};
    private boolean consistencyWritten;

    public MatchAnalysisSession(AnalysisMode mode,List<UUID> owners) {
        this.state=new State();this.mode=Objects.requireNonNull(mode);
        state.mode=mode.key();state.owners.addAll(owners);
        owners.stream().filter(Objects::nonNull).forEach(this::participant);
    }
    private MatchAnalysisSession(State state,AnalysisMode mode) { this.state=state;this.mode=mode; }
    public boolean botOperated() { return state.botOperated; }
    public String id() { return state.id; }

    /** Controllers keep their existing concrete types, including BOT avatar animation controllers. */
    public void bind(TheMahjongDriver driver,Runnable dirty,Consumer<Settlement> notify) {
        if (closed) { consistency=new DecisionConsistency();closed=false; }
        this.driver=Objects.requireNonNull(driver);this.dirty=dirty;this.notify=notify;
        driver.setActionObserver(this::action);
    }
    private UUID human(int seat) {
        return driver.playerAt(seat) instanceof MahjongTableHumanPlayer human?human.occupant().orElse(null):null;
    }
    private void participant(UUID player) {
        if(player!=null)state.players.computeIfAbsent(player,ignored->new Counts());
    }
    public void beginHand() {
        state.roundRecorded=false;state.handPlayers.clear();state.actors.clear();state.wins.clear();
        for(int seat=0;seat<driver.match().playerCount();seat++) {
            UUID player=human(seat);
            if(player!=null) { participant(player);state.handPlayers.add(player);state.actors.put(seat,player); }
        }
        dirty.run();
    }
    public void nextHand() { state.hand++;beginHand(); }

    private void action(DecisionRequest request,PlayerAction action) {
        int seat=request.seat();UUID player=human(seat);
        if(player==null) {
            state.botOperated=true;state.actors.remove(seat);dirty.run();return;
        }
        participant(player);state.handPlayers.add(player);state.actors.put(seat,player);
        UUID owner=state.owners.get(seat);
        if(owner==null)state.owners.set(seat,player);
        else if(!owner.equals(player))state.seatChanged=true;
        var counts=state.players.get(player);
        boolean red=driver.match().tileSet().tiles().stream().anyMatch(tile->tile.redDora());
        consistency.record(request,action,red,matched->{
            counts.decisions++;if(matched)counts.matched++;consistencyWritten=false;dirty.run();
        });
        dirty.run();
    }

    /** Record public, accepted win metadata immediately, even while a blood battle continues. */
    private void captureWins() {
        if(state.roundRecorded)return;
        if(driver.sichuanState()!=null) {
            var wins=driver.sichuanState().wins();
            for(int index=state.wins.size();index<wins.size();index++) {
                var win=wins.get(index);addWin(win.seat(),win.sourceSeat(),win.selfDrawn(),win.result());
            }
        } else if(driver.bloodBattleState()!=null) {
            var wins=driver.bloodBattleState().wins();
            for(int index=state.wins.size();index<wins.size();index++) {
                var win=wins.get(index);addWin(win.seat(),win.sourceSeat(),win.selfDrawn(),win.result());
            }
        } else if(state.wins.isEmpty()&&driver.currentPhase() instanceof MatchPhase.RoundEnded ended) {
            var round=driver.currentRound();
            boolean self=driver.acceptedWinningTile() instanceof TheMahjongRound.ActiveTile.Drawn;
            for(var win:ended.winResults()) {
                int winner=-1;
                for(int seat=0;seat<win.pointDeltas().size();seat++)if(win.pointDeltas().get(seat)>0){winner=seat;break;}
                if(winner>=0)addWin(winner,round.currentTurnSeat(),self,win);
            }
        }
    }
    private void addWin(int winner,int source,boolean selfDrawn,WinResult result) {
        UUID player=state.actors.get(winner);
        UUID payer=selfDrawn?null:state.actors.get(source);
        var yaku=new ArrayList<String>();
        result.yaku().forEach(value->yaku.add(value.name()));
        result.yakuman().forEach(value->yaku.add(value.name()));
        if(result.doraCount()>0)yaku.add("DORA");
        state.wins.add(new AcceptedWin(player,payer,selfDrawn,List.copyOf(yaku)));
        if(player!=null){participant(player);state.handPlayers.add(player);}
        if(payer!=null){participant(payer);state.handPlayers.add(payer);}
        dirty.run();
    }

    public void tick() {
        consistency.poll();captureWins();
        var store=PlayerAnalysisStore.get();
        if(store==null)return;
        if(!state.roundRecorded&&driver.currentPhase() instanceof MatchPhase.RoundEnded)recordHand(store);
        if(driver.currentPhase() instanceof MatchPhase.MatchEnded&&!state.matchRecorded)recordMatch(store);
        if(state.matchRecorded&&consistency.pendingCount()==0&&!consistencyWritten)flushConsistency(store);
    }
    private void recordHand(PlayerAnalysisStore store) {
        var results=new ArrayList<PlayerAnalysisStore.HandResult>();
        for(UUID player:state.handPlayers) {
            var wins=state.wins.stream().filter(win->player.equals(win.winner())).toList();
            boolean dealt=state.wins.stream().anyMatch(win->player.equals(win.source()));
            var yaku=wins.stream().flatMap(win->win.yaku().stream()).distinct().toList();
            results.add(new PlayerAnalysisStore.HandResult(player,!wins.isEmpty(),
                    wins.stream().anyMatch(AcceptedWin::selfDrawn),dealt,yaku));
        }
        store.recordHand(state.id+"/hand/"+state.hand,mode,results);
        state.roundRecorded=true;dirty.run();
    }
    private void recordMatch(PlayerAnalysisStore store) {
        Map<Integer,Integer> deltas=new HashMap<>();
        if(!state.botOperated&&!state.seatChanged)
            ModeRatingRules.calculate(mode.preset(),driver.match()).forEach(result->deltas.put(result.seat(),result.deltaTenths()));
        for(var entry:state.players.entrySet()) {
            UUID player=entry.getKey();var counts=entry.getValue();
            int seat=state.owners.indexOf(player);
            Integer delta=seat<0?null:deltas.get(seat);
            var result=store.recordMatch(state.id,mode,player,delta,counts.matched,counts.decisions);
            if(result.recorded())notify.accept(new Settlement(player,delta,result.actualDeltaTenths(),result.currentTenths(),state.botOperated));
        }
        state.matchRecorded=true;consistencyWritten=consistency.pendingCount()==0;dirty.run();
    }
    private void flushConsistency(PlayerAnalysisStore store) {
        for(var entry:state.players.entrySet()) {
            var counts=entry.getValue();
            if(counts.decisions>0)store.recordConsistency(state.id,mode,entry.getKey(),counts.matched,counts.decisions);
        }
        consistencyWritten=true;
    }
    public void write(ValueOutput out) { out.putString("Session",JSON.toJson(state)); }
    public static MatchAnalysisSession read(ValueInput in) {
        String json=in.getStringOr("Session","");if(json.isEmpty())return null;
        try {
            State saved=JSON.fromJson(json,State.class);
            if(saved==null||saved.version!=1||saved.id==null||saved.owners==null||saved.owners.size()<3||saved.owners.size()>4
                    ||saved.players==null||saved.actors==null||saved.handPlayers==null||saved.wins==null)return null;
            UUID.fromString(saved.id);
            var mode=AnalysisMode.fromKey(saved.mode).orElseThrow();
            if(mode.preset().playerCount()!=saved.owners.size())return null;
            return new MatchAnalysisSession(saved,mode);
        } catch(RuntimeException invalid) { return null; }
    }
    @Override public void close() {
        consistency.poll();
        var store=PlayerAnalysisStore.get();
        if(store!=null&&state.matchRecorded)flushConsistency(store);
        consistency.close();closed=true;
        if(driver!=null)driver.setActionObserver((request,action)->{});
    }
}
