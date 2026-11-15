'use strict';

// The Java adapter supplies one concealed hand, public information and legal actions.
// Rebuild a player for each request so restart, chunk reload and seat handover need no replay.
const Majiang = require('@kobalab/majiang-core');
const Strategies = {
  EASY: require('@kobalab/majiang-ai/legacy')('0002'),
  NORMAL: require('@kobalab/majiang-ai/legacy')('0202'),
  HARD: require('@kobalab/majiang-ai'),
};
const Names = {
  EASY: 'majiang-ai@1.3.0/legacy-0002',
  NORMAL: 'majiang-ai@1.3.0/legacy-0202',
  HARD: 'majiang-ai@1.3.0/current',
};

function tile(value) {
  if (typeof value !== 'string' || !/^(?:[mps][0-9]|z[1-7])$/.test(value)) {
    throw new Error('Invalid tile');
  }
  return value;
}
const normal = p => p[0] + (Number(p[1]) || 5);
const tileKind = value => normal(tile(value));
const relative = (seat, dealer) => (seat + 4 - dealer) % 4;
const direction = (source, owner) => ['', '+', '=', '-'][(source + 4 - owner) % 4];
const tileOrder = (a, b) => (Number(a[1]) || 5) - (Number(b[1]) || 5) || b.localeCompare(a);

function canonicalMeld(value) {
  const result = Majiang.Shoupai.valid_mianzi(value);
  if (!result) throw new Error('Invalid meld');
  return result;
}

function meldNotation(meld, owner) {
  const tiles = meld.tiles.map(tile);
  if (!tiles.length) throw new Error('Empty meld');
  if (meld.type === 'Ankan') return canonicalMeld(tiles[0][0] + tiles.map(p => p[1]).join(''));
  const calledIndex = meld.claimedTileIndex ?? tiles.indexOf(meld.calledTile);
  if (calledIndex < 0 || calledIndex >= tiles.length) throw new Error('Invalid claimed tile');
  const called = tiles[calledIndex];
  const d = direction(meld.sourceSeat, owner);
  if (!d) throw new Error('Meld source is its owner');
  if (meld.type === 'Chi') {
    const parts = tiles.map((p, i) => ({ p, called: i === calledIndex }));
    parts.sort((a, b) => tileOrder(a.p, b.p));
    return canonicalMeld(called[0] + parts.map(x => x.p[1] + (x.called ? d : '')).join(''));
  }
  const remaining = tiles.filter((_, i) => i !== calledIndex);
  if (meld.type === 'Kakan') {
    // Snapshot Kakan.tiles is the original pon followed by the added tile.
    const added = tile(meld.addedTile ?? tiles.at(-1));
    const baseTiles = tiles.slice(0, 3);
    const base = meldNotation({ ...meld, type: 'Pon', tiles: baseTiles }, owner);
    return canonicalMeld(base + added[1]);
  }
  remaining.sort((a, b) => b[1].localeCompare(a[1]));
  return canonicalMeld(called[0] + remaining.map(p => p[1]).join('') + called[1] + d);
}

function legalMeld(action, snapshot) {
  if (action.type === 'Kakan') {
    const base = meldNotation(action.upgradedFrom, snapshot.seat);
    return canonicalMeld(base + tile(action.tile)[1]);
  }
  if (action.type === 'Ankan') {
    const hand = action.handTiles.map(tile);
    return canonicalMeld(hand[0][0] + hand.map(p => p[1]).join(''));
  }
  const last = snapshot.lastDiscard;
  if (!last) throw new Error('Call without public discard');
  const hand = action.handTiles.map(tile);
  return meldNotation({
    type: action.type,
    tiles: [...hand, tile(last.tile)],
    claimedTileIndex: hand.length,
    sourceSeat: last.seat,
  }, snapshot.seat);
}

