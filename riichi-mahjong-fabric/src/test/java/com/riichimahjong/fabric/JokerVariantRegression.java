package com.riichimahjong.fabric;

import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.variants.Jokers;
import com.themahjong.variants.VariantRules;
import com.themahjong.yaku.*;
import java.util.*;
import java.util.function.Supplier;

/** Real riichi shape, settlement, legal-action and initial-deal paths with an independent wildcard. */
public final class JokerVariantRegression {
    private static TheMahjongTile t(int suit, int rank) { return new TheMahjongTile(TheMahjongTile.Suit.values()[suit], rank, false); }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static TheMahjongPlayer player(List<TheMahjongTile> hand) {
        return player(hand, List.of(), List.of(), TheMahjongPlayer.RiichiState.NONE);
    }
    private static TheMahjongPlayer player(List<TheMahjongTile> hand, List<TheMahjongMeld> melds,
            List<TheMahjongDiscard> discards, TheMahjongPlayer.RiichiState riichi) {
        return new TheMahjongPlayer(25000, riichi, false, hand, melds, discards, List.of());
    }

    private static void dealMatrix() {
        List<Supplier<TheMahjongMatch>> factories = List.of(TheMahjongMatch::defaultMahjongSoul,
                TheMahjongMatch::defaultTenhou, TheMahjongMatch::defaults,
                TheMahjongMatch::defaultMahjongSoulSanma, TheMahjongMatch::defaultTenhouSanma);
        for (var factory : factories) {
            var base = factory.get();
            check(VariantRules.withOptions(base, false, false).equals(base), "disabled options changed a preset");
            for (boolean exchange : new boolean[]{false, true}) for (boolean jokers : new boolean[]{false, true}) {
                var match = VariantRules.withOptions(base, exchange, jokers);
                check(match.ruleSet().exchangeThree() == exchange && match.ruleSet().jokersEnabled() == jokers,
                        "variant flags were not retained");
                var wall = match.tileSet().createOrderedWall();
                check(wall.stream().filter(Jokers::isJoker).count() == (jokers ? match.playerCount() : 0),
                        "independent joker count must equal the number of seats");
                check(wall.stream().filter(t(4,3)::equals).count() == 4, "ordinary red dragons were replaced");
                var started = match.startRound(new Random(31));
                var round = started.currentRound().orElseThrow();
                int deadSize = TheMahjongRound.rinshanTileCountFor(match.playerCount()) + 10;
                check(round.liveWall().size() == wall.size() - 13 * match.playerCount() - deadSize,
                        "riichi live/dead wall sizes changed");
                check(round.rinshanTiles().size() == TheMahjongRound.rinshanTileCountFor(match.playerCount())
                                && round.doraIndicators().size() == 5 && round.uraDoraIndicators().size() == 5,
                        "riichi dora or replacement draws were lost");
                for (var p : round.players()) check(p.currentHand().size() == 13
                                && p.currentHand().stream().filter(Jokers::isJoker).count() == (jokers ? 1 : 0),
                        "a seat did not receive exactly its selected initial wildcard");
                var dealt = new ArrayList<TheMahjongTile>();
                round.players().forEach(p -> dealt.addAll(p.currentHand()));
                for (var zone : List.of(round.liveWall(), round.rinshanTiles(), round.doraIndicators(), round.uraDoraIndicators())) {
                    check(zone.stream().noneMatch(Jokers::isJoker), "a joker remained drawable or became a dora indicator");
                    dealt.addAll(zone);
                }
                check(dealt.size() == wall.size() && match.tileSet().tiles().stream().allMatch(tile ->
                                dealt.stream().filter(tile::equals).count() == match.tileSet().copiesPerTile().get(tile)),
                        "rearranging the initial deal lost physical tiles");
                if (jokers) check(VariantRules.prepareRound(round).equals(round), "preparing a fixed-joker deal twice changed it");
                var end = new TheMahjongRound(round.roundWind(), round.handNumber(), round.honba(), round.riichiSticks(),
                        round.dealerSeat(), TheMahjongRound.State.ENDED, round.currentTurnSeat(), -1,
                        TheMahjongRound.ActiveTile.none(), round.liveWall(), round.rinshanTiles(), round.doraIndicators(),
                        round.uraDoraIndicators(), round.revealedDoraCount(), round.players(), List.of());
                var ended = new TheMahjongMatch(match.playerCount(), match.startingPoints(), match.targetPoints(),
                        match.roundCount(), TheMahjongMatch.State.IN_ROUND, match.tileSet(), match.ruleSet(), List.of(), end);
                var next = ended.advanceRound(new Random(9), false, 0).currentRound().orElseThrow();
                for (var p : next.players()) check(p.currentHand().stream().filter(Jokers::isJoker).count() == (jokers ? 1 : 0),
                        "the next hand did not apply the wildcard deal");
            }
        }
    }

