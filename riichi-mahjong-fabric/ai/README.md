# Four-player AI worker

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
The Mod currently does not record a globally ordered discard history or historical
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
