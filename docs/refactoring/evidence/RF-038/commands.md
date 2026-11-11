# RF038 2026-11-11 실행 명령

Windows PowerShell, 기존 JDK17.0.10·Gradle8.7 캐시만 사용했다. backend 명령은 `NGNG-backend`에서 실행했으며 `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'`를 지정했다. 실행마다 stdout/stderr 및 `$LASTEXITCODE`를 Git 제외 `.local-runtime/rf038-results/<phase>/console.txt`, `exit-code.txt`에 보존했고 XML은 다음 실행 전에 복사했다. 다른 backend Gradle과 직렬화했다.

## before

```powershell
.\gradlew.bat test --offline --no-daemon --rerun-tasks --tests com.ngng.api.transaction.status.TransactionStatusBeforeTest --tests com.ngng.api.transaction.status.TransactionStatusBeforeChainTest
```

최초 sandbox zip.lck 접근 거부는 wrapper-denied에 보존했다. 기존 캐시 접근 승인 후 동일 명령으로20건을 실제 실행했다. before 전용 클래스는 당시 source와 함께 보존하고 운영 테스트 트리에서 제거했다.

## after-initial / after

```powershell
.\gradlew.bat test --offline --no-daemon --rerun-tasks --tests com.ngng.api.transaction.status.TransactionStatusOwnershipTest --tests com.ngng.api.transaction.status.TransactionStatusSecurityChainTest --tests com.ngng.api.transaction.status.TransactionStatusLockQueryTest
```

최초350건, 기존 RF006 장면 보강 후 최종355건이다. 두 실행은 동일 사례가 반복되므로 합산하지 않는다. 최종의 운영 service/repository는 최초 after와 동일하다.

## regression + assemble

```powershell
.\gradlew.bat test --offline --no-daemon --rerun-tasks --tests com.ngng.api.transaction.service.TransactionAcceptancePolicyTest --tests com.ngng.api.transaction.service.TransactionCreationContractTest --tests com.ngng.api.transaction.authorization.TransactionRequestAuthorizationServiceTest --tests com.ngng.api.transaction.authorization.TransactionRequestAuthorizationChainTest --tests com.ngng.api.transaction.read.TransactionReadOwnershipTest --tests com.ngng.api.transaction.read.TransactionReadSecurityChainTest assemble
```

## 공유본 생성·검증

```powershell
node docs/refactoring/evidence/RF-038/prepare-evidence.cjs
```

이미 보존한 raw를 복사·정제하고 raw/shared 및 선택 source SHA256을 구분할 뿐 테스트를 실행하지 않는다. XML의 hostname·개인경로·출력에 등장하는 합성 JWT literal을 정제한다. source는 당시 선택 파일이며 전체 저장소 snapshot이 아니다. 후일 같은 명령 실행은 과거 실행의 원본 증거가 아니다.
