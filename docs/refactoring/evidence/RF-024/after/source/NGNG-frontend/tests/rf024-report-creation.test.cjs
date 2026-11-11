const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const react = require('react');
const before = process.env.RF024_BEFORE === '1';
function load(relative, deps, globals) {
  const filename = path.resolve(__dirname, '..', relative);
  const code = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    fileName: filename, compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
  }).outputText;
  const module = {exports:{}};
  vm.runInNewContext(code, {module, exports:module.exports, ...globals, require(name) {
    if(name === 'react/jsx-runtime') return require(name);
    if(Object.hasOwn(deps,name)) return deps[name];
    throw new Error(`Unexpected module ${name}`);
  }}, {filename}); return module.exports;
}
function fixture(responses, imageFiles = []) {
  const requests = [], tokens = [], alerts = []; let closes = 0;
  const globals = {fetch:async (url,options) => {requests.push({url,...options}); const response = responses.shift(); if(response instanceof Error) throw response; return response;},
    FormData, alert:message => alerts.push(message)};
  const api = load('src/app/product/_api/api.ts', {'@/app/my_page/_utils/auth-header': {getAccessToken:()=>'synthetic',setAccessToken:res=>tokens.push(res)},
    dayjs:{extend:()=>{}}, 'dayjs/plugin/utc':{}}, globals);
  let state = 0, ref = 0; const values = ['synthetic report', imageFiles, false, null], refs = [];
  const Modal = load('src/app/product/[id]/_components/ReportModal.tsx', {
    react:{...react,useContext:()=>({getUser:()=>({id:1000})}),useState:()=>{const index=state++;return [values[index],v=>values[index]=v];},
      useRef:initial=>refs[ref++]??=( {current:initial}),useEffect:()=>{}},
    './ImageUploadBox':()=>null,'@/assets/SVG/Close.svg':()=>null,'../../_api/api':api,
    'next/navigation':{redirect:()=>{}},'@/providers/UserContext':{UserContext:{}},
  },globals).default;
  const render = () => {state=0;ref=0;return Modal({onClose:()=>closes++,data:{id:7,user:{id:2000,nickname:'synthetic'}},userId:2000});};
  function find(node) {if(!node||typeof node!=='object')return; if(node.type==='button')return node; return react.Children.toArray(node.props?.children).map(find).find(Boolean);}
  const text = node => typeof node === 'string'||typeof node==='number' ? String(node) : react.Children.toArray(node?.props?.children).map(text).join('');
  return {api,submit:find(render()).props.onClick,requests,tokens,alerts,closes:()=>closes,button:()=>find(render()),text:()=>text(render()),values};
}
if(!before) {
  for(const status of [200,201]) test(`create ${status} with JSON ID keeps normal success and request contract`,async()=>{
    const f=fixture([new Response('{"reportId":88}',{status})]); await f.submit();
    assert.equal(f.closes(),1);assert.match(f.alerts[0],/성공/);assert.equal(f.requests.length,1);
    assert.equal(f.requests[0].url,'/api/reports');assert.equal(f.requests[0].method,'POST');assert.equal(f.requests[0].headers.Authorization,'synthetic');
    const body=JSON.parse(f.requests[0].body);assert.equal(body.reporterId,1000);assert.equal(body.userId,2000);assert.equal(body.productId,7);assert.equal(body.reportTypeId,1);assert.equal(body.isReport,undefined);
    assert.equal(f.tokens.length,1);
  });
  for(const status of [400,401,403]) test(`create ${status} is rejected and modal remains with feedback`,async()=>{
    const f=fixture([new Response('',{status})]);await f.submit();assert.equal(f.closes(),0);assert.match(f.alerts[0],/접수되지/);assert.equal(f.requests.length,1);assert.equal(f.button().props.disabled,false);
  });
  for(const [label,response] of [
    ['network',new Error('synthetic offline')], ['server500',new Response('',{status:500})],
    ['invalidJson201',new Response('broken',{status:201})],['false200',new Response('false',{status:200})],
    ['null201',new Response('null',{status:201})],['missingId',new Response('{}',{status:201})],
    ['stringId',new Response('{"reportId":"88"}',{status:201})],['zeroId',new Response('{"reportId":0}',{status:201})],
    ['fractionId',new Response('{"reportId":1.2}',{status:201})],['unsafeId',new Response('{"reportId":9007199254740992}',{status:201})],
  ]) test(`${label} is uncertain, never success or automatic repeat`,async()=>{
    const f=fixture([response],[new Blob(['synthetic'])]);await f.submit();await f.submit();
    assert.equal(f.closes(),0);assert.equal(f.alerts.length,0);assert.equal(f.requests.length,1);assert.equal(f.button().props.disabled,true);assert.match(f.text(),/接수|접수 여부를 확인할 수 없습니다/);
  });
  for(const imageBody of ['reportImages upload','"reportImages upload"']) test(`image200 ${imageBody} uses HTTP status without JSON parsing`,async()=>{
    const f=fixture([new Response('{"reportId":88}',{status:201}),new Response(imageBody,{status:200})],[new Blob(['synthetic'])]);await f.submit();
    assert.equal(f.closes(),1);assert.match(f.alerts[0],/성공/);assert.equal(f.requests.length,2);assert.equal(f.requests[1].url,'/api/reportImages/upload');assert.equal(f.requests[1].body.get('reportId'),'88');assert.equal(f.requests[1].body.getAll('files').length,1);assert.equal(f.tokens.length,2);
  });
  for(const [label,response,expected] of [
    ['403',new Response('',{status:403}),'image-failed'],['500',new Response('',{status:500}),'image-unknown'],['network',new Error('synthetic upload loss'),'image-unknown'],
  ]) test(`created report with image ${label} keeps ID and blocks whole-report retry`,async()=>{
    const f=fixture([new Response('{"reportId":88}',{status:201}),response],[new Blob(['synthetic'])]);await f.submit();await f.submit();
    assert.equal(f.closes(),0);assert.equal(f.alerts.length,0);assert.equal(f.requests.length,2);assert.equal(f.values[3].status,expected);assert.equal(f.values[3].reportId,88);
    assert.equal(f.button().props.disabled,true);assert.match(f.text(),/88/);assert.match(f.text(),/다시 제출하지/);
  });
  test('in-flight double click creates and uploads at most once',async()=>{
    let finish;const pending=new Promise(resolve=>finish=resolve);const f=fixture([pending]);
    const first=f.submit();await f.submit();assert.equal(f.requests.length,1);assert.equal(f.button().props.disabled,true);
    finish(new Response('{"reportId":88}',{status:201}));await first;assert.equal(f.closes(),1);
  });
  test('explicit creation denial permits deliberate corrected retry',async()=>{
    const f=fixture([new Response('',{status:403}),new Response('{"reportId":88}',{status:201})]);await f.submit();await f.submit();assert.equal(f.requests.length,2);assert.equal(f.closes(),1);
  });
}
const request = {reportContents:'synthetic',reportTypeId:1,reporterId:1000,userId:2000,productId:7,images:[]};
if(before) {
  test('before false denial is shown as success and closes',async()=>{const f=fixture([new Response('',{status:403})]); await f.submit(); assert.equal(f.closes(),1); assert.match(f.alerts[0],/성공/);});
  test('before successful plain-text image response throws JSON error after report was created',async()=>{
    const f=fixture([new Response('{"reportId":88}',{status:201}),new Response('reportImages upload',{status:200})]);
    await assert.rejects(f.api.createReport({...request,images:[{id:0,imageURL:new Blob(['synthetic'])}]})); assert.equal(f.requests.length,2);
  });
  test('before JSON string image response returns undefined ok',async()=>{
    const f=fixture([new Response('{"reportId":88}',{status:201}),new Response('"reportImages upload"',{status:200})]);
    assert.equal(await f.api.createReport({...request,images:[{id:0,imageURL:new Blob(['synthetic'])}]}),undefined);
  });
}
