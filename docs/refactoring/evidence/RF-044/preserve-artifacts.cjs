// Run from repository root. Preserves the first captured artifacts, then sanitizes
// shared logs. Source snapshots retain synthetic fixture/key literals deliberately.
// This neither recreates a missing original nor runs any tests.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const root=process.cwd(), evidence=path.join(root,'docs/refactoring/evidence/RF-044');
const originals=path.join(root,'.local-runtime/refactoring/RF-044/20261111-originals');
const excluded=new Set(['README.md','ARTIFACT_SHA256.tsv','artifact-check.json','preserve-artifacts.cjs']);
const home=os.homedir(),variants=[home.replaceAll('\\','\\\\'),home,home.replaceAll('\\','/')];
const jwt=/eyJ[A-Za-z0-9_-]*\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g;
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(item=>item.isDirectory()?walk(path.join(dir,item.name)):[path.join(dir,item.name)]);}
const manifest=[];
for(const file of walk(evidence).filter(file=>!excluded.has(path.basename(file))).sort()){
  const relative=path.relative(evidence,file).replaceAll('\\','/'),original=path.join(originals,relative);
  fs.mkdirSync(path.dirname(original),{recursive:true});if(!fs.existsSync(original))fs.copyFileSync(file,original);
  const raw=fs.readFileSync(original);let text=raw.toString('utf8'),transformations=[];
  if(!relative.includes('/source/')){
    for(const value of variants)if(text.includes(value)){text=text.replaceAll(value,'REDACTED_USER_HOME');transformations.push('home');}
    if(/hostname="[^"]*"/.test(text)){text=text.replace(/hostname="[^"]*"/g,'hostname="REDACTED_HOST"');transformations.push('hostname');}
    if(new RegExp(jwt).test(text)){text=text.replace(jwt,'REDACTED_COMPACT_JWT');transformations.push('compact-jwt');}
    if(/synthetic-sensitive-[a-z]+/.test(text)){text=text.replace(/synthetic-sensitive-[a-z]+/g,'SYNTHETIC_REDACTED');transformations.push('synthetic-sensitive-literal');}
  }
  const shared=transformations.length?Buffer.from(text):raw;fs.writeFileSync(file,shared);
  let provenance='captured-artifact';
  if(/^before\/(create|list|detail|patch)\.json$/.test(relative))provenance='payload-redacted-at-capture; unredacted-memory-tree-not-written';
  if(relative==='concurrent-first-draft-compile-failure.txt')provenance='copy-of-RF039-shared-compile-log; not-independent-original';
  manifest.push({path:relative,original_sha256:hash(raw),shared_sha256:hash(shared),transformations:[...new Set(transformations)].sort().join(',')||'none',
    preserved:path.relative(root,original).replaceAll('\\','/'),provenance});
}
const fields=Object.keys(manifest[0]);fs.writeFileSync(path.join(evidence,'ARTIFACT_SHA256.tsv'),[fields.join('\t'),...manifest.map(row=>fields.map(key=>row[key]).join('\t'))].join('\n')+'\n');
const checks={artifact_count:manifest.length,hash_mismatches:0,unredacted_home_files:0,unredacted_jwt_files:0,unredacted_hostname_files:0,source_snapshot_mismatches:0,
  xml_strict_parse:'performed separately with PowerShell [xml]; this script only reads attributes',before_tests:null,after_tests:[]};
for(const row of manifest){
  const bytes=fs.readFileSync(path.join(evidence,row.path)),text=bytes.toString('utf8');
  checks.hash_mismatches+=hash(bytes)!==row.shared_sha256;
  checks.hash_mismatches+=hash(fs.readFileSync(path.join(root,row.preserved)))!==row.original_sha256;
  if(!row.path.includes('/source/')){
    checks.unredacted_home_files+=variants.some(value=>text.includes(value));checks.unredacted_jwt_files+=new RegExp(jwt).test(text);
    checks.unredacted_hostname_files+=/hostname="(?!REDACTED_HOST)[^"]+"/.test(text);
  }
  if(row.path.endsWith('.xml')){
    const header=text.match(/<testsuite\s+([^>]+)>/)[1],result={};
    for(const key of ['name','tests','failures','errors','skipped'])result[key]=header.match(new RegExp(`${key}="([^"]*)"`))[1];
    if(row.path==='before/test.xml')checks.before_tests=result;else checks.after_tests.push(result);
  }
  if(row.path.startsWith('after/source/'))checks.source_snapshot_mismatches+=!bytes.equals(fs.readFileSync(path.join(root,row.path.slice('after/source/'.length))));
}
fs.writeFileSync(path.join(evidence,'artifact-check.json'),JSON.stringify(checks,null,2)+'\n');
console.log(JSON.stringify(Object.fromEntries(Object.entries(checks).filter(([key])=>!['before_tests','after_tests'].includes(key)))));
