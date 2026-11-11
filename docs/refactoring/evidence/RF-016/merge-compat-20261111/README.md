# 2026-11-11 RF016 머지 후 테스트 구성 호환 복구

통합 담당이 확인한 기준 HEAD는 `2091fae`다. PR20이 PR19보다 먼저 통합되면서 변경된 `PointHistoryService` 생성자와 RF016의 테스트 구성이 불일치했다. 이번 작업은 **3단계의 정상 테스트 컴파일을 위해 명시적으로 허용한 범위 밖 긴급 복구**이며 RF016 B 물리 검증의 재개가 아니다. 공통 BACKLOG/LOG 및 RF024/039/040/043/044 문서는 이 작업에서 편집하지 않았다. Git 명령·commit/push, MySQL 시작·접속·쓰기 없이 수행했다.

## 변경과 경계

- B `Rf016MySqlFixture.JpaConfiguration`의 구 3인자 생성자를 현재 7인자 구성으로 맞췄다. 기존 `charge()`는 `updateCost`만 사용하므로 새 product/transaction repository, payment verification gate, settlement repository는 테스트용 Mockito 대역을 명시 주입한다. 업무 메서드 호출은 즉시 `AssertionError`이며 Object 진단 메서드만 기본 대역 동작을 허용한다.
- A 제한 context도 같은 네 의존성이 필요하므로 명시 대역 bean만 추가했다. 기존 A smoke의 매 사례 종료 시 네 대역의 무상호작용을 확인한다. 운영 gate/config와 파일 기반 승인 로더를 등록하거나 실행하지 않는다.
- 공통 helper `Rf016UnusedPaymentCollaborator`와 순수 `Rf016FixtureCompatibilityTest` 3건을 추가했다. 정상 충전의 repository save→기존 observation proxy 전달, 결제 오호출의 포인트 접근 전 실패, 네 collaborator 각각의 실패를 검사한다. 이 단위검증은 실제 fixture constructor를 실행하지 않고 Mockito fixture/EMF/repository를 사용한다. ledger·flush·rollback 물리 검증으로 세지 않는다.
- 기존 `observe`, ledger/journal, host/DB identity, read-only, 소유행·cleanup·경쟁 probe 코드는 변경하지 않았다. JPA managed types는 User/Role/PointHistory뿐이며 실제 payment repository factory나 거래 엔티티를 추가하지 않았다. `unchanged-boundaries.json`은 safety gate/test·read-only test와 운영 PointHistoryService/gate/config의 전후 SHA256 동일성을 기록한다.

## 실패 원본과 복구 후 실행

| 구분 | 실제 결과와 근거 |
|---|---|
| 수정 전 전체 sourceSet 실패 | RF024 작업 중 보존된 최초 원본 `docs/refactoring/evidence/RF-024/before/unrelated-compile-failure.txt`: `Rf016MySqlFixture.java:408`, 생성자 required7/found3로 compileTestJava 실패·테스트 미기동. 잘못된 init 제한 재시도 원본 `docs/refactoring/evidence/RF-024/before/init-ineffective-failure.txt`도 같은 오류다. 해당 원본 파일은 이번 커밋에 미포함이며 이번 작업에서 덮어쓰거나 실패를 재현하지 않았다. |
| 이번 첫 wrapper 실행 | `wrapper-denied-console.txt`, exit1. 기존 Gradle cache `.lck` 접근 거부로 컴파일/테스트 미시작. 별도 보존 후 기존 캐시 접근 상승을 허용받아 같은 선택 명령을 재실행했다. 자동 승인 심사 거부가 아니라 최초 파일 sandbox 접근 실패다. |
| 복구 후 정상 sourceSet | `selected-console.txt`, `selected-exit.txt`, `xml/` 8개, `results.json`: **281건, failures/errors/skipped0, exit0**. compileJava/compileTestJava/processResources/processTestResources/test의 **5 task 실실행**. sourceSet을 제한하는 init script는 사용하지 않았다. |
| RF016 기존 선택 | A 환경1+Boot smoke11=12, B 순수 guard18, 합30. 기존 결과의 소급 대체가 아닌 현재 구성의 새 실행이다. |
| 신규 RF016 경계 | FixtureCompatibility3. RF016 합33이며 물리 JPA/DB는 포함하지 않는다. |
| RF035~037 최소 회귀 | PaymentTrustService42, PaymentVerificationGate163+Configuration15, PaymentAtomicity28=248. 기존 RF035~038 전체942/공동체인6의 재실행이 아니다. |

