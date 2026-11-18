package com.riichimahjong.fabric;

import com.riichimahjong.analysis.*;
import com.riichimahjong.mahjongtable.*;
import com.riichimahjong.themahjongcompat.DriverNbt;
import com.themahjong.*;
import com.themahjong.driver.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Actual driver callbacks, NBT resume and stale-save settlement retain the original match identity. */
public final class MatchAnalysisSessionRegression {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());
    private static final List<UUID> OWNERS = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private static final AnalysisMode MODE = AnalysisMode.of(RuleSetPreset.MAHJONG_SOUL_4P, MatchLength.SOUTH, false, false, false);
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static MahjongTableHumanPlayer human(int seat) {
        var player = new MahjongTableHumanPlayer();
        player.setOccupant(OWNERS.get(seat));
        return player;
    }
    private static List<MahjongPlayerInterface> humans() {
        return java.util.stream.IntStream.range(0, 4).mapToObj(seat -> (MahjongPlayerInterface) human(seat)).toList();
    }
    private static TheMahjongDriver start() {
        var driver = new TheMahjongDriver(RuleSetPreset.MAHJONG_SOUL_4P.newMatch(), humans(), new Random(52));
        driver.startMatch();
        return driver;
    }
    private static MatchAnalysisSession session(TheMahjongDriver driver) {
        var session = new MatchAnalysisSession(MODE, OWNERS);
        session.bind(driver, () -> {}, settlement -> {});
        session.beginHand();
        return session;
    }
    private static CompoundTag save(MatchAnalysisSession session) {
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        session.write(output);
        return output.buildResult();
    }
    private static MatchAnalysisSession read(CompoundTag tag) {
        return MatchAnalysisSession.read(TagValueInput.create(ProblemReporter.DISCARDING, REGISTRIES, tag));
    }
    private static TheMahjongDriver reload(TheMahjongDriver driver) {
        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        DriverNbt.writeDriver(output, driver);
        return DriverNbt.readDriver(TagValueInput.create(ProblemReporter.DISCARDING, REGISTRIES, output.buildResult()),
                humans(), new Random(53));
    }
    private static void historyTracksOperations() {
        var driver = start();
        var session = session(driver);
        var original = driver.playerAt(0);
        MahjongPlayerInterface idleBot = (request, delta) -> Optional.empty();
        driver.replacePlayer(0, idleBot);
        driver.advance(.05);
        check(!session.botOperated(), "an idle BOT controller was counted as an operation");
        driver.replacePlayer(0, original);
        ((MahjongTableHumanPlayer) original).queue(PlayerAction.Draw.INSTANCE);
        driver.advance(.05);
        check(!session.botOperated(), "leaving and returning before BOT action invalidated the match");
        // Controller identity changes alone do not count; a real legal BOT decision does.
        var botDriver = start();
        var botSession = session(botDriver);
        MahjongPlayerInterface drawOnly = (request, delta) -> request.legalActions().contains(PlayerAction.Draw.INSTANCE)
                ? Optional.of(PlayerAction.Draw.INSTANCE) : Optional.empty();
        botDriver.replacePlayer(0, drawOnly);
        botDriver.advance(.05);
        check(botSession.botOperated(), "real BOT draw did not disqualify the match");
        botDriver.replacePlayer(0, human(0));
        var restored = read(save(botSession));
        check(restored != null && restored.botOperated() && restored.id().equals(botSession.id()),
                "BOT operation history or match identity disappeared from NBT");
        var restoredDriver = reload(botDriver);
        restored.bind(restoredDriver, () -> {}, settlement -> {});
        restored.beginHand();
        check(restored.botOperated(), "resuming a human-controlled hand cleared historical BOT actions");
        restored.close();
        // Rebinding after a chunk unload keeps the lifetime BOT flag.
        restored.bind(restoredDriver, () -> {}, settlement -> {});
        check(restored.botOperated(), "close/rebind cleared BOT history");
        restored.close();botSession.close();session.close();
    }
    private static TheMahjongDriver terminal() {
        var base = RuleSetPreset.MAHJONG_SOUL_4P.newMatch().startRound(new Random(54));
        var round = base.currentRound().orElseThrow();
        var players = new ArrayList<TheMahjongPlayer>();
        var points = List.of(40000, 30000, 20000, 10000);
        for (int seat = 0; seat < 4; seat++) {
            var old = round.players().get(seat);
            players.add(new TheMahjongPlayer(points.get(seat), old.riichiState(), old.ippatsuEligible(),
                    old.currentHand(), old.melds(), old.discards(), old.temporaryFuritenTiles(), old.riichiPermanentFuriten(), old.kitaCount()));
        }
        var ended = new TheMahjongRound(TheMahjongTile.Wind.SOUTH, 4, 0, 0, 3, TheMahjongRound.State.ENDED,
                3, -1, TheMahjongRound.ActiveTile.none(), round.liveWall(), round.rinshanTiles(),
                round.doraIndicators(), round.uraDoraIndicators(), round.revealedDoraCount(), players, List.of());
        var match = new TheMahjongMatch(4, 25000, 30000, 2, TheMahjongMatch.State.ENDED,
                base.tileSet(), base.ruleSet(), List.of(ended), ended);
        return new TheMahjongDriver(match, humans(), new Random(55),
                new TheMahjongDriver.Snapshot(new MatchPhase.MatchEnded(), false, OptionalInt.empty()));
    }
    private static void terminalSettlesOnce(PlayerAnalysisStore store) {
        var driver = terminal();
        var session = session(driver);
        var stale = save(session);
        session.tick();session.tick();
        var first = store.profile(OWNERS.getFirst(), MODE);
        check(first.matches() == 1 && first.scoreTenths() == 450, "normal terminal match failed to settle exactly once");
        var resumed = read(save(session));
        resumed.bind(reload(driver), () -> {}, settlement -> {});
        resumed.tick();
        check(store.profile(OWNERS.getFirst(), MODE).matches() == 1, "saved terminal match settled twice");
        var staleResumed = read(stale);
        staleResumed.bind(reload(driver), () -> {}, settlement -> {});
        staleResumed.tick();
        check(store.profile(OWNERS.getFirst(), MODE).matches() == 1
                        && store.profile(OWNERS.getFirst(), MODE).scoreTenths() == 450,
                "stale table save replayed already committed rating points");
        staleResumed.close();resumed.close();session.close();
    }
    private static void practiceHasNoSession(PlayerAnalysisStore store) {
        // Imported records have their PlayerAnalysis child removed by the table. Missing session data stays disabled.
        check(read(new CompoundTag()) == null, "practice/legacy save without analysis invented a ranked session");
        var driver = terminal();
        long before = store.profile(OWNERS.getFirst(), MODE).matches();
        driver.advance(.05);
        check(store.profile(OWNERS.getFirst(), MODE).matches() == before, "unbound replay produced statistics");
    }
    private static void botTerminalHasOnlyStatistics(PlayerAnalysisStore store) {
        var driver = start();
        var session = session(driver);
        driver.replacePlayer(0, (request, delta) -> request.legalActions().contains(PlayerAction.Draw.INSTANCE)
                ? Optional.of(PlayerAction.Draw.INSTANCE) : Optional.empty());
        driver.advance(.05);
        check(session.botOperated(), "fixture did not make a real BOT move");
        var restored = read(save(session));
        long previousPoints = store.profile(OWNERS.getFirst(), MODE).scoreTenths();
        long previousMatches = store.profile(OWNERS.getFirst(), MODE).matches();
        restored.bind(terminal(), () -> {}, settlement -> {});
        restored.tick();restored.tick();
        check(store.profile(OWNERS.getFirst(), MODE).scoreTenths() == previousPoints,
                "resumed match with a historical BOT action received points");
        check(store.profile(OWNERS.getFirst(), MODE).matches() == previousMatches + 1,
                "unrated BOT match omitted statistics or counted the match twice");
        restored.close();session.close();
    }
    private static TheMahjongTile tile(int suit, int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.values()[suit], rank, false);
    }
    private static List<TheMahjongTile> waiting(int suit) {
        return List.of(tile(suit, 1), tile(suit, 2), tile(suit, 3), tile(suit, 4), tile(suit, 5),
                tile(suit, 6), tile(suit, 7), tile(suit, 8), tile(suit, 9),
                tile(3, 1), tile(3, 1), tile(3, 1), tile(4, 1));
    }
    private static void acceptedRonKeepsItsHuman(PlayerAnalysisStore store) {
        var base = RuleSetPreset.MAHJONG_SOUL_4P.newMatch();
        var players = new ArrayList<TheMahjongPlayer>();
        for (int seat = 0; seat < 4; seat++) {
            var hand = new ArrayList<>(waiting(seat % 3));
            if (seat == 0) hand.add(tile(4, 1));
            players.add(new TheMahjongPlayer(25000, TheMahjongPlayer.RiichiState.NONE, false,
                    hand, List.of(), List.of(), List.of()));
        }
        var indicators = Collections.nCopies(5, tile(4, 3));
        var round = new TheMahjongRound(TheMahjongTile.Wind.EAST, 1, 0, 0, 0, TheMahjongRound.State.TURN,
                0, -1, TheMahjongRound.ActiveTile.drawn(tile(4, 1)), Collections.nCopies(20, tile(4, 2)),
                Collections.nCopies(4, tile(4, 3)), indicators, indicators, 1, players, List.of());
        var match = new TheMahjongMatch(4, 25000, 30000, 2, TheMahjongMatch.State.IN_ROUND,
                base.tileSet(), base.ruleSet(), List.of(), round);
        var driver = new TheMahjongDriver(match, humans(), new Random(56),
                new TheMahjongDriver.Snapshot(new MatchPhase.AwaitingDiscard(0), false, OptionalInt.empty()));
        var session = session(driver);
        long oldWins = store.profile(OWNERS.get(1), MODE).wins();
        long oldDealt = store.profile(OWNERS.getFirst(), MODE).dealtIn();
        driver.submitAction(0, new PlayerAction.Discard(tile(4, 1)));
        driver.advance(.05);
        var ron = driver.legalActions(1).stream().filter(PlayerAction.DeclareRon.class::isInstance).findFirst().orElseThrow();
        driver.submitAction(1, ron);
        driver.advance(.05);
        check(driver.currentPhase() instanceof MatchPhase.AwaitingClaims, "fixture resolved before all claims arrived");
        // The controller leaves after the accepted human decision. This BOT never makes a decision.
        driver.replacePlayer(1, (request, delta) -> Optional.empty());
        for (int seat : List.of(2, 3)) driver.submitAction(seat, PlayerAction.Pass.INSTANCE);
        driver.advance(.05);
        session.tick();
        check(store.profile(OWNERS.get(1), MODE).wins() == oldWins + 1,
                "accepted human ron was attributed to the replacement BOT");
        check(store.profile(OWNERS.getFirst(), MODE).dealtIn() == oldDealt + 1,
                "accepted ron lost its original human discard source");
        check(!session.botOperated(), "leaving after an accepted ron counted an idle replacement BOT operation");
        session.close();
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("riichi-session-regression-");
        Field active = PlayerAnalysisStore.class.getDeclaredField("active");active.setAccessible(true);
        Object previous = active.get(null);
        var store = new PlayerAnalysisStore(directory);active.set(null, store);
        try {
            historyTracksOperations();terminalSettlesOnce(store);practiceHasNoSession(store);
            botTerminalHasOnlyStatistics(store);acceptedRonKeepsItsHuman(store);
            System.out.println("PASS: BOT history survives resume without scoring, idle rejoin stays eligible, stale saves settle once, accepted human ron retains identity, and practice stays disabled");
        } finally {
            active.set(null, previous);
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
