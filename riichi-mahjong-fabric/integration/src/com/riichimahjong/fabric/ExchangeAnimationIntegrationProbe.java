package com.riichimahjong.fabric;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.cuterenderer.InteractKey;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.riichimahjong.registry.ModBlocks;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.driver.bots.StupidActiveBot;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import io.netty.channel.ChannelFutureListener;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Explicit disposable-server probe for real exchange display allowlists and transient table guards. */
public final class ExchangeAnimationIntegrationProbe implements ModInitializer {
    private static final BlockPos POS=new BlockPos(8,300,8);
    private static final Path RESULT=Path.of("exchange-animation-integration-result.json");
    private record Saved(BlockState state,CompoundTag blockEntity) {}

    @Override public void onInitialize() {
        if(!Files.exists(Path.of("riichi-exchange-test-enabled")))
            throw new IllegalStateException("Exchange probe requires explicit disposable-server marker");
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(
                Commands.literal("riichi_exchange_test").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("run").executes(context->run(context.getSource().getServer())))));
    }
    private static int run(MinecraftServer server) {
        var result=new LinkedHashMap<String,Object>();var checks=new ArrayList<String>();
        var level=server.overworld();var saved=new LinkedHashMap<BlockPos,Saved>();var players=new ArrayList<ProbePlayer>();
        MahjongTableBlockEntity table=null;ExchangeRenderer renderer=null;ElementHolder holder=null;
        try {
            level.getChunkAt(POS);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                var pos=POS.offset(dx,0,dz);var be=level.getBlockEntity(pos);
                saved.put(pos,new Saved(level.getBlockState(pos),be==null?null:be.saveWithFullMetadata(server.registryAccess())));
            }
            var state=ModBlocks.MAHJONG_TABLE.get().defaultBlockState();
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.setBlock(POS.offset(dx,0,dz),
                    state.setValue(BaseMultipartBlock.PART_X,dx+1).setValue(BaseMultipartBlock.PART_Z,dz+1),3);
            table=(MahjongTableBlockEntity)level.getBlockEntity(POS);require(table!=null,"temporary master table missing");
            var sender=register(server,"ExchangeSender",players);var recipient=register(server,"ExchangeReceiver",players);
            var watcher=register(server,"ExchangeWatcher",players);var outsider=register(server,"ExchangeOutsider",players);
            var late=register(server,"ExchangeLate",players);
            require(table.claimSeat(0,sender.getUUID())&&table.claimSeat(1,recipient.getUUID()),"could not assign probe seat owners");
            for (var preset : List.of(RuleSetPreset.MAHJONG_SOUL_4P, RuleSetPreset.MAHJONG_SOUL_SANMA_3P, RuleSetPreset.SICHUAN_4P)) {
                duplicateExchange(table, sender, outsider, preset);
                checks.add("duplicate-exchange-clicks-private-marks-and-animation-" + preset.name());
            }
            var driver=new TheMahjongDriver(RuleSetPreset.MAHJONG_SOUL_4P.newMatch(MatchLength.SOUTH,true,true,false),bots(),new Random(5));
            driver.startMatch();
            for(int attempt=0;attempt<12&&ExchangePresentation.pending(driver);attempt++)driver.advance(30);
            require(!ExchangePresentation.pending(driver)&&driver.currentRound().players().stream().allMatch(p->p.currentHand().size()==13),
                    "exchange driver did not yield the accepted pre-draw hands");
            var session=ExchangePresentation.captureAfterExchange(driver);require(session!=null,"no accepted exchange session");
            set(table,"driver",driver);set(table,"exchangePresentation",session);
            var accepted=driver.currentRound();
            var tick=MahjongTableBlockEntity.class.getDeclaredMethod("serverTick");tick.setAccessible(true);
            for(int i=0;i<3;i++)tick.invoke(table);
            require(driver.currentRound()==accepted&&session.elapsedTicks()==3,"table advanced rules during floating exchange");
            require(table.tableButtonsForSeat(0).isEmpty(),"exchange exposed an actionable private button");
            table.onCuteClick(sender,new InteractKey.SeatSlot((byte)0,InteractKey.SeatSlot.AREA_HAND,(short)0));
            require(driver.currentRound()==accepted,"clicking a moving exchange tile mutated the running rules");
            checks.add("real-table-exchange-session-pauses-driver-and-rejects-hand-controls");

            holder=new ElementHolder();new ManualAttachment(holder,level,()->Vec3.atCenterOf(POS));
            renderer=new ExchangeRenderer(holder,level,POS,table.getBlockState());
            require(table.watch(watcher),"nearby explicit observer could not watch");table.setSpectatorHandsVisible(true);
            renderer.update(session,table);
            require(holder.getElements().size()==session.flights().size()*2+1,"exchange omitted backs, private faces or direction label");
            assertAudiences(renderer,session,table,players,true);
            checks.add("real-public-back-and-private-exchange-face-entities-have-correct-source-owner-spectator-allowlists");
            var earlyFaces=faces(renderer);
            holder.startWatching(late);
            require(table.watch(late),"late observer could not register");renderer.update(session,table);
            require(earlyFaces.stream().noneMatch(holder.getElements()::contains),"new observer audience did not retire prior face entity IDs");
            assertAudiences(renderer,session,table,players,true);
            var revealedFaces=faces(renderer);table.setSpectatorHandsVisible(false);renderer.update(session,table);
            require(revealedFaces.stream().noneMatch(holder.getElements()::contains),"disabled reveal retained prior face entity IDs");
            assertAudiences(renderer,session,table,players,false);
            checks.add("late-watcher-state-and-reveal-toggle-recreate-filtered-entities-without-concealed-face-leak");

            while(session.elapsedTicks()<30)session.advance();renderer.update(session,table);
            assertAudiences(renderer,session,table,players,false);
            checks.add("moving-private-face-ownership-hands-off-to-recipient-after-midpoint");
            var out=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);DriverNbt.writeDriver(out,driver);
            var restored=DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING,server.registryAccess(),out.buildResult()),bots(),new Random(8));
            require(!ExchangePresentation.pending(restored)&&restored.currentRound().equals(accepted),"saving midway repeated or reverted accepted swap");
            checks.add("interrupted-animation-nbt-keeps-one-final-accepted-swap");
            renderer.destroy();require(holder.getElements().isEmpty(),"exchange completion/destruction leaked display entities");
            checks.add("exchange-renderer-destruction-clears-all-temporary-entities");
            result.put("passed",true);
        } catch(Throwable error) {
            error.printStackTrace();result.put("passed",false);result.put("failure",error.toString());
        } finally {
            if(renderer!=null)renderer.destroy();if(holder!=null)holder.destroy();
            if(table!=null){setQuiet(table,"exchangePresentation",null);if(table.hasActiveMatch())table.endGame();table.clearSpectators();}
            for(var player:players){server.getPlayerList().getPlayers().remove(player);server.getPlayerList().getPlayersByUUID().remove(player.getUUID(),player);player.getTextFilter().leave();}
            for(var pos:saved.keySet())level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            for(var entry:saved.entrySet())level.setBlock(entry.getKey(),entry.getValue().state(),2);
            for(var entry:saved.entrySet())if(entry.getValue().blockEntity()!=null){
                var be=BlockEntity.loadStatic(entry.getKey(),entry.getValue().state(),entry.getValue().blockEntity(),server.registryAccess());if(be!=null)level.setBlockEntity(be);
            }
            result.put("checks",checks);result.put("fakePlayersRemoved",players.stream().allMatch(p->server.getPlayerList().getPlayer(p.getUUID())==null));
            result.put("originalRegionRestored",saved.entrySet().stream().allMatch(e->level.getBlockState(e.getKey()).equals(e.getValue().state())));
            result.put("clientVisualTest",false);
            try{Files.writeString(RESULT,new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");}catch(Exception error){error.printStackTrace();}
        }
        System.out.println("RIICHI_EXCHANGE_INTEGRATION "+new GsonBuilder().create().toJson(result));
        return Boolean.TRUE.equals(result.get("passed"))?1:0;
    }
    private static void duplicateExchange(MahjongTableBlockEntity table, ProbePlayer owner, ProbePlayer outsider,
            RuleSetPreset preset) throws Exception {
        var controllers = new ArrayList<>(bots().subList(0, preset.playerCount()));
        var human = new MahjongTableHumanPlayer();
        human.setOccupant(owner.getUUID());
        controllers.set(0, human);
        var driver = new TheMahjongDriver(preset.newMatch(MatchLength.SOUTH, true, true), controllers, new Random(0));
        driver.startMatch();
        driver.advance(30); // Let the other seats confirm before the human.
        set(table, "driver", driver);
        var order = table.handDisplayOrder(0);
        var duplicate = order.stream().filter(tile -> tile.suit() != TheMahjongTile.Suit.DRAGON
                && tile.suit() != TheMahjongTile.Suit.WIND && Collections.frequency(order, tile) > 1
                && order.stream().filter(other -> other.suit() == tile.suit() && !other.equals(tile)).count() >= 2)
                .findFirst().orElseThrow();
        int first = order.indexOf(duplicate);
        int second = order.lastIndexOf(duplicate);
        var other = IntStream.range(0, order.size())
                .filter(i -> order.get(i).suit() == duplicate.suit() && !order.get(i).equals(duplicate)).limit(2).boxed().toList();

        pick(table, owner, outsider, second, Set.of(second));
        pick(table, owner, outsider, second, Set.of());
        pick(table, owner, outsider, second, Set.of(second));
        pick(table, owner, outsider, first, Set.of(first, second));
        pick(table, owner, outsider, first, Set.of(second));
        pick(table, owner, outsider, other.getFirst(), Set.of(second, other.getFirst()));
        var selected = Set.of(second, other.getFirst(), other.getLast());
        pick(table, owner, outsider, other.getLast(), selected);
        // A fourth click on the unselected copy at capacity must leave all three selections intact.
        pick(table, owner, outsider, first, selected);

        var buttons = table.tableButtonsForSeat(0);
        int confirm = IntStream.range(0, buttons.size())
                .filter(i -> buttons.get(i) instanceof MahjongTableHumanPlayer.TableButton.ConfirmExchange).findFirst().orElseThrow();
        table.onCuteClick(owner, new InteractKey.SeatSlot((byte) 0, InteractKey.SeatSlot.AREA_BUTTON, (short) confirm));
        var session = table.exchangePresentation();
        require(session != null, "confirmed exchange did not start its animation");
        var sources = session.flights().stream().filter(f -> f.exchanged() && f.sourceSeat() == 0)
                .map(ExchangePresentation.Flight::sourceIndex).toList();
        require(sources.size() == 3 && selected.equals(Set.copyOf(sources)), "wrong outgoing exchange slots " + sources);
        table.endGame();
    }

    private static void pick(MahjongTableBlockEntity table, ProbePlayer owner, ProbePlayer outsider,
            int slot, Set<Integer> expected) {
        var hand = new PrivateHand(table, owner, 0, PrivateHand.snapshot(table, 0));
        try {
            require(hand.valid(owner), "private hand is stale before click");
            hand.click(InteractKey.SeatSlot.AREA_HAND, slot, false);
        } finally {
            hand.destroy();
        }
        var updated = new PrivateHand(table, owner, 0, PrivateHand.snapshot(table, 0));
        try {
            require(table.driver().exchangeSelection(0).size() == expected.size(), "wrong selected count after clicking slot " + slot);
            var marks = updated.holder.getElements().stream().filter(TextDisplayElement.class::isInstance)
                    .map(TextDisplayElement.class::cast).filter(text -> text.getText().getString().equals("▼")).toList();
            require(marks.size() == expected.size(), "wrong number of exchange markers after clicking slot " + slot);
            for (int index : expected) {
                var direction = PortViews.outward(0, table.getBlockState());
                var at = TableLayout.handAnchor(POS, direction,
                        TableLayout.handAlong(updated.snapshot.handSlots().get(index), updated.snapshot.handGapStarts()), false);
                var marked = at.add(0, .15 * TableLayout.TILE_ENLARGEMENT, 0);
                require(marks.stream().anyMatch(mark -> mark.getOverridePos().distanceTo(marked) < 1e-7
                                && mark.getVisibilityPredicate().test(owner) && !mark.getVisibilityPredicate().test(outsider)),
                        "private exchange marker missing from clicked tile " + index);
            }
        } finally {
            updated.destroy();
        }
    }

    private static List<MahjongPlayerInterface> bots(){return IntStream.range(0,4).mapToObj(i->(MahjongPlayerInterface)new StupidActiveBot()).toList();}
    private static ProbePlayer register(MinecraftServer server,String name,List<ProbePlayer> players){
        var player=new ProbePlayer(server,name);server.getPlayerList().getPlayers().add(player);server.getPlayerList().getPlayersByUUID().put(player.getUUID(),player);players.add(player);return player;
    }
    private static List<?> pairs(ExchangeRenderer renderer)throws Exception{var f=ExchangeRenderer.class.getDeclaredField("tiles");f.setAccessible(true);return (List<?>)f.get(renderer);}
    private static ItemDisplayElement element(Object pair,String name)throws Exception{var f=pair.getClass().getDeclaredField(name);f.setAccessible(true);return (ItemDisplayElement)f.get(pair);}
    private static List<ItemDisplayElement> faces(ExchangeRenderer renderer)throws Exception{var out=new ArrayList<ItemDisplayElement>();for(var pair:pairs(renderer))out.add(element(pair,"face"));return out;}
    private static void assertAudiences(ExchangeRenderer renderer,ExchangePresentation.Session session,MahjongTableBlockEntity table,List<ProbePlayer> players,boolean showSpectators)throws Exception{
        var pairs=pairs(renderer);
        for(int i=0;i<pairs.size();i++){
            var face=element(pairs.get(i),"face");var back=element(pairs.get(i),"back");
            int faceSeat=ExchangeRenderer.faceSeat(session.flights().get(i),session.elapsedTicks());
            for(var player:players){
                boolean allowed=table.seats().get(faceSeat).occupant().filter(player.getUUID()::equals).isPresent()
                        ||showSpectators&&table.isWatching(player.getUUID())&&table.seatOfPlayer(player.getUUID()).isEmpty();
                require(face.getVisibilityPredicate().test(player)==allowed,"exchange face leaked to another seat or ordinary bystander");
                require(back.getVisibilityPredicate().test(player)!=allowed,"exchange back audience does not complement private face");
            }
        }
    }
    private static void set(Object target,String name,Object value)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void setQuiet(Object target,String name,Object value){try{set(target,name,value);}catch(Exception error){throw new RuntimeException(error);}}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static final class ProbePlayer extends ServerPlayer{
        ProbePlayer(MinecraftServer server,String name){super(server,server.overworld(),new GameProfile(UUID.randomUUID(),name),ClientInformation.createDefault());setPos(POS.getX()+.5,POS.getY()+1,POS.getZ()+2.5);new ServerGamePacketListenerImpl(server,new CaptureConnection(),this,CommonListenerCookie.createInitial(getGameProfile(),false));}
        @Override public void sendSystemMessage(Component text){}
        @Override public void sendSystemMessage(Component text,boolean overlay){}
    }
    private static final class CaptureConnection extends Connection{
        CaptureConnection(){super(PacketFlow.SERVERBOUND);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isMemoryConnection(){return true;}
    }
}
