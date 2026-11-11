# RF036 2026-11-11 실제 실행 명령

기존 설치/캐시를 사용했고 backend 명령은 `NGNG-backend`에서 실행했다. stdout/stderr와 `$LASTEXITCODE`를 Git 제외 `.local-runtime/rf036-results/`에 보존했다. 최초 잘못된 상대 로그 경로 준비 오류는 도구 대화에만 있다. wrapper 캐시 접근 거부 뒤 같은 캐시 접근이 허용되어 실행했다. 의존성 설치/DB/외부 서비스/전체 test·build는 하지 않았다.

첫 정상 sourceSet 명령은 아래이며, 다른 담당의 일시적인 StatusFixture 호환 실패로 compileTestJava39오류/테스트0건이었다. `unit-initial/`에 wrapper 실패와 캐시 허용 후 실패를 각각 보존했다.

```powershell
./gradlew.bat test --offline --no-daemon --tests com.ngng.api.user.point.payment.PaymentVerificationGateTest --tests com.ngng.api.user.point.payment.PaymentVerificationConfigurationTest
```

타 담당 테스트 변경과 분리하려고 `isolated-tests.init.gradle`에서 build directory를 `.local-runtime/rf036-build`로 옮기고 두 RF036 테스트만 compileTestJava 대상에 포함했다. 첫 명령은 `test assemble ... --tests ...`로 적어 assemble이 --tests를 지원하지 않는 CLI 오류/테스트0건이었다. 수정한 실제 성공 명령은 아래다. 이 결과는 전체 테스트 컴파일 증명이 아니며 뒤 정상 sourceSet 최종 실행으로 별도 검증했다.

```powershell
./gradlew.bat test --offline --no-daemon --init-script ../.local-runtime/rf036-results/isolated-tests.init.gradle --tests com.ngng.api.user.point.payment.PaymentVerificationGateTest --tests com.ngng.api.user.point.payment.PaymentVerificationConfigurationTest assemble
```

fixture 일관성 회복 뒤 최종 정상 sourceSet 명령:

```powershell
./gradlew.bat test --offline --no-daemon --rerun-tasks --tests com.ngng.api.user.point.payment.PaymentVerificationGateTest --tests com.ngng.api.user.point.payment.PaymentVerificationConfigurationTest assemble
```

위 두 성공 실행은 같은178개 테스트이며 합산하지 않는다. raw XML은 각각 실행 직후 `unit-isolated/xml`, `unit-final/xml`에 복사해 후속 다른 담당 Gradle 실행으로 덮어쓰이지 않게 했다. JVM release metadata는 JAVA_HOME의 release에서 버전/구현사/arch만 기록하고 개인 경로는 공유하지 않는다.

정제/복사는 저장소 루트에서 아래 명령으로 실행한다. 이미 보존한 raw를 복사하며 테스트 재실행이 아니다.

```powershell
node docs/refactoring/evidence/RF-036/prepare-evidence.cjs
```
