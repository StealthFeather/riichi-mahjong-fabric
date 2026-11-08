const fs=require('node:fs'),p=require('node:path'),crypto=require('node:crypto'),{execFileSync:exec}=require('node:child_process');
const project=__dirname,root=p.resolve(project,'..'),build=p.join(project,'build'),java=p.join(root,'build/tools/jdk-25.0.4.1+1/bin');
const release=p.join(root,'deploy/RiichiMahjong-0.4.2+fabric.26.3.6'),pack=p.join(build,'merged-pack'),sources=p.join(build,'release-sources');
const fileList=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?fileList(p.join(d,e.name)):[p.join(d,e.name)]);
const hash=(file,alg='sha256')=>crypto.createHash(alg).update(fs.readFileSync(file)).digest('hex');
fs.mkdirSync(release,{recursive:true});fs.mkdirSync(pack,{recursive:true});
exec(p.join(java,'jar.exe'),['--extract','--file',p.join(root,'deploy/TimoryGameplay-2.7.18+26.3/TimoryResource-26.3.zip')],{cwd:pack,windowsHide:true});
// The baseline supplies all other gameplay assets; only the original Mahjong namespace is added.
fs.cpSync(p.join(root,'TimoryResource/assets/riichi_mahjong'),p.join(pack,'assets/riichi_mahjong'),{recursive:true});
const atlasRoot=p.join(root,'TimoryResource/assets/minecraft/atlases');
for(const file of fs.readdirSync(atlasRoot)){
 const dest=p.join(pack,'assets/minecraft/atlases',file),source=JSON.parse(fs.readFileSync(p.join(atlasRoot,file)));
 const current=fs.existsSync(dest)?JSON.parse(fs.readFileSync(dest)):{sources:[]};
 current.sources=[...new Map([...(current.sources||[]),...(source.sources||[])].map(x=>[JSON.stringify(x),x])).values()];
 fs.mkdirSync(p.dirname(dest),{recursive:true});fs.writeFileSync(dest,JSON.stringify(current));
}
fs.copyFileSync(p.join(root,'TimoryResource/pack.mcmeta'),p.join(pack,'pack.mcmeta'));
for(const file of ['LICENSE.txt'])fs.copyFileSync(p.join(project,'upstream',file),p.join(release,file));
fs.copyFileSync(p.join(project,'upstream/neoforge/src/main/resources/LICENSE_mahjong4j.txt'),p.join(release,'LICENSE_mahjong4j.txt'));
fs.copyFileSync(p.join(project,'PORTING.md'),p.join(release,'README.md'));
const jar='riichi-mahjong-0.4.2+fabric.26.3.6.jar';fs.copyFileSync(p.join(build,'libs',jar),p.join(release,jar));
exec(p.join(java,'jar.exe'),['--create','--no-manifest','--file',p.join(release,'TimoryResource-26.3.zip'),'-C',pack,'.'],{windowsHide:true});
if(p.relative(project,sources)!==p.join('build','release-sources'))throw Error('Unexpected source staging directory');
fs.rmSync(sources,{recursive:true,force:true});fs.mkdirSync(sources,{recursive:true});for(const name of ['src','upstream','resource-overrides','build.cjs','resources.cjs','package.cjs','install-local.cjs','PORTING.md','prepare.cjs','adapt.cjs'])fs.cpSync(p.join(project,name),p.join(sources,'riichi-mahjong-fabric',name),{recursive:true});
exec(p.join(java,'jar.exe'),['--create','--no-manifest','--file',p.join(release,'riichi-mahjong-fabric-sources.zip'),'-C',sources,'.'],{windowsHide:true});
const manifest={version:'0.4.2+fabric.26.3.6',upstream:'https://github.com/caxapexac/MinecraftRiichiMahjong',commit:'fe469836ed461d44998819dd3004c8dea01f5be0',minecraft:'26.3',loader:'Fabric',clientModRequired:false,resourcePack:'TimoryResource-26.3.zip',resourcePackSha1:hash(p.join(release,'TimoryResource-26.3.zip'),'sha1'),acceptance:'user',checks:{compiled:true,packaged:true,gameplayTests:false,protocolTests:false,visualTests:false,rulesTests:false},limitations:['原版客户端无法执行客户端 FOV 修改与渲染编辑器；交互采用显示实体、私有 interaction、私聊与原版容器','原版流体使用水投影，客户端占位碰撞无法完全等同原 Mod','能源兼容需服务端具有 Team Reborn Energy API']};
fs.writeFileSync(p.join(release,'release.json'),JSON.stringify(manifest,null,2)+'\n');
fs.writeFileSync(p.join(release,'SHA256SUMS.txt'),fileList(release).filter(f=>!f.endsWith('SHA256SUMS.txt')).map(f=>hash(f)+'  '+p.relative(release,f).replaceAll('\\','/')).join('\n')+'\n');
console.log(JSON.stringify({release,jar,packSha1:manifest.resourcePackSha1,packBytes:fs.statSync(p.join(release,'TimoryResource-26.3.zip')).size},null,2));
