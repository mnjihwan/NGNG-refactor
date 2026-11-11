// Offline execution of the actual TypeScript API and payment button handler.
// Uses only Node built-ins and the already installed TypeScript/React packages.
// Does not exercise a browser, live HTTP, hooks rendering, or the backend.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const frontend = path.resolve(__dirname, '..');
const ts = require(path.join(frontend, 'node_modules/typescript'));
const react = require(path.join(frontend, 'node_modules/react'));

function load(relative, dependencies, globals = {}) {
  const filename = path.join(frontend, relative);
  const code = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    fileName: filename,
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.React, esModuleInterop: true },
  }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(code, { module, exports: module.exports, ...globals,
    require(name) {
      if (Object.hasOwn(dependencies, name)) return dependencies[name];
      throw new Error(`Unexpected module: ${name}`);
    },
  }, { filename });
  return module.exports;
}

const enumModule = load('src/app/my_page/_constans/enum.ts', {});
const response = (data, ok = true, status = ok ? 200 : 400) => ({ ok, status, json: async () => data });

async function exercise({ check = response(true), payment = response({ id: 101 }), confirm = true } = {}) {
  const requests = [];
  const updates = [];
  const accessHeaders = [];
  const alerts = [];
  let confirmations = 0;
  const api = load('src/app/my_page/_api/api.ts', {
    '../_utils/auth-header': { getAccessToken: () => 'test-token', setAccessToken: response => accessHeaders.push(response.status) },
  }, { fetch: async (url, options) => {
    requests.push({ url, body: JSON.parse(options.body), authorization: options.headers.Authorization });
    const result = url.endsWith('/check') ? check : payment;
    if (result instanceof Error) throw result;
    return result;
  } });
  const component = load('src/app/my_page/_compoenets/modal/BuyProductDetails.tsx', {
    react: { ...react, useEffect: () => {} },
    '../../_constans/enum': enumModule,
    '../../_design/ProductDetail': () => null,
    '../../_hooks/useMypageSWR': () => ({ UpdateTransactionDetailStatus: payload => updates.push(payload) }),
    '../../_api/api': api,
  }, { alert: message => alerts.push(message), confirm: () => { confirmations++; return confirm; } });
  const tree = component.default({ transactionDetails: {
    id: 9, price: 100, status: { id: 1, status: '입금 대기' }, product: { id: 42 },
  } });
  function findPayment(node) {
    if (!node || typeof node !== 'object') return undefined;
    if (node.type === 'button' && node.props.children === '결제 하기') return node;
    return react.Children.toArray(node.props?.children).map(findPayment).find(Boolean);
  }
  const button = findPayment(tree);
  assert.ok(button, 'payment button is reachable in the actual component');
  await button.props.onClick();
  return { requests, updates, alerts, confirmations, accessHeaders };
}

test('successful check/payment preserves request fields and changes status only after payment', async () => {
  const result = await exercise();
  assert.deepEqual(result.requests, [
    { url: '/api/points/check', body: { paymentCost: 100 }, authorization: 'test-token' },
    { url: '/api/points/payment', body: { paymentCost: 100, payProductId: 42 }, authorization: 'test-token' },
  ]);
  assert.equal(result.updates.length, 1);
  assert.equal(result.updates[0].data.transactionDetailsId, 9);
  assert.equal(result.updates[0].data.status, '2');
  assert.equal(result.alerts.length, 0);
});

test('check false prevents payment and status change', async () => {
  const result = await exercise({ check: response(false) });
  assert.equal(result.requests.length, 1);
  assert.equal(result.updates.length, 0);
  assert.equal(result.confirmations, 0);
  assert.equal(result.alerts.length, 1);
});

test('cancelled confirmation prevents payment and status change', async () => {
  const result = await exercise({ confirm: false });
  assert.equal(result.requests.length, 1);
  assert.equal(result.updates.length, 0);
  assert.equal(result.alerts.length, 0);
});

for (const [name, check] of [
  ['HTTP error', response({ status: 400 }, false)],
  ['network error', new Error('check network failure')],
  ['nonboolean payload', response({ error: 'invalid' })],
  ['JSON error', { ok: true, json: async () => { throw new Error('invalid check JSON'); } }],
]) {
  test(`check ${name} prevents payment/status change and is handled`, async () => {
    const result = await exercise({ check });
    assert.equal(result.requests.length, 1);
    assert.equal(result.updates.length, 0);
    assert.equal(result.confirmations, 0);
    assert.equal(result.alerts.length, 1);
  });
}

for (const [name, payment] of [
  ['HTTP error', response({ status: 400 }, false)],
  ['network error', new Error('payment network failure')],
  ['JSON error', { ok: true, json: async () => { throw new Error('invalid payment JSON'); } }],
]) {
  test(`payment ${name} prevents status change and is handled`, async () => {
    const result = await exercise({ payment });
    assert.equal(result.requests.length, 2);
    assert.equal(result.updates.length, 0);
    assert.equal(result.alerts.length, 1);
  });
}

for (const status of [400, 401, 403, 404, 409]) {
  test(`payment HTTP ${status} keeps access-header handling and stops the separate status PUT`, async () => {
    let parsed = 0;
    const result = await exercise({ payment: { ok: false, status, json: async () => { parsed++; throw new Error('error body must not be parsed as a success'); } } });
    assert.equal(result.requests.length, 2);
    assert.equal(result.updates.length, 0);
    assert.equal(result.alerts.length, 1);
    assert.deepEqual(result.accessHeaders, [200, status]);
    assert.equal(parsed, 0);
    assert.equal(result.alerts[0], '결제에 실패했습니다. 다시 시도해주세요.');
  });
}
