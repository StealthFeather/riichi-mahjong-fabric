package com.riichimahjong.fabric;

import com.mojang.serialization.Codec;
import com.riichimahjong.mahjongcore.MahjongWinEffects;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.yaku.WinResult;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Public result metadata only. Private hands and rule drivers never enter display elements. */
public final class WinResultLabels {
    public record Result(int seat, int han, int fu, int points, int dora,
                         List<String> yaku, List<String> yakuman, boolean sichuan) {
        public Result {
            yaku=List.copyOf(yaku);yakuman=List.copyOf(yakuman);
        }
    }
    private record Visual(Result result, Vec3 position) {}
    private final ElementHolder holder;
    private final BlockPos pos;
    private final BlockState state;
    private final Map<Integer,TextDisplayElement> labels=new HashMap<>();
    private final Map<Integer,Visual> sent=new HashMap<>();

    WinResultLabels(ElementHolder holder,BlockPos pos,BlockState state) {
        this.holder=holder;this.pos=pos;this.state=state;
    }

    /** Capture exact accepted payments; total scores and other players' concealed tiles are excluded. */
    public static List<Result> captureResults(TheMahjongDriver driver) {
        if(driver==null)return List.of();
        var results=new ArrayList<Result>();
        if(driver.sichuanState()!=null) {
            for(var win:driver.sichuanState().wins())results.add(result(win.seat(),win.result(),true));
        } else if(driver.bloodBattleState()!=null) {
            for(var win:driver.bloodBattleState().wins())results.add(result(win.seat(),win.result(),false));
        } else if(driver.currentPhase() instanceof MatchPhase.RoundEnded ended) {
            for(var win:ended.winResults()) {
                // Normal riichi accepts one result per winner, whose gain is its positive delta.
                for(int seat=0;seat<win.pointDeltas().size();seat++)if(win.pointDeltas().get(seat)>0) {
                    results.add(result(seat,win,false));break;
                }
            }
        }
        return List.copyOf(results);
    }

    public static Result result(int seat,WinResult win,boolean sichuan) {
        if(seat<0||seat>=win.pointDeltas().size())throw new IllegalArgumentException("invalid winning seat");
        return new Result(seat,win.han(),win.fu(),win.pointDeltas().get(seat),win.doraCount(),
                win.yaku().stream().map(Enum::name).toList(),win.yakuman().stream().map(Enum::name).toList(),sichuan);
    }

    /** A result becomes visible only after its own hand presentation has completed. */
    static List<Result> visibleResults(List<Result> results,Set<Integer> completed,PublicTable board) {
        return results.stream().filter(result->completed.contains(result.seat())
                &&result.seat()>=0&&result.seat()<board.seats().size()
                &&!board.seats().get(result.seat()).revealed().isEmpty()).toList();
    }

    void update(MahjongTableBlockEntity table,PublicTable board) {
        var visible=visibleResults(table.winResultLabels(),table.completedWinPresentationSeats(),board);
        var used=new HashSet<Integer>();
        for(var result:visible) {
            int seat=result.seat();var hand=board.seats().get(seat);
            var location=position(pos,PortViews.outward(seat,state),hand.handSlots(),hand.handGapStarts());
            var visual=new Visual(result,location);used.add(seat);
            if(visual.equals(sent.get(seat)))continue;
            var display=labels.get(seat);
            if(display==null) {
                display=new TextDisplayElement();display.setBillboardMode(Display.BillboardConstraints.CENTER);
                display.setScale(new Vector3f(.18f));display.setLineWidth(220);
                display.setBackground(0xB0222222);display.setDisplaySize(3,3);
                display.setText(text(result));display.setOverridePos(location);
                labels.put(seat,display);holder.addElement(display);
            }else {
                display.setText(text(result));display.setOverridePos(location);
            }
            sent.put(seat,visual);
        }
        for(var iterator=labels.entrySet().iterator();iterator.hasNext();) {
            var entry=iterator.next();
            if(!used.contains(entry.getKey())) {
                holder.removeElement(entry.getValue());sent.remove(entry.getKey());iterator.remove();
            }
        }
    }

    /** Centre the floating result above that seat's flat winning hand in every table rotation. */
    static Vec3 position(BlockPos pos,Direction direction,List<Integer> slots,List<Integer> gaps) {
        double along=slots.isEmpty()?0:(TableLayout.handAlong(slots.getFirst(),gaps)
                +TableLayout.handAlong(slots.getLast(),gaps))/2;
        return TableLayout.handAnchor(pos,direction,along,true).add(0,.26,0);
    }

    static Component text(Result result) {
        String points=(result.points()>=0?"+":"")+result.points()+"点";
        var text=Component.literal(result.han()+"番"+(result.sichuan()?"":" "+result.fu()+"符")+" · "+points)
                .withStyle(ChatFormatting.GOLD);
        var names=new ArrayList<Component>();
        result.yakuman().forEach(name->names.add(yakuName(name)));
        result.yaku().forEach(name->names.add(yakuName(name)));
        if(!result.sichuan()&&result.dora()>0)
            names.add(Component.translatableWithFallback("riichi_mahjong.yaku.dora","宝牌")
                    .append(Component.literal(" ×"+result.dora())));
        for(int index=0;index<names.size();index++)
            text.append(Component.literal(index%3==0?"\n":" · ").withStyle(ChatFormatting.WHITE))
                    .append(names.get(index).copy().withStyle(ChatFormatting.WHITE));
        return text;
    }

