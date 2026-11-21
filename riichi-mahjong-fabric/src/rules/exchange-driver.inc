    public com.themahjong.variants.ExchangeThreeState exchangeState() { return exchangeState; }
    public void restoreExchangeState(com.themahjong.variants.ExchangeThreeState.Snapshot snapshot) {
        if (rules().sichuan() || !rules().exchangeThree() || snapshot.playerCount() != match.playerCount())
            throw new IllegalArgumentException("exchange state does not match rules");
        exchangeState = new com.themahjong.variants.ExchangeThreeState(snapshot);
    }
    private void initExchangeRound() {
        exchangeState = !rules().sichuan() && rules().exchangeThree()
                && match.state() == TheMahjongMatch.State.IN_ROUND && currentRound().state() != TheMahjongRound.State.ENDED
                ? new com.themahjong.variants.ExchangeThreeState(match.playerCount(), 1 + random.nextInt(match.playerCount() - 1)) : null;
    }
    public boolean exchangeInProgress() { return exchangeState != null && !exchangeState.complete(); }
    public boolean pendingExchange(int seat) {
        return exchangeState != null ? exchangeState.pending(seat) : sichuanState != null && sichuanState.pendingExchange(seat);
    }
    public List<TheMahjongTile> exchangeSelection(int seat) {
        return exchangeState != null ? (exchangeInProgress() ? exchangeState.selection(seat) : List.of()) : sichuanState != null && sichuanState.stage() == com.themahjong.sichuan.SichuanState.Stage.EXCHANGE ? sichuanState.exchangeSelection(seat) : List.of();
    }
    public List<com.themahjong.variants.ExchangeTile> exchangePicks(int seat) {
        if (exchangeInProgress()) return exchangeState.picks(seat);
        if (sichuanState != null && sichuanState.stage() == com.themahjong.sichuan.SichuanState.Stage.EXCHANGE)
            return sichuanState.exchangePicks(seat);
        return List.of();
    }
    public boolean toggleExchangeSlot(int seat, int index) {
        if (!pendingExchange(seat)) return false;
        var round = currentRound();
        var hand = round.handDisplayOrder(seat);
        if (index < 0 || index >= hand.size()) return false;
        var pick = com.themahjong.variants.ExchangeTile.at(hand, index);
        if (exchangeState != null) {
            if (!canConfigureExchange() || !exchangeState.toggleCopy(round, seat, pick)) return false;
            touchExchangeRound();
        } else {
            if (!canConfigureSichuan() || !sichuanState.toggleExchangeCopy(round, seat, pick)) return false;
            touchSichuanRound();
        }
        stateObserver.accept(this);
        return true;
    }
    private boolean canConfigureExchange() {
        return exchangeInProgress() && !(phase instanceof MatchPhase.NotStarted) && !(phase instanceof MatchPhase.Dealing)
                && !(phase instanceof MatchPhase.RoundEnded) && !(phase instanceof MatchPhase.MatchEnded);
    }
    private void touchExchangeRound() {
        match = matchWithRound(com.themahjong.variants.ExchangeThreeState.copy(currentRound(), currentRound().players()));
    }
    private boolean advanceExchange() {
        long before = exchangeState.revision();
        for (int seat = 0; seat < players.size(); seat++) if (!(players.get(seat) instanceof MahjongHumanPlayer))
            exchangeState.automatic(currentRound(), seat);
        if (exchangeState.ready()) match = matchWithRound(exchangeState.exchange(currentRound()));
        else if (before != exchangeState.revision()) touchExchangeRound();
        return before != exchangeState.revision();
    }
