// Actual API/component sources with synthetic responses and hook/fetch doubles; no browser or network.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");
const root = path.resolve(__dirname, "..");
function load(relative, dependencies, globals = {}) {
  const filename = path.join(root, relative);
  const code = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
    fileName: filename,
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
  }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(code, { module, exports: module.exports, ...globals, require(name) {
    if (name === "react/jsx-runtime") return require(name);
    if (Object.hasOwn(dependencies, name)) return dependencies[name];
    throw new Error(`Unexpected module: ${name}`);
  } }, { filename });
  return module.exports;
}
const point = Object.freeze({ id: 103, beforeCost: 1000, addCost: 200, cost: 1200,
  type: "충전", typeDetail: "Fixture 표시 문구", createdAt: "2026-01-02T03:04:05.123+00:00", updatedAt: null });
function apiFixture(body, status) {
  const calls = [], headers = [];
  const api = load("src/app/my_page/_api/api.ts", {
    "../_utils/auth-header": { getAccessToken: () => "synthetic-header", setAccessToken: response => headers.push(response.status) },
  }, { fetch: async (url, options) => { calls.push({ url, options }); return new Response(JSON.stringify(body), {
    status, headers: { "Content-Type": "application/json" },
  }); } });
  return { api, calls, headers };
}
test("list API preserves point values and order without nested user", async () => {
  const fixture = apiFixture([point, { ...point, id: 99 }], 200);
  const result = await fixture.api.getPointHistoryAll();
  assert.equal(JSON.stringify(result), JSON.stringify([point, { ...point, id: 99 }]));
  assert.equal(fixture.calls[0].url, "/api/points/all");
  assert.deepEqual(fixture.headers, [200]);
});
test("charge API invokes completion and parses the user-free 201 response", async () => {
  const fixture = apiFixture(point, 201); let completed = 0;
  const result = await fixture.api.addFetchPoint(200, () => completed++);
  assert.equal(JSON.stringify(result), JSON.stringify(point)); assert.equal(completed, 1);
  assert.equal(fixture.calls[0].url, "/api/points");
  assert.deepEqual(JSON.parse(fixture.calls[0].options.body), { addCost: 200, type: "충전" });
});
test("payment API parses the user-free response and preserves request fields", async () => {
  const fixture = apiFixture({ ...point, type: "결제", cost: 0 }, 200);
  const result = await fixture.api.paymentProduct({ cost: 1000, productId: 42 });
  assert.equal(result.cost, 0); assert.equal(result.type, "결제");
  assert.equal(fixture.calls[0].url, "/api/points/payment");
  assert.deepEqual(JSON.parse(fixture.calls[0].options.body), { paymentCost: 1000, payProductId: 42 });
});
test("actual history view renders dates, amounts and details from point-only data", () => {
  const guarded = new Proxy(point, { get(target, key) {
    if (key === "user") throw new Error("Unexpected user access");
    return Reflect.get(target, key);
  } });
  const { default: View } = load("src/app/my_page/_compoenets/PointHistory.tsx", {
    react: React,
    "../_utils/format": { formatKRW: n => n.toLocaleString("ko-KR") },
    "../_utils/auth-header": {},
    "../_hooks/usePointeHistory": { __esModule: true, default: () => ({ pointHistory: [guarded] }) },
    "../_provider/ModalProovider": { ModalStatus: { Point: 1 }, useModalController: () => [null, () => {}] },
  });
  const html = renderToStaticMarkup(React.createElement(View));
  assert.ok(html.includes("1,200")); assert.ok(html.includes("Fixture 표시 문구"));
  assert.ok(html.includes("충전")); assert.ok(html.includes(new Date(point.createdAt).toLocaleString()));
});
