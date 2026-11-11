# RF-016 증빙 및 실행 한계

2026-11-10 분석·설계 단계. [명세](../../tasks/RF-016.md), [백로그](../../BACKLOG.md), [1B 재평가](../../BACKLOG_REASSESSMENT_2026-11-10.md). 기준 HEAD `82aa7279886385bc0974ae78679ab492447f4135`, 시작 Git 상태 `?? .tmp/`; 기존 사용자 파일을 변경하지 않았다.

## 수행한 검증과 근거

루트 AGENTS/README/TEMPLATE/BACKLOG/재평가, RF001~009·020/022/023/041/042 증빙의 완료 범위와 잔여를 확인했다. `git status --short`, `git rev-parse HEAD`, `git ls-files`, `rg --files`, `rg -n`, `Get-Content`로 기존 Boot 테스트 9개, 사용자/포인트/JWT 체인 대역, MySQL RF003/004 fixture, JoinService, EmailAuthService, PhoneNumberAuthService, Redis/ES/AWS configuration 및 build 의존성을 조사했다. test application.yml/local 예제에서 설정 key와 DDL/발송 플래그만 확인하고 실제 자격값은 기록하지 않았다.

- test resources의 git 미추적 application.yml `ddl-auto:update`와 기본 Boot 탐색은 미실행 전체 suite 위험의 직접 코드 근거다. user 이메일 발송 테스트는 활성, SMS 메서드는 @Test 없음, SDK 생성자는 원격 domain을 고정한다.
- 기존 RF008 등 실제 Security/MVC+repository 대역은 기능 경계 참고이며 전체 Boot/JPA 통과 증거가 아니다. RF003/004의 과거 identity/cleanup은 설계 근거이고 현 환경 확인이 아니다.
- `Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue`에서 선택 포트 13306/16379/19200/1025/9000/9001 출력 없음. 조회 오류를 억제했으므로 listener 부재·안전함을 확정하지 않는다. `Get-CimInstance Win32_Process -Filter name=mysqld.exe`는 CIM 접근 불가. 현재 process/datadir/DB identity 미확인이다. 접속/서버 구동/쓰기 없음.

원시 shell 출력은 대화에만 보존되며 별도 raw 파일/XML/DB snapshot은 생성하지 않았다. 현재 실행 테스트 수는 0이다. 이 문서는 실행 원본을 대신하지 않고 후일 실행은 새 날짜/환경/HEAD/명령/실패·skip·UP-TO-DATE 구분과 함께 추가한다. 기존 성공 수치를 RF016 결과로 합산하지 않는다.

## 단계별 미실행과 재개 조건

| 범위 | 현재 판정/이유 | 추가 검증·재개 조건 |
|---|---|---|
| A 명시 Boot context·합성 사용자/포인트/인증·메일 대역·guard | 설계 완료, 구현/실행 미수행 | 새 test-only classes 명시, 기본 config/auto-config 차단, 선택 offline 실행·XML/console 및 독립 리뷰 |
| B DB identity·소유 fixture·commit/rollback·cleanup | 미검증, 현재 대상 identity 미확인 | 명세의 대상/행 범위 보고와 현재 지시 범위 판단→listener/process/datadir→허용된 읽기 identity→DDL 없는 제한 JPA→소유행 검증·정리·독립 사후 조회 |
| 기존 9 Boot 전체 suite | 미실행, 고정 데이터/외부 자원 위험 | 묶음별 안전 설정/fixture 완성 이후, A만 통과해도 전체 완료 아님 |
| Redis/ES/상품/거래/chat/SMTP/SMS/S3 실연동·HTTP/브라우저 | 최소 범위 밖 및 대상 미확인 | 별도 묶음과 정확한 소유 key/행 정리·발송 차단 계획 |
| 성능 | 미측정/유예 | 사용자 재개 지시 및 대표 전후 기준선; 테스트 경과시간은 성능 자료 아님 |

