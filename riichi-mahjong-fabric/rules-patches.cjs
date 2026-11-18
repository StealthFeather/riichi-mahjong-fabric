// Keep upstream/ byte-for-byte; apply small, checked fixes to compiled rule sources.
module.exports=function patchDriver(source){
 const replace=(from,to)=>{
  if(source.split(from).length!==2)throw Error('Upstream driver patch no longer matches: '+from);
  source=source.replace(from,to);
 };
 replace('if (!c.pendingSeats().contains(seat)) return List.of();',
         'if (!c.pendingSeats().contains(seat) || claimDecisions.containsKey(seat)) return List.of();');
 replace('        commitPendingRiichi();\n\n        for (int offset',
         '        commitPendingRiichi();\n        round = currentRound(); // Preserve the accepted deposit when a declaration discard is called.\n\n        for (int offset');
 replace('        claimDecisions.remove(seat);',
         '        // Keep an already accepted claim when its human controller leaves.');
 // Preserve display metadata at the exact accepted action, including multiple actions in one advance().
 replace('    private boolean animationsEnabled;',
         `    private boolean animationsEnabled;
    private java.util.function.Consumer<TheMahjongDriver> stateObserver = driver -> {};
    public void setStateObserver(java.util.function.Consumer<TheMahjongDriver> observer) {
        stateObserver = java.util.Objects.requireNonNull(observer);
    }
    private boolean abortiveDraw;
    public boolean abortiveDraw() {
        return phase instanceof MatchPhase.RoundEnded ended && ended.winResults().isEmpty() && abortiveDraw;
    }
    private TheMahjongRound.ActiveTile winningTile = TheMahjongRound.ActiveTile.none();
    public TheMahjongRound.ActiveTile winningTile() {
        return phase instanceof MatchPhase.RoundEnded ended && !ended.winResults().isEmpty()
                ? winningTile : TheMahjongRound.ActiveTile.none();
    }
    /** Accepted-action metadata also remains available while a Sichuan blood battle continues. */
    public TheMahjongRound.ActiveTile acceptedWinningTile() { return winningTile; }`);
 // Stop at an accepted blood-battle win so the table can display it before polling another player.
 replace(`        while (progress) {
            progress = advanceOnce(deltaSeconds, polledThisFrame);
        }`,
         `        while (progress) {
            int previousWins = sichuanState == null ? 0 : sichuanState.wins().size();
            TheMahjongMatch beforeMatch = match;
            MatchPhase beforePhase = phase;
            long beforeSichuanRevision = sichuanState == null ? -1 : sichuanState.revision();
            progress = advanceOnce(deltaSeconds, polledThisFrame);
            if (beforeMatch != match || !beforePhase.equals(phase)
                    || (sichuanState != null && sichuanState.revision() != beforeSichuanRevision)) stateObserver.accept(this);
            if (sichuanState != null && sichuanState.wins().size() > previousWins) return;
        }`);
 const roundStart='                : new MatchPhase.AwaitingDraw(round.currentTurnSeat(), false);';
 if(source.split(roundStart).length!==3)throw Error('Upstream driver patch no longer matches: replay round starts');
 source=source.replaceAll(roundStart,roundStart+'\n        stateObserver.accept(this);');
 replace('            broadcast(new MatchEvent.MatchEnded());',
         '            broadcast(new MatchEvent.MatchEnded());\n            stateObserver.accept(this);');
 replace('            round = round.declareWin(seat, seat, t.result());',
         '            winningTile = round.activeTile();\n            round = round.declareWin(seat, seat, t.result());');
 replace('    private void applyRonResolution(List<Integer> ronSeats) {\n        TheMahjongRound round = currentRound();',
         '    private void applyRonResolution(List<Integer> ronSeats) {\n        TheMahjongRound round = currentRound();\n        winningTile = round.activeTile();');
 // Keep the reason separate: a last-tile abortive draw also has an empty live wall.
 replace('        this.lastDrawWasRinshan = snapshot.lastDrawWasRinshan();',
         '        this.lastDrawWasRinshan = snapshot.lastDrawWasRinshan();\n        this.abortiveDraw = snapshot.abortiveDraw();');
 replace(`            OptionalInt pendingRiichiCommit) {
    }`, `            OptionalInt pendingRiichiCommit,
            boolean abortiveDraw) {
        public Snapshot(MatchPhase phase, boolean lastDrawWasRinshan, OptionalInt pendingRiichiCommit) {
            this(phase, lastDrawWasRinshan, pendingRiichiCommit, false);
        }
    }`);
 replace('        return new Snapshot(phase, lastDrawWasRinshan, pendingRiichiCommit);',
         '        return new Snapshot(phase, lastDrawWasRinshan, pendingRiichiCommit, abortiveDraw);');
 const abort='            round = round.abortiveDraw(rules());';
 if(source.split(abort).length!==3)throw Error('Upstream driver patch no longer matches: explicit abortive draws');
 source=source.replaceAll(abort,abort+'\n            abortiveDraw = true;');
 replace(`        if (round.state() == TheMahjongRound.State.ENDED) {
            phase = new MatchPhase.RoundEnded(List.of());`,
         `        if (round.state() == TheMahjongRound.State.ENDED) {
            abortiveDraw = true;
            phase = new MatchPhase.RoundEnded(List.of());`);
 replace('        match = match.advanceRound(random, renchan, nextHonba);',
         '        match = match.advanceRound(random, renchan, nextHonba);\n        abortiveDraw = false;');
 return require('./analysis-patches.cjs')(require('./competition-patches.cjs').driver(require('./riichi-blood-patches.cjs')(require('./exchange-patches.cjs')(require('./joker-patches.cjs').driver(require('./sichuan-patches.cjs').driver(source))))));
};

