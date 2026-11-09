// Copy upstream resources byte-for-byte; add vanilla display wrappers separately.
const fs=require('node:fs'),p=require('node:path');
const project=__dirname,root=p.resolve(project,'..'),source=p.join(project,'upstream/neoforge/src/main/resources'),resources=p.join(project,'src/main/resources'),pack=p.join(root,'TimoryResource');
fs.mkdirSync(resources,{recursive:true});
fs.cpSync(p.join(source,'data'),p.join(resources,'data'),{recursive:true});
fs.cpSync(p.join(source,'assets/riichi_mahjong'),p.join(pack,'assets/riichi_mahjong'),{recursive:true});
// 26.3 ClientAsset.ResourceTexture adds textures/ and .png itself.
const advancementRoot=p.join(resources,'data/riichi_mahjong/advancement/yaku/root.json');
const advancement=JSON.parse(fs.readFileSync(advancementRoot));
advancement.display.background='minecraft:gui/advancements/backgrounds/adventure';
fs.writeFileSync(advancementRoot,JSON.stringify(advancement,null,2)+'\n');
// Keep port translations separate from the pinned, unmodified upstream snapshot.
fs.cpSync(p.join(project,'resource-overrides/assets'),p.join(pack,'assets'),{recursive:true});
// Preserve other mods' atlas sources and append the upstream entries.
for(const name of fs.readdirSync(p.join(source,'assets/minecraft/atlases'))){
 const dest=p.join(pack,'assets/minecraft/atlases',name),original=JSON.parse(fs.readFileSync(p.join(source,'assets/minecraft/atlases',name)));
 const current=fs.existsSync(dest)?JSON.parse(fs.readFileSync(dest)): {sources:[]};
 current.sources=[...new Map([...current.sources,...original.sources].map(x=>[JSON.stringify(x),x])).values()];fs.mkdirSync(p.dirname(dest),{recursive:true});fs.writeFileSync(dest,JSON.stringify(current));
}
function json(file,obj){fs.mkdirSync(p.dirname(file),{recursive:true});fs.writeFileSync(file,JSON.stringify(obj));}
const ns=p.join(pack,'assets/riichi_mahjong');
for(const file of fs.readdirSync(p.join(ns,'models/block')).filter(n=>n.endsWith('.json'))){const id=file.slice(0,-5);json(p.join(ns,'items/bridge',file),{model:{type:'minecraft:model',model:'riichi_mahjong:block/'+id}});}
for(const file of fs.readdirSync(p.join(ns,'blockstates')).filter(n=>n.endsWith('.json'))){
 const blockstate=JSON.parse(fs.readFileSync(p.join(ns,'blockstates',file))),variant=blockstate.multipart?.[0]?.apply||Object.values(blockstate.variants||{})[0],apply=Array.isArray(variant)?variant[0]:variant;
 if(apply?.model)json(p.join(ns,'items/bridge',file),{model:{type:'minecraft:model',model:apply.model}});
}
// A genuine blank face with the original shell. No secret tile is baked into any concealed model.
json(p.join(ns,'models/bridge/tile_back.json'),{parent:'riichi_mahjong:block/tile',textures:{'1':'riichi_mahjong:block/tile/tile_dragon_white'}});
json(p.join(ns,'items/bridge/tile_back.json'),{model:{type:'minecraft:model',model:'riichi_mahjong:bridge/tile_back'}});
// The jar keeps a matching copy so standard Polymer pack generation also works.
fs.cpSync(p.join(pack,'assets/riichi_mahjong'),p.join(resources,'assets/riichi_mahjong'),{recursive:true});
json(p.join(resources,'fabric.mod.json'),{schemaVersion:1,id:'riichi_mahjong',version:'0.4.2+fabric.26.3.8',name:'Riichi Mahjong — Fabric server port',description:'Server-only port of caxapexac/MinecraftRiichiMahjong. Upstream logic, models and textures with private vanilla-client displays.',authors:['caxapexac (upstream)','Timory (Fabric adaptation)'],environment:'server',mixins:['riichi-mahjong.mixins.json'],entrypoints:{main:['com.riichimahjong.RiichiMahjong']},license:'LGPL-2.1-only',depends:{fabricloader:'>=0.19.0','fabric-api':'*',minecraft:'26.3',java:'>=25'},jars:[{file:'META-INF/jars/polymer-bundled.jar'}]});
console.log('Upstream assets and Fabric metadata prepared.');
