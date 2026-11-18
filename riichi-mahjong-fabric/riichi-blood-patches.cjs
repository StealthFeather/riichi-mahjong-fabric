const fs=require('node:fs'),p=require('node:path');
module.exports=source=>{
 const replace=(from,to)=>{if(source.split(from).length!==2)throw Error('Riichi blood-battle patch no longer matches: '+from);source=source.replace(from,to);};
 replace('    private boolean animationsEnabled;','    private com.themahjong.variants.BloodBattleState bloodBattleState;\n    private boolean animationsEnabled;');
 replace('    public List<PlayerAction> legalActions(int seat) {',`    public List<PlayerAction> legalActions(int seat) {
        return bloodActions(seat, baseLegalActions(seat));
    }
    private List<PlayerAction> baseLegalActions(int seat) {
        if (bloodBattleState != null && bloodBattleState.won(seat)) return List.of();`);
 const init='        initExchangeRound();';
 if(source.split(init).length!==3)throw Error('Blood battle round initialization no longer matches');
 source=source.replaceAll(init,init+'\n        initBloodBattleRound();');
 replace('        this.phase = Objects.requireNonNull(snapshot.phase(), "snapshot.phase");','        this.phase = Objects.requireNonNull(snapshot.phase(), "snapshot.phase");\n        initBloodBattleRound();');
 replace('            int previousWins = sichuanState == null ? 0 : sichuanState.wins().size();','            int previousBloodWins = bloodBattleState == null ? 0 : bloodBattleState.wins().size();\n            int previousWins = sichuanState == null ? 0 : sichuanState.wins().size();');
 replace('            if (sichuanState != null && sichuanState.wins().size() > previousWins) return;','            if (sichuanState != null && sichuanState.wins().size() > previousWins) return;\n            if (bloodBattleState != null && bloodBattleState.wins().size() > previousBloodWins) return;');
 replace('        if (rules().sichuan()) return advanceSichuan(deltaSeconds, polledThisFrame);', `        if (rules().sichuan()) return advanceSichuan(deltaSeconds, polledThisFrame);
        if (bloodBattleState != null && phase instanceof MatchPhase.AwaitingDraw draw
                && !draw.rinshan() && currentRound().liveWall().isEmpty()) { finishBloodBattle(true); return true; }`);
 replace('    private void applyDiscardPhase(int seat, PlayerAction action) {',`    private void applyDiscardPhase(int seat, PlayerAction action) {
        if (bloodBattleState != null && action instanceof PlayerAction.DeclareTsumo win) { acceptBloodTsumo(seat, win); return; }
        if (bloodBattleState != null && action instanceof PlayerAction.KyuushuAbort) {
            match = matchWithRound(currentRound().abortiveDraw(rules())); abortiveDraw = true;
            finishBloodBattle(false); return;
        }`);
 replace('    private void applyRonResolution(List<Integer> ronSeats) {','    private void applyRonResolution(List<Integer> ronSeats) {\n        if (bloodBattleState != null) { acceptBloodRons(ronSeats); return; }');
 replace('    private void applyResolving() {', `    private void applyResolving() {
        if (bloodBattleState != null) {
            int source = currentRound().claimSourceSeat().orElseThrow();
            var winners = new ArrayList<Integer>();
            for (int offset = 1; offset < players.size(); offset++) {
                int seat = (source + offset) % players.size();
                var action = claimDecisions.get(seat);
                if (action instanceof PlayerAction.DeclareRon || action instanceof PlayerAction.DeclareChankan) winners.add(seat);
            }
            if (winners.isEmpty()) throw new IllegalStateException("blood battle resolution has no accepted winner");
            acceptBloodRons(winners); return;
        }`);
 replace('    private void checkExhaustiveDraw(TheMahjongRound round) {',`    private void checkExhaustiveDraw(TheMahjongRound round) {
        if (bloodBattleState != null) {
            if (round.liveWall().isEmpty()) { finishBloodBattle(true); return; }
            if (bloodBattleState.won(round.currentTurnSeat())) {
                int next = bloodBattleState.nextActive(round.currentTurnSeat(), players.size());
                round = com.themahjong.variants.BloodBattleState.copy(round, round.state(), next, -1,
                        round.activeTile(), round.riichiSticks(), round.players());
                match = matchWithRound(round);
            }
        }`);
 replace(`        if (round.state() == TheMahjongRound.State.ENDED) {
            abortiveDraw = true;`, `        if (round.state() == TheMahjongRound.State.ENDED) {
            if (bloodBattleState != null) { abortiveDraw = true; finishBloodBattle(false); return; }
            abortiveDraw = true;`);
 replace('    private List<PlayerAction> computeClaimActions(int seat, TheMahjongTile held, MatchPhase.ClaimSource source) {','    private List<PlayerAction> computeClaimActions(int seat, TheMahjongTile held, MatchPhase.ClaimSource source) {\n        if (bloodBattleState != null && bloodBattleState.won(seat)) return List.of();');
 const methods=fs.readFileSync(p.join(__dirname,'src/rules/riichi-blood-driver.inc'),'utf8');
 replace('    private TheMahjongMatch matchWithRound(TheMahjongRound updated) {',methods+'\n    private TheMahjongMatch matchWithRound(TheMahjongRound updated) {');
 return source;
};

// Reuse the original payment engine when blood battle has retired a pao responsibility seat.
module.exports.winCalculator=source=>{
 const replace=(from,to)=>{if(source.split(from).length!==2)throw Error('Blood-battle scoring patch no longer matches: '+from);source=source.replace(from,to);};
 replace(`        if (!(shape instanceof HandShape.Standard s)) return OptionalInt.empty();

        boolean hasDaisangen`, `        if (!(shape instanceof HandShape.Standard s)) return OptionalInt.empty();
        return findPaoSeat(s.melds(), yakumanList, rules);
    }

    public static OptionalInt findPaoSeat(List<TheMahjongMeld> melds, List<Yakuman> yakumanList, TheMahjongRuleSet rules) {
        boolean hasDaisangen`);
 replace('lastHonorGroupPaoSeat(s.melds(), TheMahjongTile.Suit.DRAGON)', 'lastHonorGroupPaoSeat(melds, TheMahjongTile.Suit.DRAGON)');
 replace('lastHonorGroupPaoSeat(s.melds(), TheMahjongTile.Suit.WIND)', 'lastHonorGroupPaoSeat(melds, TheMahjongTile.Suit.WIND)');
 replace('lastKanPaoSeat(s.melds())', 'lastKanPaoSeat(melds)');
 replace('    private static List<Integer> computeDeltas(', `    /** Restore ordinary payments if the blood-battle responsibility seat has already exited. */
    public static WinResult withoutResponsibility(WinResult result, boolean tsumo, int playerCount,
            int winner, int source, int dealer, int honba, int sticks, TheMahjongRuleSet rules) {
        int multiplier = result.yakuman().isEmpty()
                || (result.yakuman().size() == 1 && result.yakuman().get(0) == Yakuman.RENHOU)
                ? 1 : result.yakuman().size();
        var deltas = computeDeltas(result.han(), result.fu(), tsumo, winner == dealer,
                playerCount, winner, source, dealer, honba, sticks, rules.kiriageMangan(),
                OptionalInt.empty(), multiplier, rules.tsumoLoss());
        return new WinResult(result.yaku(), result.yakuman(), result.han(), result.fu(), result.doraCount(), deltas);
    }

    private static List<Integer> computeDeltas(`);
 return source;
};
