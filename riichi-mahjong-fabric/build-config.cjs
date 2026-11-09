const fs = require('node:fs');
const p = require('node:path');

const project = __dirname;
const root = p.resolve(project, '..');
const metadata = JSON.parse(fs.readFileSync(p.join(project, 'src/main/resources/fabric.mod.json'), 'utf8'));
const files = (directory, extension) => fs.existsSync(directory)
  ? fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => entry.isDirectory()
    ? files(p.join(directory, entry.name), extension)
    : entry.name.endsWith(extension) ? [p.join(directory, entry.name)] : [])
  : [];
const configuredPath = (name, fallback) => p.resolve(process.env[name] || fallback);
// Avoid fs.cpSync's native Windows failure on some Node builds with Unicode paths.
const copyTree = (source, destination) => {
  if (fs.statSync(source).isDirectory()) {
    fs.mkdirSync(destination, { recursive: true });
    for (const name of fs.readdirSync(source)) copyTree(p.join(source, name), p.join(destination, name));
  } else {
    fs.mkdirSync(p.dirname(destination), { recursive: true });
    fs.copyFileSync(source, destination);
  }
};
const javaHome = configuredPath('RIICHI_JAVA_HOME', process.env.JAVA_HOME || p.join(root, 'build/tools/jdk-25.0.4.1+1'));
const javaTool = name => p.join(javaHome, 'bin', name + (process.platform === 'win32' ? '.exe' : ''));
const server = configuredPath('RIICHI_MC_SERVER', p.join(root, 'run/crop-26.3'));
const polymerRuntime = configuredPath('RIICHI_POLYMER_RUNTIME', p.join(root, 'build/kaleidoscope/runtime'));
const polymerJar = configuredPath('RIICHI_POLYMER_JAR', p.join(polymerRuntime, 'polymer-bundled-0.18.7+26.3.jar'));
const polymerClasspathFile = configuredPath('RIICHI_POLYMER_CLASSPATH_FILE', p.join(root, 'build/kaleidoscope/classpath.json'));
const polymerClasspath = () => process.env.RIICHI_POLYMER_RUNTIME && !process.env.RIICHI_POLYMER_CLASSPATH_FILE
  ? files(polymerRuntime, '.jar')
  : JSON.parse(fs.readFileSync(polymerClasspathFile, 'utf8')).filter(file => file.includes('polymer-'));
const build = p.join(project, 'build');
const version = metadata.version;
const jarName = `riichi-mahjong-${version}.jar`;
const resourceRoot = configuredPath('RIICHI_RESOURCE_ROOT', p.join(root, 'TimoryResource'));

module.exports = {
  project, root, metadata, version, files, copyTree, javaTool, server, polymerJar, polymerClasspath,
  build, jarName, jarPath: p.join(build, 'libs', jarName), resourceRoot,
  release: configuredPath('RIICHI_RELEASE_DIR', p.join(root, 'deploy', `RiichiMahjong-${version}`)),
  baselinePack: configuredPath('RIICHI_RESOURCE_PACK_BASELINE', p.join(root, 'deploy/TimoryGameplay-2.7.18+26.3/TimoryResource-26.3.zip')),
  changelog: configuredPath('RIICHI_CHANGELOG', p.join(project, 'docs', `${version.split('+fabric.')[1]}.md`))
};
