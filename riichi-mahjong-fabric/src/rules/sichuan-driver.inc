    public com.themahjong.sichuan.SichuanState sichuanState() { return sichuanState; }

    public void restoreSichuanState(com.themahjong.sichuan.SichuanState.Snapshot snapshot) {
        if (!rules().sichuan()) throw new IllegalStateException("not a Sichuan match");
        sichuanState = new com.themahjong.sichuan.SichuanState(snapshot);
    }

    public Map<Integer,Integer> sichuanClaimDecisions() {
        var out = new HashMap<Integer,Integer>();
        if (sichuanState != null && phase instanceof MatchPhase.AwaitingClaims) {
            claimDecisions.forEach((seat, action) -> {
                int index = sichuanState.legalActions(currentRound(), phase, rules(), seat).indexOf(action);
                if (index >= 0) out.put(seat, index);
            });
        }
        return Map.copyOf(out);
    }

    public void restoreSichuanClaims(Map<Integer,Integer> indices) {
        if (sichuanState == null || !(phase instanceof MatchPhase.AwaitingClaims)) return;
        for (var entry : indices.entrySet()) {
            var actions = sichuanState.legalActions(currentRound(), phase, rules(), entry.getKey());
            int index = entry.getValue();
            if (index >= 0 && index < actions.size()) claimDecisions.put(entry.getKey(), actions.get(index));
        }
    }

    private void initSichuanRound() {
        if (!rules().sichuan() || match.state() != TheMahjongMatch.State.IN_ROUND
                || currentRound().state() == TheMahjongRound.State.ENDED) return;
        sichuanState = new com.themahjong.sichuan.SichuanState(rules().exchangeThree(), 1 + random.nextInt(3));
        match = matchWithRound(com.themahjong.sichuan.SichuanRules.prepareRound(currentRound()));
        submitted.clear(); claimDecisions.clear();
    }

    private boolean canConfigureSichuan() {
        return sichuanState != null && !(phase instanceof MatchPhase.NotStarted)
                && !(phase instanceof MatchPhase.Dealing) && !(phase instanceof MatchPhase.RoundEnded)
                && !(phase instanceof MatchPhase.MatchEnded);
    }
    private void touchSichuanRound() {
        var round = currentRound();
        match = matchWithRound(com.themahjong.sichuan.SichuanRules.copy(round, round.state(),
                round.currentTurnSeat(), round.claimSourceSeat().orElse(-1), round.activeTile(),
                round.liveWall(), round.players()));
    }
    public boolean toggleExchangeTile(int seat, TheMahjongTile tile) {
        if (!canConfigureSichuan() || !sichuanState.toggleExchangeTile(currentRound(), seat, tile)) return false;
        touchSichuanRound(); stateObserver.accept(this); return true;
    }
    public boolean clearExchangeSelection(int seat) {
        if (!canConfigureSichuan() || !sichuanState.clearExchangeSelection(seat)) return false;
        touchSichuanRound(); stateObserver.accept(this); return true;
    }
    public boolean confirmExchange(int seat) {
        if (!canConfigureSichuan() || !sichuanState.confirmExchange(currentRound(), seat)) return false;
        if (sichuanState.exchangeReady()) match = matchWithRound(sichuanState.exchange(currentRound()));
        else touchSichuanRound();
        stateObserver.accept(this);
        return true;
    }
    public boolean chooseVoidSuit(int seat, TheMahjongTile.Suit suit) {
        if (!canConfigureSichuan() || !sichuanState.chooseVoidSuit(seat, suit)) return false;
        touchSichuanRound(); stateObserver.accept(this); return true;
    }

    private boolean advanceSichuan(double deltaSeconds, Set<Integer> polledThisFrame) {
        if (sichuanState == null) initSichuanRound();
        if (sichuanState == null) return false;
        if (sichuanState.stage() != com.themahjong.sichuan.SichuanState.Stage.PLAY) {
            long before = sichuanState.revision();
            if (sichuanState.stage() == com.themahjong.sichuan.SichuanState.Stage.EXCHANGE) {
                for (int seat = 0; seat < players.size(); seat++) if (!(players.get(seat) instanceof MahjongHumanPlayer))
                    sichuanState.automaticExchange(currentRound(), seat);
                if (sichuanState.exchangeReady()) {
                    match = matchWithRound(sichuanState.exchange(currentRound()));
                    // The shared loop yields here so the table can animate the accepted swap before dingque.
                    return true;
                }
            }
            if (sichuanState.stage() == com.themahjong.sichuan.SichuanState.Stage.VOID_SUIT) {
                for (int seat = 0; seat < players.size(); seat++) if (!(players.get(seat) instanceof MahjongHumanPlayer))
                    sichuanState.automaticVoidSuit(currentRound(), seat);
            }
            if (before != sichuanState.revision()) touchSichuanRound();
            return before != sichuanState.revision();
        }
        if (phase instanceof MatchPhase.AwaitingDraw ad) {
            PlayerAction action = pollSeat(ad.seat(), deltaSeconds, polledThisFrame);
            if (action == null) return false;
            applySichuanUpdate(sichuanState.apply(currentRound(), phase, rules(), ad.seat(), action));
            return true;
        }
        if (phase instanceof MatchPhase.AwaitingDiscard ad) {
            PlayerAction action = pollSeat(ad.seat(), deltaSeconds, polledThisFrame);
            if (action == null) return false;
            if (action instanceof PlayerAction.DeclareTsumo) winningTile = currentRound().activeTile();
            applySichuanUpdate(sichuanState.apply(currentRound(), phase, rules(), ad.seat(), action));
            return true;
        }
        if (phase instanceof MatchPhase.AwaitingClaims ac) {
            boolean changed = false;
            for (int seat : ac.pendingSeats()) {
                if (claimDecisions.containsKey(seat)) continue;
                PlayerAction action = pollSeat(seat, deltaSeconds, polledThisFrame);
                if (action == null) continue;
                claimDecisions.put(seat, action); changed = true;
            }
            if (claimDecisions.size() >= ac.pendingSeats().size()) {
                winningTile = currentRound().activeTile();
                var update = sichuanState.resolveClaims(currentRound(), ac, rules(), claimDecisions);
                claimDecisions.clear(); submitted.clear(); applySichuanUpdate(update); return true;
            }
            if (changed) touchSichuanRound();
            return changed;
        }
        return false;
    }
    private void applySichuanUpdate(com.themahjong.sichuan.SichuanState.Update update) {
        match = matchWithRound(update.round()); phase = update.phase();
        for (var event : update.events()) broadcast(event);
    }
