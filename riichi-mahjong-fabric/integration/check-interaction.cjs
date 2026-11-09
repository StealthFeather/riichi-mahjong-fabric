// Usage: node check-interaction.cjs <server-report.json> <text-entities.jsonl> <sound-capture-index.json>
const fs=require('node:fs'),assert=require('node:assert/strict');
const [serverFile,clientFile,soundIndexFile,denseClientFile]=process.argv.slice(2);assert(serverFile&&clientFile&&soundIndexFile,'Provide server report, actual text capture and actual SoundPacket capture index');
const server=JSON.parse(fs.readFileSync(serverFile,'utf8'));assert.equal(server.passed,true);assert.equal(server.clientConnected,true);
const expected=server.buttonDisplays;assert(expected?.length>=2,'Server must record actual button display expectations');
const near=(a,b)=>Math.abs(a-b)<1e-5;
const quaternion=value=>Array.isArray(value)?value:value.match(/[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?/g).map(Number);
const sameRotation=(a,b)=>near(Math.abs(quaternion(a).reduce((sum,v,i)=>sum+v*quaternion(b)[i],0)),1);
const position=entry=>entry.position??entry.textPosition;
const width=text=>Array.from(text).reduce((sum,char)=>sum+(char===' '?4:char==='1'?3:char>='0'&&char<='9'?4:9),0);
const samples=fs.readFileSync(clientFile,'utf8').trim().split(/\r?\n/).map(JSON.parse);assert(samples.length>=3);
for(const [expected,file] of [[server.buttonDisplays,clientFile],...(denseClientFile?[[server.denseButtonDisplays,denseClientFile]]:[])]){
const samples=fs.readFileSync(file,'utf8').trim().split(/\r?\n/).map(JSON.parse);assert(samples.length>=3);
let stable;
for(const sample of samples){
 const actual=[];
 for(const button of expected){
  const found=sample.textDisplays.filter(display=>display.text===button.text&&display.position.every((v,i)=>near(v,position(button)[i])));assert.equal(found.length,1,'Missing/duplicate actual TextDisplay for '+button.text);
  const display=found[0],scale=Array.isArray(button.scale)?button.scale[0]:button.scale;assert(display.scale.every(v=>near(v,scale)));assert(sameRotation(display.leftRotation,button.leftRotation??button.rotation));actual.push(display);
 }
 for(let i=1;i<actual.length;i++){
  const first=actual[i-1],next=actual[i];assert(near(first.scale[0],next.scale[0]));const pixel=.025*first.scale[0];
  const distance=Math.hypot(...first.position.map((v,axis)=>v-next.position[axis]));const gap=distance-(width(first.text)+width(next.text)+2)*pixel/2;
  assert(near(gap/pixel,27),'Actual text backgrounds are not separated by three fullwidth glyph advances');
 }
 const current=actual.map(display=>[display.id,display.text,display.position,display.scale,display.leftRotation]);if(stable)assert.deepEqual(current,stable,'Actual button displays changed across samples');else stable=current;
}
}
const labels=expected.map(display=>display.text);for(const dragon of ['中','发','白'])assert(labels.some(label=>label.includes(dragon)),'Actual fixture is missing dragon name '+dragon);assert(labels.every(label=>!label.includes('元')),'Numeric dragon names remain');
const expectedSounds={normal:1,riichi:1,bot:1,invalid:0,duplicate:1,claim:0,final:1};
const index=JSON.parse(fs.readFileSync(soundIndexFile,'utf8'));assert.equal(index.length,7);const soundResults=[];
for(const [name,count] of Object.entries(expectedSounds)){
 const capture=index.find(entry=>entry.name===name);assert(capture,'Missing actual sound capture for '+name);const raw=fs.readFileSync(capture.file,'utf8').trim();const packets=raw?raw.split(/\r?\n/).map(JSON.parse):[];
 const stone=packets.filter(packet=>packet.id==='minecraft:block.stone.place');assert.equal(stone.length,count,`Case ${name} emitted the wrong number of discard sounds`);
 const timing=server.soundCases.find(entry=>(entry.name??entry.case)===name);assert(timing&&timing.passed!==false,'Missing or failed server sound case '+name);
 for(const packet of stone){assert.equal(packet.source.toLowerCase(),'ambient');assert(near(packet.volume,1));assert(near(packet.pitch,2));assert(packet.position.every((v,i)=>near(v,[.5,-59.5,.5][i])));assert(packet.epochMillis>=(timing.startEpochMillis??timing.startedAtMillis)-100&&packet.epochMillis<=(timing.endEpochMillis??timing.endedAtMillis)+500,'Sound arrived outside its actual server action window');}
 soundResults.push({name,passed:true,expectedCount:count,actualCount:stone.length,file:capture.file,packets:stone});
}
console.log(JSON.stringify({kind:'interaction-sound',passed:true,version:server.coreVersion,clientVisualTest:true,textCapture:clientFile,denseTextCapture:denseClientFile,samples:samples.length,buttonDisplays:expected,dragonLabels:['中','发','白'],checks:['actual-client-text-labels-and-transforms','actual-three-glyph-button-spacing','stable-text-displays','actual-client-sound-packets','one-sound-per-successful-discard','quiet-on-invalid-click-and-claim'],soundResults,serverChecks:server.checks},null,2));