JDK17.0.10/Gradle8.7의 기존 캐시·의존성만 사용했다. RF024의 별도 작업과 공유 build 실행을 직렬화했고, 이 결과 XML을 복사한 뒤 슬롯을 반납했다. 정상 compileTestJava는 당시 전체 테스트 소스셋을 컴파일하며 다른 작업의 현재 테스트 파일도 포함될 수 있지만 테스트 실행은 아래 8클래스만 선택했다.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
./gradlew.bat test --offline --no-daemon --rerun-tasks `
  --tests com.ngng.api.isolation.Rf016EnvironmentIsolationTest `
  --tests com.ngng.api.isolation.Rf016IsolatedBootSmokeTest `
  --tests com.ngng.api.isolation.mysql.Rf016MySqlSafetyGateTest `
  --tests com.ngng.api.isolation.mysql.Rf016FixtureCompatibilityTest `
  --tests com.ngng.api.user.point.payment.PaymentTrustServiceTest `
  --tests com.ngng.api.user.point.payment.PaymentVerificationGateTest `
  --tests com.ngng.api.user.point.payment.PaymentVerificationConfigurationTest `
  --tests com.ngng.api.user.point.payment.PaymentAtomicityTest
```

## 보존과 한계·재개 조건

`source-before/`는 변경 직전 B fixture와 A configuration 두 파일만 보존했다. `before-sha256.json`은 이 둘과 변경하지 않을 경계6개를 기록한다. `source-after/`는 실행 직전 RF016 Java11개이며 실행 후 현재 소스와 SHA256 불일치0을 확인했다. 신규 helper/test를 포함한다. 이는 RF016의 정확한 실행 소스 snapshot이며 전체 저장소·동시 RF024 작업의 snapshot은 아니다.

XML8개와 console2개의 치환 전 원본은 Git ignored `.local-runtime/rf016-raw/20261111-merge-compat/`에 보존했다. 공유본만 workspace/home/사용자 경로를 placeholder로 치환했다. `sharing-manifest.json`은 각 원본/공유 SHA256과 위치를 연결한다. 테스트 수치·실패·timestamp는 바꾸지 않았다. 이 디렉터리의 manifest는 과거 RF016 마감 manifest와 별개이며 과거 증빙을 덮어쓰지 않았다.

**MySQL 시작/접속/쓰기0, 실제 read-only preflight·B JPA/commit/rollback/cleanup·락 경쟁 미실행**이다. 기존 RF016 host identity·권한·쓰기 허용 gate 미충족 상태와 운영 결제 default deny는 유지한다. 전체 backend test/build/assemble, RF035~038 전수 회귀, 실제 외부 서비스·HTTP/브라우저·성능은 이번 범위에서 실행하지 않았다. 선택 mock 및 fake transaction advice 성공을 물리 DB 안전·운영 결제 개방의 근거로 사용하지 않는다.

추가 compile 회귀는 같은 offline 정상 sourceSet 선택 명령으로 재검증하고 새 원시자료를 보존한다. RF016 B는 기존 명세의 안전한 runtime identity/read-only 확인 및 별도 쓰기 범위 허용이 충족되어야 재개한다. 운영 결제는 RF035~038의 별도 DB·writer·배포 검증 조건을 따른다. 이 복구로 잔여 검증을 완료 처리하거나 후속 작업을 자동 시작하지 않는다.
