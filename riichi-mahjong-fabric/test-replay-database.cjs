// Isolated SQL regression; never reads Quest credentials and never opens a network database.
const fs=require('node:fs'),p=require('node:path'),crypto=require('node:crypto'),{execFileSync:exec}=require('node:child_process');
const {project,javaTool,jarPath}=require('./build-config.cjs');
const report=p.join(project,'build/replay-database-verification.json');
const h2=p.resolve(process.argv[2]||p.join(project,'build/test-deps/h2-2.3.232.jar'));
const out=p.join(project,'build/replay-database-tests');
fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(report,JSON.stringify({passed:false,checks:0,isolatedDatabase:true,liveDatabaseTest:false},null,2)+'\n');
if(!fs.existsSync(h2))throw new Error('Provide a local H2 JDBC jar: node test-replay-database.cjs /absolute/path/to/h2.jar (no dependency is downloaded automatically).');
const classpathFile=p.join(project,'build/classpath.txt');
if(!fs.existsSync(classpathFile))throw new Error('Build the mod first so build/classpath.txt and build/classes exist.');
const runtime=[out,p.join(project,'build/classes'),fs.readFileSync(classpathFile,'utf8').trim(),h2].join(p.delimiter);
const source=p.join(project,'src/test/java/com/riichimahjong/replay/ReplayStoreRegression.java');
exec(javaTool('javac'),['--release','25','-encoding','UTF-8','-proc:none','-cp',runtime,'-d',out,
 p.join(project,'src/main/java/com/riichimahjong/replay/ReplayStore.java'),source],{stdio:'inherit',windowsHide:true});
const output=exec(javaTool('java'),['-cp',runtime,'com.riichimahjong.replay.ReplayStoreRegression',p.join(project,'build')],{encoding:'utf8',windowsHide:true});
process.stdout.write(output);
const checks=/ReplayStoreRegression: (\d+) checks passed/.exec(output);
const version=/H2 version: ([^\r\n]+)/.exec(output);
if(!checks||!version)throw new Error('Regression did not produce its completion/version evidence.');
fs.writeFileSync(report,JSON.stringify({passed:true,checks:Number(checks[1]),h2Version:version[1],h2JarSha256:crypto.createHash('sha256').update(fs.readFileSync(h2)).digest('hex'),
 jarSha256:crypto.createHash('sha256').update(fs.readFileSync(jarPath)).digest('hex'),isolatedDatabase:true,liveDatabaseTest:false,scenarios:['idempotent-schema','transaction-rollback','idempotent-match-UUID','participant-access-filter',
 'configured-timezone-first-query','ordered-pagination','analysis-cache-access-filter','durable-outbox-on-SQL-outage','restart-outbox-recovery',
 'missing-config-never-generated','unstarted-store-rejection','disk-write-failure-rejection','stopped-executor-rejection']},null,2)+'\n');
