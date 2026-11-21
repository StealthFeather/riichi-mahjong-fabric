package com.riichimahjong.fabric;

import com.riichimahjong.themahjongcompat.DriverNbt;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.themahjong.*;
import com.themahjong.compat.DrawSettlement;
import com.themahjong.driver.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Exercise the same progression decision used by the table against accepted driver actions. */
public final class RoundProgressionRegression {
    private static final TheMahjongTile SPARE = tile(4,2);
    private static final TheMahjongTile WINNING = tile(4,1);

    private static TheMahjongPlayer fixture(boolean ready, boolean river) {
        var hand = new ArrayList<>(waiting(1));
        if (!ready) hand.set(0, SPARE);
        var player = new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,
                hand,List.of(),river ? List.of(new TheMahjongDiscard(tile(2,5),false)) : List.of(),List.of());
        check(DrawSettlement.inTenpai(player)==ready,"invalid readiness fixture");
        return player;
    }

    private static TheMahjongPlayer addTile(TheMahjongPlayer player, TheMahjongTile drawn) {
        var hand = new ArrayList<>(player.currentHand());hand.add(drawn);
        return new TheMahjongPlayer(player.points(),player.riichiState(),player.ippatsuEligible(),
                hand,player.melds(),player.discards(),player.temporaryFuritenTiles(),
                player.riichiPermanentFuriten(),player.kitaCount());
    }

    private static List<MahjongPlayerInterface> humans(int count) {
        return java.util.stream.IntStream.range(0,count)
                .mapToObj(seat -> (MahjongPlayerInterface)new MahjongHumanPlayer()).toList();
    }

    private static TheMahjongDriver rig(List<TheMahjongPlayer> players,int dealer,int turn,
            List<TheMahjongTile> wall,TheMahjongTile drawn,TheMahjongRuleSet rules) {
        int count=players.size();var indicators=Collections.nCopies(5,tile(4,3));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,dealer+1,2,2,dealer,
                TheMahjongRound.State.TURN,turn,-1,TheMahjongRound.ActiveTile.drawn(drawn),wall,
                Collections.nCopies(TheMahjongRound.rinshanTileCountFor(count),tile(4,3)),
                indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(count,25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                count==3 ? TheMahjongTileSet.standardSanma(false) : TheMahjongTileSet.standardRiichi(false),
                rules,List.of(),round);
        return new TheMahjongDriver(match,humans(count),new Random(9),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(turn),false,OptionalInt.empty()));
    }

    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        return reload(driver,false);
    }

    private static TheMahjongDriver reload(TheMahjongDriver driver,boolean legacy) {
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(out,driver);
        var tag=out.buildResult();
        if(legacy)tag.remove("abortiveDraw");
        var in=TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),tag);
        return DriverNbt.readDriver(in,humans(driver.match().playerCount()),new Random(9));
    }

    private static PlayerAction action(TheMahjongDriver driver,int seat,Class<? extends PlayerAction> kind) {
        return driver.legalActions(seat).stream().filter(kind::isInstance).findFirst()
                .orElseThrow(() -> new AssertionError("missing "+kind.getSimpleName()+" for seat "+seat));
    }

    private static List<Integer> points(TheMahjongRound round) {
        return round.players().stream().map(TheMahjongPlayer::points).toList();
    }

    private static void advanceAndCheck(TheMahjongDriver driver,boolean renchan,int honba) {
        advanceAndCheck(driver,renchan,honba,Map.of());
    }

    private static void advanceAndCheck(TheMahjongDriver driver,boolean renchan,int honba,Map<Integer,Integer> targets) {
        check(driver.currentPhase() instanceof MatchPhase.RoundEnded,"fixture did not end a round");
        var ended=driver.currentRound();var policy=RoundProgression.next(driver);
        check(policy.renchan()==renchan,"incorrect renchan for dealer "+ended.dealerSeat());
        check(policy.nextHonba()==honba,"incorrect next honba: "+policy.nextHonba());
        driver.advanceRound(policy.renchan(),policy.nextHonba(),targets);
        check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw,"next round did not start");
        var next=driver.currentRound();int count=next.players().size();
        int dealer=renchan ? ended.dealerSeat() : (ended.dealerSeat()+1)%count;
        boolean nextWind=!renchan && dealer==0;
        check(next.dealerSeat()==dealer,"dealer changed contrary to progression policy");
        check(next.handNumber()==(renchan ? ended.handNumber() : nextWind ? 1 : ended.handNumber()+1),
                "hand number did not follow dealer rotation");
        check(next.roundWind().ordinal()==ended.roundWind().ordinal()+(nextWind ? 1 : 0),
                "round wind did not follow dealer rotation");
        check(next.honba()==honba,"driver lost the computed honba");
        check(next.riichiSticks()==ended.riichiSticks(),"next hand changed carried deposits");
        check(points(next).equals(points(ended)),"next hand erased or reapplied settlement");
        check(driver.match().completedRounds().getLast().equals(ended),"history lost settled round");
        check(!driver.abortiveDraw() && !driver.snapshot().abortiveDraw(),"abortive marker leaked into next hand");
    }

    private static void specifiedDeals() {
        for (boolean renchan : List.of(true, false)) {
            int dealer = 3;
            var players = new ArrayList<TheMahjongPlayer>();
            for (int seat = 0; seat < 4; seat++) players.add(fixture(seat == dealer ? renchan : !renchan, true));
            players.set(dealer, addTile(players.get(dealer), SPARE));
            var driver = rig(players, dealer, dealer, List.of(), SPARE, TheMahjongRuleSet.mahjongSoul());
            act(driver, dealer, new PlayerAction.Discard(SPARE));
            passClaims(driver);
            var observed = new ArrayList<TheMahjongRound>();
            driver.setStateObserver(state -> observed.add(state.currentRound()));
            advanceAndCheck(driver, renchan, 3, Map.of(2, 0, 1, 6));
            var next = driver.currentRound();
            check(observed.equals(List.of(next)), "replay observed an intermediate deal");
            check(next.players().stream().allMatch(p -> p.currentHand().size() == 13), "incorrect opening hand size");
            check(DrawSettlement.inTenpai(next.players().get(2)) && !DrawSettlement.inTenpai(next.players().get(1)),
                    "scheduled next hand did not reach the requested players");
            var tile = next.liveWall().getFirst();
            act(driver, next.dealerSeat(), PlayerAction.Draw.INSTANCE);
            check(driver.currentRound().activeTile().equals(TheMahjongRound.ActiveTile.drawn(tile))
                    && driver.currentRound().players().get(next.dealerSeat()).currentHand().size() == 14,
                    "dealer did not take the ordinary first draw");
        }
    }

    private static void exhaustiveDraws() {
        for(int count:new int[]{3,4})for(int dealer=0;dealer<count;dealer++)
                for(int mask=0;mask<(1<<count);mask++) {
            var players=new ArrayList<TheMahjongPlayer>();
            for(int seat=0;seat<count;seat++)players.add(fixture((mask&(1<<seat))!=0,true));
            players.set(dealer,addTile(players.get(dealer),SPARE));
            var presets=count==3 ? List.of(TheMahjongRuleSet.mahjongSoulSanma(),TheMahjongRuleSet.tenhouSanma())
                    : List.of(TheMahjongRuleSet.mahjongSoul(),TheMahjongRuleSet.tenhou(),TheMahjongRuleSet.wrc());
            for(var rules:presets) {
                var driver=rig(players,dealer,dealer,List.of(),SPARE,rules);
                act(driver,dealer,new PlayerAction.Discard(SPARE));passClaims(driver);
                check(driver.currentPhase() instanceof MatchPhase.RoundEnded ended && ended.winResults().isEmpty(),
                        "last discard did not trigger exhaustive draw");
                check(!driver.abortiveDraw(),"ordinary exhaustion classified as abortive");
                int ready=Integer.bitCount(mask);
                for(int seat=0;seat<count;seat++) {
                    int delta=ready==0 || ready==count ? 0
                            : (mask&(1<<seat))!=0 ? 3000/ready : -3000/(count-ready);
                    check(driver.currentRound().players().get(seat).points()==25000+delta,
                            "unexpected exhaustive settlement for "+count+"p mask "+mask);
                }
                // Zero/all-tenpai cases deliberately have identical deltas but opposite dealer outcomes.
                boolean dealerReady=(mask&(1<<dealer))!=0;
                check(DrawSettlement.inTenpai(driver.currentRound().players().get(dealer))==dealerReady,
                        "discard changed intended dealer readiness");
                if(mask==0 || mask==(1<<count)-1) {
                    check(!reload(driver,true).abortiveDraw(),"legacy exhaustive save classified as abortive");
                    driver=reload(driver);
                }
                advanceAndCheck(driver,dealerReady,3);
            }
        }
    }

    private static TheMahjongDriver winRig(int count,int dealer,int turn,TheMahjongTile draw,
            List<TheMahjongTile> wall,TheMahjongRuleSet rules) {
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<count;seat++) {
            var base=fixture(true,true);
            // A double ron must not end this progression fixture by bankrupting its discarder.
            players.add(new TheMahjongPlayer(100000,base.riichiState(),false,
                    base.currentHand(),base.melds(),base.discards(),List.of()));
        }
        players.set(turn,addTile(players.get(turn),draw));
        return rig(players,dealer,turn,wall,draw,rules);
    }

    private static void submitRons(TheMahjongDriver driver,Set<Integer> winners) {
        check(driver.currentPhase() instanceof MatchPhase.AwaitingClaims,"discard opened no claim window");
        var claims=(MatchPhase.AwaitingClaims)driver.currentPhase();
        for(int seat:claims.pendingSeats())driver.submitAction(seat,winners.contains(seat)
                ? action(driver,seat,PlayerAction.DeclareRon.class) : PlayerAction.Pass.INSTANCE);
        driver.advance(.05);
        check(driver.currentPhase() instanceof MatchPhase.RoundEnded,"ron did not resolve");
    }

    private static void wins() {
        for(int count:new int[]{3,4})for(int dealer=0;dealer<count;dealer++) {
            var rules=count==3 ? TheMahjongRuleSet.mahjongSoulSanma() : TheMahjongRuleSet.mahjongSoul();
            for(boolean dealerWins:new boolean[]{false,true}) {
                int winner=dealerWins ? dealer : (dealer+1)%count;
                var driver=winRig(count,dealer,winner,WINNING,Collections.nCopies(12,SPARE),rules);
                act(driver,winner,action(driver,winner,PlayerAction.DeclareTsumo.class));
                advanceAndCheck(driver,dealerWins,dealerWins ? 3 : 0);

                int discarder=(winner+1)%count;
                driver=winRig(count,dealer,discarder,WINNING,Collections.nCopies(12,SPARE),rules);
                act(driver,discarder,new PlayerAction.Discard(WINNING));submitRons(driver,Set.of(winner));
                advanceAndCheck(driver,dealerWins,dealerWins ? 3 : 0);
            }
            // Claim priority is clockwise from the discarder: the dealer is the second winner.
            int discarder=Math.floorMod(dealer-2,count),first=Math.floorMod(dealer-1,count);
            var driver=winRig(count,dealer,discarder,WINNING,Collections.nCopies(12,SPARE),rules);
            act(driver,discarder,new PlayerAction.Discard(WINNING));submitRons(driver,Set.of(first,dealer));
            var wins=((MatchPhase.RoundEnded)driver.currentPhase()).winResults();
            check(wins.size()==2 && wins.getFirst().pointDeltas().get(dealer)<=0
                    && wins.get(1).pointDeltas().get(dealer)>0,"dealer was not the second ron result");
            advanceAndCheck(driver,true,3);
        }
    }

    private static void kyuushuAndPersistence() {
        var orphans=List.of(tile(0,1),tile(0,9),tile(1,1),tile(1,9),tile(2,1),tile(2,9),
                tile(3,1),tile(3,2),tile(3,3),tile(3,4),tile(4,1),tile(4,2),tile(4,3),tile(1,2));
        for(int count:new int[]{3,4})for(int dealer=0;dealer<count;dealer++) {
            int turn=(dealer+1)%count;var players=new ArrayList<TheMahjongPlayer>();
            for(int seat=0;seat<count;seat++)players.add(fixture(false,false));
            players.set(turn,player(orphans));
            var rules=count==3 ? TheMahjongRuleSet.mahjongSoulSanma() : TheMahjongRuleSet.mahjongSoul();
            var blood=rig(players,dealer,turn,Collections.nCopies(12,SPARE),tile(1,2),
                    (count==3 ? RuleSetPreset.MAHJONG_SOUL_SANMA_3P : RuleSetPreset.MAHJONG_SOUL_4P)
                            .newMatch(MatchLength.SOUTH,false,false,true).ruleSet());
            act(blood,turn,action(blood,turn,PlayerAction.KyuushuAbort.class));
            check(blood.abortiveDraw(),"blood-battle nine-terminals declaration did not abort the hand");
            advanceAndCheck(reload(blood),false,3);
            // As dealer, the same declarer holds all thirteen orphans and a spare on their draw.
            var readyDealer=rig(players,turn,turn,Collections.nCopies(12,SPARE),tile(1,2),blood.rules());
            act(readyDealer,turn,action(readyDealer,turn,PlayerAction.KyuushuAbort.class));
            advanceAndCheck(reload(readyDealer),true,3);

            var driver=rig(players,dealer,turn,Collections.nCopies(12,SPARE),tile(1,2),rules);
            act(driver,turn,action(driver,turn,PlayerAction.KyuushuAbort.class));
            check(!DrawSettlement.inTenpai(driver.currentRound().players().get(dealer)),"abortive dealer was listening");
            check(driver.abortiveDraw() && driver.snapshot().abortiveDraw(),"kyuushu lost its abortive marker");
            check(points(driver.currentRound()).stream().allMatch(p -> p==25000),"abortive draw charged noten");
            check(reload(driver,true).abortiveDraw(),"legacy kyuushu save lost recoverable abortive reason");
            driver=reload(driver);
            check(driver.abortiveDraw(),"save/reload erased abortive-draw reason");
            advanceAndCheck(driver,true,3);
            check(!reload(driver).abortiveDraw(),"new-round save retained stale abortive reason");
        }
    }

    private static void lastTileTripleRon() {
        int dealer=0;var players=new ArrayList<TheMahjongPlayer>();
        players.add(addTile(fixture(false,true),WINNING));
        for(int seat=1;seat<4;seat++)players.add(fixture(true,true));
        var driver=rig(players,dealer,dealer,List.of(),WINNING,TheMahjongRuleSet.tenhou());
        act(driver,dealer,new PlayerAction.Discard(WINNING));submitRons(driver,Set.of(1,2,3));
        check(((MatchPhase.RoundEnded)driver.currentPhase()).winResults().isEmpty(),"triple ron was not abortive");
        check(driver.currentRound().liveWall().isEmpty(),"triple ron fixture was not on the last tile");
        check(!DrawSettlement.inTenpai(driver.currentRound().players().get(dealer)),"triple-ron dealer accidentally waits");
        check(driver.abortiveDraw(),"last-tile triple ron confused with exhaustive draw");
        driver=reload(driver);
        check(driver.abortiveDraw(),"last-tile abortive reason lost on reload");
        advanceAndCheck(driver,true,3);
    }

    public static void main(String[] args) {
        specifiedDeals();
        System.out.println("PASS: targeted deals on dealer repeat and wind rotation preserve points, deposits, history and the first draw");
        exhaustiveDraws();
        System.out.println("PASS: all 3p/4p readiness distributions and dealer seats advance with correct dealer, hand, wind, honba, scores and deposits");
        wins();
        System.out.println("PASS: dealer/non-dealer tsumo and ron, including dealer as the second double-ron winner, determine renchan and honba");
        kyuushuAndPersistence();lastTileTripleRon();
        System.out.println("PASS: kyuushu and last-tile triple ron keep noten dealers, survive save/reload and clear abortive state in the next hand");
    }
}