function validate(snapshot) {
  if (!snapshot || !Strategies[snapshot.difficulty]) throw new Error('Unknown difficulty');
  if (![snapshot.seat, snapshot.dealer].every(n => Number.isInteger(n) && n >= 0 && n < 4)) {
    throw new Error('Four-player seats required');
  }
  for (const name of ['melds', 'rivers', 'riichi', 'points']) {
    if (!Array.isArray(snapshot[name]) || snapshot[name].length !== 4) throw new Error('Four-player snapshot required');
  }
  if (!Array.isArray(snapshot.hand) || snapshot.hand.length > 14) throw new Error('Invalid hand');
  snapshot.hand.forEach(tile);
  if (snapshot.drawn != null) tile(snapshot.drawn);
  if (!Array.isArray(snapshot.doraIndicators) || !snapshot.doraIndicators.length) throw new Error('Missing dora indicator');
  snapshot.doraIndicators.forEach(tile);
  if (!Array.isArray(snapshot.legal) || !snapshot.legal.length || snapshot.legal.length > 100) throw new Error('Invalid legal actions');
  if (!['discard', 'claim', 'draw'].includes(snapshot.phase)) throw new Error('Invalid phase');
  if (!Number.isInteger(snapshot.remaining) || snapshot.remaining < 0 || snapshot.remaining > 70) throw new Error('Invalid wall count');
}

