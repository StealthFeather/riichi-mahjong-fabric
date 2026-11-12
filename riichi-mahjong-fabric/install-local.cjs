// Install only this port and its merged resource pack after a graceful shutdown.
const fs=require('node:fs'),p=require('node:path'),crypto=require('node:crypto');
const {root,release,jarName:jar}=require('./build-config.cjs');
const server=p.join(root,'run/local-26.3'),manager=p.join(root,'run/single-server');
if(JSON.parse(fs.readFileSync(p.join(manager,'status.json'))).state!=='stopped')throw Error('Stop the existing launcher gracefully before installing.');
const hash=(f,algorithm='sha256')=>crypto.createHash(algorithm).update(fs.readFileSync(f)).digest('hex');
const manifest=JSON.parse(fs.readFileSync(p.join(release,'release.json'))),pack='TimoryResource-26.3.zip';
const sums=new Map(fs.readFileSync(p.join(release,'SHA256SUMS.txt'),'utf8').trim().split(/\r?\n/).map(s=>s.split('  ').reverse()));
for(const name of [jar,pack])if(hash(p.join(release,name))!==sums.get(name))throw Error('Packaged artifact changed: '+name);
const backup=p.join(manager,'riichi-upstream-backup-'+Date.now()),mods=p.join(server,'mods');fs.mkdirSync(backup,{recursive:true});
const oldMods=fs.readdirSync(mods).filter(n=>/^(timory-mahjong-|riichi-mahjong-).*\.jar$/.test(n));
for(const relative of [...oldMods.map(n=>'mods/'+n),'server.properties','resource-pack/'+pack,'world/level.dat','world/level.dat_old','world/data/timory-mahjong-tables.json']){
 const source=p.join(server,relative);if(!fs.existsSync(source))continue;const dest=p.join(backup,relative);fs.mkdirSync(p.dirname(dest),{recursive:true});fs.copyFileSync(source,dest);
}
for(const name of oldMods)fs.renameSync(p.join(mods,name),p.join(backup,'previous-'+name));
fs.copyFileSync(p.join(release,jar),p.join(mods,jar));fs.copyFileSync(p.join(release,pack),p.join(server,'resource-pack',pack));
const properties=p.join(server,'server.properties');let text=fs.readFileSync(properties,'utf8');text=text.replace(/^resource-pack-sha1=.*$/m,'resource-pack-sha1='+manifest.resourcePackSha1);fs.writeFileSync(properties,text);
const receipt={mod:jar,resourcePack:pack,sha1:manifest.resourcePackSha1,jarSha256:hash(p.join(mods,jar)),backup,server:'127.0.0.1:25565',acceptance:'user',automatedTestsRunForThisBuild:!!manifest.checks.rulesTests,reference:manifest.upstream,referenceCommit:manifest.commit,clientModRequired:false};
fs.writeFileSync(p.join(manager,'mahjong-installation.json'),JSON.stringify(receipt,null,2)+'\n');console.log(JSON.stringify(receipt,null,2));
