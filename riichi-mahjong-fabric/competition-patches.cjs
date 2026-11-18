// Additional tournament profiles are compiled over the pinned upstream sources.
const replace=(source,from,to,name)=>{
  if(source.split(from).length!==2)throw Error('Competition patch no longer matches: '+name);
  return source.replace(from,to);
};
exports.ruleSet=source=>{
  const start=source.indexOf('public record TheMahjongRuleSet('),end=source.indexOf(') {',start);
  const names=[...source.slice(start,end).matchAll(/boolean (\w+)/g)].map(m=>m[1]);
  if(names.length!==24)throw Error('Competition patch expected 24 pre-existing rule fields');
  source=replace(source,'        boolean bloodBattle\n','        boolean bloodBattle,\n        com.themahjong.competition.CompetitiveRules.Profile competitiveProfile\n','profile field');
  const constructor=`    public TheMahjongRuleSet(${names.map(n=>'boolean '+n).join(', ')}) {\n        this(${names.join(', ')}, com.themahjong.competition.CompetitiveRules.Profile.STANDARD);\n    }\n\n`;
  const helpers=`    public TheMahjongRuleSet withCompetitiveProfile(com.themahjong.competition.CompetitiveRules.Profile profile) {
        return new TheMahjongRuleSet(${names.join(', ')}, java.util.Objects.requireNonNull(profile));
    }
    public boolean ippatsuAllowed() { return competitiveProfile != com.themahjong.competition.CompetitiveRules.Profile.JPML_A; }
    public boolean uraDoraAllowed() { return ippatsuAllowed(); }
    public boolean kanDoraAllowed() { return ippatsuAllowed(); }
    public boolean redDoraAllowed() { return ippatsuAllowed(); }
    public boolean countedYakumanAllowed() { return competitiveProfile != com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE; }
    public boolean multipleYakumanAllowed() { return true; }
    public boolean dealerTenpaiContinues() { return true; }
    public int minimumRiichiWallTiles() { return competitiveProfile == com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE ? 1 : 4; }

`;
  return replace(source,'    public static TheMahjongRuleSet wrc()',constructor+helpers+'    public static TheMahjongRuleSet wrc()','legacy variant constructor');
};

