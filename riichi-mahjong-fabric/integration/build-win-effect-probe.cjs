const fs=require('node:fs'),p=require('node:path'),{execFileSync:exec}=require('node:child_process');
const {javaTool,build}=require('../build-config.cjs');
const out=p.join(build,'win-effect-probe'),classes=p.join(out,'classes');
fs.rmSync(classes,{recursive:true,force:true});fs.mkdirSync(classes,{recursive:true});
const cp=[p.join(build,'classes'),fs.readFileSync(p.join(build,'classpath.txt'),'utf8')].join(p.delimiter);
const source=p.join(__dirname,'src/com/riichimahjong/fabric/WinEffectBootstrapProbe.java');
const args=['--release','25','-encoding','UTF-8','-proc:none','-cp',cp,'-d',classes,source];
const argfile=p.join(out,'javac.args');
fs.writeFileSync(argfile,args.map(s=>'"'+s.replaceAll('\\','/')+'"').join('\n'));
exec(javaTool('javac'),['@'+argfile],{stdio:'inherit',windowsHide:true});
const metadata={schemaVersion:1,id:'riichi_win_effect_probe',version:'1.0.0',
  name:'Riichi win effect bootstrap probe (test only)',environment:'server',
  entrypoints:{main:['com.riichimahjong.fabric.WinEffectBootstrapProbe']},
  depends:{fabricloader:'>=0.19.0','fabric-api':'*',minecraft:'26.3',java:'>=25',riichi_mahjong:'*'}};
fs.writeFileSync(p.join(classes,'fabric.mod.json'),JSON.stringify(metadata,null,2)+'\n');
const jar=p.join(out,'riichi-win-effect-probe-test-only.jar');
exec(javaTool('jar'),['--create','--file',jar,'-C',classes,'.'],{stdio:'inherit',windowsHide:true});
console.log('TEST ONLY; no server/world is started; never install on production: '+jar);
