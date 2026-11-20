"""Mortal v4 four-player decision worker. One independent JSON request per line."""
import json
import os
import sys
from pathlib import Path

# Two server-side workers can run concurrently. Each needs at most one Rayon job.
os.environ.setdefault("RAYON_NUM_THREADS", "1")
os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")

import numpy as np
import onnxruntime as ort
from libriichi.mjai import Bot

SUITS = "mps"
HONORS = "ESWNPFC"
WIN_TYPES = {"DeclareTsumo", "DeclareRon", "DeclareChankan"}
KAN_TYPES = {"Ankan", "Kakan", "Daiminkan"}
CALL_TYPES = {"chi": "Chi", "pon": "Pon", "daiminkan": "Daiminkan", "ankan": "Ankan"}
TILES = [s + str(n) for s in SUITS for n in range(1, 10)] + [
    "z" + str(n) for n in range(1, 8)
] + ["m0", "p0", "s0"]
TILE_ID = {tile: index for index, tile in enumerate(TILES)}
# libriichi v4 encodes hand, reds, scores, rank, kyoku, honba, sticks, then two winds.
ROUND_PROGRESS_ROW = 4 + 3 + 8 + 4 + 4 + 1 + 1 + 2


def table_tile(pai):
    if pai in HONORS and len(pai) == 1:
        return "z" + str(HONORS.index(pai) + 1)
    if len(pai) == 3 and pai[0] == "5" and pai[2] == "r" and pai[1] in SUITS:
        return pai[1] + "0"
    if len(pai) == 2 and pai[0] in "123456789" and pai[1] in SUITS:
        return pai[1] + pai[0]
    raise ValueError("invalid MJAI tile")


def plain(tile):
    return tile[0] + "5" if tile[1] == "0" else tile


def action_mask(legal, claimed_tile):
    """Return main-action mask, riichi discard mask and kan selection mask."""
    main, reach, kan = (np.zeros(46, dtype=bool) for _ in range(3))
    for move in legal:
        kind = move["type"]
        if kind == "Discard":
            main[TILE_ID[move["tile"]]] = True
        elif kind == "DiscardWithRiichi":
            main[37] = True
            reach[TILE_ID[move["tile"]]] = True
        elif kind == "Chi":
            called_rank = int(plain(claimed_tile)[1])
            first_rank = min(int(plain(t)[1]) for t in [claimed_tile, *move["handTiles"]])
            main[38 + called_rank - first_rank] = True
        elif kind == "Pon":
            main[41] = True
        elif kind in KAN_TYPES:
            main[42] = True
            if kind in {"Ankan", "Kakan"}:
                tile = move["tile"] if kind == "Kakan" else move["handTiles"][0]
                kan[TILE_ID[plain(tile)]] = True
        elif kind in WIN_TYPES:
            main[43] = True
        elif kind == "KyuushuAbort":
            main[44] = True
        elif kind == "Pass":
            main[45] = True
    return main, reach, kan


class Engine:
    """Network is shared by sequential requests. Each request creates a seat-local Bot."""
    engine_type = "mortal"
    name = "Mortal"
    version = 4
    is_oracle = False
    # Force model inference even when the Rust engine sees only one discard.
    enable_quick_eval = False
    # Rust's optional placement heuristic assumes a Tenhou hanchan. Java owns table rules.
    enable_rule_based_agari_guard = False

    def __init__(self, model):
        options = ort.SessionOptions()
        options.intra_op_num_threads = 1
        options.inter_op_num_threads = 1
        self.session = ort.InferenceSession(str(model), options, providers=["CPUExecutionProvider"])

    def react_batch(self, obs, masks, invisible_obs):
        features = np.stack(obs)
        native = np.stack(masks)
        allowed = native.copy()
        # With quick eval off, libriichi appends the primary decision last and
        # optionally prepends a tile-selection row for a concealed or added kan.
        allowed[-1] &= self.main_mask
        if len(allowed) == 2:
            allowed[0] &= self.kan_mask
            if not allowed[0].any():
                # libriichi requests this row even when Java disallows all kans.
                # Supply a valid dummy kan selection while excluding kan from the main action.
                allowed[0] = native[0]
                allowed[-1, 42] = False
        if not allowed.any(axis=1).all():
            raise ValueError("no common legal action")
        if self.east_progress is not None:
            # Only the horizon changes. Field wind, seat wind and native legality
            # continue to describe the actual hand, including in South extension.
            features[:, ROUND_PROGRESS_ROW, :] = self.east_progress
        scores = self.session.run(["q_values"], {"obs": features, "mask": allowed.astype(np.float32)})[0]
        if not np.isfinite(scores[allowed]).all():
            raise ValueError("nonfinite Mortal score")
        scores = np.where(allowed, scores, -np.inf)
        return scores.argmax(axis=1).tolist(), scores.tolist(), allowed.tolist(), [True] * len(scores)


