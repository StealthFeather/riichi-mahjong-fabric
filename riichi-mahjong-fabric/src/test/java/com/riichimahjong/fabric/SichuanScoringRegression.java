package com.riichimahjong.fabric;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTile.Suit;
import com.themahjong.sichuan.SichuanScoring;
import com.themahjong.yaku.NonYakuman;
import com.themahjong.yaku.WinResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Actual Sichuan shape/payment checks, independent of riichi's yaku and furiten. */
public final class SichuanScoringRegression {
    private static TheMahjongTile tile(Suit suit, int rank) { return new TheMahjongTile(suit, rank, false); }
    private static List<TheMahjongTile> hand(String man, String pin, String sou, int jokers) {
        var result = new ArrayList<TheMahjongTile>();
        String[] digits = {man, pin, sou};
        for (int suit = 0; suit < 3; suit++) for (char digit : digits[suit].toCharArray())
            result.add(tile(Suit.values()[suit], digit - '0'));
        for (int i = 0; i < jokers; i++) result.add(tile(Suit.DRAGON, 3));
        return result;
    }
    private static TheMahjongPlayer player(List<TheMahjongTile> hand, List<TheMahjongMeld> melds) {
        return new TheMahjongPlayer(25000, TheMahjongPlayer.RiichiState.NONE, false,
                hand, melds, List.of(), List.of());
    }
    private static TheMahjongRound round(List<TheMahjongTile> hand, List<TheMahjongMeld> melds) {
        var player = player(hand, melds);
        var other = player(hand("123456789", "1234", "", 0), List.of());
        return new TheMahjongRound(TheMahjongTile.Wind.EAST, 1, 0, 0, 0,
                TheMahjongRound.State.TURN, 0, -1, TheMahjongRound.ActiveTile.drawn(hand.getLast()),
                Collections.nCopies(12, tile(Suit.MANZU, 1)),
                List.of(), Collections.nCopies(5, tile(Suit.MANZU, 1)),
                Collections.nCopies(5, tile(Suit.MANZU, 1)), 1,
                List.of(player, other, other, other), List.of());
    }
    private static WinResult win(List<TheMahjongTile> hand, Set<Integer> winners, boolean jokers) {
        return SichuanScoring.tryWin(round(hand, List.of()), 0, 0, hand.getLast(), Suit.SOUZU,
                winners, jokers, false, false).orElseThrow(() -> new AssertionError("valid Sichuan hand rejected"));
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void baseWinAndPayments() {
        var complete = hand("12345699", "234678", "", 0);
        var drawn = win(complete, Set.of(), false);
        check(drawn.han() == 1 && drawn.yaku().contains(NonYakuman.SC_PINGHU), "ordinary hand needs no riichi yaku");
        check(drawn.pointDeltas().equals(List.of(6000, -2000, -2000, -2000)), "self draw did not charge every active opponent equally");
        var waiting = new ArrayList<>(complete);
        var last = waiting.removeLast();
        var ron = SichuanScoring.tryWin(round(waiting, List.of()), 0, 2, last, Suit.SOUZU,
                Set.of(1), false, false, false).orElseThrow();
        check(ron.han() == 0 && ron.pointDeltas().equals(List.of(1000, 0, -1000, 0)), "ron must charge only the discarder");
        var continued = win(complete, Set.of(1, 3), false);
        check(continued.pointDeltas().equals(List.of(2000, 0, -2000, 0)), "retired winners paid into a later self draw");
        check(SichuanScoring.tryWin(round(complete, List.of()), 0, 0, last, Suit.SOUZU,
                Set.of(0), false, false, false).isEmpty(), "a retired player won twice");
    }

    private static void missingSuitAndJokers() {
        var complete = hand("12345699", "234678", "", 0);
        check(SichuanScoring.tryWin(round(complete, List.of()), 0, 0, complete.getLast(), Suit.MANZU,
                Set.of(), false, false, false).isEmpty(), "a hand containing the missing suit was allowed to win");
        check(SichuanScoring.tryWin(round(complete, List.of()), 0, 0, complete.getLast(), null,
                Set.of(), false, false, false).isEmpty(), "a hand won before declaring its missing suit");
        var withJokers = hand("134569", "23467", "", 3);
        check(win(withJokers, Set.of(), true).han() >= 1, "jokers did not complete a sequence and pair");
        check(SichuanScoring.tryWin(round(withJokers, List.of()), 0, 0, withJokers.getLast(), Suit.SOUZU,
                Set.of(), false, false, false).isEmpty(), "red dragons were wild while the option was off");
        var wrong = hand("123456789", "2345", "", 1);
        check(SichuanScoring.tryWin(round(wrong, List.of()), 0, 0, wrong.getLast(), Suit.SOUZU,
                Set.of(), false, false, false).isEmpty(), "an ordinary red dragon was treated as a usable honor");
        var fiveCopies = hand("11111567", "234678", "", 0);
        check(SichuanScoring.tryWin(round(fiveCopies, List.of()), 0, 0, fiveCopies.getLast(), Suit.SOUZU,
                Set.of(), false, false, false).isEmpty(), "a fifth natural copy was accepted");
    }

    private static void scoringShapesAndLimits() {
        var pairs = win(hand("112233", "44556677", "", 0), Set.of(), false);
        check(pairs.yaku().contains(NonYakuman.SC_QIDUI) && pairs.han() == 3, "seven pairs was not recognized/scored");
        var dragonPairs = win(hand("11112233", "445566", "", 0), Set.of(), false);
        check(dragonPairs.yaku().contains(NonYakuman.SC_LONGQIDUI) && dragonPairs.han() == 4,
                "a natural quad must count as two pairs and make dragon seven pairs");
        var triples = win(hand("111222", "44466699", "", 0), Set.of(), false);
        check(triples.yaku().contains(NonYakuman.SC_PENGPENGHU) && triples.han() == 2, "all triplets not recognized");
        var jiang = win(hand("222555", "22255588", "", 0), Set.of(), false);
        check(jiang.yaku().contains(NonYakuman.SC_JIANGDUI) && jiang.han() == 4, "2/5/8 triplets not recognized");
        var pure = win(hand("11223345556789", "", "", 0), Set.of(), false);
        check(pure.yaku().contains(NonYakuman.SC_QINGYISE) && pure.han() == 3, "pure single suit was not recognized");
        var cap = win(hand("11112233445566", "", "", 0), Set.of(), false);
        check(cap.han() == SichuanScoring.MAX_FAN && cap.pointDeltas().get(1) == -16000,
                "fan cap did not limit the strongest hand's payment");
        var melds = List.<TheMahjongMeld>of(
                new TheMahjongMeld.Pon(hand("111", "", "", 0), 0, 1, 0),
                new TheMahjongMeld.Pon(hand("222", "", "", 0), 0, 2, 0),
                new TheMahjongMeld.Pon(hand("", "444", "", 0), 0, 3, 0),
                new TheMahjongMeld.Pon(hand("", "666", "", 0), 0, 1, 1));
        var pair = hand("", "99", "", 0);
        var hooked = SichuanScoring.tryWin(round(pair, melds), 0, 0, pair.getLast(), Suit.SOUZU,
                Set.of(), false, false, false).orElseThrow();
        check(hooked.yaku().contains(NonYakuman.SC_JINGOUDIAO) && hooked.han() == 3, "four exposed melds and a pair must make golden hook");
    }

    private static void realWaits() {
        var waiting = hand("1234569", "234678", "", 0);
        check(SichuanScoring.inTenpai(player(waiting, List.of()), Suit.SOUZU, false), "ordinary pair wait not detected");
        check(SichuanScoring.tenpaiPayment(player(waiting, List.of()), Suit.SOUZU, false) == 1000,
                "exhaustive settlement used a self-draw bonus or wrong base payment");
        check(!SichuanScoring.inTenpai(player(waiting, List.of()), Suit.MANZU, false), "missing-suit hand was treated as tenpai");
        var jokerWait = hand("134569", "23467", "", 2);
        check(SichuanScoring.inTenpai(player(jokerWait, List.of()), Suit.SOUZU, true), "joker-assisted wait not detected");
        check(!SichuanScoring.inTenpai(player(jokerWait, List.of()), Suit.SOUZU, false), "disabled jokers made a wait");
    }

    public static void main(String[] args) {
        baseWinAndPayments();
        missingSuitAndJokers();
        scoringShapesAndLimits();
        realWaits();
        System.out.println("PASS: Sichuan no-yaku wins, dingque, optional red-dragon jokers, seven/dragon pairs, triplets, pure suit, cap and payments excluding earlier winners");
    }
}
