package com.riichimahjong.fabric;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.registry.ModBlocks;
import com.themahjong.*;
import com.themahjong.driver.*;
import eu.pb4.polymer.virtualentity.api.elements.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Test-only addon. This class is deliberately outside src/main and the production jar. */
public final class RiichiIntegrationTest implements ModInitializer {
    private static final BlockPos POS=new BlockPos(0,-60,0);
    private static final Path RESULTS=Path.of("riichi-integration-results.json");
    private static final List<String> checks=new ArrayList<>();
    private static MinecraftServer runningServer;
    private static MahjongTableBlockEntity table;
    private static ServerPlayer owner;
    private static int stage=-1;
    private static long due,accepted;
    private static List<TheMahjongTile> heldOrder;
    private static List<Integer> heldSlots;
    private static List<Vec3> heldPositions;
    private static int gapStage=-1,gapDiscardIndex;
    private static long gapDue,gapAccepted;
    private static List<TheMahjongTile> gapBeforeOrder;
    private static List<Integer> gapBeforeSlots;
    private static List<Vec3> gapBeforePositions;
    private static final Map<String,Object> gapSnapshots=new LinkedHashMap<>();
    private static boolean emptySlotIgnored,shiftedSlotMappedCorrectly;

    @Override public void onInitialize(){
        if(!Files.exists(Path.of("riichi-integration-enabled")))throw new IllegalStateException("Test addon requires isolated-server marker riichi-integration-enabled");
        CommandRegistrationCallback.EVENT.register((dispatcher,access,env)->dispatcher.register(Commands.literal("riichi_test")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("run").executes(context->{begin(context.getSource().getServer());return 1;}))
                .then(Commands.literal("aim").then(Commands.argument("index",IntegerArgumentType.integer(0)).executes(context->{
                    aim(context.getSource().getServer(),IntegerArgumentType.getInteger(context,"index"));return 1;
                })))
                .then(Commands.literal("state").executes(context->{state(context.getSource().getServer());return 1;}))
                .then(Commands.literal("gap_discard").executes(context->{discardGap();return 1;}))
                .then(Commands.literal("gap_state").executes(context->{System.out.println("RIICHI_GAP_STATE "+new GsonBuilder().create().toJson(gapSnapshot()));return 1;}))
                .then(Commands.literal("gap_finish").executes(context->{finishGap();return 1;}))
                .then(Commands.literal("fixture").then(Commands.argument("scenario",StringArgumentType.word()).executes(context->{
                    String scenario=StringArgumentType.getString(context,"scenario");
                    if(scenario.equals("discard_gap")){prepareGap(context.getSource().getServer());return 1;}
                    stage=-1;gapStage=-1;context.getSource().getServer().tickRateManager().setFrozen(false);
                    fixture(context.getSource().getServer(),scenario);
                    context.getSource().sendSuccess(()->Component.literal("Riichi test fixture ready at 0 -60 0"),false);return 1;
                })))));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server!=runningServer)return;
            try{
                if(gapStage>=0){gapStep();return;}
                if(stage>=0&&server.overworld().getGameTime()>=due)step();
            }catch(Throwable failure){failure.printStackTrace();if(gapStage>=0){gapStage=-1;saveGap(false,failure.toString());}else{stage=-1;save(false,failure.toString());}}
        });
    }

    private static void current(MinecraftServer server){
        table=(MahjongTableBlockEntity)server.overworld().getBlockEntity(POS);
        require(table!=null&&table.driver()!=null,"create a fixture before aiming or inspecting state");
        owner=server.getPlayerList().getPlayers().stream().filter(player->table.seatOfPlayer(player.getUUID()).isPresent()).findFirst().orElseThrow(()->new IllegalStateException("no connected fixture owner"));
    }

    private static void aim(MinecraftServer server,int index){
        try{
            current(server);
            var hand=(PrivateHand)((Map<?,?>)get(view(),"hands")).get(owner.getUUID());
            require(hand!=null&&hand.valid(owner),"wait one server tick for the current private button holder");
            require(index<hand.buttonLayout.size(),"button index out of range: "+index);
            var button=hand.buttonLayout.get(index);
            var dir=PortViews.outward(table.seatOfPlayer(owner.getUUID()).orElseThrow(),table.getBlockState());
            var centre=button.centre(POS,dir);var delta=centre.subtract(owner.getEyePosition());
            float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
            float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
            owner.teleportTo(owner.level(),owner.getX(),owner.getY(),owner.getZ(),Set.of(),yaw,pitch,true);
            System.out.println("RIICHI_TEST_AIM index="+index+" text="+button.text()+" yaw="+yaw+" pitch="+pitch+" centre="+centre);
        }catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new RuntimeException(failure);}
    }

    private static void state(MinecraftServer server){
        current(server);int seat=table.seatOfPlayer(owner.getUUID()).orElseThrow();
        boolean queued=table.driver().playerAt(seat) instanceof MahjongTableHumanPlayer human&&human.hasQueued();
        var buttons=MahjongTableHumanPlayer.tableButtons(table.driver(),seat,table.riichiPendingForSeat(seat));
        String labels="";for(int index=0;index<buttons.size();index++)labels+=(index==0?"":" | ")+index+":"+PrivateHand.label(buttons.get(index));
        System.out.println("RIICHI_TEST_STATE phase="+table.driver().currentPhase()+" owner="+owner.getName().getString()+" seat="+seat+" riichiPending="+table.riichiPendingForSeat(seat)+" queued="+queued+" buttons=["+labels+"]");
    }

    private static void begin(MinecraftServer server){
        checks.clear();runningServer=server;gapStage=-1;server.tickRateManager().setFrozen(false);
        try{
            fixture(server,"draw");
            require(PublicTable.capture(table).seats().stream().allMatch(seat->seat.revealed().isEmpty()),"active hand identities leaked into PublicTable");
            require(table.hasSeparatedDrawnTile(0)&&table.handDisplayOrder(0).getLast().equals(tile(0,2)),"draw is not separated at the right");
            pass("active-round-public-privacy-and-separated-draw");
            stage=0;due=server.overworld().getGameTime()+3;save(false,"running");
        }catch(Throwable failure){failure.printStackTrace();stage=-1;save(false,failure.toString());}
    }

    private static void step() throws Exception{
        refreshView();
        long tick=runningServer.overworld().getGameTime();
        switch(stage){
            case 0->{
                Object view=view();var tiles=tiles(view);
                var a=tiles.get("hand/0/12").getOverridePos();var b=tiles.get("hand/0/13").getOverridePos();
                require(Math.abs(a.distanceTo(b)-TableLayout.TILE_WIDTH*4/3)<1e-6,"public renderer draw gap is not one third tile width");
                assertPrivate(false,14);
                pass("real-portviews-private-hand-and-public-draw-layout");
                int discarded=table.handDisplayOrder(0).indexOf(tile(2,9));
                heldOrder=new ArrayList<>(table.handDisplayOrder(0));heldOrder.remove(discarded);
                heldSlots=new ArrayList<>(table.handDisplaySlots(0));heldSlots.remove(discarded);
                heldPositions=new ArrayList<>(publicHandPositions());heldPositions.remove(discarded);
                if(owner!=null){
                    int index=table.handDisplayOrder(0).indexOf(tile(2,9));
                    table.onCuteClick(owner,new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_HAND,(short)index));
                    require(((MahjongTableHumanPlayer)table.driver().playerAt(0)).hasQueued(),"real TableBE click did not queue its displayed tile");
                }else table.driver().submitAction(0,new PlayerAction.Discard(tile(2,9)));
                stage=1;due=tick+1;
            }
            case 1->{
                var discards=table.driver().currentRound().players().get(0).discards();
                require(discards.size()==1&&discards.getLast().tile().equals(tile(2,9)),"real table click discarded a different tile");
                accepted=tick;
                require(table.handDisplayOrder(0).equals(heldOrder)&&table.hasSeparatedDrawnTile(0),"real table sorted immediately after discard");
                require(table.handDisplaySlots(0).equals(heldSlots),"discarded slot was immediately closed");assertHandGeometry(heldPositions);
                stage=2;due=accepted+9;
            }
            case 2->{
                require(table.handDisplayOrder(0).equals(heldOrder)&&table.hasSeparatedDrawnTile(0),"real table sorted before ten ticks");
                require(table.handDisplaySlots(0).equals(heldSlots),"discard gap disappeared before ten ticks");assertHandGeometry(heldPositions);
                stage=3;due=accepted+10;
            }
            case 3->{
                require(table.handDisplayOrder(0).equals(table.driver().currentRound().handDisplayOrder(0))&&!table.hasSeparatedDrawnTile(0),"real table did not sort at ten ticks");
                require(table.handDisplaySlots(0).equals(java.util.stream.IntStream.range(0,13).boxed().toList())&&table.handDisplayGapStarts(0).isEmpty(),"real table did not compact geometry at ten ticks");
                var compact=new ArrayList<Vec3>();var dir=PortViews.outward(0,table.getBlockState());
                for(int slot=0;slot<13;slot++)compact.add(TableLayout.anchor(POS,dir,-.44+slot*TableLayout.HAND_STEP,.65,.85));
                assertHandGeometry(compact);pass("real-server-discard-gap-and-exact-ten-tick-sort");
                fixture(runningServer,"all_tenpai");
                require(PublicTable.capture(table).seats().stream().allMatch(seat->seat.revealed().size()==13),"drawn round did not reveal all four tenpai hands");
                stage=4;due=tick+2;
            }
            case 4->{
                assertPrivate(true,0);
                require(tiles(view()).keySet().stream().filter(key->key.startsWith("hand/")).count()==52,"renderer did not create all revealed hand tiles");
                pass("all-four-tenpai-reveal-and-no-private-duplicate");
                fixture(runningServer,"mixed");
                var board=PublicTable.capture(table);
                for(int seat=0;seat<4;seat++)require(board.seats().get(seat).revealed().isEmpty()==(seat%2==1),"mixed tenpai allowlist incorrect at seat "+seat);
                pass("noten-hands-remain-private-at-drawn-result");
                fixture(runningServer,"riichi");stage=5;due=tick+2;
            }
            case 5->{
                var elements=tiles(view());int count=0;
                for(var entry:elements.entrySet())if(entry.getKey().startsWith("riichi/")||entry.getKey().startsWith("carried-riichi/")){
                    var at=entry.getValue().getOverridePos();
                    require(Math.abs(at.x-(POS.getX()+.5))<.126&&Math.abs(at.z-(POS.getZ()+.5))<.126,"riichi stick lies outside centre plate");
                    require(at.y>=POS.getY()+9.5/16,"riichi stick lies below centre plate top");
                    require(Math.abs(entry.getValue().getScale().x()-.22)<1e-6,"riichi stick size differs from reduced scale");count++;
                }
                require(count==6,"expected four declarations and two carried deposits");
                pass("six-riichi-sticks-on-centre-plate-at-reduced-size");
                fixture(runningServer,"claims");stage=6;due=tick+2;
            }
            case 6->{
                var buttons=MahjongTableHumanPlayer.tableButtons(table.driver(),1,false);
                require(buttons.stream().anyMatch(button->button instanceof MahjongTableHumanPlayer.TableButton.SubmitAction action&&action.action() instanceof PlayerAction.Chi),"chi fixture lacks chi");
                require(buttons.stream().anyMatch(button->button instanceof MahjongTableHumanPlayer.TableButton.SubmitAction action&&action.action() instanceof PlayerAction.Pon),"pon fixture lacks pon");
                require(buttons.stream().anyMatch(button->button instanceof MahjongTableHumanPlayer.TableButton.SubmitAction action&&action.action() instanceof PlayerAction.Daiminkan),"kan fixture lacks kan");
                assertButtons(1);pass("real-claim-buttons-chi-pon-kan-edge-geometry");
                fixture(runningServer,"tsumo");stage=7;due=tick+2;
            }
            case 7->{
                require(MahjongTableHumanPlayer.tableButtons(table.driver(),0,false).stream().anyMatch(button->button instanceof MahjongTableHumanPlayer.TableButton.SubmitAction action&&action.action() instanceof PlayerAction.DeclareTsumo),"tsumo fixture lacks tsumo");
                assertButtons(0);pass("real-tsumo-button-edge-geometry");
                realDelayedClickMapping();
                fixture(runningServer,"tsumo");
                stage=-1;save(true,null);System.out.println("RIICHI_INTEGRATION_PASS "+checks.size()+" checks; client="+(owner!=null));
            }
            default->throw new AssertionError("unexpected integration stage "+stage);
        }
    }

    private static void realDelayedClickMapping() throws Exception{
        if(owner==null)return;
        fixture(runningServer,"draw");var driver=table.driver();table.handDisplayOrder(0);
        driver.submitAction(0,new PlayerAction.Discard(tile(2,9)));driver.advance(.05);table.handDisplayOrder(0);finishClaims(driver);
        for(int seat=1;seat<4;seat++){
            driver.submitAction(seat,PlayerAction.Draw.INSTANCE);driver.advance(.05);table.handDisplayOrder(seat);
            driver.submitAction(seat,new PlayerAction.Discard(tile(4,2)));driver.advance(.05);table.handDisplayOrder(seat);finishClaims(driver);
        }
        driver.submitAction(0,PlayerAction.Draw.INSTANCE);driver.advance(.05);
        var displayed=table.handDisplayOrder(0);int index=displayed.indexOf(tile(0,2));
        require(index>=0&&!driver.currentRound().handDisplayOrder(0).get(index).equals(tile(0,2)),"delayed click fixture does not distinguish visual and canonical indices");
        require(table.handDisplaySlots(0).get(index)>index,"delayed click fixture lacks a shifted physical slot");
        var view=view();var update=view.getClass().getDeclaredMethod("update");update.setAccessible(true);update.invoke(view);
        aimAt(tiles(view).get("hand/0/"+index).getOverridePos());
        ((Map<?,?>)get(table,"privateClicks")).clear();
        require(PortViews.handleTileHit(owner,table),"shifted physical tile ray was not handled");
        require(((MahjongTableHumanPlayer)driver.playerAt(0)).hasQueued(),"delayed displayed-slot click did not queue");
        driver.advance(.05);
        require(driver.currentRound().players().get(0).discards().getLast().tile().equals(tile(0,2)),"delayed displayed-slot click discarded canonical slot's different tile");
        shiftedSlotMappedCorrectly=true;
        pass("real-table-delayed-display-index-discards-correct-tile");
    }

    private static List<Vec3> publicHandPositions() throws Exception{
        var elements=tiles(view());var positions=new ArrayList<Vec3>();
        for(int index=0;index<table.handDisplayOrder(0).size();index++){
            var tile=elements.get("hand/0/"+index);require(tile!=null,"public hand tile missing at logical index "+index);positions.add(tile.getOverridePos());
        }
        return positions;
    }

    private static void assertHandGeometry(List<Vec3> expected) throws Exception{
        var seat=PublicTable.capture(table).seats().get(0);
        require(seat.handSlots().equals(table.handDisplaySlots(0))&&seat.handGapStarts().equals(table.handDisplayGapStarts(0)),"public snapshot lost sparse hand geometry");
        var actual=publicHandPositions();require(actual.size()==expected.size(),"public hand geometry count changed");
        for(int index=0;index<expected.size();index++)require(actual.get(index).distanceTo(expected.get(index))<1e-7,"public survivor moved at logical index "+index+": "+actual.get(index)+" != "+expected.get(index));
        if(owner==null)return;
        var hand=(PrivateHand)((Map<?,?>)get(view(),"hands")).get(owner.getUUID());
        require(hand!=null,"connected owner private holder missing");
        require(hand.snapshot.handSlots().equals(seat.handSlots())&&hand.snapshot.handGapStarts().equals(seat.handGapStarts()),"private snapshot lost sparse hand geometry");
        var remaining=new ArrayList<Vec3>(expected);
        for(var element:hand.holder.getElements())if(element instanceof ItemDisplayElement display){
            int found=-1;for(int index=0;index<remaining.size();index++)if(display.getOverridePos().distanceTo(remaining.get(index))<1e-7){found=index;break;}
            require(found>=0,"private hand tile moved or overlaps another tile: "+display.getOverridePos());remaining.remove(found);
        }
        require(remaining.isEmpty(),"private hand omitted survivor positions");
    }

    private static void prepareGap(MinecraftServer server){
        try{
            checks.clear();stage=-1;gapStage=-1;runningServer=server;gapSnapshots.clear();emptySlotIgnored=false;shiftedSlotMappedCorrectly=false;
            server.tickRateManager().setFrozen(false);fixture(server,"draw");require(owner!=null,"discard gap visual test requires a connected client");
            gapDiscardIndex=table.handDisplayOrder(0).indexOf(tile(2,9));require(gapDiscardIndex==8,"fixture discard must occupy physical slot eight");
            gapBeforeOrder=List.copyOf(table.handDisplayOrder(0));gapBeforeSlots=List.copyOf(table.handDisplaySlots(0));
            gapStage=0;gapDue=server.overworld().getGameTime()+3;
            saveGap(false,"running");
        }catch(Throwable failure){gapStage=-1;saveGap(false,failure.toString());throw new RuntimeException(failure);}
    }

    private static void discardGap(){
        require(gapStage==1,"wait for RIICHI_GAP_BEFORE_READY before gap_discard");
        table.onCuteClick(owner,new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_HAND,(short)gapDiscardIndex));
        require(((MahjongTableHumanPlayer)table.driver().playerAt(0)).hasQueued(),"discard gap click did not queue");
        gapStage=2;runningServer.tickRateManager().setFrozen(false);
    }

    private static void gapStep() throws Exception{
        refreshView();
        long tick=runningServer.overworld().getGameTime();
        if(gapStage==0&&tick>=gapDue){
            gapBeforePositions=List.copyOf(publicHandPositions());assertHandGeometry(gapBeforePositions);
            require(gapBeforeSlots.equals(java.util.stream.IntStream.range(0,14).boxed().toList()),"baseline hand slots are not complete");
            gapSnapshots.put("before",gapSnapshot());runningServer.tickRateManager().setFrozen(true);gapStage=1;
            System.out.println("RIICHI_GAP_BEFORE_READY gameTime="+tick);saveGap(false,"awaiting discard");
        }else if(gapStage==2&&table.driver().currentRound().players().get(0).discards().size()==1){
            gapAccepted=tick;heldOrder=new ArrayList<>(gapBeforeOrder);heldOrder.remove(gapDiscardIndex);
            heldSlots=new ArrayList<>(gapBeforeSlots);heldSlots.remove(gapDiscardIndex);
            heldPositions=new ArrayList<>(gapBeforePositions);heldPositions.remove(gapDiscardIndex);
            assertGapHeld();
            int discards=table.driver().currentRound().players().get(0).discards().size();
            float yaw=owner.getYRot(),pitch=owner.getXRot();aimAt(gapBeforePositions.get(gapDiscardIndex));
            ((Map<?,?>)get(table,"privateClicks")).clear();
            require(PortViews.handleTileHit(owner,table),"empty physical slot did not consume the ray");
            require(!((MahjongTableHumanPlayer)table.driver().playerAt(0)).hasQueued(),"empty slot queued a different tile");
            require(table.seatOfPlayer(owner.getUUID()).orElse(-1)==0&&table.driver().currentRound().players().get(0).discards().size()==discards,"empty slot changed seat or discards");
            owner.teleportTo(owner.level(),owner.getX(),owner.getY(),owner.getZ(),Set.of(),yaw,pitch,true);emptySlotIgnored=true;
            gapSnapshots.put("0",gapSnapshot());runningServer.tickRateManager().setFrozen(true);gapStage=3;
            System.out.println("RIICHI_GAP_TICK_READY relative=0 gameTime="+tick);saveGap(false,"awaiting tick nine");
        }else if(gapStage==3&&tick>=gapAccepted+9){
            require(tick==gapAccepted+9,"discard gap missed exact nine-tick observation");assertGapHeld();gapSnapshots.put("9",gapSnapshot());gapStage=4;
            System.out.println("RIICHI_GAP_TICK_READY relative=9 gameTime="+tick);saveGap(false,"awaiting tick ten");
        }else if(gapStage==4&&tick>=gapAccepted+10){
            require(tick==gapAccepted+10,"discard gap missed exact ten-tick observation");
            require(table.handDisplayOrder(0).equals(table.driver().currentRound().handDisplayOrder(0)),"hand is not canonically sorted at tick ten");
            require(table.handDisplaySlots(0).equals(java.util.stream.IntStream.range(0,13).boxed().toList())&&table.handDisplayGapStarts(0).isEmpty()&&!table.hasSeparatedDrawnTile(0),"hand did not compact at tick ten");
            var compact=new ArrayList<Vec3>();var dir=PortViews.outward(0,table.getBlockState());for(int slot=0;slot<13;slot++)compact.add(TableLayout.anchor(POS,dir,-.44+slot*TableLayout.HAND_STEP,.65,.85));assertHandGeometry(compact);
            gapSnapshots.put("10",gapSnapshot());gapStage=5;
            System.out.println("RIICHI_GAP_TICK_READY relative=10 gameTime="+tick);saveGap(false,"awaiting finish");
        }
    }

    private static void assertGapHeld() throws Exception{
        require(table.handDisplayOrder(0).equals(heldOrder),"surviving tile order changed during discard delay");
        require(table.handDisplaySlots(0).equals(heldSlots)&&!heldSlots.contains(gapDiscardIndex),"discard slot was immediately closed");
        require(table.hasSeparatedDrawnTile(0)&&table.handDisplayOrder(0).getLast().equals(tile(0,2)),"old drawn tile lost its separated right-hand position");
        assertHandGeometry(heldPositions);
    }

    private static void aimAt(Vec3 centre){
        var delta=centre.subtract(owner.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        owner.teleportTo(owner.level(),owner.getX(),owner.getY(),owner.getZ(),Set.of(),yaw,pitch,true);
    }

    private static void refreshView() throws Exception{
        // Fabric orders addon callbacks before the core callback on this test loader.
        // Read the same end-tick renderer state the core flushes later in this tick.
        var current=view();var update=current.getClass().getDeclaredMethod("update");update.setAccessible(true);update.invoke(current);
    }

    private static Map<String,Object> gapSnapshot(){
        try{
            var result=new LinkedHashMap<String,Object>();long tick=runningServer.overworld().getGameTime();result.put("gameTime",tick);result.put("relativeTick",gapStage<2?null:tick-gapAccepted);
            result.put("tiles",table.handDisplayOrder(0).stream().map(PublicTable::code).toList());result.put("slots",table.handDisplaySlots(0));result.put("gapStarts",table.handDisplayGapStarts(0));
            result.put("separatedDraw",table.hasSeparatedDrawnTile(0));result.put("positions",publicHandPositions().stream().map(p->List.of(p.x,p.y,p.z)).toList());return result;
        }catch(Exception error){throw new RuntimeException(error);}
    }

    private static void finishGap(){
        try{
            require(gapStage==5&&gapSnapshots.keySet().containsAll(List.of("before","0","9","10")),"capture all exact discard-gap stages before finishing");
            realDelayedClickMapping();require(emptySlotIgnored&&shiftedSlotMappedCorrectly,"discard gap click regressions were not checked");
            gapStage=-1;saveGap(true,null);System.out.println("RIICHI_DISCARD_GAP_PASS");
        }catch(Throwable failure){failure.printStackTrace();gapStage=-1;saveGap(false,failure.toString());}
    }

    private static void saveGap(boolean passed,String failure){
        try{
            var result=new LinkedHashMap<String,Object>();result.put("passed",passed);result.put("coreVersion",FabricLoader.getInstance().getModContainer("riichi_mahjong").orElseThrow().getMetadata().getVersion().getFriendlyString());
            result.put("clientConnected",owner!=null);result.put("delayTicks",10);result.put("gapSlot",gapDiscardIndex);result.put("snapshots",gapSnapshots);result.put("emptySlotIgnored",emptySlotIgnored);result.put("shiftedSlotMappedCorrectly",shiftedSlotMappedCorrectly);result.put("failure",failure);
            Files.writeString(Path.of("riichi-discard-gap-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        }catch(Exception error){throw new RuntimeException(error);}
    }

    private static void finishClaims(TheMahjongDriver driver){
        for(int attempt=0;driver.currentPhase() instanceof MatchPhase.AwaitingClaims claims;attempt++){
            require(attempt<8,"fixture claims did not finish");
            for(int seat:claims.pendingSeats())if(driver.legalActions(seat).contains(PlayerAction.Pass.INSTANCE))driver.submitAction(seat,PlayerAction.Pass.INSTANCE);
            driver.advance(.05);
        }
    }

    private static void assertPrivate(boolean revealed,int tilesExpected) throws Exception{
        if(owner==null)return;
        var hands=(Map<?,?>)get(view(),"hands");var hand=(PrivateHand)hands.get(owner.getUUID());
        require(hand!=null,"PortViews did not generate the connected owner's private holder");
        require(hand.snapshot.revealed()==revealed,"private snapshot reveal mismatch");
        require(hand.holder.getElements().stream().filter(element->element instanceof ItemDisplayElement).count()==tilesExpected,"private hand has missing tiles or public duplicates");
    }

    private static void assertButtons(int seat) throws Exception{
        if(owner==null)return;
        var hand=(PrivateHand)((Map<?,?>)get(view(),"hands")).get(owner.getUUID());
        require(hand!=null&&!hand.buttonLayout.isEmpty(),"private button displays not generated");
        var dir=PortViews.outward(seat,table.getBlockState());
        for(var button:hand.buttonLayout){
            var centre=button.centre(POS,dir);
            require(button.hit(POS,dir,centre.add(0,1,0),centre.add(0,-1,0)).isPresent(),"text centre not clickable");
            var side=dir.getCounterClockWise();
            var outside=centre.add(side.getStepX()*(button.width()/2+.001),0,side.getStepZ()*(button.width()/2+.001));
            require(button.hit(POS,dir,outside.add(0,1,0),outside.add(0,-1,0)).isEmpty(),"click plane extends past displayed text");
        }
        for(var element:hand.holder.getElements())if(element instanceof TextDisplayElement display)
            require(display.getBillboardMode()==Display.BillboardConstraints.FIXED,"button text faces camera instead of table edge");
    }

    private static void fixture(MinecraftServer server,String scenario){
        try{
            var level=server.overworld();level.getChunkAt(POS);
            var state=ModBlocks.MAHJONG_TABLE.get().defaultBlockState();
            if(!(level.getBlockEntity(POS) instanceof MahjongTableBlockEntity)){
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.setBlock(POS.offset(dx,0,dz),state.setValue(BaseMultipartBlock.PART_X,dx+1).setValue(BaseMultipartBlock.PART_Z,dz+1),3);
            }
            table=(MahjongTableBlockEntity)level.getBlockEntity(POS);
            require(table!=null,"real MahjongTableBlockEntity not created");
            int ownerSeat=scenario.equals("draw_observer")?-1:scenario.equals("claims")?1:0;
            owner=server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            var seats=new ArrayList<MahjongTableBlockEntity.SeatInfo>();
            var controllers=new ArrayList<MahjongPlayerInterface>();
            for(int seat=0;seat<4;seat++){
                if(owner!=null&&seat==ownerSeat){
                    seats.add(MahjongTableBlockEntity.SeatInfo.claimed(owner.getUUID()));
                    var human=new MahjongTableHumanPlayer();human.setOccupant(owner.getUUID());controllers.add(human);
                }else{seats.add(MahjongTableBlockEntity.SeatInfo.open());controllers.add(new MahjongHumanPlayer());}
            }
            var players=new ArrayList<TheMahjongPlayer>();for(int seat=0;seat<4;seat++)players.add(player(waiting(seat%3),false));
            TheMahjongRound.State roundState=TheMahjongRound.State.TURN;
            TheMahjongRound.ActiveTile active=TheMahjongRound.ActiveTile.drawn(tile(0,2));
            MatchPhase phase=new MatchPhase.AwaitingDiscard(0);int sticks=0;
            switch(scenario){
                case "draw","draw_observer"->{var hand=new ArrayList<>(waiting(2));hand.add(tile(0,2));players.set(0,player(hand,false));}
                case "tsumo"->{var hand=new ArrayList<>(waiting(0));hand.add(tile(4,1));players.set(0,player(hand,false));active=TheMahjongRound.ActiveTile.drawn(tile(4,1));}
                case "claims"->{
                    var source=new ArrayList<>(waiting(2));source.add(tile(0,3));players.set(0,player(source,false));
                    players.set(1,player(List.of(tile(0,1),tile(0,2),tile(0,3),tile(0,3),tile(0,3),tile(1,1),tile(1,2),tile(1,3),tile(2,1),tile(2,2),tile(2,3),tile(3,1),tile(3,1)),false));
                    active=TheMahjongRound.ActiveTile.drawn(tile(0,3));
                }
                case "all_tenpai","mixed"->{
                    roundState=TheMahjongRound.State.ENDED;active=TheMahjongRound.ActiveTile.none();phase=new MatchPhase.RoundEnded(List.of());
                    if(scenario.equals("mixed"))for(int seat:new int[]{1,3})players.set(seat,player(List.of(tile(0,1),tile(0,2),tile(0,4),tile(0,5),tile(0,7),tile(0,8),tile(1,1),tile(1,4),tile(1,7),tile(2,2),tile(2,5),tile(2,8),tile(4,2)),false));
                }
                case "riichi"->{for(int seat=0;seat<4;seat++)players.set(seat,player(waiting(seat%3),true));active=TheMahjongRound.ActiveTile.none();phase=new MatchPhase.AwaitingDraw(0,false);sticks=6;}
                default->throw new IllegalArgumentException("scenario must be draw, draw_observer, tsumo, claims, all_tenpai, mixed, or riichi");
            }
            var indicators=Collections.nCopies(5,tile(4,3));
            var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,sticks,0,roundState,0,-1,active,
                    Collections.nCopies(30,tile(4,2)),Collections.nCopies(4,tile(4,3)),indicators,indicators,1,players,List.of());
            var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,TheMahjongTileSet.standardRiichi(false),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
            var driver=new TheMahjongDriver(match,controllers,new Random(1),new TheMahjongDriver.Snapshot(phase,false,OptionalInt.empty()));
            if(scenario.equals("claims")){driver.submitAction(0,new PlayerAction.Discard(tile(0,3)));driver.advance(.05);}
            set(table,"driver",driver);set(table,"seats",seats);set(table,"resultAnimStage",MahjongTableBlockEntity.ResultAnimStage.NONE);table.setChanged();
            if(owner!=null){var direction=PortViews.outward(ownerSeat,state);float yaw=switch(direction){case NORTH->0;case EAST->90;case WEST->-90;default->180;};
                owner.setGameMode(GameType.CREATIVE);owner.teleportTo(level,.5+direction.getStepX()*2.35,-60,.5+direction.getStepZ()*2.35,Set.of(),yaw,29,true);
            }
            var ensure=PortViews.class.getDeclaredMethod("ensure",ServerLevel.class,BlockPos.class);ensure.setAccessible(true);ensure.invoke(null,level,POS);
            System.out.println("RIICHI_FIXTURE_READY "+scenario+" owner="+(owner==null?"none":owner.getName().getString())+" seat="+ownerSeat+" pos="+POS.toShortString());
        }catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new RuntimeException(failure);}
    }

    private static TheMahjongTile tile(int suit,int rank){return new TheMahjongTile(TheMahjongTile.Suit.values()[suit],rank,false);}
    private static List<TheMahjongTile> waiting(int suit){return List.of(tile(suit,1),tile(suit,2),tile(suit,3),tile(suit,4),tile(suit,5),tile(suit,6),tile(suit,7),tile(suit,8),tile(suit,9),tile(3,1),tile(3,1),tile(3,1),tile(4,1));}
    private static TheMahjongPlayer player(List<TheMahjongTile> hand,boolean riichi){return new TheMahjongPlayer(25000,riichi?TheMahjongPlayer.RiichiState.RIICHI:TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(),List.of(),List.of());}
    private static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    private static void pass(String check){checks.add(check);System.out.println("RIICHI_INTEGRATION_CHECK_PASS "+check);}
    private static Object get(Object instance,String name) throws Exception{Field field=instance instanceof Class<?> type?type.getDeclaredField(name):instance.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(instance instanceof Class<?>?null:instance);}
    private static void set(Object instance,String name,Object value) throws Exception{var field=instance.getClass().getDeclaredField(name);field.setAccessible(true);field.set(instance,value);}
    private static Object view() throws Exception{
        for(var value:((Map<?,?>)get(PortViews.class,"VIEWS")).values())if(POS.equals(get(value,"pos")))return value;
        throw new AssertionError("PortViews did not attach the fixture table");
    }
    @SuppressWarnings("unchecked") private static Map<String,ItemDisplayElement> tiles(Object view) throws Exception{return (Map<String,ItemDisplayElement>)get(view,"tiles");}
    private static void save(boolean passed,String failure){
        try{
            var result=new LinkedHashMap<String,Object>();result.put("passed",passed);result.put("coreVersion",FabricLoader.getInstance().getModContainer("riichi_mahjong").orElseThrow().getMetadata().getVersion().getFriendlyString());
            result.put("clientConnected",owner!=null);result.put("checks",checks);result.put("failure",failure);result.put("worldGameTime",runningServer.overworld().getGameTime());
            Files.writeString(RESULTS,new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        }catch(Exception error){throw new RuntimeException(error);}
    }
}