function makePlayer(snapshot) {
  validate(snapshot);
  const Player = Strategies[snapshot.difficulty];
  const player = new Player();
  const r = snapshot.rules || {};
  const red = r.redFives !== false ? 1 : 0;
  const rule = Majiang.rule({
    '赤牌': { m: red, p: red, s: red },
    'クイタンあり': r.openTanyao !== false,
    '切り上げ満貫あり': !!r.kiriageMangan,
    '連風牌は2符': !r.doubleWindPairFu4,
    '喰い替え許可レベル': r.kuikaeForbidden ? 0 : 2,
    '途中流局あり': r.abortiveDrawsAllowed !== false,
    '流し満貫あり': r.nagashiManganAllowed !== false,
    '最大同時和了数': r.doubleRonAllowed === false ? 1 : 2,
    'リーチ後暗槓許可レベル': r.strictRiichiAnkan ? 1 : 2,
    'カンドラ後乗せ': r.openKanDoraDelayedReveal !== false,
    'トビ終了あり': r.bustingEndsGame !== false,
    'オーラス止めあり': r.agariYameAllowed !== false,
    '延長戦方式': r.suddenDeathRound ? 1 : 0,
  });
  const handNumber = snapshot.handNumber || 1;
  player.kaiju({
    id: snapshot.seat,
    title: 'Riichi Mahjong Fabric AI',
    player: ['East', 'South', 'West', 'North'],
    qijia: (snapshot.dealer - handNumber + 5) % 4,
    rule,
  });
  const ownRelative = relative(snapshot.seat, snapshot.dealer);
  const startingHands = ['', '', '', ''];
  // qipai initializes each strategy's caches and counting implementation.
  startingHands[ownRelative] = new Majiang.Shoupai(snapshot.hand).toString();
  player.qipai({
    zhuangfeng: snapshot.roundWind,
    jushu: handNumber - 1,
    changbang: snapshot.honba || 0,
    lizhibang: snapshot.riichiSticks || 0,
    defen: Array.from({ length: 4 }, (_, l) => snapshot.points[(snapshot.dealer + l) % 4]),
    baopai: snapshot.doraIndicators[0],
    shoupai: startingHands,
  });

  let gangs = 0;
  for (let absolute = 0; absolute < 4; absolute++) {
    const l = relative(absolute, snapshot.dealer);
    const melds = snapshot.melds[absolute].map(m => meldNotation(m, absolute));
    const shoupai = absolute === snapshot.seat
      ? new Majiang.Shoupai(snapshot.hand)
      : new Majiang.Shoupai(Array(Math.max(0, 13 - melds.length * 3)).fill('_'));
    shoupai._fulou = melds;
    shoupai._lizhi = !!snapshot.riichi[absolute];
    if (absolute === snapshot.seat && snapshot.phase === 'discard') {
      if (snapshot.drawn) {
        if (!snapshot.hand.includes(snapshot.drawn)) throw new Error('Drawn tile absent from hand');
        shoupai._zimo = snapshot.drawn;
      } else {
        // A pon/chi creates a discard turn without drawing a tile.
        shoupai._zimo = melds.at(-1) || null;
      }
    }
    player._model.shoupai[l] = shoupai;
    player._model.he[l] = new Majiang.He();
    for (const discard of snapshot.rivers[absolute]) {
      player._model.he[l].dapai(tile(discard.tile) + (discard.tsumogiri ? '_' : '') + (discard.riichi ? '*' : ''));
    }
    gangs += snapshot.melds[absolute].filter(m => ['Ankan', 'Daiminkan', 'Kakan'].includes(m.type)).length;
  }
  player._model.shan.paishu = snapshot.remaining;
  player._model.shan.baopai = snapshot.doraIndicators.slice();
  player._model.lunban = snapshot.phase === 'claim'
    ? relative(snapshot.lastDiscard.seat, snapshot.dealer) : ownRelative;
  player._diyizimo = snapshot.rivers.every(river => !river.length) && snapshot.melds.every(m => !m.length);
  player._n_gang = gangs;
  player._neng_rong = true;

  if (player._suanpai) {
    const counter = player._suanpai;
    counter._paishu = {
      m: [red, 4, 4, 4, 4, 4, 4, 4, 4, 4],
      p: [red, 4, 4, 4, 4, 4, 4, 4, 4, 4],
      s: [red, 4, 4, 4, 4, 4, 4, 4, 4, 4],
      z: [0, 4, 4, 4, 4, 4, 4, 4],
    };
    counter._menfeng = ownRelative;
    counter._zhuangfeng = snapshot.roundWind;
    counter._baopai = snapshot.doraIndicators.slice();
    counter._n_zimo = snapshot.remaining;
    counter._dapai = [{}, {}, {}, {}];
    counter._lizhi = [];
    const seen = [...snapshot.hand, ...snapshot.doraIndicators];
    for (let absolute = 0; absolute < 4; absolute++) {
      const l = relative(absolute, snapshot.dealer);
      counter._lizhi[l] = absolute !== snapshot.seat && !!snapshot.riichi[absolute];
      for (const discard of snapshot.rivers[absolute]) {
        counter._dapai[l][normal(tile(discard.tile))] = true;
        // Claimed discards appear in their meld. Count the physical tile once.
        if (!discard.called) seen.push(discard.tile);
      }
      for (const meld of snapshot.melds[absolute]) seen.push(...meld.tiles.map(tile));
    }
    for (const p of seen) counter.decrease(p);
    // Do not infer extra safe tiles after riichi: per-seat saved rivers have no
    // global turn ordering. Opponents' own discards are certainly safe.
    for (const suit of ['m', 'p', 's', 'z']) {
      if (counter._paishu[suit].some(n => n < 0)) throw new Error('Inconsistent public tile counts');
    }
  }

  const own = player.shoupai;
  const getDiscard = player.get_dapai.bind(player);
  const allowedDiscards = snapshot.legal.filter(a => ['Discard', 'DiscardWithRiichi'].includes(a.type));
  player.get_dapai = hand => hand === own
    ? [...new Set(allowedDiscards.map(a => tile(a.tile) + (a.tile === snapshot.drawn ? '_' : '')))]
    : getDiscard(hand);
  player.allow_lizhi = (hand, p) => hand === own && snapshot.legal.some(a => a.type === 'DiscardWithRiichi' && a.tile === p?.slice(0, 2));
  const getterTypes = { get_chi_mianzi: ['Chi'], get_peng_mianzi: ['Pon'], get_gang_mianzi: ['Ankan', 'Kakan', 'Daiminkan'] };
  for (const [name, types] of Object.entries(getterTypes)) {
    const original = player[name].bind(player);
    player[name] = (hand, p) => hand === own
      ? snapshot.legal.filter(a => types.includes(a.type) && (p ? a.type === 'Daiminkan' || name !== 'get_gang_mianzi' : a.type !== 'Daiminkan')).map(a => legalMeld(a, snapshot))
      : original(hand, p);
  }
  return player;
}

