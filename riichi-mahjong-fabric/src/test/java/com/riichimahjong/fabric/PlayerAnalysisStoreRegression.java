package com.riichimahjong.fabric;

import com.riichimahjong.analysis.AnalysisMode;
import com.riichimahjong.analysis.PlayerAnalysisStore;
import com.riichimahjong.analysis.PlayerAnalysisStore.HandResult;
import com.riichimahjong.mahjongtable.*;
import java.nio.file.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Verifies durable settlement, replay idempotency, mode isolation and complete rolling windows. */
public final class PlayerAnalysisStoreRegression {
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("riichi-analysis-regression-");
        UUID winner=UUID.randomUUID(),loser=UUID.randomUUID();
        var mode=AnalysisMode.of(RuleSetPreset.MAHJONG_SOUL_4P,MatchLength.SOUTH,false,false,false);
        var east=AnalysisMode.of(RuleSetPreset.MAHJONG_SOUL_4P,MatchLength.EAST,false,false,false);
        var sanma=AnalysisMode.of(RuleSetPreset.MAHJONG_SOUL_SANMA_3P,MatchLength.SOUTH,false,false,false);
        var fun=AnalysisMode.of(RuleSetPreset.MAHJONG_SOUL_4P,MatchLength.SOUTH,true,false,false);
        try {
            var store=new PlayerAnalysisStore(directory);
            var results=List.of(new HandResult(winner,true,false,false,List.of("RIICHI","RIICHI","PINFU")),
                    new HandResult(loser,false,false,true,List.of()));
            check(store.recordHand("round-1",mode,results),"first hand was not recorded");
            check(!store.recordHand("round-1",mode,results),"replayed hand was counted twice");
            var win=store.profile(winner,mode);
            check(win.hands()==1&&win.wins()==1&&win.ron()==1&&win.tsumo()==0,"winner outcome counters differ");
            check(win.yaku().equals(Map.of("RIICHI",1L,"PINFU",1L)),"yaku counted han or duplicate labels as repeated wins");
            check(store.profile(loser,mode).dealtIn()==1,"ron source was not counted");
            store.recordHand("round-2",mode,List.of(new HandResult(winner,true,true,false,List.of("MENZEN_TSUMO"))));
            check(store.profile(winner,mode).tsumo()==1,"self draw missing");
            check(store.profile(winner,east).hands()==0&&store.profile(winner,sanma).hands()==0
                    &&store.profile(winner,fun).hands()==0,"statistics crossed mode boundaries");
            var zero=store.recordMatch("game-0",mode,loser,-300,0L,1L);
            check(zero.currentTenths()==0&&zero.actualDeltaTenths()==0,"zero-point account was deducted");
            var gain=store.recordMatch("game-0",mode,winner,125,1L,1L);
            check(gain.currentTenths()==125&&gain.actualDeltaTenths()==125,"positive score missing");
            check(!store.recordMatch("game-0",mode,winner,125,1L,1L).recorded(),"replayed match was settled twice");
            var clipped=store.recordMatch("game-1",mode,winner,-200,0L,1L);
            check(clipped.previousTenths()==125&&clipped.currentTenths()==0&&clipped.actualDeltaTenths()==-125,
                    "deduction crossed zero or reported unapplied amount");
            store.recordMatch("game-fun",fun,winner,1000,null,null);
            check(store.profile(winner,fun).scoreTenths()==0,"entertainment game received ranked points");
            store.recordMatch("game-with-bot",mode,winner,null,null,null);
            check(store.profile(winner,mode).scoreTenths()==0,"unrated completed game changed account balance");
            // The exact most recent N games are required: unknown analysis cannot be replaced by 0.
            for(int i=0;i<9;i++)store.recordMatch("east-"+i,east,winner,null,1L,2L);
            check(store.profile(winner,east).average(10).isEmpty(),"nine games exposed a ten-game window");
            store.recordMatch("east-9",east,winner,null,null,null);
            check(store.profile(winner,east).average(10).isEmpty(),"missing analysis was treated as a real score");
            check(store.recordConsistency("east-9",east,winner,1,1),"late analysis was not attached to original game");
            check(Math.abs(store.profile(winner,east).average(10).orElseThrow()-55)<0.00001,
                    "window is not arithmetic mean of per-game consistency");
            for(int i=10;i<50;i++)store.recordMatch("east-"+i,east,winner,null,1L,2L);
            check(store.profile(winner,east).average(20).orElseThrow()==50,"twenty-game window includes older scores");
            check(store.profile(winner,east).average(50).orElseThrow()==51,"fifty-game mean incorrect");
            store.recordMatch("east-50",east,winner,null,0L,1L);
            check(store.profile(winner,east).recent().size()==50,"recent history is not bounded at fifty");
            var reloaded=new PlayerAnalysisStore(directory);
            check(reloaded.profile(winner,mode).wins()==2&&reloaded.profile(loser,mode).dealtIn()==1,
                    "hand statistics did not survive reload");
            check(!reloaded.recordHand("round-1",mode,results),"reloaded replay ledger lost hand IDs");
            check(!reloaded.recordMatch("game-0",mode,winner,125,1L,1L).recorded(),"reloaded ledger lost match IDs");
            check(reloaded.profile(winner,east).matches()==51,"durable match count incorrect");
            check(reloaded.modes(winner).containsAll(List.of(mode,east,fun)),"mode selection lost stored histories");
            check(AnalysisMode.fromKey(sanma.key()).orElseThrow().equals(sanma),"mode key round trip lost dimensions");
            check(AnalysisMode.fromKey("unknown|SOUTH|0|0|0").isEmpty(),"invalid mode accepted");
            check(!AnalysisMenus.score(55.5).contains("%")&&AnalysisMenus.points(125).equals("12.5"),
                    "display changed point format or attached percent sign");
            System.out.println("PASS: player analysis persists by UUID and mode, settles once with zero floor, and only displays complete rolling consistency windows");
        } finally {
            try(var paths=Files.walk(directory)) {
                for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
    }
}