    private static TheMahjongRound round(int count, TheMahjongPlayer first, TheMahjongRound.ActiveTile active,
            TheMahjongTile indicator) {
        if (first.discards().isEmpty()) first = player(first.currentHand(), first.melds(),
                List.of(new TheMahjongDiscard(t(3,4),false)), first.riichiState());
        var players = new ArrayList<TheMahjongPlayer>(); players.add(first);
        for (int seat = 1; seat < count; seat++) players.add(player(List.of(t(0,2),t(0,3),t(0,4),t(1,3),t(1,4),t(1,5),
                t(2,4),t(2,5),t(2,6),t(1,6),t(1,7),t(4,1),t(4,1))));
        return new TheMahjongRound(TheMahjongTile.Wind.EAST, 1, 0, 0, 1,
                active instanceof TheMahjongRound.ActiveTile.HeldDiscard ? TheMahjongRound.State.CLAIM_WINDOW : TheMahjongRound.State.TURN,
                0, active instanceof TheMahjongRound.ActiveTile.HeldDiscard ? 1 : -1, active,
                Collections.nCopies(30, t(2,9)), Collections.nCopies(TheMahjongRound.rinshanTileCountFor(count), t(0,9)),
                Collections.nCopies(5, indicator), Collections.nCopies(5, t(0,9)), 1, players, List.of());
    }

    private static List<TheMahjongTile> ready() {
        return List.of(t(0,2),t(0,3),t(0,4),t(1,3),t(1,4),t(1,5),t(2,4),t(2,5),t(2,6),t(1,6),Jokers.TILE,t(4,1),t(4,1));
    }

    private static void shapeAndScoring() {
        for (int count : new int[]{3,4}) {
            var rules = VariantRules.withOptions(count == 3 ? TheMahjongMatch.defaultMahjongSoulSanma()
                    : TheMahjongMatch.defaultMahjongSoul(), false, true).ruleSet();
            var waits = TenpaiChecker.winningTiles(player(ready()));
            check(waits.stream().anyMatch(t(1,7)::equals), "wildcard-assisted shape was not tenpai");
            check(com.themahjong.compat.DrawSettlement.inTenpai(player(ready())), "draw settlement ignored a wildcard wait");
            var hand = new ArrayList<>(ready()); hand.add(t(1,7));
            var round = round(count, player(hand), TheMahjongRound.ActiveTile.drawn(t(1,7)), t(0,9));
            var tsumo = WinResultBuilder.tryTsumo(round, rules, 0, false).orElseThrow();
            check(tsumo.yaku().contains(NonYakuman.MENZEN_TSUMO) && tsumo.doraCount() == 0,
                    "a wildcard lost the real riichi yaku or acquired a red-dora bonus");
            var full = List.of(t(4,3),t(4,3),Jokers.TILE,t(0,2),t(0,2),t(0,2),t(1,4),t(1,4),t(1,4),t(1,9),t(1,9));
            var opened = new TheMahjongMeld.Pon(List.of(t(2,6),t(2,6),t(2,6)),1,1,0);
            round = round(count, player(full,List.of(opened),List.of(),TheMahjongPlayer.RiichiState.NONE),
                    TheMahjongRound.ActiveTile.drawn(t(1,9)), t(4,2));
            var scored = WinResultBuilder.tryTsumo(round, rules, 0, false).orElseThrow();
            check(scored.doraCount() == 2, "the wildcard was counted as a physical dragon or red dora");
            check(scored.yaku().contains(NonYakuman.TOITOI), "a wildcard did not retain normal riichi triplet scoring");
            check(HandShape.decompose(full,List.of(opened)).stream().filter(HandShape.Standard.class::isInstance)
                            .map(HandShape.Standard.class::cast).flatMap(shape -> shape.closedGroups().stream())
                            .anyMatch(group -> group instanceof HandShape.ConcealedGroup.Triplet
                                    && group.tiles().getFirst().equals(t(4,3))),
                    "an ordinary red-dragon triplet could not be completed by the independent wildcard");
            var chi = new TheMahjongMeld.Chi(List.of(t(0,1),t(0,2),t(0,3)), 0, 1, 0);
            var openHand = List.of(t(1,2),t(1,3),t(1,4),t(2,4),t(2,5),t(2,6),t(0,7),Jokers.TILE,t(1,9),t(1,9));
            round = round(count, player(openHand, List.of(chi), List.of(), TheMahjongPlayer.RiichiState.NONE),
                    TheMahjongRound.ActiveTile.heldDiscard(t(0,8)), t(0,9));
            check(WinResultBuilder.tryRon(round, rules, 0, t(0,8), 1).isEmpty(), "wildcards allowed a yakuless open ron");
            check(WinResultBuilder.tryRon(round, rules, 0, Jokers.TILE, 1).isEmpty(), "a discarded wildcard could be ronned");
        }
        check(!Jokers.TILE.matchesSuitRank(t(4,3)) && !t(4,3).matchesSuitRank(Jokers.TILE),
                "wildcards and ordinary red dragons share a physical identity");
        check(new TheMahjongTile(TheMahjongTile.Suit.MANZU,5,true).matchesSuitRank(t(0,5)),
                "separating wildcard identity changed ordinary red-five identity");
        check(!Jokers.TILE.honor() && !Jokers.TILE.terminal() && t(4,3).honor(),
                "a wildcard was treated as an actual terminal/honor tile");
    }