function decide(snapshot, suppliedPlayer) {
  validate(snapshot);
  const engine = Names[snapshot.difficulty];
  const result = action => ({ id: snapshot.id, index: action.index, engine });
  const win = snapshot.legal.find(a => ['DeclareTsumo', 'DeclareRon', 'DeclareChankan'].includes(a.type));
  if (win) return result(win);
  const draw = snapshot.legal.find(a => a.type === 'Draw');
  if (draw) return result(draw);
  if (snapshot.legal.length === 1 && snapshot.legal[0].type === 'Pass') return result(snapshot.legal[0]);
  const player = suppliedPlayer || makePlayer(snapshot);
  let selected;
  if (snapshot.phase === 'claim') {
    // Robbing a kan has only win/pass actions, handled by the authoritative list.
    if (!snapshot.claimSource || snapshot.claimSource === 'DISCARD') {
      selected = player.select_fulou({
        l: relative(snapshot.lastDiscard.seat, snapshot.dealer),
        p: snapshot.lastDiscard.tile,
      });
    }
    if (selected) {
      const match = snapshot.legal.find(a => ['Chi', 'Pon', 'Daiminkan'].includes(a.type) && legalMeld(a, snapshot) === canonicalMeld(selected));
      if (match) return result(match);
      throw new Error('Strategy returned a call outside legal actions');
    }
    const pass = snapshot.legal.find(a => a.type === 'Pass');
    if (pass) return result(pass);
  } else if (snapshot.phase === 'discard') {
    const abort = snapshot.legal.find(a => a.type === 'KyuushuAbort');
    if (abort && player.select_pingju()) return result(abort);
    if (snapshot.legal.some(a => ['Ankan', 'Kakan'].includes(a.type))) {
      selected = player.select_gang();
      if (selected) {
        const match = snapshot.legal.find(a => ['Ankan', 'Kakan'].includes(a.type) && legalMeld(a, snapshot) === canonicalMeld(selected));
        if (match) return result(match);
        throw new Error('Strategy returned a kan outside legal actions');
      }
    }
    selected = player.select_dapai();
    const kind = selected?.endsWith('*') ? 'DiscardWithRiichi' : 'Discard';
    const match = snapshot.legal.find(a => a.type === kind && a.tile === selected?.slice(0, 2));
    if (match) return result(match);
    throw new Error('Strategy returned a discard outside legal actions');
  }
  throw new Error('No strategy action');
}


/** Deterministic strategy -> explicit modeled policy, not native AI probabilities or win chances.
 * utility = EV * exp(-3 * extra shanten) / (1 + danger / 3.2).
 * logits = log1p(utility), temperature .75. The deterministic recommendation receives
 * a .5 logit margin above every alternative so defense remains the most probable policy.
 * Discard tile probabilities are conditional on discarding and normalized separately
 * from the full legal-action distribution. Integer percentages use largest remainders.
 */
const Policy = { version: 3, name: 'modeled-kind-softmax-v3', temperature: .75, recommendationMargin: .5 };

function distribution(items, selected, identity) {
  if (!items.length) return [];
  const values = items.map(item => ({ ...item }));
  if (!values.every(item => Number.isFinite(item.logit))) throw new Error('Invalid policy utility');
  const maximum = Math.max(...values.map(item => item.logit));
  const recommendation = values.find(item => identity(item) === selected);
  if (recommendation) recommendation.logit = maximum + Policy.recommendationMargin;
  const peak = Math.max(...values.map(item => item.logit));
  const weights = values.map(item => Math.exp((item.logit - peak) / Policy.temperature));
  const total = weights.reduce((sum, value) => sum + value, 0);
  values.forEach((item, index) => {
    item.policyProbability = weights[index] / total;
    item.probability = Math.floor(item.policyProbability * 100);
  });
  const remainderOrder = values.slice().sort((a, b) =>
    (b.policyProbability * 100 - b.probability) - (a.policyProbability * 100 - a.probability)
    || b.policyProbability - a.policyProbability || String(identity(a)).localeCompare(String(identity(b))));
  const remaining = 100 - values.reduce((sum, item) => sum + item.probability, 0);
  for (let index = 0; index < remaining; index++) remainderOrder[index].probability++;
  values.sort((a, b) => b.policyProbability - a.policyProbability || String(identity(a)).localeCompare(String(identity(b))));
  return values.map(({ logit, utility, ...item }, index) => ({ ...item, rank: index + 1 }));
}

/** One entry per 34-way tile kind, independent of physical copies and discard/riichi aliases.
 * Red and ordinary five variants retain their own EV during evaluation; use the best legal
 * variant's utility once for the kind rather than adding mass for each way to submit it.
 */
function discardKindDistribution(discards, recommended) {
  const kinds = new Map();
  for (const discard of discards) {
    const kind = tileKind(discard.tile), previous = kinds.get(kind);
    if (!previous || discard.logit > previous.logit) kinds.set(kind, { ...discard, tile: kind });
  }
  return distribution([...kinds.values()], recommended == null ? null : tileKind(recommended), item => item.tile);
}

