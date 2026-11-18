package com.riichimahjong.fabric;

import com.riichimahjong.analysis.AnalysisMode;
import com.riichimahjong.analysis.PlayerAnalysisStore;
import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;

/** Private vanilla-client statistics menu, accessible independently of a nearby table. */
public final class AnalysisMenus {
    private AnalysisMenus() {}
    public static void open(ServerPlayer player) {
        open(player,AnalysisMode.of(RuleSetPreset.MAHJONG_SOUL_4P,MatchLength.SOUTH,false,false,false));
    }
    public static void open(ServerPlayer player,AnalysisMode mode) { open(player,mode,0); }
    private static void open(ServerPlayer player,AnalysisMode mode,int page) {
        var store=PlayerAnalysisStore.get();
        if(store==null) {
            player.sendSystemMessage(Component.literal("统计数据尚未就绪，请稍后再试。"),true);
            return;
        }
        var profile=store.profile(player.getUUID(),mode);
        var yaku=new ArrayList<>(profile.yaku().entrySet());
        yaku.sort(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        int lastPage=Math.max(0,(yaku.size()-1)/9),shownPage=Math.max(0,Math.min(page,lastPage));
        PortMenus.open(player,"玩家分析 · "+mode.preset().family().label(),()->PlayerAnalysisStore.get()==store,menu->{
            for(var family:RuleSetPreset.Family.values()) {
                boolean selected=family==mode.preset().family();
                var candidate=AnalysisMode.of(family.preset(mode.preset().playerCount()),mode.length(),mode.exchange(),mode.jokers(),mode.blood());
                menu.button(family.ordinal(),item(selected?Items.EMERALD:Items.PAPER,family.label()+(selected?" · 已选":"")),()->open(player,candidate));
            }
            menu.button(8,item(Items.CLOCK,"刷新统计"),()->open(player,mode,shownPage));
            menu.button(9,item(Items.COMPASS,mode.length().label(),"点击切换东风场 / 南风场"),()->open(player,
                    AnalysisMode.of(mode.preset(),mode.length()==MatchLength.EAST?MatchLength.SOUTH:MatchLength.EAST,mode.exchange(),mode.jokers(),mode.blood())));
            menu.button(10,item(Items.PLAYER_HEAD,mode.preset().playerCount()==3?"三麻":"四麻",
                    mode.preset().family().supportsThreePlayers()?"点击切换三麻 / 四麻":"此规则仅支持四麻"),()->open(player,
                    AnalysisMode.of(mode.preset().family().preset(mode.preset().playerCount()==4?3:4),mode.length(),mode.exchange(),mode.jokers(),mode.blood())));
            menu.button(11,item(mode.exchange()?Items.EMERALD:Items.GUNPOWDER,"换三张："+(mode.exchange()?"开":"关")),()->open(player,
                    AnalysisMode.of(mode.preset(),mode.length(),!mode.exchange(),mode.jokers(),mode.blood())));
            menu.button(12,item(mode.jokers()?Items.EMERALD:Items.GUNPOWDER,"赖子："+(mode.jokers()?"开":"关")),()->open(player,
                    AnalysisMode.of(mode.preset(),mode.length(),mode.exchange(),!mode.jokers(),mode.blood())));
            menu.button(13,item(mode.blood()?Items.EMERALD:Items.GUNPOWDER,"血战到底："+(mode.blood()?"开":"关")),()->open(player,
                    AnalysisMode.of(mode.preset(),mode.length(),mode.exchange(),mode.jokers(),!mode.blood())));
            menu.button(16,item(Items.BOOK,"统计范围",mode.label(),"胡牌、点炮和役种按每手结算。","一致率按完整东风场 / 南风场对局统计。",
                    "近 10 / 20 / 50 局均有有效分析时才显示。","仅无娱乐规则、全程真人操作、正常结束的","雀魂、公式战、M-League 对局累计积分。"),()->{});
            menu.button(18,item(Items.PAPER,"已结算手数："+profile.hands()),()->{});
            menu.button(19,item(Items.NETHER_STAR,"胡牌："+profile.wins()),()->{});
            menu.button(20,item(Items.GOLD_NUGGET,"自摸："+profile.tsumo()),()->{});
            menu.button(21,item(Items.IRON_NUGGET,"荣和："+profile.ron()),()->{});
            menu.button(22,item(Items.REDSTONE,"点炮："+profile.dealtIn()),()->{});
            menu.button(23,item(Items.CLOCK,"完成对局："+profile.matches()),()->{});
            if(mode.ranked())menu.button(24,item(Items.EMERALD,"累计积分："+points(profile.scoreTenths()),"积分最低为 0。"),()->{});
            int[] windows={10,20,50};
            for(int index=0;index<windows.length;index++) {
                int window=windows[index];var average=profile.average(window);
                if(average.isPresent())menu.button(27+index,item(Items.COMPARATOR,"近 "+window+" 局平均一致率："+score(average.getAsDouble())),()->{});
            }
            menu.button(35,item(Items.WRITABLE_BOOK,"胡牌役种 · "+(shownPage+1)+" / "+(lastPage+1),"次数表示包含该役种的胡牌次数。"),()->{});
            for(int index=0;index<9&&shownPage*9+index<yaku.size();index++) {
                var entry=yaku.get(shownPage*9+index);
                var icon=item(Items.PAPER,"");
                icon.set(DataComponents.CUSTOM_NAME,WinResultLabels.yakuName(entry.getKey()).copy().append(Component.literal(" × "+entry.getValue())));
                menu.button(36+index,icon,()->{});
            }
            if(shownPage>0)menu.button(45,item(Items.ARROW,"上一页役种"),()->open(player,mode,shownPage-1));
            menu.button(49,item(Items.BARRIER,"关闭"),player::closeContainer);
            if(shownPage<lastPage)menu.button(53,item(Items.ARROW,"下一页役种"),()->open(player,mode,shownPage+1));
        });
    }
    static String points(long tenths) {
        return String.format(Locale.ROOT,"%d.%d",tenths/10,Math.abs(tenths%10));
    }
    static String score(double value) { return String.format(Locale.ROOT,"%.2f",value); }
    private static ItemStack item(Item item,String name,String... lore) {
        var stack=PortMenus.icon(item,name);
        if(lore.length>0)stack.set(DataComponents.LORE,new ItemLore(Arrays.stream(lore).<Component>map(Component::literal).toList()));
        return stack;
    }
}
