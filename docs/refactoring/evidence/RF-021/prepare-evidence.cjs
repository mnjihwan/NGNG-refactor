// Run once from repository root after preserving each execution in its own directory.
// Originals remain in the ignored .local-runtime directory; shared logs redact JWTs/paths/hostnames.
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const root = process.cwd();
const evidence = path.join(root, "docs/refactoring/evidence/RF-021");
const rawRoot = path.join(root, ".local-runtime/rf021-raw");
const manifest = [];
const hash = data => crypto.createHash("sha256").update(data).digest("hex");
const escapeRegex = value => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
function visit(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== "source-final") visit(file);
      continue;
    }
    if (!/\.(xml|txt)$/.test(entry.name)) continue;
    const relative = path.relative(evidence, file);
    const raw = path.join(rawRoot, relative);
    fs.mkdirSync(path.dirname(raw), { recursive: true });
    // Preserve the first original. Re-running never replaces it with a redacted copy.
    if (!fs.existsSync(raw)) fs.copyFileSync(file, raw);
    const original = fs.readFileSync(raw);
    let tokens = 0;
    let text = original.toString("utf8").replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, () => {
      tokens++;
      return "<synthetic-jwt-redacted>";
    });
    text = text.replace(/hostname="[^"]*"/g, 'hostname="local-host-redacted"')
      .replace(new RegExp(escapeRegex(root), "g"), "<workspace>")
      .replace(new RegExp(escapeRegex(root.replaceAll("\\", "/")), "g"), "<workspace>")
      .replace(/C:[\\/]Users[\\/][^\\/\r\n]+/g, "<user-home>")
      .replace(/[ \t]+\r?$/gm, "");
    fs.writeFileSync(file, text);
    manifest.push({ file: relative.replaceAll("\\", "/"), rawSha256: hash(original),
      sharedSha256: hash(Buffer.from(text)), redactedSyntheticJwtCount: tokens });
  }
}
visit(evidence);
fs.writeFileSync(path.join(evidence, "redaction-manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
console.log(JSON.stringify({ files: manifest.length, syntheticTokens: manifest.reduce((n, x) => n + x.redactedSyntheticJwtCount, 0) }));
