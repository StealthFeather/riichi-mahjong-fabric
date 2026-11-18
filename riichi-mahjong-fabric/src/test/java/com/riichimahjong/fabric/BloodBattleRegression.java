package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.riichimahjong.themahjongcompat.MatchNbt;
import com.themahjong.*;
import com.themahjong.TheMahjongTile.Suit;
import com.themahjong.driver.*;
import com.themahjong.sichuan.SichuanRules;
import com.themahjong.sichuan.SichuanState;
import com.themahjong.yaku.WinResult;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Verify the optional blood-battle boundary through real accepted Sichuan actions and saved rules. */
public final class BloodBattleRegression {
    private static TheMahjongTile tile(Suit suit,int rank) { return new TheMahjongTile(suit,rank,false); }
    private static List<TheMahjongTile> hand(String man,String pin) {
        var out=new ArrayList<TheMahjongTile>();
        for(char rank:man.toCharArray())out.add(tile(Suit.MANZU,rank-'0'));
        for(char rank:pin.toCharArray())out.add(tile(Suit.PINZU,rank-'0'));
        return out;
    }
    private static List<TheMahjongTile> waiting() { return hand("1234569","234678"); }
    private static TheMahjongPlayer player(List<TheMahjongTile> hand) {
        return new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(),List.of(),List.of());
    }
    private static List<MahjongPlayerInterface> humans() {
        return Stream.generate(()->(MahjongPlayerInterface)new MahjongHumanPlayer()).limit(4).toList();
    }
    private static TheMahjongDriver rig(boolean bloodBattle,List<TheMahjongPlayer> players,TheMahjongTile drawn) {
        var base=SichuanRules.newMatch(2,false,false,bloodBattle);
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,TheMahjongRound.State.TURN,
                0,-1,TheMahjongRound.ActiveTile.drawn(drawn),Collections.nCopies(20,tile(Suit.MANZU,9)),
                List.of(),List.of(),List.of(),0,players,List.of());
        var match=new TheMahjongMatch(4,base.startingPoints(),base.targetPoints(),2,
                TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),round);
        var driver=new TheMahjongDriver(match,humans(),new Random(5),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
        driver.restoreSichuanState(new SichuanState.Snapshot(SichuanState.Stage.PLAY,1,
                List.of(List.of(),List.of(),List.of(),List.of()),Set.of(),List.of(2,2,2,2),
                List.of(),List.of(),false,-1));
        return driver;
    }
    private static TheMahjongDriver rig(boolean bloodBattle) {
        var complete=new ArrayList<>(waiting());complete.add(tile(Suit.MANZU,9));
        return rig(bloodBattle,List.of(player(complete),player(waiting()),player(waiting()),player(waiting())),tile(Suit.MANZU,9));
    }
    private static PlayerAction action(TheMahjongDriver driver,int seat,Class<? extends PlayerAction> type) {
        return driver.legalActions(seat).stream().filter(type::isInstance).findFirst().orElseThrow();
    }
    private static void act(TheMahjongDriver driver,int seat,PlayerAction action) {
        driver.submitAction(seat,action);driver.advance(.05);
    }
    private static List<Integer> points(TheMahjongDriver driver) {
        return driver.currentRound().players().stream().map(TheMahjongPlayer::points).toList();
    }
    private static List<Integer> add(List<Integer> before,WinResult result) {
        var out=new ArrayList<>(before);
        for(int seat=0;seat<out.size();seat++)out.set(seat,out.get(seat)+result.pointDeltas().get(seat));
        return List.copyOf(out);
    }
    private static CompoundTag save(TheMahjongDriver driver) {
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(out,driver);return out.buildResult();
    }
    private static TheMahjongDriver reload(CompoundTag tag) {
        return DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),tag),humans(),new Random(5));
    }
    private static void terminalAndStable(TheMahjongDriver driver,int winners,List<Integer> expected) {
        check(driver.currentPhase() instanceof MatchPhase.RoundEnded ended&&ended.winResults().size()==winners,
                "single-hand mode failed to finish all simultaneous wins");
        check(driver.currentRound().state()==TheMahjongRound.State.ENDED&&points(driver).equals(expected),
                "win settlement was lost, duplicated or confused with exhaustive-draw settlement");
        int wall=driver.currentRound().liveWall().size();
        driver.advance(3);driver.advance(3);
        for(int seat=0;seat<4;seat++)check(driver.legalActions(seat).isEmpty(),"terminal hand still accepts actions");
        check(points(driver).equals(expected)&&driver.currentRound().liveWall().size()==wall,
                "ended hand advanced or applied its win twice");
        var restored=reload(save(driver));
        check(!restored.rules().bloodBattle()&&points(restored).equals(expected)
                &&restored.currentPhase() instanceof MatchPhase.RoundEnded,"single-hand flag or terminal result lost on reload");
    }
    private static void defaultAndRuleFlags() {
        check(!SichuanRules.newMatch(2,false,false).ruleSet().bloodBattle(),"new Sichuan factory enables blood battle by default");
        check(!RuleSetPreset.SICHUAN_4P.newMatch().ruleSet().bloodBattle(),"new Sichuan preset enables blood battle by default");
        for(var preset:RuleSetPreset.values())for(boolean enabled:new boolean[]{false,true})for(boolean exchange:new boolean[]{false,true})for(boolean jokers:new boolean[]{false,true}) {
            var match=preset.newMatch(MatchLength.EAST,exchange,jokers,enabled);
            check(match.ruleSet().bloodBattle()==enabled&&match.ruleSet().exchangeThree()==exchange
                    &&match.ruleSet().jokersEnabled()==jokers,"shared variants changed each other's switches for "+preset);
            var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);MatchNbt.writeMatch(out,match);
            var restored=MatchNbt.readMatch(TagValueInput.create(ProblemReporter.DISCARDING,
                    HolderLookup.Provider.create(Stream.empty()),out.buildResult()));
            check(restored.equals(match),"explicit blood-battle flag failed real match NBT round-trip");
        }
    }
    private static void firstTsumoEnds() {
        var driver=rig(false);var action=(PlayerAction.DeclareTsumo)action(driver,0,PlayerAction.DeclareTsumo.class);
        var expected=add(points(driver),action.result());
        act(driver,0,action);terminalAndStable(driver,1,expected);
    }
    private static void acceptedRonGroupEnds() {
        for(int winnerCount=1;winnerCount<=3;winnerCount++) {
            var driver=rig(false);act(driver,0,new PlayerAction.Discard(tile(Suit.MANZU,9)));
            var claims=(MatchPhase.AwaitingClaims)driver.currentPhase();
            var expected=points(driver);
            for(int seat:claims.pendingSeats()) {
                if(seat<=winnerCount) {
                    var ron=(PlayerAction.DeclareRon)action(driver,seat,PlayerAction.DeclareRon.class);
                    expected=add(expected,ron.result());driver.submitAction(seat,ron);
                }else driver.submitAction(seat,PlayerAction.Pass.INSTANCE);
            }
            driver.advance(.05);
            terminalAndStable(driver,winnerCount,expected);
            check(driver.sichuanState().wins().size()==winnerCount,"first ron dropped another accepted winner");
        }
    }
    private static void robbedKanEnds() {
        var discard=tile(Suit.MANZU,4);
        var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,discard),0,1,0);
        var own=hand("4","1234567899");
        var upgraded=new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,
                own,List.of(pon),List.of(),List.of());
        var driver=rig(false,List.of(upgraded,player(hand("12356","12367899")),player(waiting()),player(waiting())),discard);
        act(driver,0,action(driver,0,PlayerAction.Kakan.class));
        var claims=(MatchPhase.AwaitingClaims)driver.currentPhase();var expected=points(driver);
        for(int seat:claims.pendingSeats()) {
            if(seat==1) {
                var ron=(PlayerAction.DeclareChankan)action(driver,seat,PlayerAction.DeclareChankan.class);
                expected=add(expected,ron.result());driver.submitAction(seat,ron);
            }else driver.submitAction(seat,PlayerAction.Pass.INSTANCE);
        }
        driver.advance(.05);terminalAndStable(driver,1,expected);
        check(driver.currentRound().players().getFirst().melds().getFirst() instanceof TheMahjongMeld.Pon,
                "robbed kan paid a kan transfer or failed to restore its original pon");
    }
    private static void enabledModeContinues() {
        var driver=rig(true);
        for(int seat=0;seat<3;seat++) {
            if(seat>0)act(driver,seat,PlayerAction.Draw.INSTANCE);
            var tsumo=(PlayerAction.DeclareTsumo)action(driver,seat,PlayerAction.DeclareTsumo.class);
            var before=points(driver);
            check(tsumo.result().han()==1&&tsumo.result().pointDeltas().get(seat)==2000*(3-seat),
                    "continuous Sichuan self draw omitted a surviving payer");
            act(driver,seat,tsumo);
            for(int other=0;other<4;other++) {
                int expected=other<seat?0:other==seat?2000*(3-seat):-2000;
                check(points(driver).get(other)==before.get(other)+expected,
                        "continuous Sichuan self draw failed exact balance transfer at seat "+other);
            }
            check(driver.sichuanState().winners().size()==seat+1,"blood battle lost an accepted winner");
            if(seat<2) {
                check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw draw&&draw.seat()==seat+1,
                        "enabled blood battle ended before three winners");
                var restored=reload(save(driver));
                check(restored.rules().bloodBattle()&&restored.sichuanState().wins().equals(driver.sichuanState().wins()),
                        "enabled blood-battle continuation failed save/reload");
                driver=restored;
            }else check(driver.currentPhase() instanceof MatchPhase.RoundEnded ended&&ended.winResults().size()==3,
                    "enabled blood battle did not end at three winners");
        }
        check(points(driver).stream().mapToInt(Integer::intValue).sum()==100000,"blood battle created points");
    }
    private static void legacyMatchRules() {
        var old=save(rig(true));old.getCompoundOrEmpty("match").remove("bloodBattle");
        var restored=reload(old);
        check(restored.rules().sichuan()&&restored.rules().bloodBattle(),"legacy active Sichuan game lost its existing blood-battle rule");
        var match=RuleSetPreset.MAHJONG_SOUL_4P.newMatch();
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);MatchNbt.writeMatch(out,match);var tag=out.buildResult();tag.remove("bloodBattle");
        var normal=MatchNbt.readMatch(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),tag));
        check(!normal.ruleSet().bloodBattle(),"legacy riichi match acquired blood battle");
    }
    public static void main(String[] args) {
        defaultAndRuleFlags();firstTsumoEnds();acceptedRonGroupEnds();robbedKanEnds();enabledModeContinues();legacyMatchRules();
        System.out.println("PASS: Sichuan blood battle defaults off; tsumo, single/multiple ron and robbed kan end once while explicit blood battle continues through three winners and legacy saved matches retain their rules");
    }
}
