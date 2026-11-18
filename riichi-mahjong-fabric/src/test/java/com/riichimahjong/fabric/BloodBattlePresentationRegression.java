package com.riichimahjong.fabric;

import com.riichimahjong.fabric.wineffect.WinEffects;
import com.riichimahjong.fabric.wineffect.WinEffectContext;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.registry.ModBlockEntities;
import com.riichimahjong.registry.ModBlocks;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.variants.BloodBattleState;
import com.themahjong.variants.Jokers;
import com.themahjong.yaku.WinResult;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Continuing riichi winners stay private until terminal, then present once in accepted win order. */
public final class BloodBattlePresentationRegression {
    private static List<MahjongPlayerInterface> humans(int count) {
        return Stream.generate(()->(MahjongPlayerInterface)new MahjongHumanPlayer()).limit(count).toList();
    }
    private static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        var flag=Bootstrap.class.getDeclaredField("isBootstrapped");flag.setAccessible(true);
        check(!flag.getBoolean(null),"blood-battle display regression needs a fresh JVM");
        flag.setBoolean(null,true);
        try {
            var id=Identifier.fromNamespaceAndPath("riichi_mahjong","mahjong_table_new");
            Registry.register(BuiltInRegistries.BLOCK,id,ModBlocks.MAHJONG_TABLE.get());
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id,ModBlockEntities.MAHJONG_TABLE_BLOCK_ENTITY.get());
        } finally { flag.setBoolean(null,false); }
        Bootstrap.bootStrap();
    }
    private static MahjongTableBlockEntity table(TheMahjongDriver driver) throws Exception {
        var table=new MahjongTableBlockEntity(BlockPos.ZERO,ModBlocks.MAHJONG_TABLE.get().defaultBlockState());
        var field=MahjongTableBlockEntity.class.getDeclaredField("driver");field.setAccessible(true);field.set(table,driver);
        return table;
    }
    private static void invoke(MahjongTableBlockEntity table,String name) throws Exception {
        Method method=MahjongTableBlockEntity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(table);
    }
    private static void record(MahjongTableBlockEntity table,WinEffectContext context) throws Exception {
        Method method=MahjongTableBlockEntity.class.getDeclaredMethod("recordWinPresentation",WinEffectContext.class);
        method.setAccessible(true);method.invoke(table,context);
    }
    /** The same public-hand allowlist used by the renderer, without needing a running level. */
    private static PublicTable board(MahjongTableBlockEntity table) throws Exception {
        var visible=PublicTable.revealedSeats(table);var seats=new ArrayList<PublicTable.Seat>();
        var players=table.driver().currentRound().players();
        for(int seat=0;seat<players.size();seat++){
            var hand=players.get(seat).currentHand();var layout=winningLayout(table,seat);
            var order=layout.map(WinningHandDisplay.Layout::order).orElse(hand);
            var slots=layout.map(WinningHandDisplay.Layout::slots)
                    .orElseGet(()->java.util.stream.IntStream.range(0,hand.size()).boxed().toList());
            var gaps=layout.map(WinningHandDisplay.Layout::gapStarts).orElse(List.of());
            seats.add(new PublicTable.Seat(null,"BOT",seat,players.get(seat).points(),order.size(),slots,gaps,
                    visible.contains(seat)?order.stream().map(PublicTable::code).toList():List.of(),
                    List.of(),List.of(),0,false,false,false));
        }
        return new PublicTable(List.copyOf(seats),20,0,List.of(),List.of(),Set.of(),"","东一局",-1,0,null,1,136,false,52);
    }
    private static void stillPrivate(MahjongTableBlockEntity table,String event) throws Exception {
        invoke(table,"prepareBloodBattlePlayback");
        check(table.winEffectSession()==null&&table.completedWinPresentationSeats().isEmpty()
                        &&PublicTable.revealedSeats(table).isEmpty(),event+" revealed or animated an unfinished blood-battle hand");
        check(WinResultLabels.visibleResults(table.winResultLabels(),table.completedWinPresentationSeats(),board(table)).isEmpty(),
                event+" exposed han/points/yaku before the hand ended");
    }
    @SuppressWarnings("unchecked")
    private static Optional<WinningHandDisplay.Layout> winningLayout(MahjongTableBlockEntity table,int seat) throws Exception {
        Method method=MahjongTableBlockEntity.class.getDeclaredMethod("winningHandLayout",int.class);method.setAccessible(true);
        return (Optional<WinningHandDisplay.Layout>)method.invoke(table,seat);
    }
    private static TheMahjongDriver fixture(int count,boolean enabled,boolean jokers) {
        var preset=count==3?RuleSetPreset.MAHJONG_SOUL_SANMA_3P:RuleSetPreset.MAHJONG_SOUL_4P;
        var base=preset.newMatch(MatchLength.SOUTH,false,jokers,enabled);
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<count;seat++) {
            var hand=new ArrayList<>(waiting(count==3?1:0));
            if(jokers)hand.set(0,Jokers.TILE);
            if(seat==0)hand.add(tile(4,1));
            players.add(player(hand));
        }
        var indicators=Collections.nCopies(5,tile(4,3));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,2,0,0,TheMahjongRound.State.TURN,
                0,-1,TheMahjongRound.ActiveTile.drawn(tile(4,1)),Collections.nCopies(20,tile(4,1)),
                Collections.nCopies(4,tile(4,3)),indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(count,base.startingPoints(),base.targetPoints(),2,
                TheMahjongMatch.State.IN_ROUND,base.tileSet(),base.ruleSet(),List.of(),round);
        return new TheMahjongDriver(match,humans(count),new Random(7),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
    }
    private static PlayerAction action(TheMahjongDriver driver,int seat,Class<? extends PlayerAction> kind) {
        return driver.legalActions(seat).stream().filter(kind::isInstance).findFirst().orElseThrow();
    }
    private static CompoundTag save(TheMahjongDriver driver) {
        var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);DriverNbt.writeDriver(out,driver);return out.buildResult();
    }
    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        return DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),save(driver)),humans(driver.match().playerCount()),new Random(7));
    }
    private static void winnersAndEffects() throws Exception {
        for(int count:new int[]{3,4}) {
            var driver=fixture(count,true,false);
            var before=driver.currentPhase();
            act(driver,0,action(driver,0,PlayerAction.DeclareTsumo.class));
            check(driver.bloodBattleState()!=null&&driver.bloodBattleState().wins().size()==1,
                    "accepted riichi tsumo did not create a blood-battle winner");
            var table=table(driver);
            stillPrivate(table,"first win");
            var layout=winningLayout(table,0).orElseThrow();
            check(layout.order().getLast().equals(tile(4,1))&&layout.order().size()==driver.currentRound().players().getFirst().currentHand().size()
                            &&layout.gapStarts().equals(List.of(layout.order().size()-1)),
                    "continuing winner lost the exact rightmost tile or duplicated its self draw");
            check(winningLayout(table,1).isEmpty(),"active losing seat inherited a winner layout");
            var effect=WinEffectTriggers.capture(driver,before,0,WinEffects.DEFAULT);
            check(effect!=null&&effect.winners().size()==1&&effect.includes(0)
                            &&effect.source().slot().equals("hand/0/13")&&effect.source().tileCode()==PublicTable.code(tile(4,1)),
                    "first riichi blood-battle effect has the wrong source/winner/tile");
            record(table,effect);stillPrivate(table,"queued first win");
            check(WinEffectTriggers.capture(driver,driver.currentPhase(),1,WinEffects.DEFAULT)==null,
                    "old winner replayed without a new accepted win");
            var restored=reload(driver);
            check(restored.bloodBattleState().wins().equals(driver.bloodBattleState().wins())
                            &&PublicTable.revealedSeats(table(restored)).isEmpty(),
                    "saved riichi winner source/result changed or became public mid-hand");
            driver=restored;
            table=table(driver);
            // Match the block entity's legacy-save recovery before accepting any later win.
            for(var saved:WinEffectTriggers.history(driver,WinEffects.DEFAULT))record(table,saved);
            stillPrivate(table,"restored first win");
            before=driver.currentPhase();
            act(driver,1,PlayerAction.Draw.INSTANCE);
            act(driver,1,action(driver,1,PlayerAction.DeclareTsumo.class));
            var second=WinEffectTriggers.capture(driver,before,1,WinEffects.DEFAULT);
            check(second!=null&&second.winners().size()==1&&second.includes(1)&&!second.includes(0),
                    "later win replayed an earlier winner or omitted the new one");
            record(table,second);
            check(WinEffectTriggers.capture(driver,driver.currentPhase(),2,WinEffects.DEFAULT)==null,
                    "newest riichi winner replayed in the next display frame");
            if(count==4){
                stillPrivate(table,"second win");
                before=driver.currentPhase();act(driver,2,PlayerAction.Draw.INSTANCE);
                act(driver,2,action(driver,2,PlayerAction.DeclareTsumo.class));
                var third=WinEffectTriggers.capture(driver,before,2,WinEffects.DEFAULT);
                check(third!=null&&third.winners().size()==1&&third.includes(2),"third winner lost its accepted effect");
                record(table,third);
            }
            check(driver.currentPhase() instanceof MatchPhase.RoundEnded,"blood-battle fixture did not finish at one remaining player");
            invoke(table,"prepareBloodBattlePlayback");
            var completed=new HashSet<Integer>();
            for(int seat=0;seat<count-1;seat++){
                var session=table.winEffectSession();
                check(session!=null&&session.context().winners().size()==1&&session.context().includes(seat),
                        "terminal playback did not follow accepted win order at seat "+seat);
                var publicSeats=new HashSet<>(completed);publicSeats.add(seat);
                check(PublicTable.revealedSeats(table).equals(publicSeats),"pending blood-battle winner fell flat before its own playback");
                var labels=WinResultLabels.visibleResults(table.winResultLabels(),table.completedWinPresentationSeats(),board(table));
                check(labels.stream().map(WinResultLabels.Result::seat).toList().equals(completed.stream().sorted().toList()),
                        "an active or pending animation exposed its floating result early");
                invoke(table,"finishWinEffect");completed.add(seat);
                check(table.completedWinPresentationSeats().equals(completed)&&PublicTable.revealedSeats(table).equals(completed),
                        "finished winner did not remain public while pending hands stayed standing");
                labels=WinResultLabels.visibleResults(table.winResultLabels(),table.completedWinPresentationSeats(),board(table));
                check(labels.stream().map(WinResultLabels.Result::seat).toList().equals(completed.stream().sorted().toList()),
                        "finished winner lost its floating han/points/yaku result");
                invoke(table,"playNextDeferredWin");
            }
            invoke(table,"prepareBloodBattlePlayback");
            check(table.winEffectSession()==null&&table.completedWinPresentationSeats().equals(completed),
                    "completed terminal sequence replayed an accepted win");
        }
    }
    private static WinResult result(int count,int winner,int loser) {
        var deltas=new ArrayList<>(Collections.nCopies(count,0));deltas.set(winner,2000);deltas.set(loser,-2000);
        return new WinResult(List.of(),List.of(),1,30,0,List.copyOf(deltas));
    }
    private static void explicitJokerWinningTile() throws Exception {
        var base=fixture(4,true,true);var round=base.currentRound();
        var players=new ArrayList<>(round.players());var old=players.get(1);var hand=new ArrayList<>(old.currentHand());
        // An imported already-won hand may record its fixed joker as the accepted winning identity.
        hand.add(tile(4,1));
        players.set(1,new TheMahjongPlayer(old.points(),old.riichiState(),old.ippatsuEligible(),hand,
                old.melds(),old.discards(),old.temporaryFuritenTiles(),old.riichiPermanentFuriten(),old.kitaCount()));
        var updated=new TheMahjongRound(round.roundWind(),round.handNumber(),round.honba(),round.riichiSticks(),
                round.dealerSeat(),round.state(),round.currentTurnSeat(),-1,round.activeTile(),round.liveWall(),round.rinshanTiles(),
                round.doraIndicators(),round.uraDoraIndicators(),round.revealedDoraCount(),round.pendingKanDoraReveals(),players,List.of());
        var match=new TheMahjongMatch(4,base.match().startingPoints(),base.match().targetPoints(),2,
                TheMahjongMatch.State.IN_ROUND,base.match().tileSet(),base.rules(),List.of(),updated);
        var driver=new TheMahjongDriver(match,humans(4),new Random(7),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDraw(2,false),false,OptionalInt.empty()));
        driver.restoreBloodBattleState(new BloodBattleState.Snapshot(List.of(
                new BloodBattleState.Win(1,Jokers.TILE,true,result(4,1,0),1))));
        stillPrivate(table(driver),"imported joker win");
        var layout=winningLayout(table(driver),1).orElseThrow();
        check(layout.order().getLast().equals(Jokers.TILE)&&PublicTable.code(layout.order().getLast())==37,
                "continuing riichi joker winner used an ordinary red-dragon model or lost rightmost identity");
        check(layout.order().size()==hand.size()&&layout.order().stream().filter(Jokers::isJoker).count()==1,
                "winner display duplicated its physical joker");
        var restored=reload(driver);
        check(restored.bloodBattleState().wins().equals(driver.bloodBattleState().wins())
                        &&winningLayout(table(restored),1).orElseThrow().equals(layout)
                        &&PublicTable.revealedSeats(table(restored)).isEmpty(),
                "saved joker winning layout/source/result changed");
    }
    private static void claimsAndProgression() {
        var driver=fixture(4,true,false);act(driver,0,new PlayerAction.Discard(tile(4,1)));
        check(driver.currentPhase() instanceof MatchPhase.AwaitingClaims,"blood-battle claim fixture lacks legal ron");
        act(driver,1,action(driver,1,PlayerAction.DeclareRon.class));
        check(driver.currentPhase() instanceof MatchPhase.AwaitingClaims&&!driver.bloodBattleClaimDecisions().isEmpty(),
                "first claim resolved before other humans responded");
        var restored=reload(driver);
        check(restored.bloodBattleClaimDecisions().equals(driver.bloodBattleClaimDecisions())&&restored.legalActions(1).isEmpty(),
                "accepted riichi blood-battle claim was reopened or lost after save");
        for(int dealerWinner:new int[]{0,1}) {
            var base=fixture(4,true,false);var r=base.currentRound();
            var win=result(4,dealerWinner,2);
            var ended=new TheMahjongRound(r.roundWind(),r.handNumber(),r.honba(),r.riichiSticks(),r.dealerSeat(),
                    TheMahjongRound.State.ENDED,r.currentTurnSeat(),-1,TheMahjongRound.ActiveTile.none(),r.liveWall(),r.rinshanTiles(),
                    r.doraIndicators(),r.uraDoraIndicators(),r.revealedDoraCount(),r.pendingKanDoraReveals(),r.players(),List.of());
            var match=new TheMahjongMatch(4,base.match().startingPoints(),base.match().targetPoints(),2,
                    TheMahjongMatch.State.IN_ROUND,base.match().tileSet(),base.rules(),List.of(),ended);
            var resultDriver=new TheMahjongDriver(match,humans(4),new Random(7),
                    new TheMahjongDriver.Snapshot(new MatchPhase.RoundEnded(List.of(win)),false,OptionalInt.empty()));
            resultDriver.restoreBloodBattleState(new BloodBattleState.Snapshot(List.of(
                    new BloodBattleState.Win(dealerWinner,tile(4,1),true,win,dealerWinner))));
            var next=RoundProgression.next(resultDriver);
            check(next.renchan()==(dealerWinner==0)&&next.nextHonba()==(dealerWinner==0?r.honba()+1:0),
                    "riichi blood battle changed dealer-win continuation/non-dealer rotation");
        }
    }
    private static void disabledUnchanged() throws Exception {
        var driver=fixture(4,false,false);
        check(driver.bloodBattleState()==null&&PublicTable.revealedSeats(table(driver)).isEmpty(),
                "default disabled mode acquired blood-battle winners or public hands");
        act(driver,0,action(driver,0,PlayerAction.DeclareTsumo.class));
        check(driver.currentPhase() instanceof MatchPhase.RoundEnded&&driver.bloodBattleState()==null,
                "disabled riichi no longer ends on its first win");
    }
    public static void main(String[] args) throws Exception {
        bootstrap();winnersAndEffects();explicitJokerWinningTile();claimsAndProgression();disabledUnchanged();
        System.out.println("PASS: blood-battle winners remain private mid-hand, then animate and expose floating results in win order; exact winning/joker identities, saved claims, progression and ordinary disabled games are preserved");
    }
}
