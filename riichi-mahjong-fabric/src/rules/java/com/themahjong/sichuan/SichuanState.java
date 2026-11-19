package com.themahjong.sichuan;

import com.themahjong.*;
import com.themahjong.driver.*;
import com.themahjong.yaku.WinResult;
import java.util.*;

/** Persisted blood-battle state; the immutable Round remains the single source of tiles and scores. */
public final class SichuanState {
    public enum Stage { EXCHANGE, VOID_SUIT, PLAY }
    /** The originating seat survives nextTurn(), which clears the active tile and claim source. */
    public record Win(int seat, TheMahjongTile tile, boolean selfDrawn, WinResult result, int sourceSeat) {
        /** Legacy saves can recover a ron source from its single paying seat; -1 means unknown. */
        public Win(int seat, TheMahjongTile tile, boolean selfDrawn, WinResult result) {
            this(seat, tile, selfDrawn, result, legacySourceSeat(seat, selfDrawn, result));
        }
        private static int legacySourceSeat(int seat, boolean selfDrawn, WinResult result) {
            if (selfDrawn) return seat;
            int source = -1;
            for (int i = 0; i < result.pointDeltas().size(); i++) {
                if (result.pointDeltas().get(i) >= 0) continue;
                if (source >= 0) return -1;
                source = i;
            }
            return source;
        }
    }
    public record Payment(int payer, int receiver, int amount) {}
    public record Snapshot(Stage stage, int exchangeDirection, List<List<TheMahjongTile>> selections,
            Set<Integer> confirmed, List<Integer> missingSuits, List<Win> wins, List<Payment> kanPayments,
            boolean lastDrawRinshan, int pendingKakan, int firstWinCount) {
        public Snapshot {
            wins = List.copyOf(wins);
            if (firstWinCount < (wins.isEmpty() ? 0 : 1) || firstWinCount > wins.size())
                throw new IllegalArgumentException("invalid first Sichuan win count");
        }
        /** Older snapshots preserve win order without the boundary between claim windows. */
        public Snapshot(Stage stage, int exchangeDirection, List<List<TheMahjongTile>> selections,
                Set<Integer> confirmed, List<Integer> missingSuits, List<Win> wins, List<Payment> kanPayments,
                boolean lastDrawRinshan, int pendingKakan) {
            this(stage, exchangeDirection, selections, confirmed, missingSuits, wins, kanPayments,
                    lastDrawRinshan, pendingKakan, wins.isEmpty() ? 0 : 1);
        }
    }
    public record Update(TheMahjongRound round, MatchPhase phase, List<MatchEvent> events) {}

    private Stage stage;
    private final int exchangeDirection;
    private final List<List<TheMahjongTile>> selections = new ArrayList<>();
    private final Set<Integer> confirmed = new LinkedHashSet<>();
    private final TheMahjongTile.Suit[] missingSuits = new TheMahjongTile.Suit[4];
    private final List<Win> wins = new ArrayList<>();
    private int firstWinCount;
    private final List<Payment> kanPayments = new ArrayList<>();
    private boolean lastDrawRinshan;
    private int pendingKakan = -1;
    private long revision;

    public SichuanState(boolean exchangeThree, int exchangeDirection) {
        this.stage = exchangeThree ? Stage.EXCHANGE : Stage.VOID_SUIT;
        if (exchangeDirection < 1 || exchangeDirection > 3) throw new IllegalArgumentException("exchange direction");
        this.exchangeDirection = exchangeDirection;
        for (int seat = 0; seat < 4; seat++) selections.add(new ArrayList<>());
    }

    public SichuanState(Snapshot saved) {
        this(saved.stage() == Stage.EXCHANGE, saved.exchangeDirection());
        stage = saved.stage();
        for (int seat = 0; seat < 4; seat++) {
            if (seat < saved.selections().size()) selections.get(seat).addAll(saved.selections().get(seat));
            int suit = seat < saved.missingSuits().size() ? saved.missingSuits().get(seat) : -1;
            if (suit >= 0 && suit < 3) missingSuits[seat] = TheMahjongTile.Suit.values()[suit];
        }
        confirmed.addAll(saved.confirmed()); wins.addAll(saved.wins());
        firstWinCount = saved.firstWinCount();
        kanPayments.addAll(saved.kanPayments());
        lastDrawRinshan = saved.lastDrawRinshan(); pendingKakan = saved.pendingKakan();
    }

