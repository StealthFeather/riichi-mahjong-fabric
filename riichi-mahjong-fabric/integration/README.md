# Isolated Minecraft integration addon

This addon is kept outside `src/main` and is never part of the production JAR. It injects deterministic fixtures by reflection and changes a small area of its test world. **Install only in a disposable local test server.** It refuses to initialize unless the server working directory contains the marker file `riichi-integration-enabled`.

After building the main mod, build with the same Java environment:

```powershell
node riichi-mahjong-fabric/integration/build-addon.cjs
```

Install `build/integration-addon/riichi-integration-test-only.jar` beside the main mod only on the isolated server. Create the marker before starting it. With a vanilla client connected, run this server-console command:

```text
riichi_test run
```

The addon puts a real table at overworld `0 -60 0`, gives the first connected test player the relevant seat, and tests actual table ticks, accepted discard actions, ten-tick empty-slot retention and compaction timing, connected-player private holders, public rendering, tenpai reveal allowlists, centre-plate riichi sticks, and real claim/tsumo button construction. Results are written to the server's `riichi-integration-results.json`; success is logged as `RIICHI_INTEGRATION_PASS`. Without a connected client the private-holder checks are skipped and `clientConnected` is false.

For visible manual checks, leave an individual scenario in place:

```text
riichi_test fixture draw
riichi_test fixture tsumo
riichi_test fixture claims
riichi_test fixture all_tenpai
riichi_test fixture mixed
riichi_test fixture riichi
```

The player is teleported to the appropriate side and put into creative mode. `draw` has a riichi button and a separated drawn tile; `claims` gives the player chi, pon and daiminkan candidates. `all_tenpai` reveals all four hands, while `mixed` reveals only seats zero and two. `riichi` shows four current declarations and two deposits carried from previous hands.

`riichi_test state` prints the current phase, owner seat, queued action flag, riichi toggle and button indices to the server log. `riichi_test aim <index>` turns the connected owner's camera precisely toward that button's hit-plane centre; it only changes the view direction and does not execute an action. The normal vanilla use-item action can then test the real raycast and interaction packet path.

For the overlapping public/private hand regression, the test-only `ClientProbe` accepts `entities 30 200 <label>` and writes thirty client entity snapshots at 5 Hz. Once a fixture has settled, check its capture with:

```text
node riichi-mahjong-fabric/integration/check-hand-entities.cjs <capture.jsonl> 14 owner
```

Check `draw` on a fresh table, `all_tenpai` followed by `draw`, and `draw_observer` followed by `draw`. The observer fixture keeps all four controllers inert and removes the connected player's seat, so its own capture must pass with `14 observer`. Each hand position must have one display, the owner must see faces and the observer backs, and IDs/transforms must stay stable for all samples. This checks the real client entity set; counting private-holder elements alone misses public back entities already sent to that client. Test fixtures are not saved-game compatibility tests; use a fresh disposable world after restarting a fixture server.

For teleport recovery, keep the `draw` fixture unchanged and record at least three settled client samples before leaving, while far away, and after returning. Repeat the away/return pair to cover multiple trips:

```text
node riichi-mahjong-fabric/integration/check-teleport-entities.cjs owner <before.jsonl> <away.jsonl> <returned.jsonl> [away2.jsonl returned2.jsonl ...]
```

Use `observer` with `draw_observer` to verify that returning spectators still see only concealed hand backs. The checker requires the complete table and all four hand rows to recover, compares every item model and transform plus table labels/private buttons against the initial capture, rejects overlapping displays, and verifies that no original-table displays remain while away. Entity IDs may change between trips but must stay stable within each settled capture. Captures alone do not prove a server chunk unloaded or a dimension changed; record those conditions separately from the server when testing those paths.

`riichi_test view_state` prints `RIICHI_VIEW_STATE` JSON without acquiring an unloaded chunk or requiring an active driver. It records chunk availability, view identity, attachment validity and chunk identity, public item/watcher counts, private hand count, and server/player dimensions. `riichi_test detach_view` destroys only the current public holder's attachment to simulate Polymer unloading it; the normal mod loop must recover the view without the addon calling `ensure` or refreshing it.

`riichi_test view_lifecycle` automates that detached-attachment regression on a fresh `draw` fixture. It waits three ticks, detaches the attachment, then observes recovery two ticks later. The view must be replaced, its public/private displays and watchers restored, and the old holders disposed while the driver, round, hands and points stay unchanged. It also queues an unavailable remote chunk and verifies that its pending request survives those two ticks without synchronously loading the chunk. Results are written to `riichi-view-lifecycle-results.json`; success prints `RIICHI_VIEW_LIFECYCLE_PASS`. This deterministic test complements actual player teleport/unload captures.