    private static TheMahjongDriver driver(TheMahjongRound round, MatchPhase phase) {
        var base = round.players().size() == 3 ? TheMahjongMatch.defaultMahjongSoulSanma() : TheMahjongMatch.defaultMahjongSoul();
        var variant = VariantRules.withOptions(base, false, true);
        var match = new TheMahjongMatch(variant.playerCount(), variant.startingPoints(), variant.targetPoints(),
                variant.roundCount(), TheMahjongMatch.State.IN_ROUND, variant.tileSet(), variant.ruleSet(), List.of(), round);
        var players = new ArrayList<MahjongPlayerInterface>();
        for (int i = 0; i < match.playerCount(); i++) players.add(new MahjongHumanPlayer());
        return new TheMahjongDriver(match, players, new Random(1),
                new TheMahjongDriver.Snapshot(phase, false, OptionalInt.empty()));
    }

    private static void legalActionsAndFuriten() {
        var whiteTriplet = List.of(t(0,2),t(0,3),t(0,4),t(1,3),t(1,4),t(1,5),t(2,4),t(2,5),t(2,6),
                t(4,1),t(4,1),t(4,1),t(4,3));
        var discardedJoker = player(whiteTriplet, List.of(), List.of(new TheMahjongDiscard(Jokers.TILE,false)), TheMahjongPlayer.RiichiState.NONE);
        check(Furiten.canRon(discardedJoker,t(4,3)), "discarding a wildcard incorrectly created ordinary red-dragon furiten");
        check(!Furiten.canRon(discardedJoker,Jokers.TILE), "a wildcard discard was treated as a normal winning red dragon");
        for (int count : new int[]{3,4}) {
            var hand = new ArrayList<>(ready()); hand.add(t(3,3));
            var r = round(count, player(hand), TheMahjongRound.ActiveTile.drawn(t(3,3)), t(0,9));
            var d = driver(r,new MatchPhase.AwaitingDiscard(0));
            check(d.legalActions(0).contains(new PlayerAction.DiscardWithRiichi(t(3,3))),
                    "a closed wildcard wait could not declare riichi");
            ((MahjongHumanPlayer)d.playerAt(0)).queue(new PlayerAction.DiscardWithRiichi(t(3,3)));
            d.advance(.05);
            check(d.currentRound().players().getFirst().riichi(), "wildcard riichi was offered but not committed");
            var claimHand = List.of(t(4,3),Jokers.TILE,t(0,2),t(0,3),t(0,4),t(1,3),t(1,4),t(1,5),
                    t(2,4),t(2,5),t(2,6),t(4,1),t(4,1));
            r = round(count,player(claimHand),TheMahjongRound.ActiveTile.heldDiscard(t(4,3)),t(0,9));
            d = driver(r,new MatchPhase.AwaitingClaims(Set.of(0),t(4,3),MatchPhase.ClaimSource.DISCARD));
            check(d.legalActions(0).stream().noneMatch(a -> a instanceof PlayerAction.Pon || a instanceof PlayerAction.Daiminkan),
                    "a wildcard was offered as a real red dragon for pon/kan");
            var kanHand = List.of(t(4,3),t(4,3),t(4,3),t(4,3),Jokers.TILE,
                    t(0,2),t(0,3),t(0,4),t(1,3),t(1,4),t(1,5),t(2,4),t(2,5),t(2,6));
            r=round(count,player(kanHand),TheMahjongRound.ActiveTile.drawn(t(4,3)),t(0,9));
            d=driver(r,new MatchPhase.AwaitingDiscard(0));
            check(d.legalActions(0).stream().anyMatch(a -> a instanceof PlayerAction.Ankan kan
                            && kan.handTiles().size() == 4 && kan.handTiles().stream().allMatch(t(4,3)::equals)),
                    "a wildcard prevented the four actual red dragons from forming a concealed kan");
            check(kanHand.stream().filter(tile -> tile.matchesSuitRank(t(4,3))).count() == 4,
                    "physical fifth-copy counting included the independent wildcard");
            var pon = new TheMahjongMeld.Pon(List.of(t(4,3),t(4,3),t(4,3)),1,1,0);
            var upgrading = List.of(Jokers.TILE,t(4,3),t(0,2),t(0,3),t(0,4),t(1,3),t(1,4),t(1,5),t(2,4),t(2,5),t(2,6));
            r = round(count,player(upgrading,List.of(pon),List.of(),TheMahjongPlayer.RiichiState.NONE),
                    TheMahjongRound.ActiveTile.drawn(t(2,6)),t(0,9));
            d = driver(r,new MatchPhase.AwaitingDiscard(0));
            var kakan = d.legalActions(0).stream().filter(PlayerAction.Kakan.class::isInstance)
                    .map(PlayerAction.Kakan.class::cast).findFirst().orElseThrow();
            check(kakan.addedTile().equals(t(4,3)), "kakan consumed a wildcard instead of the fourth real red dragon");
            var upgraded = r.declareKakan(pon,kakan.addedTile(),d.rules());
            check(upgraded.players().getFirst().currentHand().stream().filter(Jokers::isJoker).count() == 1
                            && upgraded.players().getFirst().currentHand().stream().noneMatch(t(4,3)::equals),
                    "accepted red-dragon kakan lost or changed the wildcard identity");
        }
    }