    public Snapshot snapshot() {
        return new Snapshot(stage, exchangeDirection,
                selections.stream().map(List::copyOf).toList(), Set.copyOf(confirmed),
                Arrays.stream(missingSuits).map(s -> s == null ? -1 : s.ordinal()).toList(),
                wins, List.copyOf(kanPayments), lastDrawRinshan, pendingKakan, firstWinCount);
    }
    public Stage stage() { return stage; }
    public long revision() { return revision; }
    public int exchangeDirection() { return exchangeDirection; }
    public boolean pendingExchange(int seat) { return validSeat(seat) && stage == Stage.EXCHANGE && !confirmed.contains(seat); }
    public boolean pendingVoidSuit(int seat) { return validSeat(seat) && stage == Stage.VOID_SUIT && missingSuits[seat] == null; }
    public List<TheMahjongTile> exchangeSelection(int seat) { return validSeat(seat) ? List.copyOf(selections.get(seat)) : List.of(); }
    public TheMahjongTile.Suit voidSuit(int seat) { return validSeat(seat) ? missingSuits[seat] : null; }
    public boolean won(int seat) { return wins.stream().anyMatch(win -> win.seat() == seat); }
    public Set<Integer> winners() { var out = new LinkedHashSet<Integer>(); for (var win : wins) out.add(win.seat()); return Collections.unmodifiableSet(out); }
    public List<Win> wins() { return List.copyOf(wins); }
    public boolean dealerContinues(TheMahjongRound round, TheMahjongRuleSet rules) {
        int dealer = round.dealerSeat();
        if (wins.isEmpty())
            return SichuanScoring.inTenpai(round.players().get(dealer), voidSuit(dealer), rules.jokersEnabled());
        return wins.subList(0, firstWinCount).stream().anyMatch(win -> win.seat() == dealer);
    }
    public TheMahjongTile winningTile(int seat) { return wins.stream().filter(w -> w.seat() == seat).map(Win::tile).findFirst().orElse(null); }
    public boolean selfDrawn(int seat) { return wins.stream().anyMatch(w -> w.seat() == seat && w.selfDrawn()); }
    private static boolean validSeat(int seat) { return seat >= 0 && seat < 4; }