신규 의존성/스키마/인증정책 변경은 계획하지 않았다. 기존 승인 범위와 환경 안전을 구분한다. 과거 격리 DB 승인은 현재 동일 대상/상태를 보증하지 않으나, 작업 번호 변경만으로 기존 1B 일반 기능검증 요청을 취소하거나 새 승인 필수로 해석하지 않는다. 물리 연결 전 구체 범위와 미확인 identity를 보고하고 후속 판단한다. 독립 명세 리뷰 결과와 실제 구현 담당의 실행 결과는 이후 별도 절로 추가한다.

## 독립 계획 리뷰 반영

공통 독립 리뷰는 A 착수 가능, B는 환경 gate 확인 뒤 가능으로 판정했다. 다음은 명세 보완이며 실행 결과가 아니다.

- B 정상 service 호출을 테스트 외곽 transaction 없이 종료한 후 독립 연결로 commit을 확인한다. 실제 INSERT/flush 뒤 관찰 경계에서 의도 실패를 발생시킨 rollback 사례는 service transaction 종료 후 독립 연결로 행 미반영·기존 값 불변을 확인한다.
- `updateCost`가 생성하는 point도 실제 repository 위임 관찰 시 생성 PK/user ID/삽입 불변값을 commit 전 ledger에 기록한다. 사용자 하위 전체 행을 사후 소유행으로 간주하지 않으며, 추적 불명·미추적 참조·commit 결과 불명은 cleanup 중단 조건이다.
- A 실제 JWT/filter+repository 대역과 B 실제 JPA+합성 인증은 별개다. 두 통과 결과를 JWT→실DB 통합으로 합산하지 않는다.
- 외부 config import/additional-location/profile·환경/시스템 property와 전이적 auto-config 유입을 refresh 전 차단하고 후속 bean/source 검사는 회귀 탐지로 사용한다. OS 수준 네트워크 차단 증거와 구분하며 출처 검증 실패 또는 B identity 실패 시 실행을 보류한다.

독립 리뷰의 계획 판단과 향후 구현·실행 최종 리뷰를 구분한다. 위 항목의 원시 실행 자료는 아직 없으며 후일 실행 시 별도 보존한다.

## 2026-11-10 최신 실행 결과: A 12 + B 순수 gate 18

위 내용은 분석 당시 이력이다. 구현 담당의 최신 기준은 동일 HEAD `82aa7279886385bc0974ae78679ab492447f4135`, Java17.0.10, Gradle8.7 기존 캐시·기존 의존성이다. 운영 소스/기본 설정/의존성/schema/인증정책은 변경하지 않았고 Git commit/push도 하지 않았다. test-only Java 9개(A4/B5), 고유 properties, RF016 문서/증빙/준비 runner만 본 작업 범위다. 기존 `.tmp`의 읽기 해시와 마지막 재확인 자료는 `a-local/existing-tmp-*`에 보존했다.