    private static void physicalHonorRules() {
        var hand = new ArrayList<>(List.of(t(0,1),t(0,9),t(1,1),t(1,9),t(2,1),t(2,9),t(3,1),t(3,2),
                Jokers.TILE,t(0,2),t(0,3),t(1,2),t(1,3),t(2,2)));
        for (boolean ninthHonor : new boolean[]{false,true}) {
            if (ninthHonor) hand.set(9,t(3,3));
            var source = round(4,player(hand),TheMahjongRound.ActiveTile.drawn(t(2,2)),t(0,9));
            var players = new ArrayList<>(source.players()); players.set(0,player(hand));
            var firstDraw = new TheMahjongRound(source.roundWind(),source.handNumber(),0,0,source.dealerSeat(),
                    source.state(),0,-1,source.activeTile(),source.liveWall(),source.rinshanTiles(),source.doraIndicators(),
                    source.uraDoraIndicators(),1,players,List.of());
            var d = driver(firstDraw,new MatchPhase.AwaitingDiscard(0));
            check(d.legalActions(0).contains(PlayerAction.KyuushuAbort.INSTANCE) == ninthHonor,
                    "nine-terminals legality counted the wildcard as a physical red dragon");
        }
        var discarded = player(ready(),List.of(),List.of(new TheMahjongDiscard(Jokers.TILE,false)),TheMahjongPlayer.RiichiState.NONE);
        var r = round(4,discarded,TheMahjongRound.ActiveTile.none(),t(0,9));
        check(!r.isNagashiManganEligible(0),"discarding only a wildcard qualified for nagashi mangan");
    }

    public static void main(String[] args) {
        dealMatrix(); shapeAndScoring(); legalActionsAndFuriten(); physicalHonorRules();
        System.out.println("PASS: optional 3p/4p riichi joker deals conserve real tiles/dead walls and keep ordinary red dragons");
        System.out.println("PASS: wildcard shapes retain riichi yaku/fu, actual-tile dora, riichi legality, furiten and meld identities");
    }
}