    /** Repeated clicks select separate physical copies; clicking a fully selected kind removes one. */
    public boolean toggleExchangeTile(TheMahjongRound round, int seat, TheMahjongTile tile) {
        if (!pendingExchange(seat) || tile == null || !tile.suit().isNumber()) return false;
        var selected = selections.get(seat);
        long owned = round.players().get(seat).currentHand().stream().filter(tile::equals).count();
        long picked = selected.stream().filter(tile::equals).count();
        if (picked > 0 && (picked >= owned || selected.size() == 3)) {
            selected.remove(tile); revision++; return true;
        }
        if (owned == 0 || selected.size() >= 3
                || !selected.isEmpty() && selected.getFirst().suit() != tile.suit()) return false;
        selected.add(tile); revision++; return true;
    }
    public boolean clearExchangeSelection(int seat) {
        if (!pendingExchange(seat) || selections.get(seat).isEmpty()) return false;
        selections.get(seat).clear(); revision++; return true;
    }
    public boolean confirmExchange(TheMahjongRound round, int seat) {
        if (!pendingExchange(seat) || selections.get(seat).size() != 3) return false;
        var hand = new ArrayList<>(round.players().get(seat).currentHand());
        TheMahjongTile.Suit suit = selections.get(seat).getFirst().suit();
        for (var tile : selections.get(seat)) if (tile.suit() != suit || !hand.remove(tile)) return false;
        confirmed.add(seat); revision++; return true;
    }
    public boolean exchangeReady() { return stage == Stage.EXCHANGE && confirmed.size() == 4; }
    public TheMahjongRound exchange(TheMahjongRound round) {
        if (!exchangeReady()) return round;
        var players = new ArrayList<>(round.players());
        for (int seat = 0; seat < 4; seat++) {
            var player = players.get(seat);
            var hand = new ArrayList<>(player.currentHand());
            for (var tile : selections.get(seat)) if (!hand.remove(tile)) throw new IllegalStateException("exchange selection changed");
            hand.addAll(selections.get((seat - exchangeDirection + 4) % 4));
            players.set(seat, SichuanRules.player(player, player.points(), hand, player.melds(), player.discards()));
        }
        stage = Stage.VOID_SUIT; revision++;
        return copy(round, round.state(), round.currentTurnSeat(), -1, round.activeTile(), round.liveWall(), players);
    }
    public boolean chooseVoidSuit(int seat, TheMahjongTile.Suit suit) {
        if (!pendingVoidSuit(seat) || suit == null || !suit.isNumber()) return false;
        missingSuits[seat] = suit; revision++;
        if (Arrays.stream(missingSuits).noneMatch(Objects::isNull)) stage = Stage.PLAY;
        return true;
    }
    public void automaticExchange(TheMahjongRound round, int seat) {
        if (!pendingExchange(seat)) return;
        var hand = round.players().get(seat).currentHand();
        var choice = numberSuits().stream().filter(s -> hand.stream().filter(t -> t.suit() == s).count() >= 3)
                .min(Comparator.comparingLong(s -> hand.stream().filter(t -> t.suit() == s).count())).orElseThrow();
        selections.get(seat).clear();
        selections.get(seat).addAll(hand.stream().filter(t -> t.suit() == choice).sorted(TheMahjongTile.DISPLAY_ORDER).limit(3).toList());
        confirmExchange(round, seat);
    }
    public void automaticVoidSuit(TheMahjongRound round, int seat) {
        if (!pendingVoidSuit(seat)) return;
        var hand = round.players().get(seat).currentHand();
        var choice = numberSuits().stream().min(Comparator.comparingLong(s -> hand.stream().filter(t -> t.suit() == s).count())).orElseThrow();
        chooseVoidSuit(seat, choice);
    }
    private static List<TheMahjongTile.Suit> numberSuits() {
        return List.of(TheMahjongTile.Suit.MANZU, TheMahjongTile.Suit.PINZU, TheMahjongTile.Suit.SOUZU);
    }

