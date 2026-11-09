package com.riichimahjong.fabric;

import java.util.*;
import java.util.function.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.riichimahjong.mahjongtable.*;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.registry.ModItems;
import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** The original custom client screens are represented by vanilla container menus. */
public final class PortMenus {
    public static void register(){
        CommandRegistrationCallback.EVENT.register((d,access,env)->{
            var root=Commands.literal("mahjong").executes(c->{c.getSource().sendSuccess(()->Component.literal("立直麻将：右键桌边或座位文字入座，也可 /mahjong join；潜行右键中央开局。右键自己的单张手牌出牌；吃碰杠、立直、胡牌看经验条上方提示并点击本人按钮。对局中 Shift 右键命中桌子退出，命中牌不离座；普通右键桌子或 /mahjong join 接回原座位。/mahjong menu 打开设置。管理员 /mahjong give 获得原版物品。"),false);return 1;});
            root.then(Commands.literal("join").executes(c->{var p=c.getSource().getPlayerOrException();return TableSeating.joinNearest(p,nearest(p))?1:0;}).then(Commands.argument("seat",IntegerArgumentType.integer(1,4)).executes(c->{var p=c.getSource().getPlayerOrException();return TableSeating.join(p,nearest(p),IntegerArgumentType.getInteger(c,"seat")-1)?1:0;})));
            root.then(Commands.literal("menu").executes(c->{var p=c.getSource().getPlayerOrException();settings(p,nearest(p));return 1;}));
            root.then(Commands.literal("start").executes(c->{var p=c.getSource().getPlayerOrException();var t=nearest(p);if(host(p,t))t.tryStartMatchWithAutoSeating(new java.security.SecureRandom().nextLong());return 1;}));
            root.then(Commands.literal("next").executes(c->{var p=c.getSource().getPlayerOrException();var t=nearest(p);if(t.seatOfPlayer(p.getUUID()).isPresent())t.advanceRoundAfterResult();return 1;}));
            root.then(Commands.literal("leave").executes(c->{var p=c.getSource().getPlayerOrException();return TableSeating.leave(p,nearest(p))?1:0;}));
            root.then(Commands.literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c->{items(c.getSource().getPlayerOrException(),0);return 1;}));
            d.register(root);
            d.register(Commands.literal("riichi_action").then(Commands.argument("token",StringArgumentType.word()).executes(c->{try{PrivateHand.run(c.getSource().getPlayerOrException(),UUID.fromString(StringArgumentType.getString(c,"token")));}catch(IllegalArgumentException ignored){}return 1;})));
        });
    }
    static MahjongTableBlockEntity nearest(ServerPlayer p){
        MahjongTableBlockEntity best=null;double distance=65;var center=p.blockPosition();
        for(var pos:BlockPos.betweenClosed(center.offset(-8,-4,-8),center.offset(8,4,8))){if(!p.level().hasChunkAt(pos))continue;if(p.level().getBlockEntity(pos) instanceof MahjongTableBlockEntity table){double d=pos.distToCenterSqr(p.position());if(d<distance){best=table;distance=d;}}}
        if(best==null)throw new IllegalArgumentException("请靠近原版麻将桌。");return best;
    }
    static boolean host(ServerPlayer p,MahjongTableBlockEntity table){return table.seats().stream().flatMap(s->s.occupant().stream()).findFirst().map(p.getUUID()::equals).orElse(true);}
    static ItemStack icon(Item item,String text){var s=new ItemStack(item);s.set(DataComponents.CUSTOM_NAME,Component.literal(text));return s;}
    static void open(ServerPlayer p,String title,BooleanSupplier valid,Consumer<Actions> setup){p.openMenu(new SimpleMenuProvider((id,inv,player)->{var menu=new Actions(id,inv,valid);setup.accept(menu);return menu;},Component.literal(title)));}
    public static void settings(ServerPlayer p,MahjongTableBlockEntity table){
        open(p,"立直麻将 · 原版设置",()->table.getLevel()==p.level()&&PortViews.near(p,p.level(),table.getBlockPos())&&!table.isRemoved(),menu->{
            var presets=RuleSetPreset.values();String[] names={"雀魂四麻","天凤四麻","WRC 四麻","雀魂三麻","天凤三麻"};
            for(int i=0;i<presets.length;i++){final int n=i;menu.button(i,icon(table.preset()==presets[i]?Items.EMERALD:Items.PAPER,names[i]),()->{if(host(p,table))table.selectPreset(presets[n]);settings(p,table);});}
            for(int i=0;i<table.seats().size();i++){final int seat=i;var s=table.seats().get(i);String occupant=s.occupant().map(uuid->{var player=p.level().getServer().getPlayerList().getPlayer(uuid);return player==null?"离线玩家":player.getName().getString();}).orElse("空位（开局由 BOT 补齐）");menu.button(9+i,icon(s.enabled()?Items.OAK_BOAT:Items.CLAY_BALL,new String[]{"东","南","西","北"}[i]+" · "+occupant),()->{if(table.seatOfPlayer(p.getUUID()).orElse(-1)==seat){TableSeating.leave(p,table);}else TableSeating.join(p,table,seat);settings(p,table);});menu.button(36+i,icon(Items.LEVER,"启用 / 关闭座位 "+(i+1)),()->{if(host(p,table))table.toggleSeatEnabled(seat);settings(p,table);});}
            menu.button(18,icon(Items.EMERALD,table.hasActiveMatch()?"结束对局":"开始对局（空位由原版 BOT 补齐）"),()->{if(host(p,table)){if(table.hasActiveMatch())table.endGame();else table.tryStartMatchWithAutoSeating(new java.security.SecureRandom().nextLong());}p.closeContainer();});
            menu.button(19,icon(Items.CHEST,"无限供牌："+table.mintTilesFromNothing()),()->{if(host(p,table)&&!table.hasActiveMatch())table.setMintTilesFromNothing(!table.mintTilesFromNothing());settings(p,table);});
            menu.button(20,icon(Items.ENDER_EYE,"摸牌使用私有显示，右键单张牌打出"),()->{});
            menu.button(27,icon(Items.BARREL,"牌桌库存 · 136 槽分页"),()->inventory(p,table,0));
            menu.button(28,icon(Items.BOOK,"领取空白牌谱"),()->{p.getInventory().add(new ItemStack(ModItems.MAHJONG_TABLE_RECORD.get()));p.closeContainer();});
        });
    }
    static void items(ServerPlayer p,int page){
        var list=BuiltInRegistries.ITEM.entrySet().stream().filter(e->e.getKey().identifier().getNamespace().equals("riichi_mahjong")).map(Map.Entry::getValue).toList();
        open(p,"原项目物品 · "+(page+1),()->true,menu->{for(int i=0;i<45&&page*45+i<list.size();i++){Item item=list.get(page*45+i);menu.button(i,new ItemStack(item),()->p.getInventory().add(new ItemStack(item)));}if(page>0)menu.button(45,icon(Items.ARROW,"上一页"),()->items(p,page-1));if((page+1)*45<list.size())menu.button(53,icon(Items.ARROW,"下一页"),()->items(p,page+1));});
    }
    public static void generator(ServerPlayer p,YakuGeneratorBlockEntity machine){
        open(p,"役发电机 · Tier "+machine.getTierIndex(),()->machine.getLevel()==p.level()&&PortViews.near(p,p.level(),machine.getBlockPos())&&!machine.isRemoved(),menu->{
            for(int i=0;i<machine.getSlotCount();i++){final int slot=i;int code=machine.getTileCode(i);menu.button(i,code<0?icon(Items.CLAY_BALL,"空位"):new ItemStack(MahjongTileItems.itemForCode(code)),()->{machine.rerollSlot(slot);generator(p,machine);});}
            menu.button(27,icon(Items.EMERALD,"自摸 / 结算"),()->{machine.tsumo(p);generator(p,machine);});
            menu.button(28,icon(Items.COMPARATOR,"自动理牌："+machine.isAutoSortOnReroll()),()->{machine.toggleAutoSortOnReroll();generator(p,machine);});
            menu.button(29,icon(Items.REDSTONE,"储能 "+machine.getEnergyStored()+" / "+machine.getMaxEnergyStored()+" · "+machine.getCurrentRfPerTick()+" RF/t"),()->generator(p,machine));
            menu.button(30,icon(Items.CLOCK,"剩余换牌 "+machine.getDrawsRemaining()+" · 上次 "+machine.getLastHan()+"番"),()->generator(p,machine));
            menu.button(35,icon(Items.BARRIER,"重置发电机"),()->{machine.resetMachine();generator(p,machine);});
        });
    }
    public static AbstractContainerMenu inventoryMenu(int id,Inventory inv,MahjongTableBlockEntity table,int page){return new InventoryPage(id,inv,table,page);}
    static void inventory(ServerPlayer p,MahjongTableBlockEntity table,int page){if(!table.hasActiveMatch())p.openMenu(new SimpleMenuProvider((id,inv,player)->inventoryMenu(id,inv,table,page),Component.literal("牌桌库存 · "+(page+1)+" / 4")));}
    static final class Actions extends ChestMenu {
        final Map<Integer,Runnable> actions=new HashMap<>();final BooleanSupplier valid;
        Actions(int id,Inventory inv,BooleanSupplier valid){super(MenuType.GENERIC_9x6,id,inv,new SimpleContainer(54),6);this.valid=valid;}
        void button(int slot,ItemStack item,Runnable action){getContainer().setItem(slot,item);actions.put(slot,action);}
        @Override public void clicked(int slot,int button,ContainerInput type,Player p){if(valid.getAsBoolean()&&type==ContainerInput.PICKUP&&actions.containsKey(slot))actions.get(slot).run();}
        @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player p){return valid.getAsBoolean();}
    }
    static final class InventoryPage extends ChestMenu {
        final MahjongTableBlockEntity table;final int page;
        InventoryPage(int id,Inventory inv,MahjongTableBlockEntity table,int page){super(MenuType.GENERIC_9x6,id,inv,new PagedContainer(table,page),6);this.table=table;this.page=page;}
        @Override public boolean stillValid(Player p){return table.stillValid(p);}
        @Override public void clicked(int slot,int button,ContainerInput type,Player player){if(!stillValid(player))return;if(slot>=45&&slot<54){if(player instanceof ServerPlayer p){if(slot==45&&page>0)inventory(p,table,page-1);if(slot==53&&page<3)inventory(p,table,page+1);if(slot==49)settings(p,table);}return;}super.clicked(slot,button,type,player);}
        @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    }
    static final class PagedContainer implements Container {
        final MahjongTableBlockEntity table;final int offset;PagedContainer(MahjongTableBlockEntity table,int page){this.table=table;offset=page*45;}
        int index(int slot){return slot>=0&&slot<45&&offset+slot<136?offset+slot:-1;}
        public int getContainerSize(){return 54;}public boolean isEmpty(){return table.isEmpty();}
        public ItemStack getItem(int slot){int i=index(slot);if(i>=0)return table.getItem(i);return switch(slot){case 45->icon(Items.ARROW,"上一页");case 49->icon(Items.BOOK,"设置");case 53->icon(Items.ARROW,"下一页");default->ItemStack.EMPTY;};}
        public ItemStack removeItem(int slot,int count){int i=index(slot);return i<0?ItemStack.EMPTY:table.removeItem(i,count);}
        public ItemStack removeItemNoUpdate(int slot){int i=index(slot);return i<0?ItemStack.EMPTY:table.removeItemNoUpdate(i);}
        public void setItem(int slot,ItemStack stack){int i=index(slot);if(i>=0)table.setItem(i,stack);}
        public void setChanged(){table.setChanged();}public boolean stillValid(Player p){return table.stillValid(p);}
        public boolean canPlaceItem(int slot,ItemStack stack){return index(slot)>=0&&!table.hasActiveMatch();}
        public void clearContent(){}
    }
}
