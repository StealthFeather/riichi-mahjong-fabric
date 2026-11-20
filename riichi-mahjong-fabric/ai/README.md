# Four-player AI workers

Standard four-player Mahjong Soul, Tenhou and M-League tables use the local Mortal worker for live decisions and replay scoring in both East-only and East–South matches. Other four-player tables and live fallback decisions use Kobalab. [Mortal installation and replay scoring](../docs/0.5.4.md)

<!-- timory-resource-repository:start -->
## 材质包统一维护

材质包独立维护于 [StealthFeather/Timory-ResourcePack](https://github.com/StealthFeather/Timory-ResourcePack)。GitHub 独立仓库是统一维护来源；主工作区 `TimoryResource/` 是现有构建脚本的兼容工作目录，独立检出位于 `projects/timory-resource-pack/`。

**每次涉及材质包的设计、修改或生成（含贴图、模型、物品定义、语言、图集、动画和元数据），必须先从 GitHub 同步最新内容，再开始修改。** 在 Timory 主工作区根目录执行：

```powershell
git -C projects/timory-resource-pack fetch origin
git -C projects/timory-resource-pack pull --ff-only origin main
```

首次使用先克隆该仓库；本地有未提交改动或分支分叉时先保留并合并，解决冲突后再修改。同步后比较并合并独立检出与 `TimoryResource/` 的差异；资源生成脚本同样遵循此顺序。保留其他项目资源、图集条目与许可证，禁止用旧快照或单项目包覆盖共享内容。验证后的材质包改动需提交并推送回独立仓库，具体流程见其 [README](https://github.com/StealthFeather/Timory-ResourcePack#readme)。
<!-- timory-resource-repository:end -->

The Mod starts `worker.cjs` as a local, persistent Node.js 22+ process. It sends one
JSON request per line and receives `{id,index,engine}`. The index refers to the
request's authoritative `legal` action list. An invalid request or strategy error
returns `{id,index:-1,error}`; the Java adapter owns fallback and time limits.
Standard output is reserved for this protocol. No network service is used.

Runtime packages are vendored from the exact npm release tarballs recorded in
`dependencies.json`; no npm installation, downloads, model files or GPU are needed
on the Minecraft server. Both packages retain their original MIT licenses and
unmodified source. The downloaded tarballs are kept as source provenance and are
not included in the Mod runtime. Vendored development utilities are not executed.

| Mod setting | Pinned upstream strategy |
| --- | --- |
| EASY / 初级 | `@kobalab/majiang-ai@1.3.0/legacy` → `0002` |
| NORMAL / 中级 | `@kobalab/majiang-ai@1.3.0/legacy` → `0202` |
| HARD / 高级 | `@kobalab/majiang-ai@1.3.0` current player |

These are separate historical algorithms, not an upstream difficulty parameter or
a measured ranking guarantee. EASY optimizes theoretical effective tiles; NORMAL
adds visible-tile counts, yaku-aware calls and defense; HARD evaluates expected
value, calls and risk. Difficulty strength needs larger paired-game calibration.

## Input boundary

`AiSnapshot.java` defines the schema. The worker receives its own concealed hand,
public melds/rivers, declared riichi, revealed indicators, scores, current public
discard, remaining tile count, rules and legal action descriptions. It receives
no opponent concealed hands, wall order, ura-dora or unrevealed indicators.

Tile codes are `m1`…`m9`, `p1`…`p9`, `s1`…`s9`, honors `z1`…`z7`, and red fives
`m0`, `p0`, `s0`. `hand` includes the drawn tile. `drawn` is its exact identity,
or null after chi/pon and while responding to a discard. Seats use the Mod's
absolute indices; the worker rotates them to the engine's dealer-relative model.
Claimed river entries set `called:true` and are counted in their meld exactly once.

Every decision rebuilds the upstream player from this snapshot, so process
restart, saved-game reload and human/BOT handover do not require an event replay.
The Kobalab snapshot omits the global discard order and historical
tsumogiri. The reconstruction therefore uses only each opponent's own discards as
certain safe tiles and does not infer additional tiles discarded after riichi.
This is conservative and may differ from upstream's decisions with a complete
chronological game log.

The worker limits its top-level options to the Mod's legal actions. Simulated
future options use the upstream rules helpers. Legal wins are accepted directly
from the Mod; final game legality and scoring stay with the Mod. Three-player
snapshots are explicitly rejected; three-player tables keep the existing BOT.

## Verification

Run `node --test ai/test.cjs`. Tests cover actual strategy differences, riichi,
defense, yakuhai calls, red tiles, source-seat rotations, kan notation, called-tile
counts, deterministic reloads and JSON-line error recovery. Java and real-server
integration tests cover the snapshot boundary and controller lifecycle separately.

## Replay weights and consistency scores (cache version 4)

For Mortal replays, the worker normalizes legal Q-values using softmax at temperature 1. Each discard kind takes its best legal variant before normalization. Separate action weights retain red-tile, riichi and kan choices. Both distributions reach Java at full precision. The cache records the scoring policy and the engine used for each evaluated frame. [Mortal worker details](mortal/README.md)

### Other rules

Replay analysis exposes **modeled policy probabilities**. The bundled AI is a
deterministic strategy and does not provide native action probabilities. These
percentages are neither win probabilities nor calibrated predictions of human
choices. The Kobalab policy is named `modeled-kind-softmax-v3`.

For four-player discard candidates, reuse the hard strategy's expected value,
shanten and public-information danger estimate:

```
utility = max(0, EV) * exp(-3 * max(0, extraShanten)) / (1 + danger / 3.2)
logit = log(1 + utility)
```

The deterministic hard strategy's recommendation receives a logit equal to the
largest candidate logit plus 0.5. This preserves defensive recommendations even
when a dangerous attacking discard has a larger raw EV. Apply stable softmax
with temperature 0.75 to obtain a probability distribution over the 34 tile kinds.
Each kind appears once, including choices whose integer percentage is zero.
Red and ordinary fives share `m5`/`p5`/`s5`; repeated physical copies and normal
versus riichi discards do not create extra candidates or extra probability mass.
The legal red/ordinary variants retain their own EV during evaluation; the best
legal variant supplies that kind's utility once before softmax. Exact physical
faces and normal/riichi action IDs remain in the authoritative action list for
matching the actual recorded operation.

Two distributions are normalized independently:

- **Discard kinds**, conditional on choosing to discard, sum to one even when a
  kan or win is also legal. Their stored integer values sum to 100.
- **All legal actions**, including each specific discard/riichi combination,
  chi, pon, kan, kita, win, pass and draw, sum to one separately. Action indices
  refer to the authoritative Java engine's legal list. Calls and kans use the
  same AI's post-action hand evaluation; legal wins receive an additional 2
  logit units, riichi 0.15, and abortive draw -1. The deterministic full-action
  recommendation receives the same 0.5 margin before softmax. The replay
  displays a weight per discard kind and annotates the highest-ranked action buttons.

Integer percentages use the largest-remainder method: floor all percentages,
then assign the remaining percentage points to the largest fractional parts.
This conserves exactly 100, assigns a forced choice 100 internally, and avoids
independent rounding errors. From Mod 26.3.27, displayed probability scores use
exactly two digits (01, 09, ..., 99), capped at 99 for presentation; stored
probabilities and 100-point consistency scores keep their original values.
Ranks use the full precision probabilities; a
rounded tie does not erase the ranking.

Three-player analysis uses the existing Java fallback shanten and visible
ukeire evaluator with the actual sanma tile set, the same softmax temperature,
and the same largest-remainder rule. The original three-player BOT provides the
full-action recommendation. This is a separate modeled policy with lower
strategic detail than the four-player engine.

### Scoring across rules

A replay result retains the actual recorded discard kind, its conditional
probability, its full-distribution rank, and the corresponding exact normal/riichi
action index. The timeline matches only the immediately resulting discard of
that decision, so a kan, kita or win cannot accidentally acquire a later turn's
discard. Each actual discard receives a consistency score relative to the most
probable kind: `100 * Pchosen / Pmax`, using full precision probabilities. An
optimal choice scores 100 even when several alternatives have substantial mass.
Per-hand scores average that hand's actual discard scores. The match score
averages every actual discard across all hands, weighted by discard count rather
than by hand. Only the final aggregate is rounded to an integer; fractional
per-discard scores remain in cache. The GUI displays a final match score only
when the complete analysis is available.

Analysis only reads immutable recorded frames. It never submits an action to
the rule engine. The worker still receives only the analyzed player's own hand
and public information; recorded future actions are consulted afterward solely
to identify which historic choice was actually made.
