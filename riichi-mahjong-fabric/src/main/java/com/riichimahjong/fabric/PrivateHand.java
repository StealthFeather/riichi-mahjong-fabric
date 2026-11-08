package com.riichimahjong.fabric;

import java.util.*;
import com.riichimahjong.mahjongtable.*;
import com.riichimahjong.cuterenderer.InteractKey;
import com.themahjong.driver.*;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.*;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A manual attachment has no chunk audience: exactly one watcher and a predicate per element. */
final class PrivateHand {
    record Snapshot(Object driver,Object round,Object phase,List<Integer> hand,List<MahjongTableHumanPlayer.TableButton> buttons){}
    final ServerPlayer owner;Snapshot snapshot;final ElementHolder holder=new ElementHolder();
    final MahjongTableBlockEntity table;final int seat;final List<UUID> tokens=new ArrayList<>();
    static final Map<UUID,RunnableAction> ACTIONS=new HashMap<>();
    record RunnableAction(PrivateHand view,Runnable action){}
    static Snapshot snapshot(MahjongTableBlockEntity table,int seat){
        var d=table.driver();var r=d.match().currentRound().orElseThrow();
        int count=r.handDisplayOrder(seat).size();if(d.currentPhase() instanceof MatchPhase.Dealing dealing){double fraction=switch(dealing.stage()){case WALL_BUILDING->0;case DEALING_HANDS->Math.min(1,dealing.elapsed()/dealing.stage().buildDuration());case DORA_FLIP->1;};count=(int)Math.round(count*fraction);}
        return new Snapshot(d,r,d.currentPhase(),r.handDisplayOrder(seat).stream().limit(count).map(PublicTable::code).toList(),List.copyOf(MahjongTableHumanPlayer.tableButtons(d,seat,table.riichiPendingForSeat(seat))));
    }
    PrivateHand(MahjongTableBlockEntity table,ServerPlayer owner,int seat,Snapshot snapshot){
        this.table=table;this.owner=owner;this.seat=seat;this.snapshot=snapshot;
        var dir=PortViews.outward(seat,table.getBlockState());var pos=table.getBlockPos();
        // Polymer removes unattached holders from the player's tracking list every tick.
        // ManualAttachment keeps it alive without registering any chunk watchers.
        new ManualAttachment(holder,owner.level(),()->Vec3.atCenterOf(pos));
        boolean winner=false;if(snapshot.phase instanceof MatchPhase.RoundEnded ended)for(var win:ended.winResults())if(seat<win.pointDeltas().size()&&win.pointDeltas().get(seat)>0)winner=true;
        if(!winner)for(int i=0;i<snapshot.hand.size();i++){
            final int index=i;Vec3 at=TableLayout.anchor(pos,dir,-.44+i*.073,.65,.85);
            var tile=PortViews.display(holder,PortViews.tileModel(snapshot.hand.get(i)),at,TableLayout.TILE_SCALE,PortViews.rotation(dir,false,false));tile.setVisibilityPredicate(p->p==owner);
            var hit=new InteractionElement(new VirtualElement.InteractionHandler(){@Override public void interact(ServerPlayer player,InteractionHand hand,Vec3 loc,boolean sneaking){if(hand==InteractionHand.MAIN_HAND&&valid(player))click(InteractKey.SeatSlot.AREA_HAND,index);}});
            hit.setOverridePos(at.add(0,-.05*TableLayout.TILE_ENLARGEMENT,0));hit.setSize(.071f*TableLayout.TILE_ENLARGEMENT,.12f*TableLayout.TILE_ENLARGEMENT);hit.setResponse(true);hit.setVisibilityPredicate(p->p==owner);holder.addElement(hit);
        }
        MutableComponent message=Component.literal("麻将操作 ").withStyle(ChatFormatting.GOLD);
        for(int i=0;i<snapshot.buttons.size();i++){
            final int index=i;var button=snapshot.buttons.get(i);String text=label(button);UUID token=UUID.randomUUID();tokens.add(token);ACTIONS.put(token,new RunnableAction(this,()->click(InteractKey.SeatSlot.AREA_BUTTON,index)));
            message.append(Component.literal("["+text+"] ").withStyle(s->s.withColor(ChatFormatting.YELLOW).withClickEvent(new ClickEvent.RunCommand("/riichi_action "+token))));
            int cols=Math.min(4,snapshot.buttons.size()),row=i/4,col=i%4,count=Math.min(4,snapshot.buttons.size()-row*4);
            Vec3 at=TableLayout.anchor(pos,dir,(col-(count-1)/2.0)*.37,.71,.70+(row-.5)*.10);
            var display=new TextDisplayElement(Component.literal(text));display.setOverridePos(at);display.setScale(new Vector3f(.28f*TableLayout.TILE_ENLARGEMENT));display.setLeftRotation(PortViews.rotation(dir,true,false).rotateZ((float)Math.PI));display.setLineWidth(200);display.setBackground(0xC0333333);display.setDisplaySize(2,2);display.setVisibilityPredicate(p->p==owner);holder.addElement(display);
            var hit=new InteractionElement(new VirtualElement.InteractionHandler(){@Override public void interact(ServerPlayer p,InteractionHand h,Vec3 loc,boolean sneak){if(h==InteractionHand.MAIN_HAND&&valid(p))click(InteractKey.SeatSlot.AREA_BUTTON,index);}});hit.setOverridePos(at.add(0,-.02*TableLayout.TILE_ENLARGEMENT,0));hit.setSize(.30f*TableLayout.TILE_ENLARGEMENT,.04f*TableLayout.TILE_ENLARGEMENT);hit.setResponse(true);hit.setVisibilityPredicate(p->p==owner);holder.addElement(hit);
        }
        holder.startWatching(owner);
        if(!snapshot.buttons.isEmpty())owner.sendSystemMessage(message);
    }
    boolean valid(ServerPlayer player){
        if(player!=owner||!PortViews.near(player,owner.level(),table.getBlockPos())||table.isRemoved()||table.getLevel()!=owner.level())return false;
        if(table.seatOfPlayer(owner.getUUID()).orElse(-1)!=seat||table.driver()!=snapshot.driver)return false;
        return snapshot.equals(snapshot(table,seat));
    }
    boolean sameVisuals(Snapshot other){return snapshot.driver==other.driver&&snapshot.hand.equals(other.hand)&&snapshot.buttons.equals(other.buttons)&&winner(snapshot.phase)==winner(other.phase);}
    boolean winner(Object phase){if(phase instanceof MatchPhase.RoundEnded ended)for(var win:ended.winResults())if(seat<win.pointDeltas().size()&&win.pointDeltas().get(seat)>0)return true;return false;}
    void click(byte area,int index){if(valid(owner))table.onCuteClick(owner,new InteractKey.SeatSlot((byte)seat,area,(short)index));}
    static void run(ServerPlayer p,UUID token){var action=ACTIONS.get(token);if(action!=null&&action.view.valid(p))action.action.run();else p.sendSystemMessage(Component.literal("操作已过期，请点击当前手牌或最新按钮。"),true);}
    void destroy(){tokens.forEach(ACTIONS::remove);holder.destroy();}
    static String label(MahjongTableHumanPlayer.TableButton button){
        if(button instanceof MahjongTableHumanPlayer.TableButton.RiichiToggle r)return r.active()?"立直：已选":"立直";
        var action=((MahjongTableHumanPlayer.TableButton.SubmitAction)button).action();
        return switch(action){
            case PlayerAction.DeclareRon a->"荣和";case PlayerAction.DeclareTsumo a->"自摸";case PlayerAction.DeclareChankan a->"抢杠";
            case PlayerAction.Chi a->"吃 "+tiles(a.handTiles());case PlayerAction.Pon a->"碰 "+tiles(a.handTiles());case PlayerAction.Daiminkan a->"大明杠 "+tiles(a.handTiles());
            case PlayerAction.Ankan a->"暗杠 "+tiles(a.handTiles());case PlayerAction.Kakan a->"加杠 "+a.addedTile();case PlayerAction.DeclareKita a->"拔北";
            case PlayerAction.KyuushuAbort a->"九种九牌";case PlayerAction.Pass a->"跳过";default->"操作";
        };
    }
    private static String tiles(List<TheMahjongTile> tiles){return tiles.stream().map(t->(t.redDora()?"赤":"")+t.rank()+switch(t.suit()){case MANZU->"万";case PINZU->"筒";case SOUZU->"条";case WIND->"风";case DRAGON->"元";}).reduce((a,b)->a+" "+b).orElse("");}
}