exports.driver=source=>{
  source=replace(source,`        Set<TileKey> seen = new HashSet<>();
        for (TheMahjongTile t : player.currentHand()) {`,
    `        Set<TileKey> seen = new HashSet<>();
        for (TheMahjongTile t : player.currentHand()) {
            if (player.riichi() && (!(round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn drawn)
                    || !t.equals(drawn.tile()))) continue;`,'accepted riichi exposes only drawn discard');
  source=replace(source,'&& player.points() >= 1000','&& (!rules().riichiRequires1000Points() || player.points() >= 1000)','rule-specific riichi balance');
  source=replace(source,'&& round.liveWall().size() >= 4','&& round.liveWall().size() >= rules().minimumRiichiWallTiles()','riichi wall minimum');
  source=replace(source,'        winningTile = round.activeTile();\n        int discarder = round.claimSourceSeat().orElseThrow();',
    `        winningTile = round.activeTile();
        if (rules().competitiveProfile() != com.themahjong.competition.CompetitiveRules.Profile.STANDARD
                && round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKakan) {
            round = com.themahjong.competition.CompetitiveRules.cancelRobbedKakan(round);
            match = matchWithRound(round);
        }
        int discarder = round.claimSourceSeat().orElseThrow();`,'rollback robbed physical kan');
  if(source.split('.skipKakanClaims()').length!==3)throw Error('Competition expected two kakan acceptance paths');
  source=source.replaceAll('.skipKakanClaims()', '.skipKakanClaims(rules())');
  return replace(source,'        if (source != MatchPhase.ClaimSource.DISCARD) return List.copyOf(out);',
    `        if (rules().competitiveProfile() != com.themahjong.competition.CompetitiveRules.Profile.STANDARD && round.liveWall().isEmpty()) return List.copyOf(out);
        if (source != MatchPhase.ClaimSource.DISCARD) return List.copyOf(out);`,'no last-discard melds');
};
exports.round=source=>{
  source=replace(source,`        TheMahjongPlayer discardingPlayer = players.get(currentTurnSeat);
        List<TheMahjongTile> newHand = new ArrayList<>(discardingPlayer.currentHand());
        int index = newHand.indexOf(tile);`,
    `        TheMahjongPlayer discardingPlayer = players.get(currentTurnSeat);
        boolean acceptedRiichi = discardingPlayer.riichi()
                && discardingPlayer.discards().stream().anyMatch(TheMahjongDiscard::riichiDeclared);
        if (acceptedRiichi && (!(activeTile instanceof ActiveTile.Drawn drawn) || !tile.equals(drawn.tile())))
            throw new IllegalStateException("accepted riichi requires the exact drawn discard");
        List<TheMahjongTile> newHand = new ArrayList<>(discardingPlayer.currentHand());
        int index = acceptedRiichi ? newHand.lastIndexOf(tile) : newHand.indexOf(tile);`,'lock accepted riichi hand at mutation boundary');
  for(const declaration of ['public TheMahjongRound declareAnkan(List<TheMahjongTile> handTiles, TheMahjongRuleSet rules) {',
      'public TheMahjongRound declareKakan(TheMahjongMeld.Pon existingPon, TheMahjongTile addedTile, TheMahjongRuleSet rules) {'])
    source=replace(source,declaration,declaration+`
        if (!(activeTile instanceof ActiveTile.Drawn))
            throw new IllegalStateException("self kan requires a drawn tile");`,'self-kan requires draw');
  const limit='if (revealedDoraCount + pendingKanDoraReveals >= MAX_DORA_INDICATORS) {';
  if(source.split(limit).length!==4)throw Error('Competition patch expected three kan guards');
  source=source.replaceAll(limit,`if (rules.competitiveProfile() != com.themahjong.competition.CompetitiveRules.Profile.STANDARD && liveWall.isEmpty())
            throw new IllegalStateException("cannot kan the last live-wall tile");
        if (rules.kanDoraAllowed() ? revealedDoraCount + pendingKanDoraReveals >= MAX_DORA_INDICATORS
                : players.stream().flatMap(p -> p.melds().stream()).filter(m -> m instanceof TheMahjongMeld.Ankan
                        || m instanceof TheMahjongMeld.Daiminkan || m instanceof TheMahjongMeld.Kakan).count() >= 4) {`);
  source=replace(source,`TheMahjongMeld meld = new TheMahjongMeld.Daiminkan(
                allTiles, allTiles.size() - 1, claimSourceSeat, sourceDiscardIndex);`,
    `TheMahjongMeld meld = new TheMahjongMeld.Daiminkan(
                allTiles, allTiles.size() - 1, claimSourceSeat, sourceDiscardIndex,
                rules.competitiveProfile() == com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE
                        && players.get(claimantSeat).melds().stream().filter(m -> m instanceof TheMahjongMeld.Ankan
                                || m instanceof TheMahjongMeld.Daiminkan || m instanceof TheMahjongMeld.Kakan).count() == 3);`,'record accepted fourth open kan');
  const revealed='int newRevealed = rules.openKanDoraDelayedReveal() ? revealedDoraCount : revealedDoraCount + 1;';
  const pending='int newPending  = rules.openKanDoraDelayedReveal() ? pendingKanDoraReveals + 1 : pendingKanDoraReveals;';
  if(source.split(revealed).length!==3||source.split(pending).length!==3)throw Error('Competition patch expected two open-kan dora counters');
  source=source.replaceAll(revealed,'int newRevealed = !rules.kanDoraAllowed() || rules.openKanDoraDelayedReveal() ? revealedDoraCount : revealedDoraCount + 1;')
    .replaceAll(pending,'int newPending  = rules.kanDoraAllowed() && rules.openKanDoraDelayedReveal() ? pendingKanDoraReveals + 1 : pendingKanDoraReveals;');
  const begin=source.indexOf('    public TheMahjongRound declareKakan('),end=source.indexOf('    public TheMahjongRound skipKakanClaims()',begin);
  let kakan=source.slice(begin,end);
  kakan=replace(kakan,'int newRevealed = !rules.kanDoraAllowed() || rules.openKanDoraDelayedReveal()',
    'int newRevealed = !rules.kanDoraAllowed() || rules.competitiveProfile() == com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE || rules.openKanDoraDelayedReveal()','hold unaccepted kakan dora');
  kakan=replace(kakan,'int newPending  = rules.kanDoraAllowed() && rules.openKanDoraDelayedReveal()',
    'int newPending  = rules.kanDoraAllowed() && (rules.competitiveProfile() == com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE || rules.openKanDoraDelayedReveal())','pending unaccepted kakan dora');
  source=source.slice(0,begin)+kakan+source.slice(end);
  source=replace(source,'    public TheMahjongRound skipKakanClaims() {',
    `    public TheMahjongRound skipKakanClaims(TheMahjongRuleSet rules) {
        TheMahjongRound accepted = skipKakanClaims();
        return rules.competitiveProfile() == com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE
                ? com.themahjong.competition.CompetitiveRules.revealAcceptedKan(accepted) : accepted;
    }

    public TheMahjongRound skipKakanClaims() {`,'accepted kakan reveals immediately');
  source=replace(source,'            validateRiichiAnkan(player, ankan);',
    `            validateRiichiAnkan(player, ankan);
            if (rules.competitiveProfile() != com.themahjong.competition.CompetitiveRules.Profile.STANDARD)
                com.themahjong.competition.CompetitiveRules.validateRiichiAnkan(player, ankan, activeTile);`,'competitive riichi kan decomposition');
  return replace(source,'liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount + 1,',
    'liveWall, rinshanTiles, doraIndicators, uraDoraIndicators, revealedDoraCount + (rules.kanDoraAllowed() ? 1 : 0),','closed-kan dora');
};
exports.winResultBuilder=source=>{
  source=replace(source,'&& !rules.openKanDoraDelayedReveal()) {','&& rules.kanDoraAllowed() && rules.competitiveProfile() != com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE && !rules.openKanDoraDelayedReveal()) {','robbed kan dora');
  return replace(source,'List<TheMahjongTile> ura = player.riichi()','List<TheMahjongTile> ura = player.riichi() && rules.uraDoraAllowed()','profile ura');
};
exports.winCalculator=source=>{
  source=replace(source,'int doraCount = countDora(allWinnerTiles, doraIndicators, uraDoraIndicators, playerCount);',
    `int doraCount = countDora(allWinnerTiles,
                rules.kanDoraAllowed() ? doraIndicators : doraIndicators.stream().limit(1).toList(),
                rules.uraDoraAllowed() ? uraDoraIndicators : List.of(), playerCount);
        if (!rules.redDoraAllowed()) doraCount -= (int)allWinnerTiles.stream().filter(TheMahjongTile::redDora).count();`,'profile dora count');
  source=replace(source,'yakuList = NonYakuman.check(shape, ctx);',
    'yakuList = NonYakuman.check(shape, ctx).stream().filter(y -> y != NonYakuman.IPPATSU || rules.ippatsuAllowed()).toList();','profile ippatsu');
  source=replace(source,': yakumanList.size();',': rules.multipleYakumanAllowed() ? yakumanList.size() : 1;','composite yakuman multiplier');
  source=replace(source,'han, fu, ctx.tsumo(), ctx.dealer(),',
    '(yakumanList.isEmpty() && !rules.countedYakumanAllowed() ? Math.min(12, han) : han), fu, ctx.tsumo(), ctx.dealer(),','counted yakuman cap');
  source=replace(source,'? 1 : result.yakuman().size();','? 1 : rules.multipleYakumanAllowed() ? result.yakuman().size() : 1;','blood composite multiplier');
  source=replace(source,'            WinResult candidate = new WinResult(yakuList, yakumanList, han, fu, doraCount, deltas);',
    `            if (rules.competitiveProfile() == com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE && !yakumanList.isEmpty())
                deltas = competitiveYakumanDeltas(shape,yakumanList,fu,ctx.tsumo(),playerCount,winnerSeat,loserSeat,dealerSeat,honba,riichiSticks,rules,deltas);
            WinResult candidate = new WinResult(yakuList, yakumanList, han, fu, doraCount, deltas);`,'component-specific responsibility');
  source=replace(source,'if (hasSuukantsu) result = lastKanPaoSeat(melds);',
    `if (hasSuukantsu) result = rules.competitiveProfile() == com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE
                ? melds.stream().filter(m -> m instanceof TheMahjongMeld.Daiminkan d && d.completesSuukantsu())
                    .mapToInt(m -> ((TheMahjongMeld.Daiminkan)m).sourceSeat()).findFirst()
                : lastKanPaoSeat(melds);`,'only fourth-open-kan responsibility');
  const component=`
    private static List<Integer> competitiveYakumanDeltas(HandShape shape,List<Yakuman> yakuman,int fu,boolean tsumo,
            int count,int winner,int loser,int dealer,int honba,int sticks,TheMahjongRuleSet rules,List<Integer> ordinary) {
        if (!(shape instanceof HandShape.Standard standard)) return ordinary;
        return competitiveYakumanDeltas(standard.melds(),yakuman,fu,tsumo,count,winner,loser,dealer,honba,sticks,rules,java.util.Set.of(),ordinary);
    }
    /** Recompute only responsibility components whose payer has exited a continuing blood battle. */
    public static WinResult withoutRetiredResponsibilities(WinResult result,boolean tsumo,int winner,
            com.themahjong.TheMahjongRound round,TheMahjongRuleSet rules,java.util.Set<Integer> retired) {
        var melds=round.players().get(winner).melds();
        int source=tsumo?winner:round.claimSourceSeat().orElseThrow();
        if(rules.competitiveProfile() == com.themahjong.competition.CompetitiveRules.Profile.M_LEAGUE) {
            boolean retiredPart=result.yakuman().stream().anyMatch(y -> {
                var liable=findPaoSeat(melds,List.of(y),rules);
                return liable.isPresent()&&retired.contains(liable.getAsInt());
            });
            if(!retiredPart)return result;
            var ordinary=withoutResponsibility(result,tsumo,round.players().size(),winner,source,round.dealerSeat(),round.honba(),round.riichiSticks(),rules);
            var deltas=competitiveYakumanDeltas(melds,result.yakuman(),result.fu(),tsumo,round.players().size(),winner,source,
                    round.dealerSeat(),round.honba(),round.riichiSticks(),rules,retired,ordinary.pointDeltas());
            return new WinResult(result.yaku(),result.yakuman(),result.han(),result.fu(),result.doraCount(),deltas);
        }
        var liable=findPaoSeat(melds,result.yakuman(),rules);
        return liable.isPresent()&&retired.contains(liable.getAsInt())
                ?withoutResponsibility(result,tsumo,round.players().size(),winner,source,round.dealerSeat(),round.honba(),round.riichiSticks(),rules):result;
    }
    private static OptionalInt activeResponsibility(List<TheMahjongMeld> melds,Yakuman y,TheMahjongRuleSet rules,java.util.Set<Integer> retired) {
        var liable=findPaoSeat(melds,List.of(y),rules);
        return liable.isPresent()&&retired.contains(liable.getAsInt())?OptionalInt.empty():liable;
    }
    private static List<Integer> competitiveYakumanDeltas(List<TheMahjongMeld> melds,List<Yakuman> yakuman,int fu,boolean tsumo,
            int count,int winner,int loser,int dealer,int honba,int sticks,TheMahjongRuleSet rules,java.util.Set<Integer> retired,List<Integer> ordinary) {
        int firstResponsible=-1,firstIndex=Integer.MAX_VALUE;
        for (var y:yakuman) {
            var liable=activeResponsibility(melds,y,rules,retired);
            if(liable.isEmpty())continue;
            int index=0;
            for(int i=0;i<melds.size();i++) {
                var m=melds.get(i);
                boolean matches=y==Yakuman.SUUKANTSU?m instanceof TheMahjongMeld.Daiminkan d&&d.completesSuukantsu()
                        : !m.tiles().isEmpty()&&m.tiles().getFirst().suit()==(y==Yakuman.DAISANGEN?TheMahjongTile.Suit.DRAGON:TheMahjongTile.Suit.WIND);
                if(matches)index=i;
            }
            if(index<firstIndex){firstIndex=index;firstResponsible=liable.getAsInt();}
        }
        if(firstResponsible<0)return ordinary;
        int[] total=new int[count];
        for(var y:yakuman) {
            var part=computeDeltas(13,fu,tsumo,winner==dealer,count,winner,loser,dealer,0,0,rules.kiriageMangan(),
                    activeResponsibility(melds,y,rules,retired),1,rules.tsumoLoss());
            for(int seat=0;seat<count;seat++)total[seat]+=part.get(seat);
        }
        int bonus=honba*(count-1)*100;total[firstResponsible]-=bonus;total[winner]+=bonus+sticks*1000;
        return java.util.Arrays.stream(total).boxed().toList();
    }
`;
  source=replace(source,'    private static List<Integer> computeDeltas(',component+'\n    private static List<Integer> computeDeltas(','competitive responsibility helper');
  return replace(source,'computeDeltas(result.han(), result.fu(), tsumo, winner == dealer,',
    'computeDeltas(result.yakuman().isEmpty() && !rules.countedYakumanAllowed() ? Math.min(12,result.han()) : result.han(), result.fu(), tsumo, winner == dealer,','blood counted cap');
};

exports.match=source=>replace(source,'    public TheMahjongMatch applyFinalDeposits() {',
  `    public TheMahjongMatch applyFinalDeposits() {
        if (ruleSet.competitiveProfile() != com.themahjong.competition.CompetitiveRules.Profile.STANDARD)
            return com.themahjong.competition.CompetitiveRules.applyFinalDeposits(this);`,'competitive final deposits');

exports.meld=source=>{
  source=replace(source,`    record Daiminkan(
            List<TheMahjongTile> tiles,
            int claimedTileIndex,
            int sourceSeat,
            int sourceDiscardIndex) implements TheMahjongMeld {`,
    `    record Daiminkan(
            List<TheMahjongTile> tiles,
            int claimedTileIndex,
            int sourceSeat,
            int sourceDiscardIndex,
            boolean completesSuukantsu) implements TheMahjongMeld {
        public Daiminkan(List<TheMahjongTile> tiles, int claimedTileIndex, int sourceSeat, int sourceDiscardIndex) {
            this(tiles, claimedTileIndex, sourceSeat, sourceDiscardIndex, false);
        }`,'fourth-open-kan responsibility marker');
  return source;
};
