const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const workspace=path.resolve(__dirname,'../../../..'),rawRoot=path.join(workspace,'.local-runtime/rf037-results'),sharedRoot=path.join(__dirname,'implementation');
const digest=data=>crypto.createHash('sha256').update(data).digest('hex');
const relative=file=>path.relative(workspace,file).split(path.sep).join('/');
const clean=text=>text.split(workspace).join('[WORKSPACE]').split(workspace.replaceAll('\\','\\\\')).join('[WORKSPACE]')
 .replace(/C:\\Users\\[^\\\r\n<>" ]+/gi,'C:\\Users\\[USER]')
 .replace(/C:\/Users\/[^/\r\n<>" ]+/gi,'C:/Users/[USER]')
 .replace(/hostname="[^"]*"/g,'hostname="[REDACTED]"')
 .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[SYNTHETIC_TOKEN_REDACTED]');
const sources=[
 'NGNG-backend/src/main/java/com/ngng/api/user/point/service/PointHistoryService.java',
 'NGNG-backend/src/main/java/com/ngng/api/user/point/repository/PaymentSettlementRepository.java',
 'NGNG-backend/src/main/java/com/ngng/api/user/point/payment/PaymentVerificationGate.java',
 'NGNG-backend/src/main/java/com/ngng/api/user/point/payment/PaymentVerificationConfiguration.java',
 'NGNG-backend/src/main/java/com/ngng/api/transaction/repository/TransactionDetailsRepository.java',
 'NGNG-backend/src/main/java/com/ngng/api/transaction/service/TransactionDetailsService.java',
 'NGNG-backend/src/main/java/com/ngng/api/user/point/dto/PaymentPointRequestDto.java',
 'NGNG-frontend/src/app/my_page/_api/api.ts',
 'NGNG-frontend/src/app/my_page/_hooks/useMypageSWR.ts',
 'NGNG-frontend/src/app/my_page/_compoenets/modal/BuyProductDetails.tsx',
 'NGNG-frontend/src/app/my_page/_compoenets/BuyHistory.tsx',
 'NGNG-frontend/tests/rf035-payment-trust.test.cjs',
 ...['payment/PaymentAndStatusChainTest.java','payment/PaymentAtomicityTest.java','payment/PaymentFixture.java','payment/PaymentTrustServiceTest.java','payment/PaymentTrustSecurityChainTest.java','payment/PaymentLockQueryContractTest.java','payment/PaymentVerificationGateTest.java','payment/PaymentVerificationConfigurationTest.java','service/PointHistoryBehaviorTest.java','service/PointHistoryPolicyTest.java','controller/PointControllerContractTest.java','controller/PointResponseSecurityChainTest.java','repository/PointHistoryRepositoryBatchTest.java','integration/PointHistoryMySqlConcurrencyTest.java'].map(x=>'NGNG-backend/src/test/java/com/ngng/api/user/point/'+x),
 ...['TransactionStatusOwnershipTest.java','TransactionStatusSecurityChainTest.java','TransactionStatusLockQueryTest.java','Rf038Fixture.java','StatusChainHarness.java'].map(x=>'NGNG-backend/src/test/java/com/ngng/api/transaction/status/'+x),
];
const sourceHashes=[];
for(const source of sources){const original=fs.readFileSync(path.join(workspace,source));const snapshot=path.join(rawRoot,source.endsWith('/PaymentAndStatusChainTest.java')?'joint-chain-source':'final-source',source);fs.mkdirSync(path.dirname(snapshot),{recursive:true});if(fs.existsSync(snapshot)){if(!original.equals(fs.readFileSync(snapshot)))throw Error('Frozen source changed: '+source);}else fs.writeFileSync(snapshot,original);sourceHashes.push({file:source,snapshot:relative(snapshot),sha256:digest(original)});}
const preserved=[],excluded=[];
function walk(directory){for(const item of fs.readdirSync(directory,{withFileTypes:true})){const input=path.join(directory,item.name);if(item.isDirectory()){if(item.name==='preexisting-stale-xml-not-this-run'){excluded.push({path:relative(input),reason:'Previous build XML copied after wrapper/compile failure; zero tests executed in that failed run.'});continue;}walk(input);}else{const raw=fs.readFileSync(input),name=path.relative(rawRoot,input),target=path.join(sharedRoot,name),shared=Buffer.from(clean(raw.toString('utf8')));fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,shared);preserved.push({raw:relative(input),shared:relative(target),rawSha256:digest(raw),sharedSha256:digest(shared)});}}}
walk(rawRoot);
const finalXml=path.join(rawRoot,'final/xml'),rows=[];const totals={tests:0,failures:0,errors:0,skipped:0};
for(const f of fs.readdirSync(finalXml).filter(x=>x.endsWith('.xml'))){const tag=fs.readFileSync(path.join(finalXml,f),'utf8').match(/<testsuite\b[^>]*>/)[0],row={file:f};for(const k of Object.keys(totals)){row[k]=Number(tag.match(new RegExp(k+'="(\\d+)"'))[1]);totals[k]+=row[k];}rows.push(row);}
const jointTag=fs.readFileSync(path.join(rawRoot,'joint-chain-final/xml/TEST-com.ngng.api.user.point.payment.PaymentAndStatusChainTest.xml'),'utf8').match(/<testsuite\b[^>]*>/)[0];
const jointTotals={};for(const key of ['tests','failures','errors','skipped'])jointTotals[key]=Number(jointTag.match(new RegExp(key+'="(\\d+)"'))[1]);
const metadata={jointChain:{phase:'joint-chain-final',totals:jointTotals,sourcePhase:'joint-chain-source',overlapsOriginal942:false},recordedAt:new Date().toISOString(),sourceHashes,preserved,excluded,finalTests:{totals,classes:rows},frontendFinal:{tests:37,failures:0,skipped:0,phase:'frontend-review-final',typescriptExit:0,lintExit:0},scope:'Synthetic manifest, mocks, actual MVC/security chain and transaction advice; no JDBC, physical locks, DB atomicity or operational approval. First 30 snapshots were captured after backend942/frontend37 while frozen. The additional joint-chain test was captured after its separate6 execution; all production/previous test snapshots stayed identical. Snapshots are selected sources captured after successful execution while frozen, not a whole worktree snapshot. Failed phases lack exact full source snapshots.'};
fs.writeFileSync(path.join(__dirname,'verification.json'),JSON.stringify(metadata,null,2)+'\n');console.log(JSON.stringify({sources:sourceHashes.length,files:preserved.length,excluded:excluded.length,totals}));
