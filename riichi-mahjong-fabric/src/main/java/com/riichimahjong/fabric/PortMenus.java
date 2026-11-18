package com.riichimahjong.fabric;

import java.util.*;
import java.util.function.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.riichimahjong.mahjongtable.*;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.registry.ModItems;
import com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity;
import com.themahjong.TheMahjongTile;
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
import net.minecraft.world.item.component.ItemLore;

/** The original custom client screens are represented by vanilla container menus. */
public final class PortMenus {
    public static void register(){
        CommandRegistrationCallback.EVENT.register((d,access,env)->{
            var root=Commands.literal("mahjong").executes(c->{c.getSource().sendSuccess(()->Component.literal("立直麻将：右键桌边或座位文字入座，也可 /mahjong join；桌主潜行右键中心风盘打开菜单，再点击开始对局。右键自己的单张手牌出牌；吃碰杠、立直、胡牌看经验条上方提示并点击本人按钮。仅 /mahjong leave 离座；普通右键桌子或 /mahjong join 接回原座位。所有玩家可用 /mahjong menu 或潜行右键风盘打开菜单；设置仅桌主可调；/mahjong start 同样打开菜单。/mahjong stats 查看个人统计。管理员 /mahjong give 获得原版物品。"),false);return 1;});
            root.then(Commands.literal("join").executes(c->{var p=c.getSource().getPlayerOrException();return TableSeating.joinNearest(p,nearest(p))?1:0;}).then(Commands.argument("seat",IntegerArgumentType.integer(1,4)).executes(c->{var p=c.getSource().getPlayerOrException();return TableSeating.join(p,nearest(p),IntegerArgumentType.getInteger(c,"seat")-1)?1:0;})));
            root.then(Commands.literal("menu").executes(c->{var p=c.getSource().getPlayerOrException();settings(p,nearest(p));return 1;}));
            root.then(Commands.literal("start").executes(c->{var p=c.getSource().getPlayerOrException();settings(p,nearest(p));return 1;}));
            root.then(Commands.literal("next").executes(c->{var p=c.getSource().getPlayerOrException();var t=nearest(p);if(t.seatOfPlayer(p.getUUID()).isPresent())t.advanceRoundAfterResult();return 1;}));
            root.then(Commands.literal("leave").executes(c->{var p=c.getSource().getPlayerOrException();return TableSeating.leave(p,nearest(p))?1:0;}));
            root.then(Commands.literal("stats").executes(c->{AnalysisMenus.open(c.getSource().getPlayerOrException());return 1;}));
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
    static boolean host(ServerPlayer p,MahjongTableBlockEntity table){return table.owner().filter(p.getUUID()::equals).isPresent();}
    static boolean tableMenuValid(ServerPlayer p,MahjongTableBlockEntity table){return table.getLevel()==p.level()&&!table.isRemoved()&&PortViews.viewingNear(p,p.level(),table.getBlockPos())&&p.level().hasChunkAt(table.getBlockPos())&&p.level().getBlockEntity(table.getBlockPos())==table;}
    static ItemStack icon(Item item,String text){var s=new ItemStack(item);s.set(DataComponents.CUSTOM_NAME,Component.literal(text));return s;}
    static ItemStack tileOption(TheMahjongTile.Suit suit,int rank,String title,String... descriptions){
        var item=icon(MahjongTileItems.itemForCode(MahjongTileItems.codeForTile(new TheMahjongTile(suit,rank,false))),title);
        item.set(DataComponents.LORE,new ItemLore(Arrays.stream(descriptions).<Component>map(Component::literal).toList()));
        return item;
    }
    static void open(ServerPlayer p,String title,BooleanSupplier valid,Consumer<Actions> setup){p.openMenu(new SimpleMenuProvider((id,inv,player)->{var menu=new Actions(id,inv,valid);setup.accept(menu);return menu;},Component.literal(title)));}
    /** Literal descriptions remain readable with the existing shared resource pack. */
    static List<String> familyDescription(RuleSetPreset.Family family){
        return switch(family){
            case MAHJONG_SOUL -> List.of("雀魂日麻规则；四麻 / 三麻均可。", "有赤宝、一发与里宝。", "换三张、癞子与血战可另行开启。");
            case TENHOU -> List.of("天凤日麻规则；四麻 / 三麻均可。", "有赤宝、一发与里宝。", "换三张、癞子与血战可另行开启。");
            case WRC -> List.of("WRC 四人竞赛规则；30,000 点起。", "无赤宝；有一发、里宝与杠宝。", "换三张、癞子与血战可另行开启。");
            case SICHUAN -> List.of("川麻四人规则；开局定缺。", "无吃、立直与宝牌；换三张须同花色。", "换三张、癞子与血战可另行开启。");
            case JPML -> List.of("日本职业麻将联盟公式战（A规则）。", "30,000 点起；无赤宝、一发、里宝与杠宝。", "换三张、癞子与血战可另行开启。");
            case M_LEAGUE -> List.of("M-League 四人规则。", "25,000 点起 / 30,000 点返；赤宝三张。", "有一发、里宝与杠宝；切上满贯。", "头跳；负分继续；不西入。", "换三张、癞子与血战可另行开启。");
        };
    }
    public static void settings(ServerPlayer p,MahjongTableBlockEntity table){
        if(table.replay()!=null){com.riichimahjong.replay.ReplayMenus.controls(p,table);return;}
        if(!tableMenuValid(p,table)){
            p.sendSystemMessage(Component.literal("请靠近有效的麻将桌再打开菜单。"),true);
            return;
        }
        if(table.owner().isEmpty())table.claimHostIfUnowned(p);
        var driver=table.driver();long revision=table.matchRevision();
        open(p,"立直麻将 · "+(host(p,table)?"桌主设置":"查看配置"),()->tableMenuValid(p,table)&&table.driver()==driver&&table.matchRevision()==revision,menu->{
            var family=table.preset().family();
            boolean locked=table.hasActiveMatch();
            String lockLabel=!host(p,table)?" · 仅桌主可调":locked?" · 对局中锁定":"";
            for(var candidate:RuleSetPreset.Family.values()){
                boolean selected=family==candidate;
                var familyIcon=icon(selected?Items.EMERALD:Items.PAPER,
                        candidate.label()+(selected?" · 已选":"")+lockLabel);
                var descriptions=new ArrayList<Component>();
                descriptions.add(Component.literal(candidate.supportsThreePlayers()?"支持四麻 / 三麻":"仅支持四麻"));
                familyDescription(candidate).forEach(line->descriptions.add(Component.literal(line)));
                descriptions.add(Component.literal(locked?"请先结束当前对局，再调整规则。":"点击选择规则，人数在第二行切换。"));
                familyIcon.set(DataComponents.LORE,new ItemLore(descriptions));
                menu.button(candidate.ordinal(),familyIcon,()->{
                    if(host(p,table)&&!table.hasActiveMatch())
                        table.selectPreset(candidate.preset(table.effectivePreset().playerCount()));
                    settings(p,table);
                });
            }
            var length=table.matchLength();
            menu.button(9,tileOption(TheMahjongTile.Suit.WIND,length==MatchLength.EAST?1:2,
                    "赛程："+length.label()+lockLabel,
                    length==MatchLength.EAST?"预定赛程：东场":"预定赛程：东场＋南场",
                    "点击切换东风场 / 南风场。",
                    "连庄及延长局仍依所选规则执行。"),()->{
                if(host(p,table)&&!table.hasActiveMatch())
                    table.selectMatchLength(table.matchLength()==MatchLength.EAST?MatchLength.SOUTH:MatchLength.EAST);
                settings(p,table);
            });
            boolean fourPlayer=table.effectivePreset().playerCount()==4;
            menu.button(10,tileOption(TheMahjongTile.Suit.MANZU,fourPlayer?4:3,
                    "人数："+(fourPlayer?"四麻":"三麻")+lockLabel,
                    family.supportsThreePlayers()?"点击切换四麻 / 三麻。":family.label()+"仅支持四麻，不能切换三麻。"),()->{
                if(host(p,table)&&!table.hasActiveMatch()&&table.preset().family().supportsThreePlayers())
                    table.selectPreset(table.preset().family().preset(table.effectivePreset().playerCount()==4?3:4));
                settings(p,table);
            });
            boolean sichuan=family==RuleSetPreset.Family.SICHUAN;
            String variantLock=locked?lockLabel:"";
            menu.button(11,tileOption(table.exchangeThreeTiles()?TheMahjongTile.Suit.SOUZU:TheMahjongTile.Suit.DRAGON,
                    table.exchangeThreeTiles()?3:1,"换三张："+(table.exchangeThreeTiles()?"开":"关")+variantLock,
                    "三索：开启；白板：关闭。",
                    "所有模式均可切换换三张。"),()->{
                if(host(p,table)&&!table.hasActiveMatch())
                    table.setExchangeThreeTiles(!table.exchangeThreeTiles());
                settings(p,table);
            });
            menu.button(12,tileOption(TheMahjongTile.Suit.DRAGON,
                    table.jokersEnabled()?3:1,"癞子："+(table.jokersEnabled()?"有":"无")+variantLock,
                    "红中：开启；白板：关闭。癞子使用独立牌面，日麻普通红中保留。",
                    "所有模式均可切换癞子。"),()->{
                if(host(p,table)&&!table.hasActiveMatch())
                    table.setJokersEnabled(!table.jokersEnabled());
                settings(p,table);
            });
            var bloodBattleIcon=icon(table.bloodBattle()?Items.REDSTONE:Items.GUNPOWDER,
                    "血战到底："+(table.bloodBattle()?"开":"关")+variantLock);
            bloodBattleIcon.set(DataComponents.LORE,new ItemLore(List.of(
                    Component.literal("开启后胡牌者退出本手，其余玩家继续。"),
                    Component.literal("红石粉：开启；火药：关闭。所有模式可用。"))));
            menu.button(13,bloodBattleIcon,()->{
                if(host(p,table)&&!table.hasActiveMatch())
                    table.setBloodBattle(!table.bloodBattle());
                settings(p,table);
            });
            boolean configurableAi=fourPlayer&&!sichuan&&!table.jokersEnabled()&&!table.bloodBattle();
            String aiLock=locked?lockLabel:sichuan?" · 川麻使用原版 BOT":table.jokersEnabled()?" · 癞子使用本地 BOT":table.bloodBattle()?" · 血战使用本地 BOT":fourPlayer?"":" · 仅日麻四麻生效";
            for(int i=0;i<table.seats().size();i++){
                final int seat=i;var s=table.seats().get(i);
                String seatName=new String[]{"东","南","西","北"}[i]+"位";
                String occupant=s.occupant().map(uuid->{var player=p.level().getServer().getPlayerList().getPlayer(uuid);return player==null?"离线玩家":player.getName().getString();}).orElse("空位（开局由 BOT 补齐）");
                ItemStack seatIcon;
                if(s.occupant().isPresent()){
                    UUID id=s.occupant().orElseThrow();
                    var seated=p.level().getServer().getPlayerList().getPlayer(id);
                    seatIcon=icon(Items.PLAYER_HEAD,seatName+" · "+occupant+(table.owner().filter(id::equals).isPresent()?" · 桌主":"")
                            +(id.equals(p.getUUID())?" · 点击离座":host(p,table)?" · 点击移交桌主":""));
                    seatIcon.set(DataComponents.PROFILE,seated==null
                            ?net.minecraft.world.item.component.ResolvableProfile.createUnresolved(id)
                            :net.minecraft.world.item.component.ResolvableProfile.createResolved(seated.getGameProfile()));
                }else seatIcon=icon(s.enabled()?Items.OAK_BOAT:Items.CLAY_BALL,
                        seatName+" · "+(s.enabled()?"空位 · 点击入座":"未启用"));
                menu.button(36+i,seatIcon,()->{
                    if(!tableMenuValid(p,table))return;
                    var current=table.seats().get(seat);
                    if(current.occupant().filter(p.getUUID()::equals).isPresent()){
                        TableSeating.leave(p,table);
                    }else if(current.occupant().isEmpty()){
                        TableSeating.join(p,table,seat);
                    }else if(host(p,table)){
                        confirmHostTransfer(p,table,seat,current.occupant().orElseThrow());
                        return;
                    }else p.sendSystemMessage(Component.literal("这个座位已有玩家。点击自己的头像可离座。"),true);
                    settings(p,table);
                });
                if(i<4){
                    String difficulty=table.botDifficultyOverride(seat).map(d->d.label()).orElse("跟随默认（"+table.defaultBotDifficulty().label()+"）");
                    menu.button(45+i,icon(Items.COMPARATOR,seatName+"四麻 AI："+difficulty+aiLock),()->{
                        if(host(p,table)&&configurableAi)table.cycleSeatBotDifficulty(seat);
                        settings(p,table);
                    });
                }
            }
            menu.button(44,icon(Items.SPYGLASS,table.isWatching(p.getUUID())?"退出观战":"观战 · 查看战局信息"),()->{
                if(!tableMenuValid(p,table))return;
                if(table.isWatching(p.getUUID())){
                    table.stopWatching(p.getUUID());
                    p.sendSystemMessage(Component.literal("已退出观战。"),true);
                }else{
                    if(table.seatOfPlayer(p.getUUID()).isPresent()&&!TableSeating.leave(p,table))return;
                    if(table.watch(p))p.sendSystemMessage(Component.literal("已开始观战。桌主可设置是否向观战者展示手牌。"),true);
                }
                settings(p,table);
            });
            if(host(p,table))for(var candidate:table.offlineSeatCandidates()){
                String seatName=new String[]{"东","南","西","北"}[candidate.seat()]+"位";
                menu.button(14+candidate.seat(),icon(Items.COMPARATOR,seatName+" · 用 AI 取代"+(candidate.reserved()?"离线保留者":"离线玩家")),()->{
                    if(table.replaceOfflineWithAI(p,candidate.seat(),candidate.occupant(),driver,revision))
                        p.sendSystemMessage(Component.literal("已用 AI 接替该座位，手牌和分数保留。"),true);
                    else p.sendSystemMessage(Component.literal("座位状态已变化，或你已不是桌主。"),true);
                    settings(p,table);
                });
            }
            String matchAction=!table.hasActiveMatch()?"开始对局（空位由 BOT 补齐）"
                    :table.isDriverTerminal()?"开始新对局（先确认结束终盘）":"结束对局（需确认）";
            menu.button(18,icon(Items.EMERALD,matchAction),()->{
                if(!host(p,table)){p.sendSystemMessage(Component.literal("只有桌主可以开始或结束对局。"),true);return;}
                if(table.hasActiveMatch())confirmTableAction(p,table,TableAction.END);
                else{
                    var result=table.tryStartMatchWithAutoSeating(new java.security.SecureRandom().nextLong());
                    if(result==MahjongTableBlockEntity.StartMatchResult.STARTED)p.closeContainer();
                    else{
                        p.sendSystemMessage(Component.literal(result==MahjongTableBlockEntity.StartMatchResult.SEATS_MISMATCH_PRESET
                                ?"座位配置与规则不一致，请重新选择规则预设或启用对应座位。":"牌桌状态已变化，请在菜单中重新操作。"),true);
                        settings(p,table);
                    }
                }
            });
            menu.button(19,icon(Items.CHEST,"无限供牌："+table.mintTilesFromNothing()),()->{if(host(p,table)&&!table.hasActiveMatch())table.setMintTilesFromNothing(!table.mintTilesFromNothing());settings(p,table);});
            menu.button(20,icon(table.spectatorHandsVisible()?Items.ENDER_EYE:Items.ENDER_PEARL,
                    "观战手牌："+(table.spectatorHandsVisible()?"显示":"隐藏")+(host(p,table)?"":" · 仅桌主可调")),()->{
                if(host(p,table))table.setSpectatorHandsVisible(!table.spectatorHandsVisible());
                settings(p,table);
            });
            menu.button(21,icon(Items.COMPARATOR,"默认四麻 AI："+table.defaultBotDifficulty().label()+aiLock),()->{if(host(p,table)&&configurableAi)table.setDefaultBotDifficulty(table.defaultBotDifficulty().next());settings(p,table);});
            menu.button(22,icon(Items.PAPER,sichuan?"川麻使用原版 BOT，三级 AI 难度不生效":table.jokersEnabled()||table.bloodBattle()?"癞子及血战模式使用本地 BOT":fourPlayer?"初级 → 中级 → 高级；座位可单独设置":"三麻使用原版 BOT；难度配置仅适用于日麻四麻"),()->{});
            var effects=com.riichimahjong.fabric.wineffect.WinEffects.all();
            var effect=com.riichimahjong.fabric.wineffect.WinEffects.resolve(table.winEffectId());
            menu.button(23,icon(Items.EVOKER_SPAWN_EGG,"胡牌特效："+effect.displayName()+lockLabel),()->{
                if(host(p,table)&&!table.hasActiveMatch())
                    table.selectWinEffect(effects.get((effects.indexOf(effect)+1)%effects.size()).id());
                settings(p,table);
            });
            menu.button(27,icon(Items.BARREL,"牌桌库存 · 136 槽分页"),()->inventory(p,table,0));
            menu.button(28,icon(Items.BOOK,"领取空白牌谱"),()->{p.getInventory().add(new ItemStack(ModItems.MAHJONG_TABLE_RECORD.get()));p.closeContainer();});
            menu.button(29,icon(Items.WRITABLE_BOOK,"我的统计分析"),()->AnalysisMenus.open(p,com.riichimahjong.analysis.AnalysisMode.of(table.effectivePreset(),table.matchLength(),table.exchangeThreeTiles(),table.jokersEnabled(),table.bloodBattle())));
            menu.button(35,icon(Items.BARRIER,"重置牌桌（需确认"+(host(p,table)?"":" · 仅桌主")+"）"),()->{if(host(p,table))confirmTableReset(p,table);else p.sendSystemMessage(Component.literal("只有桌主可以重置牌桌。"),true);});
        });
    }
    static void confirmHostTransfer(ServerPlayer player,MahjongTableBlockEntity table,int seat,UUID occupant){
        if(!tableMenuValid(player,table)||!host(player,table)||occupant.equals(player.getUUID()))return;
        var driver=table.driver();long revision=table.matchRevision();
        BooleanSupplier valid=()->tableMenuValid(player,table)&&host(player,table)&&table.driver()==driver
                &&table.matchRevision()==revision&&seat>=0&&seat<table.seats().size()
                &&table.seats().get(seat).occupant().filter(occupant::equals).isPresent();
        var target=player.level().getServer().getPlayerList().getPlayer(occupant);
        String name=target==null?"离线玩家":target.getName().getString();
        open(player,"立直麻将 · 确认移交桌主",valid,menu->{
            menu.button(13,icon(Items.PLAYER_HEAD,"移交桌主给 "+name),()->{});
            menu.button(29,icon(Items.REDSTONE_BLOCK,"确认移交桌主"),()->{
                if(player.containerMenu!=menu||!valid.getAsBoolean())return;
                if(table.transferHost(player,seat,occupant,driver,revision)){
                    player.sendSystemMessage(Component.literal("已将桌主移交给 "+name+"。"),true);
                    var recipient=player.level().getServer().getPlayerList().getPlayer(occupant);
                    if(recipient!=null)recipient.sendSystemMessage(Component.literal("你已成为麻将桌的桌主。"),true);
                }else player.sendSystemMessage(Component.literal("移交失败，请确认对方仍在线并位于该座位。"),true);
                settings(player,table);
            });
            menu.button(33,icon(Items.ARROW,"取消 · 返回配置"),()->settings(player,table));
        });
    }
    private enum TableAction {
        END("结束对局","结束当前对局，清空比分与供托","保留当前入座玩家，返回菜单后可开始新对局","对局已结束，可在菜单中点击“开始对局”。"),
        RESET("重置牌桌","结束当前牌局，清空比分与供托","释放所有座位，重新入座后开局","牌桌已重置，可重新入座开局。");
        final String label,description,seats,success;
        TableAction(String label,String description,String seats,String success){this.label=label;this.description=description;this.seats=seats;this.success=success;}
    }
    static void confirmTableReset(ServerPlayer p,MahjongTableBlockEntity table){
        confirmTableAction(p,table,TableAction.RESET);
    }
    private static void confirmTableAction(ServerPlayer p,MahjongTableBlockEntity table,TableAction action){
        if(!tableMenuValid(p,table)||!host(p,table))return;
        if(action==TableAction.END&&!table.hasActiveMatch())return;
        var driver=table.driver();long revision=table.matchRevision();
        BooleanSupplier valid=()->tableMenuValid(p,table)&&host(p,table)&&table.driver()==driver&&table.matchRevision()==revision;
        open(p,"立直麻将 · 确认"+action.label,valid,menu->{
            menu.button(12,icon(Items.PAPER,action.description),()->{});
            menu.button(13,icon(Items.CHEST,"保留桌子、库存和规则 / AI 设置"),()->{});
            menu.button(14,icon(Items.OAK_BOAT,action.seats),()->{});
            menu.button(29,icon(Items.REDSTONE_BLOCK,"确认"+action.label),()->{
                if(p.containerMenu!=menu||!valid.getAsBoolean())return;
                boolean changed=action==TableAction.RESET?table.resetTable(driver,revision):table.endGame(p,driver,revision);
                if(changed){
                    p.sendSystemMessage(Component.literal(action.success),true);
                    settings(p,table);
                }
            });
            menu.button(33,icon(Items.ARROW,"取消 · 返回设置"),()->settings(p,table));
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
    static boolean inventoryValid(ServerPlayer p,MahjongTableBlockEntity table){return tableMenuValid(p,table)&&host(p,table)&&!table.hasActiveMatch();}
    static void inventory(ServerPlayer p,MahjongTableBlockEntity table,int page){if(inventoryValid(p,table))p.openMenu(new SimpleMenuProvider((id,inv,player)->inventoryMenu(id,inv,table,page),Component.literal("牌桌库存 · "+(page+1)+" / 4")));}
    static final class Actions extends ChestMenu {
        final Map<Integer,Runnable> actions=new HashMap<>();final BooleanSupplier valid;
        Actions(int id,Inventory inv,BooleanSupplier valid){super(MenuType.GENERIC_9x6,id,inv,new SimpleContainer(54),6);this.valid=valid;}
        void button(int slot,ItemStack item,Runnable action){getContainer().setItem(slot,item);actions.put(slot,action);}
        @Override public void clicked(int slot,int button,ContainerInput type,Player p){if(p.containerMenu==this&&valid.getAsBoolean()&&type==ContainerInput.PICKUP&&actions.containsKey(slot))actions.get(slot).run();}
        @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player p){return valid.getAsBoolean();}
    }
    static final class InventoryPage extends ChestMenu {
        final MahjongTableBlockEntity table;final int page;final long revision;
        InventoryPage(int id,Inventory inv,MahjongTableBlockEntity table,int page){super(MenuType.GENERIC_9x6,id,inv,new PagedContainer(table,page),6);this.table=table;this.page=page;this.revision=table.matchRevision();}
        @Override public boolean stillValid(Player p){return p instanceof ServerPlayer player&&inventoryValid(player,table)&&table.matchRevision()==revision;}
        @Override public void clicked(int slot,int button,ContainerInput type,Player player){if(player.containerMenu!=this||!stillValid(player))return;if(slot>=45&&slot<54){if(player instanceof ServerPlayer p){if(slot==45&&page>0)inventory(p,table,page-1);if(slot==53&&page<3)inventory(p,table,page+1);if(slot==49)settings(p,table);}return;}super.clicked(slot,button,type,player);}
        @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    }
    static final class PagedContainer implements Container {
        final MahjongTableBlockEntity table;final int offset;final long revision;PagedContainer(MahjongTableBlockEntity table,int page){this.table=table;offset=page*45;revision=table.matchRevision();}
        int index(int slot){return slot>=0&&slot<45&&offset+slot<136?offset+slot:-1;}
        public int getContainerSize(){return 54;}public boolean isEmpty(){return table.isEmpty();}
        public ItemStack getItem(int slot){int i=index(slot);if(i>=0)return table.getItem(i);return switch(slot){case 45->icon(Items.ARROW,"上一页");case 49->icon(Items.BOOK,"设置");case 53->icon(Items.ARROW,"下一页");default->ItemStack.EMPTY;};}
        public ItemStack removeItem(int slot,int count){int i=index(slot);return i<0?ItemStack.EMPTY:table.removeItem(i,count);}
        public ItemStack removeItemNoUpdate(int slot){int i=index(slot);return i<0?ItemStack.EMPTY:table.removeItemNoUpdate(i);}
        public void setItem(int slot,ItemStack stack){int i=index(slot);if(i>=0)table.setItem(i,stack);}
        public void setChanged(){table.setChanged();}public boolean stillValid(Player p){return p instanceof ServerPlayer player&&inventoryValid(player,table)&&table.matchRevision()==revision;}
        public boolean canPlaceItem(int slot,ItemStack stack){return index(slot)>=0&&!table.hasActiveMatch();}
        public void clearContent(){}
    }
}
