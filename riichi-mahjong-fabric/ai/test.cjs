'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { spawnSync } = require('node:child_process');
const { decide, makePlayer, meldNotation, legalMeld, Names } = require('./worker.cjs');

function fixture(changes = {}) {
  const hand = ['m1', 'm2', 'm3', 'm4', 'm5', 'm6', 'p1', 'p2', 'p3', 's4', 's5', 'z5', 'z5', 'z4'];
  return {
    id: 1, difficulty: 'HARD', seat: 0, dealer: 0, roundWind: 0, handNumber: 1,
    honba: 0, riichiSticks: 0, points: [25000, 25000, 25000, 25000],
    hand, drawn: 'z4', melds: [[], [], [], []], rivers: [[], [], [], []],
    riichi: [false, false, false, false], doraIndicators: ['z7'], remaining: 52,
    phase: 'discard', lastDiscard: null, rules: { redFives: true, kuikaeForbidden: true },
    legal: [...new Set(hand)].map((tile, index) => ({ index, type: 'Discard', tile })),
    ...changes,
  };
}

test('all three pinned strategies discard the isolated wind and can declare legal riichi', () => {
  for (const difficulty of Object.keys(Names)) {
    const s = fixture({ difficulty });
    s.legal.push({ index: 100, type: 'DiscardWithRiichi', tile: 'z4' });
    const decision = decide(s);
    assert.equal(decision.index, 100, difficulty);
    assert.equal(decision.engine, Names[difficulty]);
    assert.deepEqual(decide(JSON.parse(JSON.stringify(s))), decision, 'restoring a snapshot changes no decision');
  }
});

test('normal and hard use a public safe tile to defend a weak hand against riichi', () => {
  const hand = ['m2', 'm4', 'm6', 'p1', 'p4', 'p8', 's1', 's5', 's9', 'z1', 'z2', 'z3', 'z4', 'z7'];
  for (const difficulty of ['NORMAL', 'HARD']) {
    const s = fixture({ difficulty, hand, drawn: 'z7', doraIndicators: ['p9'],
      riichi: [false, true, false, false],
      rivers: [[], [{ tile: 'z4', riichi: true, called: false }], [], []],
      legal: hand.map((tile, index) => ({ index, type: 'Discard', tile })) });
    const decision = decide(s);
    assert.equal(s.legal.find(a => a.index === decision.index).tile, 'z4', difficulty);
  }
});

test('easy passes a call while normal and hard improve the hand with yakuhai pon', () => {
  const hand = ['z5', 'z5', 'm1', 'm2', 'm3', 'p1', 'p2', 'p3', 's4', 's5', 's6', 'z1', 'z2'];
  for (const difficulty of Object.keys(Names)) {
    const s = fixture({ difficulty, hand, drawn: null, phase: 'claim', claimSource: 'DISCARD',
      lastDiscard: { seat: 3, tile: 'z5' },
      rivers: [[], [], [], [{ tile: 'z5', riichi: false, called: false }]],
      legal: [{ index: 4, type: 'Pon', handTiles: ['z5', 'z5'] }, { index: 7, type: 'Pass' }] });
    assert.equal(decide(s).index, difficulty === 'EASY' ? 7 : 4, difficulty);
  }
});

test('red calls, all source directions, concealed kan and added kan preserve exact tile identity', () => {
  const s = fixture({ phase: 'claim', lastDiscard: { seat: 3, tile: 'm6' } });
  assert.equal(legalMeld({ type: 'Chi', handTiles: ['m4', 'm0'] }, s), 'm406-');
  for (const sourceSeat of [1, 2, 3]) {
    const expectedDirection = ['', '+', '=', '-'][sourceSeat];
    assert.equal(meldNotation({ type: 'Pon', tiles: ['p5', 'p5', 'p0'], claimedTileIndex: 2, sourceSeat }, 0), 'p550' + expectedDirection);
  }
  assert.equal(legalMeld({ type: 'Ankan', handTiles: ['s5', 's5', 's5', 's0'] }, s), 's5550');
  const pon = { type: 'Pon', tiles: ['p5', 'p5', 'p0'], claimedTileIndex: 2, sourceSeat: 2 };
  assert.equal(legalMeld({ type: 'Kakan', upgradedFrom: pon, tile: 'p5' }, s), 'p550=5');
  assert.equal(meldNotation({ ...pon, type: 'Kakan', tiles: [...pon.tiles, 'p5'], addedTile: 'p5' }, 0), 'p550=5');
});