    public List<PlayerAction> legalActions(TheMahjongRound round, MatchPhase phase, TheMahjongRuleSet rules, int seat) {
        if (!validSeat(seat) || stage != Stage.PLAY || won(seat)) return List.of();
        if (phase instanceof MatchPhase.AwaitingDraw d) return d.seat() == seat && !round.liveWall().isEmpty()
                ? List.of(PlayerAction.Draw.INSTANCE) : List.of();
        if (phase instanceof MatchPhase.AwaitingDiscard d && d.seat() == seat) return discardActions(round, rules, seat);
        if (phase instanceof MatchPhase.AwaitingClaims c && c.pendingSeats().contains(seat))
            return claimActions(round, rules, seat, c.heldTile(), c.source());
        return List.of();
    }
    private List<PlayerAction> discardActions(TheMahjongRound round, TheMahjongRuleSet rules, int seat) {
        var player = round.players().get(seat);
        var out = new ArrayList<PlayerAction>();
        var drawn = round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn d ? d.tile() : null;
        if (drawn != null) SichuanScoring.tryWin(round, seat, seat, drawn, voidSuit(seat), winners(),
                rules.jokersEnabled(), lastDrawRinshan, false).ifPresent(w -> out.add(new PlayerAction.DeclareTsumo(w)));
        boolean hasMissing = SichuanScoring.hasMissingSuit(player, voidSuit(seat));
        for (var tile : new LinkedHashSet<>(player.currentHand())) {
            if (!hasMissing || tile.suit() == voidSuit(seat)) out.add(new PlayerAction.Discard(tile));
        }
        if (drawn != null && !hasMissing && !round.liveWall().isEmpty()) {
            for (var tile : new LinkedHashSet<>(player.currentHand())) {
                if (tile.suit() == voidSuit(seat) || SichuanScoring.isJoker(tile, rules.jokersEnabled())) continue;
                var matching = player.currentHand().stream().filter(tile::equals).toList();
                if (matching.size() == 4) out.add(new PlayerAction.Ankan(matching));
                for (var meld : player.melds()) if (meld instanceof TheMahjongMeld.Pon pon && pon.tiles().getFirst().matchesSuitRank(tile))
                    out.add(new PlayerAction.Kakan(pon, tile));
            }
        }
        return List.copyOf(out);
    }
    private List<PlayerAction> claimActions(TheMahjongRound round, TheMahjongRuleSet rules, int seat,
            TheMahjongTile tile, MatchPhase.ClaimSource source) {
        var out = new ArrayList<PlayerAction>(); out.add(PlayerAction.Pass.INSTANCE);
        int from = round.claimSourceSeat().orElseThrow();
        // A discarded wildcard cannot be claimed as a numbered tile or a meld.
        if (SichuanScoring.isJoker(tile, rules.jokersEnabled())) return List.copyOf(out);
        SichuanScoring.tryWin(round, seat, from, tile, voidSuit(seat), winners(), rules.jokersEnabled(),
                false, source == MatchPhase.ClaimSource.KAKAN).ifPresent(w -> out.add(source == MatchPhase.ClaimSource.KAKAN
                        ? new PlayerAction.DeclareChankan(w) : new PlayerAction.DeclareRon(w)));
        var player = round.players().get(seat);
        if (source != MatchPhase.ClaimSource.DISCARD || tile.suit() == voidSuit(seat)
                || round.liveWall().isEmpty()
                || SichuanScoring.hasMissingSuit(player, voidSuit(seat))) return List.copyOf(out);
        var matching = player.currentHand().stream().filter(tile::equals).toList();
        if (matching.size() >= 2) out.add(new PlayerAction.Pon(List.copyOf(matching.subList(0, 2))));
        if (matching.size() >= 3 && !round.liveWall().isEmpty()) out.add(new PlayerAction.Daiminkan(List.copyOf(matching.subList(0, 3))));
        return List.copyOf(out);
    }
    public Set<Integer> claimSeats(TheMahjongRound round, TheMahjongRuleSet rules, TheMahjongTile tile, MatchPhase.ClaimSource source) {
        var out = new LinkedHashSet<Integer>();
        int from = round.claimSourceSeat().orElseThrow();
        for (int seat = 0; seat < 4; seat++) if (seat != from && !won(seat) && claimActions(round, rules, seat, tile, source).size() > 1) out.add(seat);
        return out;
    }