    static Component yakuName(String name) {
        String sc=switch(name) {
            case "SC_PINGHU"->"平胡";case "SC_PENGPENGHU"->"碰碰胡";case "SC_QIDUI"->"七对";
            case "SC_LONGQIDUI"->"龙七对";case "SC_QINGYISE"->"清一色";case "SC_JINGOUDIAO"->"金钩钓";
            case "SC_JIANGDUI"->"将对";case "SC_ZIMO"->"自摸";case "SC_GANGSHANGHUA"->"杠上花";
            case "SC_QIANGGANGHU"->"抢杠胡";case "SC_HAIDI"->"海底";case "SC_GEN"->"根";default->null;
        };
        if(sc!=null)return Component.literal(sc);
        String key=switch(name) {
            case "IIPEIKO"->"iipeikou";case "SEAT_WIND"->"yakuhai_jikaze";case "ROUND_WIND"->"yakuhai_bakaze";
            case "ITTSU"->"ikkitsuukan";case "CHIITOI"->"chiitoitsu";case "CHIHOU"->"chiihou";
            default->MahjongWinEffects.canonicalYakuKey(name);
        };
        return Component.translatableWithFallback("riichi_mahjong.yaku."+key,fallback(name));
    }

    private static String fallback(String name) {
        return switch(name) {
            case "RIICHI"->"立直";case "DOUBLE_RIICHI"->"两立直";case "IPPATSU"->"一发";
            case "MENZEN_TSUMO"->"门前清自摸和";case "PINFU"->"平和";case "IIPEIKO"->"一杯口";
            case "TANYAO"->"断幺九";case "HAKU"->"役牌 白";case "HATSU"->"役牌 发";case "CHUN"->"役牌 中";
            case "SEAT_WIND"->"自风牌";case "ROUND_WIND"->"场风牌";case "HAITEI"->"海底摸月";
            case "HOUTEI"->"河底捞鱼";case "RINSHAN_KAIHOU"->"岭上开花";case "CHANKAN"->"抢杠";
            case "CHANTA"->"混全带幺九";case "HONROUTOU"->"混老头";case "SANSHOKU_DOUJUN"->"三色同顺";
            case "ITTSU"->"一气通贯";case "TOITOI"->"对对和";case "SANSHOKU_DOUKOU"->"三色同刻";
            case "SANANKOU"->"三暗刻";case "SANKANTSU"->"三杠子";case "SHOUSANGEN"->"小三元";
            case "CHIITOI"->"七对子";case "RYANPEIKOU"->"二杯口";case "JUNCHAN"->"纯全带幺九";
            case "HONITSU"->"混一色";case "CHINITSU"->"清一色";case "KOKUSHIMUSOU"->"国士无双";
            case "SUUANKOU"->"四暗刻";case "CHUURENPOUTOU"->"九莲宝灯";case "DAISANGEN"->"大三元";
            case "TSUISOU"->"字一色";case "SHOUSUUSHI"->"小四喜";case "DAISUUSHI"->"大四喜";
            case "RYUUIISOU"->"绿一色";case "CHINROUTOU"->"清老头";case "SUUKANTSU"->"四杠子";
            case "RENHOU"->"人和";case "CHIHOU"->"地和";case "TENHOU"->"天和";
            default->name;
        };
    }

    public static void write(ValueOutput out,List<Result> results) {
        var entries=out.childrenList("results");
        for(var result:results) {
            var entry=entries.addChild();entry.putInt("seat",result.seat());entry.putInt("han",result.han());
            entry.putInt("fu",result.fu());entry.putInt("points",result.points());entry.putInt("dora",result.dora());
            entry.putBoolean("sichuan",result.sichuan());
            var yaku=entry.list("yaku",Codec.STRING);result.yaku().forEach(yaku::add);
            var yakuman=entry.list("yakuman",Codec.STRING);result.yakuman().forEach(yakuman::add);
        }
    }
    public static List<Result> read(ValueInput in) {
        var results=new ArrayList<Result>();
        for(var entry:in.childrenListOrEmpty("results")) {
            int seat=entry.getIntOr("seat",-1);if(seat<0||seat>=4)continue;
            var yaku=new ArrayList<String>();entry.listOrEmpty("yaku",Codec.STRING).forEach(yaku::add);
            var yakuman=new ArrayList<String>();entry.listOrEmpty("yakuman",Codec.STRING).forEach(yakuman::add);
            results.add(new Result(seat,entry.getIntOr("han",0),entry.getIntOr("fu",0),entry.getIntOr("points",0),
                    entry.getIntOr("dora",0),yaku,yakuman,entry.getBooleanOr("sichuan",false)));
        }
        return List.copyOf(results);
    }

    void destroy() {
        labels.values().forEach(holder::removeElement);labels.clear();sent.clear();
    }
}