For the exact discard-gap regression, connect a vanilla client, run `riichi_test fixture discard_gap`, and wait for `RIICHI_GAP_BEFORE_READY`. The test freezes game ticks so entity packets can settle. Capture three client entity samples and a screenshot, then run `riichi_test gap_discard` and capture again after `relative=0`. Run `tick step 9`, wait for `relative=9`, capture; then run `tick step 1`, wait for `relative=10`, and capture. Finish with `riichi_test gap_finish`, which also tests a real server ray through the empty slot and another ray toward a shifted survivor. Compare all four captures with:

```text
node riichi-mahjong-fabric/integration/check-discard-gap.cjs <server/riichi-discard-gap-results.json> <before.jsonl> <tick0.jsonl> <tick9.jsonl> <tick10.jsonl>
```

The fixture discards the unique souzu-nine tile in slot eight. All other entities must retain their original coordinates and faces through tick nine; the old drawn tile stays in its separated right-hand position. At tick ten the thirteen survivors must occupy contiguous sorted slots. Remove the fixture table, unfreeze ticks, quit the client and stop the server after testing.

The test loader registers its end-tick callback before the mod's render callback, so the addon invokes that same view update before reading server-side geometry. This changes no game time. The timing evidence comes from real game ticks, the frozen vanilla client's `gameTime`, and actual received display entities.

`riichi_test presentation` validates concealed kans and exhaustive-draw payments. It starts five real driver scenarios with one live-wall tile, draws that tile, discards it, verifies that legal ron claims remain open without an early payment, submits real passes, then checks the resulting exhaustive-draw scores and matching public hand reveals. Four-player scenarios cover one, two and three tenpai players; three-player scenarios cover one and two. Each uses a 3000-point noten pool, and each player has a middle-tile river so nagashi mangan cannot replace the noten settlement.

After those checks it leaves four concealed kans, one at each table side. Wait for `RIICHI_PRESENTATION_PASS`, record three actual client entity snapshots plus a screenshot, and compare the entities with:

```text
node riichi-mahjong-fabric/integration/check-presentation.cjs <server/riichi-presentation-results.json> <capture.jsonl>
```

The checker confirms all sixteen actual entity models and quaternions: both outer tiles use concealed models and flip 180 degrees from the face-up middle pair. The server report records actual payment phases and points; it does not inject an already-ended draw result.

`riichi_test interaction` runs the `.13` controls regression with a connected vanilla player. It uses the actual `PrivateHand` button ray handler and table click path for ron, chankan and tsumo: the first skip opens confirmation without queueing, return restores the win, a wrong token and old private holder cannot submit, and only explicit confirmation queues a claim pass. A confirmed tsumo skip remains local and ordinary discards remain available. It also waits eight real server ticks to verify that a riichi player's confirmed tsumo skip resumes the existing automatic drawn-tile discard.

The same run calls the real block-use path while sneaking, the settings menu's own-seat slot and the lobby virtual seat's interaction handler, asserting that all retain the seat. It executes registered `mahjong leave` and `mahjong join` commands as the connected player and checks BOT takeover, unchanged hand/scores and original-seat recovery. These are server handler tests; they do not manufacture client mouse packets.

Wait for `RIICHI_INTERACTION_PASS`. Results are written to `riichi-interaction-results.json`, including `checks`, `buttonDisplays` and `denseButtonDisplays`. Every expected display records its text, position, scale, left quaternion, index, along coordinate, hit-plane centre, width/depth and expected 27-font-pixel edge gap. The final fixture is `dragon_buttons`, with real white/green/red concealed-kan choices labelled 白/发/中. Capture its actual client `textDisplays`; then run `riichi_test fixture dense_buttons` and capture the reduced-scale claim buttons against `denseButtonDisplays`. Both fixtures can also be selected independently, as can `ron`, `chankan` and `riichi_tsumo`.

For placement-sound packets, run each case with the test-only client's sound probe already recording:

```text
riichi_test sound_case normal
riichi_test sound_case riichi
riichi_test sound_case bot
riichi_test sound_case invalid
riichi_test sound_case duplicate
riichi_test sound_case claim
riichi_test sound_case final
```

Run cases serially, waiting for `RIICHI_SOUND_CASE_PASS <name>` before the next. Each resets the fixture and uses subsequent real table ticks rather than calling the sound helper directly. The report appends `soundCases` with `startEpochMillis`, `endEpochMillis`, accepted-discard count, expected packet count and the sound signature: `minecraft:block.stone.place`, ambient source, volume 1, pitch 2, table-centre position `[0.5,-59.5,0.5]`. Normal, riichi, BOT, duplicate clicks and final discard each expect one packet; an invalid hand index and an accepted pon expect zero. The BOT window lasts 32 ticks to include its normal riichi thinking delay; other windows last six ticks. Compare actual client SoundPackets within these windows, allowing only a small delivery margin outside the server timestamps. The server report alone cannot prove that a client received the sound.
