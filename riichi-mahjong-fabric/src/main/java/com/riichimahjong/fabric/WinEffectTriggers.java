package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.WinEffectContext;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.themahjong.TheMahjongRound;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import java.util.List;

/** Convert accepted rule events to a small public presentation context, once per event. */
public final class WinEffectTriggers {
    private WinEffectTriggers() {}

    /** Recover presentation order for legacy active saves without an animation queue. */
    public static List<WinEffectContext> history(TheMahjongDriver driver,String effectId) {
        if(driver==null||driver.match().currentRound().isEmpty())return List.of();
        var contexts=new java.util.ArrayList<WinEffectContext>();
        if(driver.sichuanState()!=null)for(var win:driver.sichuanState().wins())
            contexts.add(historical(driver,effectId,win.seat(),win.sourceSeat(),win.tile(),win.selfDrawn()));
        else if(driver.bloodBattleState()!=null)for(var win:driver.bloodBattleState().wins())
            contexts.add(historical(driver,effectId,win.seat(),win.sourceSeat(),win.tile(),win.selfDrawn()));
        return List.copyOf(contexts);
    }
    private static WinEffectContext historical(TheMahjongDriver driver,String effectId,int winner,int source,
            com.themahjong.TheMahjongTile tile,boolean selfDrawn) {
        var round=driver.currentRound();
        if(source<0||source>=round.players().size())source=winner;
        String slot="robbed/"+source;
        if(selfDrawn)slot="hand/"+winner+"/"+(round.players().get(winner).currentHand().size()-1);
        // Legacy wins lack a discard index. A later equal discard must never be consumed instead.
        // Exact queued contexts retain their original river source; recovered ron uses a virtual tile.
        int code=PublicTable.code(tile);
        return new WinEffectContext(effectId,new WinEffectContext.Source(slot,code),List.of(new WinEffectContext.Winner(winner,code)));
    }

    public static WinEffectContext capture(TheMahjongDriver driver, MatchPhase previousPhase,
            int previousSichuanWins, String effectId) {
        var round=driver.match().currentRound().orElse(null);
        if(round==null)return null;
        List<WinEffectContext.Winner> winners;
        int source;
        var active=driver.acceptedWinningTile();
        if(driver.sichuanState()!=null){
            var wins=driver.sichuanState().wins();
            if(wins.size()<=previousSichuanWins)return null;
            var added=wins.subList(previousSichuanWins,wins.size());
            source=added.getFirst().sourceSeat();
            winners=added.stream().map(w->new WinEffectContext.Winner(w.seat(),PublicTable.code(w.tile()))).toList();
        }else if(driver.bloodBattleState()!=null){
            var wins=driver.bloodBattleState().wins();
            if(wins.size()<=previousSichuanWins)return null;
            var added=wins.subList(previousSichuanWins,wins.size());
            source=added.getFirst().sourceSeat();
            winners=added.stream().map(w->new WinEffectContext.Winner(w.seat(),PublicTable.code(w.tile()))).toList();
        }else{
            if(previousPhase instanceof MatchPhase.RoundEnded
                    || !(driver.currentPhase() instanceof MatchPhase.RoundEnded ended)
                    || ended.winResults().isEmpty())return null;
            var display=WinningHandDisplay.capture(active,driver.currentPhase());
            if(display==null)return null;
            int code=PublicTable.code(display.tile());
            winners=PublicTable.revealedSeats(driver.currentPhase(),round).stream().sorted()
                    .map(seat->new WinEffectContext.Winner(seat,code)).toList();
            source=round.currentTurnSeat();
        }
        if(winners.isEmpty())return null;
        int code=winners.getFirst().tileCode();
        source=source>=0&&source<round.players().size()?source:winners.getFirst().seat();
        var player=round.players().get(source);
        String slot=switch(active){
            case TheMahjongRound.ActiveTile.Drawn drawn -> "hand/"+source+"/"+(player.currentHand().size()-1);
            case TheMahjongRound.ActiveTile.HeldDiscard held -> "river/"+source+"/"+(RiverDisplay.capture(round.players(),source).size()-1);
            default -> "robbed/"+source;
        };
        return new WinEffectContext(effectId,new WinEffectContext.Source(slot,code),winners);
    }
}
