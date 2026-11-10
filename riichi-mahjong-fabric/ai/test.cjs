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