| 실제 수행 | 결과·근거 | 해석 한계 |
|---|---|---|
| 제한 Boot 환경 guard 1 + 실제 MVC/Security/JWT/사용자·포인트·메일 smoke 11 | **A12, fail/error/skip0**. `a-local/final`의 XML2개/console/4 Java snapshot/properties/hash. 잘못된/만료 인증401, 사용자 조회, 포인트 최신/이력/충전, 메일 중복409·메모리 발송, SMS service 대역, 등록 bean/source와 실제 context 종료 확인 | repository/미사용 collaborator/login manager/메일 sender/SMS는 대역. transaction manager·실DB·외부 호출 없음. 전체 Boot/HTTP 서버/로그인/메일·SMS 실제 SDK 전송을 검증하지 않음 |
| B 순수 safety gate18 | **18, fail/error/skip0**. `b-prepared/offline` XML/console, `b-prepared/source` snapshot. missing opt-in의 실제 fixture constructor 조기 거부, 고정 endpoint/host·DB metadata, 불확실 commit·PK/identity/참조/journal 실패, global metadata 권한·partial revoke·서버 flags/runtime·read-only/write opt-in 구분 | fake metadata/문자열/메모리 ledger를 이용한 검증. 실제 process/DB privilege/schema/JPA transaction/락/cleanup의 성공이 아님 |
| test compilation 및 선택 3클래스 실행 | 최종 **30건, fail/error/skip0, exit0**, compileJava/compileTestJava/test 등5 task 실실행. `final-summary.json` | 전체 suite 및 기존 Boot/MySQL test는 선택하지 않음. B 물리/읽기 preflight test는 컴파일만 함 |
| assemble | 첫 명령 exit0·5 UP-TO-DATE 이력 보존 후 `--rerun-tasks` 재실행 **exit0·5 task 실실행**. `b-prepared/offline/assemble-console.txt` | 패키징 성공이며 앱/JPA/실DB/전체 build 통합 검증 아님 |
| runner/host 읽기 | PowerShell parse 오류0, Inspect 실행. 상승 읽기에서 기존 MySQL80 PID4716→자식5544, 3306/33060 listener 확인. 서비스 설정 datadir는 승인 private target과 다르나 runtime commandline/executable 가시성 없음 | 제한 sandbox의 process0은 host 부재를 증명하지 못했다. 실제 process/datadir identity gate는 미충족. DB 연결0·새 서버 시작0·fixture INSERT/DELETE0 |

