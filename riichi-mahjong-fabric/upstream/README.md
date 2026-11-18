# Riichi Mahjong

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

Riichi Mahjong adds a fully playable Riichi Mahjong table to Minecraft on **NeoForge**.

## Features

- Full Riichi Mahjong ruleset with all yaku and specific rules (even chankan)
- Rule presets (Mahjong Soul / Tenhou / WRC)
- 1..4 player modes - bots fill empty seats
- Mahjong altar: craft items by scoring playing mahjong (what?)
- Yaku generator: generate RF energy by playing mahjong (what?)
- Fake mahjong: another game which was called mahjong for some reason
- FOV normalizer: enforce FOV in the area
- Achievements

## Dependencies

- Minecraft 26.2 (NeoForge 26.2.0.64+) or 26.1.2 (NeoForge 26.1.2.97+)

Recipes are balanced for vanilla - no extra dependencies required

## Credits

Btw Fabric also has mahjong! [MahjongCraft](https://www.curseforge.com/minecraft/mc-mods/mahjongcraft).


## TODO
-ux visual indicator who's turn is it
-Move buttons taller. And dont move them up at all
-riichi wait show (highlight furiten) -! winningTiles !
-make turns slower (settings, default 20 ticks)
-Sideways tile spacing: the rotated tile occupies more lateral space visually but the layout still uses spacing per slot, so the rotated tile may overlap its neighbours
-hide opponent tiles only to opponents, only client-side participant
-when I discard, my discarded tile isnt highlighted, so if someone rons me, its confusing (different tile shown)
-Uma / placement bonuses (+15/0/-15, +10/-30, etc.)
-When e.g. tile is claimed - it moves from discard to meld. How do we animate that? Like, I'd like having smooth controlled motion for the tiles that move across the board...
-minimum-han flags
-Ankan face-down convention
-Kakan stacked tile
-Local yaku (sanrenkou, paarenchan, shiisanputa, etc.)
-idle table should allow free placement of tiles
