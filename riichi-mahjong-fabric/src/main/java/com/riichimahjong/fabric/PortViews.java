package com.riichimahjong.fabric;

import java.util.*;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.mahjongtable.*;
import com.riichimahjong.mahjongsolitaire.*;
import com.riichimahjong.mahjongaltar.MahjongAltarBlockEntity;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.cuterenderer.InteractKey;
import com.themahjong.driver.MatchPhase;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Vanilla display adapters using the pinned upstream geometry, textures and layout. */
public final class PortViews {
    private record Key(ServerLevel level,BlockPos pos){}
    private static final Map<Key,View> VIEWS=new HashMap<>();
    private static final int LOAD_RETRY_TICKS=200;
    private static final Map<Key,Integer> PENDING=new HashMap<>();
    public static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            // Flush hand positions every tick so a discard gap closes at its exact deadline.
            // Chunk-load callbacks may run before C2ME finishes making the chunk accessible.
            // Never synchronously acquire a chunk from inside that callback.
            for(var it=VIEWS.entrySet().iterator();it.hasNext();){var entry=it.next();var key=entry.getKey();var view=entry.getValue();
                var chunk=key.level.getChunkSource().getChunkNow(key.pos.getX()>>4,key.pos.getZ()>>4);
                if(chunk==null||chunk.getBlockState(key.pos)!=view.state||!view.attachedTo(chunk)){
                    view.destroy();it.remove();
                    if(chunk!=null)PENDING.put(key,LOAD_RETRY_TICKS);
                    continue;
                }
                view.update();
            }
            var pending=new HashMap<>(PENDING);PENDING.clear();
            for(var entry:pending.entrySet()){
                var key=entry.getKey();
                var chunk=key.level.getChunkSource().getChunkNow(key.pos.getX()>>4,key.pos.getZ()>>4);
                if(chunk==null){
                    // Async chunk loading may expose a load event before getChunkNow.
                    // Retry without loading the chunk ourselves; unload cancels the request.
                    if(entry.getValue()>1)PENDING.putIfAbsent(key,entry.getValue()-1);
                    continue;
                }
                var state=chunk.getBlockState(key.pos);
                if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("riichi_mahjong"))continue;
                if(state.hasProperty(BaseMultipartBlock.PART_X)&&!BaseMultipartBlock.isMaster(state))continue;
                if(!VIEWS.containsKey(key))VIEWS.put(key,new View(key.level,chunk,key.pos,state));
            }
        });
        ServerChunkEvents.CHUNK_LOAD.register((level,chunk,generated)->{for(var be:chunk.getBlockEntities().values())if(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getNamespace().equals("riichi_mahjong"))ensure(level,be.getBlockPos());});
        ServerChunkEvents.CHUNK_UNLOAD.register((level,chunk)->{
            // Polymer destroys the chunk attachment on unload. Retiring the cached
            // view here also removes private hands, HUDs and collision registrations.
            var pos=chunk.getPos();
            PENDING.keySet().removeIf(key->key.level==level&&(key.pos.getX()>>4)==pos.x()&&(key.pos.getZ()>>4)==pos.z());
            VIEWS.entrySet().removeIf(entry->{var key=entry.getKey();
                if(key.level!=level||(key.pos.getX()>>4)!=pos.x()||(key.pos.getZ()>>4)!=pos.z())return false;
                entry.getValue().destroy();return true;
            });
        });
        ServerLifecycleEvents.SERVER_STARTED.register(RecordVault::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{VIEWS.values().forEach(View::destroy);VIEWS.clear();PENDING.clear();});
        ServerEntityEvents.ENTITY_LOAD.register((entity,level)->{if(entity.entityTags().contains("timory_mahjong_display"))entity.discard();});
    }
    public static void ensure(ServerPlayer player,BlockPos pos){ensure(player.level(),pos);}
    private static void ensure(ServerLevel level,BlockPos pos){
        var key=new Key(level,pos.immutable());
        // Polymer can request a view while serializing a chunk off the server thread.
        level.getServer().execute(()->PENDING.put(key,LOAD_RETRY_TICKS));
    }
    static ItemStack model(String path) {
        var stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("riichi_mahjong", path));
        return stack;
    }
    static String tileModel(int code) { return tileModel(code, false); }
    static String tileModel(int code, boolean sichuan) { return tileModel(code, sichuan, false); }
    static String tileModel(int code, boolean sichuan, boolean dora) {
        if (code < 0) return "bridge/tile_back";
        if (code == 37 || sichuan && code == 33) return "bridge/tile_joker";
        String name = code == 33 ? "tile_dragon_red" : BuiltInRegistries.ITEM.getKey(MahjongTileItems.itemForCode(code)).getPath();
        return (dora && !sichuan ? "bridge/dora/" : "bridge/") + name;
    }
    static ItemStack tileStack(int code, boolean sichuan, boolean dora) {
        var stack = model(tileModel(code, sichuan, dora));
        if (sichuan && code == 33) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }
    static Direction outward(int seat,BlockState state){
        Direction facing=state.hasProperty(BaseMultipartBlock.FACING)?state.getValue(BaseMultipartBlock.FACING):Direction.NORTH;
        for(var direction:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST))if(MahjongTableBlock.seatForDirection(direction,facing)==seat)return direction;
        return Direction.NORTH;
    }
    static Quaternionf rotation(Direction outward,boolean flat,boolean down){
        float yaw=switch(outward){case NORTH->0;case EAST->-90;case WEST->90;default->180;};var q=new Quaternionf();
        if(flat){q.rotateX((float)Math.PI/2).rotateLocalY((float)Math.toRadians(yaw));if(down)q.rotateLocalX((float)Math.PI);}else q.rotateY((float)Math.toRadians(yaw));return q;
    }
    static Vec3 anchor(BlockPos pos,Direction outward,double along,double height,double distance){var line=outward.getCounterClockWise();return new Vec3(pos.getX()+.5+line.getStepX()*along+outward.getStepX()*distance,pos.getY()+height,pos.getZ()+.5+line.getStepZ()*along+outward.getStepZ()*distance);}
    private static ItemDisplayElement tileDisplay(ItemStack stack, Vec3 pos, float scale, Quaternionf rotation) {
        var d = new ItemDisplayElement(stack);
        d.setItemDisplayContext(ItemDisplayContext.NONE);
        d.setOverridePos(pos);
        d.setScale(new Vector3f(scale));
        d.setLeftRotation(rotation);
        d.setRightRotation(new Quaternionf().rotationY((float)Math.PI));
        d.setDisplaySize(3,3);
        return d;
    }
    static ItemDisplayElement displayForViewer(ElementHolder holder, ItemStack stack, Vec3 pos, float scale,
                                               Quaternionf rotation, ServerPlayer viewer) {
        var d = tileDisplay(stack, pos, scale, rotation);
        d.setVisibilityPredicate(player -> player == viewer);
        holder.addElement(d);
        return d;
    }
    private static ItemDisplayElement display(ElementHolder holder,String path,Vec3 pos,float scale,Quaternionf rotation){
        return display(holder,model(path),pos,scale,rotation,null,Set.of());
    }
    private static ItemDisplayElement display(ElementHolder holder,ItemStack stack,Vec3 pos,float scale,Quaternionf rotation,UUID hideFrom,Set<UUID> hiddenSpectators){
        var d=tileDisplay(stack,pos,scale,rotation);
        // addElement immediately spawns for existing watchers. Set the audience first.
        if(hideFrom!=null||!hiddenSpectators.isEmpty())d.setVisibilityPredicate(player->!player.getUUID().equals(hideFrom)&&!hiddenSpectators.contains(player.getUUID()));
        holder.addElement(d);return d;
    }
    public static boolean viewingNear(ServerPlayer p,ServerLevel level,BlockPos pos){return p.level()==level&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    static boolean near(ServerPlayer p,ServerLevel level,BlockPos pos){return !p.isSpectator()&&viewingNear(p,level,pos);}
    static boolean clearButtonRay(ServerPlayer player,MahjongTableBlockEntity table,Vec3 start,Vec3 hit){
        double distance=start.distanceToSqr(hit);
        var block=player.level().clip(new ClipContext(start,hit,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS&&start.distanceToSqr(block.getLocation())<distance-1e-8)return false;
        var view=VIEWS.get(new Key(player.level(),table.getBlockPos()));if(view==null)return false;
        for(var visual:view.sent.values()){
            var tileHit=TableHitTest.tile(start,hit,visual.position(),visual.scale(),visual.rotation());
            if(tileHit.isPresent()&&start.distanceToSqr(tileHit.get())<distance-1e-8)return false;
        }
        return true;
    }
    public static boolean handleTileHit(ServerPlayer player,MahjongTableBlockEntity table){
        // A centre gesture takes precedence over stale hand/button entities and click suppression.
        if(MahjongTableBlock.routeWindPlateUse(player,table))return true;
        if(table.recentPrivateClick(player))return true;
        var view=VIEWS.get(new Key(player.level(),table.getBlockPos()));
        if(view==null)return false;
        Vec3 start=player.getEyePosition(),end=tileRayEnd(player,start);
        var privateHand=view.hands.get(player.getUUID());
        if(view.replayView!=null&&view.replayView.owner==player&&view.replayView.handleButtonHit(start,end))return true;
        if(table.replay()!=null)return true;
        if(privateHand!=null&&privateHand.handleButtonHit(start,end))return true;
        var pos=table.getBlockPos();
        var surface=TableLayout.surface(pos);
        end=surface.clip(start,end).orElse(end);
        String chosen=null;double nearest=Double.MAX_VALUE;
        for(var entry:view.sent.entrySet()){
            var visual=entry.getValue();var hit=TableHitTest.tile(start,end,visual.position(),visual.scale(),visual.rotation());
            if(hit.isPresent()){double distance=start.distanceToSqr(hit.get());if(distance<nearest){nearest=distance;chosen=entry.getKey();}}
        }
        if(privateHand!=null&&privateHand.handleEmptySlotHit(start,end,nearest))return true;
        if(chosen==null)return false;
        table.recordPrivateClick(player);
        if(chosen.startsWith("hand/")){
            var parts=chosen.split("/");int seat=Integer.parseInt(parts[1]),index=Integer.parseInt(parts[2]);
            var hand=view.hands.get(player.getUUID());
            if(table.seatOfPlayer(player.getUUID()).orElse(-1)==seat&&hand!=null&&hand.valid(player))
                table.onCuteClick(player,new InteractKey.SeatSlot((byte)seat,InteractKey.SeatSlot.AREA_HAND,(short)index));
        }
        return true;
    }
    private static Vec3 tileRayEnd(ServerPlayer player,Vec3 start){
        var end=start.add(player.getLookAngle().scale(8));
        var hit=player.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        return hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS?end:hit.getLocation();
    }
    private static final class View {
        final ServerLevel level;final BlockPos pos;final BlockState state;final ElementHolder holder=new ElementHolder();
        final Map<String,ItemDisplayElement> tiles=new HashMap<>();final Set<String> used=new HashSet<>();
        private record Visual(int code,Vec3 position,float scale,Quaternionf rotation,UUID hiddenFrom,Set<UUID> hiddenSpectators,boolean dora){}
        Set<Integer> doraTiles=Set.of();
        boolean sichuanJoker;
        final Map<String,Visual> sent=new HashMap<>();
        final Map<UUID,PrivateHand> hands=new HashMap<>();
        ReplayView replayView;
        final Map<UUID,SpectatorHands> spectatorHands=new HashMap<>();
        Set<UUID> spectatorHandAudience=Set.of();
        MahjongTableBlockEntity spectatorTable;
        final TextDisplayElement label=new TextDisplayElement();
        final Map<String,List<InteractionElement>> hits=new HashMap<>();PublicTable previous;
        final Map<Integer,TextDisplayElement> seatLabels=new HashMap<>();
        final Map<Integer,InteractionElement> seatHits=new HashMap<>();
        final Map<Integer,TableButtonLayout.Button> seatButtons=new HashMap<>();
        final TableHud hud;
        final BotAvatars bots;
        final WinEffectRenderer winEffects;
        final WinResultLabels winResultLabels;
        final ExchangeRenderer exchangeRenderer;
        Set<String> consumedWinTiles=Set.of();
        final TableCollision.Registration collision;
        View(ServerLevel level,LevelChunk chunk,BlockPos pos,BlockState state){
            this.level=level;this.pos=pos;this.state=state;
            winEffects=new WinEffectRenderer(holder,level,pos,state);
            winResultLabels=new WinResultLabels(holder,pos,state);
            exchangeRenderer=new ExchangeRenderer(holder,level,pos,state);String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if(!id.equals("liquid_experience_block")){
                var base=display(holder,"bridge/"+id,Vec3.atCenterOf(pos),1,blockRotation(state));
                if(state.getBlock() instanceof MahjongTableBlock)base.setScale(new Vector3f(TableLayout.TABLE_HORIZONTAL_SCALE,1,TableLayout.TABLE_HORIZONTAL_SCALE));
            }
            collision=state.getBlock() instanceof BaseMultipartBlock?TableCollision.add(holder,level,pos,state):null;
            hud=state.getBlock() instanceof MahjongTableBlock?new TableHud():null;
            bots=state.getBlock() instanceof MahjongTableBlock?new BotAvatars(holder,level,pos,state):null;
            label.setOverridePos(Vec3.atCenterOf(pos).add(0,1.5,0));label.setScale(new Vector3f(.2f));label.setBillboardMode(Display.BillboardConstraints.CENTER);label.setLineWidth(500);label.setDisplaySize(4,4);if(hud==null)holder.addElement(label);
            new ChunkAttachment(holder,chunk,Vec3.atCenterOf(pos),false);update();
        }
        boolean attachedTo(LevelChunk chunk){
            return holder.getAttachment() instanceof ChunkAttachment attachment&&!attachment.isRemoved()&&attachment.getChunk()==chunk;
        }
        Quaternionf blockRotation(BlockState state){
            if(state.hasProperty(BlockStateProperties.FACING)){var d=state.getValue(BlockStateProperties.FACING);if(d==Direction.UP)return new Quaternionf().rotateX((float)Math.PI/2);if(d==Direction.DOWN)return new Quaternionf().rotateX(-(float)Math.PI/2);return rotation(d,false,false);}
            return state.hasProperty(BaseMultipartBlock.FACING)?rotation(state.getValue(BaseMultipartBlock.FACING),false,false):new Quaternionf();
        }
        void tile(String slot,int code,Vec3 at,float scale,Quaternionf rotation,UUID hideFrom){
            if(consumedWinTiles.contains(slot)||winEffects.hides(slot))return;
            boolean dora=code>=0&&doraTiles.contains(code);
            Set<UUID> hiddenSpectators=code<0&&slot.startsWith("hand/")?spectatorHandAudience:Set.of();
            used.add(slot);var visual=new Visual(code,at,scale,rotation,hideFrom,hiddenSpectators,dora);var old=sent.put(slot,visual);if(visual.equals(old))return;
            var d=tiles.get(slot);
            if(d!=null&&(old==null||!Objects.equals(old.hiddenFrom(),hideFrom)||!old.hiddenSpectators().equals(hiddenSpectators))){
                // removeElement explicitly removes the old ID from every client,
                // including a newly seated owner who previously saw this public tile.
                holder.removeElement(d);d=null;
            }
            if(d==null){
                d=display(holder,tileStack(code,sichuanJoker,dora),at,scale,rotation,hideFrom,hiddenSpectators);
                tiles.put(slot,d);
            }else{
                if(old.code()!=code||old.dora()!=dora)d.setItem(tileStack(code,sichuanJoker,dora));
                d.setOverridePos(at);d.setScale(new Vector3f(scale));d.setLeftRotation(rotation);
            }
        }
        void tile(String key,int code,Direction dir,double along,double height,double distance,boolean flat,boolean down,boolean sideways,UUID hide){var q=rotation(dir,flat,down);if(sideways)q.rotateLocalY((float)Math.PI/2);tile(key,code,TableLayout.anchor(pos,dir,along,height,distance),TableLayout.TILE_SCALE,q,hide);}
        void cleanup(){for(var it=tiles.entrySet().iterator();it.hasNext();){var e=it.next();if(!used.contains(e.getKey())){holder.removeElement(e.getValue());sent.remove(e.getKey());it.remove();}}}
        void update(){
            // A table's chunk can stay loaded for another player or a chunk ticket.
            // ElementHolder.tick does not reconcile chunk watchers after a teleport.
            var attachment=holder.getAttachment();
            if(attachment!=null)attachment.updateCurrentlyTracking(List.copyOf(holder.getWatchingPlayers()));
            var be=level.getBlockEntity(pos);
            if(be instanceof MahjongTableBlockEntity table){
                spectatorTable=table;
                // Drop live observer sessions before composing either public or private audiences.
                for(var uuid:table.spectators()){
                    var player=level.getServer().getPlayerList().getPlayer(uuid);
                    if(player==null||!viewingNear(player,level,pos)||table.seatOfPlayer(uuid).isPresent())table.stopWatching(uuid);
                }
                var nextSpectators=new HashSet<UUID>();
                for(var uuid:table.spectators()){
                    var player=level.getServer().getPlayerList().getPlayer(uuid);
                    if(player!=null&&SpectatorHands.eligible(table,player))nextSpectators.add(uuid);
                }
                boolean spectatorAudienceChanged=!spectatorHandAudience.equals(nextSpectators);
                spectatorHandAudience=Set.copyOf(nextSpectators);
                var board=PublicTable.capture(table);var session=table.winEffectSession();
                boolean wasAnimating=winEffects.active();
                var source=session==null?null:sent.get(session.context().source().slot());
                winEffects.bind(session,board,source==null?null:new WinEffectRenderer.Tile(source.code(),source.position(),source.scale(),source.rotation()),
                        session!=null&&table.consumedWinTileSlots().contains(session.context().source().slot()));
                var consumed=table.consumedWinTileSlots();boolean consumedChanged=!consumedWinTiles.equals(consumed);consumedWinTiles=consumed;
                boolean wasExchanging=exchangeRenderer.active();
                exchangeRenderer.update(table.exchangePresentation(),table);
                if(session!=null||wasAnimating||consumedChanged||spectatorAudienceChanged||wasExchanging||exchangeRenderer.active()||!board.equals(previous)){
                    renderTable(board);previous=board;
                }
                winResultLabels.update(table,board);
                updateLobby(table,board);updatePrivate(table);updateSpectators(table,board);bots.update(table);hud.update(table,board);
            }
            else if(be instanceof MahjongSolitaireBlockEntity solitaire)renderSolitaire(solitaire);
            else if(be instanceof MahjongAltarBlockEntity altar)renderAltar(altar);holder.tick();
        }
        void updateLobby(MahjongTableBlockEntity table,PublicTable board){
            boolean active=table.hasActiveMatch();
            if(active){seatHits.values().forEach(holder::removeElement);seatHits.clear();seatButtons.clear();}
            for(int seat=0;seat<board.seats().size();seat++){
                final int index=seat;var info=table.seats().get(seat);var s=board.seats().get(seat);
                var dir=outward(seat,state);
                boolean turn=board.turnSeat()==seat;
                var text=Component.literal(TableSeating.wind(s.wind())+(active?(turn?"\n▶ 出牌":""):("家 · "+s.name()+(info.enabled()&&s.owner()==null?" · 右键入座":"")))).withStyle(turn?net.minecraft.ChatFormatting.GOLD:net.minecraft.ChatFormatting.WHITE);
                if(!active)text.withStyle(style->style.withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("uniform"))));
                var button=active?null:TableButtonLayout.arrange(List.of(text.getString())).getFirst();
                if(button!=null)seatButtons.put(seat,button);
                var name=seatLabels.get(seat);
                if(name==null){
                    name=new TextDisplayElement();
                    name.setLineWidth(250);name.setDisplaySize(2,2);holder.addElement(name);seatLabels.put(seat,name);
                }
                name.setOverridePos(active?TableLayout.anchor(pos,dir,0,.585,.16):button.textPosition(pos,dir));
                name.setScale(new Vector3f((active?.14f:button.scale())*TableLayout.TILE_ENLARGEMENT));name.setBackground(active?0x00000000:0x80333333);
                name.setBillboardMode(Display.BillboardConstraints.FIXED);
                name.setLeftRotation(TableLayout.textRotation(dir));
                if(!text.equals(name.getText()))name.setText(text);
                if(!active&&!seatHits.containsKey(seat)){
                    var hit=new InteractionElement(new VirtualElement.InteractionHandler(){
                        @Override public void interact(ServerPlayer p,InteractionHand hand,Vec3 loc,boolean sneak){
                            if(hand!=InteractionHand.MAIN_HAND||table.hasActiveMatch()||MahjongTableBlock.routeWindPlateUse(p,table))return;
                            var current=seatButtons.get(index);if(current==null)return;
                            Vec3 start=p.getEyePosition();
                            var aimed=current.hit(pos,dir,start,start.add(p.getLookAngle().scale(8)));
                            if(aimed.isPresent()&&clearButtonRay(p,table,start,aimed.get()))TableSeating.join(p,table,index);
                        }
                    });
                    hit.setOverridePos(TableLayout.anchor(pos,dir,0,TableButtonLayout.HEIGHT-.003,TableButtonLayout.EDGE_DISTANCE));
                    hit.setSize((float)button.width()*TableLayout.TILE_ENLARGEMENT,.006f*TableLayout.TILE_ENLARGEMENT);
                    hit.setResponse(true);holder.addElement(hit);seatHits.put(seat,hit);
                }
                if(!active){
                    var hit=seatHits.get(seat);
                    hit.setOverridePos(TableLayout.anchor(pos,dir,0,TableButtonLayout.HEIGHT-.003,TableButtonLayout.EDGE_DISTANCE));
                    hit.setSize((float)button.width()*TableLayout.TILE_ENLARGEMENT,.006f*TableLayout.TILE_ENLARGEMENT);
                }
            }
        }
        void renderSichuanWall(PublicTable board){
            int[] full=board.totalTiles()==112?new int[]{28,28,28,28}:new int[]{28,26,28,26};
            int[] remains=full.clone();
            int dealt=board.totalTiles()-board.liveWall();
            if(board.stage()==MatchPhase.Stage.WALL_BUILDING)dealt=0;
            else if(board.stage()==MatchPhase.Stage.DEALING_HANDS)dealt=(int)Math.round(52*board.progress());
            else if(board.stage()==MatchPhase.Stage.DORA_FLIP)dealt=52;
            for(int seat:new int[]{0,3,2,1}){
                int taken=Math.min(Math.max(0,dealt),remains[seat]);
                remains[seat]-=taken;dealt-=taken;
            }
            int built=board.stage()==MatchPhase.Stage.WALL_BUILDING?(int)Math.floor(board.totalTiles()/2*board.progress()):board.totalTiles()/2;
            int before=0;
            for(int seat=0;seat<4;seat++){
                int count=board.stage()==MatchPhase.Stage.WALL_BUILDING?Math.min(full[seat]/2,Math.max(0,built-before))*2:remains[seat];
                before+=full[seat]/2;
                Direction dir=outward(seat,state);
                for(int i=0;i<count;i++)tile("wall/"+seat+"/"+i,-1,dir,-.59+(i/2)*.073,.60+(i%2)*.055,.70,true,true,false,null);
            }
        }
        void renderTable(PublicTable board){
            if(sichuanJoker!=board.sichuan())sent.clear();
            sichuanJoker=board.sichuan();
            doraTiles=board.doraTiles();
            used.clear();boolean active=board.seats().stream().anyMatch(s->s.handCount()>0);
            if(active&&board.sichuan())renderSichuanWall(board);
            if(active&&!board.sichuan()){
                var wall=RiichiWallLayout.capture(board.totalTiles(),board.liveWall(),board.openingWallDraws(),board.stage(),board.progress());
                for(int seat=0;seat<4;seat++){
                    Direction dir=outward(seat,state);int count=wall.sides().get(seat);
                    for(int i=0;i<count;i++)tile("wall/"+seat+"/"+i,-1,dir,-.59+(i/2)*.073,.60+(i%2)*.055,.70,true,true,false,null);
                }
                Direction dir=outward(0,state);int deadCount=wall.deadWall();
                for(int i=0;i<deadCount;i++){
                    int stack=i/2,layer=i%2,drawIndex=stack==6?(layer==1?1:2):stack==5?(layer==1?3:4):-1;
                    if(drawIndex>0&&board.rinshanDrawn()>=drawIndex)continue;
                    int k=4-stack;boolean dora=layer==1&&k>=0&&k<board.dora().size()&&board.stage()!=com.themahjong.driver.MatchPhase.Stage.WALL_BUILDING;
                    boolean ura=layer==0&&k>=0&&k<board.ura().size();double z=.70+(dora||ura?-.0125:0)+(ura?-.083:0);
                    tile("dead/"+i,dora?board.dora().get(k):ura?board.ura().get(k):-1,dir,.59-stack*.073,.60+layer*.055,z,true,!(dora||ura),false,null);
                }
                for(int extra=0;extra<board.rinshanDrawn();extra++)tile("deadextra/"+extra,-1,dir,.59+(1+extra/2)*.073,.60+(extra%2)*.055,.70,true,true,false,null);
            }
            for(int seat=0;seat<board.seats().size();seat++){
                var s=board.seats().get(seat);Direction dir=outward(seat,state);double fraction=board.stage()==com.themahjong.driver.MatchPhase.Stage.WALL_BUILDING?0:board.stage()==com.themahjong.driver.MatchPhase.Stage.DEALING_HANDS?board.progress():1;int count=(int)Math.round(s.handCount()*fraction);
                boolean revealed=!s.revealed().isEmpty();
                for(int i=0;i<count&&!exchangeRenderer.active();i++){
                    int code=revealed?s.revealed().get(i):-1;
                    if(winEffects.controls(seat)&&revealed){
                        if(i==count-1)winEffects.winningTile(seat,s,i,code,(key,tile,at,scale,q)->tile(key,tile,at,scale,q,null));
                        else tile("hand/"+seat+"/"+i,code,winEffects.handPosition(seat,s,i,winEffects.reveal(seat)),
                                TableLayout.TILE_SCALE,winEffects.handRotation(seat,winEffects.reveal(seat)),null);
                    }else tile("hand/"+seat+"/"+i,code,
                            TableLayout.handAnchor(pos,dir,TableLayout.handAlong(s.handSlots().get(i),s.handGapStarts()),revealed),
                            TableLayout.TILE_SCALE,rotation(dir,revealed,false),revealed?null:s.owner());
                }
                for(int i=0;i<s.river().size();i++){
                    var t=s.river().get(i);
                    tile("river/"+seat+"/"+i,t.code(),RiverDisplay.position(pos,dir,i),
                            TableLayout.TILE_SCALE,RiverDisplay.rotation(dir,t.sideways()),null);
                }
                double cursor=0;for(int m=0;m<s.melds().size();m++){
                    var meld=s.melds().get(m);double width=meld.tiles().stream().filter(t->!t.stacked()).mapToDouble(t->t.sideways()?.090:.073).sum();
                    double offset=0,last=0;
                    for(int i=0;i<meld.tiles().size();i++){
                        var t=meld.tiles().get(i);double step=t.sideways()?.090:.073;
                        double x=t.stacked()?last:.90-cursor-width+offset+step/2;
                        tile("meld/"+seat+"/"+m+"/"+i,t.code(),dir,x,.59+(t.stacked()?.058:0),.89,true,t.faceDown(),t.sideways(),null);
                        if(!t.stacked()){last=x;offset+=step;}
                    }cursor+=width+.020;
                }
                for(int i=0;i<s.kita();i++)tile("kita/"+seat+"/"+i,30,dir,-.53+i*.073,.59,.57,true,false,false,null);
                if(s.riichiStick()){
                    String key="riichi/"+seat+"/"+s.riichiSkin().name();used.add(key);var stick=tiles.get(key);
                    // The centre wind plate is 4/16 wide and its top is at 9.5/16.
                    if(stick==null){stick=display(holder,s.riichiSkin().model(),TableLayout.anchor(pos,dir,0,.596,.09),.22f*TableLayout.TILE_ENLARGEMENT,rotation(dir,false,false));tiles.put(key,stick);}
                }
            }
            // Deposits carried from a drawn hand stay visible in the centre.
            int carried=Math.max(0,board.riichiSticks()-(int)board.seats().stream().filter(PublicTable.Seat::riichiStick).count());
            for(int i=0;i<carried;i++){
                String key="carried-riichi/"+i;used.add(key);if(!tiles.containsKey(key))tiles.put(key,display(holder,"bridge/table_riichi_stick",TableLayout.anchor(pos,Direction.NORTH,0,.610+(i/4)*.012,(i%4-1.5)*.038),.22f*TableLayout.TILE_ENLARGEMENT,new Quaternionf()));
            }
            winEffects.render(board,(key,code,at,scale,q)->tile(key,code,at,scale,q,null));
            cleanup();
        }
        void updatePrivate(MahjongTableBlockEntity table){
            if(table.replay()!=null){
                hands.values().forEach(PrivateHand::destroy);hands.clear();
                var player=table.owner().map(level.getServer().getPlayerList()::getPlayer).orElse(null);
                if(player!=null&&near(player,level,pos)){
                    if(replayView==null||replayView.owner!=player||!replayView.sameState()){
                        if(replayView!=null)replayView.destroy();replayView=new ReplayView(table,player);
                    }
                    replayView.tick();
                }else if(replayView!=null){replayView.destroy();replayView=null;}
                return;
            }
            if(replayView!=null){replayView.destroy();replayView=null;}
            if(table.exchangePresentation()!=null){hands.values().forEach(PrivateHand::destroy);hands.clear();return;}
            Set<UUID> keep=new HashSet<>();var driver=table.driver();var round=driver==null?null:driver.match().currentRound().orElse(null);
            if(round!=null)for(int seat=0;seat<table.seats().size();seat++){
                UUID uuid=table.seats().get(seat).occupant().orElse(null);if(uuid==null||seat>=round.players().size())continue;ServerPlayer player=level.getServer().getPlayerList().getPlayer(uuid);if(player==null||!near(player,level,pos))continue;
                var old=hands.get(uuid);var snapshot=PrivateHand.snapshot(table,seat);keep.add(uuid);
                if(old==null||old.owner!=player||!old.sameVisuals(snapshot)){if(old!=null)old.destroy();hands.put(uuid,new PrivateHand(table,player,seat,snapshot));}else old.snapshot=snapshot;hands.get(uuid).tick();
            }
            for(var it=hands.entrySet().iterator();it.hasNext();){var e=it.next();if(!keep.contains(e.getKey())){e.getValue().destroy();it.remove();}}
        }
        void updateSpectators(MahjongTableBlockEntity table,PublicTable board){
            if(table.exchangePresentation()!=null){spectatorHands.values().forEach(SpectatorHands::destroy);spectatorHands.clear();return;}
            Set<UUID> keep=new HashSet<>();
            if(!spectatorHandAudience.isEmpty()){
                var snapshot=SpectatorHands.capture(table,board);
                for(var uuid:spectatorHandAudience){
                    var player=level.getServer().getPlayerList().getPlayer(uuid);
                    if(player==null||!SpectatorHands.eligible(table,player))continue;
                    keep.add(uuid);var old=spectatorHands.get(uuid);
                    if(old==null||old.owner!=player||!old.snapshot.equals(snapshot)){
                        if(old!=null)old.destroy();spectatorHands.put(uuid,new SpectatorHands(table,player,snapshot));
                    }
                    spectatorHands.get(uuid).tick();
                }
            }
            for(var it=spectatorHands.entrySet().iterator();it.hasNext();){var entry=it.next();
                if(!keep.contains(entry.getKey())){entry.getValue().destroy();it.remove();}
            }
        }
        void renderSolitaire(MahjongSolitaireBlockEntity be){
            var board=be.board();if(board==null)return;used.clear();Set<String> liveHits=new HashSet<>();float scale=(float)((2.0/17)/(11.0/16));double gx=1.0/17,gz=(14.0/16)*scale/2,layerHeight=(9.0/16)*scale;
            for(int i=0;i<be.slotCount();i++)if(be.tileAt(i)!=null){var slot=board.slots().get(i);double x=.5-board.width()*gx/2+(slot.x()-board.minX()+1)*gx;double z=.5-board.depth()*gz/2+(slot.z()-board.minZ()+1)*gz+.0625*scale;double y=.0625+slot.y()*layerHeight+layerHeight/2;var at=new Vec3(pos.getX()+x,pos.getY()+y,pos.getZ()+z);String key="solitaire/"+i;
                tile(key,MahjongTileItems.codeForTile(be.tileAt(i)),at,scale,rotation(Direction.SOUTH,true,false),null);
                if(be.isFree(i)){
                    liveHits.add(key);
                    var cells=TileInteractionLayout.cells(at,scale,rotation(Direction.SOUTH,true,false));
                    var previousHits=hits.get(key);
                    if(previousHits!=null&&previousHits.size()!=cells.size()){
                        previousHits.forEach(holder::removeElement);hits.remove(key);
                    }
                    if(!hits.containsKey(key)){
                        final int index=i;var elements=new ArrayList<InteractionElement>();
                        for(var cell:cells){
                            var hit=new InteractionElement(new VirtualElement.InteractionHandler(){@Override public void interact(ServerPlayer p,InteractionHand hand,Vec3 loc,boolean sneak){
                                if(hand==InteractionHand.MAIN_HAND&&near(p,level,pos)&&be.isFree(index)&&solitaireTarget(p).equals("solitaire/"+index))be.onCuteClick(p,new InteractKey.Slot(index));
                            }});
                            cell.apply(hit);hit.setResponse(true);holder.addElement(hit);elements.add(hit);
                        }
                        hits.put(key,List.copyOf(elements));
                    }else{
                        for(int cell=0;cell<cells.size();cell++)cells.get(cell).apply(hits.get(key).get(cell));
                    }
                }
            }
            for(var it=hits.entrySet().iterator();it.hasNext();){var e=it.next();if(!liveHits.contains(e.getKey())){e.getValue().forEach(holder::removeElement);it.remove();}}label.setText(Component.literal(board.name()+" · 剩余 "+be.remainingTiles()+"张\n右键单张牌配对 · 右键底座获取提示"));cleanup();
        }
        String solitaireTarget(ServerPlayer player){
            var start=player.getEyePosition();var end=tileRayEnd(player,start);
            String chosen="";double nearest=Double.MAX_VALUE;
            for(var entry:sent.entrySet())if(entry.getKey().startsWith("solitaire/")){
                var visual=entry.getValue();var hit=TableHitTest.tile(start,end,visual.position(),visual.scale(),visual.rotation());
                if(hit.isPresent()&&start.distanceToSqr(hit.get())<nearest){nearest=start.distanceToSqr(hit.get());chosen=entry.getKey();}
            }
            return chosen;
        }
        void renderAltar(MahjongAltarBlockEntity altar){used.clear();for(int i=0;i<altar.getContainerSize();i++){var stack=altar.getItem(i);if(stack.isEmpty())continue;String key="altar/"+i;used.add(key);var d=tiles.get(key);var at=new Vec3(pos.getX()+.25+(i%3)*.25,pos.getY()+.85,pos.getZ()+.25+(i/3)*.25);if(d==null){d=new ItemDisplayElement(stack);d.setOverridePos(at);d.setScale(new Vector3f(.25f));holder.addElement(d);tiles.put(key,d);}else d.setItem(stack);}cleanup();}
        void destroy(){
            if(replayView!=null)replayView.destroy();
            winEffects.destroy();hands.values().forEach(PrivateHand::destroy);hands.clear();
            winResultLabels.destroy();exchangeRenderer.destroy();
            spectatorHands.values().forEach(SpectatorHands::destroy);spectatorHands.clear();spectatorHandAudience=Set.of();
            if(spectatorTable!=null)for(var uuid:spectatorTable.spectators())spectatorTable.stopWatching(uuid);
            spectatorTable=null;
            if(hud!=null)hud.destroy();if(bots!=null)bots.destroy();if(collision!=null)collision.destroy();holder.destroy();
        }
    }
}
