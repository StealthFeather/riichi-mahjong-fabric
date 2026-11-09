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