test('public counts count called rivers once, include red tiles and rotate player seats', () => {
  const s = fixture({ seat: 2, dealer: 1, handNumber: 3,
    hand: ['m1', 'm2', 'm3', 'p2', 'p3', 'p4', 's2', 's3', 's4', 'z1', 'z2', 'z3', 'z4'],
    drawn: null, phase: 'claim', lastDiscard: { seat: 1, tile: 'z4' },
    rivers: [[{ tile: 'p0', called: true }], [], [], []],
    melds: [[], [{ type: 'Pon', tiles: ['p5', 'p5', 'p0'], claimedTileIndex: 2, sourceSeat: 0 }], [], []],
    legal: [{ index: 0, type: 'Pass' }] });
  const p = makePlayer(s);
  assert.equal(p._menfeng, 1);
  assert.equal(p._model.menfeng(2), 1);
  assert.equal(p._suanpai._paishu.p[0], 0);
  assert.equal(p._suanpai._paishu.p[5], 1);
  assert.equal(p._suanpai._paishu.p[2], 3);
  assert.equal(p._model.shoupai[0]._fulou[0], 'p550-');
});

test('kan choices survive sanitizing and legal-action filtering', () => {
  const hand = ['m1', 'm1', 'm1', 'm1', 'p1', 'p2', 'p3', 's1', 's2', 's3', 'z5', 'z5', 'z4', 'z4'];
  const s = fixture({ difficulty: 'EASY', hand, drawn: 'm1',
    legal: [{ index: 12, type: 'Ankan', handTiles: ['m1', 'm1', 'm1', 'm1'] },
      ...[...new Set(hand)].map((tile, index) => ({ index, type: 'Discard', tile }))] });
  assert.equal(decide(s).index, 12);
});

test('all levels can discard after pon without a drawn tile, including rotated seats', () => {
  const hand = ['m1', 'm2', 'm3', 'p1', 'p2', 'p3', 's4', 's5', 's6', 'z1', 'z2'];
  for (const difficulty of Object.keys(Names)) {
    for (const seat of [0, 1, 2, 3]) {
      const source = (seat + 3) % 4;
      const melds = [[], [], [], []];
      melds[seat].push({ type: 'Pon', tiles: ['z5', 'z5', 'z5'], claimedTileIndex: 2, sourceSeat: source });
      const rivers = [[], [], [], []];
      rivers[source].push({ tile: 'z5', called: true });
      const s = fixture({ difficulty, seat, dealer: 2, handNumber: 2, hand, drawn: null, melds, rivers,
        legal: hand.map((tile, index) => ({ index, type: 'Discard', tile })) });
      const decision = decide(s);
      assert.ok(s.legal.some(a => a.index === decision.index), difficulty + '/' + seat);
      assert.equal(decision.engine, Names[difficulty]);
    }
  }
});

test('legal wins and robbing-kan passes use Mod decisions, not a second rules engine', () => {
  const s = fixture({ phase: 'claim', claimSource: 'KAKAN', drawn: null,
    lastDiscard: { seat: 1, tile: 'z4' }, legal: [{ index: 3, type: 'DeclareChankan' }, { index: 8, type: 'Pass' }] });
  assert.equal(decide(s).index, 3);
  s.legal.shift();
  assert.equal(decide(s).index, 8);
});

