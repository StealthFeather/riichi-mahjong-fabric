package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.driver.*;
import java.util.*;
import static com.riichimahjong.fabric.RulesRegression.*;

/** Keep the declaration position visible even when its original tile was called. */
public final class RiichiRiverRegression {
    private static TheMahjongPlayer riverPlayer(List<TheMahjongDiscard> river) {
        return new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.RIICHI,false,
                List.of(),List.of(),river,List.of());
    }

    private static TheMahjongPlayer withMelds(List<TheMahjongMeld> melds) {
        return new TheMahjongPlayer(25000,TheMahjongPlayer.RiichiState.NONE,false,
                List.of(),melds,List.of(),List.of());
    }

    private static void actualCalledDeclaration() {
        var first = new ArrayList<>(waiting(0));first.add(tile(4,2));
        var caller = new ArrayList<>(waiting(1));caller.set(0,tile(4,2));caller.set(1,tile(4,2));
        var driver = driver(List.of(player(first),player(caller),player(waiting(2)),player(waiting(0))),
                Collections.nCopies(30,tile(4,2)));
        act(driver,0,new PlayerAction.DiscardWithRiichi(tile(4,2)));
        check(RiverDisplay.capture(driver.currentRound().players(),0).getFirst().sideways(),
                "an uncalled declaration must stay sideways during its claim window");
        var pon = driver.legalActions(1).stream().filter(a -> a instanceof PlayerAction.Pon).findFirst().orElseThrow();
        act(driver,1,pon);passClaims(driver);
        check(RiverDisplay.capture(driver.currentRound().players(),0).isEmpty(),
                "the called declaration remained visible in its original river");
        var discard = driver.legalActions(1).stream().filter(a -> a instanceof PlayerAction.Discard).findFirst().orElseThrow();
        act(driver,1,discard);passClaims(driver);
        for (int seat : new int[]{2,3,0}) {
            act(driver,seat,PlayerAction.Draw.INSTANCE);
            act(driver,seat,new PlayerAction.Discard(tile(4,2)));passClaims(driver);
        }
        var river = RiverDisplay.capture(driver.currentRound().players(),0);
        check(river.size()==1 && river.getFirst().sideways(),
                "the first accepted discard after a called riichi declaration was not sideways");
        var original = driver.currentRound().players().get(0);
        check(original.discards().getFirst().riichiDeclared() && !original.discards().get(1).riichiDeclared(),
                "display correction changed the scoring engine's original declaration metadata");
        check(original.points()==24000 && driver.currentRound().riichiSticks()==1,
                "display correction changed the accepted riichi deposit");
        for (int seat : new int[]{1,2,3,0}) {
            act(driver,seat,PlayerAction.Draw.INSTANCE);
            act(driver,seat,new PlayerAction.Discard(tile(4,2)));passClaims(driver);
        }
        river = RiverDisplay.capture(driver.currentRound().players(),0);
        check(river.size()==2 && river.getFirst().sideways() && !river.getLast().sideways(),
                "a transferred declaration marker repeated on later discards");
    }

    private static void everyCallAndSeat() {
        var declared = tile(0,3);
        var red = new TheMahjongTile(TheMahjongTile.Suit.PINZU,5,true);
        var discards = List.of(new TheMahjongDiscard(tile(1,1),false),
                new TheMahjongDiscard(declared,true),new TheMahjongDiscard(red,false),
                new TheMahjongDiscard(tile(2,4),false));
        for (int count : new int[]{3,4}) for (int source=0; source<count; source++) {
            var pon = new TheMahjongMeld.Pon(Collections.nCopies(3,declared),0,source,1);
            List<TheMahjongMeld> calls = List.of(
                    new TheMahjongMeld.Chi(List.of(tile(0,1),tile(0,2),declared),2,source,1),
                    pon,new TheMahjongMeld.Daiminkan(Collections.nCopies(4,declared),0,source,1),
                    new TheMahjongMeld.Kakan(pon,declared));
            for (var call : calls) {
                var players = new ArrayList<TheMahjongPlayer>(Collections.nCopies(count,TheMahjongPlayer.initial(25000)));
                players.set(source,riverPlayer(discards));
                players.set((source+1)%count,withMelds(List.of(call)));
                var river = RiverDisplay.capture(players,source);
                check(river.size()==3 && !river.getFirst().sideways() && river.get(1).sideways()
                                && !river.getLast().sideways(),
                        "called declaration did not transfer exactly once for "+count+"p seat "+source+" "+call.getClass().getSimpleName());
                check(river.get(1).code()==35,"the transferred red-five discard lost its red identity");
                check(players.get(source).discards().equals(discards),"rendering mutated the original river");
                players.set((source+1)%count,TheMahjongPlayer.initial(25000));
                river = RiverDisplay.capture(players,source);
                check(river.size()==4 && !river.getFirst().sideways() && river.get(1).sideways()
                                && !river.get(2).sideways() && !river.getLast().sideways(),
                        "an uncalled declaration moved its original sideways marker");
            }
        }
    }

    private static void consecutiveCalledSuccessors() {
        var red = new TheMahjongTile(TheMahjongTile.Suit.PINZU,5,true);
        var discards = List.of(new TheMahjongDiscard(tile(0,3),true),
                new TheMahjongDiscard(red,false),new TheMahjongDiscard(tile(2,4),false),
                new TheMahjongDiscard(tile(2,5),false));
        var players = List.of(riverPlayer(discards),
                withMelds(List.of(new TheMahjongMeld.Chi(List.of(tile(0,1),tile(0,2),tile(0,3)),2,0,0))),
                withMelds(List.of(new TheMahjongMeld.Pon(List.of(red,tile(1,5),tile(1,5)),0,0,1))),
                withMelds(List.of(new TheMahjongMeld.Daiminkan(Collections.nCopies(4,tile(2,4)),0,0,2))));
        var river = RiverDisplay.capture(players,0);
        check(river.size()==1 && river.getFirst().sideways() && river.getFirst().code()==22,
                "the marker stopped at an already called successor instead of the next visible discard");
        var pending = riverPlayer(discards.subList(0,3));
        var beforeNextDiscard = new ArrayList<>(players);beforeNextDiscard.set(0,pending);
        check(RiverDisplay.capture(beforeNextDiscard,0).isEmpty(),
                "a pending declaration marker invented a river tile before the next discard");
        var normal = new ArrayList<>(discards);
        normal.set(0,new TheMahjongDiscard(tile(0,3),false));
        var withoutDeclaration = new ArrayList<>(players);withoutDeclaration.set(0,riverPlayer(normal));
        check(RiverDisplay.capture(withoutDeclaration,0).stream().noneMatch(PublicTable.Tile::sideways),
                "calling an ordinary discard created a riichi marker");
    }

    public static void main(String[] args) {
        actualCalledDeclaration();
        everyCallAndSeat();
        consecutiveCalledSuccessors();
        System.out.println("PASS: accepted called riichi transfers its sideways marker to the next visible discard once");
        System.out.println("PASS: chi/pon/open-kan/added-kan calls, every 3p/4p seat, red fives and repeated called successors preserve river identity");
    }
}
