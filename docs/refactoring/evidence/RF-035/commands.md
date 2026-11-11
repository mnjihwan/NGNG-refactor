# RF035 2026-11-11 실제 실행 명령

기존 설치/캐시만 사용했다. 각 backend 명령은 `NGNG-backend`에서 실행하고 stdout/stderr와 `$LASTEXITCODE`를 `.local-runtime/rf035-results/<단계>/console.txt`, `exit-code.txt`에 저장했다. 새 의존성 설치·DB/외부 SDK·전체 test/build는 실행하지 않았다.

## before — 운영 코드 변경 전

```powershell
./gradlew.bat test --offline --no-daemon --rerun-tasks --tests com.ngng.api.user.point.payment.PaymentBeforeTest --tests com.ngng.api.user.point.service.PointHistoryPolicyTest --tests com.ngng.api.user.point.service.PointHistoryBehaviorTest --tests com.ngng.api.user.point.controller.PointControllerContractTest --tests com.ngng.api.user.point.controller.PointResponseSecurityChainTest
```

wrapper의 sandbox 캐시 잠금 접근 거부 뒤 동일 선택 명령의 캐시 접근이 허용되어 실행했다. 거부 원문은 `wrapper-denied/`에 보존했다. before 전용 클래스는 실행 뒤 보존하고 `src/test`에서 제거했다.

## after — 최종 포인트 선택 179건

```powershell
./gradlew.bat test --offline --no-daemon --rerun-tasks --tests com.ngng.api.user.point.payment.PaymentTrustServiceTest --tests com.ngng.api.user.point.payment.PaymentTrustSecurityChainTest --tests com.ngng.api.user.point.payment.PaymentLockQueryContractTest --tests com.ngng.api.user.point.service.PointHistoryPolicyTest --tests com.ngng.api.user.point.service.PointHistoryBehaviorTest --tests com.ngng.api.user.point.controller.PointControllerContractTest --tests com.ngng.api.user.point.controller.PointResponseSecurityChainTest --tests com.ngng.api.user.point.repository.PointHistoryRepositoryBatchTest
```

`after-initial/`은 위 목록에서 `PaymentLockQueryContractTest`만 제외한 최초 실행이다. 이후 status parameter 4건과 query 3건을 추가한 실행은 `query-fixture-failure/`에 보존했고, 최종은 `after/`다. 각 실패 원인과 결과는 README를 따른다.

## regression — 별도 317건·assemble

```powershell
./gradlew.bat test --offline --no-daemon --rerun-tasks --tests com.ngng.api.transaction.service.TransactionAcceptancePolicyTest --tests com.ngng.api.transaction.service.TransactionCreationContractTest --tests com.ngng.api.transaction.authorization.TransactionRequestAuthorizationServiceTest --tests com.ngng.api.transaction.authorization.TransactionRequestAuthorizationChainTest --tests com.ngng.api.transaction.read.TransactionReadOwnershipTest --tests com.ngng.api.transaction.read.TransactionReadSecurityChainTest --tests com.ngng.api.global.security.TokenFilterTest --tests com.ngng.api.global.security.SecurityChainTest assemble
```

## frontend — 루트에서 실제 TypeScript API/handler와 대역 실행

```powershell
node --test NGNG-frontend/tests/rf035-payment-trust.test.cjs NGNG-frontend/tests/rf008-point-response.test.cjs
```

동일 명령의 최초 결과는 `after-initial/frontend.txt`, 기대 알림 문구만 보정한 최종 결과는 `after/frontend.txt`다. 새 테스트는 RF003의 실제 API/handler 오프라인 실행 방식을 사용하고 HTTP 400/401/403/404/409별 헤더 처리·generic 알림·후속 상태 변경 0을 추가했다. 기존 RF008 응답 4건과 함께 최종 19건이다.

## 보존 자료 정제

```powershell
node docs/refactoring/evidence/RF-035/prepare-evidence.cjs
```

이미 보존된 raw를 복사·정제할 뿐 테스트를 재실행하지 않는다. 당시 source/hash, 미정제 raw/hash, 정제 공유본/hash를 구분한다. 최초 Python launcher 접근 불가와 metadata 수집의 Oracle java shim 무출력·중단은 대화 도구 결과에만 남고 테스트 실행 결과로 집계하지 않는다. Java 정보는 실제 Gradle에서 사용한 `JAVA_HOME`의 JDK release 파일을 읽어 보존했다.
