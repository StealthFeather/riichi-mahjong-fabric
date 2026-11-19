package com.riichimahjong.fabric;

import java.util.List;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import com.riichimahjong.mahjongtable.MahjongTableBlock;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Shared display, hit boxes and ray validation for live actions and replay controls. */
final class TableEdgeButtons {
    final List<TableButtonLayout.Button> layout;
    private final MahjongTableBlockEntity table;
    private final ServerPlayer owner;
    private final int seat;
    private final BooleanSupplier valid;
    private final IntConsumer action;
    private final ElementHolder holder;
    private final List<VirtualElement> elements=new ArrayList<>();

    TableEdgeButtons(ElementHolder holder, MahjongTableBlockEntity table, ServerPlayer owner,
                     int seat, List<String> labels, BooleanSupplier valid, IntConsumer action) {
        this(holder,table,owner,seat,TableButtonLayout.arrange(labels),valid,action,true);
    }

    TableEdgeButtons(ElementHolder holder, MahjongTableBlockEntity table, ServerPlayer owner,
                     int seat,List<TableButtonLayout.Button> layout,BooleanSupplier valid,
                     IntConsumer action,boolean interactive){
        this.table=table;this.owner=owner;this.seat=seat;this.valid=valid;this.action=action;
        this.holder=holder;this.layout=List.copyOf(layout);
        var pos=table.getBlockPos();var dir=PortViews.outward(seat,table.getBlockState());
        for(var button:layout){
            var text=Component.literal(button.text()).withStyle(style->style.withColor(ChatFormatting.YELLOW)
                    .withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("uniform"))));
            var display=new TextDisplayElement(text);
            display.setOverridePos(button.textPosition(pos,dir));
            display.setScale(new Vector3f(button.scale()*TableLayout.TILE_ENLARGEMENT));
            display.setBillboardMode(Display.BillboardConstraints.FIXED);
            display.setLeftRotation(TableLayout.textRotation(dir));display.setLineWidth(1000);
            display.setBackground(0xD0333333);display.setDisplaySize(2,2);
            display.setVisibilityPredicate(p->p==owner);holder.addElement(display);elements.add(display);
            if(!interactive)continue;
            int cells=(int)Math.ceil(button.width()/button.depth());
            for(int cell=0;cell<cells;cell++){
                double along=button.along()-button.width()/2+button.depth()/2
                        +(cells==1?0:cell*(button.width()-button.depth())/(cells-1));
                var hit=new InteractionElement(new VirtualElement.InteractionHandler(){
                    @Override public void interact(ServerPlayer p,InteractionHand hand,Vec3 loc,boolean sneak){
                        if(hand!=InteractionHand.MAIN_HAND||p!=owner||MahjongTableBlock.routeWindPlateUse(p, table))return;
                        Vec3 start=p.getEyePosition();handleHit(start,start.add(p.getLookAngle().scale(8)));
                    }
                });
                hit.setOverridePos(TableLayout.anchor(pos,dir,along,button.height()-.003,button.distance()));
                hit.setSize((float)button.depth()*TableLayout.TILE_ENLARGEMENT,.006f*TableLayout.TILE_ENLARGEMENT);
                hit.setResponse(true);hit.setVisibilityPredicate(p->p==owner);holder.addElement(hit);elements.add(hit);
            }
        }
    }

    void destroy(){elements.forEach(holder::removeElement);elements.clear();}

    boolean handleHit(Vec3 start,Vec3 end){
        if(table.getLevel()!=owner.level()||!PortViews.near(owner,owner.level(),table.getBlockPos()))return false;
        var dir=PortViews.outward(seat,table.getBlockState());
        for(var button:layout){
            var hit=button.hit(table.getBlockPos(),dir,start,end);
            if(hit.isEmpty()||!PortViews.clearButtonRay(owner,table,start,hit.get()))continue;
            table.recordPrivateClick(owner);
            if(valid.getAsBoolean())action.accept(button.index());
            else owner.sendSystemMessage(Component.literal("操作已过期，请点击当前桌沿按钮。"),true);
            return true;
        }
        return false;
    }
}
