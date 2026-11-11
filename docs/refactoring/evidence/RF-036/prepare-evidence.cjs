const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const workspace = path.resolve(__dirname, '../../../..');
const rawRoot = path.join(workspace, '.local-runtime/rf036-results');
const sharedRoot = path.join(__dirname, 'implementation');
const sourceFiles = [
  'NGNG-backend/src/main/java/com/ngng/api/user/point/payment/PaymentVerificationGate.java',
  'NGNG-backend/src/main/java/com/ngng/api/user/point/payment/PaymentVerificationConfiguration.java',
  'NGNG-backend/src/test/java/com/ngng/api/user/point/payment/PaymentVerificationGateTest.java',
  'NGNG-backend/src/test/java/com/ngng/api/user/point/payment/PaymentVerificationConfigurationTest.java',
];
const digest = data => crypto.createHash('sha256').update(data).digest('hex');
const relative = file => path.relative(workspace, file).split(path.sep).join('/');
const clean = value => value.split(workspace).join('[WORKSPACE]')
  .replace(/C:\\Users\\[^\\\r\n<>" ]+/gi, 'C:\\Users\\[USER]');
const preserved = [];
function copy(file) {
  const localName = path.relative(rawRoot, file);
  const destination = path.join(sharedRoot, localName);
  const raw = fs.readFileSync(file);
  const shared = Buffer.from(clean(raw.toString('utf8')));
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.writeFileSync(destination, shared);
  preserved.push({ raw: relative(file), shared: relative(destination), rawSha256: digest(raw), sharedSha256: digest(shared) });
}
function walk(directory) {
  for (const item of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, item.name);
    if (item.isDirectory()) walk(file);
    else copy(file);
  }
}
const executedSourceHashes = [];
const commentReview = [];
const oldComment = '    /** Reads once, without following a symlink; failures expose neither paths nor approval evidence. */';
const newComment = [
  '    /**',
  '     * Reads once and rejects a symbolic link at the final file component. Parent directory',
  '     * symlinks/junctions, path replacement and permissions require trusted deployment controls.',
  '     * Failures expose neither paths nor approval evidence.',
  '     */',
].join('\n');
fs.mkdirSync(path.join(rawRoot, 'reviewed-source'), { recursive: true });
for (const source of sourceFiles) {
  // Never reconstruct or overwrite the source snapshot captured at test execution.
  const executed = path.join(rawRoot, 'source', path.basename(source));
  const original = fs.readFileSync(executed);
  const current = fs.readFileSync(path.join(workspace, source));
  const unchanged = original.equals(current);
  const commentOnly = source.endsWith('/PaymentVerificationGate.java')
    && original.toString('utf8').replace(oldComment, newComment) === current.toString('utf8');
  if (!unchanged && !commentOnly) throw new Error('Unexpected source change outside reviewed comment: ' + source);
  executedSourceHashes.push({ file: source, snapshot: relative(executed), sha256: digest(original) });
  commentReview.push({ file: source, executedSha256: digest(original), currentSha256: digest(current),
    unchanged, exactJavadocReplacementOnly: !unchanged && commentOnly });
  fs.copyFileSync(path.join(workspace, source), path.join(rawRoot, 'reviewed-source', path.basename(source)));
}
fs.writeFileSync(path.join(rawRoot, 'review-comment-only-check.json'), JSON.stringify({
  basis: 'Byte-for-byte equality, except the single explicitly reviewed fromFile Javadoc replacement.',
  testsRerun: false, files: commentReview,
}, null, 2) + '\n');
walk(rawRoot);
const sourceHashes = sourceFiles.map(file => ({ file, sha256: digest(fs.readFileSync(path.join(workspace, file))) }));
const javaRelease = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'release') : null;
const javaVersion = javaRelease && fs.existsSync(javaRelease)
  ? fs.readFileSync(javaRelease, 'utf8').split(/\r?\n/).filter(line => /^(JAVA_VERSION|IMPLEMENTOR|OS_ARCH)=/.test(line))
  : ['JAVA_HOME release metadata unavailable'];
fs.writeFileSync(path.join(__dirname, 'verification.json'), JSON.stringify({
  recordedAt: new Date().toISOString(),
  testExecutionSource: 'executedSourceHashes and preserved source/; comment correction was not retested',
  executedSourceHashes, sourceHashes, preserved,
  environment: { node: process.version, platform: process.platform, javaVersion },
  finalTests: { gate: 163, configuration: 15, failures: 0, errors: 0, skipped: 0 },
  scope: 'Gate/config unit tests and offline compile/assemble only; no DB, HTTP, deployment, performance or operational approval.'
}, null, 2) + '\n');
console.log(JSON.stringify({ preserved: preserved.length, sources: sourceHashes.length }));
