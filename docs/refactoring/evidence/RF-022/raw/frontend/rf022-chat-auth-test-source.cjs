// Actual TypeScript components with React/STOMP/browser doubles; no network or DB.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
const react = require("react");
const root = path.resolve(__dirname, "..");
function load(relative, deps = {}, globals = {}) {
  const beforeFiles = {
    "src/app/product/[id]/_components/Chatting.tsx": "before-public.tsx",
    "src/app/chat/[roomId]/_components/PrivateChatting.tsx": "before-private.tsx",
    "src/providers/stompAlarmSubscribe.tsx": "before-alarm.tsx",
  };
  const file = process.env.RF022_BEFORE === "1" && process.env.RF022_BEFORE_DIR && beforeFiles[relative] ?
    path.join(process.env.RF022_BEFORE_DIR, beforeFiles[relative]) : path.join(root, relative);
  const code = ts.transpileModule(fs.readFileSync(file, "utf8"), {
    fileName: file,
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
  }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(code, { module, exports: module.exports, ...globals, require(name) {
    if (name === "react/jsx-runtime") return require(name);
    if (Object.hasOwn(deps, name)) return deps[name];
    throw Error(`Unexpected module: ${name}`);
  } }, { filename: file });
  return module.exports;
}
function env() {
  const values = new Map();
  const window = new EventTarget(); window.focus = () => {};
  const globals = { window, Event, process: { env: { NEXT_PUBLIC_CHAT_SOCKET: "ws://offline.invalid" } },
    sessionStorage: { getItem: key => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) },
    setTimeout: () => 1, clearTimeout() {}, Notification: class {
      static permission = "granted"; close() {}
    } };
  const auth = load("src/providers/chatAuth.ts", {}, globals);
  const clients = [];
  class Client {
    constructor(options) { Object.assign(this, options); this.connectHeaders = {}; this.stops = 0;
      this.activations = 0; this.connected = false; this.subscriptions = []; clients.push(this); }
    activate() { this.activations++; this.active = true; }
    async connect() { await this.beforeConnect?.(); if (!this.active) return;
      this.connected = true; this.onConnect?.(); }
    deactivate() { this.stops++; this.active = false; this.connected = false; return Promise.resolve(); }
    subscribe(destination, callback) { this.subscriptions.push({ destination, callback }); }
  }
  const connection = load("src/providers/chatConnection.ts", {
    "@stomp/stompjs": { Client }, "./chatAuth": auth,
  }, globals);
  const user = { id: 2, name: "buyer", nickname: "buyer" };
  function login(id = 2, token = "Bearer first-token") {
    values.set("user", JSON.stringify({ ...user, id }));
    values.set("accessToken", JSON.stringify(token));
  }
  return { values, globals, auth, clients, Client, connection, login, user };
}
function component(kind, anonymous = false) {
  const f = env(); if (!anonymous) f.login();
  const effects = [], states = [], notifications = [], mutations = [];
  const noop = () => null;
  f.globals.Notification = class { static permission = "granted";
    constructor(...args) { notifications.push(args); } close() {} };
  const deps = { react: { ...react, useContext: () => ({ getUser: () => anonymous ? undefined : f.user }),
    useEffect: fn => effects.push(fn), useCallback: fn => fn,
    useRef: () => ({ current: null }), useState: initial => [initial, value => states.push(value)] },
    "@stomp/stompjs": { Client: f.Client },
    "@/providers/UserContext": { UserContext: {}, useChatUser: () => anonymous ? undefined : f.user },
    "./UserContext": { UserContext: {}, useChatUser: () => anonymous ? undefined : f.user },
    "@/providers/chatConnection": f.connection, "./chatConnection": f.connection,
    "next/navigation": { redirect: noop }, "next/image": noop,
    "@/components/common/chat/Message": noop,
    "@/assets/SVG/Close.svg": noop, "@/assets/SVG/Plus.svg": noop, "@/assets/SVG/CaretLeft.svg": noop,
    "../../_api": { sendPrivateChatMessage: noop }, "../../_api/api": { sendPublicChatMessage: noop },
    "./ChattingHeader": noop, "./ChattingInputTextArea": noop,
    swr: { mutate: key => mutations.push(key) } };
  const files = { public: "src/app/product/[id]/_components/Chatting.tsx",
    private: "src/app/chat/[roomId]/_components/PrivateChatting.tsx", alarm: "src/providers/stompAlarmSubscribe.tsx" };
  const data = kind === "public" ? { id: 7, chats: [], reports: null } :
    { chatRoomId: 3, messages: [], product: { productId: 7, buyer: { id: 2 }, seller: { id: 1 } } };
  const tree = load(files[kind], deps, f.globals).default({ data });
  const cleanups = effects.map(fn => fn());
  return { ...f, cleanups, states, notifications, mutations, tree };
}
if (process.env.RF022_BEFORE === "1") {
  for (const kind of ["public", "private", "alarm"]) {
    test(`before ${kind}: CONNECT has no auth and cleanup does not stop actual client`, () => {
      const f = component(kind); const client = f.clients[0];
      assert.equal(client.beforeConnect, undefined);
      assert.equal(Object.keys(client.connectHeaders).length, 0);
      for (const fn of f.cleanups) fn?.();
      assert.equal(client.stops, 0);
    });
  }
} else {
  for (const [stored, expected] of [[undefined, undefined], ["Bearer abc", "Bearer abc"],
    [JSON.stringify("Bearer abc"), "Bearer abc"], ["abc", "Bearer abc"]]) {
    test(`CONNECT header reads stored form ${stored}`, async () => {
      const f = env(); if (stored !== undefined) f.values.set("accessToken", stored);
      const c = f.connection.createChatConnection({ onConnect() {}, onUnavailable() {} });
      c.client.activate(); await c.client.connect();
      assert.equal(c.client.connectHeaders.Authorization, expected);
      assert.equal(c.client.brokerURL, "ws://offline.invalid/chat-server"); c.dispose();
    });
  }
  for (const kind of ["public", "private", "alarm"]) {
    test(`${kind}: authenticates, subscribes, stops exact client and ignores late CONNECT`, async () => {
      const f = component(kind); const client = f.clients[0];
      await client.connect();
      assert.equal(client.connectHeaders.Authorization, "Bearer first-token");
      assert.equal(client.subscriptions[0].destination, {
        public: "/public-chats/7", private: "/private-chats/7/2", alarm: "/alarms/2" }[kind]);
      const count = client.subscriptions.length;
      for (const fn of f.cleanups) fn?.();
      assert.ok(client.stops > 0); client.onConnect(); assert.equal(client.subscriptions.length, count);
    });
  }
  test("anonymous public subscribes without Authorization; anonymous alarm creates no client", async () => {
    const f = component("public", true); await f.clients[0].connect();
    assert.equal(Object.keys(f.clients[0].connectHeaders).length, 0);
    assert.equal(f.clients[0].subscriptions[0].destination, "/public-chats/7");
    assert.equal(component("alarm", true).clients.length, 0);
  });
  test("network reconnect reads the latest access; token notification restarts once", async () => {
    const f = env(); f.login(); const c = f.connection.createChatConnection({ userId: 2,
      requiresAuth: true, onConnect() {}, onUnavailable() {} });
    c.client.activate(); await c.client.connect();
    f.values.set("accessToken", JSON.stringify("Bearer renewed"));
    f.auth.notifyChatAuthChanged(); f.auth.notifyChatAuthChanged();
    await new Promise(setImmediate); assert.equal(c.client.activations, 2);
    await c.client.connect(); assert.equal(c.client.connectHeaders.Authorization, "Bearer renewed");
    f.values.set("accessToken", JSON.stringify("Bearer latest"));
    await c.client.beforeConnect(); assert.equal(c.client.connectHeaders.Authorization, "Bearer latest"); c.dispose();
  });
  test("logout/account switch blocks old identity and disposal cancels pending restart", async () => {
    const f = env(); f.login(); let unavailable = 0;
    const c = f.connection.createChatConnection({ userId: 2, requiresAuth: true,
      onConnect() { throw Error("must not subscribe"); }, onUnavailable() { unavailable++; } });
    c.client.activate(); f.values.clear(); f.auth.notifyChatAuthChanged(); await c.client.connect();
    assert.equal(c.client.active, false); assert.ok(unavailable > 0);
    f.login(3); f.auth.notifyChatAuthChanged(); await c.client.connect(); assert.equal(c.client.active, false);
    c.dispose(); const stops = c.client.stops; f.auth.notifyChatAuthChanged(); assert.equal(c.client.stops, stops);
    const d = f.connection.createChatConnection({ userId: 3, onConnect() {}, onUnavailable() {} });
    d.client.activate(); await d.client.beforeConnect(); f.values.set("accessToken", "Bearer new");
    f.auth.notifyChatAuthChanged(); d.dispose(); await new Promise(setImmediate); assert.equal(d.client.activations, 1);
  });
  test("missing private credentials and rejected token never retry as anonymous", async () => {
    const f = env(); f.login(); let connected = 0, failures = 0;
    const c = f.connection.createChatConnection({ userId: 2, requiresAuth: true,
      onConnect() { connected++; }, onUnavailable() { failures++; } });
    f.values.delete("accessToken"); c.client.activate(); await c.client.connect(); assert.equal(connected, 0);
    f.login(); c.client.activate(); await c.client.connect(); c.client.onStompError({ body: "sensitive" });
    assert.equal(c.client.active, false); assert.equal(c.client.connectHeaders.Authorization, "Bearer first-token");
    assert.equal(failures, 2); c.dispose();
  });
  for (const kind of ["public", "private", "alarm"]) {
    test(`${kind}: successful envelope only; malformed/error/late callbacks have no successful effects`, async () => {
      const f = component(kind); const client = f.clients[0]; await client.connect();
      const receive = client.subscriptions[0].callback;
      const valid = kind === "public" ? { id: 19, message: "hello", userId: 2, userNickname: "buyer" } :
        { chatId: 19, message: "hello", user: { id: 2, name: "buyer", nickname: "buyer" } };
      const before = f.states.filter(value => typeof value === "function").length;
      for (const body of ["bad-json", "null", JSON.stringify({ body: { error: "denied" } }),
        JSON.stringify({ body: null }), JSON.stringify({ error: "unauthorized" })]) {
        assert.doesNotThrow(() => receive({ body }));
      }
      assert.equal(f.states.filter(value => typeof value === "function").length, before);
      assert.equal(f.notifications.length, 0); assert.equal(f.mutations.length, 0);
      receive({ body: JSON.stringify({ body: valid }) });
      if (kind === "alarm") {
        assert.equal(f.notifications.length, 1); assert.equal(f.mutations.length, 1);
        receive({ body: JSON.stringify({ body: valid }) }); assert.equal(f.notifications.length, 1);
      } else {
        const update = f.states.filter(value => typeof value === "function").at(-1);
        assert.equal(update([])[0].message, "hello");
      }
      f.values.clear(); f.auth.notifyChatAuthChanged();
      const count = f.states.length, notices = f.notifications.length, mutations = f.mutations.length;
      receive({ body: JSON.stringify({ body: { ...valid, id: 20, chatId: 20 } }) });
      assert.equal(f.states.length, count); assert.equal(f.notifications.length, notices);
      assert.equal(f.mutations.length, mutations);
      for (const fn of f.cleanups) fn?.();
    });
  }
  test("HTTP token writes and context login/logout notify; logout removes credential", () => {
    const f = env(); let changes = 0; f.globals.document = {};
    f.globals.window.addEventListener(f.auth.CHAT_AUTH_CHANGED, () => changes++);
    const headers = load("src/app/my_page/_utils/auth-header.ts", {
      "@/providers/chatAuth": f.auth,
    }, f.globals);
    headers.setAccessToken(new Response(null, { headers: { Authorization: "Bearer renewed" } }));
    assert.equal(f.auth.getChatAuthorization(), "Bearer renewed"); assert.equal(changes, 1);
    headers.setAccessToken(new Response(null, { headers: { Authorization: "Bearer renewed" } }));
    assert.equal(changes, 1);
    const context = load("src/providers/UserContext.tsx", { react, "./chatAuth": f.auth }, f.globals);
    const value = context.default({ children: null }).props.value;
    value.setUser(f.user); assert.equal(changes, 2); assert.equal(value.getUser().id, 2);
    value.logout(); assert.equal(changes, 3);
    assert.equal(f.values.has("user"), false); assert.equal(f.values.has("accessToken"), false);
    value.logout(); assert.equal(changes, 3);
  });
  function chatApi(result) {
    const tokens = [], requests = [];
    const auth = { getAccessToken: () => "Bearer old", setAccessToken: res => tokens.push(res) };
    const globals = { fetch: async (url, options) => {
      requests.push({ url, ...options }); if (result instanceof Error) throw result; return result;
    } };
    const dayjs = Object.assign(() => {}, { extend() {} });
    return { tokens, requests,
      private: load("src/app/chat/_api.ts", { "../my_page/_utils/auth-header": auth }, globals),
      product: load("src/app/product/_api/api.ts", {
        "@/app/my_page/_utils/auth-header": auth, dayjs, "dayjs/plugin/utc": () => {},
      }, globals) };
  }
  for (const [name, call] of [
    ["messages", f => f.private.getAllChatMessages({ chatRoomId: 3, userId: 2 })],
    ["rooms", f => f.private.getAllChatRoomData(2)],
    ["find", f => f.product.findPrivateChatRoomByProductIdAndBuyerId(7, 2)],
    ["create", f => f.product.createPrivateChatRoom({ productId: 7, buyerId: 2, sellerId: 1 })],
  ]) {
    for (const status of [401, 403, 500]) {
      test(`${name}: HTTP ${status} cannot become normal chat data`, async () => {
        const res = new Response(JSON.stringify({ status, message: "denied" }), { status });
        const f = chatApi(res); await assert.rejects(call(f));
        assert.equal(f.tokens[0], res); assert.equal(f.requests[0].headers.Authorization, "Bearer old");
      });
    }
    test(`${name}: network errors reject`, async () => {
      const f = chatApi(new Error("offline")); await assert.rejects(call(f)); assert.equal(f.tokens.length, 0);
    });
    test(`${name}: valid HTTP body/headers preserved`, async () => {
      const body = name === "rooms" ? [{ privateChatRoomId: 3 }] : name === "messages" ?
        { chatRoomId: 3, messages: [] } : 3;
      const f = chatApi(new Response(JSON.stringify(body)));
      assert.equal(JSON.stringify(await call(f)), JSON.stringify(body)); assert.equal(f.tokens.length, 1);
    });
  }
  test("find missing room remains -1; invalid successful room IDs reject", async () => {
    const f = chatApi(new Response(null, { status: 404 }));
    assert.equal(await f.product.findPrivateChatRoomByProductIdAndBuyerId(7, 2), -1);
    for (const body of [{ status: 401 }, "3", null, -1]) {
      const g = chatApi(new Response(JSON.stringify(body)));
      await assert.rejects(g.product.findPrivateChatRoomByProductIdAndBuyerId(7, 2));
      const h = chatApi(new Response(JSON.stringify(body)));
      await assert.rejects(h.product.createPrivateChatRoom({ productId: 7, buyerId: 2, sellerId: 1 }));
    }
  });
  test("installed STOMP Client emits authenticated CONNECT and closes/reconnects using fake transport", async () => {
    const f = env(); f.login(); const sockets = [], sent = [];
    const previousSocket = global.WebSocket;
    class OfflineSocket {
      constructor(url) { this.url = url; this.readyState = 0; sockets.push(this);
        queueMicrotask(() => { this.readyState = 1; this.onopen?.({}); }); }
      send(frame) {
        sent.push(frame);
        if (frame.startsWith("CONNECT")) queueMicrotask(() => this.onmessage?.({
          data: "CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0",
        }));
      }
      close() { this.readyState = 3; queueMicrotask(() => this.onclose?.({ code: 1000 })); }
    }
    global.WebSocket = OfflineSocket;
    const implementation = load("src/providers/chatConnection.ts", {
      "@stomp/stompjs": require("@stomp/stompjs"), "./chatAuth": f.auth,
    }, f.globals);
    let connected = 0;
    const c = implementation.createChatConnection({ userId: 2, requiresAuth: true,
      onConnect: client => { connected++; client.subscribe("/alarms/2", () => {}); },
      onUnavailable() {} });
    c.client.heartbeatIncoming = 0; c.client.heartbeatOutgoing = 0;
    try {
      c.client.activate(); await new Promise(setImmediate);
      assert.equal(connected, 1); assert.match(sent[0], /Authorization:Bearer first-token\n/);
      assert.equal(sockets[0].url, "ws://offline.invalid/chat-server");
      f.values.set("accessToken", JSON.stringify("Bearer renewed"));
      f.auth.notifyChatAuthChanged(); f.auth.notifyChatAuthChanged(); await new Promise(setImmediate);
      assert.equal(connected, 2); assert.equal(sockets.length, 2); assert.equal(sockets[0].readyState, 3);
      const connects = sent.filter(frame => frame.startsWith("CONNECT"));
      assert.equal(connects.length, 2); assert.match(connects[1], /Authorization:Bearer renewed\n/);
      c.dispose(); await new Promise(setImmediate);
      assert.equal(c.client.active, false); assert.equal(sockets[1].readyState, 3);
      assert.equal(c.isCurrent(), false);
      // beforeConnect on a disposed client cancels activation before opening a transport.
      c.client.activate(); await new Promise(setImmediate); assert.equal(sockets.length, 2);
    } finally {
      c.dispose(); global.WebSocket = previousSocket;
    }
  });
  test("alarm re-render creates no duplicate connection and identity transition disposes old client", async () => {
    const f = env(); f.login(); const noop = () => null;
    let previousDeps, nextEffect, cleanup;
    const alarm = load("src/providers/stompAlarmSubscribe.tsx", {
      react: { ...react, useRef: () => ({ current: null }), useEffect(fn, deps) {
        if (!previousDeps || deps.some((value, i) => value !== previousDeps[i])) nextEffect = fn;
        previousDeps = deps;
      } },
      "./UserContext": { useChatUser: () => JSON.parse(f.values.get("user") ?? "null") ?? undefined },
      "./chatConnection": f.connection, swr: { mutate: noop },
    }, f.globals).default;
    function render() { alarm(); if (nextEffect) { cleanup?.(); cleanup = nextEffect(); nextEffect = undefined; } }
    render(); await f.clients[0].connect(); render(); render(); assert.equal(f.clients.length, 1);
    f.login(3); f.auth.notifyChatAuthChanged(); render(); await f.clients[1].connect();
    assert.ok(f.clients[0].stops > 0); assert.equal(f.clients.length, 2);
    assert.equal(f.clients[1].subscriptions[0].destination, "/alarms/3");
    f.values.clear(); f.auth.notifyChatAuthChanged(); render(); assert.equal(f.clients.length, 2);
    assert.ok(f.clients[1].stops > 0); cleanup?.();
  });
  test("chat identity hook reacts to same-tab auth events and removes listener", () => {
    const f = env(); f.login(); let updates = 0, cleanup;
    const context = load("src/providers/UserContext.tsx", {
      react: { ...react, useContext: () => ({ getUser: () => JSON.parse(f.values.get("user")) }),
        useState: () => [0, () => updates++], useEffect: fn => { cleanup = fn(); } },
      "./chatAuth": f.auth,
    }, f.globals);
    assert.equal(context.useChatUser().id, 2); f.auth.notifyChatAuthChanged(); assert.equal(updates, 1);
    cleanup(); f.auth.notifyChatAuthChanged(); assert.equal(updates, 1);
  });
  test("popup logout/account switch uses credential-free invalidation; anonymous public stays connected", async () => {
    const channels = [], sent = [];
    class Channel {
      constructor(name) { this.name = name; channels.push(this); }
      postMessage(data) {
        sent.push(data);
        for (const peer of channels) if (peer !== this && peer.name === this.name) {
          queueMicrotask(() => peer.onmessage?.({ data }));
        }
      }
    }
    const parent = env(), popup = env(), anonymous = env(); parent.login(); popup.login();
    for (const f of [parent, popup, anonymous]) f.globals.window.BroadcastChannel = Channel;
    const privateConnection = popup.connection.createChatConnection({ userId: 2, requiresAuth: true,
      onConnect() {}, onUnavailable() {} });
    privateConnection.client.activate(); await privateConnection.client.connect();
    const publicConnection = anonymous.connection.createChatConnection({ onConnect() {}, onUnavailable() {} });
    publicConnection.client.activate(); await publicConnection.client.connect();
    parent.values.set("accessToken", JSON.stringify("Bearer renewed")); parent.auth.notifyChatAuthChanged();
    assert.equal(sent.length, 0); assert.equal(popup.auth.getChatAuthorization(), "Bearer first-token");
    parent.values.clear(); parent.auth.notifyChatAuthChanged(true); await new Promise(setImmediate);
    assert.equal(JSON.stringify(sent), '[{"type":"invalidate"}]');
    assert.equal(popup.values.has("accessToken"), false); assert.equal(popup.values.has("user"), false);
    assert.equal(privateConnection.client.active, false); assert.equal(privateConnection.isCurrent(), false);
    assert.equal(publicConnection.client.active, true); assert.equal(publicConnection.isCurrent(), true);
    popup.login(); const other = popup.connection.createChatConnection({ userId: 2, requiresAuth: true,
      onConnect() {}, onUnavailable() {} }); other.client.activate(); await other.client.connect();
    parent.login(3); parent.auth.notifyChatAuthChanged(true); await new Promise(setImmediate);
    assert.equal(other.client.active, false); assert.equal(popup.values.has("user"), false);
    other.dispose(); privateConnection.dispose(); publicConnection.dispose();
  });
  for (const kind of ["rooms", "messages"]) {
    test(`${kind}: rejected HTTP data renders an error instead of normal/loading content`, () => {
      const noop = () => null;
      const hooks = { error: new Error("unauthorized"), isLoading: false };
      const deps = {
        react: { ...react, useContext: () => ({ getUser: () => ({ id: 2 }) }),
          useState: initial => [initial, noop], useEffect: noop },
        "@/assets/Loading.svg": noop, "@/utils": {}, "next/image": noop,
        "next/navigation": { redirect: noop, useParams: () => ({ roomId: "3" }) },
        "@/providers/UserContext": {}, "../_hooks/usePrivateChatRoom": () => hooks,
        "../_hooks/usePrivateChatMessgae": () => hooks,
        "react-dom": {}, "./_components/PrivateChatting": noop,
        "./_components/TransferRequest": noop, "./_components/RequestProcessModal": noop,
        "./_components/TransactionStatusToActiveButton": noop,
        "@/app/my_page/_hooks/useMypageSWR": noop, swr: { mutate: noop },
      };
      const component = load(kind === "rooms" ? "src/app/chat/_components/ChatRoomList.tsx" :
        "src/app/chat/[roomId]/PrivateChat.tsx", deps, { console: { log: noop } }).default;
      const tree = component({ userId: 2, currentBadge: 1 }); assert.equal(tree.props.role, "alert");
    });
  }
}
