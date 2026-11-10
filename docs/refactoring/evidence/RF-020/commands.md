# RF020 실제 실행 명령

2026-11-10, Windows PowerShell의 NGNG-backend에서 실행했다. 아래는 현재 결과 기록이며 재실행 결과를 소급 대체하지 않는다. 기존 Gradle cache 쓰기는 sandbox escalation 승인, 의존성 조회는 --offline으로 차단했다. stdout/stderr는 `2>&1 | Tee-Object -FilePath ../docs/refactoring/evidence/RF-020/<단계>-console.txt; exit $LASTEXITCODE`로 보존했다.

## 변경 전14건

```powershell
.\gradlew.bat --offline test --tests 'com.ngng.api.user.user.confirmation.ConfirmAccountSecurityChainTest' --rerun-tasks
```

첫 wrapper sandbox 실패(test 미기동), 테스트 compile2회 실패 후 위 명령으로14건 통과했다. `before/`의 당시 소스로 해석한다.

## 변경 후170건

```powershell
.\gradlew.bat --offline test --tests 'com.ngng.api.user.user.confirmation.ConfirmAccountServiceTest' --tests 'com.ngng.api.user.user.confirmation.ConfirmAccountSecurityChainTest' --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest' --rerun-tasks
```

첫170건 중 fixture restub NPE1실패 후 fixture 수정한 재실행170건 통과. 실패/성공 XML과 console을 구분해 보존했다.

## 최종317건 및 패키징

```powershell
.\gradlew.bat --offline test --tests 'com.ngng.api.user.user.confirmation.ConfirmAccountServiceTest' --tests 'com.ngng.api.user.user.confirmation.ConfirmAccountSecurityChainTest' --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest' --tests 'com.ngng.api.product.authorization.ProductAuthorizationServiceTest' --tests 'com.ngng.api.product.authorization.ProductAuthorizationSecurityChainTest' --tests 'com.ngng.api.product.authorization.ProductAuthorizationCacheTest' --tests 'com.ngng.api.user.point.controller.PointResponseSecurityChainTest' --tests 'com.ngng.api.transaction.status.TransactionStatusOwnershipTest' --tests 'com.ngng.api.transaction.status.TransactionStatusSecurityChainTest' --rerun-tasks
.\gradlew.bat --offline assemble
```

최종317건 fail/error/skip0, assemble exit0. 외부 DB/Redis/SMTP/SMS 및 전체 Boot test/build·브라우저·성능 검증은 아니다. `git diff --check`와 RF020 production diff를 확인했다. 신규 의존성/스키마/commit/push는 없다.
