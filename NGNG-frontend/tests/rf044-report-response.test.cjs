const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const react = require('react');
const {renderToStaticMarkup} = require('react-dom/server');
function load(relative, deps, globals = {}) {
  const filename = path.resolve(__dirname, '..', relative);
  const code = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {fileName:filename,
    compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText;
  const module = {exports:{}};
  vm.runInNewContext(code,{module,exports:module.exports,...globals,require(name){
    if(name==='react/jsx-runtime')return require(name);
    if(Object.hasOwn(deps,name))return deps[name];
    throw new Error(`Unexpected dependency ${name}`);
  }},{filename}); return module.exports;
}
const summary = id => ({userId:id,name:`fixture-name-${id}`,nickname:`fixture-nick-${id}`});
const report = () => ({reportId:88,reportContents:'fixture-contents',reportType:{reportTypeId:1,reportType:'fixture-type'},
  reporter:summary(1000),user:summary(2000),isReport:null,createdAt:'2026-01-02T03:04:05.123+00:00',updatedAt:null,
  productId:7,privateChatId:9,visible:null,reportImages:[{reportImageId:4,imageUrl:'https://example.test/fixture.png',contentType:'IMAGE',createdAt:null,updatedAt:null}]});
function nodes(tree, predicate) {
  const found=[]; function visit(node){if(!node||typeof node!=='object')return;if(predicate(node))found.push(node);
    react.Children.toArray(node.props?.children).forEach(visit);} visit(tree); return found;
}
const text = tree => typeof tree==='string'||typeof tree==='number'?String(tree):react.Children.toArray(tree?.props?.children).map(text).join('');
test('eight-field creation response keeps reportId image upload consumption',async()=>{
  const requests=[];const value=report();const created=Object.fromEntries(['reportId','reportContents','reportType','isReport','createdAt','updatedAt','productId','privateChatId'].map(key=>[key,value[key]]));
  const responses=[new Response(JSON.stringify(created),{status:201}),new Response('reportImages upload',{status:200})];
  const api=load('src/app/product/_api/api.ts',{'@/app/my_page/_utils/auth-header':{getAccessToken:()=>'synthetic',setAccessToken(){}},
    dayjs:{extend(){}},'dayjs/plugin/utc':{}},{FormData,fetch:async(url,options)=>{requests.push({url,...options});return responses.shift();}});
  const result=await api.createReport({reportContents:'fixture',reportTypeId:1,reporterId:1000,userId:2000,productId:7,
    images:[{id:0,imageURL:new Blob(['fixture'])}]});
  assert.equal(result.status,'created');assert.equal(result.reportId,88);assert.equal(requests.length,2);
  assert.equal(requests[1].url,'/api/reportImages/upload');assert.equal(requests[1].body.get('reportId'),'88');assert.equal(requests[1].body.getAll('files').length,1);
});
function harness(relative, responses, seed=[]) {
  const states=[...seed],effects=[],requests=[],tokens=[],alerts=[]; let index=0;
  const Link = ({children,...props})=>react.createElement('a',props,children);
  const DropDown=()=>null, Player=()=>null;
  const deps={react:{...react,useState:initial=>{const n=index++;if(!(n in states))states[n]=initial;return[states[n],v=>states[n]=typeof v==='function'?v(states[n]):v];},useEffect:fn=>effects.push(fn)},
    'next/link':Link,'next/navigation':{useRouter:()=>({back(){}})},
    '@/components/common/drop_down/DropDown':DropDown,
    'react-responsive-carousel/lib/styles/carousel.min.css':{},'react-responsive-carousel':{Carousel:({children})=>react.createElement('div',null,children)},'react-player':Player,
    './_utils/auth-header':{getAccessToken:()=>'synthetic'},'../_utils/auth-header':{getAccessToken:()=>'synthetic'},
    '@/app/my_page/_utils/auth-header':{setAccessToken:res=>tokens.push(res)},
    '@/components/layouts/admin_menu/design/SVG/refresh.svg':()=>null,'@/components/layouts/admin_menu/design/SVG/check_report.svg':()=>null};
  const Component=load(relative,deps,{fetch:async(url,options)=>{requests.push({url,...options});const body=responses.shift();assert.notEqual(body,undefined);return new Response(JSON.stringify(body),{status:200});},alert:value=>alerts.push(value)}).default;
  const render=()=>{index=0;effects.length=0;return Component({params:{reportId:88}});};
  const flush=async()=>{const pending=[...effects];effects.length=0;pending.forEach(fn=>fn());await new Promise(resolve=>setImmediate(resolve));};
  return{states,render,flush,requests,tokens,alerts,DropDown,Player};
}
test('admin list fetches Page and renders names, nicknames, dates, status and links using three-field users',async()=>{
  const f=harness('src/app/admin/reports/page.tsx',[{content:[report()],pageable:{pageNumber:1,pageSize:5},totalPages:3}]);
  f.render();await f.flush();const tree=f.render(),html=renderToStaticMarkup(tree);
  for(const value of ['fixture-name-1000','fixture-nick-1000','fixture-name-2000','fixture-nick-2000','2026-01-02','fixture-type','False'])assert.ok(html.includes(value));
  assert.equal(f.requests[0].url,'/api/admin/reports?page=0&unprocessedOnly=false');assert.equal(f.states[2],1);assert.equal(f.states[3],3);assert.equal(f.states[4],5);
  assert.equal(nodes(tree,n=>n.props?.href==='/admin/reports/88').length,1);assert.equal(f.tokens.length,1);
  nodes(tree,n=>n.type==='input')[0].props.onChange({target:{checked:true}});assert.equal(f.states[0],true);
  const updated=f.render();assert.match(text(updated),/fixture-name-1000/);
});
test('admin detail fetches summary and still posts both userId values for penalties',async()=>{
  const f=harness('src/app/admin/reports/[reportId]/page.tsx',[report(),{}]);f.render();await f.flush();
  let tree=f.render();const html=renderToStaticMarkup(tree);
  for(const value of ['fixture-name-1000','fixture-nick-1000','fixture-name-2000','fixture-nick-2000','fixture-type','2026-01-02','fixture-contents','https://example.test/fixture.png'])assert.ok(html.includes(value));
  assert.equal(f.requests[0].url,'/api/admin/reports/88');
  nodes(tree,n=>n.type===f.DropDown)[0].props.onClickItem({id:2,name:'30일 정지'});
  nodes(tree,n=>n.type==='textarea'&&n.props.onChange)[0].props.onChange({target:{value:'fixture-reason'}});
  tree=f.render();await nodes(tree,n=>n.type==='div'&&text(n)==='적용')[0].props.onClick();
  assert.equal(f.requests[1].url,'/api/admin/penalties');assert.equal(f.requests[1].method,'POST');
  assert.deepEqual(JSON.parse(f.requests[1].body),{userId:2000,reporterId:1000,reason:'fixture-reason',penaltyLevelId:2,reportId:88});
  assert.match(f.alerts[0],/성공/);
});
test('processed detail keeps penalty fetch, selected level and hides Apply',async()=>{
  const value={...report(),isReport:true};const penalty={reason:'fixture-penalty',penaltyLevel:{penaltyLevelId:2}};
  const f=harness('src/app/admin/reports/[reportId]/page.tsx',[value,penalty]);f.render();await f.flush();const tree=f.render();
  assert.equal(f.requests.length,2);assert.equal(f.requests[1].url,'/api/admin/penalties/88');assert.equal(f.states[2].id,2);assert.equal(f.states[3].reason,'fixture-penalty');
  assert.equal(nodes(tree,n=>n.type==='div'&&text(n)==='적용').length,0);
});
test('null user names render while a null user association retains the pre-existing UI failure',()=>{
  const value=report();value.reporter={userId:1000,name:null,nickname:null};
  const f=harness('src/app/admin/reports/[reportId]/page.tsx',[],[value]);assert.doesNotThrow(()=>renderToStaticMarkup(f.render()));
  value.reporter=null;assert.throws(()=>f.render(),{name:'TypeError'});
  const list=harness('src/app/admin/reports/page.tsx',[],[false,[value]]);assert.throws(()=>list.render(),{name:'TypeError'});
});
test('public product reporter.id remains independent from admin reporter.userId',()=>{
  const deps={react:{...react,useState:initial=>[initial,()=>{}],useContext:()=>({getUser:()=>({id:1000})})},
    'react-dom':{createPortal:()=>null},'next/navigation':{useRouter:()=>({})},'next/link':({children})=>react.createElement('a',null,children),'next/image':()=>null,
    '@/components/common/Button':()=>null,'@/assets/SVG/Siren.svg':()=>null,'./AccountAuthenticationWanringModal':()=>null,
    '../../_api/api':{},'./ReportModal':()=>null,'./ImageModal':()=>null,'@/providers/UserContext':{UserContext:{}},
    'react-responsive-carousel/lib/styles/carousel.min.css':{},'react-responsive-carousel':{Carousel:({children})=>react.createElement('div',null,children)},'react-player':()=>null,
    '@/components/layouts/header/Header':{categories:[]}};
  const Component=load('src/app/product/[id]/_components/ProductInfo.tsx',deps).default;
  const value={id:7,user:{id:2000,nickname:'seller'},images:[],reports:[{reporter:{id:1000,name:'reporter',nickname:'nick'},isReport:null}],forSale:true,
    category:{name:'fixture'},status:{name:'fixture'},price:100,tags:[]};
  assert.match(renderToStaticMarkup(Component({data:value})),/이미 신고한 상품입니다/);
  value.reports[0].reporter.id=3000;assert.match(renderToStaticMarkup(Component({data:value})),/신고받은 상품입니다/);
});
