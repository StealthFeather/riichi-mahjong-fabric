package com.riichimahjong.fabric;

import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.TheMahjongTile.Suit;
import com.themahjong.driver.*;
import com.themahjong.driver.bots.StupidActiveBot;
import com.themahjong.sichuan.SichuanRules;
import com.themahjong.sichuan.SichuanScoring;
import com.themahjong.sichuan.SichuanState;
import com.themahjong.yaku.NonYakuman;
import com.themahjong.yaku.WinResult;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Exercise accepted actions through the actual driver, including setup and resumed blood battles. */
public final class SichuanDriverRegression {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static TheMahjongTile tile(Suit suit, int rank) { return new TheMahjongTile(suit, rank, false); }
    private static List<TheMahjongTile> hand(String man, String pin, String sou) {
        var result = new ArrayList<TheMahjongTile>();
        String[] digits = {man, pin, sou};
        for (int suit = 0; suit < 3; suit++) for (char digit : digits[suit].toCharArray())
            result.add(tile(Suit.values()[suit], digit - '0'));
        return result;
    }
    private static TheMahjongPlayer player(List<TheMahjongTile> hand) { return player(hand, List.of()); }
    private static TheMahjongPlayer player(List<TheMahjongTile> hand, List<TheMahjongMeld> melds) {
        return new TheMahjongPlayer(25000, TheMahjongPlayer.RiichiState.NONE, false,
                hand, melds, List.of(), List.of());
    }
    private static List<MahjongPlayerInterface> humans() {
        return Stream.generate(() -> (MahjongPlayerInterface)new MahjongHumanPlayer()).limit(4).toList();
    }
    private static List<TheMahjongTile> waiting() { return hand("1234569", "234678", ""); }
    private static SichuanState.Snapshot playState(List<SichuanState.Win> wins) {
        return new SichuanState.Snapshot(SichuanState.Stage.PLAY, 1,
                List.of(List.of(), List.of(), List.of(), List.of()), Set.of(),
                List.of(2, 2, 2, 2), wins, List.of(), false, -1);
    }
    private static TheMahjongDriver rig(List<TheMahjongPlayer> players, int turn,
            TheMahjongTile drawn, List<TheMahjongTile> wall, List<SichuanState.Win> wins) {
        var base = SichuanRules.newMatch(2, false, false, true);
        var round = new TheMahjongRound(TheMahjongTile.Wind.EAST, 1, 0, 0, 0,
                TheMahjongRound.State.TURN, turn, -1, TheMahjongRound.ActiveTile.drawn(drawn),
                wall, List.of(), List.of(), List.of(), 0, players, List.of());
        var match = new TheMahjongMatch(4, base.startingPoints(), base.targetPoints(), 2,
                TheMahjongMatch.State.IN_ROUND, base.tileSet(), base.ruleSet(), List.of(), round);
        var driver = new TheMahjongDriver(match, humans(), new Random(5),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(turn), false, OptionalInt.empty()));
        driver.restoreSichuanState(playState(wins));
        return driver;
    }
    private static TheMahjongDriver rig(List<TheMahjongPlayer> players, TheMahjongTile drawn) {
        return rig(players, 0, drawn, Collections.nCopies(20, tile(Suit.MANZU, 8)), List.of());
    }
    private static void act(TheMahjongDriver driver, int seat, PlayerAction action) {
        driver.submitAction(seat, action); driver.advance(.05);
    }
    private static PlayerAction action(TheMahjongDriver driver, int seat, Class<? extends PlayerAction> type) {
        return driver.legalActions(seat).stream().filter(type::isInstance).findFirst()
                .orElseThrow(() -> new AssertionError("missing " + type.getSimpleName() + " for seat " + seat + " in " + driver.currentPhase()));
    }
    private static void pass(TheMahjongDriver driver) {
        for (int tries = 0; driver.currentPhase() instanceof MatchPhase.AwaitingClaims claims; tries++) {
            check(tries < 5, "claims never resolved");
            for (int seat : claims.pendingSeats()) if (driver.legalActions(seat).contains(PlayerAction.Pass.INSTANCE))
                driver.submitAction(seat, PlayerAction.Pass.INSTANCE);
            driver.advance(.05);
        }
    }
    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        var out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(out, driver);
        var in = TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), out.buildResult());
        return DriverNbt.readDriver(in, humans(), new Random(5));
    }
    private static List<Integer> points(TheMahjongDriver driver) {
        return driver.currentRound().players().stream().map(TheMahjongPlayer::points).toList();
    }
    private static Map<TheMahjongTile, Integer> bag(List<TheMahjongTile> tiles) {
        var result = new HashMap<TheMahjongTile, Integer>();
        for (var tile : tiles) result.merge(tile, 1, Integer::sum);
        return result;
    }
    private static Map<TheMahjongTile, Integer> roundBag(TheMahjongRound round) {
        var tiles = new ArrayList<>(round.liveWall());
        tiles.addAll(round.rinshanTiles()); tiles.addAll(round.doraIndicators()); tiles.addAll(round.uraDoraIndicators());
        for (var player : round.players()) tiles.addAll(player.currentHand());
        return bag(tiles);
    }
    private static List<TheMahjongTile> exchangeChoice(TheMahjongDriver driver, int seat) {
        var hand = driver.currentRound().players().get(seat).currentHand();
        for (var suit : List.of(Suit.MANZU, Suit.PINZU, Suit.SOUZU)) {
            var same = hand.stream().filter(t -> t.suit() == suit).limit(3).toList();
            if (same.size() == 3) return same;
        }
        throw new AssertionError("no exchangeable suit in thirteen tiles");
    }

    private static void fixedInitialJokers(TheMahjongRound round) {
        check(round.liveWall().size() == 60 && round.liveWall().stream().allMatch(t -> t.suit().isNumber()),
                "joker mode must leave exactly sixty ordinary tiles and no jokers in the wall");
        for (int seat = 0; seat < 4; seat++) {
            var hand = round.players().get(seat).currentHand();
            check(hand.size() == 13 && hand.stream().filter(t -> SichuanScoring.isJoker(t, true)).count() == 1
                    && hand.stream().filter(t -> t.suit().isNumber()).count() == 12,
                    "seat " + seat + " did not receive twelve ordinary tiles and its fixed joker");
        }
    }

    private static void fixedJokerDealsAcrossSeeds() {
        for (int seed = 0; seed < 12; seed++) {
            var driver = new TheMahjongDriver(SichuanRules.newMatch(2, (seed & 1) == 0, true), humans(), new Random(seed));
            driver.startMatch();
            fixedInitialJokers(driver.currentRound());
            var saved = driver.currentRound();
            driver = reload(driver);
            fixedInitialJokers(driver.currentRound());
            check(driver.currentRound().equals(saved), "loading a fresh joker deal dealt the hands again");
        }
    }

    private static void setupAndSerialization() {
        var driver = new TheMahjongDriver(SichuanRules.newMatch(2, true, true), humans(), new Random(77));
        driver.startMatch();
        var original = driver.currentRound();
        fixedInitialJokers(original);
        var originalBag = roundBag(original);
        check(originalBag.values().stream().mapToInt(Integer::intValue).sum() == 112, "joker mode did not deal exactly 112 tiles");
        check(original.rinshanTiles().isEmpty() && original.doraIndicators().isEmpty()
                && original.uraDoraIndicators().isEmpty() && original.revealedDoraCount() == 0,
                "Sichuan reserved a riichi dead wall or dora");
        check(driver.sichuanState().stage() == SichuanState.Stage.EXCHANGE, "exchange option skipped the exchange phase");
        for (int seat = 0; seat < 4; seat++) check(driver.legalActions(seat).isEmpty(), "normal play leaked into setup");
        check(!driver.confirmExchange(0), "an empty exchange selection was accepted");
        var selections = new ArrayList<List<TheMahjongTile>>();
        var selected = exchangeChoice(driver, 0);
        check(driver.toggleExchangeTile(0, selected.getFirst()), "first exchange click failed");
        var other = original.players().getFirst().currentHand().stream()
                .filter(t -> t.suit().isNumber() && t.suit() != selected.getFirst().suit()).findFirst();
        if (other.isPresent()) check(!driver.toggleExchangeTile(0, other.get()), "mixed-suit exchange was accepted");
        check(!driver.toggleExchangeTile(0, tile(Suit.DRAGON, 3)), "a joker was exchangeable");
        check(driver.clearExchangeSelection(0), "exchange selection could not be cleared");
        for (int seat = 0; seat < 4; seat++) {
            var choice = exchangeChoice(driver, seat); selections.add(choice);
            for (var tile : choice) check(driver.toggleExchangeTile(seat, tile), "an owned exchange tile was rejected");
            check(driver.confirmExchange(seat), "three same-suit tiles could not be confirmed");
            check(!driver.toggleExchangeTile(seat, choice.getFirst()), "a confirmed exchange could still be edited");
            if (seat == 0) {
                var saved = driver.sichuanState().snapshot();
                var before = driver.currentRound();
                driver = reload(driver);
                fixedInitialJokers(driver.currentRound());
                check(driver.sichuanState().snapshot().equals(saved) && driver.currentRound().equals(before),
                        "partial exchange/confirmation did not survive NBT");
            }
        }
        driver.advance(0);
        check(driver.sichuanState().stage() == SichuanState.Stage.VOID_SUIT, "exchange did not proceed to dingque");
        int direction = driver.sichuanState().exchangeDirection();
        for (int seat = 0; seat < 4; seat++) {
            var expected = new ArrayList<>(original.players().get(seat).currentHand());
            for (var tile : selections.get(seat)) check(expected.remove(tile), "bad exchange fixture");
            expected.addAll(selections.get((seat - direction + 4) % 4));
            check(bag(expected).equals(bag(driver.currentRound().players().get(seat).currentHand())),
                    "seat received tiles from the wrong exchange direction");
            check(driver.currentRound().players().get(seat).currentHand().size() == 13, "exchange changed a hand's size");
        }
        check(roundBag(driver.currentRound()).equals(originalBag), "exchange duplicated or lost tiles");
        fixedInitialJokers(driver.currentRound());
        check(!driver.chooseVoidSuit(0, Suit.WIND), "a wind could be selected as the missing suit");
        check(driver.chooseVoidSuit(0, Suit.MANZU), "dingque choice failed");
        driver = reload(driver);
        fixedInitialJokers(driver.currentRound());
        check(driver.sichuanState().voidSuit(0) == Suit.MANZU && !driver.sichuanState().pendingVoidSuit(0),
                "dingque choice was lost on reload");
        check(driver.legalActions(0).isEmpty(), "draw was available before all missing suits were chosen");
        for (int seat = 1; seat < 4; seat++) check(driver.chooseVoidSuit(seat, Suit.SOUZU), "other player's dingque failed");
        check(driver.sichuanState().stage() == SichuanState.Stage.PLAY, "play never started after dingque");
        check(driver.legalActions(0).contains(PlayerAction.Draw.INSTANCE), "dealer did not get the first draw");
        act(driver, 0, PlayerAction.Draw.INSTANCE);
        var missing = driver.currentRound().players().getFirst().currentHand().stream().anyMatch(t -> t.suit() == Suit.MANZU);
        if (missing) check(driver.legalActions(0).stream().allMatch(a -> a instanceof PlayerAction.Discard d && d.tile().suit() == Suit.MANZU),
                "a player could keep missing-suit tiles and discard another suit");
    }

    private static void noRiichiOrChiAndDingque() {
        var drawn = tile(Suit.MANZU, 2);
        var first = new ArrayList<>(waiting()); first.add(drawn);
        var driver = rig(List.of(player(first), player(hand("11223345", "34567", "")),
                player(waiting()), player(waiting())), drawn);
        check(driver.legalActions(0).stream().noneMatch(a -> a instanceof PlayerAction.DiscardWithRiichi
                || a instanceof PlayerAction.DeclareKita || a instanceof PlayerAction.KyuushuAbort), "riichi-only actions leaked into Sichuan");
        act(driver, 0, new PlayerAction.Discard(drawn));
        check(driver.currentPhase() instanceof MatchPhase.AwaitingClaims, "pon fixture produced no claims");
        check(driver.legalActions(1).stream().anyMatch(a -> a instanceof PlayerAction.Pon), "Sichuan pon was unavailable");
        check(driver.legalActions(1).stream().noneMatch(a -> a instanceof PlayerAction.Chi), "Sichuan allowed chi");
        pass(driver);

        var mixed = hand("1234569", "234678", "1");
        driver = rig(List.of(player(mixed), player(waiting()), player(waiting()), player(waiting())), tile(Suit.SOUZU, 1));
        check(driver.legalActions(0).equals(List.of(new PlayerAction.Discard(tile(Suit.SOUZU, 1)))),
                "dingque did not force the remaining missing-suit tile to be discarded");
    }

    private static void firstWinnerRetiresAndContinues() {
        var complete = hand("12345699", "234678", "");
        var driver = rig(List.of(player(complete), player(waiting()), player(waiting()), player(waiting())), complete.getLast());
        act(driver, 0, action(driver, 0, PlayerAction.DeclareTsumo.class));
        check(driver.sichuanState().won(0) && driver.sichuanState().winners().equals(Set.of(0)), "winner was not recorded");
        check(!(driver.currentPhase() instanceof MatchPhase.RoundEnded), "the first winner prematurely ended blood battle");
        check(driver.legalActions(0).isEmpty(), "a winner could keep playing");
        check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw d && d.seat() == 1, "play did not continue with the next active seat");
        var saved = driver.sichuanState().snapshot();
        var before = driver.currentRound();
        driver = reload(driver);
        check(driver.sichuanState().snapshot().equals(saved) && driver.currentRound().equals(before), "winner/score state did not survive NBT");
        int settledWinnerPoints = points(driver).getFirst();
        for (int seat = 1; seat < 4; seat++) {
            check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw d && d.seat() == seat, "turn did not visit the expected active player");
            act(driver, seat, PlayerAction.Draw.INSTANCE);
            act(driver, seat, action(driver, seat, PlayerAction.Discard.class));
            pass(driver);
        }
        check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw d && d.seat() == 1, "turn cycle did not skip a retired winner");
        check(points(driver).getFirst() == settledWinnerPoints, "retired winner's points changed during later play");
    }

    private static void simultaneousWinners() {
        var first = new ArrayList<>(waiting()); first.add(tile(Suit.MANZU, 9));
        var driver = rig(List.of(player(first), player(waiting()), player(waiting()), player(waiting())), tile(Suit.MANZU, 9));
        act(driver, 0, new PlayerAction.Discard(tile(Suit.MANZU, 9)));
        check(driver.currentPhase() instanceof MatchPhase.AwaitingClaims, "multi-ron fixture produced no claim window");
        act(driver, 1, action(driver, 1, PlayerAction.DeclareRon.class));
        check(driver.legalActions(1).isEmpty(), "an accepted ron remained clickable");
        driver = reload(driver);
        check(driver.legalActions(1).isEmpty() && driver.sichuanClaimDecisions().containsKey(1),
                "an accepted ron was lost or reopened after NBT reload");
        act(driver, 2, action(driver, 2, PlayerAction.DeclareRon.class));
        check(!(driver.currentPhase() instanceof MatchPhase.RoundEnded), "multi-ron resolved before the last responder");
        act(driver, 3, action(driver, 3, PlayerAction.DeclareRon.class));
        check(driver.sichuanState().winners().equals(Set.of(1, 2, 3)), "a simultaneous winner was lost");
        check(driver.currentPhase() instanceof MatchPhase.RoundEnded ended && ended.winResults().size() == 3,
                "three winners did not finish the hand");
        check(points(driver).equals(List.of(22000, 26000, 26000, 26000)), "multi-ron paid the wrong players or applied payment more than once");
        check(points(driver).stream().mapToInt(Integer::intValue).sum() == 100000, "ron did not conserve total points");
        driver = reload(driver);
        check(driver.sichuanState().winners().size() == 3 && driver.currentPhase() instanceof MatchPhase.RoundEnded,
                "terminal winner state did not survive NBT");
    }

    private static void kansAndRobbingKan() {
        var quad = hand("1111", "1234567899", "");
        var driver = rig(List.of(player(quad), player(waiting()), player(waiting()), player(waiting())), quad.getLast());
        act(driver, 0, action(driver, 0, PlayerAction.Ankan.class));
        check(points(driver).equals(List.of(31000, 23000, 23000, 23000)), "concealed kan did not charge each active opponent 2000");
        check(driver.currentPhase() instanceof MatchPhase.AwaitingDraw d && d.seat() == 0 && d.rinshan(), "concealed kan did not yield a replacement draw");
        int wall = driver.currentRound().liveWall().size();
        act(driver, 0, PlayerAction.Draw.INSTANCE);
        check(driver.currentRound().liveWall().size() == wall - 1 && driver.currentRound().doraIndicators().isEmpty(),
                "Sichuan kan used a dead wall or revealed dora");

        var discard = tile(Suit.MANZU, 4);
        var first = new ArrayList<>(waiting()); first.add(discard);
        driver = rig(List.of(player(first), player(hand("444", "1234567899", "")), player(waiting()), player(waiting())), discard);
        act(driver, 0, new PlayerAction.Discard(discard));
        act(driver, 1, action(driver, 1, PlayerAction.Daiminkan.class)); pass(driver);
        check(points(driver).equals(List.of(23000, 27000, 25000, 25000)), "open kan did not charge only its discarder");

        var pon = new TheMahjongMeld.Pon(List.of(discard, discard, discard), 0, 1, 0);
        var upgraded = player(hand("4", "1234567899", ""), List.of(pon));
        driver = rig(List.of(upgraded, player(waiting()), player(waiting()), player(waiting())), discard);
        act(driver, 0, action(driver, 0, PlayerAction.Kakan.class)); pass(driver);
        check(points(driver).equals(List.of(28000, 24000, 24000, 24000)), "added kan did not charge every active opponent 1000");

        driver = rig(List.of(upgraded, player(hand("12356", "12367899", "")), player(waiting()), player(waiting())), discard);
        act(driver, 0, action(driver, 0, PlayerAction.Kakan.class));
        act(driver, 1, action(driver, 1, PlayerAction.DeclareChankan.class)); pass(driver);
        check(driver.sichuanState().won(1) && driver.currentRound().players().getFirst().melds().getFirst() instanceof TheMahjongMeld.Pon,
                "robbed added kan did not restore its pon and retire its winner");
        check(points(driver).equals(List.of(23000, 27000, 25000, 25000)), "robbed kan paid a kan transfer or wrong win bonus");

        var complete = hand("12345699", "234678", "");
        driver = rig(List.of(player(complete), player(hand("7777", "123456789", "")),
                player(waiting()), player(waiting())), complete.getLast());
        act(driver, 0, action(driver, 0, PlayerAction.DeclareTsumo.class));
        int retiredPoints = points(driver).getFirst();
        var beforeKan = points(driver);
        act(driver, 1, PlayerAction.Draw.INSTANCE);
        act(driver, 1, action(driver, 1, PlayerAction.Ankan.class));
        check(points(driver).getFirst() == retiredPoints && points(driver).get(1) == beforeKan.get(1) + 4000
                && points(driver).get(2) == beforeKan.get(2) - 2000 && points(driver).get(3) == beforeKan.get(3) - 2000,
                "a later kan charged an earlier winner or missed an active payer");
    }

    private static void wholeBotHands() {
        for (boolean exchange : new boolean[]{false, true}) for (boolean jokers : new boolean[]{false, true}) {
            var bots = Stream.generate(() -> (MahjongPlayerInterface)new StupidActiveBot(0, 0, 0, 0, 0, 0, 0)).limit(4).toList();
            var driver = new TheMahjongDriver(SichuanRules.newMatch(1, exchange, jokers, true), bots, new Random(exchange ? 29 : 17));
            driver.startMatch();
            if (jokers) fixedInitialJokers(driver.currentRound());
            int deals = 0;
            while (!(driver.currentPhase() instanceof MatchPhase.MatchEnded)) {
                for (int tick = 0; !(driver.currentPhase() instanceof MatchPhase.RoundEnded); tick++) {
                    check(tick < 2000, "all-bot Sichuan hand stalled: " + driver.currentPhase() + "/" + driver.sichuanState().stage());
                    driver.advance(.5);
                }
                check(driver.currentRound().state() == TheMahjongRound.State.ENDED, "bot hand ended without settled round state");
                check(points(driver).stream().mapToInt(Integer::intValue).sum() == 100000, "full bot hand created/lost points");
                check(driver.sichuanState().stage() == SichuanState.Stage.PLAY, "bots never finished setup");
                check(++deals <= 4, "Sichuan east match did not stop after four dealers");
                driver.advanceRound(false, 0);
                if (!(driver.currentPhase() instanceof MatchPhase.MatchEnded)) {
                    check(driver.sichuanState().winners().isEmpty(), "previous winners leaked into a fresh deal");
                    if (jokers) fixedInitialJokers(driver.currentRound());
                }
                check(driver.currentRound().honba() == 0 && driver.currentRound().riichiSticks() == 0, "riichi honba/deposits leaked into Sichuan");
            }
            check(deals == 4, "Sichuan east match ended before every dealer played");
        }
    }

    public static void main(String[] args) {
        fixedJokerDealsAcrossSeeds();
        setupAndSerialization();
        noRiichiOrChiAndDingque();
        firstWinnerRetiresAndContinues();
        simultaneousWinners();
        kansAndRobbingKan();
        wholeBotHands();
        System.out.println("PASS: real Sichuan driver setup, directional exchanges, dingque, no chi/riichi/dora, blood-battle continuation, multi-ron, kan/robbed-kan payments, NBT and complete bot hands");
    }
}
