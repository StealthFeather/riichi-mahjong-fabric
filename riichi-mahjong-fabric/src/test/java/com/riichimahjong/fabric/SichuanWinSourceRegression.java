package com.riichimahjong.fabric;

import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.sichuan.SichuanRules;
import com.themahjong.sichuan.SichuanState;
import com.themahjong.yaku.WinResult;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** A continuing blood battle must retain its accepted source and yield before another player acts. */
public final class SichuanWinSourceRegression {
    private static List<TheMahjongTile> waiting() {
        var hand=new ArrayList<TheMahjongTile>();
        for(char rank:"1234569".toCharArray())hand.add(tile(0,rank-'0'));
        for(char rank:"234678".toCharArray())hand.add(tile(1,rank-'0'));
        return hand;
    }
    private static List<MahjongPlayerInterface> humans() {
        return Stream.generate(()->(MahjongPlayerInterface)new MahjongHumanPlayer()).limit(4).toList();
    }
    private static TheMahjongDriver rig(List<TheMahjongPlayer> players, int turn, TheMahjongTile drawn) {
        var base=SichuanRules.newMatch(2,false,false,true);
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,TheMahjongRound.State.TURN,
                turn,-1,TheMahjongRound.ActiveTile.drawn(drawn),Collections.nCopies(20,tile(0,8)),
                List.of(),List.of(),List.of(),0,players,List.of());
        var match=new TheMahjongMatch(4,base.startingPoints(),base.targetPoints(),2,
                TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),round);
        var driver=new TheMahjongDriver(match,humans(),new Random(5),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(turn),false,OptionalInt.empty()));
        driver.restoreSichuanState(new SichuanState.Snapshot(SichuanState.Stage.PLAY,1,
                List.of(List.of(),List.of(),List.of(),List.of()),Set.of(),List.of(2,2,2,2),
                List.of(),List.of(),false,-1));
        return driver;
    }
    private static TheMahjongDriver rig(int turn) {
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<4;seat++) {
            var hand=waiting();if(seat==turn)hand.add(tile(0,9));
            players.add(player(hand));
        }
        return rig(players,turn,tile(0,9));
    }
    private static PlayerAction action(TheMahjongDriver driver,int seat,Class<? extends PlayerAction> type) {
        return driver.legalActions(seat).stream().filter(type::isInstance).findFirst().orElseThrow();
    }
    private static void persistence(TheMahjongDriver driver) {
        for(boolean legacy:new boolean[]{false,true}) {
            var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
            DriverNbt.writeDriver(out,driver);var tag=out.buildResult();
            if(legacy)for(var win:tag.getCompoundOrEmpty("sichuan").getListOrEmpty("wins").compoundStream().toList())
                win.remove("sourceSeat");
            var restored=DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,
                    HolderLookup.Provider.create(Stream.empty()),tag),humans(),new Random(5));
            check(restored.sichuanState().wins().equals(driver.sichuanState().wins()),
                    "winner source was lost in "+(legacy?"legacy":"current")+" NBT");
        }
    }
    private static void selfDrawAndRonSources() {
        for(int source=0;source<4;source++) {
            var driver=rig(source);
            act(driver,source,action(driver,source,PlayerAction.DeclareTsumo.class));
            var win=driver.sichuanState().wins().getFirst();
            check(win.sourceSeat()==source&&win.selfDrawn(),"self draw lost its originating seat");
            check(driver.acceptedWinningTile() instanceof TheMahjongRound.ActiveTile.Drawn,
                    "continuing self draw lost its accepted active tile");
            check(driver.winningTile() instanceof TheMahjongRound.ActiveTile.None,
                    "legacy winningTile phase gate changed");
            persistence(driver);

            driver=rig(source);act(driver,source,new PlayerAction.Discard(tile(0,9)));
            int winner=(source+1)%4;
            var claims=(MatchPhase.AwaitingClaims)driver.currentPhase();
            for(int seat:claims.pendingSeats())driver.submitAction(seat,seat==winner
                    ?action(driver,seat,PlayerAction.DeclareRon.class):PlayerAction.Pass.INSTANCE);
            driver.advance(.05);win=driver.sichuanState().wins().getFirst();
            check(win.sourceSeat()==source&&!win.selfDrawn(),"ron lost its discarder source");
            persistence(driver);
        }
    }
    private static void sameAdvanceRonYields() {
        var driver=rig(0);var draws=new AtomicInteger();
        for(int seat=1;seat<4;seat++) {
            final int own=seat;
            driver.replacePlayer(seat,(request,seconds)->{
                var ron=request.legalActions().stream().filter(a->a instanceof PlayerAction.DeclareRon).findFirst();
                if(own==1&&ron.isPresent())return ron;
                if(request.legalActions().contains(PlayerAction.Draw.INSTANCE)) {
                    draws.incrementAndGet();return Optional.of(PlayerAction.Draw.INSTANCE);
                }
                return request.legalActions().contains(PlayerAction.Pass.INSTANCE)
                        ?Optional.of(PlayerAction.Pass.INSTANCE):Optional.empty();
            });
        }
        act(driver,0,new PlayerAction.Discard(tile(0,9)));
        check(driver.sichuanState().wins().size()==1,"same-frame bot ron was not accepted");
        check(draws.get()==0&&driver.currentRound().liveWall().size()==20,
                "driver polled another player before the accepted win could be displayed");
        check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw next&&next.seat()==2,
                "win yield did not preserve the next legal phase");
        check(driver.acceptedWinningTile() instanceof TheMahjongRound.ActiveTile.HeldDiscard held
                &&held.tile().equals(tile(0,9)),"later actions overwrote the accepted ron source");
        driver.advance(.05);
        check(draws.get()==1&&driver.currentRound().liveWall().size()==19,
                "the yielded driver did not resume normally on a later frame");
    }
    private static void robKanSource() {
        var discard=tile(0,4);
        var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,discard),0,1,0);
        var own=new ArrayList<TheMahjongTile>();own.add(discard);
        for(char rank:"1234567899".toCharArray())own.add(tile(1,rank-'0'));
        var waiting=new ArrayList<TheMahjongTile>();
        for(char rank:"12356".toCharArray())waiting.add(tile(0,rank-'0'));
        for(char rank:"12367899".toCharArray())waiting.add(tile(1,rank-'0'));
        var driver=rig(List.of(new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,
                own,List.of(pon),List.of(),List.of()),player(waiting),player(waiting()),player(waiting())),0,discard);
        driver.replacePlayer(1,(request,seconds)->request.legalActions().stream()
                .filter(a->a instanceof PlayerAction.DeclareChankan).findFirst());
        for(int seat=2;seat<4;seat++)driver.replacePlayer(seat,(request,seconds)->Optional.of(PlayerAction.Pass.INSTANCE));
        act(driver,0,action(driver,0,PlayerAction.Kakan.class));
        check(driver.sichuanState().wins().getFirst().sourceSeat()==0,"same-frame chankan lost its source");
        check(driver.acceptedWinningTile() instanceof TheMahjongRound.ActiveTile.HeldKakan,
                "same-frame chankan was mistaken for an ordinary discard");
        persistence(driver);
    }
    private static void ambiguousLegacySource() {
        var invalid=new WinResult(List.of(),List.of(),1,0,0,List.of(2000,-1000,-1000,0));
        check(new SichuanState.Win(0,tile(0,9),false,invalid).sourceSeat()==-1,
                "ambiguous legacy transfers invented a discarder");
    }
    public static void main(String[] args) {
        selfDrawAndRonSources();sameAdvanceRonYields();robKanSource();ambiguousLegacySource();
        System.out.println("PASS: continuing Sichuan wins retain tsumo/ron/robbed-kan sources, yield before later actions, and preserve current/legacy NBT");
    }
}