최종 선택 명령은 아래와 같으며 실제 명령·결과·미선택 여부는 `final-summary.json`에도 기록했다. Gradle cache lock 접근은 이 선택 실행/패키징만 허용된 sandbox 상승을 사용했고 네트워크 의존성 설치는 하지 않았다.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
./gradlew.bat test --offline --no-daemon --rerun-tasks --tests com.ngng.api.isolation.Rf016EnvironmentIsolationTest --tests com.ngng.api.isolation.Rf016IsolatedBootSmokeTest --tests com.ngng.api.isolation.mysql.Rf016MySqlSafetyGateTest
./gradlew.bat assemble --offline --no-daemon --rerun-tasks
```

### 실패·수정·중간 성공 이력과 원시 자료

- `a-local/initial`: wrapper-cache lock의 sandbox 거부 console, 최초 Boot 실행은 환경 unit1 통과/Boot 초기화1 실패. loader.configureEnvironment만으로는 Boot test의 뒤늦은 PrepareEnvironmentListener가 다시 넣은 합성 poison import를 제거하지 못했다. 원본 test application.yml을 읽거나 실제 외부 import에 접속한 실패가 아니다. source/XML/console/hash를 보존했다.
- `a-local/second`: 실제 사례12개는 통과했지만 수동 context.close가 Spring afterTestClass와 충돌해 executionError1로 전체 실패(총13/실패1). 원시 source/XML/console 보존. DirtiesContext가 종료하고 그 뒤 listener가 close event/inactive를 검증하게 고쳤다.
- `a-local/first-success`: 위 수정 뒤12개 성공 원본. expired fixture에 Bearer 접두사가 없어 당시엔 형식 거부를 검증했다. 이를 실제 만료 검증으로 소급하지 않으며 접두사를 수정한 최신 실행/소스는 `a-local/final`이다.
- `b-prepared/offline-first`: 당시 A12+B13=25 통과 원시 XML/console. 그 B source는 보완 전에 별도 snapshot을 남기지 못한 한계가 있다. 이후 source로 당시 결과를 대체하지 않는다.
- `b-prepared/offline-journal`: A12+B14=26 통과와 해당 source snapshot. `offline-review`: A12+B16=28 통과와 해당 snapshot. `offline-before-readonly`: A12+B17=29 통과 및 snapshot. 추가 지적을 반영한 최신은 `source`/`offline`의30건이며 중간 성공을 최종 소스로 오인하지 않는다.
- A bean inventory와 config source, 실제 context-close 확인은 최신 Boot XML stdout에 보존했다. 사용자/token/메일 인증번호 원문을 출력하는 assertion/MockMvc print는 사용하지 않았다. 원시 자료/소스별 해시는 manifest에 기록하고 재실행 결과와 과거 원본을 구분한다.

### 서버 시작 승인·guard 중단 이력

최초 Start의 외부 datadir 쓰기 상승 요청은 자동 승인 심사가 "구체 외부 datadir 서버 시작에 대한 사용자 명시 재승인 없음"으로 거부했다. 그 명령은 실행되지 않았고 우회하지 않았다(`start-auto-review-rejected.json`). 이후 사용자가 **정확한 기존 executable/private datadir의 서버 시작 및 read-only preflight만** 명시 승인했다. 새 승인 근거의 동일 요청은 허용됐으나 runner 재검사에서 mysqld2개를 발견해 **Start 전에** 중단했다. 프로세스 종료/새 서버 시작/DB 연결은 없었다.

상승 읽기에서 MySQL80 서비스 PID4716, 부모1432 및 자식 mysqld5544를 확인했고 PID5544의 0.0.0.0:3306/33060 listener를 확인했다. 13306은 없었다. 현재 서비스 defaults-file의 datadir 항목1개는 승인 target과 다르지만 실제 executable/commandline은 가시성이 없어 runtime 동일성 확인을 대체하지 않는다. `start-host-gate-stopped.json`, `host-service-readonly.json`, `service-defaults-readonly.json`에 비밀/commandline 원문 없이 보존했다. guard를 완화하거나 재시도하지 않았다.

### 남은 범위와 재개 조건

**RF016 전체·B 물리 검증은 미완료다.** [B 준비 상세](b-prepared/README.md)의 metadata 가시성/직접 global grant·partial revoke 부재·runtime replication/scheduler·명시 host args·소유 journal/정확 cleanup/range-lock 경쟁 probe는 준비 코드이며 실제 통과가 아니다. 현재는 기존 mysqld runtime identity를 확인할 안전한 읽기 수단을 확보하고 승인된 private datadir와 무관함을 독립적으로 확인해야 한다. 현재 사용자는 fixture INSERT/DELETE/경쟁 probe를 승인하지 않았으며 서버/read-only 범위만 승인했다.

ReadOnlyPreflight는 별도 opt-in/class/mode이며 매 새 연결에서 server-side `TRANSACTION READ ONLY`와 @@transaction_read_only를 확인한다. constructor의 SELECT/SHOW gate만 실행하고 JPA/fixture/cleanup 경로를 금지한다. 이 경로도 현재 host guard 때문에 미실행이다. 권한 부족·메타데이터 불명·예상 밖 상태는 데이터 쓰기 없이 중단하며 grant/schema/role 생성 fallback은 없다.

물리 commit/rollback/cleanup 및 경쟁 probe를 실제 수행하려면 먼저 안전한 read-only identity/grant/schema/role/빈대상 확인 결과와 별도 허용 범위를 검토해야 한다. B가 통과하더라도 합성 SecurityContext+실JPA와 A JWT+repository 대역을 JWT→DB 전체 통합으로 합산하지 않는다. 전체 기존 Boot suite/Redis·ES·SMTP·SMS·AWS·상품·거래·채팅/HTTP·브라우저·운영·성능은 미검증/범위 밖이며 성능은 사용자 유예다. 원시 증빙 보존 공백과 후일 재실행은 구분한다. 최종 독립 판정·공통 BACKLOG/LOG 상태는 아래 마감 기록을 따른다.

## 마감 독립 리뷰·공유본 치환

[독립 최종 리뷰](independent-review.md)는 A12+B 순수 gate18=30 및 fresh assemble, 실패/중간 이력·source snapshot/해시와 현재 host gate 보류 상태를 대조했다. **A 제한 기반 완료와 B 준비/보류 판정 범위에서 차단 결함 없음**이며 실제 B 물리·preflight·전체 통합의 완료가 아니다. 공통 BACKLOG RF016/R16-로컬·DB·확장·성능과 REFACTORING_LOG도 이 상태로 맞췄다. 기존 이력은 남겼으며 마감 중 코드/테스트/서비스/DB를 다시 실행하지 않았다.

리뷰에서 XML/console11개에 로컬 사용자/절대경로를 발견했다. 먼저 기존 증빙94파일의 **치환 전 원본 전체**를 Git ignored `.local-runtime/rf016-raw/20261110-final-review/original-evidence`에 복사하고, 그 파일들의 원본 SHA256/bytes를 같은 private 경로의 `original-sha256.json`에 보존했다. 공유본의 XML/console11개와 초기 source-hash metadata1개에만 `[WORKSPACE]`/`[USER_HOME]`/`[LOCAL_USER]` 경로·사용자 placeholder를 적용했다. 초기 source-hash의 source 내용 hash는 그대로이며 절대 경로 표기만 치환했다. 테스트 결과·실패 stack의 나머지 내용·timestamp는 바꾸지 않았고 후일 재실행으로 대체하지 않았다.

[sharing-manifest.json](sharing-manifest.json)은 변경12개 각각의 원본/공유 SHA256·bytes·kind와 private 원본 위치/manifest digest를 기록한다. 따라서 기존 실행의 원본 보존과 Git 공유용 치환본을 구분할 수 있다. Java9+properties1 **source10개는 변경 없음**이며 source manifest와 snapshot hash가 일치한다. 현재 공유 증빙은 **96파일**이고 [artifact manifest](manifest-sha256.json)는 자기 자신을 제외한 **95항목**을 현재 공유 바이트로 기록한다. 과거 "94파일"은 치환 전 snapshot의 실제 개수이며 현재 파일 수로 소급하지 않는다. 기존 `.tmp`16파일의 변경/누락0도 재확인했다.

## 2026-11-11 생성자·제한 context 호환 복구

위96파일/최종30건은 2026-11-10 마감 당시 기록이다. 통합 기준 `2091fae`의 RF035~038 생성자 확장과 RF016 구성이 충돌하여 발생한 정상 compileTestJava 회귀를 RF016 test-only 범위에서 복구했다. 이는 **3단계 정상 컴파일을 위한 명시 범위 밖 긴급 복구**이며 B 물리 실행 재개가 아니다. B의 직접 생성자 및 A 명시 context에 신규 네 payment-only 의존성을 즉시 실패하는 대역으로 주입하고, guard/ledger/JPA managed types/운영 gate·config/default deny는 유지했다.

[새 증빙](merge-compat-20261111/README.md)은 수정 전 RF024 compile 실패 원본 연결, 이번 wrapper 접근 실패, 복구 후 정상 sourceSet 컴파일 및 선택8클래스 **281/281·fail/error/skip0·5 task 실실행·exit0**, 실행 RF016 Java11개 source/hash를 보존한다. RF01633=A12+순수guard18+신규호환3, RF035~037 최소회귀248이며 과거 전수942/공동체인6의 재실행은 아니다. 치환 전 console2/XML8 원본은 `.local-runtime/rf016-raw/20261111-merge-compat/`에 남기고 공유본과 hash를 별도 manifest로 연결했다. 과거 마감 manifest 및 원시 증빙은 변경하지 않았다.

MySQL 시작/접속/쓰기0이다. 실제 read-only preflight·B JPA/commit/rollback/cleanup·경쟁 probe 및 전체 test/build/assemble·운영 개방/성능은 미실행이다. 신규 호환 검증은 mock fixture/EMF를 사용하므로 물리 안전을 입증하지 않는다. 재검증은 새 증빙으로 선택 명령을 실행하며, B와 운영 결제 재개는 기존 안전·허용·배포 조건을 각각 충족해야 한다. RF016 A 제한 완료/B 준비·보류 판정은 유지한다.