function analyze(snapshot) {
  const input = { ...snapshot, difficulty: 'HARD' };
  validate(input);
  const player = makePlayer(input);
  const selected = decide(input, player).index;
  const paishu = player._suanpai.get_paishu();
  const danger = player._suanpai.suan_weixian_all(player.shoupai._bingpai);
  const baseline = Majiang.Util.xiangting(player.shoupai);
  const info = [];
  const allowed = [...new Set(input.legal.filter(a => ['Discard', 'DiscardWithRiichi'].includes(a.type)).map(a => tile(a.tile)))];
  const recommendedDiscard = allowed.length ? player.select_dapai(info)?.slice(0, 2) : null;
  const discards = allowed.map(p => {
    const hand = player.shoupai.clone().dapai(p + (p === input.drawn ? '_' : ''));
    const shanten = Majiang.Util.xiangting(hand);
    const entry = info.find(i => i.p === p && !i.m);
    const value = Math.max(0, Number(entry?.ev ?? player.eval_shoupai(hand, paishu)) || 0);
    const risk = Math.max(0, Number(danger && danger(p)) || 0);
    const utility = value * Math.exp(-3 * Math.max(0, shanten - baseline)) / (1 + risk / 3.2);
    return { tile: p, value, risk, shanten, logit: Math.log1p(utility) };
  });
  const discardDistribution = discardKindDistribution(discards, recommendedDiscard);
  const defaultValue = Math.max(0, Number(player.eval_shoupai(player.shoupai, paishu)) || 0);
  const maxDiscardLogit = discards.length ? Math.max(...discards.map(c => c.logit)) : Math.log1p(defaultValue);
  const actions = input.legal.map(action => {
    let value = defaultValue, risk = 0, shanten = baseline, logit = Math.log1p(defaultValue);
    const discard = discards.find(c => c.tile === action.tile);
    if (['Discard', 'DiscardWithRiichi'].includes(action.type)) {
      if (!discard) throw new Error('Missing legal discard evaluation');
      ({ value, risk, shanten, logit } = discard);
      if (action.type === 'DiscardWithRiichi') logit += .15;
    } else if (['Chi', 'Pon', 'Daiminkan', 'Ankan', 'Kakan'].includes(action.type)) {
      const hand = player.shoupai.clone();
      const notation = legalMeld(action, input);
      if (['Ankan', 'Kakan'].includes(action.type)) hand.gang(notation);
      else hand.fulou(notation);
      shanten = Majiang.Util.xiangting(hand);
      value = Math.max(0, Number(player.eval_shoupai(hand, paishu)) || 0);
      const utility = value * Math.exp(-3 * Math.max(0, shanten - baseline));
      logit = Math.log1p(utility);
    } else if (['DeclareTsumo', 'DeclareRon', 'DeclareChankan'].includes(action.type)) {
      // The authoritative Java engine supplies legality; the deterministic BOT always takes a legal win.
      logit = maxDiscardLogit + 2;
    } else if (action.type === 'KyuushuAbort') logit -= 1;
    return { index: action.index, type: action.type, tile: action.tile ?? null,
      value, risk, shanten, logit };
  });
  return { id: snapshot.id, engine: Names.HARD, policy: Policy,
    selected, selectedDiscard: recommendedDiscard == null ? null : tileKind(recommendedDiscard),
    candidates: discardDistribution,
    actions: distribution(actions, selected, item => item.index),
  };
}

if (require.main === module) {
  const input = require('node:readline').createInterface({ input: process.stdin, crlfDelay: Infinity });
  input.on('line', line => {
    let request;
    try {
      if (Buffer.byteLength(line, 'utf8') > 256 * 1024) throw new Error('Request too large');
      request = JSON.parse(line);
      process.stdout.write(JSON.stringify(request.mode === 'analysis' ? analyze(request) : decide(request)) + '\n');
    } catch (error) {
      process.stdout.write(JSON.stringify({ id: request?.id ?? null, index: -1, error: error.message }) + '\n');
    }
  });
}

module.exports = { decide, analyze, distribution, discardKindDistribution, tileKind, Policy, makePlayer, meldNotation, legalMeld, Names };
