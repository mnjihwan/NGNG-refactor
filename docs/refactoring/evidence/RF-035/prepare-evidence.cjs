// Copies already completed execution artifacts. Does not run tests, access DBs, or replace raw history.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const workspace = path.resolve(__dirname, '../../../..');
const rawRoot = path.join(workspace, '.local-runtime/rf035-results');
const sharedRoot = path.join(__dirname, 'implementation');
const sha256 = buffer => crypto.createHash('sha256').update(buffer).digest('hex');
const manifest = [];
let redactedTokens = 0;
function copy(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const source = path.join(directory, entry.name);
    if (entry.isDirectory()) { copy(source); continue; }
    if (!/\.(xml|txt|json|java|cjs|ts|tsx)$/.test(source)) continue;
    const relative = path.relative(rawRoot, source);
    const target = path.join(sharedRoot, relative);
    const raw = fs.readFileSync(source);
    let shared = raw.toString('utf8');
    if (!/\.(java|cjs|ts|tsx)$/.test(source)) {
      shared = shared.replaceAll(workspace.replaceAll('\\', '\\\\'), '[workspace]')
        .replaceAll(workspace, '[workspace]')
        .replaceAll(workspace.replaceAll('\\', '/'), '[workspace]')
        .replace(/hostname="[^"]*"/g, 'hostname="redacted"')
        .replace(/file:\/\/\/C:\/Users\/[^\r\n<]*/gi, 'file:///[workspace]')
        .replace(/C:\\\\Users\\\\[^\\\r\n]+/gi, '[user-home]')
        .replace(/C:\\Users\\[^\\\r\n]+/gi, '[user-home]')
        .replace(/C:\/Users\/[^/\r\n]+/gi, '[user-home]')
        .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, () => {
          redactedTokens++; return '[synthetic-jwt-redacted]';
        })
        .replace(/[\t ]+(?=\r?$)/gm, '');
    }
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, shared);
    manifest.push({ file: relative.replaceAll('\\', '/'), rawSha256: sha256(raw), sharedSha256: sha256(Buffer.from(shared)) });
  }
}
copy(rawRoot);
fs.writeFileSync(path.join(__dirname, 'redaction-manifest.json'), JSON.stringify({ redactedTokens, files: manifest }, null, 2));
console.log(JSON.stringify({ files: manifest.length, redactedTokens }));
