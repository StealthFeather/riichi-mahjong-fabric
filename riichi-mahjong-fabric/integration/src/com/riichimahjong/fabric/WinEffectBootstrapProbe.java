package com.riichimahjong.fabric;

import com.google.gson.GsonBuilder;
import com.riichimahjong.fabric.wineffect.DeferredWinQueue;
import com.riichimahjong.fabric.wineffect.WinEffectContext;
import com.riichimahjong.fabric.wineffect.WinEffectSession;
import com.riichimahjong.fabric.wineffect.WinEffects;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.registry.ModBlocks;
import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.sichuan.SichuanState;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.TagValueInput;

/** Real Fabric/registry bootstrap and block-entity NBT; exits before creating a server/world. */
public final class WinEffectBootstrapProbe implements ModInitializer {
    private static final List<String> checks=new ArrayList<>();
    private static final Map<String,Object> result=new LinkedHashMap<>();
    private static final HolderLookup.Provider REGISTRIES=HolderLookup.Provider.create(Stream.empty());

    @Override public void onInitialize() {
        if(!Files.exists(Path.of("riichi-win-effect-probe-enabled")))
            throw new IllegalStateException("Win effect probe requires its disposable-runtime marker");
        int exit=0;
        try {
            settingsAndFallback();
            replayAndSpectatorPrivacy();
            interruptedSaveAndLifecycle();
            finalMatchSave();
            scaledFangsDisplaysAndPackets();
            bloodBattleDeferredSaveAndPlayback();
            result.put("passed",true);
        } catch(Throwable error) {
            error.printStackTrace();result.put("passed",false);result.put("failure",error.toString());exit=1;
        }
        result.put("checks",checks);
        result.put("serverWorldStarted",false);
        result.put("clientVisualTest",false);
        result.putIfAbsent("entityElementLifecycleTest",false);
        try {
            Files.writeString(Path.of("win-effect-probe-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        } catch(Exception failure) { failure.printStackTrace();exit=2; }
        System.out.println("RIICHI_WIN_EFFECT_PROBE "+new GsonBuilder().create().toJson(result));
        System.exit(exit);
    }
    private static MahjongTableBlockEntity table() {
        return new MahjongTableBlockEntity(new BlockPos(7,80,-11),ModBlocks.MAHJONG_TABLE.get().defaultBlockState());
    }
    private static void replayAndSpectatorPrivacy() throws Exception {
        UUID player=UUID.randomUUID(), replacement=UUID.randomUUID(), viewer=UUID.randomUUID();
        var table=table();
        var driver=wonDriver();
        set(table,"driver",driver);
        require(table.claimSeat(0,player),"could not claim fixture seat");
        table.releaseSeat(0);
        var reservations=(com.riichimahjong.mahjongtable.ReturningSeats)get(table,"returningSeats");
        require(reservations.forget(player,0),"could not revoke fixture seat reservation");
        require(table.claimSeat(0,replacement),"could not replace fixture player");
        table.releaseSeat(0);
        require(!table.spectatorCanSeeHands(player) && !table.spectatorCanSeeHands(replacement)
                && table.spectatorCanSeeHands(viewer),"former participant gained access to concealed hands");
        var saved=table.saveCustomOnly(REGISTRIES);
        var restored=reload(saved);
        require(!restored.spectatorCanSeeHands(player) && !restored.spectatorCanSeeHands(replacement)
                && restored.spectatorCanSeeHands(viewer),"participant history was not preserved by block-entity NBT");
        var people=java.util.stream.IntStream.range(0,4).mapToObj(i->
                new com.riichimahjong.replay.ReplayRecord.Participant(i,i==0?player:null,
                        i==0?"Player":"BOT",0)).toList();
        var recorder=new com.riichimahjong.replay.ReplayRecorder("MAHJONG_SOUL_4P",people);
        recorder.capture(driver);
        var terminal=new TheMahjongDriver(driver.match(),humans(),new Random(13),
                new TheMahjongDriver.Snapshot(new MatchPhase.MatchEnded(),false,OptionalInt.empty()));
        var replay=recorder.finish(terminal);
        var decoded=com.riichimahjong.replay.ReplayRecord.fromNbt(replay.toNbt());
        require(decoded.frames().size()>=2 && decoded.participants().stream().anyMatch(participant->player.equals(participant.uuid())),
                "completed replay lost its frames or participants after NBT decoding");
        var resumed=decoded.frames().getFirst().restore(REGISTRIES);
        require(resumed.currentRound().players().size()==4,"replay frame did not restore a four-player hand");
        set(table,"replay",new com.riichimahjong.replay.ReplaySession(decoded,player,false));
        require(PublicTable.revealedSeats(table).contains(0),"replay perspective did not show selected hand");
        table.replay().control(com.riichimahjong.replay.ReplaySession.Control.REVEAL);
        require(PublicTable.revealedSeats(table).size()==4,"replay reveal control did not show all hands");
        invoke(table,"clearMatchState");invoke(restored,"clearMatchState");
        checks.add("real-table-participant-privacy-after-leave-replacement-and-block-entity-nbt");
        checks.add("replay-record-nbt-frame-restoration-perspective-and-reveal-controls");
    }

    private static void settingsAndFallback() throws Exception {
        var table=table();
        require(table.winEffectId().equals(WinEffects.DEFAULT),"new table has no default win effect");
        table.selectWinEffect("riichi_mahjong:none");
        var selected=reload(table.saveCustomOnly(REGISTRIES));
        require(selected.winEffectId().equals("riichi_mahjong:none"),"table selection did not survive real NBT");
        var unknown=selected.saveCustomOnly(REGISTRIES);unknown.putString("WinEffect","removed_pack:old_effect");
        require(reload(unknown).winEffectId().equals(WinEffects.DEFAULT),"unknown saved effect does not fall back");
        for (var preset : com.riichimahjong.mahjongtable.RuleSetPreset.values()) {
            var options = table();
            options.selectPreset(preset);
            require(!options.bloodBattle(), "new table defaults to blood battle");
            options.setBloodBattle(true);
            options.setExchangeThreeTiles(true);
            options.setJokersEnabled(true);
            require(!options.spectatorHandsVisible(), "new table exposes concealed spectator hands by default");
            options.setSpectatorHandsVisible(true);
            require(options.exchangeThreeTiles() && options.jokersEnabled(), "shared options locked to Sichuan");
            var loaded = reload(options.saveCustomOnly(REGISTRIES));
            require(loaded.exchangeThreeTiles() && loaded.jokersEnabled() && loaded.preset() == preset && loaded.spectatorHandsVisible() && loaded.bloodBattle(),
                    "real table NBT lost shared rules or selected preset");
        }
        checks.add("all-preset-table-options-and-real-block-entity-nbt");
        checks.add("real-block-entity-effect-selection-and-unknown-id-nbt-fallback");
    }
    private static void interruptedSaveAndLifecycle() throws Exception {
        var driver=wonDriver();
        var context=WinEffectTriggers.capture(driver,new MatchPhase.AwaitingDiscard(0),0,WinEffects.DEFAULT);
        require(context!=null&&context.source().slot().equals("river/0/0"),"probe did not capture accepted double ron");
        var table=prepared(driver,context);
        require(table.tableButtonsForSeat(0).isEmpty(),"buttons exposed during active presentation");
        table.selectWinEffect("riichi_mahjong:none");
        require(table.winEffectId().equals(WinEffects.DEFAULT),"active match allowed effect selection changes");
        // Saving before the bite commits the presentation's terminal river state for a clean reload.
        var saved=table.saveCustomOnly(REGISTRIES);
        require(table.winEffectSession()!=null&&table.consumedWinTileSlots().isEmpty(),"save mutated the live playback");
        var restored=reload(saved);
        require(restored.winEffectSession()==null,"read resumed a transient effect or replayed its cues");
        require(restored.consumedWinTileSlots().equals(Set.of("river/0/0")),"interrupted save lost the consumed source");
        var display=(WinningHandDisplay)get(restored,"winningHandDisplay");
        require(display!=null&&display.tile().redDora()&&!display.selfDraw(),"interrupted save lost winning red-five identity");
        require(get(restored,"finalWinningSeats").equals(Set.of(1,2)),"saved final winners differ from double ron");
        require(display.layout(restored.driver().currentRound(),restored.driver().currentPhase(),1).orElseThrow()
                .order().getLast().equals(display.tile()),"restored winning tile is not in the final slot");
        require(WinEffectTriggers.capture(restored.driver(),restored.driver().currentPhase(),0,WinEffects.DEFAULT)==null,
                "restored RoundEnded emits the same effect again");
        require(restored.driver().currentRound().players().stream().map(TheMahjongPlayer::points).toList()
                .equals(driver.currentRound().players().stream().map(TheMahjongPlayer::points).toList()),
                "presentation save/reload changed accepted settlement points");
        invoke(table,"finishWinEffect");invoke(table,"finishWinEffect");
        require(table.winEffectSession()==null&&table.consumedWinTileSlots().equals(Set.of("river/0/0")),
                "finish is not idempotent or lost source consumption");
        invoke(table,"clearWinPresentation");
        require(table.winEffectSession()==null&&table.consumedWinTileSlots().isEmpty()
                &&get(table,"finalWinningSeats").equals(Set.of()),"cancel retained an old effect/winner/river mask");

        var plainContext=new WinEffectContext("riichi_mahjong:none",context.source(),context.winners());
        set(table,"winEffectSession",new WinEffectSession(plainContext));
        require(reload(table.saveCustomOnly(REGISTRIES)).consumedWinTileSlots().isEmpty(),
                "saving a direct reveal consumed the river source");
        invoke(table,"finishWinEffect");
        require(table.consumedWinTileSlots().isEmpty(),"finishing a direct reveal consumed the river source");
        set(table,"winEffectSession",new WinEffectSession(context));
        table.setRemoved();
        require(table.isRemoved()&&table.winEffectSession()==null
                &&table.consumedWinTileSlots().contains("river/0/0"),"unload did not finish and release presentation");
        invoke(table,"clearMatchState");
        require(table.driver()==null&&table.winEffectSession()==null&&table.consumedWinTileSlots().isEmpty()
                &&get(table,"winningHandDisplay")==null&&get(table,"finalWinningSeats").equals(Set.of()),
                "reset left presentation state attached to the next match");
        invoke(restored,"clearMatchState");
        checks.add("interrupted-effect-nbt-keeps-terminal-river-winning-tile-and-double-ron-winners");
        checks.add("presentation-does-not-replay-or-change-rules-and-points-on-load");
        checks.add("finish-cancel-reset-unload-and-direct-reveal-cleanup");
    }
    private static void finalMatchSave() throws Exception {
        var accepted=wonDriver();
        var context=WinEffectTriggers.capture(accepted,new MatchPhase.AwaitingDiscard(0),0,WinEffects.DEFAULT);
        var table=prepared(accepted,context);
        invoke(table,"finishWinEffect");
        // The last round remains available in MatchEnded; its presentation must remain public too.
        var finalDriver=new TheMahjongDriver(accepted.match(),humans(),new Random(3),
                new TheMahjongDriver.Snapshot(new MatchPhase.MatchEnded(),false,OptionalInt.empty()));
        set(table,"driver",finalDriver);
        // advanceRoundAfterResult explicitly carries the previously visible winners into terminal choices.
        var reveals=(ResultHandReveals)get(table,"resultHandReveals");
        require(reveals.show(finalDriver,finalDriver.currentRound(),1)
                &&reveals.show(finalDriver,finalDriver.currentRound(),2),"terminal winner reveal fixture failed");
        require(reveals.show(finalDriver,finalDriver.currentRound(),3),"terminal voluntary loser reveal fixture failed");
        var restored=reload(table.saveCustomOnly(REGISTRIES));
        require(restored.driver().currentPhase() instanceof MatchPhase.MatchEnded,"terminal phase did not persist");
        require(PublicTable.revealedSeats(restored).containsAll(Set.of(1,2)),"MatchEnded hid automatic winning hands");
        var display=(WinningHandDisplay)get(restored,"winningHandDisplay");
        require(display!=null&&get(restored,"finalWinningSeats").equals(Set.of(1,2)),"terminal save lost winning display metadata");
        var layout=display.layout(restored.driver().currentRound(),Set.of(1,2),1).orElseThrow();
        require(layout.order().getLast().equals(display.tile())&&layout.gapStarts().equals(List.of(layout.order().size()-1)),
                "terminal winning tile lost its final slot or gap");
        require(PublicTable.revealedSeats(restored).contains(3),"voluntary terminal loser reveal was lost");
        var winningLayout=MahjongTableBlockEntity.class.getDeclaredMethod("winningHandLayout",int.class);
        winningLayout.setAccessible(true);
        require(((Optional<?>)winningLayout.invoke(restored,3)).isEmpty(),
                "voluntarily revealed loser acquired an extra winning tile");
        require(restored.consumedWinTileSlots().equals(Set.of("river/0/0")),"MatchEnded restored the swallowed river tile");
        invoke(table,"clearMatchState");invoke(restored,"clearMatchState");
        checks.add("terminal-match-nbt-retains-winner-visibility-red-winning-tile-gap-and-consumed-river");
    }
    private static MahjongTableBlockEntity prepared(TheMahjongDriver driver,WinEffectContext context) throws Exception {
        var table=table();
        set(table,"driver",driver);
        set(table,"winningHandDisplay",WinningHandDisplay.capture(driver.winningTile(),driver.currentPhase()));
        set(table,"finalWinningSeats",Set.of(1,2));
        set(table,"winEffectSession",new WinEffectSession(context));
        return table;
    }
    private static TheMahjongDriver bloodDriver(RuleSetPreset preset) {
        int count=preset.playerCount();boolean sichuan=preset.family()==RuleSetPreset.Family.SICHUAN;
        var base=preset.newMatch(MatchLength.SOUTH,false,false,true);
        var winning=new TheMahjongTile(sichuan?TheMahjongTile.Suit.MANZU:TheMahjongTile.Suit.WIND,sichuan?5:1,false);
        var waiting=new ArrayList<TheMahjongTile>();
        if(sichuan) {
            for(char rank:"1112223334445".toCharArray())
                waiting.add(new TheMahjongTile(TheMahjongTile.Suit.MANZU,rank-'0',false));
        }else{
            for(int rank=1;rank<=9;rank++)waiting.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU,rank,false));
            waiting.addAll(Collections.nCopies(3,new TheMahjongTile(TheMahjongTile.Suit.SOUZU,1,false)));
            waiting.add(winning);
        }
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<count;seat++){
            var own=new ArrayList<>(waiting);if(seat==0)own.add(winning);
            players.add(new TheMahjongPlayer(50000,TheMahjongPlayer.RiichiState.NONE,false,own,List.of(),List.of(),List.of()));
        }
        var indicators=sichuan?List.<TheMahjongTile>of():Collections.nCopies(5,new TheMahjongTile(TheMahjongTile.Suit.DRAGON,3,false));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,2,0,0,TheMahjongRound.State.TURN,0,-1,
                TheMahjongRound.ActiveTile.drawn(winning),Collections.nCopies(20,winning),
                sichuan?List.of():Collections.nCopies(4,winning),indicators,indicators,sichuan?0:1,players,List.of());
        var match=new TheMahjongMatch(count,base.startingPoints(),base.targetPoints(),2,TheMahjongMatch.State.IN_ROUND,
                base.tileSet(),base.ruleSet(),List.of(),round);
        List<MahjongPlayerInterface> humans=Stream.generate(()->(MahjongPlayerInterface)new MahjongHumanPlayer()).limit(count).toList();
        var driver=new TheMahjongDriver(match,humans,new Random(7),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
        if(sichuan)driver.restoreSichuanState(new SichuanState.Snapshot(SichuanState.Stage.PLAY,1,
                List.of(List.of(),List.of(),List.of(),List.of()),Set.of(),List.of(1,1,1,1),List.of(),List.of(),false,-1));
        return driver;
    }
    private static List<Integer> points(TheMahjongDriver driver) {
        return driver.currentRound().players().stream().map(TheMahjongPlayer::points).toList();
    }
    private static int acceptedWins(TheMahjongDriver driver) {
        return driver.sichuanState()!=null?driver.sichuanState().wins().size():driver.bloodBattleState().wins().size();
    }
    private static DeferredWinQueue queue(MahjongTableBlockEntity table) throws Exception {
        return (DeferredWinQueue)get(table,"deferredWinQueue");
    }
    private static void record(MahjongTableBlockEntity table,WinEffectContext context) throws Exception {
        var method=MahjongTableBlockEntity.class.getDeclaredMethod("recordWinPresentation",WinEffectContext.class);
        method.setAccessible(true);method.invoke(table,context);
    }
    private static void act(TheMahjongDriver driver,int seat,PlayerAction action) {
        driver.submitAction(seat,action);driver.advance(.05);
    }
    private static void inertControllers(MahjongTableBlockEntity table) {
        // Block-entity reload binds the real table-human controllers; keep this bootstrap probe
        // independent of their live-server auto-draw timers while accepting explicit real actions.
        var driver=table.driver();driver.setAnimationsEnabled(false);
        for(int seat=0;seat<driver.match().playerCount();seat++)driver.replacePlayer(seat,new MahjongHumanPlayer());
    }
    @SuppressWarnings("unchecked")
    private static Optional<WinningHandDisplay.Layout> winningLayout(MahjongTableBlockEntity table,int seat) throws Exception {
        var method=MahjongTableBlockEntity.class.getDeclaredMethod("winningHandLayout",int.class);
        method.setAccessible(true);return (Optional<WinningHandDisplay.Layout>)method.invoke(table,seat);
    }
    /** Bootstrap has no Level; build only public rows from the same BE allowlist as PublicTable.capture. */
    private static PublicTable bloodBoard(MahjongTableBlockEntity table) throws Exception {
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
        return new PublicTable(List.copyOf(seats),20,0,List.of(),List.of(),Set.of(),"","东一局",-1,0,null,1,136,
                table.driver().sichuanState()!=null,52);
    }
    private static void resultEntities(MahjongTableBlockEntity table,Set<Integer> expected) throws Exception {
        var holder=new ElementHolder();var labels=new WinResultLabels(holder,table.getBlockPos(),table.getBlockState());
        try{
            labels.update(table,bloodBoard(table));
            var field=WinResultLabels.class.getDeclaredField("labels");field.setAccessible(true);
            require(((Map<?,?>)field.get(labels)).keySet().equals(expected)
                    &&holder.getElements().size()==expected.size(),"real floating result entities differ from completed winners");
            labels.update(table,bloodBoard(table));
            require(holder.getElements().size()==expected.size(),"updating floating results duplicated entities");
        }finally{
            labels.destroy();require(holder.getElements().isEmpty(),"destroying floating results leaked entities");holder.destroy();
        }
    }
    private static void hiddenBloodBattle(MahjongTableBlockEntity table,int count) throws Exception {
        invoke(table,"prepareBloodBattlePlayback");
        require(table.winEffectSession()==null&&table.completedWinPresentationSeats().isEmpty()
                &&PublicTable.revealedSeats(table).isEmpty(),"unfinished blood battle animated or revealed a winning hand");
        require(queue(table).contexts().size()==count,"unfinished blood battle lost an accepted presentation");
        resultEntities(table,Set.of());
    }
    private static void terminalBloodReload(MahjongTableBlockEntity live,Set<Integer> winners,List<Integer> settled) throws Exception {
        var contexts=queue(live).contexts();var completed=live.completedWinPresentationSeats();var active=live.winEffectSession();
        var saved=live.saveCustomOnly(REGISTRIES);
        require(queue(live).contexts().equals(contexts)&&live.completedWinPresentationSeats().equals(completed)
                &&live.winEffectSession()==active,"terminal save advanced or completed the live animation");
        var restored=reload(saved);
        try{
            require(restored.winEffectSession()==null&&restored.completedWinPresentationSeats().equals(winners)
                    &&PublicTable.revealedSeats(restored).equals(winners),"interrupted terminal load did not preserve the fully revealed result");
            require(queue(restored).contexts().equals(contexts)&&queue(restored).completed().equals(winners),
                    "interrupted terminal load lost winner order or left queued playback pending");
            require(restored.winResultLabels().equals(live.winResultLabels()),"terminal load changed accepted floating scores/yaku");
            require(points(restored.driver()).equals(settled),"terminal animation load paid the accepted win again");
            invoke(restored,"prepareBloodBattlePlayback");invoke(restored,"playNextDeferredWin");
            require(restored.winEffectSession()==null&&points(restored.driver()).equals(settled),
                    "terminal reload replayed an effect or duplicated a payment");
            resultEntities(restored,winners);
            var twice=reload(restored.saveCustomOnly(REGISTRIES));
            try{
                invoke(twice,"prepareBloodBattlePlayback");
                require(twice.winEffectSession()==null&&twice.completedWinPresentationSeats().equals(winners)
                        &&points(twice.driver()).equals(settled),"second terminal reload replayed or settled again");
                resultEntities(twice,winners);
            }finally{invoke(twice,"clearMatchState");}
        }finally{invoke(restored,"clearMatchState");}
    }
    private static void bloodBattleDeferredSaveAndPlayback() throws Exception {
        int activeReloads=0,interruptedReloads=0;
        for(var preset:List.of(RuleSetPreset.MAHJONG_SOUL_SANMA_3P,RuleSetPreset.MAHJONG_SOUL_4P,RuleSetPreset.SICHUAN_4P)){
            var table=table();table.selectPreset(preset);
            int count=preset.playerCount();var seats=new ArrayList<MahjongTableBlockEntity.SeatInfo>();
            for(int seat=0;seat<4;seat++)seats.add(seat<count
                    ?MahjongTableBlockEntity.SeatInfo.claimed(new UUID(77,seat+1)):MahjongTableBlockEntity.SeatInfo.closed());
            set(table,"seats",seats);set(table,"driver",bloodDriver(preset));
            try{
                for(int winner=0;winner<count-1;winner++){
                    var driver=table.driver();if(winner>0)act(driver,winner,PlayerAction.Draw.INSTANCE);
                    var before=driver.currentPhase();var old=points(driver);int wins=acceptedWins(driver);
                    var action=driver.legalActions(winner).stream().filter(PlayerAction.DeclareTsumo.class::isInstance)
                            .map(PlayerAction.DeclareTsumo.class::cast).findFirst().orElseThrow();
                    act(driver,winner,action);var after=points(driver);
                    require(acceptedWins(driver)==wins+1,"Fabric blood-battle fixture did not accept its next winner");
                    for(int seat=0;seat<count;seat++)require(after.get(seat)==old.get(seat)+action.result().pointDeltas().get(seat),
                            "accepted blood-battle balance differs from the real action result");
                    require(after.get(winner)>old.get(winner),"later Fabric blood-battle tsumo collected no point sticks");
                    var context=WinEffectTriggers.capture(driver,before,wins,WinEffects.DEFAULT);
                    require(context!=null&&context.includes(winner),"accepted Fabric blood-battle win lost its effect context");
                    record(table,context);
                    if(winner<count-2){
                        hiddenBloodBattle(table,wins+1);var contexts=queue(table).contexts();
                        var restored=reload(table.saveCustomOnly(REGISTRIES));
                        require(queue(restored).contexts().equals(contexts)&&points(restored.driver()).equals(after),
                                "mid-hand block-entity NBT changed the ordered queue or settled balances");
                        require(WinEffectTriggers.history(restored.driver(),WinEffects.DEFAULT).size()==wins+1,
                                "mid-hand block-entity NBT lost accepted winning tiles/results");
                        hiddenBloodBattle(restored,wins+1);inertControllers(restored);
                        invoke(table,"clearMatchState");table=restored;activeReloads++;
                    }
                }
                require(table.driver().currentPhase() instanceof MatchPhase.RoundEnded,"Fabric blood-battle fixture never reached terminal");
                invoke(table,"prepareBloodBattlePlayback");var settled=points(table.driver());var completed=new HashSet<Integer>();
                var winners=new HashSet<Integer>();for(int winner=0;winner<count-1;winner++)winners.add(winner);
                require(table.winResultLabels().equals(WinResultLabels.captureResults(table.driver())),"terminal floating results differ from accepted per-win receipts");
                for(int winner=0;winner<count-1;winner++){
                    var active=table.winEffectSession();require(active!=null&&active.context().winners().size()==1
                            &&active.context().includes(winner),"Fabric terminal animations are out of accepted win order");
                    var publicSeats=new HashSet<>(completed);publicSeats.add(winner);
                    require(PublicTable.revealedSeats(table).equals(publicSeats),"pending terminal winner fell flat before its turn");
                    resultEntities(table,completed);terminalBloodReload(table,winners,settled);interruptedReloads++;
                    invoke(table,"finishWinEffect");completed.add(winner);
                    require(table.completedWinPresentationSeats().equals(completed)
                            &&PublicTable.revealedSeats(table).equals(completed),"finishing terminal playback revealed a pending winner");
                    resultEntities(table,completed);invoke(table,"playNextDeferredWin");
                    require(points(table.driver()).equals(settled),"presenting a queued winner duplicated its accepted payment");
                }
                invoke(table,"prepareBloodBattlePlayback");
                require(table.winEffectSession()==null&&completed.equals(winners),"completed Fabric terminal sequence restarted");
            }finally{invoke(table,"clearMatchState");}
        }
        result.put("bloodBattleActiveNbtReloads",activeReloads);result.put("bloodBattleInterruptedNbtReloads",interruptedReloads);
        result.put("floatingResultEntityLifecycleTest",true);
        checks.add("riichi-three-four-and-sichuan-mid-hand-wins-stay-standing-with-no-effects-or-result-entities");
        checks.add("real-block-entity-mid-hand-nbt-preserves-ordered-deferred-wins-and-exact-paid-balances");
        checks.add("terminal-wins-play-in-accepted-order-and-only-completed-hands-have-floating-result-entities");
        checks.add("every-terminal-animation-boundary-save-load-finishes-once-without-effects-replay-or-repeat-payment");
    }
    private static void scaledFangsDisplaysAndPackets() throws Exception {
        // This bootstrap-only process exits before normal server item templates are bound.
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());
        var holder=new ElementHolder();var feet=new Vec3(7.5,80.5625,-10.5);
        var fangs=new SmallFangsDisplay(holder,feet,100);
        require(holder.getElements().size()==3,"scaled fangs did not create one base and two jaws");
        var parts=holder.getElements().stream().map(element->{
            require(element instanceof ItemDisplayElement,"fangs spawned a native entity instead of a scaled display");
            return (ItemDisplayElement)element;
        }).toList();
        require(holder.getEntityIds().size()==3,"fangs displays lack distinct entity IDs");
        for(int i=0;i<parts.size();i++) {
            var display=parts.get(i);
            require(display.getItemDisplayContext()==ItemDisplayContext.NONE,"fangs inherited an inventory display transform");
            var id=display.getItem().get(DataComponents.ITEM_MODEL);
            require(id!=null&&id.toString().equals("riichi_mahjong:bridge/win_fangs_"+(i==0?"base":"jaw")),
                    "fangs point to the wrong item model");
            String path="/assets/riichi_mahjong/";
            try(var item=SmallFangsDisplay.class.getResourceAsStream(path+"items/bridge/win_fangs_"+(i==0?"base":"jaw")+".json");
                    var model=SmallFangsDisplay.class.getResourceAsStream(path+"models/bridge/win_fangs_"+(i==0?"base":"jaw")+".json");
                    var texture=SmallFangsDisplay.class.getResourceAsStream(path+"textures/block/win_fangs.png")) {
                require(item!=null&&model!=null&&texture!=null,"fangs item/model/block-atlas texture missing from the packaged mod");
            }
        }
        require(fangs.update(118),"fangs ended before their full bite");
        var expected=SmallFangsDisplay.frame(18);
        int spawnCount=0,metadataCount=0;
        for(int i=0;i<parts.size();i++) {
            var display=parts.get(i);var target=expected.get(i);
            require(display.getOverridePos().distanceTo(feet.add(target.offset()))<1e-7
                    &&Math.abs(display.getScale().x()-target.scale())<1e-7,
                    "live Polymer metadata does not match the requested scaled geometry");
            var packets=new ArrayList<Packet<ClientGamePacketListener>>();
            // Generic display elements do not consult a player when serializing their public spawn/data.
            display.startWatching(null,packets::add);
            for(var packet:packets) {
                var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
                try {
                    if(packet instanceof ClientboundAddEntityPacket spawn) {
                        require(spawn.getType()==EntityTypes.ITEM_DISPLAY,"fangs spawn packet contains an unscaled evoker entity");
                        ClientboundAddEntityPacket.STREAM_CODEC.encode(buffer,spawn);
                        var decoded=ClientboundAddEntityPacket.STREAM_CODEC.decode(buffer);
                        require(decoded.getType()==EntityTypes.ITEM_DISPLAY&&decoded.getId()==display.getEntityId()&&!buffer.isReadable(),
                                "item display spawn packet failed to round-trip");
                        spawnCount++;
                    } else if(packet instanceof ClientboundSetEntityDataPacket metadata) {
                        ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer,metadata);
                        var decoded=ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
                        require(decoded.id()==display.getEntityId()&&!decoded.packedItems().isEmpty()&&!buffer.isReadable(),
                                "item-model/scale metadata failed to round-trip");
                        metadataCount++;
                    }
                } finally { buffer.release(); }
            }
        }
        require(spawnCount==3&&metadataCount==3,"late watchers did not receive all scaled fangs displays and metadata");
        require(!fangs.update(122)&&holder.getElements().isEmpty(),"completed fangs leaked temporary display elements");
        var interrupted=new SmallFangsDisplay(holder,feet,200);
        require(interrupted.update(204),"interrupted fangs could not start");
        interrupted.destroy();
        require(holder.getElements().isEmpty(),"table destruction leaked fangs display elements");
        result.put("fangsItemDisplayCount",spawnCount);
        result.put("fangsMaximumHeight",SmallFangsDisplay.TARGET_HEIGHT);
        result.put("entityElementLifecycleTest",true);
        checks.add("real-polymer-three-scaled-fangs-displays-with-packaged-models-and-block-atlas-texture");
        checks.add("vanilla-26.3-item-display-spawn-and-model-scale-metadata-codecs");
        checks.add("late-watcher-current-frame-and-fangs-completion-interruption-cleanup");
    }
    private static MahjongTableBlockEntity reload(CompoundTag saved) {
        var restored=table();
        restored.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING,REGISTRIES,saved));
        return restored;
    }
    private static List<MahjongPlayerInterface> humans() {
        return Stream.generate(()->(MahjongPlayerInterface)new MahjongHumanPlayer()).limit(4).toList();
    }
    private static TheMahjongDriver wonDriver() {
        var waiting=new ArrayList<TheMahjongTile>();
        for(int rank=1;rank<=9;rank++)waiting.add(new TheMahjongTile(TheMahjongTile.Suit.MANZU,rank,false));
        for(int i=0;i<3;i++)waiting.add(new TheMahjongTile(TheMahjongTile.Suit.WIND,1,false));
        waiting.add(new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,false));
        var red=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);
        var players=new ArrayList<TheMahjongPlayer>();
        for(int seat=0;seat<4;seat++) {
            var own=new ArrayList<>(waiting);if(seat==0)own.add(red);
            players.add(new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,own,List.of(),List.of(),List.of()));
        }
        var indicator=new TheMahjongTile(TheMahjongTile.Suit.DRAGON,3,false);
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,TheMahjongRound.State.TURN,0,-1,
                TheMahjongRound.ActiveTile.drawn(red),Collections.nCopies(20,indicator),Collections.nCopies(4,indicator),
                Collections.nCopies(5,indicator),Collections.nCopies(5,indicator),1,players,List.of());
        var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,
                TheMahjongTileSet.standardRiichi(true),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
        var driver=new TheMahjongDriver(match,humans(),new Random(3),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0),false,OptionalInt.empty()));
        driver.submitAction(0,new PlayerAction.Discard(red));driver.advance(.05);
        for(int seat=1;seat<4;seat++)driver.submitAction(seat,seat<3
                ?driver.legalActions(seat).stream().filter(a->a instanceof PlayerAction.DeclareRon).findFirst().orElseThrow()
                :PlayerAction.Pass.INSTANCE);
        driver.advance(.05);
        require(driver.currentPhase() instanceof MatchPhase.RoundEnded,"accepted-win probe fixture did not finish");
        return driver;
    }
    private static Object get(Object owner,String name) throws Exception {
        Field field=MahjongTableBlockEntity.class.getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        Field field=MahjongTableBlockEntity.class.getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    private static void invoke(Object owner,String name) throws Exception {
        Method method=MahjongTableBlockEntity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(owner);
    }
    private static void require(boolean condition,String message) {
        if(!condition)throw new IllegalStateException(message);
    }
}
