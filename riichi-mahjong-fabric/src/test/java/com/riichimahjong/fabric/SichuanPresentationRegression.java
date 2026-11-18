package com.riichimahjong.fabric;

import com.riichimahjong.mahjongtable.MahjongTableHumanPlayer.TableButton;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTile.Suit;
import com.themahjong.driver.MahjongHumanPlayer;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.PlayerAction;
import com.themahjong.driver.TheMahjongDriver;
import com.themahjong.sichuan.SichuanRules;
import com.themahjong.sichuan.SichuanState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import static com.riichimahjong.fabric.RulesRegression.check;

/** Presentation keeps a fixed left joker while real discards, resumed hands and private controls remain usable. */
public final class SichuanPresentationRegression {
    private static final TheMahjongTile JOKER = tile(Suit.DRAGON, 3);
    private static final TheMahjongTile DRAW = tile(Suit.PINZU, 2);

    private static TheMahjongTile tile(Suit suit, int rank) {
        return new TheMahjongTile(suit, rank, false);
    }

    private static List<MahjongPlayerInterface> humans() {
        return Stream.generate(() -> (MahjongPlayerInterface)new MahjongHumanPlayer()).limit(4).toList();
    }

    private static List<TheMahjongTile> initialHand() {
        return List.of(JOKER, tile(Suit.MANZU, 1), tile(Suit.MANZU, 1), tile(Suit.MANZU, 2),
                tile(Suit.MANZU, 3), tile(Suit.MANZU, 4), tile(Suit.MANZU, 5), tile(Suit.MANZU, 6),
                tile(Suit.PINZU, 2), tile(Suit.PINZU, 3), tile(Suit.PINZU, 4),
                tile(Suit.PINZU, 5), tile(Suit.PINZU, 6));
    }

