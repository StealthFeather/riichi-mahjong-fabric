# Riichi Mahjong

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
