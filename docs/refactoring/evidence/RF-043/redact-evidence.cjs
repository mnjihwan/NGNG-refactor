// Rebuild shared evidence from the originals archived under ignored raw/.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = __dirname;
const raw = path.join(root, 'raw');
const walk = dir => fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
  if (entry.name === 'raw') return [];
  const file = path.join(dir, entry.name);
  return entry.isDirectory() ? walk(file) : [file];
});
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const relative = file => path.relative(root, file).split(path.sep).join('/');
const escape = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const files = walk(root).filter(file => /\.(xml|txt)$/.test(file));
const hosts = new Set();
const startupAccounts = new Set();
const startupAccount = /(\bwith PID \d+ \(started by )([^\r\n]+?)( in )/g;
const accountMask = '[OS_USER]';
for (const file of files) {
  const original = fs.readFileSync(path.join(raw, relative(file)), 'utf8');
  for (const match of original.matchAll(/\bhostname="([^"]*)"/g)) if (match[1]) hosts.add(match[1]);
  for (const match of original.matchAll(startupAccount)) startupAccounts.add(match[2]);
}
const home = process.env.USERPROFILE;
if (!home) throw new Error('USERPROFILE is required; no evidence was modified');
const forward = home.replaceAll('\\', '/');
const homeVariants = [...new Set([home, forward, home.replaceAll('\\', '\\\\'),
  encodeURIComponent(home), encodeURIComponent(forward), forward.replace(':', '%3A')])].sort((a,b)=>b.length-a.length);
const pairs = [];
const sharedOutputs = new Map();
let startupAccountsRedacted = 0;
for (const file of files) {
  const original = fs.readFileSync(path.join(raw, relative(file)));
  let shared = original.toString('utf8');
  for (const value of homeVariants) shared = shared.replace(new RegExp(escape(value), 'gi'), '[USER_HOME]');
  for (const host of hosts) shared = shared.replace(new RegExp(escape(host), 'gi'), '[HOST]');
  shared = shared.replace(startupAccount, (_, prefix, account, suffix) => {
    startupAccountsRedacted++;
    return prefix + accountMask + suffix;
  });
  const output = Buffer.from(shared, 'utf8');
  sharedOutputs.set(file, output);
  pairs.push({ shared: relative(file), raw: `raw/${relative(file)}`,
    originalSha256: hash(original), sharedSha256: hash(output),
    originalBytes: original.length, sharedBytes: output.length, changed: !output.equals(original) });
}
const findings = [];
const sharedFiles = [...walk(root), path.resolve(root, '../../tasks/RF-043.md')];
const textFiles = sharedFiles.filter(file => /\.(xml|txt|json|md|java|tsx?|cjs|ps1|gradle|gitignore)$/.test(file));
const literalAccounts = [...startupAccounts].map(account => new RegExp(`(?<![\\w.-])${escape(account)}(?![\\w.-])`, 'gi'));
const contactCandidates = { email: 0, koreanMobile: 0, ipv4: 0 };
for (const file of textFiles) {
  const text = (sharedOutputs.get(file) ?? fs.readFileSync(file)).toString('utf8');
  const homes = (text.match(/[A-Za-z]:(?:[\\/]+|%5[cC]|%2[fF])Users(?:[\\/]+|%5[cC]|%2[fF])[A-Za-z0-9_.-]+/g) || []).length
    + (text.match(/\/(?:home|Users)\/[^/\s"'`<>]+/g) || []).length
    + homeVariants.filter(value => text.toLowerCase().includes(value.toLowerCase())).length;
  const jwt = (text.match(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g) || []).length;
  const actualHosts = [...hosts].filter(host => text.toLowerCase().includes(host.toLowerCase())).length;
  const accounts = literalAccounts.reduce((count, pattern) => count + [...text.matchAll(pattern)].length, 0);
  const rawStartupAccounts = /\.(xml|txt)$/.test(file)
    ? [...text.matchAll(startupAccount)].filter(match => match[2] !== accountMask).length : 0;
  const emails = [...text.matchAll(/[A-Za-z0-9_.+%!-]+@((?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,63})\b/g)];
  const nonReservedEmails = emails.filter(match => !/\.(?:example|invalid|test)$/i.test(match[1])).length;
  const urlUserinfo = (text.match(/\b(?:https?|smtps?|rediss?):\/\/[^/\s"'<>]*@/gi) || []).length;
  contactCandidates.email += emails.length;
  contactCandidates.koreanMobile += (text.match(/(?<!\d)(?:\+?82[-. ]?0?1[016789]|01[016789])[-. ]?\d{3,4}[-. ]?\d{4}(?!\d)/g) || []).length;
  contactCandidates.ipv4 += (text.match(/(?<![.\d])(?:\d{1,3}\.){3}\d{1,3}(?![.\d])/g) || []).length;
  if (homes || jwt || actualHosts || accounts || rawStartupAccounts || nonReservedEmails || urlUserinfo) {
    findings.push({file:relative(file),homeCandidates:homes,jwtCandidates:jwt,actualHostCandidates:actualHosts,
      actualStartupAccountCandidates:accounts,unmaskedStartupAccountCandidates:rawStartupAccounts,
      nonReservedEmailCandidates:nonReservedEmails,urlUserinfoCandidates:urlUserinfo});
  }
}
if (findings.length) throw new Error(`Redaction stopped before writing: ${findings.length} files have unmasked candidates`);
for (const [file, output] of sharedOutputs) {
  if (!output.equals(fs.readFileSync(file))) fs.writeFileSync(file, output);
}
fs.writeFileSync(path.join(root, 'redaction-pairs.json'), JSON.stringify(pairs, null, 2) + '\n');
fs.writeFileSync(path.join(root, 'redaction-scan.json'), JSON.stringify({
  scope:'Shared RF043 evidence and task specification; ignored raw/ excluded. Values are never printed.',
  reviewedRawFiles:files.length, changedFiles:pairs.filter(p=>p.changed).length,
  scannedSharedTextFiles:textFiles.length,
  replacementCategories:['USER_HOME, native/slash/escaped/URI variants','actual XML hostname','OS account in Spring Boot startup log'],
  startupAccountsRedacted,
  contactCandidates,
  findings,
},null,2)+'\n');
process.stdout.write(JSON.stringify({files:pairs.length,changed:pairs.filter(p=>p.changed).length,findings:findings.length})+'\n');