module.exports.round=function patchRound(source){
 const from=`        List<TheMahjongPlayer> newPlayers = players;
        if (rules.nagashiManganAllowed()) {`;
 const to=`        List<TheMahjongPlayer> newPlayers = players;
        boolean nagashiSettled = false;
        if (rules.nagashiManganAllowed()) {`;
 if(source.split(from).length!==2)throw Error('Upstream round patch no longer matches: exhaustive draw entry');
 source=source.replace(from,to);
 const nagashi=`                newPlayers = applyNagashiManganDeltas(winners);
            }
        }
        return new TheMahjongRound(`;
 const settlement=`                newPlayers = applyNagashiManganDeltas(winners);
                nagashiSettled = true;
            }
        }
        // Exhaustive draws settle directly into player points, as nagashi does.
        // Abortive draws have their own path and must never pay noten penalties.
        if (!nagashiSettled) {
            newPlayers = com.themahjong.compat.DrawSettlement.applyNotenPayments(players);
        }
        return new TheMahjongRound(`;
 if(source.split(nagashi).length!==2)throw Error('Upstream round patch no longer matches: nagashi settlement');
 return require('./competition-patches.cjs').round(require('./sichuan-patches.cjs').round(source.replace(nagashi,settlement)));
};

module.exports.ruleSet=source=>require('./competition-patches.cjs').ruleSet(require('./sichuan-patches.cjs').ruleSet(source));
module.exports.nonYakuman=require('./sichuan-patches.cjs').nonYakuman;

module.exports.match=source=>require('./competition-patches.cjs').match(require('./sichuan-patches.cjs').match(source));
module.exports.tile=require('./joker-patches.cjs').tile;
module.exports.handShape=require('./joker-patches.cjs').handShape;
module.exports.furiten=require('./joker-patches.cjs').furiten;
module.exports.winCalculator=source=>require('./competition-patches.cjs').winCalculator(require('./riichi-blood-patches.cjs').winCalculator(require('./joker-patches.cjs').winCalculator(source)));
module.exports.winResultBuilder=source=>require('./competition-patches.cjs').winResultBuilder(require('./joker-patches.cjs').winResultBuilder(source));

module.exports.meld=require('./competition-patches.cjs').meld;