test('worker protocol stays alive after malformed input and serves independent requests', () => {
  const a = fixture({ difficulty: 'EASY', id: 41 });
  const b = fixture({ difficulty: 'NORMAL', id: 42 });
  const run = spawnSync(process.execPath, [require.resolve('./worker.cjs')], {
    input: 'invalid JSON\n' + JSON.stringify(a) + '\n' + JSON.stringify(b) + '\n', encoding: 'utf8', timeout: 20000,
  });
  assert.equal(run.status, 0, run.stderr);
  const replies = run.stdout.trim().split('\n').map(line => JSON.parse(line));
  assert.equal(replies.length, 3);
  assert.equal(replies[0].index, -1);
  assert.equal(replies[1].id, 41);
  assert.equal(replies[2].id, 42);
  assert.equal(replies[1].engine, Names.EASY);
  assert.equal(replies[2].engine, Names.NORMAL);
});

test('a three-player or inconsistent snapshot is rejected explicitly', () => {
  assert.throws(() => decide(fixture({ points: [35000, 35000, 35000] })), /Four-player/);
  const bad = fixture({ rivers: [[{ tile: 'z5' }, { tile: 'z5' }, { tile: 'z5' }], [], [], []] });
  assert.throws(() => makePlayer(bad), /Inconsistent public tile counts/);
});

test('analysis models complete legal discard and action probabilities separately, each totaling 100', () => {
  const { analyze, Policy } = require('./worker.cjs');
  const s = fixture();
  s.legal.push({ index: 100, type: 'DiscardWithRiichi', tile: 'z4' });
  const result = analyze(s);
  assert.equal(result.id, s.id);
  assert.equal(result.engine, Names.HARD);
  assert.equal(result.policy.name, Policy.name);
  assert.equal(result.policy.version, 3);
  assert.equal(result.candidates[0].tile, 'z4');
  assert.equal(result.candidates.length, new Set(s.legal.map(a => a.tile)).size);
  assert.equal(result.actions.length, s.legal.length);
  for (const choices of [result.candidates, result.actions]) {
    assert.equal(choices.reduce((sum, c) => sum + c.probability, 0), 100);
    assert.ok(Math.abs(choices.reduce((sum, c) => sum + c.policyProbability, 0) - 1) < 1e-12);
    assert.deepEqual(choices.map(c => c.rank), choices.map((_, i) => i + 1));
    assert.ok(choices.every(c => Number.isInteger(c.probability) && c.probability >= 0 && c.probability <= 100));
  }
  assert.equal(result.actions[0].index, decide(s).index);
  assert.ok(result.actions.some(c => c.type === 'DiscardWithRiichi'));
  assert.deepEqual(analyze(structuredClone(s)), result);
});

test('modeled policy preserves the strategy defensive choice as the strict largest probability', () => {
  const { analyze } = require('./worker.cjs');
  const hand = ['m2', 'm4', 'm6', 'p1', 'p4', 'p8', 's1', 's5', 's9', 'z1', 'z2', 'z3', 'z4', 'z7'];
  const s = fixture({ hand, drawn: 'z7', doraIndicators: ['p9'], riichi: [false, true, false, false],
    rivers: [[], [{ tile: 'z4', riichi: true, called: false }], [], []],
    legal: hand.map((tile, index) => ({ index, type: 'Discard', tile })) });
  const result = analyze(s);
  assert.equal(result.candidates[0].tile, s.legal.find(a => a.index === decide(s).index).tile);
  assert.equal(result.candidates[0].tile, 'z4');
  assert.ok(result.candidates.slice(1).every(c => c.policyProbability < result.candidates[0].policyProbability));
  assert.equal(result.candidates.reduce((sum, c) => sum + c.probability, 0), 100);
});

test('largest remainders conserve 100 percent, including ties and a single legal choice', () => {
  const { distribution } = require('./worker.cjs');
  const three = distribution([{ key: 'a', logit: 0 }, { key: 'b', logit: 0 }, { key: 'c', logit: 0 }], null, c => c.key);
  assert.deepEqual(three.map(c => c.probability), [34, 33, 33]);
  const single = distribution([{ key: 'only', logit: -1000 }], 'only', c => c.key);
  assert.equal(single[0].policyProbability, 1);
  assert.equal(single[0].probability, 100);
  assert.equal(single[0].rank, 1);
});

