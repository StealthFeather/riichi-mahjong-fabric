// Verify the unchanged draw fixture through one or more real teleport round trips.
// Usage: node check-teleport-entities.cjs <owner|observer> <before.jsonl> <away.jsonl> <returned.jsonl> [away.jsonl returned.jsonl ...]
// Each capture needs at least three settled ClientProbe samples. The fixture is at (0,-60,0).
const fs=require('node:fs'),assert=require('node:assert/strict');
const [mode,...files]=process.argv.slice(2);
assert(['owner','observer'].includes(mode)&&files.length>=3&&files.length%2===1,
    'Provide owner/observer mode, a before capture, then an away/returned capture pair for every round trip');
const near=(a,b)=>Math.abs(a-b)<1e-5;
const positionKey=position=>position.map(value=>Number(value.toFixed(5))).join(',');
const atTable=entity=>entity.itemModel?.startsWith('riichi_mahjong:')&&
    Math.abs(entity.position[0]-.5)<2&&Math.abs(entity.position[2]-.5)<2&&Math.abs(entity.position[1]+59.5)<2;
const tableItems=sample=>sample.itemDisplays.filter(atTable);
const tableTexts=sample=>(sample.textDisplays||[]).filter(entity=>
    Math.abs(entity.position[0]-.5)<2&&Math.abs(entity.position[2]-.5)<2&&Math.abs(entity.position[1]+59.5)<2);
const itemSignature=items=>items.map(entity=>[
    entity.itemModel,entity.position,entity.scale,entity.translation,entity.leftRotation,entity.rightRotation
]).sort((a,b)=>JSON.stringify(a).localeCompare(JSON.stringify(b)));
const textSignature=items=>items.map(entity=>[
    entity.text,entity.position,entity.scale,entity.leftRotation
]).sort((a,b)=>JSON.stringify(a).localeCompare(JSON.stringify(b)));
function equivalent(actual,expected,label){
    // Existing displays can have 1/4096-block relative-move quantization, while
    // newly spawned displays receive full coordinates. Both render identically.
    if(typeof actual==='number'&&typeof expected==='number'){
        assert(Math.abs(actual-expected)<=1/4096+1e-9,`${label}: transform changed beyond packet coordinate precision`);
    }else if(Array.isArray(actual)&&Array.isArray(expected)){
        assert.equal(actual.length,expected.length,label);actual.forEach((value,index)=>equivalent(value,expected[index],label));
    }else assert.deepEqual(actual,expected,label);
}
function validateHands(items,label){
    const directions=[[0,-1],[-1,0],[0,1],[1,0]];
    for(let seat=0;seat<4;seat++){
        const [dx,dz]=directions[seat],axis=dx===0?2:0,expected=.5+(dx||dz)*.85;
        const row=items.filter(entity=>near(entity.position[1],-59.35)&&near(entity.position[axis],expected));
        assert.equal(row.length,seat===0?14:13,`${label}: missing/duplicate hand displays at seat ${seat}`);
        assert.equal(new Set(row.map(entity=>positionKey(entity.position))).size,row.length,`${label}: overlapping hand displays`);
        for(const tile of row){
            const faceExpected=mode==='owner'&&seat===0;
            assert.equal(tile.itemModel.endsWith('/tile_back'),!faceExpected,
                `${label}: wrong hand audience at seat ${seat}; owner must see faces and opponents must retain backs`);
        }
    }
}
let baselineItems,baselineTexts;
const captures=[];
for(let stage=0;stage<files.length;stage++){
    const file=files[stage],away=stage%2===1,label=stage===0?'before':`${away?'away':'returned'}-${Math.ceil(stage/2)}`;
    const samples=fs.readFileSync(file,'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
    assert(samples.length>=3,`${label}: need at least three settled actual-client samples`);
    let firstIds,firstItemSignature,firstTextSignature;
    for(const sample of samples){
        assert(Array.isArray(sample.playerPosition),`${label}: client player is disconnected`);
        const items=tableItems(sample),texts=tableTexts(sample);
        if(away){
            assert.equal(items.length,0,`${label}: stale table or private hand displays remain after leaving`);
            assert.equal(texts.length,0,`${label}: stale table labels/buttons remain after leaving`);
            continue;
        }
        const bases=items.filter(entity=>/^riichi_mahjong:bridge\/mahjong_table(?:_new)?$/.test(entity.itemModel));
        assert.equal(bases.length,1,`${label}: table model is missing or duplicated`);
        assert(bases[0].position.every((value,index)=>near(value,[.5,-59.5,.5][index])),`${label}: table model moved`);
        assert.equal(new Set(items.map(entity=>positionKey(entity.position))).size,items.length,`${label}: duplicate display positions`);
        validateHands(items,label);
        const itemState=itemSignature(items),textState=textSignature(texts);
        const ids=[...items,...texts].map(entity=>entity.id).sort((a,b)=>a-b);
        assert.equal(new Set(ids).size,ids.length,`${label}: duplicate client entity IDs`);
        if(firstIds){
            assert.deepEqual(ids,firstIds,`${label}: entity IDs keep changing after teleport settled`);
            assert.deepEqual(itemState,firstItemSignature,`${label}: item transforms keep changing after teleport settled`);
            assert.deepEqual(textState,firstTextSignature,`${label}: text keeps changing after teleport settled`);
        }else{firstIds=ids;firstItemSignature=itemState;firstTextSignature=textState;}
        if(stage===0){
            if(!baselineItems){baselineItems=itemState;baselineTexts=textState;}
        }else{
            equivalent(itemState,baselineItems,`${label}: the complete table/tiles/private hand did not recover`);
            equivalent(textState,baselineTexts,`${label}: table labels/private buttons did not recover`);
        }
    }
    captures.push({label,file,samples:samples.length,itemDisplays:tableItems(samples[0]).length,textDisplays:tableTexts(samples[0]).length,
        playerPosition:samples[0].playerPosition,gameTime:samples[0].gameTime});
}
console.log(JSON.stringify({kind:'teleport-table-recovery',passed:true,clientVisualTest:true,mode,roundTrips:(files.length-1)/2,
    checks:['three-settled-actual-client-samples-per-stage','one-table-model','all-four-hands-recovered','owner-faces-and-opponent-backs',
        'complete-public-and-private-item-transforms-restored','labels-and-private-buttons-restored','no-stale-table-displays-while-away',
        'no-overlapping-or-duplicate-displays','stable-entities-after-return'],captures},null,2));
