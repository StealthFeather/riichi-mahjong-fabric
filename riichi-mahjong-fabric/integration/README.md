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

The addon puts a real table at overworld `0 -60 0`, gives the first connected test player the relevant seat, and tests actual table ticks, accepted discard actions, twenty-tick sort timing, connected-player private holders, public rendering, tenpai reveal allowlists, centre-plate riichi sticks, and real claim/tsumo button construction. Results are written to the server's `riichi-integration-results.json`; success is logged as `RIICHI_INTEGRATION_PASS`. Without a connected client the private-holder checks are skipped and `clientConnected` is false.

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
