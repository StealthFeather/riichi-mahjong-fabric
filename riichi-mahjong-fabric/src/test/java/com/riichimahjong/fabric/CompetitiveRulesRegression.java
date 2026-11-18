package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.competition.CompetitiveRules;
import com.themahjong.driver.*;
import com.themahjong.yaku.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Tournament scoring and actual rule actions, independent of menu labels. */
public final class CompetitiveRulesRegression {
    private static List<TheMahjongTile> tiles(String text) {
        var out=new ArrayList<TheMahjongTile>();
        for(var part:text.split(" ")) {
            int suit="mpsz".indexOf(part.charAt(part.length()-1));
            for(int i=0;i<part.length()-1;i++) {
                int rank=part.charAt(i)-'0';
                out.add(suit==3?tile(rank<=4?3:4,rank<=4?rank:rank-4):tile(suit,rank));
            }
        }
        return out;
    }
    private static WinResult score(TheMahjongRuleSet rules,List<TheMahjongTile> hand,List<TheMahjongMeld> melds,
            boolean tsumo,boolean riichi,boolean ippatsu,List<TheMahjongTile> dora,List<TheMahjongTile> ura,int honba) {
        var win=hand.getLast();
        var ctx=tsumo?WinContext.tsumo(false,false,riichi?TheMahjongPlayer.RiichiState.RIICHI:TheMahjongPlayer.RiichiState.NONE,
                ippatsu,win,TheMahjongTile.Wind.SOUTH,TheMahjongTile.Wind.EAST,false,false)
                :WinContext.ron(false,false,riichi?TheMahjongPlayer.RiichiState.RIICHI:TheMahjongPlayer.RiichiState.NONE,
                ippatsu,win,TheMahjongTile.Wind.SOUTH,TheMahjongTile.Wind.EAST,false,false);
        var all=new ArrayList<>(hand);melds.forEach(m->all.addAll(m.tiles()));
        return WinCalculator.calculateBest(HandShape.decomposeForWin(hand,melds,win),ctx,all,dora,ura,4,1,3,0,honba,0,rules);
    }
    private static List<TheMahjongPlayer> players(TheMahjongPlayer owner,int seat) {
        var out=new ArrayList<TheMahjongPlayer>();for(int i=0;i<4;i++)out.add(i==seat?owner:owner(waiting(i%3),List.of(),25000));return out;
    }
    private static TheMahjongRound round(List<TheMahjongPlayer> players,TheMahjongRound.State state,int turn,int source,
            TheMahjongRound.ActiveTile active,int live,int revealed) {
        return new TheMahjongRound(TheMahjongTile.Wind.EAST,1,1,0,0,state,turn,source,active,
                Collections.nCopies(live,tile(1,8)),Collections.nCopies(4,tile(3,4)),Collections.nCopies(5,tile(1,4)),
                Collections.nCopies(5,tile(1,4)),revealed,players,List.of());
    }
    private static TheMahjongPlayer owner(List<TheMahjongTile> hand,List<TheMahjongMeld> melds,int points) {
        return new TheMahjongPlayer(points,TheMahjongPlayer.RiichiState.NONE,false,hand,melds,
                List.of(new TheMahjongDiscard(tile(1,5),false)),List.of());
    }
    private static void optionsAndScores() {
        var jp=CompetitiveRules.jpmlA();var ml=CompetitiveRules.mLeague();
        check(jp.startingPoints()==30000&&jp.targetPoints()==30000,"JPML points");
        check(ml.startingPoints()==25000&&ml.targetPoints()==30000,"MLeague points");
        check(jp.tileSet().createOrderedWall().stream().noneMatch(TheMahjongTile::redDora),"JPML red fives");
        check(ml.tileSet().createOrderedWall().stream().filter(TheMahjongTile::redDora).count()==3,"MLeague red fives");
        for(var match:List.of(jp,ml)) {
            var variant=com.themahjong.variants.VariantRules.withOptions(match,true,true,true);
            check(variant.ruleSet().competitiveProfile()==match.ruleSet().competitiveProfile(),"optional variants lost competition profile");
        }
        var hand=tiles("123456m 789p 23455s");
        hand.set(hand.size()-1,new TheMahjongTile(TheMahjongTile.Suit.SOUZU,5,true));
        var indicators=List.of(tile(2,4),tile(2,4));
        var a=score(jp.ruleSet(),hand,List.of(),true,true,true,indicators,List.of(tile(2,4)),0);
        var b=score(ml.ruleSet(),hand,List.of(),true,true,true,indicators,List.of(tile(2,4)),0);
        check(a.doraCount()==2&&!a.yaku().contains(NonYakuman.IPPATSU),"JPML must have only first normal dora, no red/ura/kan/ippatsu: "+a);
        check(b.doraCount()==7&&b.yaku().contains(NonYakuman.IPPATSU),"MLeague dora and ippatsu: "+b);
        var counted=tiles("11223344556677m");
        var c=score(ml.ruleSet(),counted,List.of(),true,true,true,List.of(),List.of(),0);
        check(c.yakuman().isEmpty()&&c.han()>=13&&c.pointDeltas().get(1)==24000,"MLeague counted yakuman must cap at sanbaiman: "+c);
        var jpCounted=score(jp.ruleSet(),counted,List.of(),true,true,true,List.of(tile(0,6)),List.of(),0);
        check(jpCounted.yakuman().isEmpty()&&jpCounted.han()>=13&&jpCounted.pointDeltas().get(1)==32000,"JPML counted yakuman must be preserved: "+jpCounted);
        check(jp.ruleSet().doubleWindPairFu4()&&!ml.ruleSet().doubleWindPairFu4(),"competition double-wind pair fu");
        var fourHan=score(ml.ruleSet(),tiles("123456m 789p 2355s 4s"),List.of(),false,true,true,List.of(tile(0,3)),List.of(),0);
        check(fourHan.han()==4&&fourHan.fu()==30&&fourHan.pointDeltas().get(1)==8000,"MLeague kiriage mangan: "+fourHan);
    }
    private static void kanDoraAndResponsibility() {
        var jp=CompetitiveRules.jpmlA().ruleSet();var ml=CompetitiveRules.mLeague().ruleSet();
        var hand=tiles("1111m 234p 567s 55z 22p");
        var owner=owner(hand,List.of(),30000);
        var original=round(players(owner,1),TheMahjongRound.State.TURN,1,-1,TheMahjongRound.ActiveTile.drawn(tile(0,1)),10,1);
        check(original.declareAnkan(Collections.nCopies(4,tile(0,1)),jp).revealedDoraCount()==1,"JPML kan revealed dora");
        check(original.declareAnkan(Collections.nCopies(4,tile(0,1)),ml).revealedDoraCount()==2,"MLeague ankan did not reveal immediately");
        var pons=new ArrayList<TheMahjongMeld>();for(int rank=1;rank<=3;rank++)pons.add(new TheMahjongMeld.Pon(Collections.nCopies(3,tile(4,rank)),0,rank==3?2:0,0));
        pons.add(new TheMahjongMeld.Pon(Collections.nCopies(3,tile(3,1)),0,0,0));
        var compound=score(ml,tiles("33z"),pons,true,false,false,List.of(),List.of(),1);
        check(compound.yakuman().containsAll(List.of(Yakuman.DAISANGEN,Yakuman.TSUISOU)),"compound yakuman fixture");
        check(compound.pointDeltas().equals(List.of(-16000,64300,-40300,-8000)),"MLeague pao must charge only responsible component and honba: "+compound);
        var ron=score(ml,tiles("33z"),pons,false,false,false,List.of(),List.of(),1);
        check(ron.pointDeltas().equals(List.of(0,64300,-16300,-48000)),"MLeague ron pao/honba split: "+ron);
        var kans=new ArrayList<TheMahjongMeld>();for(int rank=1;rank<=3;rank++)kans.add(new TheMahjongMeld.Ankan(Collections.nCopies(4,tile(4,rank))));
        var ready=owner(tiles("4443z"),kans,30000);
        var claim=round(players(ready,1),TheMahjongRound.State.CLAIM_WINDOW,2,2,TheMahjongRound.ActiveTile.heldDiscard(tile(3,4)),10,1);
        var accepted=claim.claimDaiminkan(1,Collections.nCopies(3,tile(3,4)),ml);
        var fourth=(TheMahjongMeld.Daiminkan)accepted.players().get(1).melds().getLast();
        check(fourth.completesSuukantsu(),"actual fourth daiminkan did not retain responsibility");
        check(WinCalculator.findPaoSeat(accepted.players().get(1).melds(),List.of(Yakuman.SUUKANTSU),ml).orElse(-1)==2,"fourth-open-kan responsibility seat");
        var unmarked=new TheMahjongMeld.Daiminkan(fourth.tiles(),fourth.claimedTileIndex(),fourth.sourceSeat(),fourth.sourceDiscardIndex());
        var legacy=new ArrayList<TheMahjongMeld>(accepted.players().get(1).melds());legacy.set(3,unmarked);
        check(WinCalculator.findPaoSeat(legacy,List.of(Yakuman.SUUKANTSU),ml).isEmpty(),"unproven fourth kan guessed responsibility");
    }
    private static void riichiAndFinalDeposits() {
        var base=CompetitiveRules.mLeague();var hand=new ArrayList<>(waiting(0));hand.add(tile(4,2));
        var r=round(players(owner(hand,List.of(),-1000),0),TheMahjongRound.State.TURN,0,-1,TheMahjongRound.ActiveTile.drawn(tile(4,2)),1,1);
        var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),r);
        var driver=new TheMahjongDriver(match,java.util.stream.IntStream.range(0,4).mapToObj(i->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),new Random(1),new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
        check(driver.legalActions(0).stream().anyMatch(PlayerAction.DiscardWithRiichi.class::isInstance),"MLeague riichi incorrectly requires balance or later self draw");
        for(int sticks:new int[]{1,2,3,4,5,6,7}) {
            var p=List.of(owner(waiting(0),List.of(),30000),owner(waiting(0),List.of(),30000),owner(waiting(0),List.of(),30000),owner(waiting(0),List.of(),10000));
            var ended=new TheMahjongRound(TheMahjongTile.Wind.SOUTH,4,0,sticks,3,TheMahjongRound.State.ENDED,3,-1,
                    TheMahjongRound.ActiveTile.none(),r.liveWall(),r.rinshanTiles(),r.doraIndicators(),r.uraDoraIndicators(),1,p,List.of());
            var m=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.ENDED,base.tileSet(),base.ruleSet(),List.of(ended),ended);
            var settled=m.applyFinalDeposits();int low=sticks%3==0?sticks*1000/3:sticks*300,high=sticks*1000-low*2;
            check(settled.currentRound().orElseThrow().players().stream().map(TheMahjongPlayer::points).toList().equals(List.of(30000+high,30000+low,30000+low,10000)),"tied leaders lost final deposits");
            check(settled.currentRound().orElseThrow().riichiSticks()==0&&settled.applyFinalDeposits()==settled,"final deposits paid more than once");
            check(settled.completedRounds().getLast()==ended,"deposit settlement overwrote original tied ranking");
        }
    }
    private static TheMahjongDriver driverFor(TheMahjongRound round,TheMahjongRuleSet rules,MatchPhase phase) {
        var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,TheMahjongTileSet.standardRiichi(false),rules,List.of(),round);
        return new TheMahjongDriver(match,java.util.stream.IntStream.range(0,4).mapToObj(i->(MahjongPlayerInterface)new MahjongHumanPlayer()).toList(),new Random(1),new TheMahjongDriver.Snapshot(phase,false,OptionalInt.empty()));
    }
    private static void actionRestrictions() {
        for(var rules:List.of(CompetitiveRules.mLeague().ruleSet(),CompetitiveRules.jpmlA().ruleSet())) {
            var ready=owner(waiting(0),List.of(),30000);
            var last=round(players(ready,1),TheMahjongRound.State.CLAIM_WINDOW,0,0,TheMahjongRound.ActiveTile.heldDiscard(tile(4,1)),0,1);
            var claims=driverFor(last,rules,new MatchPhase.AwaitingClaims(Set.of(1,2,3),tile(4,1),MatchPhase.ClaimSource.DISCARD));
            check(claims.legalActions(1).stream().anyMatch(PlayerAction.DeclareRon.class::isInstance),"last-discard guard also removed legal ron");
            for(int seat=1;seat<4;seat++)check(claims.legalActions(seat).stream().noneMatch(a->a instanceof PlayerAction.Chi||a instanceof PlayerAction.Pon||a instanceof PlayerAction.Daiminkan),"last-discard exposed a meld");
            var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,tile(4,1)),0,0,0);
            var afterPon=owner(tiles("1111m 123p 556s 5z"),List.of(pon),30000);
            var noDraw=round(players(afterPon,1),TheMahjongRound.State.TURN,1,-1,TheMahjongRound.ActiveTile.none(),10,1);
            var controls=driverFor(noDraw,rules,new MatchPhase.AwaitingDiscard(1));
            check(controls.legalActions(1).stream().noneMatch(a->a instanceof PlayerAction.Ankan||a instanceof PlayerAction.Kakan),"self-kan available immediately after pon without draw");
        }
    }
    private static void robbedKanDoesNotReveal() {
        for(boolean blood:new boolean[]{false,true}) {
            var rules=com.themahjong.variants.VariantRules.withOptions(CompetitiveRules.mLeague(),false,false,blood).ruleSet();
            var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,tile(4,1)),0,2,0);
            var source=owner(tiles("123m 456p 789s 15z"),List.of(pon),25000);
            var original=round(players(source,0),TheMahjongRound.State.TURN,0,-1,TheMahjongRound.ActiveTile.drawn(tile(4,1)),10,1);
            var declared=original.declareKakan(pon,tile(4,1),rules);
            check(declared.revealedDoraCount()==1&&declared.pendingKanDoraReveals()==1,"unaccepted MLeague kakan leaked new dora");
            var accepted=declared.skipKakanClaims(rules);
            check(accepted.revealedDoraCount()==2&&accepted.pendingKanDoraReveals()==0,"accepted kakan did not immediately reveal dora");
            var driver=driverFor(declared,rules,new MatchPhase.AwaitingClaims(Set.of(1,2,3),tile(4,1),MatchPhase.ClaimSource.KAKAN));
            var win=driver.legalActions(1).stream().filter(PlayerAction.DeclareChankan.class::isInstance).findFirst().orElseThrow();
            act(driver,1,win);passClaims(driver);
            check(driver.currentRound().revealedDoraCount()==1&&driver.currentRound().pendingKanDoraReveals()==0,"robbed kan leaked or kept pending new dora");
            check(driver.currentRound().players().get(0).melds().getFirst() instanceof TheMahjongMeld.Pon,"robbed physical kakan was not restored to pon");
            if(blood)check(driver.bloodBattleState().won(1),"blood battle chankan not accepted");
            else check(driver.currentPhase() instanceof MatchPhase.RoundEnded,"normal chankan did not end round");
        }
    }
    private static void retiredCompositeResponsibility() {
        var rules=com.themahjong.variants.VariantRules.withOptions(CompetitiveRules.mLeague(),false,false,true).ruleSet();
        var melds=new ArrayList<TheMahjongMeld>();
        for(int rank=1;rank<=3;rank++)melds.add(new TheMahjongMeld.Daiminkan(Collections.nCopies(4,tile(4,rank)),0,rank==3?0:3,0));
        melds.add(new TheMahjongMeld.Daiminkan(Collections.nCopies(4,tile(0,1)),0,2,0,true));
        var hand=tiles("55p");var result=score(rules,hand,melds,true,false,false,List.of(),List.of(),0);
        check(result.yakuman().equals(List.of(Yakuman.DAISANGEN,Yakuman.SUUKANTSU)),"double responsibility fixture");
        var p=owner(hand,melds,50000);var r=round(players(p,1),TheMahjongRound.State.TURN,1,-1,TheMahjongRound.ActiveTile.drawn(tile(1,5)),10,5);
        // This regression isolates responsibility allocation, excluding honba and deposits.
        r=new TheMahjongRound(r.roundWind(),r.handNumber(),0,0,r.dealerSeat(),r.state(),r.currentTurnSeat(),-1,r.activeTile(),r.liveWall(),r.rinshanTiles(),r.doraIndicators(),r.uraDoraIndicators(),r.revealedDoraCount(),r.players(),List.of());
        var prior=new WinResult(List.of(NonYakuman.MENZEN_TSUMO),List.of(),1,30,0,List.of(3000,-1000,-1000,-1000));
        for(int retired:new int[]{0,2}) {
            var state=new com.themahjong.variants.BloodBattleState(new com.themahjong.variants.BloodBattleState.Snapshot(List.of(
                    new com.themahjong.variants.BloodBattleState.Win(retired,tile(2,9),true,prior,retired))));
            var recomputed=WinCalculator.withoutRetiredResponsibilities(result,true,1,r,rules,state.winners());
            var expected=retired==0?List.of(0,48000,-40000,-8000):List.of(-48000,56000,0,-8000);
            check(state.adjust(recomputed,1).pointDeltas().equals(expected),"retired component erased active responsibility or ordinary survivor payments");
            check(state.adjust(result,1,true,r,rules).pointDeltas().equals(expected),"full blood-battle adjustment did not preserve per-component liability");
        }
    }
    public static void main(String[] args) {
        optionsAndScores();kanDoraAndResponsibility();riichiAndFinalDeposits();actionRestrictions();robbedKanDoesNotReveal();retiredCompositeResponsibility();
        System.out.println("PASS: JPML/MLeague profile preservation, dora/ippatsu/counted limits, immediate kan, exact compound responsibility, late/negative riichi and tied final deposits");
    }
}
