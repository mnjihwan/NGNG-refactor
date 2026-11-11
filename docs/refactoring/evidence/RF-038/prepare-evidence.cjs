// Preserve selected run output as a redacted shareable copy. Does not execute tests.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const os = require('node:os');
const root = path.resolve(__dirname, '../../../..');
const rawRoot = path.join(root, '.local-runtime/rf038-results');
const sharedRoot = path.join(__dirname, 'implementation');
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const relative = file => path.relative(root,file).replaceAll('\\','/');
const text = bytes => bytes[0] === 255 && bytes[1] === 254 ? bytes.subarray(2).toString('utf16le') : bytes.toString('utf8').replace(/^\uFEFF/,'');
const manifest = [];
function clean(value) {
  for (const target of [root,root.replaceAll('\\','/'),os.homedir(),os.homedir().replaceAll('\\','/')]) {
    value = value.split(target).join(target.startsWith(root) ? '[workspace]' : '[user-home]');
  }
  return value.replace(/hostname="[^"]*"/g,'hostname="[redacted]"')
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[synthetic-jwt-redacted]')
    .replace(/[ \t]+\r?$/gm,'');
}
for (const phase of fs.readdirSync(rawRoot)) {
  const source = path.join(rawRoot,phase);
  if (!fs.statSync(source).isDirectory()) continue;
  const destination = path.join(sharedRoot,phase); fs.mkdirSync(destination,{recursive:true});
  for (const name of fs.readdirSync(source)) {
    const raw = path.join(source,name); if (!fs.statSync(raw).isFile()) continue;
    const bytes = fs.readFileSync(raw), shared = path.join(destination,name);
    fs.writeFileSync(shared,clean(text(bytes)));
    manifest.push({raw:relative(raw),shared:relative(shared),rawSha256:hash(bytes),sharedSha256:hash(fs.readFileSync(shared))});
  }
}
function walk(directory) {
  return fs.readdirSync(directory,{withFileTypes:true}).flatMap(entry => entry.isDirectory()
    ? walk(path.join(directory,entry.name)) : [path.join(directory,entry.name)]);
}
const snapshots = walk(sharedRoot).filter(file => file.endsWith('.java')).map(file => ({file:relative(file),sha256:hash(fs.readFileSync(file))}));
fs.writeFileSync(path.join(__dirname,'redaction-manifest.json'),JSON.stringify({rawOutputFiles:manifest,sourceSnapshots:snapshots},null,2)+'\n');
const residuals = walk(sharedRoot).filter(file => {
  const value = text(fs.readFileSync(file));
  return value.includes(root) || value.includes(os.homedir()) || /eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(value);
});
fs.writeFileSync(path.join(__dirname,'verification.json'),JSON.stringify({outputFiles:manifest.length,sourceSnapshots:snapshots.length,residualPersonalPathsOrJwt:residuals.map(relative),node:process.version},null,2)+'\n');
if (residuals.length) throw new Error('Unredacted data remains');
console.log(JSON.stringify({outputFiles:manifest.length,sourceSnapshots:snapshots.length,residuals:0}));
