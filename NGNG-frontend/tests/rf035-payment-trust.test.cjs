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

async function exercise({ check = response(true), payment = response({ id: 101 }), confirm = true, reloadError, duplicate = false } = {}) {
  const requests = [];
  const updates = [];
  const accessHeaders = [];
  const alerts = [];
  let confirmations = 0;
  let reloads = 0;
  const api = load('src/app/my_page/_api/api.ts', {
    '../_utils/auth-header': { getAccessToken: () => 'test-token', setAccessToken: response => accessHeaders.push(response.status) },
  }, { fetch: async (url, options) => {
    requests.push({ url, body: JSON.parse(options.body), authorization: options.headers.Authorization });
    const result = url.endsWith('/check') ? check : payment;
    if (result instanceof Error) throw result;
    return result;
  } });
  const component = load('src/app/my_page/_compoenets/modal/BuyProductDetails.tsx', {
    react: { ...react, useEffect: () => {}, useRef: value => ({current:value}), useState: value => [value, () => {}] },
    '../../_constans/enum': enumModule,
    '../../_design/ProductDetail': () => null,
    '../../_hooks/useMypageSWR': () => ({ UpdateTransactionDetailStatus: payload => updates.push(payload), refreshPaymentState: async () => { reloads++; if(reloadError) throw reloadError; } }),
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
  if(duplicate) await Promise.all([button.props.onClick(), button.props.onClick()]);
  else await button.props.onClick();
  return { requests, updates, alerts, confirmations, accessHeaders, reloads };
}

test('successful payment preserves request fields and reloads without a status PUT', async () => {
  const result = await exercise();
  assert.deepEqual(result.requests, [
    { url: '/api/points/check', body: { paymentCost: 100 }, authorization: 'test-token' },
    { url: '/api/points/payment', body: { paymentCost: 100, payProductId: 42 }, authorization: 'test-token' },
  ]);
  assert.equal(result.updates.length, 0);
  assert.equal(result.reloads, 1);
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
    assert.equal(result.reloads, 1);
    if (status === 409) assert.match(result.alerts[0], /현재 상태만으로 결제 성공을 판단할 수 없습니다/);
    else assert.match(result.alerts[0], /결제 요청이 거부/);
  });
}

test('repeated clicks share one pending submission', async () => {
  const result=await exercise({duplicate:true});
  assert.equal(result.requests.length,2);assert.equal(result.reloads,1);assert.equal(result.updates.length,0);
});
test('insufficient check reloads transaction and balance without a payment', async () => {
  const result=await exercise({check:response(false)});
  assert.equal(result.reloads,1);assert.equal(result.requests.length,1);assert.equal(result.updates.length,0);
});
test('lost payment response remains uncertain after a successful reload', async () => {
  const result=await exercise({payment:new Error('network')});
  assert.equal(result.reloads,1);assert.equal(result.requests.length,2);assert.equal(result.updates.length,0);
  assert.match(result.alerts[0],/결제 여부가 불명확/);
});
for(const [name,options] of [['success',{}],['conflict',{payment:response({},false,409)}],['insufficient',{check:response(false)}],['network',{payment:new Error('network')}]]) {
  test(name+' reports requery failure separately and never retries payment',async()=>{
    const result=await exercise({...options,reloadError:new Error('reload')});
    assert.equal(result.reloads,1);assert.match(result.alerts.at(-1),/재조회에 실패/);assert.equal(result.updates.length,0);
    assert.equal(result.requests.filter(x=>x.url.endsWith('/payment')).length,name==='insufficient'?0:1);
  });
}

test('actual refresh hook awaits checked read and replaces point and transaction caches',async()=>{
  const mutations=[];
  const fresh={point:800,sellList:[{visible:true},{visible:false}],buyList:[{id:9,status:{id:2},product:{visible:true}},{product:{visible:false}}]};
  const hook=load('src/app/my_page/_hooks/useMypageSWR.ts',{
    swr:()=>({mutate:async (...args)=>mutations.push(args)}),
    '../_api/api':{getFetchMyPage:async()=>fresh},
  }).default();
  await hook.refreshPaymentState();
  assert.equal(mutations.length,1);assert.equal(mutations[0][0].point,800);
  assert.equal(mutations[0][0].buyList[0].status.id,2);assert.equal(mutations[0][0].buyList.length,1);
  assert.equal(mutations[0][0].sellList.length,1);assert.equal(mutations[0][1].revalidate,false);
});
test('actual refresh hook propagates read failure without changing cache',async()=>{
  let mutations=0;
  const hook=load('src/app/my_page/_hooks/useMypageSWR.ts',{
    swr:()=>({mutate:async()=>{mutations++;}}),
    '../_api/api':{getFetchMyPage:async()=>{throw new Error('read');}},
  }).default();
  await assert.rejects(()=>hook.refreshPaymentState());assert.equal(mutations,0);
});
test('actual mypage read rejects HTTP errors before parsing recovery state and handles access header',async()=>{
  let headers=0,parsed=0;
  const api=load('src/app/my_page/_api/api.ts',{'../_utils/auth-header':{getAccessToken:()=>'',setAccessToken:()=>{headers++;}}},
    {fetch:async()=>({ok:false,json:async()=>{parsed++;return {};}})});
  await assert.rejects(()=>api.getFetchMyPage());assert.equal(headers,1);assert.equal(parsed,0);
});

test('server error after submission keeps payment result uncertain and only reloads',async()=>{
 const result=await exercise({payment:response({},false,500)});
 assert.match(result.alerts[0],/결제 여부가 불명확/);assert.equal(result.reloads,1);assert.equal(result.requests.length,2);assert.equal(result.updates.length,0);
});

for(const status of [500,502,503,504]) for(const failedReload of [false,true]) {
 test('HTTP '+status+' keeps postcommit outcome unknown; reload failure='+failedReload,async()=>{
  const result=await exercise({payment:response({},false,status),...(failedReload?{reloadError:new Error('read')}: {})});
  assert.match(result.alerts[0],/결제 여부가 불명확/);assert.equal(result.reloads,1);
  assert.equal(result.requests.filter(x=>x.url.endsWith('/payment')).length,1);assert.equal(result.updates.length,0);
  assert.equal(result.alerts.length,failedReload?2:1);if(failedReload)assert.match(result.alerts[1],/재조회에 실패/);
 });
}
function historyHarness() {
 let state=[],cursor=0,effects=[];const alerts=[];
 const Details=()=>null,Modal=()=>null;
 const Component=load('src/app/my_page/_compoenets/BuyHistory.tsx',{
  react:{...react,useState:initial=>{const i=cursor++;if(!(i in state))state[i]=initial;return [state[i],value=>{state[i]=value;}];},useEffect:fn=>effects.push(fn)},
  'react-dom':{createPortal:element=>element},'../_design/ProductCard':()=>null,'../_design/ModalLarge':Modal,'./modal/BuyProductDetails':Details,
 },{document:{body:{}},alert:message=>alerts.push(message)}).default;
 function all(node,predicate){if(!node||typeof node!=='object')return [];return [...(predicate(node)?[node]:[]),...react.Children.toArray(node.props?.children).flatMap(x=>all(x,predicate))];}
 return {alerts,render(buyList){cursor=0;effects=[];const tree=Component({buyList});for(const effect of effects)effect();return {cards:all(tree,node=>node.type==='div'&&node.props.onClick),details:all(tree,node=>node.type===Details),modal:all(tree,node=>node.type===Modal)};}};
}
const buy=(id,status=1)=>({id,status:{id:status,status:'state'},product:{title:'product-'+id,price:100,thumbnail:{thumbnailURL:'synthetic'}}});
test('buy modal tracks transaction ID when refreshed list reorders',()=>{
 const h=historyHarness();const first=buy(9),second=buy(10);
 h.render([first,second]).cards[0].props.onClick();assert.equal(h.render([first,second]).details[0].props.transactionDetails.id,9);
 const refreshed=h.render([second,buy(9,2)]);assert.equal(refreshed.details[0].props.transactionDetails.id,9);assert.equal(refreshed.details[0].props.transactionDetails.status.id,2);assert.equal(h.alerts.length,0);
});
test('buy modal safely closes with notice when selected transaction disappears',()=>{
 const h=historyHarness();h.render([buy(9),buy(10)]).cards[0].props.onClick();
 assert.equal(h.render([buy(9),buy(10)]).details.length,1);
 assert.equal(h.render([buy(10)]).details.length,0);assert.equal(h.alerts.length,1);
 assert.equal(h.render([buy(10)]).details.length,0);assert.equal(h.alerts.length,1);
});
test('buy modal safely closes if refreshed list becomes empty',()=>{
 const h=historyHarness();h.render([buy(9)]).cards[0].props.onClick();assert.equal(h.render([]).modal.length,0);assert.equal(h.alerts.length,1);
});
