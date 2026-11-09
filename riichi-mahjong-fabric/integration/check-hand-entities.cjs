// Check actual vanilla-client entities, including public entities absent from PrivateHand's holder.
// Usage: node check-hand-entities.cjs <probe.jsonl> <13|14> <owner|observer>
const fs=require('node:fs'),assert=require('node:assert/strict');
const [file,countText,mode='owner']=process.argv.slice(2),count=Number(countText);
assert(file&&[13,14].includes(count)&&['owner','observer'].includes(mode),'Provide a probe JSONL, tile count and owner/observer mode');
const samples=fs.readFileSync(file,'utf8').trim().split(/\r?\n/).map(JSON.parse);
assert(samples.length>=30,'Need at least thirty steady-state samples');
let first;
for(const sample of samples){
    // Fixture's north hand: table at (0,-60,0), hand centres at y=-59.35, z=-0.35.
    const row=sample.itemDisplays.filter(e=>Math.abs(e.position[1]+59.35)<1e-5&&Math.abs(e.position[2]+.35)<1e-5);
    assert.equal(row.length,count,`Sample ${sample.sample}: missing or overlapping owner-row entities`);
    assert.equal(new Set(row.map(e=>e.position.map(v=>v.toFixed(5)).join(','))).size,count,'Two displays occupy one tile position');
    for(const tile of row){
        assert(tile.itemModel?.startsWith('riichi_mahjong:'),'Unexpected model in the hand row');
        assert.equal(tile.itemModel.endsWith('/tile_back'),mode==='observer','Wrong hand audience: public back overlaps a private face or observer sees a face');
    }
    const stable=row.map(e=>[e.id,e.itemModel,e.position,e.scale,e.translation,e.leftRotation,e.rightRotation]);
    if(first)assert.deepEqual(stable,first,'Hand entities were replaced or moved during the steady-state sample');
    else first=stable;
}
console.log(JSON.stringify({passed:true,file,samples:samples.length,count,mode,checks:['one-display-per-tile','correct-audience','stable-entities-and-transforms']}));
