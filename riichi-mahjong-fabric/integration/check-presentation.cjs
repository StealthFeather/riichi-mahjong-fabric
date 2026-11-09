// Usage: node check-presentation.cjs <server-report.json> <client-entities.jsonl>
const fs=require('node:fs'),assert=require('node:assert/strict');
const [serverFile,clientFile]=process.argv.slice(2);assert(serverFile&&clientFile,'Provide server and actual vanilla client captures');
const server=JSON.parse(fs.readFileSync(serverFile,'utf8'));assert.equal(server.passed,true);assert.equal(server.clientConnected,true);
assert.equal(server.paymentScenarios.length,5);assert.equal(server.meldTiles.length,16);
for(const scenario of server.paymentScenarios){
    assert.equal(scenario.passed,true);assert.equal(scenario.actualLastWallDraw,true);assert.equal(scenario.realClaimPasses,true);
    assert.deepEqual(scenario.phases,['AwaitingDraw','AwaitingDiscard','AwaitingClaims','RoundEnded']);
    assert(scenario.claimResponders.length>0);assert.equal(scenario.publicPointsMatch,true);assert.equal(scenario.revealMaskMatches,true);
    const expected=scenario.playerCount===4?({1:[-1000,3000,-1000,-1000],2:[-1500,1500,1500,-1500],3:[-3000,1000,1000,1000]})[scenario.tenpaiCount]:
        ({1:[-1500,3000,-1500],2:[-3000,1500,1500]})[scenario.tenpaiCount];
    assert.deepEqual(scenario.expectedDeltas,expected);assert.deepEqual(scenario.actualPoints,expected.map(delta=>scenario.initialPoints+delta));
    assert.equal(expected.reduce((a,b)=>a+b,0),0);
}
const samples=fs.readFileSync(clientFile,'utf8').trim().split(/\r?\n/).map(JSON.parse);assert(samples.length>=3,'Need three stable actual-client samples');
const close=(a,b)=>Math.abs(a-b)<1e-5;
const quaternion=text=>{const q=text.match(/[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?/g)?.map(Number);assert.equal(q?.length,4,'Unexpected quaternion format: '+text);return q;};
const dot=(a,b)=>a.reduce((sum,v,i)=>sum+v*b[i],0);
let first;
for(const sample of samples){
    const stable=[];
    for(const expected of server.meldTiles){
        const at=sample.itemDisplays.filter(entity=>entity.position.every((v,index)=>close(v,expected.position[index])));
        assert.equal(at.length,1,`Missing or overlapping concealed-kan tile ${expected.key}`);const actual=at[0];
        assert.equal(actual.itemModel,expected.expectedModel);assert.equal(actual.itemModel.endsWith('/tile_back'),expected.faceDown);
        assert(actual.scale.every((v,index)=>close(v,expected.scale[index])));
        const left=quaternion(actual.leftRotation);assert(close(Math.abs(dot(left,expected.expectedLeftRotation)),1),`Wrong face-up/down orientation at ${expected.key}`);
        const right=quaternion(actual.rightRotation);assert(close(Math.abs(right[1]),1)&&close(right[0],0)&&close(right[2],0)&&close(right[3],0),'Unexpected model correction rotation');
        stable.push([actual.id,actual.itemModel,actual.position,actual.scale,actual.leftRotation,actual.rightRotation]);
    }
    if(first)assert.deepEqual(stable,first,'Concealed-kan client entities changed between steady-state samples');else first=stable;
}
for(let seat=0;seat<4;seat++){
    const row=server.meldTiles.filter(tile=>tile.seat===seat).sort((a,b)=>a.index-b.index);
    assert.deepEqual(row.map(tile=>tile.faceDown),[true,false,false,true]);
    assert(close(Math.abs(dot(row[0].expectedLeftRotation,row[3].expectedLeftRotation)),1));
    assert(close(Math.abs(dot(row[1].expectedLeftRotation,row[2].expectedLeftRotation)),1));
    assert(close(dot(row[0].expectedLeftRotation,row[1].expectedLeftRotation),0),'Concealed-kan ends are not flipped 180 degrees from the middle');
}
console.log(JSON.stringify({kind:'meld-draw-payments',passed:true,version:server.coreVersion,clientVisualTest:true,
    concealedKan:{passed:true,samples:samples.length,seats:4,tiles:16,checks:['all-four-seat-client-models','outer-pair-concealed-and-face-down','middle-pair-visible-and-face-up','actual-quaternion-and-scale','one-display-per-position','stable-client-entities'],capture:clientFile},
    drawPayments:{passed:true,scenarios:server.paymentScenarios,checks:['actual-last-wall-draw','final-discard-real-ron-claims-and-passes','payments-applied-only-after-claims','four-player-one-two-three-tenpai-3000-pool','three-player-one-two-tenpai-3000-pool','point-total-conserved','public-score-and-reveal-mask-match']}},null,2));