test('claim policy exposes pon and pass without inventing discards, and supports forced wins', () => {
  const { analyze } = require('./worker.cjs');
  const hand = ['z5', 'z5', 'm1', 'm2', 'm3', 'p1', 'p2', 'p3', 's4', 's5', 's6', 'z1', 'z2'];
  const s = fixture({ hand, drawn: null, phase: 'claim', claimSource: 'DISCARD',
    lastDiscard: { seat: 3, tile: 'z5' }, rivers: [[], [], [], [{ tile: 'z5', riichi: false, called: false }]],
    legal: [{ index: 4, type: 'Pon', handTiles: ['z5', 'z5'] }, { index: 7, type: 'Pass' }] });
  const result = analyze(s);
  assert.deepEqual(result.candidates, []);
  assert.equal(result.actions.length, 2);
  assert.equal(result.actions[0].type, 'Pon');
  assert.equal(result.actions.reduce((sum, a) => sum + a.probability, 0), 100);
  const forced = analyze({ ...s, legal: [{ index: 9, type: 'DeclareRon' }] });
  assert.equal(forced.actions[0].type, 'DeclareRon');
  assert.equal(forced.actions[0].probability, 100);
});

test('discard policy remains normalized with a simultaneous legal win or kan', () => {
  const { analyze } = require('./worker.cjs');
  const s = fixture();
  s.legal.push({ index: 200, type: 'DeclareTsumo' });
  const result = analyze(s);
  assert.equal(result.actions[0].type, 'DeclareTsumo');
  assert.equal(result.candidates.reduce((sum, a) => sum + a.probability, 0), 100);
  assert.equal(result.actions.reduce((sum, a) => sum + a.probability, 0), 100);
  const hand = ['m1', 'm1', 'm1', 'm1', 'p1', 'p2', 'p3', 's1', 's2', 's3', 'z5', 'z5', 'z4', 'z4'];
  const kan = fixture({ hand, drawn: 'm1', legal: [{ index: 12, type: 'Ankan', handTiles: ['m1', 'm1', 'm1', 'm1'] },
    ...[...new Set(hand)].map((tile, index) => ({ index, type: 'Discard', tile }))] });
  assert.ok(analyze(kan).actions.some(a => a.type === 'Ankan'));
});

test('analysis JSON-line protocol coexists with normal bot decisions and rejects invalid phases', () => {
  const a = fixture({ mode: 'analysis', id: 701 });
  const b = fixture({ id: 702 });
  const c = fixture({ mode: 'analysis', id: 703, phase: 'invalid' });
  const run = spawnSync(process.execPath, [require.resolve('./worker.cjs')], {
    input: [a, c, b].map(s => JSON.stringify(s)).join('\n') + '\n', encoding: 'utf8', timeout: 20000,
  });
  assert.equal(run.status, 0, run.stderr);
  const replies = run.stdout.trim().split('\n').map(line => JSON.parse(line));
  assert.equal(replies[0].candidates[0].tile, 'z4');
  assert.equal(replies[1].id, 703);
  assert.equal(replies[1].index, -1);
  assert.equal(replies[2].id, 702);
  assert.ok(Number.isInteger(replies[2].index));
});

