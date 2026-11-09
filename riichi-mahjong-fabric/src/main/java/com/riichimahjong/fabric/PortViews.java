package com.riichimahjong.fabric;

import java.util.*;
import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.mahjongtable.*;
import com.riichimahjong.mahjongsolitaire.*;
import com.riichimahjong.mahjongaltar.MahjongAltarBlockEntity;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.cuterenderer.InteractKey;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Vanilla display adapters using the pinned upstream geometry, textures and layout. */
public final class PortViews {
    private record Key(ServerLevel level,BlockPos pos){}
    private static final Map<Key,View> VIEWS=new HashMap<>();
    private static final Set<Key> PENDING=new HashSet<>();
    private static long ticks;
    public static void register(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(++ticks%2!=0)return;
            // Chunk-load callbacks may run before C2ME finishes making the chunk accessible.
            // Never synchronously acquire a chunk from inside that callback.
            var pending=List.copyOf(PENDING);PENDING.clear();
            for(var key:pending){
                var chunk=key.level.getChunkSource().getChunkNow(key.pos.getX()>>4,key.pos.getZ()>>4);
                if(chunk==null)continue;
                var state=chunk.getBlockState(key.pos);
                if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("riichi_mahjong"))continue;
                if(state.hasProperty(BaseMultipartBlock.PART_X)&&!BaseMultipartBlock.isMaster(state))continue;
                if(!VIEWS.containsKey(key))VIEWS.put(key,new View(key.level,key.pos,state));
            }
            for(var it=VIEWS.entrySet().iterator();it.hasNext();){var entry=it.next();var key=entry.getKey();var view=entry.getValue();
                var chunk=key.level.getChunkSource().getChunkNow(key.pos.getX()>>4,key.pos.getZ()>>4);
                if(chunk==null||chunk.getBlockState(key.pos)!=view.state){view.destroy();it.remove();continue;}
                view.update();
            }
        });
        ServerChunkEvents.CHUNK_LOAD.register((level,chunk,generated)->{for(var be:chunk.getBlockEntities().values())if(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getNamespace().equals("riichi_mahjong"))ensure(level,be.getBlockPos());});
        ServerLifecycleEvents.SERVER_STARTED.register(RecordVault::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{VIEWS.values().forEach(View::destroy);VIEWS.clear();PENDING.clear();});
        ServerEntityEvents.ENTITY_LOAD.register((entity,level)->{if(entity.entityTags().contains("timory_mahjong_display"))entity.discard();});
    }
    public static void ensure(ServerPlayer player,BlockPos pos){ensure(player.level(),pos);}
    private static void ensure(ServerLevel level,BlockPos pos){
        PENDING.add(new Key(level,pos.immutable()));
    }
    static ItemStack model(String path){var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.ITEM_MODEL,Identifier.fromNamespaceAndPath("riichi_mahjong",path));return stack;}
    static String tileModel(int code){return code<0?"bridge/tile_back":"bridge/"+BuiltInRegistries.ITEM.getKey(MahjongTileItems.itemForCode(code)).getPath();}
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
    static ItemDisplayElement display(ElementHolder holder,String model,Vec3 pos,float scale,Quaternionf rotation){
        var d=new ItemDisplayElement(model(model));d.setItemDisplayContext(ItemDisplayContext.NONE);d.setOverridePos(pos);d.setScale(new Vector3f(scale));d.setLeftRotation(rotation);d.setRightRotation(new Quaternionf().rotationY((float)Math.PI));d.setDisplaySize(3,3);holder.addElement(d);return d;
    }
    static boolean near(ServerPlayer p,ServerLevel level,BlockPos pos){return !p.isSpectator()&&p.level()==level&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    public static boolean handleTileHit(ServerPlayer player,MahjongTableBlockEntity table){
        if(table.recentPrivateClick(player))return true;
        var view=VIEWS.get(new Key(player.level(),table.getBlockPos()));
        if(view==null)return false;
        Vec3 start=player.getEyePosition(),end=start.add(player.getLookAngle().scale(8));
        var pos=table.getBlockPos();
        var surface=new AABB(pos.getX()-.5,pos.getY()+TableLayout.TABLE_TOP-2,pos.getZ()-.5,pos.getX()+1.5,pos.getY()+TableLayout.TABLE_TOP,pos.getZ()+1.5);
        end=surface.clip(start,end).orElse(end);
        String chosen=null;double nearest=Double.MAX_VALUE;
        for(var entry:view.sent.entrySet()){
            var visual=entry.getValue();var hit=TableHitTest.tile(start,end,visual.position(),visual.scale(),visual.rotation());
            if(hit.isPresent()){double distance=start.distanceToSqr(hit.get());if(distance<nearest){nearest=distance;chosen=entry.getKey();}}
        }
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
    private static final class View {
        final ServerLevel level;final BlockPos pos;final BlockState state;final ElementHolder holder=new ElementHolder();
        final Map<String,ItemDisplayElement> tiles=new HashMap<>();final Set<String> used=new HashSet<>();
        private record Visual(int code,Vec3 position,float scale,Quaternionf rotation,UUID hiddenFrom){}
        final Map<String,Visual> sent=new HashMap<>();
        final Map<UUID,PrivateHand> hands=new HashMap<>();final TextDisplayElement label=new TextDisplayElement();
        final Map<String,InteractionElement> hits=new HashMap<>();PublicTable previous;
        final Map<Integer,TextDisplayElement> seatLabels=new HashMap<>();
        final Map<Integer,InteractionElement> seatHits=new HashMap<>();
        final TableHud hud;
        final TableCollision.Registration collision;
        View(ServerLevel level,BlockPos pos,BlockState state){
            this.level=level;this.pos=pos;this.state=state;String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if(!id.equals("liquid_experience_block")){
                var base=display(holder,"bridge/"+id,Vec3.atCenterOf(pos),1,blockRotation(state));
                if(state.getBlock() instanceof MahjongTableBlock)base.setScale(new Vector3f(TableLayout.TABLE_HORIZONTAL_SCALE,1,TableLayout.TABLE_HORIZONTAL_SCALE));
            }
            collision=state.getBlock() instanceof BaseMultipartBlock?TableCollision.add(holder,level,pos,state):null;
            hud=state.getBlock() instanceof MahjongTableBlock?new TableHud():null;
            label.setOverridePos(Vec3.atCenterOf(pos).add(0,1.5,0));label.setScale(new Vector3f(.2f));label.setBillboardMode(Display.BillboardConstraints.CENTER);label.setLineWidth(500);label.setDisplaySize(4,4);if(hud==null)holder.addElement(label);
            ChunkAttachment.of(holder,level,pos);update();
        }
        Quaternionf blockRotation(BlockState state){
            if(state.hasProperty(BlockStateProperties.FACING)){var d=state.getValue(BlockStateProperties.FACING);if(d==Direction.UP)return new Quaternionf().rotateX((float)Math.PI/2);if(d==Direction.DOWN)return new Quaternionf().rotateX(-(float)Math.PI/2);return rotation(d,false,false);}
            return state.hasProperty(BaseMultipartBlock.FACING)?rotation(state.getValue(BaseMultipartBlock.FACING),false,false):new Quaternionf();
        }
        void tile(String slot,int code,Vec3 at,float scale,Quaternionf rotation,UUID hideFrom){
            used.add(slot);var visual=new Visual(code,at,scale,rotation,hideFrom);var old=sent.put(slot,visual);if(visual.equals(old))return;
            var d=tiles.get(slot);if(d==null){d=display(holder,tileModel(code),at,scale,rotation);tiles.put(slot,d);}else{if(old==null||old.code()!=code)d.setItem(model(tileModel(code)));d.setOverridePos(at);d.setScale(new Vector3f(scale));d.setLeftRotation(rotation);}
            d.setVisibilityPredicate(player->hideFrom==null||!player.getUUID().equals(hideFrom));
        }
        void tile(String key,int code,Direction dir,double along,double height,double distance,boolean flat,boolean down,boolean sideways,UUID hide){var q=rotation(dir,flat,down);if(sideways)q.rotateLocalY((float)Math.PI/2);tile(key,code,TableLayout.anchor(pos,dir,along,height,distance),TableLayout.TILE_SCALE,q,hide);}
        void cleanup(){for(var it=tiles.entrySet().iterator();it.hasNext();){var e=it.next();if(!used.contains(e.getKey())){holder.removeElement(e.getValue());sent.remove(e.getKey());it.remove();}}}
        void update(){
            var be=level.getBlockEntity(pos);
            if(be instanceof MahjongTableBlockEntity table){var board=PublicTable.capture(table);if(!board.equals(previous)){renderTable(board);previous=board;}updateLobby(table,board);updatePrivate(table);hud.update(table,board);}
            else if(be instanceof MahjongSolitaireBlockEntity solitaire)renderSolitaire(solitaire);
            else if(be instanceof MahjongAltarBlockEntity altar)renderAltar(altar);holder.tick();
        }
        void updateLobby(MahjongTableBlockEntity table,PublicTable board){
            boolean active=table.hasActiveMatch();
            if(active){seatHits.values().forEach(holder::removeElement);seatHits.clear();}
            for(int seat=0;seat<board.seats().size();seat++){
                final int index=seat;var info=table.seats().get(seat);var s=board.seats().get(seat);
                var dir=outward(seat,state);
                boolean turn=board.turnSeat()==seat;
                var text=Component.literal(TableSeating.wind(s.wind())+(active?(turn?"\n▶ 出牌":""):("家 · "+s.name()+(info.enabled()&&s.owner()==null?"\n右键入座":"")))).withStyle(turn?net.minecraft.ChatFormatting.GOLD:net.minecraft.ChatFormatting.WHITE);
                var name=seatLabels.get(seat);
                if(name==null){
                    name=new TextDisplayElement();
                    name.setLineWidth(250);name.setDisplaySize(2,2);holder.addElement(name);seatLabels.put(seat,name);
                }
                name.setOverridePos(active?TableLayout.anchor(pos,dir,0,.585,.16):anchor(pos,dir,0,1.0,1.25));
                name.setScale(new Vector3f(active?.14f:.2f));name.setBackground(active?0x00000000:0x80333333);
                name.setBillboardMode(active?Display.BillboardConstraints.FIXED:Display.BillboardConstraints.CENTER);
                name.setLeftRotation(active?TableLayout.textRotation(dir):new Quaternionf());
                if(!text.equals(name.getText()))name.setText(text);
                if(!active&&!seatHits.containsKey(seat)){
                    var hit=new InteractionElement(new VirtualElement.InteractionHandler(){
                        @Override public void interact(ServerPlayer p,InteractionHand hand,Vec3 loc,boolean sneak){
                            if(hand==InteractionHand.MAIN_HAND){if(sneak&&table.seatOfPlayer(p.getUUID()).isPresent())TableSeating.leave(p,table);else TableSeating.join(p,table,index);}
                        }
                    });
                    hit.setOverridePos(anchor(pos,dir,0,.4,1.25));hit.setSize(.72f,.85f);
                    holder.addElement(hit);seatHits.put(seat,hit);
                }
            }
        }
        void renderTable(PublicTable board){
            used.clear();boolean active=board.seats().stream().anyMatch(s->s.handCount()>0);
            if(active){
                int dealt=122-board.liveWall();
                if(board.stage()==com.themahjong.driver.MatchPhase.Stage.WALL_BUILDING)dealt=0;
                else if(board.stage()==com.themahjong.driver.MatchPhase.Stage.DEALING_HANDS)dealt=(int)Math.round(52*board.progress());
                else if(board.stage()==com.themahjong.driver.MatchPhase.Stage.DORA_FLIP)dealt=52;
                int[] remains={20,34,34,34};for(int seat:new int[]{0,3,2,1}){int taken=Math.min(Math.max(0,dealt),remains[seat]);remains[seat]-=taken;dealt-=taken;}
                int built=board.stage()==com.themahjong.driver.MatchPhase.Stage.WALL_BUILDING?(int)Math.floor(68*board.progress()):68;
                for(int seat=0;seat<4;seat++){
                    Direction dir=outward(seat,state);int count=board.stage()==com.themahjong.driver.MatchPhase.Stage.WALL_BUILDING?Math.min(seat==0?10:17,Math.max(0,built-seat*17))*2:remains[seat];
                    for(int i=0;i<count;i++)tile("wall/"+seat+"/"+i,-1,dir,-.59+(i/2)*.073,.60+(i%2)*.055,.70,true,true,false,null);
                }
                Direction dir=outward(0,state);int deadCount=board.stage()==com.themahjong.driver.MatchPhase.Stage.WALL_BUILDING?Math.min(7,Math.max(0,built-10))*2:14;
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
                for(int i=0;i<count;i++)tile("hand/"+seat+"/"+i,s.revealed().isEmpty()?-1:s.revealed().get(i),dir,-.44+i*.073,.65,.85,!s.revealed().isEmpty(),false,false,s.revealed().isEmpty()?s.owner():null);
                for(int i=0;i<s.river().size();i++){var t=s.river().get(i);tile("river/"+seat+"/"+i,t.code(),dir,(i%6-2.5)*.073,.59,.26+(i/6)*.09,true,false,t.sideways(),null);}
                double cursor=0;for(int m=0;m<s.melds().size();m++){
                    var meld=s.melds().get(m);double width=meld.tiles().stream().filter(t->!t.stacked()).mapToDouble(t->t.sideways()?.090:.073).sum();
                    double offset=0,last=0;
                    for(int i=0;i<meld.tiles().size();i++){
                        var t=meld.tiles().get(i);double step=t.sideways()?.090:.073;
                        double x=t.stacked()?last:.90-cursor-width+offset+step/2;
                        tile("meld/"+seat+"/"+m+"/"+i,t.code(),dir,x,.59+(t.stacked()?.058:0),.89,true,false,t.sideways(),null);
                        if(!t.stacked()){last=x;offset+=step;}
                    }cursor+=width+.020;
                }
                for(int i=0;i<s.kita();i++)tile("kita/"+seat+"/"+i,30,dir,-.53+i*.073,.59,.57,true,false,false,null);
                if(s.riichiStick()){
                    String key="riichi/"+seat;used.add(key);var stick=tiles.get(key);
                    if(stick==null){stick=display(holder,"bridge/table_riichi_stick",TableLayout.anchor(pos,dir,0,.585,.60),.5f,rotation(dir,false,false));tiles.put(key,stick);}
                }
            }
            // Deposits carried from a drawn hand stay visible in the centre.
            int carried=Math.max(0,board.riichiSticks()-(int)board.seats().stream().filter(PublicTable.Seat::riichiStick).count());
            for(int i=0;i<carried;i++){
                String key="carried-riichi/"+i;used.add(key);if(!tiles.containsKey(key))tiles.put(key,display(holder,"bridge/table_riichi_stick",anchor(pos,Direction.NORTH,0,.59+(i/4)*.025,(i%4-1.5)*.045),.3f,new Quaternionf()));
            }cleanup();
        }
        void updatePrivate(MahjongTableBlockEntity table){
            Set<UUID> keep=new HashSet<>();var driver=table.driver();var round=driver==null?null:driver.match().currentRound().orElse(null);
            if(round!=null)for(int seat=0;seat<table.seats().size();seat++){
                UUID uuid=table.seats().get(seat).occupant().orElse(null);if(uuid==null||seat>=round.players().size())continue;ServerPlayer player=level.getServer().getPlayerList().getPlayer(uuid);if(player==null||!near(player,level,pos))continue;
                var old=hands.get(uuid);var snapshot=PrivateHand.snapshot(table,seat);keep.add(uuid);
                if(old==null||old.owner!=player||!old.sameVisuals(snapshot)){if(old!=null)old.destroy();hands.put(uuid,new PrivateHand(table,player,seat,snapshot));}else old.snapshot=snapshot;hands.get(uuid).tick();
            }
            for(var it=hands.entrySet().iterator();it.hasNext();){var e=it.next();if(!keep.contains(e.getKey())){e.getValue().destroy();it.remove();}}
        }
        void renderSolitaire(MahjongSolitaireBlockEntity be){
            var board=be.board();if(board==null)return;used.clear();Set<String> liveHits=new HashSet<>();float scale=(float)((2.0/17)/(11.0/16));double gx=1.0/17,gz=(14.0/16)*scale/2,layerHeight=(9.0/16)*scale;
            for(int i=0;i<be.slotCount();i++)if(be.tileAt(i)!=null){var slot=board.slots().get(i);double x=.5-board.width()*gx/2+(slot.x()-board.minX()+1)*gx;double z=.5-board.depth()*gz/2+(slot.z()-board.minZ()+1)*gz+.0625*scale;double y=.0625+slot.y()*layerHeight+layerHeight/2;var at=new Vec3(pos.getX()+x,pos.getY()+y,pos.getZ()+z);String key="solitaire/"+i;
                tile(key,MahjongTileItems.codeForTile(be.tileAt(i)),at,scale,rotation(Direction.SOUTH,true,false),null);
                if(be.isFree(i)){liveHits.add(key);if(!hits.containsKey(key)){final int index=i;var hit=new InteractionElement(new VirtualElement.InteractionHandler(){@Override public void interact(ServerPlayer p,InteractionHand hand,Vec3 loc,boolean sneak){if(hand==InteractionHand.MAIN_HAND&&near(p,level,pos)&&be.isFree(index))be.onCuteClick(p,new InteractKey.Slot(index));}});hit.setOverridePos(at.add(0,-layerHeight/2,0));hit.setSize((float)(2*gx-.006),(float)layerHeight);holder.addElement(hit);hits.put(key,hit);}}
            }
            for(var it=hits.entrySet().iterator();it.hasNext();){var e=it.next();if(!liveHits.contains(e.getKey())){holder.removeElement(e.getValue());it.remove();}}label.setText(Component.literal(board.name()+" · 剩余 "+be.remainingTiles()+"张\n右键单张牌配对 · 右键底座获取提示"));cleanup();
        }
        void renderAltar(MahjongAltarBlockEntity altar){used.clear();for(int i=0;i<altar.getContainerSize();i++){var stack=altar.getItem(i);if(stack.isEmpty())continue;String key="altar/"+i;used.add(key);var d=tiles.get(key);var at=new Vec3(pos.getX()+.25+(i%3)*.25,pos.getY()+.85,pos.getZ()+.25+(i/3)*.25);if(d==null){d=new ItemDisplayElement(stack);d.setOverridePos(at);d.setScale(new Vector3f(.25f));holder.addElement(d);tiles.put(key,d);}else d.setItem(stack);}cleanup();}
        void destroy(){hands.values().forEach(PrivateHand::destroy);hands.clear();if(hud!=null)hud.destroy();if(collision!=null)collision.destroy();holder.destroy();}
    }
}
