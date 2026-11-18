// Checked compile-time hooks leave the pinned upstream sources unchanged.
const replace = (source, from, to, name) => {
  if (source.split(from).length !== 2) throw Error('Joker patch no longer matches: ' + name);
  return source.replace(from, to);
};
const joker = 'com.themahjong.variants.Jokers';

exports.tile = source => {
  source = replace(source,
    '        return this.suit == other.suit && this.rank == other.rank;',
    `        if (${joker}.isJoker(this) || ${joker}.isJoker(other))
            return ${joker}.isJoker(this) && ${joker}.isJoker(other);
        return this.suit == other.suit && this.rank == other.rank;`, 'physical joker identity');
  return replace(source, '        return suit == Suit.WIND || suit == Suit.DRAGON;',
    `        return !${joker}.isJoker(this) && (suit == Suit.WIND || suit == Suit.DRAGON);`,
    'wildcard is not a physical honor');
};

exports.handShape = source => replace(source,
  '    public static List<HandShape> decompose(List<TheMahjongTile> concealedTiles, List<TheMahjongMeld> melds) {',
  `    public static List<HandShape> decompose(List<TheMahjongTile> concealedTiles, List<TheMahjongMeld> melds) {
        if (concealedTiles.stream().anyMatch(${joker}::isJoker))
            return com.themahjong.variants.JokerShapes.decompose(concealedTiles, melds);`, 'wildcard decomposition');

exports.furiten = source => replace(source,
  '        if (isFuriten(player)) return false;',
  `        if (${joker}.isJoker(winningTile)) return false;
        if (isFuriten(player)) return false;`, 'cannot ron a discarded joker');

exports.winCalculator = source => replace(source,
  '        int count = 0;\n        for (TheMahjongTile indicator : doraIndicators) {',
  `        // Wildcards affect the winning shape, but never create physical red/indicator dora.
        tiles = tiles.stream().filter(tile -> !${joker}.isJoker(tile)).toList();
        int count = 0;
        for (TheMahjongTile indicator : doraIndicators) {`, 'actual-tile dora');

exports.winResultBuilder = source => {
  source = replace(source,
    '        List<TheMahjongTile> concealed = player.currentHand();',
    `        List<TheMahjongTile> concealed = player.currentHand();
        if (${joker}.isJoker(winTile)) return tryJokerTsumo(round, rules, seat, rinshanDraw);`, 'winning joker context');
  source = replace(source,
    '        TheMahjongPlayer player = round.players().get(seat);\n        List<TheMahjongTile> concealed = new ArrayList<>(player.currentHand());',
    `        if (${joker}.isJoker(heldTile)) return Optional.empty();
        TheMahjongPlayer player = round.players().get(seat);
        List<TheMahjongTile> concealed = new ArrayList<>(player.currentHand());`, 'joker ron rejection');
  const method = `
    /** A malformed/imported drawable joker still receives its substituted winning context. */
    private static Optional<WinResult> tryJokerTsumo(
            TheMahjongRound round, TheMahjongRuleSet rules, int seat, boolean rinshanDraw) {
        TheMahjongPlayer player = round.players().get(seat);
        List<TheMahjongTile> physical = player.currentHand();
        int index = physical.lastIndexOf(((TheMahjongRound.ActiveTile.Drawn)round.activeTile()).tile());
        if (index < 0) return Optional.empty();
        WinResult best = null;
        for (TheMahjongTile candidate : com.themahjong.variants.JokerShapes.ordinaryTiles()) {
            List<TheMahjongTile> substituted = new ArrayList<>(physical);
            substituted.set(index, candidate);
            List<HandShape> shapes = HandShape.decomposeForWin(substituted, player.melds(), candidate);
            if (shapes.isEmpty()) continue;
            boolean uninterrupted = round.players().stream().allMatch(p -> p.melds().isEmpty())
                    && player.discards().isEmpty();
            WinContext ctx = WinContext.tsumo(round.dealer(seat), uninterrupted,
                    player.riichiState(), player.ippatsuEligible(), candidate,
                    round.seatWind(seat), round.roundWind(), round.liveWall().isEmpty(), rinshanDraw);
            WinResult result = finalize(round, rules, seat, seat, physical, player, ctx, shapes);
            if (best == null || result.pointDeltas().get(seat) > best.pointDeltas().get(seat)) best = result;
        }
        return Optional.ofNullable(best);
    }
`;
  return replace(source, '    private static WinResult finalize(', method + '\n    private static WinResult finalize(', 'joker tsumo helper');
};

exports.driver = source => {
  source = replace(source,
    '        for (var e : byKind.entrySet()) {',
    `        for (var e : byKind.entrySet()) {
            if (e.getValue().stream().anyMatch(${joker}::isJoker)) continue;`, 'joker concealed-kan rejection');
  source = replace(source,
    '            if (t.matchesSuitRank(held)) matching.add(t);',
    `            if (!${joker}.isJoker(t) && t.matchesSuitRank(held)) matching.add(t);`, 'joker meld rejection');
  source = replace(source,
    '            TileKey k = new TileKey(t.suit(), t.rank(), false);',
    `            TileKey k = new TileKey(t.suit(), t.rank(), ${joker}.isJoker(t));`, 'separate joker kan groups');
  return source;
};
