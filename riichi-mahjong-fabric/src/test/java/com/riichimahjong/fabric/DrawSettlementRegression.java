package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.compat.DrawSettlement;
import com.themahjong.driver.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Actual rule entry points, not a renderer-only simulation of draw payments. */
public final class DrawSettlementRegression {
    private static final TheMahjongRuleSet FOUR = TheMahjongRuleSet.mahjongSoul();
    private static final TheMahjongRuleSet THREE = TheMahjongRuleSet.mahjongSoulSanma();

    private static TheMahjongPlayer noten() {
        var hand = new ArrayList<>(waiting(1));
        hand.set(0, tile(4,2));
        var player = player(hand);
        check(!DrawSettlement.inTenpai(player), "noten fixture accidentally waits");
        return player;
    }
    private static TheMahjongPlayer withHand(List<TheMahjongTile> hand,List<TheMahjongMeld> melds) {
        return new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,melds,List.of(),List.of());
    }
    private static TheMahjongRound round(List<TheMahjongPlayer> players,int honba,int sticks,int dealer) {
        var indicators = Collections.nCopies(5,tile(4,3));
        return new TheMahjongRound(TheMahjongTile.Wind.EAST,1,honba,sticks,dealer,
                TheMahjongRound.State.TURN,dealer,-1,TheMahjongRound.ActiveTile.none(),List.of(),
                Collections.nCopies(TheMahjongRound.rinshanTileCountFor(players.size()),tile(4,2)),
                indicators,indicators,1,players,List.of());
    }
    private static List<Integer> points(TheMahjongRound round) {
        return round.players().stream().map(TheMahjongPlayer::points).toList();
    }
    private static void distributions() {
        for(int count:new int[]{3,4}) for(int mask=0;mask<(1<<count);mask++) {
            var players = new ArrayList<TheMahjongPlayer>();
            int ready = Integer.bitCount(mask);
            var expected = new ArrayList<Integer>();
            for(int seat=0;seat<count;seat++) {
                boolean tenpai = (mask&(1<<seat))!=0;
                players.add(tenpai?player(waiting(1)):noten());
                int delta = ready==0||ready==count?0:tenpai?3000/ready:-3000/(count-ready);
                expected.add(25000+delta);
            }
            var rules = count==3?List.of(THREE,TheMahjongRuleSet.tenhouSanma()):
                    List.of(FOUR,TheMahjongRuleSet.tenhou(),TheMahjongRuleSet.wrc());
            for(var preset:rules) {
                var original = round(players,7,2,count-1);
                var ended = original.exhaustiveDraw(preset);
                check(points(ended).equals(expected),count+"p readiness mask "+mask+" settled as "+points(ended)+", expected "+expected);
                check(points(ended).stream().mapToInt(Integer::intValue).sum()==25000*count,"noten transfer did not conserve points");
                check(points(original).stream().allMatch(p->p==25000),"settlement mutated its input");
                check(ended.pendingDeltas().isEmpty(),"direct settlement left an unapplied/duplicate pending payment");
                check(ended.state()==TheMahjongRound.State.ENDED&&ended.honba()==7&&ended.riichiSticks()==2&&ended.dealerSeat()==count-1,
                        "settlement changed draw/dealer/honba/deposit metadata");
            }
        }
    }
    private static void readinessEdges() {
        var fifth = withHand(List.of(tile(0,1),tile(0,1),tile(0,1),tile(0,1),
                tile(1,2),tile(1,3),tile(1,4),tile(1,5),tile(1,6),tile(1,7),tile(2,7),tile(2,8),tile(2,9)),List.of());
        check(!DrawSettlement.inTenpai(fifth),"a fifth self-owned copy counted as tenpai");
        var groups = List.of(tile(1,2),tile(1,3),tile(1,4),tile(1,5),tile(1,6),tile(1,7),tile(2,7),tile(2,8),tile(2,9));
        var hand = new ArrayList<>(groups); hand.add(tile(0,1));
        var pon = new TheMahjongMeld.Pon(Collections.nCopies(3,tile(0,1)),0,1,0);
        check(!DrawSettlement.inTenpai(withHand(hand,List.of(pon))),"own meld copies were omitted from readiness");
        hand.set(hand.size()-1,tile(0,2));
        var open = withHand(hand,List.of(pon));
        check(DrawSettlement.inTenpai(open),"ordinary open hand lost tenpai");
        check(DrawSettlement.inTenpai(withHand(hand,List.of(new TheMahjongMeld.Ankan(Collections.nCopies(4,tile(0,1)))))),
                "a valid wait with a concealed kan was rejected");
        var redFive = new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);
        var redPon = new TheMahjongMeld.Pon(List.of(redFive,tile(0,5),tile(0,5)),0,1,0);
        hand.set(hand.size()-1,tile(0,5));
        check(!DrawSettlement.inTenpai(withHand(hand,List.of(redPon))),"red five created an imaginary fifth-copy wait");
        var fourMelds = new ArrayList<TheMahjongMeld>();
        for(int rank=1;rank<=4;rank++)fourMelds.add(new TheMahjongMeld.Pon(Collections.nCopies(3,tile(0,rank)),0,1,0));
        check(DrawSettlement.inTenpai(withHand(List.of(tile(0,5)),fourMelds)),"four melds plus a tanki wait was rejected");
        check(!DrawSettlement.inTenpai(withHand(List.of(tile(0,1)),fourMelds)),"four-meld fifth-copy wait was accepted");

        // Every white dragon is outside seat 0's hand; its formal wait still pays.
        var outside = new ArrayList<>(noten().currentHand()); outside.set(0,tile(4,1));
        var externalPon = new TheMahjongMeld.Pon(Collections.nCopies(3,tile(4,1)),0,0,0);
        var external = withHand(groups,List.of(externalPon));
        var exhausted = round(List.of(player(waiting(1)),withHand(outside,List.of()),external,noten()),0,0,0).exhaustiveDraw(FOUR);
        check(exhausted.players().get(0).points()>25000,"external tiles incorrectly invalidated formal tenpai");
        var edge = round(List.of(fifth,open,noten(),noten()),0,0,0).exhaustiveDraw(FOUR);
        check(points(edge).equals(List.of(24000,28000,24000,24000)),"readiness edge classification was not used for payments");
    }
    private static void abortiveAndNagashi() {
        var players = List.of(player(waiting(1)),noten(),noten(),noten());
        var original = round(players,2,3,0);
        var abortive = original.abortiveDraw(FOUR);
        check(points(abortive).equals(points(original)),"abortive draw charged noten penalties");
        check(abortive.riichiSticks()==3&&abortive.honba()==2,"abortive draw lost carried metadata");
        var base = noten();
        var winner = new TheMahjongPlayer(base.points(),base.riichiState(),false,base.currentHand(),List.of(),
                List.of(new TheMahjongDiscard(tile(0,9),false)),List.of());
        for(int seat=0;seat<4;seat++) {
            var nagashiPlayers = new ArrayList<>(players); nagashiPlayers.set(seat,winner);
            var ended = round(nagashiPlayers,2,3,0).exhaustiveDraw(FOUR);
            int sum = 0;
            for(int other=0;other<4;other++) {
                int delta;
                if(other==seat)delta=seat==0?12600:8600;
                else delta=(seat==0||other==0)?-4200:-2200;
                check(ended.players().get(other).points()==25000+delta,"nagashi stacked a noten payment or changed existing settlement");
                sum+=delta;
            }
            check(sum==0&&ended.riichiSticks()==3,"nagashi changed its existing zero-sum/deposit behavior");
        }
        var disabled = round(List.of(winner,player(waiting(1)),noten(),noten()),0,0,0).exhaustiveDraw(TheMahjongRuleSet.wrc());
        check(points(disabled).equals(List.of(24000,28000,24000,24000)),"disabled nagashi suppressed ordinary noten payment");
    }
    private static void fieldsAndNextRound() {
        var ready = player(waiting(1));
        var rich = new TheMahjongPlayer(24000,TheMahjongPlayer.RiichiState.RIICHI,true,ready.currentHand(),
                ready.melds(),List.of(new TheMahjongDiscard(tile(1,2),true)),List.of(tile(4,1)),true,2);
        var ended = round(List.of(rich,noten(),noten(),noten()),2,1,0).exhaustiveDraw(FOUR);
        var settled = ended.players().get(0);
        check(settled.points()==27000,"riichi player's existing deposit was refunded or charged again");
        check(settled.riichiState()==rich.riichiState()&&settled.ippatsuEligible()==rich.ippatsuEligible()
                &&settled.currentHand().equals(rich.currentHand())&&settled.melds().equals(rich.melds())
                &&settled.discards().equals(rich.discards())&&settled.temporaryFuritenTiles().equals(rich.temporaryFuritenTiles())
                &&settled.riichiPermanentFuriten()==rich.riichiPermanentFuriten()&&settled.kitaCount()==rich.kitaCount(),
                "changing points erased another player field");
        check(points(ended).stream().mapToInt(Integer::intValue).sum()+1000*ended.riichiSticks()==100000,
                "points plus riichi deposits were not conserved");
        try { ended.exhaustiveDraw(FOUR); throw new AssertionError("ended draw was settled twice"); }
        catch(IllegalStateException expected) { }
        try { ended.abortiveDraw(FOUR); throw new AssertionError("ended draw could be reclassified and settled"); }
        catch(IllegalStateException expected) { }
        for(boolean renchan:new boolean[]{false,true}) {
            var match = new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                    TheMahjongTileSet.standardRiichi(false),FOUR,List.of(),ended);
            var next = match.advanceRound(new Random(3),renchan,3);
            check(points(next.currentRound().orElseThrow()).equals(points(ended)),"next hand reapplied or erased noten transfer");
            check(next.completedRounds().equals(List.of(ended)),"settled round was not preserved in history");
            var r = next.currentRound().orElseThrow();
            check(r.dealerSeat()==(renchan?0:1)&&r.handNumber()==(renchan?1:2)&&r.honba()==3&&r.riichiSticks()==1,
                    "next round changed dealer/honba/deposit policy");
            check(r.pendingDeltas().isEmpty(),"next round retained draw payments");
        }
    }
    public static void main(String[] args) {
        distributions();
        System.out.println("PASS: all 3p/4p tenpai distributions split 3000 once, including zero/all tenpai");
        readinessEdges();
        System.out.println("PASS: noten payments share fifth-copy, red-five, meld and external-exhaustion readiness");
        abortiveAndNagashi();
        System.out.println("PASS: abortive draws do not pay; nagashi replaces rather than stacks noten payments");
        fieldsAndNextRound();
        System.out.println("PASS: player fields, deposits and next-round scores survive; ended rounds cannot pay twice");
    }
}
