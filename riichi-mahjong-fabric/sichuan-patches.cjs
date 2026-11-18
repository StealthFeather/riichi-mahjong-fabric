// Extend the compiled rule sources without changing the pinned upstream snapshot.
const fs = require('node:fs');
const p = require('node:path');
const replace = (source, from, to, name) => {
  if (source.split(from).length !== 2) throw Error('Sichuan patch no longer matches: ' + name);
  return source.replace(from, to);
};
exports.ruleSet = source => {
  const names = [...source.matchAll(/boolean (\w+)/g)].map(m => m[1]);
  if (names.length !== 20) throw Error('Unexpected upstream rule-set fields');
  source = replace(source, '        boolean sanchahouAbortive\n',
    '        boolean sanchahouAbortive,\n        boolean sichuan,\n        boolean exchangeThree,\n        boolean jokersEnabled,\n        boolean bloodBattle\n', 'rule fields');
  const constructor = `    public TheMahjongRuleSet(${names.map(n => 'boolean ' + n).join(', ')}) {\n        this(${names.join(', ')}, false, false, false, false);\n    }\n\n`;
  const variantConstructor = `    public TheMahjongRuleSet(${names.map(n => 'boolean ' + n).join(', ')}, boolean sichuan, boolean exchangeThree, boolean jokersEnabled) {\n        this(${names.join(', ')}, sichuan, exchangeThree, jokersEnabled, false);\n    }\n\n`;
  return replace(source, '    public static TheMahjongRuleSet wrc()', constructor + variantConstructor + '    public static TheMahjongRuleSet wrc()', 'legacy constructor');
};
exports.nonYakuman = source => replace(source, '    CHINITSU(6, 5);', `    CHINITSU(6, 5),
    SC_PINGHU(0, 0), SC_PENGPENGHU(1, 1), SC_QIDUI(2, 2), SC_LONGQIDUI(3, 3),
    SC_QINGYISE(2, 2), SC_JINGOUDIAO(1, 1), SC_JIANGDUI(3, 3), SC_ZIMO(1, 1),
    SC_GANGSHANGHUA(1, 1), SC_QIANGGANGHU(1, 1), SC_HAIDI(1, 1), SC_GEN(1, 1);`, 'scoring names');
exports.round = source => {
  source = replace(source, 'if (doraIndicators.size() != MAX_DORA_INDICATORS)',
    'if (!doraIndicators.isEmpty() && doraIndicators.size() != MAX_DORA_INDICATORS)', 'empty dora');
  source = replace(source, 'if (uraDoraIndicators.size() != MAX_DORA_INDICATORS)',
    'if (!uraDoraIndicators.isEmpty() && uraDoraIndicators.size() != MAX_DORA_INDICATORS)', 'empty ura');
  return replace(source, 'if (revealedDoraCount < 1 || revealedDoraCount > MAX_DORA_INDICATORS)',
    'if (revealedDoraCount < (doraIndicators.isEmpty() ? 0 : 1) || revealedDoraCount > doraIndicators.size())', 'dora count');
};
exports.driver = source => {
  source = replace(source, '    private boolean animationsEnabled;',
    '    private com.themahjong.sichuan.SichuanState sichuanState;\n    private boolean animationsEnabled;', 'driver state');
  source = replace(source, '    public List<PlayerAction> legalActions(int seat) {',
    `    public List<PlayerAction> legalActions(int seat) {
        if (rules().sichuan()) return sichuanState == null || phase instanceof MatchPhase.Dealing
                || phase instanceof MatchPhase.NotStarted || phase instanceof MatchPhase.MatchEnded
                || phase instanceof MatchPhase.AwaitingClaims && claimDecisions.containsKey(seat)
                ? List.of() : sichuanState.legalActions(currentRound(), phase, rules(), seat);`, 'legal actions');
  source = replace(source, '        this.phase = Objects.requireNonNull(snapshot.phase(), \"snapshot.phase\");',
    '        this.phase = Objects.requireNonNull(snapshot.phase(), \"snapshot.phase\");\n        if (rules().sichuan()) sichuanState = new com.themahjong.sichuan.SichuanState(rules().exchangeThree(), 1 + random.nextInt(3));', 'restored state default');
  source = replace(source, '        match = match.validate().startRound(random);',
    '        match = match.validate().startRound(random);\n        initSichuanRound();', 'first round');
  source = replace(source, '        abortiveDraw = false;',
    '        abortiveDraw = false;\n        initSichuanRound();', 'next round');
  source = replace(source, '        if (phase instanceof MatchPhase.AwaitingDraw ad) {',
    `        if (rules().sichuan()) return advanceSichuan(deltaSeconds, polledThisFrame);
        if (phase instanceof MatchPhase.AwaitingDraw ad) {`, 'state machine');
  const methods = fs.readFileSync(p.join(__dirname, 'src/rules/sichuan-driver.inc'), 'utf8');
  return replace(source, '    private TheMahjongMatch matchWithRound(TheMahjongRound updated) {',
    methods + '\n    private TheMahjongMatch matchWithRound(TheMahjongRound updated) {', 'driver methods');
};
exports.match = source => {
  source = replace(source, '        TheMahjongRound initialRound = TheMahjongRound.start(playerCount, startingPoints, wall);',
    '        TheMahjongRound initialRound = TheMahjongRound.start(playerCount, startingPoints, wall);\n        if (ruleSet.sichuan()) initialRound = com.themahjong.sichuan.SichuanRules.prepareRound(initialRound);\n        else if (ruleSet.jokersEnabled()) initialRound = com.themahjong.variants.VariantRules.prepareRound(initialRound);', 'initial deal');
  return replace(source, '                currentRound.riichiSticks(), nextDealerSeat);',
    '                currentRound.riichiSticks(), nextDealerSeat);\n        if (ruleSet.sichuan()) nextRound = com.themahjong.sichuan.SichuanRules.prepareRound(nextRound);\n        else if (ruleSet.jokersEnabled()) nextRound = com.themahjong.variants.VariantRules.prepareRound(nextRound);', 'next deal');
};
