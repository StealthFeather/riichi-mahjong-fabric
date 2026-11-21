// Keep upstream/ byte-for-byte; apply small, checked fixes to compiled rule sources.
module.exports=function patchDriver(source){
 const replace=(from,to)=>{
  if(source.split(from).length!==2)throw Error('Upstream driver patch no longer matches: '+from);
  source=source.replace(from,to);
 };
 // Standard four-player replacement draws shorten the live wall at its back.
 replace('        round = round.draw();',
         '        if (fromRinshan && com.themahjong.compat.KanWall.applies(match)) round = com.themahjong.compat.KanWall.reserve(round, 1);\n        round = round.draw();');
 replace('        Map<TileKey, List<TheMahjongTile>> byKind = groupBySuitRank(player.currentHand());',
         '        if (round.liveWall().isEmpty() && com.themahjong.compat.KanWall.applies(match)) return List.copyOf(out);\n        Map<TileKey, List<TheMahjongTile>> byKind = groupBySuitRank(player.currentHand());');
 // A final discard can still be claimed for ron. Chi, pon and kan cannot follow it.
 replace('        if (player.riichi()) return List.copyOf(out);',
         '        if (player.riichi() || (round.liveWall().isEmpty() && com.themahjong.compat.KanWall.applies(match))) return List.copyOf(out);');
 replace('if (!c.pendingSeats().contains(seat)) return List.of();',
         'if (!c.pendingSeats().contains(seat) || claimDecisions.containsKey(seat)) return List.of();');
 replace('        commitPendingRiichi();\n\n        for (int offset',
         '        commitPendingRiichi();\n        round = currentRound(); // Preserve the accepted deposit when a declaration discard is called.\n\n        for (int offset');
 replace('        claimDecisions.remove(seat);',
         '        // Keep an already accepted claim when its human controller leaves.');
 // A combined multi-ron settlement can pay and charge the same seat (responsibility).
 // Record each accepted result only after the engine commits their combined score.
 replace('        round = round.resolveRons();\n        match = matchWithRound(round);',
         '        round = round.resolveRons();\n        match = matchWithRound(round);\n        for (var win : results) com.themahjong.compat.PointPayments.fromDeltas(win.pointDeltas());');
 // Preserve display metadata at the exact accepted action, including multiple actions in one advance().
 replace('    private boolean animationsEnabled;',
         `    private boolean animationsEnabled;
    /** Migrate only the live hand. Historical replay frames retain their recorded wall. */
    public void restoreLegacyKanWall() {
        if (!com.themahjong.compat.KanWall.applies(match) || match.currentRound().isEmpty()) return;
        var round = currentRound();
        if (round.state() == TheMahjongRound.State.ENDED) return;
        int used = TheMahjongRound.rinshanTileCountFor(match.playerCount()) - round.rinshanTiles().size();
        int reserved = Math.min(used, round.liveWall().size());
        if (reserved <= 0) return;
        round = com.themahjong.compat.KanWall.reserve(round, reserved);
        match = matchWithRound(round);
        if (phase instanceof MatchPhase.AwaitingDraw draw && !draw.rinshan() && round.liveWall().isEmpty())
            checkExhaustiveDraw(round);
    }
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
 replace('    public void advanceRound(boolean renchan, int nextHonba) {',
         `    public void advanceRound(boolean renchan, int nextHonba) {
        advanceRound(renchan, nextHonba, Map.of());
    }

    public void advanceRound(boolean renchan, int nextHonba, Map<Integer, Integer> shantenTargets) {`);
 replace('        match = match.advanceRound(random, renchan, nextHonba);',
         '        match = match.advanceRound(random, renchan, nextHonba, shantenTargets);\n        abortiveDraw = false;');
 return require('./analysis-patches.cjs')(require('./competition-patches.cjs').driver(require('./riichi-blood-patches.cjs')(require('./exchange-patches.cjs')(require('./joker-patches.cjs').driver(require('./sichuan-patches.cjs').driver(source))))));
};

module.exports.round=function patchRound(source){
 const declaration='    public TheMahjongRound declareRiichiIntent(TheMahjongRuleSet rules) {';
 if(source.split(declaration).length!==2)throw Error('Upstream round patch no longer matches: riichi intent');
 source=source.replace(declaration,`    public boolean isUninterruptedFirstTurn(int seat) {
        return players.get(seat).discards().isEmpty()
                && players.stream().allMatch(player -> player.melds().isEmpty() && player.kitaCount() == 0);
    }

`+declaration);
 const doubleRiichi=`        boolean noMeldsAnyPlayer = players.stream().allMatch(p -> p.melds().isEmpty());
        TheMahjongPlayer.RiichiState newState = (player.discards().isEmpty() && noMeldsAnyPlayer)`;
 if(source.split(doubleRiichi).length!==3)throw Error('Upstream round patch expected both riichi declarations');
 source=source.replaceAll(doubleRiichi,'        TheMahjongPlayer.RiichiState newState = isUninterruptedFirstTurn(currentTurnSeat)');
 const insertAfter=(anchor,statement)=>{
  if(source.split(anchor).length!==2)throw Error('Upstream payment patch no longer matches: '+anchor);
  source=source.replace(anchor,anchor+'\n'+statement);
 };
 insertAfter('                deltas[i] -= pays;',
             '                com.themahjong.compat.PointPayments.paid(i, pays);');
 insertAfter('        requireYakuPresent(result, "declareWin");',
             '        com.themahjong.compat.PointPayments.fromDeltas(result.pointDeltas());');
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

module.exports.match=source=>{
 const advance='    public TheMahjongMatch advanceRound(Random random, boolean renchan, int nextHonba) {';
 const deal='        TheMahjongRound nextRound = TheMahjongRound.start(';
 if(source.split(advance).length!==2||source.split(deal).length!==2)
  throw Error('Upstream match patch no longer matches: next round deal');
 source=source.replace(advance,advance+`
        return advanceRound(random, renchan, nextHonba, java.util.Map.of());
    }

    public TheMahjongMatch advanceRound(Random random, boolean renchan, int nextHonba,
            java.util.Map<Integer, Integer> shantenTargets) {`);
 source=source.replace(deal,`        if (!shantenTargets.isEmpty())
            wall = com.themahjong.compat.StartingShantenDeal.arrange(wall, shantenTargets, random);
`+deal);
 return require('./competition-patches.cjs').match(require('./sichuan-patches.cjs').match(source));
};
module.exports.tile=require('./joker-patches.cjs').tile;
module.exports.handShape=require('./joker-patches.cjs').handShape;
module.exports.furiten=require('./joker-patches.cjs').furiten;
module.exports.winCalculator=source=>require('./competition-patches.cjs').winCalculator(require('./riichi-blood-patches.cjs').winCalculator(require('./joker-patches.cjs').winCalculator(source)));
module.exports.winResultBuilder=source=>{
 const firstTurn=`round.players().stream().allMatch(p -> p.melds().isEmpty())
                && player.discards().isEmpty()`;
 if(source.split(firstTurn).length!==3)throw Error('Upstream scoring patch expected first-turn checks for ron and tsumo');
 source=source.replaceAll(firstTurn,'round.isUninterruptedFirstTurn(seat)');
 return require('./competition-patches.cjs').winResultBuilder(require('./joker-patches.cjs').winResultBuilder(source));
};

module.exports.meld=require('./competition-patches.cjs').meld;