    private static TheMahjongDriver fixture() {
        var base = SichuanRules.newMatch(2, false, true);
        var players = new ArrayList<TheMahjongPlayer>();
        for (int seat = 0; seat < 4; seat++) {
            var hand = new ArrayList<>(initialHand());
            if (seat == 0) hand.add(DRAW);
            players.add(new TheMahjongPlayer(25000, TheMahjongPlayer.RiichiState.NONE, false,
                    hand, List.of(), List.of(), List.of()));
        }
        var round = new TheMahjongRound(TheMahjongTile.Wind.EAST, 1, 0, 0, 0,
                TheMahjongRound.State.TURN, 0, -1, TheMahjongRound.ActiveTile.drawn(DRAW),
                Collections.nCopies(30, tile(Suit.PINZU, 8)), List.of(), List.of(), List.of(), 0,
                players, List.of());
        var match = new TheMahjongMatch(4, base.startingPoints(), base.targetPoints(), 2,
                TheMahjongMatch.State.IN_ROUND, base.tileSet(), base.ruleSet(), List.of(), round);
        var driver = new TheMahjongDriver(match, humans(), new Random(14),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0), false, OptionalInt.empty()));
        driver.restoreSichuanState(new SichuanState.Snapshot(SichuanState.Stage.PLAY, 1,
                List.of(List.of(), List.of(), List.of(), List.of()), Set.of(),
                List.of(2, 2, 2, 2), List.of(), List.of(), false, -1));
        return driver;
    }

    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(out, driver);
        var in = TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), out.buildResult());
        return DriverNbt.readDriver(in, humans(), new Random(14));
    }

    private static List<Integer> slots(int count) {
        return IntStream.range(0, count).boxed().toList();
    }

    private static void mappings(HandDisplayState display, TheMahjongRound round) {
        for (int seat = 0; seat < 4; seat++) {
            check(display.hand(seat).size() == display.slots(seat).size()
                            && new HashSet<>(display.slots(seat)).size() == display.slots(seat).size(),
                    "the pinned hand lost a slot or overlapped two physical tiles");
            for (int shown = 0; shown < display.hand(seat).size(); shown++) {
                int canonical = display.canonicalIndex(seat, shown, round);
                check(canonical >= 0 && round.handDisplayOrder(seat).get(canonical).equals(display.hand(seat).get(shown)),
                        "a pinned/sparse hand click selected a different physical tile identity");
            }
            check(display.canonicalIndex(seat, -1, round) == -1
                            && display.canonicalIndex(seat, 99, round) == -1,
                    "an out-of-range displayed hand index selected a tile");
        }
    }

    private static void pinnedJokerAndDraw() {
        var driver = fixture();
        var round = driver.currentRound();
        var raw = round.handDisplayOrder(0);
        var display = new HandDisplayState();
        display.update(round, 100, true);
        for (int seat = 0; seat < 4; seat++) {
            check(display.hand(seat).getFirst().equals(JOKER) && display.slots(seat).getFirst() == 0,
                    "the fixed joker was not the leftmost tile at seat " + seat);
        }
        check(round.handDisplayOrder(0).equals(raw) && raw.indexOf(JOKER) > 0,
                "pinning the joker rewrote the rule library's canonical hand order");
        check(display.canonicalIndex(0, 0, round) == raw.indexOf(JOKER),
                "clicking the leftmost joker did not map back to its actual rule index");
        check(display.hand(0).getLast().equals(DRAW) && display.separatedSlot(0) == 13
                        && display.gapStarts(0).equals(List.of(13)),
                "pinning the joker moved the newly drawn ordinary tile away from its rightmost gap");
        double normalStep = TableLayout.handAlong(12, List.of()) - TableLayout.handAlong(11, List.of());
        double drawStep = TableLayout.handAlong(13, display.gapStarts(0))
                - TableLayout.handAlong(12, display.gapStarts(0));
        check(drawStep > normalStep, "the rightmost drawn tile lost its visible spacing increment");
        mappings(display, round);

        var riichiDisplay = new HandDisplayState();
        riichiDisplay.update(round, 100, false);
        check(riichiDisplay.hand(0).equals(raw), "disabling the Sichuan pin changed normal riichi ordering");
    }

    private static void delayedHoleAndResume() {
        var driver = fixture();
        var display = new HandDisplayState();
        display.update(driver.currentRound(), 100, true);
        var before = display.hand(0);
        var duplicate = tile(Suit.MANZU, 1);
        int selected = before.lastIndexOf(duplicate);
        check(selected > before.indexOf(duplicate), "the discard fixture did not have two equal ordinary copies");
        display.recordDiscardSlot(0, selected);
        var action = new PlayerAction.Discard(duplicate);
        check(driver.legalActions(0).contains(action), "the ordinary presentation fixture discard was illegal");
        driver.submitAction(0, action);
        driver.advance(.05);
        display.update(driver.currentRound(), 101, true);
        var expectedHand = new ArrayList<>(before); expectedHand.remove(selected);
        var expectedSlots = new ArrayList<>(slots(14)); expectedSlots.remove(selected);
        check(display.hand(0).equals(expectedHand) && display.slots(0).equals(expectedSlots),
                "an accepted equal-copy discard emptied a different slot or sorted surviving tiles early");
        check(display.hand(0).getFirst().equals(JOKER) && display.slots(0).getFirst() == 0
                        && display.separatedSlot(0) == 13 && display.gapStarts(0).equals(List.of(13)),
                "the delayed ordinary discard hole displaced the pinned joker or retained drawn tile");
        mappings(display, driver.currentRound());
        display.update(driver.currentRound(), 110, true);
        check(display.slots(0).equals(expectedSlots), "the ordinary discard hole closed before ten ticks");
        display.update(driver.currentRound(), 111, true);
        check(display.slots(0).equals(slots(13)) && display.gapStarts(0).isEmpty()
                        && display.hand(0).getFirst().equals(JOKER),
                "the ten-tick sort failed to compact ordinary tiles while retaining the leftmost joker");
        mappings(display, driver.currentRound());

        var restored = reload(driver);
        var resumed = new HandDisplayState();
        resumed.update(restored.currentRound(), 200, true);
        check(restored.currentRound().equals(driver.currentRound())
                        && restored.sichuanState().snapshot().equals(driver.sichuanState().snapshot()),
                "presentation-resume NBT changed a real accepted discard or the Sichuan session");
        for (int seat = 0; seat < 4; seat++) {
            check(resumed.hand(seat).getFirst().equals(JOKER) && resumed.slots(seat).getFirst() == 0,
                    "a resumed Sichuan hand failed to restore its derived leftmost joker position");
        }
        mappings(resumed, restored.currentRound());
    }

    private static void preparationLabels() {
        var mutable = new ArrayList<TheMahjongTile>();
        mutable.add(tile(Suit.MANZU, 2));
        var button = new TableButton.ConfirmExchange(mutable);
        mutable.add(tile(Suit.MANZU, 3));
        check(PrivateHand.label(button).equals("确认换牌（1/3）"),
                "an exchange-confirm button mutated after its visible selection was captured");
        check(PrivateHand.label(new TableButton.ConfirmExchange(List.of())).equals("确认换牌（0/3）")
                        && PrivateHand.label(new TableButton.ConfirmExchange(initialHand().subList(1, 4)))
                                .equals("确认换牌（3/3）"),
                "exchange confirmation did not display its current selected-tile count");
        check(PrivateHand.label(new TableButton.ClearExchange()).equals("重新选择"),
                "the exchange reset control has the wrong visible label");
        for (var suit : List.of(Suit.MANZU, Suit.PINZU, Suit.SOUZU)) {
            var expected = switch (suit) { case MANZU -> "缺万"; case PINZU -> "缺筒"; case SOUZU -> "缺索"; default -> throw new AssertionError(); };
            var choice = new TableButton.ChooseVoidSuit(suit);
            check(PrivateHand.label(choice).equals(expected) && PrivateHand.shortLabel(choice).equals(expected),
                    "the dingque control named a different suit");
        }
    }

    private static void isolatedJokerModel() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
                .forEach(components -> components.apply());
        check(PortViews.tileModel(33, true).equals("bridge/tile_joker"),
                "the Sichuan wildcard did not select the isolated logo model");
        check(PortViews.tileModel(33, false).equals("bridge/tile_dragon_red"),
                "a riichi red dragon inherited the Sichuan logo model");
        check(PortViews.tileModel(-1, true).equals("bridge/tile_back")
                        && PortViews.tileModel(-1, false).equals("bridge/tile_back"),
                "a concealed tile back inherited the Sichuan wildcard identity");
        var model = PortViews.model(PortViews.tileModel(33, true));
        check(Identifier.fromNamespaceAndPath("riichi_mahjong", "bridge/tile_joker")
                        .equals(model.get(DataComponents.ITEM_MODEL)),
                "the vanilla item-model carrier lost the dedicated joker resource identifier");
    }

    public static void main(String[] args) {
        pinnedJokerAndDraw();
        delayedHoleAndResume();
        preparationLabels();
        isolatedJokerModel();
        System.out.println("PASS: Sichuan jokers stay left, normal draws stay separated right, and sparse ordinary discards map to the correct rule identities");
        System.out.println("PASS: ten-tick ordinary discard holes preserve the joker, and real driver NBT restores the derived pinned hand");
        System.out.println("PASS: exchange/dingque labels preserve visible selection counts, and only Sichuan red-dragon identities use the logo model");
    }
}
