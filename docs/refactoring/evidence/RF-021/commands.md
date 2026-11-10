# RF021 실제 실행 명령

2026-11-10 PowerShell, backend 명령은 NGNG-backend, frontend 명령은 저장소 루트 또는 명시된 frontend 디렉터리에서 수행했다. 기존 캐시 접근에 sandbox escalation 승인을 사용했다. 신규 의존성·실DB/Redis·외부 client·Boot scan/서버 실행 없음. 명령을 다시 실행하면 현재 상태의 새 결과이지 과거 before의 재현이 아니다.

before:
```powershell
.\gradlew.bat --offline --no-daemon test --tests 'com.ngng.api.transaction.authorization.TransactionRequestAuthorizationChainTest' --rerun-tasks
```

after-initial과 최종 after는 같은 아래 selector를 사용했다(최종은 fixture 보정·chain 확장 뒤232건).
```powershell
.\gradlew.bat --offline --no-daemon test --tests 'com.ngng.api.transaction.authorization.TransactionRequestAuthorizationChainTest' --tests 'com.ngng.api.transaction.authorization.TransactionRequestAuthorizationServiceTest' --tests 'com.ngng.api.transaction.service.TransactionAcceptancePolicyTest' --tests 'com.ngng.api.transaction.service.TransactionCreationContractTest' --tests 'com.ngng.api.transaction.status.TransactionStatusOwnershipTest' --tests 'com.ngng.api.transaction.status.TransactionStatusSecurityChainTest' --tests 'com.ngng.api.product.cache.ProductCacheInvalidationTest' --tests 'com.ngng.api.product.cache.ProductCacheInvalidatorTest' --tests 'com.ngng.api.product.cache.ProductCacheConcurrencyBoundaryTest' --rerun-tasks
.\gradlew.bat --offline --no-daemon assemble
```

PowerShell `*> ../docs/refactoring/evidence/RF-021/<run>/console.txt`로 stdout/stderr를 보존하고 `$LASTEXITCODE`를 확인했다. 각 test가 끝난 뒤 build/test-results/test의 해당 XML을 복사하고 다음 담당자에게 Gradle 종료를 통지했다. RF023 compile-failure는 병행 담당자의 실행에서 RF021 factory 편집 오류가 검출된 것이며 로그를 복사했다. 원본의 범위를 RF021 테스트 실행 결과로 재집계하지 않는다.

frontend (루트):
```powershell
node --test NGNG-frontend/tests/rf021-transaction-request-create.test.cjs NGNG-frontend/tests/rf004-transaction-request.test.cjs
```
같은 명령의 초기 실패/최종 성공을 frontend-initial.txt/frontend.txt에 보존했다.

frontend (NGNG-frontend):
```powershell
npm run lint -- --file src/app/chat/_api.ts --file src/app/chat/_hooks/usePrivateChatMessgae.ts --file "src/app/chat/[roomId]/PrivateChat.tsx"
./node_modules/.bin/tsc.cmd --noEmit --incremental false
```

원문 정제는 루트에서 `node docs/refactoring/evidence/RF-021/prepare-evidence.cjs`로 실행했다. 최초 원문은 ignored `.local-runtime/rf021-raw`에 보존하며 script 재실행이 원문을 공유본으로 덮어쓰지 않는다. 개인 경로/hostname 치환·행끝공백 정리만 했고 이번 JWT 치환 대상은0이다. 실제 DB·전체 test/build·frontend production build·성능 측정은 실행하지 않았다.
