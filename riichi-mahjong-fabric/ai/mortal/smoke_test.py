"""Exercise the real installed worker through its production JSON-lines protocol."""
import json
import subprocess
import sys

UNKNOWN_HAND = ["?"] * 13


def request(hand, events, legal, seat=0, red=True, length="SOUTH"):
    hands = [UNKNOWN_HAND] * 4
    hands[seat] = hand.split()
    assert len(hands[seat]) == 13
    start = {"type": "start_kyoku", "bakaze": "E", "kyoku": 1, "oya": 0, "kyotaku": 0,
             "honba": 0, "dora_marker": "3p", "scores": [25000] * 4, "tehais": hands}
    return {"seat": seat, "events": [{"type": "start_game"}, start, *events],
            "length": length, "legal": legal, "rules": {"redFives": red}}


def draw(pai, seat=0):
    return {"type": "tsumo", "actor": seat, "pai": pai}


def discard(pai, seat=0):
    return {"type": "dahai", "actor": seat, "pai": pai, "tsumogiri": False}


def move(kind, index=31, **fields):
    return {"type": kind, "index": index, **fields}


def main(worker):
    hand = "1m 1m 4m 5m 1p 5p 8p 1s 4s 4s 6s 8s N"
    tiles = "m1 p1 s1 m4 s4 m5 p5 p6 s6 p8 s8 z4".split()
    opening = request(hand, [draw("6p")], [move("Discard", i, tile=t) for i, t in enumerate(tiles)])
    opening["events"][1].update(kyoku=3, oya=0, scores=[26000, 28300, 22000, 23700], dora_marker="3s")
    win_hand = "1m 2m 3m 1p 2p 3p 1s 2s 3s 7s 8s 9s E"
    calls = [draw("?", 0), discard("3m", 0)]
    call_hand = "1m 2m 3m 3m 4m 5mr 6p 7p 1s 2s 3s E S"
    five_hand = "5m 5m 5m 2p 3p 4p 6s 7s 8s E E P P"
    cases = [
        ("unrestricted discard", opening, 2),
        ("red discard", request(hand, [draw("5pr")], [move("Discard", tile="p0")]), 31),
        ("two-step riichi", request(win_hand, [draw("9p")], [move("DiscardWithRiichi", tile="p9")]), 31),
        ("tsumo", request(win_hand, [draw("E")], [move("DeclareTsumo")]), 31),
        ("ron", request(win_hand, [draw("?", 0), discard("E", 0)], [move("DeclareRon")], seat=1), 31),
        ("chi shape and red", request(call_hand, calls, [move("Chi", handTiles=["m0", "m4"])], seat=1), 31),
        ("pon", request(call_hand, calls, [move("Pon", handTiles=["m3", "m3"])], seat=1), 31),
        ("pass", request(call_hand, calls, [move("Pass")], seat=1), 31),
        ("normal five kan", request(five_hand, [draw("5m")], [move("Ankan", handTiles=["m5"] * 4)], red=False), 31),
        ("red five kan", request(five_hand, [draw("5mr")], [move("Ankan", handTiles=["m5"] * 3 + ["m0"])]), 31),
        ("open kan", request(five_hand, [draw("?", 0), discard("5mr", 0)],
                             [move("Daiminkan", handTiles=["m5"] * 3)], seat=1), 31),
        ("nine terminals", request("1m 9m 1p 9p 1s 9s E S W N P F 2s", [draw("5m")],
                                   [move("KyuushuAbort")]), 31),
        ("mismatched move", request(hand, [draw("6p")], [move("DeclareTsumo")]), None),
        ("after rejected request", opening, 2),
    ]
    added = [draw("?", 0), discard("5pr", 0), {"type": "pon", "actor": 1, "target": 0,
             "pai": "5pr", "consumed": ["5p", "5p"]}, discard("N", 1), draw("5p", 1)]
    cases.append(("added kan", request("5p 5p 1m 2m 3m 4s 5s 6s 8p 9p E S N", added,
                                       [move("Kakan", tile="p5")], seat=1), 31))
    wait = "1m 9m 1p 9p 1s 9s S W N P F C 9s"
    closed_kan = [draw("?", 0), {"type": "ankan", "actor": 0, "consumed": ["E"] * 4}]
    # This Tenhou engine does not offer kokushi on an ankan. The server must
    # receive an error and apply its existing policy under this house rule.
    cases.append(("unsupported ankan claim", request(wait, closed_kan, [move("DeclareChankan")], seat=1), None))
    opened = [draw("?", 2), discard("5p", 2), {"type": "pon", "actor": 0, "target": 2,
              "pai": "5p", "consumed": ["5p", "5p"]}, discard("N", 0), draw("N", 1), discard("N", 1), draw("?", 0),
              {"type": "kakan", "actor": 0, "pai": "5pr", "consumed": ["5p"] * 3}]
    waiting = "1m 2m 3m 1p 2p 3p 1s 2s 3s 7s 7s 4p 6p"
    cases.append(("chankan", request(waiting, opened, [move("DeclareChankan")], seat=1), 31))
    cases.append(("east-only", {**opening, "length": "EAST"}, 11))
    lines = [json.dumps({**req, "id": i}) for i, (_, req, _) in enumerate(cases)]
    run = subprocess.run([worker], input="\n".join(lines) + "\n", text=True,
                         stdout=subprocess.PIPE, check=True, timeout=45)
    replies = list(map(json.loads, run.stdout.splitlines()))
    assert len(replies) == len(cases)
    for i, ((name, _, expected), reply) in enumerate(zip(cases, replies)):
        if expected is None:
            assert reply["id"] == i and "error" in reply and "index" not in reply, (name, reply)
        else:
            assert reply == {"id": i, "index": expected, "engine": "Mortal"}, (name, reply)
    print(f"{len(cases)} Mortal worker checks passed")


if __name__ == "__main__":
    main(sys.argv[1])
