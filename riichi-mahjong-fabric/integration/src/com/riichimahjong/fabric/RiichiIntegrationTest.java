package com.riichimahjong.fabric;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableBlock;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.registry.ModBlocks;
import com.themahjong.*;
import com.themahjong.driver.*;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
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
    private static int presentationStage=-1;
    private static long presentationDue;
    private static final List<Map<String,Object>> paymentChecks=new ArrayList<>();
    private static int interactionStage=-1;
    private static long interactionDue,soundStartEpoch,soundStartTick;
    private static String soundCase;
    private static int soundExpected,soundInitialRiver;
    private static final List<String> interactionChecks=new ArrayList<>();
    private static final List<Map<String,Object>> interactionButtons=new ArrayList<>(),denseInteractionButtons=new ArrayList<>(),soundCases=new ArrayList<>();
    private static boolean interactionPassed;
    private static int viewLifecycleStage=-1;
    private static long viewLifecycleDue;
    private static MinecraftServer viewLifecycleServer;
    private static Object lifecycleView,lifecycleDriver,lifecycleRound;
    private static List<List<TheMahjongTile>> lifecycleHands;
    private static List<Integer> lifecyclePoints;
    private static final BlockPos LIFECYCLE_UNAVAILABLE_POS=new BlockPos(1_000_000,-60,1_000_000);
    private static final Map<String,Object> lifecycleSnapshots=new LinkedHashMap<>();

    @Override public void onInitialize(){
        if(!Files.exists(Path.of("riichi-integration-enabled")))throw new IllegalStateException("Test addon requires isolated-server marker riichi-integration-enabled");
        CommandRegistrationCallback.EVENT.register((dispatcher,access,env)->dispatcher.register(Commands.literal("riichi_test")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("run").executes(context->{begin(context.getSource().getServer());return 1;}))
                .then(Commands.literal("aim").then(Commands.argument("index",IntegerArgumentType.integer(0)).executes(context->{
                    aim(context.getSource().getServer(),IntegerArgumentType.getInteger(context,"index"));return 1;
                })))
                .then(Commands.literal("state").executes(context->{state(context.getSource().getServer());return 1;}))
                .then(Commands.literal("view_state").executes(context->{printViewState(context.getSource().getServer());return 1;}))
                .then(Commands.literal("detach_view").executes(context->{detachView(context.getSource().getServer());return 1;}))
                .then(Commands.literal("view_lifecycle").executes(context->{beginViewLifecycle(context.getSource().getServer());return 1;}))
                .then(Commands.literal("gap_discard").executes(context->{discardGap();return 1;}))
                .then(Commands.literal("gap_state").executes(context->{System.out.println("RIICHI_GAP_STATE "+new GsonBuilder().create().toJson(gapSnapshot()));return 1;}))
                .then(Commands.literal("gap_finish").executes(context->{finishGap();return 1;}))
                .then(Commands.literal("presentation").executes(context->{beginPresentation(context.getSource().getServer());return 1;}))
                .then(Commands.literal("interaction").executes(context->{beginInteraction(context.getSource().getServer());return 1;}))
                .then(Commands.literal("sound_case").then(Commands.argument("scenario",StringArgumentType.word()).executes(context->{beginSoundCase(context.getSource().getServer(),StringArgumentType.getString(context,"scenario"));return 1;})))
                .then(Commands.literal("fixture").then(Commands.argument("scenario",StringArgumentType.word()).executes(context->{
                    String scenario=StringArgumentType.getString(context,"scenario");
                    if(scenario.equals("discard_gap")){prepareGap(context.getSource().getServer());return 1;}
                    stage=-1;gapStage=-1;presentationStage=-1;interactionStage=-1;viewLifecycleStage=-1;soundCase=null;context.getSource().getServer().tickRateManager().setFrozen(false);
                    fixture(context.getSource().getServer(),scenario);
                    context.getSource().sendSuccess(()->Component.literal("Riichi test fixture ready at 0 -60 0"),false);return 1;
                })))));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server!=runningServer)return;
            try{
                if(soundCase!=null){soundCaseStep();return;}
                if(interactionStage>=0){if(server.overworld().getGameTime()>=interactionDue)interactionStep();return;}
                if(presentationStage>=0){presentationStep();return;}
                if(gapStage>=0){gapStep();return;}
                if(stage>=0&&server.overworld().getGameTime()>=due)step();
            }catch(Throwable failure){failure.printStackTrace();if(interactionStage>=0||soundCase!=null){interactionStage=-1;soundCase=null;saveInteraction(false,failure.toString());}else if(presentationStage>=0){presentationStage=-1;savePresentation(false,failure.toString(),List.of());}else if(gapStage>=0){gapStage=-1;saveGap(false,failure.toString());}else{stage=-1;save(false,failure.toString());}}
        });
        // This callback only observes normal server ticks. In particular it never
        // calls refreshView/ensure after detaching the holder under test.
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server!=viewLifecycleServer||viewLifecycleStage<0||server.getTickCount()<viewLifecycleDue)return;
            try{viewLifecycleStep(server);}catch(Throwable failure){failure.printStackTrace();viewLifecycleStage=-1;saveViewLifecycle(server,false,failure.toString());}
        });
    }

    private static Object currentView(MinecraftServer server) throws Exception{
        for(var value:((Map<?,?>)get(PortViews.class,"VIEWS")).values())
            if(POS.equals(get(value,"pos"))&&get(value,"level")==server.overworld())return value;
        return null;
    }

    private static Map<String,Object> viewState(MinecraftServer server) throws Exception{
        var level=server.overworld();var chunk=level.getChunkSource().getChunkNow(POS.getX()>>4,POS.getZ()>>4);
        Object view=currentView(server);var state=new LinkedHashMap<String,Object>();
        ElementHolder holder=view==null?null:(ElementHolder)get(view,"holder");
        var attachment=holder==null?null:holder.getAttachment();
        state.put("serverTick",server.getTickCount());state.put("gameTime",level.getGameTime());
        state.put("viewPresent",view!=null);state.put("viewIdentity",view==null?null:System.identityHashCode(view));
        state.put("chunkAvailable",chunk!=null);state.put("holderAttachmentPresent",attachment!=null);
        state.put("holderAttachmentRemoved",attachment==null||attachment.isRemoved());
        state.put("chunkMatches",chunk!=null&&attachment instanceof ChunkAttachment chunkAttachment&&chunkAttachment.getChunk()==chunk);
        state.put("blockStateMatches",chunk!=null&&view!=null&&chunk.getBlockState(POS)==get(view,"state"));
        state.put("publicItemCount",holder==null?0:holder.getElements().stream().filter(ItemDisplayElement.class::isInstance).count());
        state.put("publicWatchers",holder==null?0:holder.getWatchingPlayers().size());
        state.put("publicWatcherNames",holder==null?List.of():holder.getWatchingPlayers().stream().map(connection->connection.player.getName().getString()).toList());
        state.put("privateHands",view==null?0:((Map<?,?>)get(view,"hands")).size());
        state.put("levelDimension",level.dimension().toString());
        state.put("players",server.getPlayerList().getPlayers().stream().map(player->{
            var data=new LinkedHashMap<String,Object>();data.put("name",player.getName().getString());data.put("dimension",player.level().dimension().toString());
            data.put("position",List.of(player.getX(),player.getY(),player.getZ()));return data;
        }).toList());
        return state;
    }

    private static void printViewState(MinecraftServer server){
        try{System.out.println("RIICHI_VIEW_STATE "+new GsonBuilder().create().toJson(viewState(server)));}
        catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new RuntimeException(failure);}
    }

    private static void detachView(MinecraftServer server){
        try{
            Object view=currentView(server);require(view!=null,"no current view at test table position");
            var holder=(ElementHolder)get(view,"holder");var attachment=holder.getAttachment();require(attachment!=null,"current holder already detached");
            attachment.destroy();
            System.out.println("RIICHI_VIEW_DETACHED "+new GsonBuilder().create().toJson(viewState(server)));
        }catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new RuntimeException(failure);}
    }

    private static void beginViewLifecycle(MinecraftServer server){
        runningServer=server;viewLifecycleServer=server;stage=-1;gapStage=-1;presentationStage=-1;interactionStage=-1;soundCase=null;
        lifecycleSnapshots.clear();viewLifecycleStage=-1;server.tickRateManager().setFrozen(false);
        try{fixture(server,"draw");viewLifecycleStage=0;viewLifecycleDue=server.getTickCount()+3;saveViewLifecycle(server,false,"running");}
        catch(Throwable failure){failure.printStackTrace();saveViewLifecycle(server,false,failure.toString());}
    }

    private static void viewLifecycleStep(MinecraftServer server) throws Exception{
        if(viewLifecycleStage==0){
            lifecycleView=currentView(server);require(lifecycleView!=null,"initial view missing");
            var initial=viewState(server);require(Boolean.TRUE.equals(initial.get("chunkMatches"))&&!Boolean.TRUE.equals(initial.get("holderAttachmentRemoved")),"initial attachment invalid");
            lifecycleDriver=table.driver();lifecycleRound=table.driver().currentRound();
            lifecycleHands=table.driver().currentRound().players().stream().map(player->List.copyOf(player.currentHand())).toList();
            lifecyclePoints=table.driver().currentRound().players().stream().map(TheMahjongPlayer::points).toList();
            require(server.overworld().getChunkSource().getChunkNow(LIFECYCLE_UNAVAILABLE_POS.getX()>>4,LIFECYCLE_UNAVAILABLE_POS.getZ()>>4)==null,"pending retry target unexpectedly loaded");
            var ensure=PortViews.class.getDeclaredMethod("ensure",ServerLevel.class,BlockPos.class);ensure.setAccessible(true);ensure.invoke(null,server.overworld(),LIFECYCLE_UNAVAILABLE_POS);
            lifecycleSnapshots.put("unavailablePendingTarget",LIFECYCLE_UNAVAILABLE_POS.toShortString());
            lifecycleSnapshots.put("before",initial);detachView(server);lifecycleSnapshots.put("detached",viewState(server));
            viewLifecycleStage=1;viewLifecycleDue=server.getTickCount()+2;return;
        }
        Object recovered=currentView(server);var state=viewState(server);lifecycleSnapshots.put("recovered",state);
        require(recovered!=null&&recovered!=lifecycleView,"main loop did not replace detached view");
        require(Boolean.TRUE.equals(state.get("chunkMatches"))&&!Boolean.TRUE.equals(state.get("holderAttachmentRemoved")),"recovered holder is not attached to current chunk");
        require(table.driver()==lifecycleDriver&&table.driver().currentRound()==lifecycleRound,"view recovery replaced driver or round");
        require(lifecycleHands.equals(table.driver().currentRound().players().stream().map(player->List.copyOf(player.currentHand())).toList()),"view recovery changed hands");
        require(lifecyclePoints.equals(table.driver().currentRound().players().stream().map(TheMahjongPlayer::points).toList()),"view recovery changed scores");
        var before=(Map<?,?>)lifecycleSnapshots.get("before");
        require(Objects.equals(state.get("publicItemCount"),before.get("publicItemCount")),"public table items not restored");
        require(Objects.equals(state.get("privateHands"),before.get("privateHands")),"private hands not restored");
        require(Objects.equals(state.get("publicWatchers"),before.get("publicWatchers")),"public watchers not restored");
        var oldHolder=(ElementHolder)get(lifecycleView,"holder");
        require(oldHolder.getAttachment()==null&&oldHolder.getWatchingPlayers().isEmpty(),"old public holder still attached or watched");
        require(((Map<?,?>)get(lifecycleView,"hands")).isEmpty(),"old private holders not disposed");
        require(server.overworld().getChunkSource().getChunkNow(LIFECYCLE_UNAVAILABLE_POS.getX()>>4,LIFECYCLE_UNAVAILABLE_POS.getZ()>>4)==null,"pending retry synchronously acquired an unavailable chunk");
        Object pending=get(PortViews.class,"PENDING");
        var keys=pending instanceof Map<?,?> map?map.keySet():(Collection<?>)pending;
        boolean retained=false;for(var key:keys)if(get(key,"level")==server.overworld()&&LIFECYCLE_UNAVAILABLE_POS.equals(get(key,"pos"))){retained=true;break;}
        require(retained,"pending unavailable chunk was dropped instead of retried");
        lifecycleSnapshots.put("unavailablePendingRetainedAfterTwoTicks",true);lifecycleSnapshots.put("unavailableChunkRemainedUnloaded",true);
        lifecycleSnapshots.put("driverPreserved",true);lifecycleSnapshots.put("roundPreserved",true);lifecycleSnapshots.put("handsPreserved",true);lifecycleSnapshots.put("scoresPreserved",true);
        lifecycleSnapshots.put("oldHoldersDisposed",true);viewLifecycleStage=-1;saveViewLifecycle(server,true,"");
        System.out.println("RIICHI_VIEW_LIFECYCLE_PASS automatic detached-holder recovery; game state preserved");
    }

    private static void saveViewLifecycle(MinecraftServer server,boolean passed,String failure){
        try{
            var report=new LinkedHashMap<String,Object>();report.put("passed",passed);report.put("failure",failure);
            report.put("coreVersion",FabricLoader.getInstance().getModContainer("riichi_mahjong").orElseThrow().getMetadata().getVersion().getFriendlyString());
            report.put("clientConnected",!server.getPlayerList().getPlayers().isEmpty());report.put("serverTick",server.getTickCount());
            report.put("snapshots",lifecycleSnapshots);Files.writeString(Path.of("riichi-view-lifecycle-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        }catch(Exception failureWriting){throw new RuntimeException(failureWriting);}
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
        checks.clear();runningServer=server;gapStage=-1;presentationStage=-1;server.tickRateManager().setFrozen(false);
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
            checks.clear();stage=-1;gapStage=-1;presentationStage=-1;runningServer=server;gapSnapshots.clear();emptySlotIgnored=false;shiftedSlotMappedCorrectly=false;
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

    private static void beginPresentation(MinecraftServer server){
        try{
            stage=-1;gapStage=-1;presentationStage=-1;runningServer=server;paymentChecks.clear();server.tickRateManager().setFrozen(false);
            for(int count=1;count<=3;count++)realDrawPayment(server,4,count);
            for(int count=1;count<=2;count++)realDrawPayment(server,3,count);
            fixture(server,"concealed_kan");require(owner!=null,"presentation client test requires a connected vanilla client");
            presentationStage=0;presentationDue=server.overworld().getGameTime()+3;
            savePresentation(false,"waiting for real client meld fixture",List.of());
        }catch(Throwable failure){failure.printStackTrace();presentationStage=-1;savePresentation(false,failure.toString(),List.of());}
    }

    private static List<TheMahjongTile> paymentReady(int index){
        var hand=new ArrayList<>(waiting(2));for(int at=9;at<=11;at++)hand.set(at,tile(3,index+1));return List.copyOf(hand);
    }

    private static List<TheMahjongTile> paymentNoten(){
        return List.of(tile(1,1),tile(1,2),tile(1,4),tile(1,5),tile(1,7),tile(1,8),tile(1,9),tile(2,2),tile(2,5),tile(2,8),tile(3,4),tile(4,2),tile(4,3));
    }

    private static void realDrawPayment(MinecraftServer server,int playerCount,int readyCount) throws Exception{
        fixture(server,"draw");int initial=playerCount==3?35000:25000;
        var players=new ArrayList<TheMahjongPlayer>();var controllers=new ArrayList<MahjongPlayerInterface>();
        for(int seat=0;seat<playerCount;seat++){
            boolean ready=seat>0&&seat<=readyCount;var hand=ready?paymentReady(seat-1):paymentNoten();
            players.add(new TheMahjongPlayer(initial,TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(),List.of(new TheMahjongDiscard(tile(1,3),false)),List.of()));
            controllers.add(new MahjongHumanPlayer());
        }
        var rules=playerCount==3?TheMahjongRuleSet.mahjongSoulSanma():TheMahjongRuleSet.mahjongSoul();
        var indicators=Collections.nCopies(5,tile(4,3));
        var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,0,0,TheMahjongRound.State.SETUP,0,-1,TheMahjongRound.ActiveTile.none(),
            List.of(tile(4,1)),Collections.nCopies(TheMahjongRound.rinshanTileCountFor(playerCount),tile(4,3)),indicators,indicators,1,players,List.of());
        var match=new TheMahjongMatch(playerCount,initial,playerCount==3?40000:30000,2,TheMahjongMatch.State.IN_ROUND,
            playerCount==3?TheMahjongTileSet.standardSanma(false):TheMahjongTileSet.standardRiichi(false),rules,List.of(),round);
        var driver=new TheMahjongDriver(match,controllers,new Random(7),new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDraw(0,false),false,OptionalInt.empty()));
        set(table,"driver",driver);set(table,"resultAnimStage",MahjongTableBlockEntity.ResultAnimStage.NONE);
        for(int seat=0;seat<playerCount;seat++)require(com.themahjong.compat.DrawSettlement.inTenpai(players.get(seat))==(seat>0&&seat<=readyCount),"payment fixture ready mask incorrect at "+seat);
        var phases=new ArrayList<String>();phases.add(driver.currentPhase().getClass().getSimpleName());
        driver.submitAction(0,PlayerAction.Draw.INSTANCE);driver.advance(.05);phases.add(driver.currentPhase().getClass().getSimpleName());
        require(driver.currentRound().liveWall().isEmpty()&&driver.currentPhase() instanceof MatchPhase.AwaitingDiscard,"fixture did not consume the final live-wall tile");
        require(driver.currentRound().players().stream().allMatch(p->p.points()==initial),"points changed before final discard");
        driver.submitAction(0,new PlayerAction.Discard(tile(4,1)));driver.advance(.05);phases.add(driver.currentPhase().getClass().getSimpleName());
        require(driver.currentPhase() instanceof MatchPhase.AwaitingClaims,"final discard fixture skipped the genuine claim window");
        var claim=(MatchPhase.AwaitingClaims)driver.currentPhase();require(!claim.pendingSeats().isEmpty(),"no legal final-discard response");
        require(claim.pendingSeats().stream().anyMatch(seat->driver.legalActions(seat).stream().anyMatch(a->a instanceof PlayerAction.DeclareRon)),"fixture lacks a real last-discard ron decision");
        require(driver.currentRound().players().stream().allMatch(p->p.points()==initial),"noten payment was applied while ron claims remained open");
        var responders=List.copyOf(claim.pendingSeats());finishClaims(driver);phases.add(driver.currentPhase().getClass().getSimpleName());
        require(driver.currentPhase() instanceof MatchPhase.RoundEnded ended&&ended.winResults().isEmpty()&&driver.currentRound().state()==TheMahjongRound.State.ENDED,"final-discard passes did not reach genuine exhaustive draw");
        List<Integer> deltas=playerCount==4?switch(readyCount){case 1->List.of(-1000,3000,-1000,-1000);case 2->List.of(-1500,1500,1500,-1500);default->List.of(-3000,1000,1000,1000);}:readyCount==1?List.of(-1500,3000,-1500):List.of(-3000,1500,1500);
        var actual=driver.currentRound().players().stream().map(TheMahjongPlayer::points).toList();
        for(int seat=0;seat<playerCount;seat++)require(actual.get(seat)==initial+deltas.get(seat),"incorrect real exhaustive-draw points: "+actual+" expected deltas "+deltas);
        require(actual.stream().mapToInt(Integer::intValue).sum()==initial*playerCount,"exhaustive draw did not conserve points");
        var board=PublicTable.capture(table);for(int seat=0;seat<playerCount;seat++){
            require(board.seats().get(seat).points()==actual.get(seat),"public seat score did not receive settled points");
            require(!board.seats().get(seat).revealed().isEmpty()==(seat>0&&seat<=readyCount),"draw fee and revealed ready-hand masks disagree");
        }
        var result=new LinkedHashMap<String,Object>();result.put("name",playerCount+"-player-"+readyCount+"-tenpai");result.put("passed",true);result.put("playerCount",playerCount);result.put("tenpaiCount",readyCount);result.put("initialPoints",initial);result.put("expectedDeltas",deltas);result.put("actualPoints",actual);result.put("phases",phases);result.put("claimResponders",responders);result.put("actualLastWallDraw",true);result.put("realClaimPasses",true);result.put("publicPointsMatch",true);result.put("revealMaskMatches",true);paymentChecks.add(result);
        System.out.println("RIICHI_PAYMENT_PASS "+playerCount+"p "+readyCount+"tenpai points="+actual);
    }

    private static void presentationStep() throws Exception{
        if(runningServer.overworld().getGameTime()<presentationDue)return;refreshView();
        var expected=new ArrayList<Map<String,Object>>();var publicBoard=PublicTable.capture(table);var elements=tiles(view());
        for(int seat=0;seat<4;seat++){
            var meld=publicBoard.seats().get(seat).melds().getFirst();require(meld.tiles().size()==4,"concealed kan did not create exactly four public tiles");
            for(int index=0;index<4;index++){
                boolean down=index==0||index==3;var data=meld.tiles().get(index);require(data.faceDown()==down,"concealed-kan public faceDown mask wrong");
                require((data.code()<0)==down,"concealed-kan ends leaked identity or middle identities were hidden");
                var element=elements.get("meld/"+seat+"/0/"+index);require(element!=null,"concealed-kan display entity missing");
                var dir=PortViews.outward(seat,table.getBlockState());float yaw=switch(dir){case EAST->-90;case WEST->90;case SOUTH->180;default->0;};
                var expectedRotation=new org.joml.Quaternionf().rotationX((float)Math.PI/2).rotateLocalY((float)Math.toRadians(yaw));if(down)expectedRotation.rotateLocalX((float)Math.PI);
                require(Math.abs(Math.abs(element.getLeftRotation().dot(expectedRotation))-1)<1e-5,"concealed-kan tile orientation is not face "+(down?"down":"up"));
                var entry=new LinkedHashMap<String,Object>();entry.put("key","meld/"+seat+"/0/"+index);entry.put("seat",seat);entry.put("index",index);entry.put("faceDown",down);entry.put("expectedModel","riichi_mahjong:"+PortViews.tileModel(data.code()));
                var at=element.getOverridePos();entry.put("position",List.of(at.x,at.y,at.z));var q=expectedRotation;entry.put("expectedLeftRotation",List.of(q.x,q.y,q.z,q.w));entry.put("scale",List.of(.1,.1,.1));expected.add(entry);
            }
        }
        presentationStage=-1;savePresentation(true,null,expected);System.out.println("RIICHI_PRESENTATION_PASS payments="+paymentChecks.size()+" meldTiles="+expected.size());
    }

    private static void savePresentation(boolean passed,String failure,List<Map<String,Object>> meldTiles){
        try{
            var result=new LinkedHashMap<String,Object>();result.put("passed",passed);result.put("coreVersion",FabricLoader.getInstance().getModContainer("riichi_mahjong").orElseThrow().getMetadata().getVersion().getFriendlyString());result.put("clientConnected",owner!=null);result.put("paymentScenarios",paymentChecks);result.put("meldTiles",meldTiles);result.put("failure",failure);result.put("gameTime",runningServer.overworld().getGameTime());
            Files.writeString(Path.of("riichi-presentation-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        }catch(Exception error){throw new RuntimeException(error);}
    }

    private static void beginInteraction(MinecraftServer server){
        try{
            runningServer=server;stage=-1;gapStage=-1;presentationStage=-1;interactionStage=-1;soundCase=null;
            interactionChecks.clear();interactionButtons.clear();denseInteractionButtons.clear();soundCases.clear();interactionPassed=false;
            server.tickRateManager().setFrozen(false);fixture(server,"tsumo");require(owner!=null,"interaction tests require a connected vanilla player");
            interactionStage=0;interactionDue=server.overworld().getGameTime()+3;saveInteraction(false,"running");
        }catch(Throwable failure){failure.printStackTrace();interactionStage=-1;saveInteraction(false,failure.toString());}
    }

    private static MahjongTableHumanPlayer ownerHuman(){
        return (MahjongTableHumanPlayer)table.driver().playerAt(table.seatOfPlayer(owner.getUUID()).orElseThrow());
    }

    private static PrivateHand privateHand() throws Exception{
        var hand=(PrivateHand)((Map<?,?>)get(view(),"hands")).get(owner.getUUID());
        require(hand!=null&&hand.valid(owner),"current private hand is missing or expired");return hand;
    }

    private static void clickPrivateButton(String text) throws Exception{
        refreshView();var hand=privateHand();
        var button=hand.buttonLayout.stream().filter(candidate->candidate.text().equals(text)).findFirst().orElseThrow(()->new AssertionError("no button: "+text));
        int seat=table.seatOfPlayer(owner.getUUID()).orElseThrow();aimAt(button.centre(POS,PortViews.outward(seat,table.getBlockState())));
        ((Map<?,?>)get(table,"privateClicks")).clear();
        var start=owner.getEyePosition();require(hand.handleButtonHit(start,start.add(owner.getLookAngle().scale(8))),"real private ray failed for "+text);
        refreshView();
    }

    private static void requireConfirmation() throws Exception{
        var labels=privateHand().buttonLayout.stream().map(TableButtonLayout.Button::text).toList();
        require(labels.equals(List.of("确认跳过","返回")),"confirmation UI is wrong: "+labels);
        require(!ownerHuman().hasQueued(),"first skip or fallback queued an unconfirmed win pass");
    }

    private static void invokeTableTick() throws Exception{
        var tick=MahjongTableBlockEntity.class.getDeclaredMethod("serverTick");tick.setAccessible(true);tick.invoke(table);
    }

    private static void interactionStep() throws Exception{
        refreshView();
        if(interactionStage==1){
            require(table.driver().currentRound().players().get(0).discards().size()==1,"confirmed riichi-tsumo skip did not resume automatic drawn-tile discard");
            require(table.driver().currentRound().players().get(0).discards().getLast().tile().equals(tile(4,1)),"automatic riichi discard after skipped tsumo chose the wrong tile");
            interactionChecks.add("riichi-tsumo-confirmation-resumes-real-tick-auto-discard");
            fixture(runningServer,"dragon_buttons");refreshView();interactionButtons.clear();interactionButtons.addAll(buttonDisplayEvidence("dragons"));
            interactionStage=-1;interactionPassed=true;saveInteraction(true,null);
            System.out.println("RIICHI_INTERACTION_PASS "+interactionChecks.size()+" checks; actual-client dragon fixture ready");return;
        }
        for(String scenario:List.of("ron","chankan")){
            fixture(runningServer,scenario);refreshView();int seat=table.seatOfPlayer(owner.getUUID()).orElseThrow();
            require(table.driver().legalActions(seat).stream().anyMatch(a->scenario.equals("ron")?a instanceof PlayerAction.DeclareRon:a instanceof PlayerAction.DeclareChankan),"fixture lacks "+scenario);
            clickPrivateButton("跳过");requireConfirmation();
            var stale=privateHand();var token=(MahjongTableHumanPlayer.TableButton.ConfirmSkip)stale.snapshot.buttons().getFirst();
            set(table,"lastCuteClickGameTime",-1L);table.onTableRightClick(owner);refreshView();requireConfirmation();
            require(!ownerHuman().selectTableButton(new MahjongTableHumanPlayer.TableButton.ConfirmSkip(UUID.randomUUID()),table.driver(),seat,owner),"wrong confirmation token was accepted");
            requireConfirmation();clickPrivateButton("返回");
            require(!ownerHuman().hasQueued()&&privateHand().buttonLayout.stream().anyMatch(b->b.text().equals(scenario.equals("ron")?"荣和":"抢杠")),"return did not restore the win");
            stale.click(InteractKey.SeatSlot.AREA_BUTTON,0,false);require(!ownerHuman().hasQueued(),"cancelled private holder submitted a stale confirmation");
            clickPrivateButton("跳过");requireConfirmation();
            require(!privateHand().snapshot.buttons().getFirst().equals(token),"a new prompt reused its cancelled token");
            clickPrivateButton("确认跳过");require(ownerHuman().hasQueued(),"explicit private confirmation did not queue pass");
            invokeTableTick();require(!ownerHuman().hasQueued()&&table.driver().legalActions(seat).isEmpty(),"confirmed pass was not accepted by real table tick");
            interactionChecks.add(scenario+"-private-confirm-return-wrong-token-and-accepted-pass");
        }
        fixture(runningServer,"ron");refreshView();clickPrivateButton("跳过");var expired=privateHand();
        fixture(runningServer,"ron");refreshView();expired.click(InteractKey.SeatSlot.AREA_BUTTON,0,false);
        require(!ownerHuman().hasQueued(),"a different round accepted an old private confirmation");
        interactionChecks.add("expired-private-confirmation-does-not-submit");

        fixture(runningServer,"tsumo");refreshView();clickPrivateButton("跳过");requireConfirmation();clickPrivateButton("返回");
        require(privateHand().buttonLayout.stream().anyMatch(b->b.text().equals("自摸")),"return did not restore tsumo");
        clickPrivateButton("跳过");requireConfirmation();clickPrivateButton("确认跳过");
        require(!ownerHuman().hasQueued()&&privateHand().buttonLayout.stream().noneMatch(b->b.text().equals("自摸")||b.text().equals("跳过")),"tsumo confirmation queued illegal Pass or left its button visible");
        table.onCuteClick(owner,new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_HAND,(short)0));
        require(ownerHuman().hasQueued(),"tsumo skip prevented an ordinary discard");
        invokeTableTick();require(table.driver().currentRound().players().get(0).discards().size()==1,"ordinary discard after skipped tsumo was not accepted");
        interactionChecks.add("tsumo-private-confirm-return-local-skip-and-normal-discard");

        fixture(runningServer,"draw");refreshView();owner.setShiftKeyDown(true);owner.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        aimAt(new Vec3(.5,-59.5,.5));((Map<?,?>)get(table,"privateClicks")).clear();set(table,"lastCuteClickGameTime",-1L);
        ((MahjongTableBlock)table.getBlockState().getBlock()).dispatchUse(table.getBlockState(),owner.level(),POS,owner,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(.5,-59.4,.5),Direction.UP,POS,false));
        require(table.seatOfPlayer(owner.getUUID()).orElse(-1)==0,"shift-right-click block left the active seat");
        PortMenus.settings(owner,table);require(owner.containerMenu instanceof PortMenus.Actions,"real settings menu did not open");
        ((PortMenus.Actions)owner.containerMenu).clicked(9,0,ContainerInput.PICKUP,owner);
        require(table.seatOfPlayer(owner.getUUID()).orElse(-1)==0,"own seat menu action left the seat");owner.closeContainer();
        set(table,"driver",null);refreshView();
        var seatHit=(InteractionElement)((Map<?,?>)get(view(),"seatHits")).get(0);require(seatHit!=null,"lobby seat interaction missing");
        seatHit.getInteractionHandler(owner).interact(owner,InteractionHand.MAIN_HAND,Vec3.ZERO,true);
        require(table.seatOfPlayer(owner.getUUID()).orElse(-1)==0,"sneaking lobby interaction left the seat");
        owner.setShiftKeyDown(false);fixture(runningServer,"draw");var match=table.driver().match();
        runningServer.getCommands().performPrefixedCommand(owner.createCommandSourceStack(),"mahjong leave");
        require(table.seatOfPlayer(owner.getUUID()).isEmpty()&&!(table.driver().playerAt(0) instanceof MahjongTableHumanPlayer),"actual leave command did not release the seat to BOT");
        require(table.driver().match()==match,"leave command changed the hand or score");
        runningServer.getCommands().performPrefixedCommand(owner.createCommandSourceStack(),"mahjong join");
        require(table.seatOfPlayer(owner.getUUID()).orElse(-1)==0&&table.driver().playerAt(0) instanceof MahjongTableHumanPlayer,"actual join command failed to reclaim the original seat");
        interactionChecks.add("only-command-leaves-block-menu-lobby-stay-seated-and-command-bot-rejoin");

        fixture(runningServer,"dense_buttons");refreshView();denseInteractionButtons.addAll(buttonDisplayEvidence("dense"));
        require(denseInteractionButtons.stream().anyMatch(button->((Number)((List<?>)button.get("scale")).getFirst()).doubleValue()<.1999),"dense fixture did not exercise scaled text");
        fixture(runningServer,"dragon_buttons");refreshView();interactionButtons.addAll(buttonDisplayEvidence("dragons"));
        var labels=privateHand().buttonLayout.stream().map(TableButtonLayout.Button::text).toList();
        require(labels.containsAll(List.of("暗杠 白 白 白 白","暗杠 发 发 发 发","暗杠 中 中 中 中")),"actual dragon labels do not follow HAKU/HATSU/CHUN: "+labels);
        interactionChecks.add("actual-private-button-three-character-gaps-at-normal-and-reduced-scale");
        interactionChecks.add("actual-private-dragon-buttons-use-white-green-red-enum-names");
        fixture(runningServer,"riichi_tsumo");refreshView();clickPrivateButton("跳过");requireConfirmation();clickPrivateButton("确认跳过");
        require(!ownerHuman().hasQueued(),"riichi tsumo skip must wait for ordinary auto-discard timing");
        interactionStage=1;interactionDue=runningServer.overworld().getGameTime()+8;saveInteraction(false,"waiting for real riichi auto-discard ticks");
    }

    private static List<Map<String,Object>> buttonDisplayEvidence(String scenario) throws Exception{
        var hand=privateHand();int seat=table.seatOfPlayer(owner.getUUID()).orElseThrow();var dir=PortViews.outward(seat,table.getBlockState());
        var evidence=new ArrayList<Map<String,Object>>();TableButtonLayout.Button prior=null;
        for(var button:hand.buttonLayout){
            var display=hand.holder.getElements().stream().filter(element->element instanceof TextDisplayElement text&&text.getText().getString().equals(button.text())).map(element->(TextDisplayElement)element).findFirst().orElseThrow();
            require(display.getOverridePos().distanceTo(button.textPosition(POS,dir))<1e-7,"actual button display is not aligned with its hit plane");
            require(Math.abs(display.getScale().x()-button.scale())<1e-6,"actual button scale differs from hit plane");
            double gap=prior==null?0:button.along()-button.width()/2-(prior.along()+prior.width()/2);
            if(prior!=null)require(Math.abs(gap-27*button.pixelSize())<1e-7,"actual edge gap is not three fullwidth glyph advances");
            var at=display.getOverridePos();var centre=button.centre(POS,dir);var rotation=display.getLeftRotation();
            var row=new LinkedHashMap<String,Object>();row.put("scenario",scenario);row.put("text",button.text());row.put("index",button.index());row.put("pixels",button.pixels());row.put("along",button.along());
            row.put("position",List.of(at.x,at.y,at.z));row.put("centre",List.of(centre.x,centre.y,centre.z));row.put("scale",List.of((double)display.getScale().x(),(double)display.getScale().y(),(double)display.getScale().z()));
            row.put("leftRotation",List.of((double)rotation.x(),(double)rotation.y(),(double)rotation.z(),(double)rotation.w()));row.put("width",button.width());row.put("depth",button.depth());row.put("gapBefore",gap);row.put("expectedGap",27*button.pixelSize());evidence.add(row);prior=button;
        }
        require(evidence.size()>=3,"button fixture lacks enough adjacent displays");return evidence;
    }

    private static void beginSoundCase(MinecraftServer server,String name){
        try{
            require(List.of("normal","riichi","bot","invalid","duplicate","claim","final").contains(name),"unknown sound case: "+name);
            require(soundCase==null,"wait for the current sound case to finish");
            runningServer=server;stage=-1;gapStage=-1;presentationStage=-1;interactionStage=-1;server.tickRateManager().setFrozen(false);
            fixture(server,name.equals("claim")?"claims":"draw");require(owner!=null,"sound packet checks require a connected vanilla player");
            soundCase=name;soundExpected=name.equals("invalid")||name.equals("claim")?0:1;
            soundInitialRiver=table.driver().currentRound().players().get(0).discards().size();soundStartTick=server.overworld().getGameTime();soundStartEpoch=System.currentTimeMillis();
            if(name.equals("bot"))table.driver().replacePlayer(0,com.themahjong.driver.bots.StupidActiveBot.humanLike());
            else if(name.equals("claim")){
                var buttons=MahjongTableHumanPlayer.tableButtons(table.driver(),1,false);int pon=-1;
                for(int index=0;index<buttons.size();index++)if(buttons.get(index) instanceof MahjongTableHumanPlayer.TableButton.SubmitAction action&&action.action() instanceof PlayerAction.Pon){pon=index;break;}
                require(pon>=0,"claim sound case lacks a legal pon");table.onCuteClick(owner,new InteractKey.SeatSlot((byte)1,InteractKey.SeatSlot.AREA_BUTTON,(short)pon));
                require(ownerHuman().hasQueued(),"real pon button did not queue a claim");
            }else{
                if(name.equals("final")){
                    var before=table.driver().currentRound();
                    var last=new TheMahjongRound(before.roundWind(),before.handNumber(),before.honba(),before.riichiSticks(),before.dealerSeat(),before.state(),before.currentTurnSeat(),before.claimSourceSeat().orElse(-1),before.activeTile(),List.of(),before.rinshanTiles(),before.doraIndicators(),before.uraDoraIndicators(),before.revealedDoraCount(),before.players(),List.of());
                    var old=table.driver();var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,old.match().tileSet(),old.rules(),List.of(),last);
                    var controllers=new ArrayList<MahjongPlayerInterface>();for(int seat=0;seat<4;seat++)controllers.add(old.playerAt(seat));
                    set(table,"driver",new TheMahjongDriver(match,controllers,new Random(5),old.snapshot()));
                }
                if(name.equals("riichi")){
                    var buttons=MahjongTableHumanPlayer.tableButtons(table.driver(),0,false);int toggle=-1;
                    for(int index=0;index<buttons.size();index++)if(buttons.get(index) instanceof MahjongTableHumanPlayer.TableButton.RiichiToggle){toggle=index;break;}
                    require(toggle>=0,"sound riichi fixture has no riichi toggle");table.onCuteClick(owner,new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_BUTTON,(short)toggle));
                    require(ownerHuman().riichiPending(),"real riichi toggle did not activate");
                }
                int index=name.equals("invalid")?300:table.handDisplayOrder(0).size()-1;
                table.onCuteClick(owner,new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_HAND,(short)index));
                if(name.equals("duplicate"))table.onCuteClick(owner,new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_HAND,(short)index));
                require(ownerHuman().hasQueued()!=name.equals("invalid"),"sound case queued an unexpected action");
            }
            System.out.println("RIICHI_SOUND_CASE_STARTED "+name+" startEpochMillis="+soundStartEpoch+" expectedCount="+soundExpected);
        }catch(Throwable failure){failure.printStackTrace();soundCase=null;saveInteraction(false,failure.toString());}
    }

    private static void soundCaseStep() throws Exception{
        if(runningServer.overworld().getGameTime()<soundStartTick+(soundCase.equals("bot")?32:6))return;
        String name=soundCase;int increase=table.driver().currentRound().players().get(0).discards().size()-soundInitialRiver;
        require(increase==soundExpected,"real table tick accepted unexpected discard count for "+name+": "+increase);
        if(name.equals("riichi"))require(table.driver().currentRound().players().get(0).riichi(),"riichi sound case did not accept a declaration discard");
        if(name.equals("claim"))require(table.driver().currentRound().players().get(1).melds().size()==1,"claim sound case did not accept the pon");
        if(name.equals("final"))require(table.driver().currentPhase() instanceof MatchPhase.RoundEnded,"final-discard sound fixture did not reach result phase");
        var row=new LinkedHashMap<String,Object>();row.put("name",name);row.put("passed",true);row.put("expectedCount",soundExpected);row.put("acceptedDiscards",increase);row.put("startEpochMillis",soundStartEpoch);row.put("endEpochMillis",System.currentTimeMillis());row.put("startGameTime",soundStartTick);row.put("endGameTime",runningServer.overworld().getGameTime());
        row.put("soundId","minecraft:block.stone.place");row.put("source","ambient");row.put("volume",1.0);row.put("pitch",2.0);row.put("position",List.of(.5,-59.5,.5));row.put("realServerTicks",true);
        soundCases.removeIf(previous->name.equals(previous.get("name")));soundCases.add(row);soundCase=null;saveInteraction(interactionPassed,null);
        System.out.println("RIICHI_SOUND_CASE_PASS "+name+" "+new GsonBuilder().create().toJson(row));
    }

    private static void saveInteraction(boolean passed,String failure){
        try{
            var result=new LinkedHashMap<String,Object>();result.put("passed",passed);result.put("coreVersion",FabricLoader.getInstance().getModContainer("riichi_mahjong").orElseThrow().getMetadata().getVersion().getFriendlyString());result.put("clientConnected",owner!=null);
            result.put("checks",interactionChecks);result.put("buttonDisplays",interactionButtons);result.put("denseButtonDisplays",denseInteractionButtons);result.put("soundCases",soundCases);result.put("failure",failure);result.put("gameTime",runningServer.overworld().getGameTime());
            Files.writeString(Path.of("riichi-interaction-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        }catch(Exception failureWriting){throw new RuntimeException(failureWriting);}
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
            int ownerSeat=scenario.equals("draw_observer")?-1:List.of("claims","ron","chankan","dense_buttons").contains(scenario)?1:0;
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
                case "tsumo","riichi_tsumo","ron"->{var hand=new ArrayList<>(waiting(0));hand.add(tile(4,1));players.set(0,player(hand,scenario.equals("riichi_tsumo")));active=TheMahjongRound.ActiveTile.drawn(tile(4,1));}
                case "chankan"->{
                    var hand=new ArrayList<TheMahjongTile>();for(int rank=1;rank<=9;rank++)hand.add(tile(0,rank));hand.add(tile(3,1));hand.add(tile(4,1));
                    var pon=new TheMahjongMeld.Pon(Collections.nCopies(3,tile(4,1)),0,3,0);
                    players.set(0,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(pon),List.of(),List.of()));active=TheMahjongRound.ActiveTile.drawn(tile(4,1));
                }
                case "dragon_buttons"->{var hand=new ArrayList<TheMahjongTile>();for(int rank=1;rank<=3;rank++)hand.addAll(Collections.nCopies(4,tile(4,rank)));hand.add(tile(3,1));hand.add(tile(3,1));players.set(0,player(hand,false));active=TheMahjongRound.ActiveTile.drawn(tile(3,1));}
                case "claims","dense_buttons"->{
                    var source=new ArrayList<>(waiting(2));source.add(tile(0,3));players.set(0,player(source,false));
                    players.set(1,player(List.of(tile(0,1),tile(0,2),tile(0,3),tile(0,3),tile(0,3),tile(1,1),tile(1,2),tile(1,3),tile(2,1),tile(2,2),tile(2,3),tile(3,1),tile(3,1)),false));
                    active=TheMahjongRound.ActiveTile.drawn(tile(0,3));
                    if(scenario.equals("dense_buttons"))players.set(1,player(List.of(tile(0,1),tile(0,2),tile(0,3),tile(0,3),tile(0,3),tile(0,4),tile(0,5),tile(1,1),tile(1,2),tile(1,3),tile(3,1),tile(3,1),tile(3,1)),false));
                }
                case "all_tenpai","mixed"->{
                    roundState=TheMahjongRound.State.ENDED;active=TheMahjongRound.ActiveTile.none();phase=new MatchPhase.RoundEnded(List.of());
                    if(scenario.equals("mixed"))for(int seat:new int[]{1,3})players.set(seat,player(List.of(tile(0,1),tile(0,2),tile(0,4),tile(0,5),tile(0,7),tile(0,8),tile(1,1),tile(1,4),tile(1,7),tile(2,2),tile(2,5),tile(2,8),tile(4,2)),false));
                }
                case "riichi"->{for(int seat=0;seat<4;seat++)players.set(seat,player(waiting(seat%3),true));active=TheMahjongRound.ActiveTile.none();phase=new MatchPhase.AwaitingDraw(0,false);sticks=6;}
                case "concealed_kan"->{
                    for(int seat=0;seat<4;seat++){
                        var meld=new TheMahjongMeld.Ankan(Collections.nCopies(4,tile(seat%3,seat+2)));
                        var hand=List.of(tile(2,1),tile(2,2),tile(2,3),tile(2,4),tile(2,5),tile(2,6),tile(2,7),tile(2,8),tile(2,9),tile(4,1));
                        players.set(seat,new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,hand,List.of(meld),List.of(),List.of()));
                    }
                    roundState=TheMahjongRound.State.SETUP;active=TheMahjongRound.ActiveTile.none();phase=new MatchPhase.AwaitingDraw(0,false);
                }
                default->throw new IllegalArgumentException("unknown fixture scenario "+scenario);
            }
            var indicators=Collections.nCopies(5,tile(4,3));
            var round=new TheMahjongRound(TheMahjongTile.Wind.EAST,1,0,sticks,0,roundState,0,-1,active,
                    Collections.nCopies(30,tile(4,2)),Collections.nCopies(4,tile(4,3)),indicators,indicators,1,players,List.of());
            var match=new TheMahjongMatch(4,25000,30000,2,TheMahjongMatch.State.IN_ROUND,TheMahjongTileSet.standardRiichi(false),TheMahjongRuleSet.mahjongSoul(),List.of(),round);
            var driver=new TheMahjongDriver(match,controllers,new Random(1),new TheMahjongDriver.Snapshot(phase,false,OptionalInt.empty()));
            if(scenario.equals("claims")||scenario.equals("dense_buttons")){driver.submitAction(0,new PlayerAction.Discard(tile(0,3)));driver.advance(.05);}
            if(scenario.equals("ron")){driver.submitAction(0,new PlayerAction.Discard(tile(4,1)));driver.advance(.05);}
            if(scenario.equals("chankan")){var kakan=driver.legalActions(0).stream().filter(action->action instanceof PlayerAction.Kakan).findFirst().orElseThrow();driver.submitAction(0,kakan);driver.advance(.05);}
            set(table,"driver",driver);set(table,"seats",seats);set(table,"resultAnimStage",MahjongTableBlockEntity.ResultAnimStage.NONE);table.setChanged();
            if(owner!=null){var direction=PortViews.outward(ownerSeat,state);float yaw=switch(direction){case NORTH->0;case EAST->90;case WEST->-90;default->180;};
                owner.setGameMode(GameType.CREATIVE);owner.teleportTo(level,.5+direction.getStepX()*2.35,-60,.5+direction.getStepZ()*2.35,Set.of(),yaw,29,true);
                if(scenario.equals("concealed_kan")){owner.getAbilities().flying=true;owner.onUpdateAbilities();owner.teleportTo(level,.5,-57.2,.5,Set.of(),0,90,true);}
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