test('claim policy preserves distinct chi options and legal ron while a forced draw is 100 percent', () => {
  const { analyze } = require('./worker.cjs');
  const hand = ['m1', 'm2', 'm4', 'm5', 'm7', 'm8', 'p1', 'p2', 'p3', 's1', 's2', 'z1', 'z2'];
  const s = fixture({ hand, drawn: null, phase: 'claim', claimSource: 'DISCARD',
    lastDiscard: { seat: 3, tile: 'm3' }, rivers: [[], [], [], [{ tile: 'm3', riichi: false, called: false }]],
    legal: [{ index: 10, type: 'Chi', handTiles: ['m1', 'm2'] }, { index: 11, type: 'Chi', handTiles: ['m4', 'm5'] }, { index: 12, type: 'Pass' }] });
  const result = analyze(s);
  assert.deepEqual(result.actions.map(a => a.index).sort((a, b) => a - b), [10, 11, 12]);
  assert.equal(result.actions.reduce((sum, a) => sum + a.probability, 0), 100);
  const ron = analyze({ ...s, legal: [...s.legal, { index: 15, type: 'DeclareRon' }] });
  assert.equal(ron.actions[0].type, 'DeclareRon');
  const drawn = analyze(fixture({ hand, drawn: null, phase: 'draw', legal: [{ index: 20, type: 'Draw' }] }));
  assert.deepEqual(drawn.candidates, []);
  assert.equal(drawn.actions[0].probability, 100);
});


test('discard kinds collapse red fives and repeated physical/action aliases before normalization', () => {
  const { discardKindDistribution, tileKind } = require('./worker.cjs');
  assert.equal(tileKind('m0'), 'm5');
  assert.equal(tileKind('p0'), 'p5');
  assert.equal(tileKind('s0'), 's5');
  assert.equal(tileKind('z5'), 'z5');
  const options = [{ tile: 'm0', logit: 0, value: 10, risk: 1 },
    { tile: 'm5', logit: 1, value: 20, risk: 1 }, { tile: 'p1', logit: .5, value: 15, risk: 0 }];
  const scores = discardKindDistribution(options, null);
  assert.deepEqual(scores.map(c => c.tile), ['m5', 'p1']);
  assert.equal(scores.reduce((sum, c) => sum + c.probability, 0), 100);
  assert.ok(Math.abs(scores[0].policyProbability - 1 / (1 + Math.exp(-.5 / .75))) < 1e-12);
  assert.deepEqual(discardKindDistribution([...options, options[0], options[1], options[1]], null), scores);
  assert.deepEqual(discardKindDistribution(options, 'm0'), discardKindDistribution(options, 'm5'));
});

test('real analysis reports a single five-kind score while raw discard and riichi actions remain distinct', () => {
  const { analyze, tileKind } = require('./worker.cjs');
  const hand = ['m1', 'm2', 'm3', 'm4', 'm5', 'm0', 'p1', 'p2', 'p3', 's4', 's5', 'z5', 'z5', 'z4'];
  const legal = [...new Set(hand)].map((tile, index) => ({ index, type: 'Discard', tile }));
  const plain = analyze(fixture({ hand, legal }));
  const repeated = analyze(fixture({ hand, legal: [...legal, ...legal.map(a => ({ ...a, index: a.index + 100 }))] }));
  assert.deepEqual(plain.candidates, repeated.candidates, 'extra entity/action identities must not add probability mass');
  const kinds = new Set(legal.map(a => tileKind(a.tile)));
  assert.equal(plain.candidates.length, kinds.size);
  assert.equal(plain.candidates.filter(c => c.tile === 'm5').length, 1);
  assert.ok(!plain.candidates.some(c => c.tile === 'm0'));
  assert.equal(plain.candidates.reduce((sum, c) => sum + c.probability, 0), 100);
  const riichi = analyze(fixture({ hand, legal: [...legal,
    { index: 200, type: 'DiscardWithRiichi', tile: 'm0' },
    { index: 201, type: 'DiscardWithRiichi', tile: 'm5' }] }));
  assert.equal(riichi.candidates.filter(c => c.tile === 'm5').length, 1);
  assert.equal(riichi.candidates.length, kinds.size);
  assert.equal(riichi.candidates.reduce((sum, c) => sum + c.probability, 0), 100);
  assert.ok(riichi.actions.some(a => a.index === 200 && a.tile === 'm0'));
  assert.ok(riichi.actions.some(a => a.index === 201 && a.tile === 'm5'));
});