    public Update apply(TheMahjongRound round, MatchPhase phase, TheMahjongRuleSet rules, int seat, PlayerAction action) {
        if (!legalActions(round, phase, rules, seat).contains(action)) throw new IllegalArgumentException("illegal Sichuan action");
        if (action instanceof PlayerAction.Draw) {
            if (round.liveWall().isEmpty()) return finish(round, true, rules);
            lastDrawRinshan = round.state() == TheMahjongRound.State.RINSHAN_DRAW;
            var wall = new ArrayList<>(round.liveWall());
            var tile = wall.remove(lastDrawRinshan ? wall.size() - 1 : 0);
            var players = new ArrayList<>(round.players()); var player = players.get(seat);
            var hand = new ArrayList<>(player.currentHand()); hand.add(tile);
            players.set(seat, SichuanRules.player(player, player.points(), hand, player.melds(), player.discards()));
            revision++;
            return update(copy(round, TheMahjongRound.State.TURN, seat, -1, TheMahjongRound.ActiveTile.drawn(tile), wall, players), new MatchPhase.AwaitingDiscard(seat));
        }
        if (action instanceof PlayerAction.Discard d) {
            var players = new ArrayList<>(round.players()); var player = players.get(seat);
            var hand = new ArrayList<>(player.currentHand()); hand.remove(d.tile());
            var discards = new ArrayList<>(player.discards()); discards.add(new TheMahjongDiscard(d.tile(), false));
            players.set(seat, SichuanRules.player(player, player.points(), hand, player.melds(), discards));
            round = copy(round, TheMahjongRound.State.CLAIM_WINDOW, seat, seat, TheMahjongRound.ActiveTile.heldDiscard(d.tile()), round.liveWall(), players);
            var next = enterClaims(round, rules, d.tile(), MatchPhase.ClaimSource.DISCARD);
            var events = new ArrayList<MatchEvent>(); events.add(new MatchEvent.Discarded(seat, d.tile(), false)); events.addAll(next.events());
            lastDrawRinshan = false; revision++;
            return new Update(next.round(), next.phase(), events);
        }
        if (action instanceof PlayerAction.DeclareTsumo t) {
            var tile = ((TheMahjongRound.ActiveTile.Drawn)round.activeTile()).tile();
            round = applyDeltas(round, t.result().pointDeltas());
            if (wins.isEmpty()) firstWinCount = 1;
            wins.add(new Win(seat, tile, true, t.result(), seat)); revision++;
            return continueAfterWin(round, seat, rules);
        }
        if (action instanceof PlayerAction.Ankan a) {
            var players = new ArrayList<>(round.players()); var player = players.get(seat);
            var hand = new ArrayList<>(player.currentHand()); for (var tile : a.handTiles()) hand.remove(tile);
            var melds = new ArrayList<>(player.melds()); var meld = new TheMahjongMeld.Ankan(a.handTiles()); melds.add(meld);
            players.set(seat, SichuanRules.player(player, player.points(), hand, melds, player.discards()));
            round = copy(round, TheMahjongRound.State.RINSHAN_DRAW, seat, -1, TheMahjongRound.ActiveTile.none(), round.liveWall(), players);
            round = settleKan(round, seat, -1, 2000); revision++;
            return new Update(round, new MatchPhase.AwaitingDraw(seat, true), List.of(new MatchEvent.MeldDeclared(seat, meld)));
        }
        if (action instanceof PlayerAction.Kakan k) {
            var players = new ArrayList<>(round.players()); var player = players.get(seat);
            var hand = new ArrayList<>(player.currentHand()); hand.remove(k.addedTile());
            var melds = new ArrayList<>(player.melds()); var meld = new TheMahjongMeld.Kakan(k.upgradedFrom(), k.addedTile()); melds.set(melds.indexOf(k.upgradedFrom()), meld);
            players.set(seat, SichuanRules.player(player, player.points(), hand, melds, player.discards()));
            round = copy(round, TheMahjongRound.State.KAKAN_CLAIM_WINDOW, seat, seat, TheMahjongRound.ActiveTile.heldKakan(k.addedTile()), round.liveWall(), players);
            pendingKakan = seat; revision++;
            var next = enterClaims(round, rules, k.addedTile(), MatchPhase.ClaimSource.KAKAN);
            var events = new ArrayList<MatchEvent>(); events.add(new MatchEvent.MeldDeclared(seat, meld)); events.addAll(next.events());
            return new Update(next.round(), next.phase(), events);
        }
        throw new IllegalArgumentException("unsupported Sichuan action: " + action);
    }
    private Update enterClaims(TheMahjongRound round, TheMahjongRuleSet rules, TheMahjongTile tile, MatchPhase.ClaimSource source) {
        var seats = claimSeats(round, rules, tile, source);
        if (!seats.isEmpty()) return update(round, new MatchPhase.AwaitingClaims(seats, tile, source));
        return noClaim(round, rules, source);
    }
    public Update resolveClaims(TheMahjongRound round, MatchPhase.AwaitingClaims phase,
            TheMahjongRuleSet rules, Map<Integer,PlayerAction> decisions) {
        int from = round.claimSourceSeat().orElseThrow();
        var ron = new ArrayList<Integer>();
        for (int offset = 1; offset < 4; offset++) {
            int seat = (from + offset) % 4;
            var action = decisions.get(seat);
            if (action instanceof PlayerAction.DeclareRon || action instanceof PlayerAction.DeclareChankan) ron.add(seat);
        }
        if (!ron.isEmpty()) {
            if (phase.source() == MatchPhase.ClaimSource.KAKAN) {
                // Robbing an added kan restores its original pon; it never pays a kan transfer.
                var players = new ArrayList<>(round.players()); var player = players.get(from);
                var melds = new ArrayList<>(player.melds());
                for (int i = 0; i < melds.size(); i++) if (melds.get(i) instanceof TheMahjongMeld.Kakan k && k.addedTile().equals(phase.heldTile())) { melds.set(i, k.upgradedFrom()); break; }
                players.set(from, SichuanRules.player(player, player.points(), player.currentHand(), melds, player.discards()));
                round = copy(round, round.state(), round.currentTurnSeat(), from, round.activeTile(), round.liveWall(), players);
                pendingKakan = -1;
            }
            if (wins.isEmpty()) firstWinCount = ron.size();
            for (int seat : ron) {
                var action = decisions.get(seat);
                var result = action instanceof PlayerAction.DeclareRon r ? r.result() : ((PlayerAction.DeclareChankan)action).result();
                round = applyDeltas(round, result.pointDeltas());
                var players = new ArrayList<>(round.players()); var player = players.get(seat);
                var hand = new ArrayList<>(player.currentHand()); hand.add(phase.heldTile());
                players.set(seat, SichuanRules.player(player, player.points(), hand, player.melds(), player.discards()));
                round = copy(round, round.state(), round.currentTurnSeat(), from, round.activeTile(), round.liveWall(), players);
                wins.add(new Win(seat, phase.heldTile(), false, result, from));
            }
            revision++;
            return continueAfterWin(round, from, rules);
        }
        if (phase.source() == MatchPhase.ClaimSource.DISCARD) {
            for (int offset = 1; offset < 4; offset++) {
                int seat = (from + offset) % 4; var action = decisions.get(seat);
                if (action instanceof PlayerAction.Pon || action instanceof PlayerAction.Daiminkan) {
                    var used = action instanceof PlayerAction.Pon p ? p.handTiles() : ((PlayerAction.Daiminkan)action).handTiles();
                    var players = new ArrayList<>(round.players()); var player = players.get(seat);
                    var hand = new ArrayList<>(player.currentHand()); for (var tile : used) hand.remove(tile);
                    var meldTiles = new ArrayList<>(used); meldTiles.add(phase.heldTile());
                    int riverIndex = round.players().get(from).discards().size() - 1;
                    TheMahjongMeld meld = action instanceof PlayerAction.Pon
                            ? new TheMahjongMeld.Pon(meldTiles, meldTiles.size() - 1, from, riverIndex)
                            : new TheMahjongMeld.Daiminkan(meldTiles, meldTiles.size() - 1, from, riverIndex);
                    var melds = new ArrayList<>(player.melds()); melds.add(meld);
                    players.set(seat, SichuanRules.player(player, player.points(), hand, melds, player.discards()));
                    boolean kan = action instanceof PlayerAction.Daiminkan;
                    round = copy(round, kan ? TheMahjongRound.State.RINSHAN_DRAW : TheMahjongRound.State.TURN, seat, -1,
                            TheMahjongRound.ActiveTile.none(), round.liveWall(), players);
                    if (kan) round = settleKan(round, seat, from, 2000);
                    lastDrawRinshan = false; revision++;
                    return new Update(round, kan ? new MatchPhase.AwaitingDraw(seat, true) : new MatchPhase.AwaitingDiscard(seat),
                            List.of(new MatchEvent.MeldDeclared(seat, meld)));
                }
            }
        }
        return noClaim(round, rules, phase.source());
    }
    private Update noClaim(TheMahjongRound round, TheMahjongRuleSet rules, MatchPhase.ClaimSource source) {
        if (source == MatchPhase.ClaimSource.KAKAN) {
            int seat = pendingKakan >= 0 ? pendingKakan : round.currentTurnSeat();
            round = settleKan(round, seat, -1, 1000); pendingKakan = -1;
            round = copy(round, TheMahjongRound.State.RINSHAN_DRAW, seat, -1, TheMahjongRound.ActiveTile.none(), round.liveWall(), round.players());
            return update(round, new MatchPhase.AwaitingDraw(seat, true));
        }
        return nextTurn(round, round.currentTurnSeat(), rules);
    }
    private Update continueAfterWin(TheMahjongRound round, int after, TheMahjongRuleSet rules) {
        if (!rules.bloodBattle() || winners().size() >= 3) return finish(round, false, rules);
        return nextTurn(round, after, rules);
    }
    private Update nextTurn(TheMahjongRound round, int after, TheMahjongRuleSet rules) {
        if (round.liveWall().isEmpty()) return finish(round, true, rules);
        int seat = (after + 1) % 4;
        while (won(seat)) seat = (seat + 1) % 4;
        round = copy(round, TheMahjongRound.State.SETUP, seat, -1, TheMahjongRound.ActiveTile.none(), round.liveWall(), round.players());
        return update(round, new MatchPhase.AwaitingDraw(seat, false));
    }
    private TheMahjongRound settleKan(TheMahjongRound round, int receiver, int onlyPayer, int amount) {
        var deltas = new ArrayList<>(Collections.nCopies(4, 0));
        for (int seat = 0; seat < 4; seat++) if (seat != receiver && !won(seat) && (onlyPayer < 0 || seat == onlyPayer)) {
            deltas.set(seat, deltas.get(seat) - amount); deltas.set(receiver, deltas.get(receiver) + amount);
            kanPayments.add(new Payment(seat, receiver, amount));
        }
        return applyDeltas(round, deltas);
    }
    private Update finish(TheMahjongRound round, boolean exhaustive, TheMahjongRuleSet rules) {
        if (exhaustive) round = settleExhaustive(round, rules);
        round = copy(round, TheMahjongRound.State.ENDED, round.currentTurnSeat(), -1,
                TheMahjongRound.ActiveTile.none(), round.liveWall(), round.players());
        var results = wins.stream().map(Win::result).toList(); revision++;
        return new Update(round, new MatchPhase.RoundEnded(results), List.of(new MatchEvent.RoundEnded(results)));
    }
    private TheMahjongRound settleExhaustive(TheMahjongRound round, TheMahjongRuleSet rules) {
        var deltas = new ArrayList<>(Collections.nCopies(4, 0));
        boolean[] pig = new boolean[4]; int[] ready = new int[4];
        for (int seat = 0; seat < 4; seat++) if (!won(seat)) {
            var player = round.players().get(seat); pig[seat] = SichuanScoring.hasMissingSuit(player, voidSuit(seat));
            if (!pig[seat]) ready[seat] = SichuanScoring.tenpaiPayment(player, voidSuit(seat), rules.jokersEnabled());
        }
        for (int payer = 0; payer < 4; payer++) if (!won(payer)) {
            for (int receiver = 0; receiver < 4; receiver++) if (receiver != payer && !pig[receiver] && (pig[payer] || !won(receiver))) {
                int amount = pig[payer] ? 16000 : ready[payer] == 0 && ready[receiver] > 0 ? ready[receiver] : 0;
                deltas.set(payer, deltas.get(payer) - amount); deltas.set(receiver, deltas.get(receiver) + amount);
            }
        }
        // A player who is not ready refunds all of their kan income at an exhaustive draw.
        for (var payment : kanPayments) if (!won(payment.receiver()) && ready[payment.receiver()] == 0) {
            deltas.set(payment.receiver(), deltas.get(payment.receiver()) - payment.amount());
            deltas.set(payment.payer(), deltas.get(payment.payer()) + payment.amount());
        }
        return applyDeltas(round, deltas);
    }
    private static TheMahjongRound applyDeltas(TheMahjongRound round, List<Integer> deltas) {
        var players = new ArrayList<>(round.players());
        for (int seat = 0; seat < 4; seat++) {
            var p = players.get(seat);
            players.set(seat, SichuanRules.player(p, p.points() + deltas.get(seat), p.currentHand(), p.melds(), p.discards()));
        }
        return copy(round, round.state(), round.currentTurnSeat(), round.claimSourceSeat().orElse(-1), round.activeTile(), round.liveWall(), players);
    }
    private static TheMahjongRound copy(TheMahjongRound round, TheMahjongRound.State state, int seat, int source,
            TheMahjongRound.ActiveTile tile, List<TheMahjongTile> wall, List<TheMahjongPlayer> players) {
        return SichuanRules.copy(round, state, seat, source, tile, wall, players);
    }
    private static Update update(TheMahjongRound round, MatchPhase phase) { return new Update(round, phase, List.of()); }
}
