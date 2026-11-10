# RF009 실행 명령과 범위

2026-11-10 Windows PowerShell, `NGNG-backend`에서 실행했다. 기존 Gradle cache 쓰기는 sandbox escalation 승인을 사용했고 네트워크 의존성 해결은 `--offline`으로 차단했다. 아래 명령은 실제 실행의 기록이며 후일 재실행 결과와 구분한다. 전체 test/build·Boot 서버·DB/Redis 실행 명령은 수행하지 않았다.

## before

```powershell
.\gradlew.bat --offline test --tests 'com.ngng.api.product.cache.ProductCacheInvalidationTest' --tests 'com.ngng.api.product.authorization.ProductAuthorizationCacheTest' --rerun-tasks
```

11건 통과 당시 테스트/fixture3개는 `before/`에 보존했다. 운영 소스 기준은 HEAD `6438b969e3901d14bdd519c16de26c44d51ba68a`다. 첫 sandbox 시도는 wrapper lock 접근 거부로 실행하지 못했으며 승인 뒤 위 명령으로 통과했다.

## 초기 after

```powershell
.\gradlew.bat --offline test --tests 'com.ngng.api.product.cache.*' --tests 'com.ngng.api.product.authorization.ProductAuthorizationCacheTest' --rerun-tasks
```

작성한 격리 cache 패키지만 검토해 wildcard로 선택했다. 최초 실행은 신규 테스트 compile2오류로 실패, 수정 뒤63건 통과했다. 이후 최종 선택은 아래 명시 클래스 이름을 사용했다. console은 각 파일에 `2>&1 | Tee-Object -FilePath ...`로 보존하고 `$LASTEXITCODE`를 반환했다.

## 최종 선택 회귀

```powershell
.\gradlew.bat --offline test --tests 'com.ngng.api.product.cache.ProductCacheInvalidationTest' --tests 'com.ngng.api.product.cache.ProductCacheInvalidatorTest' --tests 'com.ngng.api.product.cache.ProductCacheConcurrencyBoundaryTest' --tests 'com.ngng.api.product.authorization.ProductAuthorizationServiceTest' --tests 'com.ngng.api.product.authorization.ProductAuthorizationCacheTest' --tests 'com.ngng.api.product.authorization.ProductAuthorizationSecurityChainTest' --tests 'com.ngng.api.transaction.service.TransactionAcceptancePolicyTest' --tests 'com.ngng.api.transaction.service.TransactionCreationContractTest' --tests 'com.ngng.api.transaction.status.TransactionStatusOwnershipTest' --tests 'com.ngng.api.transaction.status.TransactionStatusSecurityChainTest' --tests 'com.ngng.api.user.point.controller.PointControllerContractTest' --tests 'com.ngng.api.user.point.controller.PointResponseSecurityChainTest' --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest' --rerun-tasks
```

첫 선택405건은 direct no-tx 수락 테스트 가정1건이 실패했다. 기존 MANDATORY 경계를 유지하도록 테스트를 수정한 retry에서는 `com.ngngng.api.transaction.status.TransactionStatusSecurityChainTest`라는 오타로 그13건이 선택되지 않아392건만 통과했다. 정확한 위 명령으로405건 전체를 다시 실행했고 fail/error/skip0이다. 이력 XML/console을 각각 보존했다.

## 패키징·환경

```powershell
.\gradlew.bat --offline assemble
.\gradlew.bat --offline dependencyInsight --dependency spring-tx --configuration testRuntimeClasspath
.\gradlew.bat --offline --version
```

모두 exit0. dependencyInsight는 Spring tx6.1.5 확인이며 의존성을 추가/업데이트하지 않는다. assemble은 서버 기동/외부 연결 또는 전체 테스트 성공의 근거가 아니다. 소스 SHA256·공유 XML 비식별화 hash는 각 manifest를 참조한다.
