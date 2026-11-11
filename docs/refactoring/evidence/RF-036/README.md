# RF-036 증빙 안내

- 기록일: 2026-11-11. 최신 판정: **동일 PR의 공동 최소 구현·오프라인 선택 검증·독립 리뷰 범위 완료(공동체인6건 최종 대조 포함), 물리DB·운영 개방/배포 미완료 및 차단 유지**. 독립178건과 후속 RF037 통합은 별도 실행 이력이며 아래 최신 공동 통합 절이 우선한다.
- 기준 HEAD: `82aa7279886385bc0974ae78679ab492447f4135`. 시작 시 `.tmp/`, RF035/038 명세·증빙 untracked 존재. RF035 병렬 구현의 결과는 이 분석에 포함하지 않는다.
- 수행: `git status --short`, `git rev-parse HEAD`, `rg`/`rg --files`, PowerShell `Get-Content`로 AGENTS·README·TEMPLATE·BACKLOG·재평가·RF003/035/038 최신 결정, payment/controller/DTO/entity/repository, transaction entity/repository/service, 프런트 payment 호출, `local/schema.sql` 및 RF003 반복요청 테스트의 존재를 확인했다. DB 접속 없이 파일만 읽었다.
- 사실: 요청별 키/거래ID와 point_history 거래 연결 부재, 로컬 product UNIQUE, 별도 payment/상태PUT. RF035는 buyer/거래가격 확정 및 직접 거래 허용; RF038은 정상 전이·6/7 복귀 금지 확정. RF037는 분석 시작 시 BACKLOG 후보였다.
- 외부 근거: [Stripe idempotent requests](https://docs.stripe.com/api/idempotent_requests), [MySQL locking reads](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html), 2026-11-11 열람. 표준 기술 근거/사례일 뿐 NGNG 키 만료·500 반환·거래당 횟수 정책의 승인 근거가 아니다.
- 원시 증빙: 탐색/웹 결과는 대화 tool 출력만 존재하고 별도 console/raw/XML·실행 fixture는 생성하지 않았다. RF003 과거 XML은 당시 동작 근거이지 RF036 재실행 결과가 아니다. 후일 실행은 별도 날짜/환경/소스·명령/결과로 보존해야 한다.

## 판단과 검증의 한계

무스키마 A안은 RF037 원자 commit·RF038 모든 writer 통제 아래 신규 규약을 따르는 같은 거래당 최대1회 차감 및 중복409를 제공할 수 있다는 설계다. 응답 유실 뒤 최초 성공 결과 재생이나 과거 오염 거래 판정은 보장하지 않는다. B/C안의 영속 receipt·거래 유일성·키 fingerprint/보존 규약은 별도 정책 및 스키마 승인이 필요하다. 어떤 대안도 이번에 구현·재현·성능 검증하지 않았다.

| 미실행 | 이유 | 재개/추가 방법·추적 |
|---|---|---|
| 테스트·빌드·독립 구현 리뷰 | 문서 분석 전용 범위, 구현/정책 미선택 | RF036 정책/승인 뒤 명세의 단위·회귀 행렬 및 독립 리뷰 |
| 실제 MySQL·DDL·운영 상태/외부 writer | 명시 DB 금지, local DDL은 실환경 근거 아님 | RF016 안전 환경·접속 승인 후 다중 연결 commit/rollback·잠금·UNIQUE·cleanup. R3-운영/RF036 |
| HTTP/브라우저·재시작·응답 유실 | 서버/프런트 변경 및 안전 실행 기반 없음 | RF037/038 공동 경계 확정 후 실패 지점·기존 소비자/키 전환·반복 클릭 검증 |
| 기존 미확정 결제/무결제 상태 판정 | 영속 거래↔point 연결 부재, 운영 자료 미조회 | 승인된 읽기 조사·데이터 정책 후 전환 계획. 문자열 추정 receipt/환불 금지 |
| 성능 | 사용자 유예·범위 밖 | 별도 재개 지시 전 측정/개선 주장 없음 |

상태 연결은 BACKLOG의 RF036/R3-멱등성, RF037/R3-거래원자성, RF038 및 R3-운영을 사용한다. 공통 문서는 통합 담당이 갱신하며 이 담당은 두 파일만 수정한다. 미실행은 통과가 아니고 분석 완료가 구현 또는 스키마 변경 승인이 아니다.

## 후속 사용자 확정 및 전환 검증 잔여

사용자는 거래당1회 차감·처리된 거래 재요청409/상태조회·응답 재현/요청 키 저장 없음(A안), 변경 전 차감 후 status1일 수 있는 기존 거래는 확인 전 재결제 차단을 확정했다. 기존 정책을 다시 미정으로 다루지 않는다. B/C는 선택되지 않은 비교 이력이다.

기존 schema에는 결제 거래 연결이 없어 기존1의 미결제를 자동 판별할 수 없다. **RF037 교차 검토에서 거래ID high-water mark 단독으로는 불충분함을 확인했다.** 변경 전 거래 없는 상품에 고아 차감 후 컷오버 이후 신규 거래가 생길 수 있고, 존재하지 않는 미래 상품ID도 과거 차감 요청에 사용될 수 있었다. 따라서 신규 거래/상품 생성 경계만으로 허용하지 않는다. 서버 fail-closed를 기본으로 상품·구매자·거래의 과거 미확정 차감 위험을 신뢰 가능한 완전한 기록으로 대조한 허용 대상만 검토한다. 설명문 검색·사용자 진술은 자동 해제 근거가 아니다. 무스키마 검증 대상 목록도 서버 설정·환경/대상 식별·검증자/근거·변조/복원 방지 운영이 필요하다. 이를 증명하지 못하면 신규ID도 차단한다. 영속 대조 기록을 DB에 추가하는 대안은 별도 schema 승인 대상이며 선택되지 않았다. 실제 MAX 조회/DB 점검/배포·reconciliation은 수행하지 않았다.

남은 항목은 gate 구현 및 경계 H±1/미설정·환경 오류/기존1 차단/신규rollback 재시도 검증, 운영 대상·writer 정지/drain·경계 검증 기록과 허용된 배포 실행, 기존 거래 확인 및 필요 시 해제 수단의 구체 정책이다. 각각 RF036/RF037/R3-운영으로 추적한다. 데이터 검증은 자동 완료된 것이 아니며 증거 부족이면 차단을 유지한다. 운영 DB 읽기/배포·기존행 변경은 이번에 승인되거나 실행되지 않았다. 신규 schema·의존성·대규모 구조는 별도 승인 조건을 유지한다.

이번 추가 작업도 두 문서만 수정했다. 소스 생성 경로를 파일로 재확인했고 신규 테스트·DB·성능·운영 조회는 없으며 별도 raw/XML도 없다. 후일 검증은 새 증빙으로 보존한다.

## 추가 확정: 검증 근거가 확보된 거래만 허용

사용자가 신규 여부와 무관하게 이전 차감 위험이 판별되지 않는 거래를 차단하고 검증 근거가 확보된 거래만 허용하도록 확정했다. 차단 정책은 미정이 아니다. 명세에 서버 전용 운영 allowlist의 최소 무스키마 설계를 추가했다: 기본 deny, 환경/DB 복원 세대 및 거래/product/buyer/seller/price binding, 검증자·근거·승인 이력, 명시 유효기간/폐기, 배포 권한/버전 일치, 교체·철회 때 gate 폐쇄/drain, 구버전 writer·복원 시 재검증. 일반 HTTP 입력·시각/ID 경계·신규 여부의 자동 허용은 금지한다.

목록은 운영 검증 결과의 입력이고 과거 결제 성공을 자동 증명하지 않는다. 실제 원장 대조·근거 확보·목록 생성·운영 배포는 수행하지 않았다. 확인 기록이 없으면 재결제 차단을 유지한다. RF037 차감+상태2 단일 commit이 거래당1회를 담당하며 목록을 별도 선소비해 두 단계 실패를 만들지 않는다. 성공 뒤 목록이 남아도 상태로409, 전체rollback 뒤에는 유효 승인과 현재 상태를 다시 검사한다.

파일 파싱/누락·만료·버전/환경/복원 세대·binding 불일치·일반 사용자 조작·철회/구버전 서버·rollback/재요청 테스트는 후일 검증 계획이다. 구현·테스트·DB 작업은 없다. 영속 검증 테이블 등 신규 schema, 신규 의존성/대규모 구조는 별도 승인 대상이며 사용자 정책 확정으로 자동 승인되지 않는다. RF036/RF037/R3-운영의 구현·물리 검증·운영 대조/배포 잔여를 계속 분리한다.

## 독립 리뷰 보완 기록

RF037과 승인 tuple을 거래/product/buyer/**seller**/price·환경·독립 복원 세대·승인 유효기간/증거참조로 통일했다. 기록 seller가 불완전하거나 승인값과 다르면 차단한다. 현재 상품 owner 대체나 수락 proof 요구는 추가하지 않으며 RF035 단독 완료 범위와 구분한다. seller 누락/불일치·owner와 다른 정상 seller 검증은 계획이며 아직 실행하지 않았다.

공통 독립 리뷰 지적을 명세에 반영했다. allowlist와 DB에 독립된 운영 기대값/control 없이는 복원 감지·개방을 주장하지 않는다. tuple A→B→A, ID 재사용/status1 복귀·외부 SQL·구 writer·복원을 검증 시점부터 승인 유효기간까지 통제하지 못하면 차단한다. 증거 확보 전에 writer 차단/drain을 완료하고 원장 포함 범위·commit/시점을 기록하며 검증→개방 사이도 차단을 유지한다.

엄격 JSON formatVersion/필수 필드·UTC/숫자·중복·미지원 필드·만료/clock 오류 규약, immutable snapshot·runtime reload 없음, 모든 인스턴스 승인 버전 통제, 상품/거래 잠금 뒤 tuple/status1/approval/expiry 재검사를 구체화했다. 최종 쓰기 직전 승인 유효성 확인과 실제 commit 완료 시각의 보장을 구분한다. 409는 자동 결제/성공 단정 없이 상태·포인트 재조회하며 check=false도 재조회를 차단하지 않는 프런트 검증을 추가했다.

이것은 문서 지적 반영이며 구현/운영 검증 통과가 아니다. parser/gate/원자 commit·UI의 코드 검증과, 원장 완전성·독립 운영 control·시계/writer/복원 통제·실제 gate 개방을 분리한다. 후자는 증거 없으면 차단 유지다. 신규 테스트·DB·배포·원시 실행 로그는 없다. 명세의 추가 반례를 향후 재현하고 운영 실행은 허용 범위/증거를 확보한 뒤 별도 기록해야 한다.

## 2단계 RF036 독립 gate/config 구현 결과

브랜치 `refactor/payment-integrity-stage2`, 기준 HEAD `82aa7279886385bc0974ae78679ab492447f4135`에서 기존 RF035 미커밋 작업을 보존했다. 이 담당은 신규 gate/config와 두 테스트, RF036 명세·증빙만 변경했다. 기존 point/transaction service/repository/frontend 및 공유 BACKLOG/LOG는 다른 담당 통합 범위이며 이 담당은 변경하지 않았다. 신규 의존성/schema/실DB/서비스 실행/운영 등록/배포/commit/push는 없다.

| 실제 실행 | 결과 | 해석·보존 |
|---|---|---|
| 최초 backend 상대경로 로그 준비 | 출력경로 오류, 테스트 시작/결과 확인 못 함 | 도구 대화만 존재, raw 파일 없음. 테스트0건 통과로 집계 금지 |
| wrapper sandbox | 캐시 lock 파일 접근 거부/exit1, 테스트 미시작 | implementation/unit-initial/console.txt 및 exit-code.txt |
| 캐시 허용 후 첫 정상 sourceSet | compileJava 성공, compileTestJava39오류, 테스트0 | 다른 담당이 수정 중이던 StatusFixture 호환 실패. console-approved.txt/exit-code-approved.txt 보존. 해당 fixture 수정/복구를 이 담당은 하지 않음 |
| 첫 isolated init 명령 | Gradle CLI `assemble` 뒤 `--tests` 위치 오류/exit1, 테스트0 | unit-isolated/console.txt/exit-code.txt, 통과와 분리 |
| isolated sourceSet/build directory | Gate163 + Configuration15 =178/178, failure/error/skip0, assemble 성공 | unit-isolated/console-run.txt/exit-code-run.txt/xml 및 isolated-tests.init.gradle |
| fixture 일관성 회복 후 정상 sourceSet 최종 | **178/178, failure/error/skip0**, compileJava/compileTestJava·bootJar/jar/assemble 성공 | unit-final/console.txt/exit-code.txt/xml. 같은 사례 재실행이며 두 성공 합산 금지 |

정확 명령은 [commands.md](commands.md)에 있다. [prepare-evidence.cjs](prepare-evidence.cjs)는 이미 보존한 raw console/exit/XML·선택 source를 복사·정제하며 테스트를 재실행하지 않는다. 미정제 raw는 Git 제외 `.local-runtime/rf036-results/`, 공유본은 [implementation/](implementation/), 각각의 SHA-256 및 현재 선택4개 source hash는 [verification.json](verification.json)이다. 초기 보존 확인에서는 정제 XML4개를 다시 파싱했고, 원시/공유19개 및 source4개 hash 불일치0·선택 source 후행공백0·공유본 개인 사용자경로/JWT literal 탐색0이었다. `git diff --check`도 오류0이며 기존 파일의 LF→CRLF 경고는 남았다. 아래 P2 정정 후에는 실행 당시 source와 주석 수정 후 source를 분리 보존하고 hash 목록을 확장했다. raw와 정제본 hash가 달라질 수 있고 전체 작업 트리의 snapshot은 아니다. 후일 재실행으로 이번 raw가 없는 실패를 소급 증명하지 않는다.

기존 Windows/JDK17/Gradle8.7/Node20 및 Jackson/Spring/JUnit/캐시만 사용했다. Gate163은 실제 Jackson malformed/duplicate/unknown/trailing/UTC/Long/필수필드·count/byte/UTF-8 검사, 정확한 전체 tuple와 status1·목록 미등록/빈목록·독립 기대값·immutable snapshot, 유효기간 포함/제외 경계와 시간 경과 재검사, backward/누적 drift/monotonic·overflow/시계장애를 검증한다. Configuration15는 모든 운영 입력 누락/기본 deny·엄격한 true·오류 path·잘못된 기대버전과 reload 부재를 검증한다. 일반 실패는 경로/근거를 담지 않은 generic409이며 receipt/소비상태를 만들지 않는다. 대역 wall/nano clock, 임시 합성 JSON, 실제 parser와 파일 I/O를 사용했고 실제 원장이나 운영 승인 증거를 쓰지 않았다.

입력/상한/API는 [최신 명세](../../tasks/RF-036.md#2026-11-11-2단계-rf036-독립-gate-구현단위-검증)에 고정했다. 서버 Spring Environment `ngng.payment.verification.*`는 manifest/DB와 별도인 운영 입력 경계지만, 문자열과 `independent-controls-confirmed=true`/`clock-trusted=true`만으로 실제 독립 통제·원장 진위·시계/writer/복원 통제가 입증되지 않는다. 운영 기본값/manifest/허용 항목은 만들지 않았다. 설정/파일 교체는 runtime reload가 아니며 전 인스턴스 차단/drain/redeploy 통제가 별도 필요하다. JDK 시계 비교의5초 허용오차는 오류 감지 보조이며 실제 clock 신뢰 증거가 아니다.

특히 `presentTupleAloneCannotDetectAbaIdReuseOrRestoredStateAndIsNotPaymentProof`는 tuple A→B→A 또는 status2→1로 되돌린 동일 현재값이 허용되는 **현재값 비교의 한계**를 의도적으로 드러낸다. ABA/ID재사용/DB복원·과거 목록 복원·구 writer 차감을 자동 검출했다고 주장하지 않는다. 실제 이력 무결성은 검증 기준시점 이전 writer 차단/drain부터 승인 수명 전체까지 외부 통제로 배제해야 하며 통제의 진실성을 단위 테스트가 보장하지 않는다. 검증/통제 근거를 확보하지 못한 거래는 신규 여부와 무관하게 차단한다.

| 잔여·미실행 | 이유 | 추가 방법·재개 조건 |
|---|---|---|
| RF037 서비스 연결 후 결제 최초1회·409·rollback·인가/잔액/쓰기직전 expiry | RF036 독립 gate/config 범위 | 공동 코드 연결 후 서비스/chain/transaction boundary 검증. RF037/RF038 통합 담당 추적 |
| 실제 MySQL current read/조건부 UPDATE/물리 rollback·경합/OSIV/commit 응답유실 | DB 실행 승인·안전 격리 fixture가 이 작업에 없음 | 명시 승인·격리 identity/데이터·cleanup 확보 후 별도 실행, mock을 물리 증거로 삼지 않음 |
| 실제 HTTP/브라우저·409/check부족 이후 조회·구 호출자 | 서버/프런트 통합 범위 | RF037/038 연결 및 안전 실행 기반 확보 뒤 복구 순서/실패 UI 검증 |
| 원장 완전성·독립 control·manifest 진위/등록·시계/writer/drain·ABA/복원/ID재사용·stale 인스턴스/구버전 차단·운영 개방 | 운영 자료/통제 구현·실행 미승인, 현재값 검사로 검출 불가 | 별도 실제 증거·운영 변경 실행 허용 범위 확보 전 전체/해당 gate 폐쇄 유지 |
| 전체 backend test/build·frontend build/lint·성능 | 선택 gate 검증 범위이며 성능 범위 밖 | 필요한 통합 범위를 정한 뒤 별도 검증; 개선 주장 없음 |

공유 BACKLOG/LOG는 통합 담당에게 결과·잔여 연결을 요청했다. 이 기록은 **독립 gate 코드 단위 완료**이며 원자 결제 통합 완료, 실제 검증된 거래 존재, 운영 안전 전환/배포·금전 개방 승인으로 해석하지 않는다.

## 공통 독립 리뷰 P2 정정·실행 당시 source 보존

공통 독립 리뷰는 새 차단 P0/P1 없이178건/assemble과 source/hash를 확인했다. P2 지적에 따라 Java Javadoc과 RF036 명세의 symlink 설명을 정정했다. **`NOFOLLOW_LINKS`가 거부하는 것은 최종 파일 경로 요소의 symbolic link다. 상위 경로의 symbolic link/junction, 경로 교체·경합 및 권한/배포 경로 신뢰는 이 옵션으로 보장하지 않으며 운영 통제 전제**다. 상위 경로 보호나 실제 권한 확인을 구현·검증했다고 하지 않는다.

- [source/](implementation/source/)는178건/assemble 실행 당시 보존한4개 파일이다. raw와 공유본 모두 덮어쓰거나 현재 파일로 재생성하지 않았다. [verification.json](verification.json)의 `executedSourceHashes`가 이 snapshot을 가리킨다.
- [reviewed-source/](implementation/reviewed-source/)는 P2 정정 후 현재4개 파일이며 `sourceHashes`가 현재 source hash다. [정확한 주석 변경 비교](implementation/review-comment-only-check.json)는 gate의 `fromFile` Javadoc 한 곳을 명시된 문구로 치환하면 byte-for-byte 동일하고 나머지3개 source/test 파일이 그대로임을 확인한다. 실행 로직·테스트·운영 설정 변경은 없다.
- 주석·문서 정정이므로 테스트/assemble은 재실행하지 않았다. 기존178건 결과를 수정본에 대한 새 실행으로 표현하지 않는다. evidence script는 실행 source를 필수 기존 입력으로 읽고 덮어쓰지 않으며, 예상한 주석 정정 이외 source 차이가 있으면 실패한다.
- 정정 후 raw/공유24개·실행 source4개·현재 source4개 hash 불일치0을 확인했다. 정제 XML4개 파싱 성공, 개인 사용자경로/JWT literal 탐색0·현재 source 후행공백0 및 해당 경로 `git diff --check` 오류0도 재확인했다. 기본 deny 및 공동 통합/물리 DB/운영 개방 미완료·운영 시계/writer/복원 통제의 한계는 그대로다.


## 2026-11-11 후속 공동 통합·현재 완료 범위

**동일 PR의 공동 최소 구현·오프라인 선택 검증·독립 리뷰 범위 완료(공동체인6건 최종 대조 포함), 물리DB·운영 개방/배포 미완료 및 차단 유지**. 브랜치는 `refactor/payment-integrity-stage2`이고 RF035/036/037/038·프런트를 같은 PR/릴리스 경계로 다룬다. 이 문서의 초기 분석·독립 구현 당시 “대기/미연결/미구현”은 당시 이력이며 아래 현재 결과를 소급 의미하지 않는다. 기존 RF036 원시 XML/console·실행 당시 source/hash·중간 실패를 덮어쓰거나 최신 source로 재생성하지 않았다.

거래당1회 차감·후행409/상태조회, 신규 포함 미확인 거래 default deny가 결제 서비스에 연결됐다. durable 요청키·과거200 응답 재현·TTL/receipt/별도schema는 도입하지 않았다. RF036178은 독립 parser/config 단위 결과이고 이번 서비스/체인 결과는 RF037 후속 증빙이다. gate 재검사는 balance 잠금 대기 뒤 쓰기 직전 수행하며 전체tuple/status1/시간창이 일치해야 한다. tuple은 소비 receipt가 아니며 ABA/복원/ID재사용·원장 진위를 자체 검출하지 못한다.

공동 잠금/쓰기 경계는 상품PK current scalar lock→거래 current scalar lock→buyer/price/status/gate→user/latest point lock→gate 재검사→이력 save/flush→전체tuple/status1 조건부UPDATE(affected=1)이며 단일 REQUIRED DB transaction이다. 프런트는 별도PUT2를 제거하고409/check부족/응답유실·5xx에 거래/포인트를 재조회하며 결과미확정·조회실패를 구분한다. UI 중복클릭 방어는 서버 보장 증거를 대체하지 않는다. 독립 리뷰 P2의5xx 단정 안내를 미확정으로 고쳤고, 재조회로 목록이 바뀔 때 다른 거래를 열거나 crash하지 않도록 BuyHistory의 index 선택을 거래ID로 바꿨다. 후자는 새 강제 재조회에 직접 영향을 주는 국소 UI 범위 보완이며 판매자·취소/신고/구매확정 정책을 변경하지 않았다.

- [후속 RF037 실행·한계](../RF-037/README.md), [raw/shared·최종source 대조](../RF-037/verification.json): backend20클래스942/942·failures/errors/skips0, compileJava/compileTestJava·bootJar/jar/assemble 성공. point388(독립 gate/config178 포함), RF038355, RF00443, RF02183, RF02373이다. 이전 RF035/036/038 독립 회귀 결과와 겹치므로 합산하지 않는다.
- frontend37/37·tsc/lint exit0(무관 img warning3), 실제 TS handler/hook/component 대역이다.5xx×조회성공/실패8건 및 BuyHistory reorder/remove/empty3건을 포함한다. 실제 browser/wire/React scheduling/SWR 네트워크를 검증한 것이 아니다.
- 별도 joint-chain-final6/6·failures/errors/skips0, compileJava/compileTestJava 성공. 기존942에 없는 클래스의 추가 직렬순서6건으로 다른 실행임을 구분한다. 최초 helper 이름충돌 compile7오류/테스트0·stale942 XML 분리 이력은 RF037 raw/evidence에 남겼다. 초기 wrapper/compile/fixture 실패도 후속통과로 지우지 않는다.
- 정상 top-level 경로의 공동 독립 리뷰는 신규 미해결P0/P1/P2 없이 로컬 합의 범위 충족을 확인했다. 추가6건의 최종 source/manifest 대조도 공통 독립 리뷰에서 완료했고 신규 차단사항은 없었다. 선택 snapshot31개는 전체 작업트리 snapshot이 아니며 실패 당시 source가 모두 보존된 것도 아니다. 후일 재실행은 새 증빙이다.

| 잔여/현재 상태 | 미실행 이유·한계 | 추가 검증·재개 조건 |
|---|---|---|
| 물리DB locking/current-read·두 쓰기 commit/rollback·동시 payment/충전/취소/신고 | 이번 실행은 repository 대역·Spring advice/MockMvc이며 DB 승인·안전 격리fixture 없음 | RF016 안전 물리격리identity·소유fixture/외부차단/cleanup 및 명시 실행범위 승인 뒤 barrier·새연결로 이력/상태/잔액을 확인 |
| outer RC/RR·사전managed/dirty entity·OSIV·다른writer | REQUIRED의RC가 outer 격리수준을 바꾸지 않음. native auto/명시flush·bulkUPDATE는 caller dirty/stale 상태를 해결하지 않음 | 실제 HTTP/OSIV·persistence context·SQL/flush 재현 후 안전한 지원경계 확인. clear/refresh/REQUIRES_NEW 임의 도입 금지 |
| 기존 원장·운영 status사전/관계·검증승인/독립 복원세대·시계·ABA/ID재사용 | 현재tuple/gate테스트로 과거차감/복원/증거진위·writer drain을 증명 못함 | 독립 업무증거/운영자료와 명시 조사·등록 승인 전 신규포함 미검증 거래는 default deny. 운영 allowlist를 테스트결과로 생성하지 않음 |
| 전체 writer/drain·구/신버전·rollback·실HTTP/브라우저 | 혼합 버전/진행중 구요청·실제 소비자 미검증 | 같은PR/릴리스 전환·구writer배제와완료drain·rollback시 금전쓰기재개 조건 및 실제 네트워크·구클라이언트 오류 검증 후 개방 판단 |
| RF021 직접생성/가격오염·기타충전증명/전체test/build·성능 | 직접생성인가 보류·충전 기존정책 유지, 별도 미승인/사용자성능유예 | 독립 범위·정책/환경 승인 전 자동착수 금지. 성능개선 주장 없음 |

공통 BACKLOG의 RF035~038 공동 잔여 및 R3/R4/R6·R21에 상태/근거/재개조건을 연결했다. **운영 결제 개방/배포 차단은 로컬 구현 완료와 별개로 유지**한다. 승인manifest가 없으면 모든 결제는 거부된다. 실제 운영분류·schema·배포·commit/push·다음작업 자동착수는 수행하지 않았다.
