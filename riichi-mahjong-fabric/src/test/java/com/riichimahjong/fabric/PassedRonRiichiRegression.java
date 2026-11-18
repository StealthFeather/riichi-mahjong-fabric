package com.riichimahjong.fabric;

import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer.TableButton;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.themahjongcompat.DriverNbt;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import java.util.stream.Stream;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.variants.ExchangeThreeState;
import com.themahjong.variants.Jokers;
import com.themahjong.yaku.Furiten;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** A real confirmed skipped ron must never remove the next closed tenpai riichi opportunity. */
public final class PassedRonRiichiRegression {
    private static final TheMahjongTile WHITE=tile(4,1),GREEN=tile(4,2);
    private record Rig(TheMahjongDriver driver,List<MahjongTableHumanPlayer> humans) {}
    private static Rig rig(RuleSetPreset preset,int options) {
        int count=preset.playerCount();boolean exchange=(options&1)!=0,jokers=(options&2)!=0,blood=(options&4)!=0;
        var base=preset.newMatch(MatchLength.SOUTH,exchange,jokers,blood);
        var players=new ArrayList<TheMahjongPlayer>();var humans=new ArrayList<MahjongTableHumanPlayer>();
        for(int seat=0;seat<count;seat++) {
            var hand=new ArrayList<>(waiting(seat%3));
            if(jokers)hand.set(0,Jokers.TILE);
            if(seat==0)hand.add(WHITE);
            players.add(new TheMahjongPlayer(base.startingPoints(),TheMahjongPlayer.RiichiState.NONE,false,
                    hand,List.of(),List.of(new TheMahjongDiscard(tile(2,8),false)),List.of()));
            var human=new MahjongTableHumanPlayer();human.setOccupant(new UUID(77,seat+1));humans.add(human);
        }
        var wall=new ArrayList<>(Collections.nCopies(32,GREEN));wall.set(count,WHITE);
        var indicators=Collections.nCopies(5,tile(4,3));
        var round=new TheMahjongRound(TheMahjongTile.Wind.SOUTH,2,0,0,0,TheMahjongRound.State.TURN,0,-1,
                TheMahjongRound.ActiveTile.drawn(WHITE),wall,Collections.nCopies(4,GREEN),indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(count,base.startingPoints(),base.targetPoints(),2,TheMahjongMatch.State.IN_ROUND,
                base.tileSet(),base.ruleSet(),List.of(),round);
        var driver=new TheMahjongDriver(match,new ArrayList<MahjongPlayerInterface>(humans),new Random(90),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
        if(exchange)driver.restoreExchangeState(new ExchangeThreeState.Snapshot(count,1,
                Collections.nCopies(count,List.of()),Set.of(),true));
        return new Rig(driver,humans);
    }
    private static List<TableButton> buttons(Rig rig,int seat) {
        return MahjongTableHumanPlayer.tableButtons(rig.driver(),seat,rig.humans().get(seat).riichiPending());
    }
    private static <T extends TableButton> T button(Rig rig,int seat,Class<T> kind) {
        return buttons(rig,seat).stream().filter(kind::isInstance).map(kind::cast).findFirst()
                .orElseThrow(()->new AssertionError("missing "+kind.getSimpleName()+" at "+rig.driver().currentPhase()+" legal="+rig.driver().legalActions(seat)));
    }
    private static Rig reload(Rig original) {
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);DriverNbt.writeDriver(out,original.driver());
        var humans=new ArrayList<MahjongTableHumanPlayer>();
        for(int seat=0;seat<original.humans().size();seat++) {
            var human=new MahjongTableHumanPlayer();human.setOccupant(original.humans().get(seat).occupant().orElseThrow());humans.add(human);
        }
        var restored=DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),out.buildResult()),new ArrayList<MahjongPlayerInterface>(humans),new Random(91));
        check(restored.currentRound().equals(original.driver().currentRound()),"passed-ron NBT changed hands or balances");
        return new Rig(restored,humans);
    }
    private static void confirmPass(Rig rig,int seat) {
        var human=rig.humans().get(seat);
        check(human.selectTableButton(new TableButton.SubmitAction(PlayerAction.Pass.INSTANCE),rig.driver(),seat,null),"cannot open ron pass");
        check(human.selectTableButton(button(rig,seat,TableButton.ConfirmSkip.class),rig.driver(),seat,null),"cannot confirm ron pass");
    }
    private static void realPassedRon(RuleSetPreset preset,int options,boolean otherWinner,boolean reload) {
        var rig=rig(preset,options);var driver=rig.driver();var human=rig.humans().get(1);
        String context=preset+" options="+options+" otherWinner="+otherWinner+" reload="+reload;
        act(driver,0,new PlayerAction.Discard(WHITE));
        check(driver.legalActions(1).stream().anyMatch(a->a instanceof PlayerAction.DeclareRon),"no real ron: "+context);
        var pass=new TableButton.SubmitAction(PlayerAction.Pass.INSTANCE);
        check(human.selectTableButton(pass,driver,1,null),"cannot open real ron skip confirmation: "+context);
        var confirm=button(rig,1,TableButton.ConfirmSkip.class);
        check(!human.hasQueued()&&!driver.currentRound().players().get(1).temporaryFuriten(),"opening prompt committed skip: "+context);
        check(human.selectTableButton(confirm,driver,1,null)&&human.hasQueued(),"cannot confirm real skipped ron: "+context);
        if(otherWinner)driver.submitAction(2,driver.legalActions(2).stream().filter(a->a instanceof PlayerAction.DeclareRon).findFirst().orElseThrow());
        driver.advance(.05);passClaims(driver);
        if(otherWinner)check(driver.bloodBattleState().won(2),"other accepted winner did not retire: "+context);
        check(!human.hasQueued(),"accepted pass stayed in human queue: "+context);
        check(driver.currentRound().players().get(1).temporaryFuriten(),"real passed ron did not create temporary furiten: "+context);
        check(!Furiten.canRon(driver.currentRound().players().get(1),WHITE),"passed ron incorrectly remained legal before own turn: "+context);
        if(reload){rig=reload(rig);driver=rig.driver();human=rig.humans().get(1);}
        check(!human.selectTableButton(confirm,driver,1,null),"accepted/stale confirmation remained actionable: "+context);
        act(driver,1,PlayerAction.Draw.INSTANCE);
        check(driver.legalActions(1).contains(new PlayerAction.DiscardWithRiichi(GREEN)),"real passed ron removed next riichi action: "+context);
        check(buttons(rig,1).stream().noneMatch(b->b instanceof TableButton.ConfirmSkip||b instanceof TableButton.CancelSkip),
                "stale skip confirmation hid new riichi buttons: "+context);
        var toggle=button(rig,1,TableButton.RiichiToggle.class);
        check(human.selectTableButton(toggle,driver,1,null),"cannot select riichi after passed ron: "+context);
        int index=driver.currentRound().handDisplayOrder(1).indexOf(GREEN);check(index>=0,"drawn discard not visible");
        human.onCuteClick(new InteractKey.SeatSlot((byte)1,InteractKey.SeatSlot.AREA_HAND,(short)index),driver,1,null);
        check(human.hasQueued(),"riichi discard did not queue after passed ron: "+context);
        driver.advance(.05);passClaims(driver);
        var declared=driver.currentRound().players().get(1);
        check(declared.riichi()&&declared.points()==driver.match().startingPoints()-1000&&driver.currentRound().riichiSticks()==1,
                "passed-ron human riichi was not accepted/charged exactly once: "+context);
        check(!declared.temporaryFuriten()&&!declared.riichiPermanentFuriten(),
                "pre-riichi temporary furiten incorrectly survived own discard as permanent furiten: "+context);
        if(!otherWinner) {
            // A later skipped ron after riichi is permanent; preserve ron exclusion while allowing tsumo.
            act(driver,2,PlayerAction.Draw.INSTANCE);act(driver,2,new PlayerAction.Discard(WHITE));
            check(driver.legalActions(1).stream().anyMatch(a->a instanceof PlayerAction.DeclareRon),"next ron after pre-riichi temporary furiten should be restored: "+context);
            confirmPass(rig,1);driver.advance(.05);passClaims(driver);
            check(driver.currentRound().players().get(1).riichiPermanentFuriten(),"post-riichi skipped ron did not become permanent: "+context);
            for(int seat=3;seat<driver.match().playerCount();seat++) {
                act(driver,seat,PlayerAction.Draw.INSTANCE);act(driver,seat,new PlayerAction.Discard(GREEN));passClaims(driver);
            }
            act(driver,0,PlayerAction.Draw.INSTANCE);act(driver,0,new PlayerAction.Discard(GREEN));passClaims(driver);
            act(driver,1,PlayerAction.Draw.INSTANCE);
            check(!Furiten.canRon(driver.currentRound().players().get(1),WHITE),"permanent furiten was loosened to fix riichi: "+context);
            var tsumo=driver.legalActions(1).stream().filter(a->a instanceof PlayerAction.DeclareTsumo).findFirst()
                    .orElseThrow(()->new AssertionError("permanent furiten removed legal self draw: "+context));
            act(driver,1,tsumo);
            check(driver.rules().bloodBattle()?driver.bloodBattleState().won(1):driver.currentPhase() instanceof MatchPhase.RoundEnded,
                    "furiten self draw was not actually accepted: "+context);
        }
    }
    public static void main(String[] args) {
        int cases=0;
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN)
            for(int options=0;options<8;options++){realPassedRon(preset,options,false,false);cases++;}
        for(var preset:RuleSetPreset.values())if(preset.family()!=RuleSetPreset.Family.SICHUAN) {
            realPassedRon(preset,7,true,false);realPassedRon(preset,7,true,true);realPassedRon(preset,7,false,true);cases+=3;
        }
        System.out.println("PASS: "+cases+" actual two-click passed-ron human flows allow next riichi in South round across all presets and exchange/joker/blood options");
    }
}