def select_index(action, legal, riichi, red_fives):
    kind = action["type"]
    if kind in {"dahai", "kakan"}:
        name = "Kakan" if kind == "kakan" else "DiscardWithRiichi" if riichi else "Discard"
        tile = table_tile(action["pai"])
        matches = [m for m in legal if m["type"] == name and m["tile"] == tile]
    elif kind in CALL_TYPES:
        # Native kan generation assumes one red five per suit. A no-red table
        # physically contains four normal fives, including the reserved red slot.
        consumed = [table_tile(t) for t in action["consumed"]]
        if not red_fives and kind in {"ankan", "daiminkan"}:
            consumed = [plain(t) for t in consumed]
        matches = [m for m in legal if m["type"] == CALL_TYPES[kind]
                   and sorted(m["handTiles"]) == sorted(consumed)]
    elif kind == "hora":
        names = {"DeclareTsumo"} if action["target"] == action["actor"] else {"DeclareRon", "DeclareChankan"}
        matches = [m for m in legal if m["type"] in names]
    else:
        name = {"none": "Pass", "ryukyoku": "KyuushuAbort"}.get(kind)
        matches = [m for m in legal if m["type"] == name]
    if not matches:
        raise ValueError("Mortal action has no matching Java move")
    return matches[0]["index"]


def decide(engine, request):
    events, legal = request["events"], request["legal"]
    if len(events) < 3 or events[0]["type"] != "start_game" or events[1]["type"] != "start_kyoku":
        raise ValueError("missing hand history")
    last = events[-1]
    claimed_tile = table_tile(last["pai"]) if last["type"] == "dahai" else None
    engine.main_mask, riichi_mask, engine.kan_mask = action_mask(legal, claimed_tile)
    engine.east_progress = None
    if request.get("length") == "EAST":
        start = events[1]
        hand_number = start["kyoku"] if start["bakaze"] == "E" else 4
        engine.east_progress = np.float32((hand_number + 3) / 7)
    bot = Bot(engine, request["seat"])
    for event in events[:-1]:
        bot.react(json.dumps(event), can_act=False)
    response = bot.react(json.dumps(last))
    if not response:
        raise ValueError("Mortal has no decision on the final event")
    action = json.loads(response)
    riichi = action["type"] == "reach"
    if riichi:
        engine.main_mask = riichi_mask
        discard = bot.react(json.dumps({"type": "reach", "actor": request["seat"]}))
        if not discard:
            raise ValueError("Mortal did not select a riichi discard")
        action = json.loads(discard)
        if action["type"] != "dahai":
            raise ValueError("invalid second riichi action")
    return select_index(action, legal, riichi, request.get("rules", {}).get("redFives", True))


def main():
    engine = Engine(Path(__file__).with_name("mortal.onnx"))
    for line in sys.stdin:
        req_id = None
        try:
            request = json.loads(line)
            req_id = request["id"]
            index = decide(engine, request)
            result = {"id": req_id, "index": index, "engine": "Mortal"}
        except Exception as exc:
            # The server owns fallback and its accounting. Never report an invented index.
            result = {"id": req_id, "error": str(exc).splitlines()[0][:200]}
        print(json.dumps(result), flush=True)


if __name__ == "__main__":
    main()
