// Offline execution of actual API, hook mutation and component event handlers.
// Uses installed SWR cache/mutate with a hook double; not browser or live HTTP coverage.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const react = require("react");
const { initCache, SWRGlobalState } = require("swr/_internal");
const frontend = path.resolve(__dirname, "..");

function load(relative, dependencies, globals = {}) {
  const filename = path.join(frontend, relative);
  const code = ts.transpileModule(fs.readFileSync(filename, "utf8"), {
    fileName: filename,
    compilerOptions: {
      target: ts.ScriptTarget.ES2020,
      module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.ReactJSX,
      esModuleInterop: true,
    },
  }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(code, {
    module,
    exports: module.exports,
    ...globals,
    require(name) {
      if (name === "react/jsx-runtime") return require("react/jsx-runtime");
      if (Object.hasOwn(dependencies, name)) return dependencies[name];
      throw new Error(`Unexpected module: ${name}`);
    },
  }, { filename });
  return module.exports;
}

function response(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", Authorization: "renewed-token" },
  });
}

function fixture(result) {
  const requests = [];
  const tokenResponses = [];
  const commits = [];
  const revalidations = [];
  const refreshes = [];
  const alerts = [];
  const stateUpdates = [];
  const key = "/chat/private-chat/chat-room-id/user-id";
  const [provider, mutate] = initCache(new Map());
  const initial = {
    product: { productId: 7, price: 100, seller: { id: 1 }, buyer: { id: 2 } },
    request: { requestId: 9, price: 100 },
  };
  provider.set(key, { data: initial });
  SWRGlobalState.get(provider)[0][key] = [async () => { revalidations.push(true); }];
  const api = load("src/app/chat/_api.ts", {
    "../my_page/_utils/auth-header": {
      getAccessToken: () => "original-token",
      setAccessToken: res => tokenResponses.push(res),
    },
  }, {
    fetch: async (url, options) => {
      requests.push({ url, ...options });
      if (result instanceof Error) throw result;
      return await result;
    },
  });
  const hook = load("src/app/chat/_hooks/usePrivateChatMessgae.ts", {
    "../_api": api,
    swr: () => ({
      data: initial,
      isLoading: false,
      mutate: async (updater, options) => {
        const next = await mutate(key, updater, options);
        commits.push(next);
        return next;
      },
    }),
  }).default({ chatRoomId: 3, userId: 1 });

  const Modal = () => null;
  let stateIndex = 0;
  const noop = () => null;
  const component = load("src/app/chat/[roomId]/PrivateChat.tsx", {
    react: {
      ...react,
      useEffect: () => {},
      useState: initial => {
        const index = stateIndex++;
        return [index === 0 ? true : initial, value => stateUpdates.push({ index, value })];
      },
    },
    "react-dom": { createPortal: element => element },
    "next/navigation": { useParams: () => ({ roomId: "3" }) },
    "next/image": noop,
    "@/assets/Loading.svg": noop,
    "./_components/PrivateChatting": noop,
    "./_components/TransferRequest": Modal,
    "./_components/RequestProcessModal": noop,
    "../_hooks/usePrivateChatMessgae": () => hook,
    swr: { mutate: key => refreshes.push(key) },
    "@/providers/UserContext": {},
    "./_components/TransactionStatusToActiveButton": noop,
    "@/app/my_page/_hooks/useMypageSWR": noop,
  }, {
    document: { hasFocus: () => false, body: {} },
    alert: message => alerts.push(message),
    console: { log: () => {} },
  }).default({ userId: 1 });
  function find(node) {
    if (!node || typeof node !== "object") return undefined;
    if (node.type === Modal) return node;
    return react.Children.toArray(node.props?.children).map(find).find(Boolean);
  }
  const modal = find(component);
  assert.ok(modal, "request processing modal is reachable in actual component");
  return { api, hook, modal, requests, tokenResponses, commits, revalidations,
    refreshes, alerts, stateUpdates, cache: () => provider.get(key).data };
}


for (const id of [9, -1]) {
  test('successful create ID ' + id + ' preserves body/token then refreshes and closes', async () => {
    const f = fixture(response(id));
    await f.modal.props.onChangeTransactionStatus();
    assert.equal(f.requests[0].method, 'POST');
    assert.deepEqual(JSON.parse(f.requests[0].body), { productId: 7, buyerId: 1, sellerId: 1, price: 100 });
    assert.equal(f.requests[0].headers.Authorization, 'original-token');
    assert.equal(f.tokenResponses.length, 1);
    assert.equal(f.commits.length, 1);
    assert.equal(f.revalidations.length, 1);
    assert.deepEqual(f.stateUpdates, [{ index: 0, value: false }]);
    assert.equal(f.alerts.length, 0);
  });
}
const failures = [
  ['403 JSON', () => response({status:403},403)],
  ['401 empty', () => new Response(null,{status:401})],
  ['404 empty', () => new Response(null,{status:404})],
  ['500 JSON', () => response({status:500},500)],
  ['network', () => new Error('fixture network')],
  ['invalid JSON', () => new Response('broken',{status:200})],
  ['object', () => response({id:9})],
  ['numeric string', () => response('9')],
  ['null', () => response(null)],
  ['zero', () => response(0)],
  ['negative except -1', () => response(-2)],
  ['fraction', () => response(1.5)],
];
for (const [name, make] of failures) {
  test(name + ' create rejects hook without successful mutate/revalidation', async () => {
    const f = fixture(make());
    await assert.rejects(f.hook.createTransactionRequest({buyerId:1,productId:7,sellerId:1,price:100}));
    assert.equal(f.commits.length,0);
    assert.equal(f.revalidations.length,0);
    assert.equal(f.tokenResponses.length,name === 'network' ? 0 : 1);
  });
  test(name + ' create leaves dialog open and reports failure', async () => {
    const f = fixture(make());
    await f.modal.props.onChangeTransactionStatus();
    assert.equal(f.commits.length,0);
    assert.equal(f.revalidations.length,0);
    assert.equal(f.stateUpdates.length,0);
    assert.equal(f.alerts.length,1);
  });
}
test('create keeps dialog open until actual response completes', async () => {
  let finish;
  const f = fixture(new Promise(resolve => {finish=resolve;}));
  const action = f.modal.props.onChangeTransactionStatus();
  await new Promise(setImmediate);
  assert.equal(f.stateUpdates.length,0);
  assert.equal(f.commits.length,0);
  finish(response(9));
  await action;
  assert.equal(f.commits.length,1);
  assert.deepEqual(f.stateUpdates,[{index:0,value:false}]);
});
