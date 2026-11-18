    public com.themahjong.variants.BloodBattleState bloodBattleState() { return bloodBattleState; }
    public void restoreBloodBattleState(com.themahjong.variants.BloodBattleState.Snapshot snapshot) {
        if (rules().sichuan() || !rules().bloodBattle()) throw new IllegalStateException("not a riichi blood battle");
        if (snapshot.wins().stream().anyMatch(win -> win.seat() >= match.playerCount()
                || win.sourceSeat() < 0 || win.sourceSeat() >= match.playerCount()
                || win.result().pointDeltas().size() != match.playerCount())) throw new IllegalArgumentException("invalid blood-battle seats");
        bloodBattleState = new com.themahjong.variants.BloodBattleState(snapshot);
    }
    private void initBloodBattleRound() {
        if (rules().sichuan() || !rules().bloodBattle()) { bloodBattleState = null; return; }
        // advanceRound() leaves the last round attached in MatchEnded. Keep its distinct winner tiles.
        if (match.state() == TheMahjongMatch.State.ENDED && bloodBattleState != null) return;
        bloodBattleState = match.state() == TheMahjongMatch.State.IN_ROUND || match.state() == TheMahjongMatch.State.ENDED
                ? new com.themahjong.variants.BloodBattleState() : null;
    }
    public Map<Integer,Integer> bloodBattleClaimDecisions() {
        var saved = new HashMap<Integer,Integer>();
        if (bloodBattleState != null && phase instanceof MatchPhase.AwaitingClaims claims) {
            claimDecisions.forEach((seat, action) -> {
                int index = bloodActions(seat, computeClaimActions(seat, claims.heldTile(), claims.source())).indexOf(action);
                if (index >= 0) saved.put(seat, index);
            });
        }
        return Map.copyOf(saved);
    }
    public void restoreBloodBattleClaims(Map<Integer,Integer> saved) {
        if (bloodBattleState == null || !(phase instanceof MatchPhase.AwaitingClaims claims)) return;
        for (var entry : saved.entrySet()) {
            if (!claims.pendingSeats().contains(entry.getKey())) continue;
            var legal = bloodActions(entry.getKey(), computeClaimActions(entry.getKey(), claims.heldTile(), claims.source()));
            int index = entry.getValue();
            if (index >= 0 && index < legal.size()) claimDecisions.put(entry.getKey(), legal.get(index));
        }
    }
    private List<PlayerAction> bloodActions(int seat, List<PlayerAction> actions) {
        if (bloodBattleState == null) return actions;
        if (bloodBattleState.won(seat)) return List.of();
        return actions.stream().map(action -> switch(action) {
            case PlayerAction.DeclareTsumo win -> (PlayerAction)new PlayerAction.DeclareTsumo(bloodBattleState.adjust(win.result(), seat, true, currentRound(), rules()));
            case PlayerAction.DeclareRon win -> new PlayerAction.DeclareRon(bloodBattleState.adjust(win.result(), seat, false, currentRound(), rules()));
            case PlayerAction.DeclareChankan win -> new PlayerAction.DeclareChankan(bloodBattleState.adjust(win.result(), seat, false, currentRound(), rules()));
            default -> action;
        }).toList();
    }
    private void acceptBloodTsumo(int seat, PlayerAction.DeclareTsumo action) {
        var round = currentRound();
        winningTile = round.activeTile();
        var tile = ((TheMahjongRound.ActiveTile.Drawn)winningTile).tile();
        match = matchWithRound(bloodBattleState.accept(round, seat, seat, tile, true, action.result()));
        pendingRiichiCommit = OptionalInt.empty(); submitted.clear(); claimDecisions.clear();
        continueBloodBattle(seat);
    }
    private void acceptBloodRons(List<Integer> seats) {
        var round = currentRound();
        int source = round.claimSourceSeat().orElseThrow();
        winningTile = round.activeTile();
        if (seats.size() == 3 && rules().sanchahouAbortive()) {
            if (pendingRiichiCommit.isPresent())
                round = bloodBattleState.cancelUncommittedRiichi(round, pendingRiichiCommit.getAsInt());
            match = matchWithRound(round.abortiveDraw(rules())); abortiveDraw = true;
            submitted.clear(); claimDecisions.clear(); pendingRiichiCommit = OptionalInt.empty();
            finishBloodBattle(false); return;
        }
        if (seats.size() > 1 && !rules().doubleRonAllowed()) seats = seats.subList(0, 1);
        var tile = phase instanceof MatchPhase.AwaitingClaims claims ? claims.heldTile()
                : winningTile instanceof TheMahjongRound.ActiveTile.HeldDiscard held ? held.tile()
                : winningTile instanceof TheMahjongRound.ActiveTile.HeldKakan held ? held.tile()
                : ((TheMahjongRound.ActiveTile.HeldKita)winningTile).tile();
        var actions = seats.stream().map(seat -> ronResultOf(claimDecisions.get(seat))).toList();
        int deposits = round.riichiSticks();
        int newDeposits = 0;
        if (pendingRiichiCommit.isPresent()) {
            boolean continues = bloodBattleState.winners().size() + seats.size() < players.size() - 1
                    && !round.liveWall().isEmpty();
            if (continues) {
                // Blood battle keeps a declaration that survives this response. The captured
                // ron results predate this payment, so award its new stick to only the first win.
                commitPendingRiichi();
                round = currentRound();
                newDeposits = 1;
            } else {
                round = bloodBattleState.cancelUncommittedRiichi(round, pendingRiichiCommit.getAsInt());
            }
        }
        if (winningTile instanceof TheMahjongRound.ActiveTile.HeldKakan || winningTile instanceof TheMahjongRound.ActiveTile.HeldKita)
            round = bloodBattleState.cancelRobbedDeclaration(round, rules());
        for (int i = 0; i < seats.size(); i++) {
            int seat = seats.get(i);
            var result = i == 0 ? bloodBattleState.withAdditionalDeposits(actions.get(i), seat, newDeposits)
                    : bloodBattleState.withoutDeposits(actions.get(i), seat, deposits);
            round = bloodBattleState.accept(round, seat, source, tile, false, result);
        }
        match = matchWithRound(round);
        submitted.clear(); claimDecisions.clear(); pendingRiichiCommit = OptionalInt.empty();
        continueBloodBattle(source);
    }
    private void continueBloodBattle(int after) {
        if (bloodBattleState.winners().size() >= players.size() - 1) { finishBloodBattle(false); return; }
        var round = currentRound();
        if (round.liveWall().isEmpty()) { finishBloodBattle(true); return; }
        int next = bloodBattleState.nextActive(after, players.size());
        match = matchWithRound(com.themahjong.variants.BloodBattleState.copy(round, TheMahjongRound.State.SETUP,
                next, -1, TheMahjongRound.ActiveTile.none(), round.riichiSticks(), round.players()));
        lastDrawWasRinshan = false;
        phase = new MatchPhase.AwaitingDraw(next, false);
    }
    private void finishBloodBattle(boolean exhaustive) {
        var round = currentRound();
        var ended = exhaustive ? bloodBattleState.exhaustive(round, rules())
                : com.themahjong.variants.BloodBattleState.copy(round, TheMahjongRound.State.ENDED,
                        round.currentTurnSeat(), -1, TheMahjongRound.ActiveTile.none(), round.riichiSticks(), round.players());
        match = matchWithRound(ended);
        var results = bloodBattleState.wins().stream().map(com.themahjong.variants.BloodBattleState.Win::result).toList();
        phase = new MatchPhase.RoundEnded(results);
        broadcast(new MatchEvent.RoundEnded(results));
    }
