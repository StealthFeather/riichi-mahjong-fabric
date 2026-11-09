// Extract compile dependencies from an existing server mod without modifying it.
const fs = require('node:fs');
const p = require('node:path');
const { execFileSync: exec } = require('node:child_process');
const { build, files, javaTool } = require('./build-config.cjs');
const source = process.argv[2];
if (!source) throw Error('Usage: node prepare-local-deps.cjs <existing-riichi-mahjong.jar> [output-directory]');
const output = p.resolve(process.argv[3] || p.join(build, 'deps/polymer'));
const parent = p.join(build, 'deps');
fs.mkdirSync(parent, { recursive: true });
fs.mkdirSync(output, { recursive: true });
const staging = fs.mkdtempSync(p.join(parent, 'polymer-extract-'));
const queue = [];
const extracted = new Set();
function dependency(file, name = p.basename(file)) {
  const destination = p.join(output, name);
  if (extracted.has(name)) {
    if (!fs.readFileSync(destination).equals(fs.readFileSync(file))) throw Error('Conflicting nested dependency: ' + name);
    return;
  }
  fs.copyFileSync(file, destination);
  extracted.add(name);
  queue.push(destination);
}
try {
  exec(javaTool('jar'), ['--extract', '--file', p.resolve(source), 'META-INF/jars/polymer-bundled.jar'], { cwd: staging, windowsHide: true });
  dependency(p.join(staging, 'META-INF/jars/polymer-bundled.jar'), 'polymer-bundled-0.18.7+26.3.jar');
  for (let index = 0; index < queue.length; index++) {
    const nested = p.join(staging, String(index));
    fs.mkdirSync(nested);
    exec(javaTool('jar'), ['--extract', '--file', queue[index], 'META-INF/jars'], { cwd: nested, windowsHide: true });
    for (const file of files(p.join(nested, 'META-INF/jars'), '.jar')) dependency(file);
  }
  console.log(JSON.stringify({ runtime: output, jars: [...extracted].sort() }, null, 2));
} finally {
  if (p.dirname(staging) !== parent || !p.basename(staging).startsWith('polymer-extract-')) throw Error('Unexpected temporary extraction path');
  fs.rmSync(staging, { recursive: true, force: true });
}
