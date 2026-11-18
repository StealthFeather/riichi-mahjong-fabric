package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MatchLength;
import com.riichimahjong.mahjongtable.RuleSetPreset;
import com.themahjong.TheMahjongTile;
import com.themahjong.driver.MahjongHumanPlayer;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.variants.BloodBattleState;
import com.themahjong.yaku.NonYakuman;
import com.themahjong.yaku.WinResult;
import com.themahjong.yaku.Yakuman;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Result text follows the accepted per-win payment and each hand's completed public presentation. */
public final class WinResultLabelsRegression {
    private static final WinResult FIRST=new WinResult(List.of(NonYakuman.RIICHI,NonYakuman.PINFU),List.of(),
            3,30,1,List.of(5900,-2000,-2000,-1900));
    private static final WinResult SECOND=new WinResult(List.of(NonYakuman.MENZEN_TSUMO),List.of(),
            2,30,1,List.of(0,3000,-1500,-1500));

    private static PublicTable board(Set<Integer> publicHands) {
        var seats=new ArrayList<PublicTable.Seat>();
        var slots=IntStream.range(0,14).boxed().toList();
        for(int seat=0;seat<4;seat++)seats.add(new PublicTable.Seat(null,"BOT",seat,25000,14,slots,List.of(13),
                publicHands.contains(seat)?List.of(1,2,3):List.of(),List.of(),List.of(),0,false,false,true));
        return new PublicTable(List.copyOf(seats),30,0,List.of(),List.of(),Set.of(),"", "东一局",0,0,null,1,136,false,52);
    }

    private static void captureExactPaymentsAndOrder() {
        var match=RuleSetPreset.MAHJONG_SOUL_4P.newMatch(MatchLength.SOUTH,false,false,true);
        List<MahjongPlayerInterface> humans=Stream.generate(()->(MahjongPlayerInterface)new MahjongHumanPlayer()).limit(4).toList();
        var driver=new TheMahjongDriver(match,humans,new Random(73));driver.startMatch();
        var tile=new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true);
        driver.restoreBloodBattleState(new BloodBattleState.Snapshot(List.of(
                new BloodBattleState.Win(0,tile,true,FIRST,0),new BloodBattleState.Win(1,tile,true,SECOND,1))));
        var results=WinResultLabels.captureResults(driver);
        check(results.size()==2&&results.get(0).seat()==0&&results.get(1).seat()==1,
                "floating labels lost the accepted winner order");
        check(results.get(0).points()==5900&&results.get(1).points()==3000,
                "labels replaced a later winner's actual gain with total scores or another win's payment");
        check(results.get(1).han()==2&&results.get(1).fu()==30&&results.get(1).yaku().equals(List.of("MENZEN_TSUMO")),
                "a later winner inherited the first winner's han/fu/yaku");
        check(WinResultLabels.visibleResults(results,Set.of(),board(Set.of(0,1))).isEmpty(),
                "blood-battle result text appeared before its queued animation completed");
        check(WinResultLabels.visibleResults(results,Set.of(0),board(Set.of(0,1))).equals(List.of(results.getFirst())),
                "finishing one hand's animation exposed another winner's result early");
        check(WinResultLabels.visibleResults(results,Set.of(0,1),board(Set.of(0))).equals(List.of(results.getFirst())),
                "a hidden hand received a floating public result");
        check(WinResultLabels.visibleResults(results,Set.of(0,1),board(Set.of(0,1))).equals(results),
                "completed visible winners lost their final result labels");
    }

    private static void savedResultsAndLocalization() {
        var results=List.of(WinResultLabels.result(0,FIRST,false),WinResultLabels.result(1,SECOND,false),
                WinResultLabels.result(2,new WinResult(List.of(NonYakuman.SC_PINGHU,NonYakuman.SC_ZIMO),List.of(),
                        1,0,0,List.of(-1000,-1000,2000,0)),true));
        var output=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        WinResultLabels.write(output,results);
        var input=TagValueInput.create(ProblemReporter.DISCARDING,HolderLookup.Provider.create(Stream.empty()),output.buildResult());
        check(WinResultLabels.read(input).equals(results),"terminal save/reload lost per-winner floating labels");
        var text=WinResultLabels.text(results.getFirst()).getString();
        check(text.contains("3番")&&text.contains("30符")&&text.contains("+5900点")&&text.contains("立直")&&text.contains("平和"),
                "floating result did not contain its actual han/fu/gain/localized yaku");
        check(!text.contains("25000"),"label showed the score total in place of this win's gain");
        check(WinResultLabels.text(results.get(2)).getString().contains("平胡")
                        &&!WinResultLabels.text(results.get(2)).getString().contains("0符"),
                "Sichuan floating labels lost their names or acquired riichi fu");
        for(var yaku:NonYakuman.values())
            check(!WinResultLabels.yakuName(yaku.name()).getString().equals(yaku.name()),"untranslated yaku: "+yaku);
        for(var yakuman:Yakuman.values())
            check(!WinResultLabels.yakuName(yakuman.name()).getString().equals(yakuman.name()),"untranslated yakuman: "+yakuman);
        var invalid=TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        var entry=invalid.childrenList("results").addChild();entry.putInt("seat",7);
        check(WinResultLabels.read(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()),invalid.buildResult())).isEmpty(),"malformed saved seat created an orphan label");
    }

    private static void positions() {
        var slots=IntStream.range(0,14).boxed().toList();
        for(var pos:List.of(BlockPos.ZERO,new BlockPos(-1200,70,4900)))for(var direction:Direction.Plane.HORIZONTAL) {
            var result=WinResultLabels.position(pos,direction,slots,List.of(13));
            double left=TableLayout.handAlong(0,List.of(13)),right=TableLayout.handAlong(13,List.of(13));
            var midpoint=TableLayout.handAnchor(pos,direction,(left+right)/2,true);
            check(Math.abs(result.x-midpoint.x)<1e-7&&Math.abs(result.z-midpoint.z)<1e-7,
                    "result label moved away from its own winning hand in direction "+direction);
            check(result.y>pos.getY()+TableLayout.TABLE_TOP+.2&&Math.abs(result.y-midpoint.y-.26)<1e-7,
                    "result label intersects its winning hand or lost its floating height");
        }
    }

    public static void main(String[] args) {
        captureExactPaymentsAndOrder();savedResultsAndLocalization();positions();
        check(new TableHud().bars.size()==3,"combat HUD retained a fourth bar for han/points/yaku");
        System.out.println("PASS: floating per-winner results use actual payments/localized yaku above the correct hand, wait for presentation completion, survive terminal NBT and leave only battle/score BossBars");
    }
}
