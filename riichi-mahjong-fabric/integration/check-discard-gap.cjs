// Compare the actual vanilla-client hand entities before and exactly 0/9/10 ticks after a discard.
// Usage: node check-discard-gap.cjs <server-result.json> <before.jsonl> <tick0.jsonl> <tick9.jsonl> <tick10.jsonl>
const fs=require('node:fs'),assert=require('node:assert/strict');
const [serverFile,...files]=process.argv.slice(2);
assert(serverFile&&files.length===4,'Provide server result and four client captures (before, 0, 9, 10)');
const server=JSON.parse(fs.readFileSync(serverFile,'utf8'));
assert.equal(server.passed,true,'Server discard-gap integration failed');
assert.equal(server.delayTicks,10);assert.equal(server.gapSlot,8);
assert.equal(server.emptySlotIgnored,true);assert.equal(server.shiftedSlotMappedCorrectly,true);
const labels=['before','0','9','10'],captures={};
const near=(a,b)=>Math.abs(a-b)<1e-5;
const key=p=>p.map(n=>n.toFixed(5)).join(',');
function row(sample){return sample.itemDisplays.filter(e=>near(e.position[1],-59.35)&&near(e.position[2],-.35));}
for(let index=0;index<files.length;index++){
    const label=labels[index],samples=fs.readFileSync(files[index],'utf8').trim().split(/\r?\n/).map(JSON.parse);
    assert(samples.length>=3,'Need three stable samples per stage');
    const gameTime=server.snapshots[label].gameTime,expectedCount=label==='before'?14:13;
    let first;
    for(const sample of samples){
        assert.equal(sample.gameTime,gameTime,`Stage ${label} was not sampled at its frozen server tick`);
        const hand=row(sample);assert.equal(hand.length,expectedCount,`Stage ${label}: missing/duplicate client tile`);
        assert.equal(new Set(hand.map(e=>key(e.position))).size,expectedCount,'Overlapping hand displays');
        for(const tile of hand)assert(tile.itemModel?.startsWith('riichi_mahjong:')&&!tile.itemModel.endsWith('/tile_back'),'Owner must see exactly one face per tile');
        const stable=hand.map(e=>[e.id,e.itemModel,e.position,e.scale,e.translation,e.leftRotation,e.rightRotation]);
        if(first)assert.deepEqual(stable,first,`Stage ${label}: entities changed during frozen samples`);else first=stable;
    }
    captures[label]={file:files[index],samples:samples.length,gameTime,hand:row(samples[0])};
}
const before=captures.before.hand.sort((a,b)=>b.position[0]-a.position[0]);
assert.equal(captures['9'].gameTime-captures['0'].gameTime,9);assert.equal(captures['10'].gameTime-captures['0'].gameTime,10);
const hole=before[8].position,survivors=before.filter((_,index)=>index!==8);
const signature=hand=>hand.map(e=>[key(e.position),e.itemModel]).sort((a,b)=>a[0].localeCompare(b[0]));
for(const label of ['0','9']){
    assert.deepEqual(signature(captures[label].hand),signature(survivors),`Stage ${label}: a survivor moved, changed identity, or the hole was filled`);
    assert(!captures[label].hand.some(e=>key(e.position)===key(hole)),`Stage ${label}: discarded slot is occupied`);
    const drawn=before[13];assert(captures[label].hand.some(e=>key(e.position)===key(drawn.position)&&e.itemModel===drawn.itemModel),'Drawn tile lost its original separated position');
}
// North-facing fixture has positive along toward negative X; compaction starts at X=.94.
const compact=captures['10'].hand.sort((a,b)=>b.position[0]-a.position[0]);
for(let index=0;index<13;index++)assert(near(compact[index].position[0],.94-index*.073),`Tick ten did not close all physical slots at ${index}`);
assert.deepEqual(compact.map(e=>e.itemModel),server.snapshots['10'].tiles.map(code=>{
    const name=code<27?`tile_${['man','pin','sou'][Math.floor(code/9)]}_${code%9+1}`:
        code<31?`tile_wind_${['east','south','west','north'][code-27]}`:`tile_dragon_${['white','green','red'][code-31]}`;
    return `riichi_mahjong:bridge/${name}`;
}), 'Tick ten client order does not match server sorted identities');
console.log(JSON.stringify({kind:'discard-gap',passed:true,delayTicks:10,clientVisualTest:true,
    checks:['three-frozen-client-samples-per-stage','survivor-identities-and-positions-retained','empty-slot-at-zero-and-nine','old-draw-keeps-separated-right-position','exact-ten-tick-compaction','empty-slot-click-consumed','shifted-slot-ray-maps-correct-tile'],
    observations:{gapSlot:8,emptySlotObserved:true,remainingTilesStayedInPlace:true,drawnTileStayedSeparated:true,compactedAtTick:10,emptySlotIgnored:true,shiftedSlotMappedCorrectly:true},
    captures:Object.fromEntries(labels.map(label=>[label,{file:captures[label].file,samples:captures[label].samples,gameTime:captures[label].gameTime}]))},null,2));
