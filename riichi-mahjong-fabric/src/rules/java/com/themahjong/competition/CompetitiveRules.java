package com.themahjong.competition;

import com.themahjong.*;
import java.util.List;

/** Four-player tournament profiles; optional table variants preserve the selected profile. */
public final class CompetitiveRules {
    public enum Profile { STANDARD, JPML_A, M_LEAGUE }
    private CompetitiveRules() {}

    public static TheMahjongMatch jpmlA() { return create(Profile.JPML_A, false, 30000, 30000); }
    public static TheMahjongMatch mLeague() { return create(Profile.M_LEAGUE, true, 25000, 30000); }

    /** Final deposits are real points, shared among tied leaders before league standings are scored. */
    public static TheMahjongMatch applyFinalDeposits(TheMahjongMatch match) {
        if(match.state()!=TheMahjongMatch.State.ENDED)throw new IllegalStateException("match must be ended");
        var round=match.currentRound().orElse(null);
        if(round==null||round.riichiSticks()==0||!match.ruleSet().depositsToFirstAtEnd())return match;
        int top=round.players().stream().mapToInt(TheMahjongPlayer::points).max().orElseThrow();
        var leaders=new java.util.ArrayList<Integer>();
        for(int seat=0;seat<round.players().size();seat++)if(round.players().get(seat).points()==top)leaders.add(seat);
        var players=new java.util.ArrayList<>(round.players());
        int hundreds=round.riichiSticks()*10,remaining=hundreds;
        for(int index=0;index<leaders.size();index++) {
            int seat=leaders.get(index);
            // With three tied leaders the official split is 400/300/300 or 800/600/600.
            int share=leaders.size()==3 && round.riichiSticks()%3!=0
                    ? round.riichiSticks()*(index==0?4:3)
                    : index==0?hundreds-(hundreds/leaders.size())*(leaders.size()-1):hundreds/leaders.size();
            remaining-=share;var old=players.get(seat);
            players.set(seat,new TheMahjongPlayer(old.points()+share*100,old.riichiState(),old.ippatsuEligible(),
                    old.currentHand(),old.melds(),old.discards(),old.temporaryFuritenTiles(),old.riichiPermanentFuriten(),old.kitaCount()));
        }
        if(remaining!=0)throw new IllegalStateException("deposit split lost points");
        var updated=new TheMahjongRound(round.roundWind(),round.handNumber(),round.honba(),0,round.dealerSeat(),
                round.state(),round.currentTurnSeat(),round.claimSourceSeat().orElse(-1),round.activeTile(),
                round.liveWall(),round.rinshanTiles(),round.doraIndicators(),round.uraDoraIndicators(),
                round.revealedDoraCount(),round.pendingKanDoraReveals(),players,round.pendingDeltas());
        return new TheMahjongMatch(match.playerCount(),match.startingPoints(),match.targetPoints(),match.roundCount(),
                match.state(),match.tileSet(),match.ruleSet(),match.completedRounds(),updated);
    }

    public static TheMahjongRound revealAcceptedKan(TheMahjongRound round) {
        return copy(round,round.players(),round.revealedDoraCount()+round.pendingKanDoraReveals(),0);
    }
    public static TheMahjongRound cancelRobbedKakan(TheMahjongRound round) {
        if(!(round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKakan held))return round;
        int source=round.claimSourceSeat().orElseThrow();var players=new java.util.ArrayList<>(round.players());var old=players.get(source);
        var melds=new java.util.ArrayList<>(old.melds());
        for(int index=0;index<melds.size();index++)if(melds.get(index) instanceof TheMahjongMeld.Kakan k&&k.addedTile().equals(held.tile())) {
            melds.set(index,k.upgradedFrom());break;
        }
        players.set(source,new TheMahjongPlayer(old.points(),old.riichiState(),old.ippatsuEligible(),old.currentHand(),melds,
                old.discards(),old.temporaryFuritenTiles(),old.riichiPermanentFuriten(),old.kitaCount()));
        return copy(round,players,round.revealedDoraCount(),Math.max(0,round.pendingKanDoraReveals()-1));
    }
    private static TheMahjongRound copy(TheMahjongRound round,List<TheMahjongPlayer> players,int revealed,int pending) {
        return new TheMahjongRound(round.roundWind(),round.handNumber(),round.honba(),round.riichiSticks(),round.dealerSeat(),
                round.state(),round.currentTurnSeat(),round.claimSourceSeat().orElse(-1),round.activeTile(),round.liveWall(),round.rinshanTiles(),
                round.doraIndicators(),round.uraDoraIndicators(),revealed,pending,players,round.pendingDeltas());
    }

    /** Riichi kans cannot remove an alternative sequence/chitoitsu decomposition of the original hand. */
    public static void validateRiichiAnkan(TheMahjongPlayer player,TheMahjongMeld.Ankan ankan,TheMahjongRound.ActiveTile active) {
        if(!(active instanceof TheMahjongRound.ActiveTile.Drawn drawn))throw new IllegalStateException("riichi kan must use drawn tile");
        var concealed=new java.util.ArrayList<>(player.currentHand());concealed.remove(drawn.tile());
        var before=new TheMahjongPlayer(player.points(),player.riichiState(),player.ippatsuEligible(),concealed,
                player.melds(),player.discards(),player.temporaryFuritenTiles(),player.riichiPermanentFuriten(),player.kitaCount());
        var kind=ankan.tiles().getFirst();
        for(var winning:com.themahjong.yaku.TenpaiChecker.winningTiles(before)) {
            var complete=new java.util.ArrayList<>(concealed);complete.add(winning);
            for(var shape:com.themahjong.yaku.HandShape.decomposeForWin(complete,player.melds(),winning)) {
                if(!(shape instanceof com.themahjong.yaku.HandShape.Standard standard)
                        ||standard.closedGroups().stream().noneMatch(g -> g instanceof com.themahjong.yaku.HandShape.ConcealedGroup.Triplet
                                && g.tiles().getFirst().matchesSuitRank(kind)))
                    throw new IllegalStateException("riichi kan changes the original decomposition");
            }
        }
    }

    private static TheMahjongMatch create(Profile profile, boolean red, int start, int target) {
        var rules=new TheMahjongRuleSet(profile == Profile.M_LEAGUE, profile == Profile.JPML_A, false, profile == Profile.M_LEAGUE, false, false, false,
                false, false, profile == Profile.M_LEAGUE, false, false, false, true, true, false,
                false, false, false, false, false, false, false, false, profile);
        return new TheMahjongMatch(4,start,target,2,TheMahjongMatch.State.NOT_STARTED,
                TheMahjongTileSet.standardRiichi(red),rules,List.of(),null);
    }
}
