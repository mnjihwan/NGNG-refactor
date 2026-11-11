// Actual TypeScript API/component handlers; fetch/React/navigation doubles, no browser/network.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const React = require("react");
function load(relative, deps, globals = {}) {
  const filename = path.resolve(__dirname, "..", relative);
  const code = ts.transpileModule(fs.readFileSync(filename, "utf8"), { fileName: filename,
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(code, { module, exports: module.exports, FormData, ...globals, require(name) {
    if (name === "react/jsx-runtime") return require(name);
    if (Object.hasOwn(deps, name)) return deps[name];
    throw new Error(`Unexpected module: ${name}`);
  } }, { filename }); return module.exports;
}
function find(node, predicate) {
  if (!node || typeof node !== "object") return undefined;
  if (predicate(node)) return node;
  return React.Children.toArray(node.props?.children).map(child => find(child, predicate)).find(Boolean);
}
const noop = () => null;
const product = () => ({ order: 1, id: 77, userId: 1000, title: "fixture", content: "text", tags: [],
  status: { id: 1 }, category: { id: 1 }, images: [{ id: -1, image: new Blob(["fixture"]), imageURL: new Blob(["fixture"]) }] });
function fixture(results) {
  const calls = [], tokens = [], alerts = [];
  const auth = { getAccessToken: () => "access", setAccessToken: res => tokens.push(res.status) };
  const globals = { alert: message => alerts.push(message), fetch: async (url, options) => {
    calls.push({ url, ...options }); const result = results.shift(); if (result instanceof Error) throw result;
    assert.ok(result, `Unexpected fetch ${url}`); return result;
  } };
  const deps = { "@/app/my_page/_utils/auth-header": auth, "../_utils/auth-header": auth };
  return { calls, tokens, alerts, globals, deps, auth };
}
function res(status, body = "77") { return new Response(body, { status }); }
for (const [label, results, expected] of [
  ["created", [res(201), res(200)], "created"],
  ["create401", [res(401)], "rejected"], ["create403", [res(403)], "rejected"],
  ["create500", [res(500)], "unknown"], ["createLost", [new Error("offline")], "unknown"],
  ["createMalformed", [res(201, "{bad")], "unknown"], ["createStringID", [res(201, '"77"')], "unknown"],
  ["image401", [res(201), res(401)], "image-failed"], ["image403", [res(201), res(403)], "image-failed"],
  ["image500", [res(201), res(500)], "image-unknown"], ["imageLost", [res(201), new Error("offline")], "image-unknown"],
  ["imageMalformed", [res(201), res(200, "{bad")], "image-unknown"], ["imageWrongID", [res(201), res(200, "88")], "image-unknown"],
]) test(`sell API ${label}`, async () => {
  const f = fixture(results);
  const api = load("src/app/sell/_api/api.ts", f.deps, f.globals);
  const value = await api.createProduct({ product: product(), userId: 1000 });
  assert.equal(value.status, expected);
  if (["created", "image-failed", "image-unknown"].includes(expected)) assert.equal(value.productId, 77);
  assert.equal(f.calls.filter(c => c.url === "/products").length, 1);
  if (f.calls.length === 2) {
    assert.equal(f.calls[1].url, "/products/upload"); assert.equal(f.calls[1].body.get("productId"), "77");
    assert.equal(f.calls[1].body.getAll("files").length, 1);
  }
  assert.equal(f.calls[0].headers.Authorization, "access");
});
for (const result of [{ status: "image-failed", productId: 77 }, { status: "image-unknown", productId: 77 }, { status: "unknown" }, { status: "created", productId: 77 }]) {
  test(`sell component ${result.status} blocks recreation and preserves result`, async () => {
    const calls = [], alerts = [], moves = [], writes = [];
    let index = 0;
    const states = [0, [product()], {}];
    const tree = load("src/app/sell/page.tsx", {
      react: { ...React, useContext: () => ({ getUser: () => ({ id: 1000 }) }), useRef: value => ({ current: value }), useState: value => {
        const current = states[index++]; return [current, updater => writes.push(updater)];
      } },
      "./_components/TabContent": noop, "./_components/TabHeader": noop, "@/assets/Loading.svg": noop,
      "./_api/api": { createProduct: async request => { calls.push(request); return result; } },
      "next/navigation": { useRouter: () => ({ push: url => moves.push(url) }) },
      "@/providers/UserContext": { UserContext: {} }, "../my_page/_hooks/useMypageSWR": { default: () => ({ user: { accountBank: "fixture", accountNumber: "fixture" }, isLoading: false }), __esModule: true },
      "next/link": noop,
    }, { alert: text => alerts.push(text) }).default();
    const button = find(tree, n => n.props?.["data-cy"] === "product-upload-button"); assert.ok(button);
    button.props.onClick(); await new Promise(setImmediate); button.props.onClick(); await new Promise(setImmediate);
    assert.equal(calls.length, 1); assert.equal(moves.length, result.status === "created" ? 1 : 0);
    const saved = writes[0]({})[1]; assert.equal(saved.status, result.status);
    if ("productId" in result) assert.equal(saved.productId, 77);
    if (result.status !== "created") assert.match(alerts[0], /확인|실패/);
  });
}
for (const [label, results, expected] of [
  ["dto", [res(200, '{"id":77,"title":"changed"}'), res(200, "hihi")], true],
  ["badDTO", [res(200, '{"id":88}')], false], ["stringDTO", [res(200, '"77"')], false],
  ["denied", [res(403)], false], ["uploadDenied", [res(200, '{"id":77}'), res(403)], false],
  ["lost", [new Error("offline")], false],
]) test(`admin PUT ${label} uses numeric DTO ID for alias upload`, async () => {
  const f = fixture(results); const api = load("src/app/admin/products/_api/api.ts", f.deps, f.globals);
  assert.equal(await api.createProduct(product()), expected);
  if (f.calls.length === 2) { assert.equal(f.calls[1].url, "/api/upload"); assert.equal(f.calls[1].body.get("productId"), "77"); assert.equal(f.tokens.length, 2); }
});
for (const outcome of [false, undefined, new Error("offline"), true]) test(`admin deletion ${String(outcome)} controls PUT/upload`, async () => {
  const alerts = [], calls = []; let state = 0;
  const old = { ...product(), images: [{ id: 1, imageURL: "old.png" }] };
  const updated = { ...product(), images: [{ id: -1, imageURL: new Blob(["fixture"]) }] };
  const tree = load("src/app/admin/products/[id]/page.tsx", {
    react: { ...React, useEffect: noop, useState: () => [[updated, old][state++], noop] },
    "../_components/TabContent": noop, "../_components/TabHeader": noop, "@/components/common/Button": noop,
    "../_api/api": { deleteImageById: async () => { calls.push("delete"); if (outcome instanceof Error) throw outcome; return outcome; },
      createProduct: async () => { calls.push("put"); return true; } },
    console: {}, "next/navigation": { usePathname: () => "/admin/products/77" },
  }, { alert: value => alerts.push(value), window: { location: { reload: () => calls.push("reload") } } }).default();
  const button = find(tree, n => n.props?.text === "수정하기"); assert.ok(button); await button.props.onClick();
  assert.deepEqual(calls, outcome === true ? ["delete", "put", "reload"] : ["delete"]);
  if (outcome !== true) assert.match(alerts[0], /일부 이미지는 이미 삭제/);
});
for (const result of [res(200), res(401), res(403), res(500), res(200, "88"), new Error("offline")]) test(`refresh API ${result.status || "lost"}`, async () => {
  const f = fixture([result]); const api = load("src/app/my_page/_api/api.ts", f.deps, f.globals);
  if (result.status === 200 && await result.clone().text() === "77") assert.equal(await api.updateProductPurchaseById(77), "77");
  else await assert.rejects(api.updateProductPurchaseById(77));
});
for (const outcome of ["77", new Error("offline")]) test(`refresh component ${String(outcome)} avoids false success`, async () => {
  const alerts = []; let state = 0;
  const states = [false, false, true, 1];
  const tree = load("src/app/my_page/_compoenets/SellHistory.tsx", {
    react: { ...React, useState: () => [states[state++], noop] }, "../_design/ProductCard": noop,
    "react-dom": { createPortal: node => node }, "../_design/ModalSmall": noop, "./modal/SellProductDetail": noop,
    "../_hooks/useMypageSWR": noop, "../_design/ModalLarge": noop,
    "../_api/api": { updateProductPurchaseById: async () => { if (outcome instanceof Error) throw outcome; return outcome; } },
  }, { alert: text => alerts.push(text), document: { body: {} } }).default({ sellList: [{ id: 1 }, { id: 77 }], deleteProduct: noop });
  const button = find(tree, n => typeof n.props?.updateDate === "function");
  // Modal render depends on selection state: find the actual handler prop even when children are not mounted.
  assert.ok(button); await button.props.updateDate();
  assert.match(alerts[0], outcome instanceof Error ? /확인하지 못/ : /갱신 하였습니다/);
});
