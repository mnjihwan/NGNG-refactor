# RF-023 분석 증빙

- 일자/상태: 2026-11-10, **최소 단건 read 구현·신규73건 및 관련 회귀80건·assemble·독립 리뷰 완료**. 초기 분석 기록은 당시 이력이며 아래 최신 실행 절을 따른다. 실DB·운영 전체 검증 완료를 뜻하지 않는다. [명세](../../tasks/RF-023.md), [BACKLOG](../../BACKLOG.md), [재평가](../../BACKLOG_REASSESSMENT_2026-11-10.md).
- 기준 HEAD `e417eaf7d476a639413414ff8f4423b89cc88e66`. 시작 git status는 RF-020/021 tasks/evidence untracked. 동시 작업을 보존하며 RF-023 두 문서만 작성했다.
- 검증 환경/범위: Windows PowerShell에서 git rev-parse/status, Get-Content, rg로 지침·명세와 세 프로젝트 관련 소스/기존 테스트를 정적 조사. 공식 OWASP Authorization Cheat Sheet와 API1:2023을 웹 열람했다(링크·적용 범위는 명세). 신규 의존성·설정·DB/소스/테스트 변경 없음.

## 확인 근거와 한계

`TransactionController.getTransactionById` → `TransactionDetailsService.readTransactionDetailsById`는 findById 뒤 인증 주체/관계 검사 없이 `ReadTransactionDetailsDTO.from`을 호출한다. 주소·seller/consumer 이름/전화가 포함되고 User 전체/비밀번호는 포함하지 않는다. from(null)은 빈 DTO를 반환하므로 부재 200이 정적 기준선이다. 실제 Jackson null 출력/HTTP 오류 직렬화는 미실행이다.

SecurityConfig의 permitAll만으로 공개 API라고 판단하지 않았다. TokenFilter는 이 URI를 건너뛰지 않고 유효 access 또는 refresh를 검사한다. principal에는 ID가 없으며 AuthService는 subject email로 user를 재조회한다. 실제 chain을 이번에 실행하지 않았으므로 운영 도달·악용을 확인한 것으로 표현하지 않는다.

초기 분석에서는 기록 seller/consumer 한정과 ADMIN 비우회를 제안하고 현재 product.user 불일치 정책을 미확정으로 남겼다. **2026-11-10 후속 사용자 결정으로 기록 seller/consumer만 허용, 현재 product owner 추가 허용 없음, seller/consumer 어느 한쪽 연관 또는 ID라도 불완전하면 다른 쪽이 요청자와 같아도 거부, ADMIN 예외 없음, 부재 거래의 기존 200 빈 DTO 유지가 확정되었다.** 공식 OWASP 자료가 이 제품 정책을 결정한 것이 아니며 승인 근거는 사용자 결정이다. 구체 거부 오류 응답은 구현 명세에서 확정한다. 이 문서 변경은 구현·테스트 실행 증빙이 아니다.

운영 단건 read 호출은 controller만 검색되었고 테스트는 직접 호출한다. frontend 거래 ID URL 호출은 PUT이며 ADMIN 거래 로그는 별도 로그 API다. 외부 GET 소비자 부재는 입증되지 않았다. 마이페이지의 동일 DTO·채팅의 직접 연관 address 조회는 단건 read를 우회하므로 RF023 완료가 전체 개인정보 경로의 안전을 뜻하지 않는다.

## 원시 자료와 실행 구분

- 탐색 원시 출력은 대화 tool 결과에만 있으며 별도 raw 파일/실행 XML/로그는 없다. 이 README와 명세는 정적 분석 서술 자료다.
- 테스트·컴파일·빌드·DB/HTTP·브라우저·운영 로그·성능 실행은 **0건**이다. skip/미실행을 통과로 기록하지 않는다. RF004·006 과거 실행은 해당 증빙의 역사이며 RF023 결과가 아니다.
- 후일 실행은 별도 일자·환경·명령·fixture·결과·당시 소스/hash·원시 XML/로그로 보존하고 이번 분석의 소급 실행 증빙으로 대체하지 않는다.

## 남은 검증과 재개 조건

| 항목 | 미실행 이유/한계 | 추가 방법·재개 조건 | 추적 |
|---|---|---|---|
| 거부 오류 계약·외부 소비자 회귀 | 기록 당사자/한쪽 불완전 거부/ADMIN 비우회/부재200은 사용자 확정; 외부 호출 자료 미확인 | 확정 정책에 따라 403/국소401 등 구체 오류 계약 명시, 한쪽 ID 누락+다른 쪽 일치도 거부하는 테스트 포함 | RF023 정책 확정·구현 미착수 |
| 변경 전 재현·서비스/MVC/실제 chain·회귀·빌드 | 분석 전담 범위, 테스트 미작성 | 합성 key·대역 repository·non-null/손상·역할/당사자 fixture 및 무쓰기/민감정보 미반환 검증; 명세의 실행 계획 | RF023 미실행 |
| 독립 리뷰 | 초안 작성 단계 | 권한 주체·DTO/부재 계약·호출자·검증 누락 검토 | RF023 미완료 |
| 실DB/HTTP/OSIV·브라우저 | 안전 격리 환경과 외부 계약 자료 없음 | 별도 범위 합의·fixture 소유/정리·프록시/클라이언트 확보 후 검증 | RF023 잔여 |
| 다른 개인정보 읽기·쓰기에 대한 검증 | 단건 GET 범위 밖 | 목록/마이페이지 별도 조사, RF022 채팅, RF021 요청 쓰기, RF041 주소, RF038 전이로 연결 | BACKLOG 기존/범위 밖 후보 |
| 운영 빈도/피해·성능 | 관찰 자료 없고 성능 작업 아님 | 별도 지시·대표 환경/자료 확보 | 미측정, 자동 착수 금지 |

공통 BACKLOG/로그 상태 반영은 통합 담당자에게 위임하며 이 분석 담당자는 두 RF023 문서 외 파일을 변경하지 않는다.

## 2026-11-10 구현 후 최신 실행

후속 사용자 승인으로 운영 `TransactionDetailsService.readTransactionDetailsById`와 import만 수정하고 독립 `transaction.read` 테스트를 추가했다. 기존 생성자/AuthService/필터/controller/공통 DTO/list/상태 PUT/RF004 API는 이 작업이 변경하지 않았다. 정확한 오류 계약·검증 범위는 [명세의 구현 결과](../../tasks/RF-023.md#2026-11-10-승인-후-최소-구현실행-결과)를 따른다. 이번 단계는 문서 전용이던 초기 분석과 구분한다.

| 실행 | 결과 | 원시/공유 자료 |
|---|---|---|
| wrapper 최초 sandbox | cache lock 접근 거부, 테스트 미시작 | 대화 tool 출력만 보존. 이후 경로 오류로 보존 복사 실패, 최초 console은 재시도로 덮였으므로 파일 원본 없음 |
| before compile 실패 | exit1, 테스트0건 | [console](before-compile-failure/console.txt), exit-code.txt. RF021 동시 fixture의 미정의 auth 2개; 해당 담당 수정 후 별도 재실행 |
| before 보정 재실행 | **6통과, fail/error/skip0, exit0** | [console](before/console.txt), [XML](before/TEST-com.ngng.api.transaction.read.TransactionReadBeforeSecurityChainTest.xml), before/source·source-hashes.json·head·git-status·java-version |
| after 신규 선택 | **73통과(서비스40+chain33), fail/error/skip0, exit0** | [console](after/console.txt), [서비스 XML](after/TEST-com.ngng.api.transaction.read.TransactionReadOwnershipTest.xml), [chain XML](after/TEST-com.ngng.api.transaction.read.TransactionReadSecurityChainTest.xml), after/source·source-hashes.json |
| 관련 회귀 명령 최초 실패 | Gradle 옵션 해석 단계 exit1, 테스트/assemble 미실행 | [console](regression-command-failure/console.txt), exit-code.txt. 담당자 명령에서 assemble 뒤 --tests를 두어 실패했고 순서 수정 후 별도 재실행 |
| 관련 RF004/006 회귀·assemble | **80통과, fail/error/skip0, exit0; compileTestJava·bootJar/jar·assemble 성공, 8tasks executed** | [console](regression/console.txt), regression XML4개·선택 source/hash·assembly-hashes.json |

명령(backend cwd, 기존 Oracle JDK17.0.10 `JAVA_HOME`, Gradle8.7):

```powershell
./gradlew.bat test --offline --no-daemon --rerun-tasks --tests 'com.ngng.api.transaction.read.TransactionReadBeforeSecurityChainTest'
./gradlew.bat test --offline --no-daemon --rerun-tasks --tests 'com.ngng.api.transaction.read.*'
./gradlew.bat test --tests 'com.ngng.api.transaction.status.*' --tests 'com.ngng.api.transaction.service.TransactionAcceptancePolicyTest' --tests 'com.ngng.api.transaction.service.TransactionCreationContractTest' assemble --offline --no-daemon --rerun-tasks
```

before 실행은 제3자와 ADMIN 제3자가 유효 JWT를 거쳐 합성 주소/이름/전화200을 받는 특성을 실제 SecurityConfig/TokenFilter/JWT/controller/service와 repository 대역으로 재현했다. 부재200 전체 null DTO·미인증401도 확인했다. 실제 실행 HEAD는 `6bc7a56bab1a94c1f79f0c96970258fd89c08150`이며 초기 분석 e417eaf 이후 동시 Git 상태를 before/head·git-status에 보존했다. 이 담당은 commit/push를 하지 않았다.

after는 양쪽 당사자 ID 완전성·non-null Long 값 비교, 기록 seller와 product.owner 불일치, ADMIN 예외 부재, 인증 주체 DB 조회, mapper 전 거부·빈 오류 응답/발행 조건·부재200/DTO 계약, DB 장애 전파와 읽기 외 repository 상호작용0을 검사했다. 실제 chain33건은 role3×관계4, 정상 DTO1, 불완전4, 인증 실패4, 잘못된 access+정상 refresh3, 정상 fallback4, DB user 불완전2, 부재1, 경로4001, DB 장애1이다. 서비스40건 및 한계의 상세 항목은 명세에 기록했다.

## 원본과 source snapshot 범위

- 공유 XML의 hostname을 `redacted`로, 실패 compile console의 개인 로컬 경로 prefix를 `<workspace>/NGNG-backend/`로 정제했다. 정제 전 XML/console은 Git 제외 `.local-runtime/rf023-results/implementation-20261110/{before,after,before-compile-failure,regression,regression-command-failure}/`에 별도 보존했다. console에는 합성 fixture만 사용하며 운영 토큰·자격값은 사용하지 않았다. source/hash를 대조해 before/after/regression snapshot 일치를 확인했고 현재 read service/테스트도 after snapshot과 일치한다. 공유 자료의 실제 토큰형 문자열과 개인 로컬 경로 검색은 일치0이며 SECRET은 공개 합성 테스트 key뿐이다. 소스 hash는 SHA256이다.
- before/source는 당시 read service, fixture/harness, 선택 baseline Java와 당시 작성 중이던 Ownership Java를 담는다. after/source는 실행 당시 service 및 활성 read 테스트4파일이다. 전체 컴파일 대상/전체 저장소 snapshot은 아니며 baseline 실행에 선택되지 않은 후속 테스트의 완전 보존을 주장하지 않는다.
- `context-source/`와 `context-source-hashes.json`은 after 후 추가로 보존한 기존 SecurityConfig/TokenFilter/JWT/custom principal/AuthService/controller/DTO/entity/repository의 snapshot이다. 해당 경로의 Git diff가 비어 있음을 확인했으나 before 당시 즉시 채집한 파일이라고 표현하지 않는다. 각 Java의 package로 원래 경로를 복원할 수 있다.
- before 전용 baseline Java는 성공 후 active source에서 evidence로 옮겼다. 과거 재현을 재실행하려면 격리 checkout에서 before/source의 운영 read service·fixture/harness·baseline Java를 원래 package 경로로 복원하고 해당 클래스만 선택한다. 현재 after guard에서 취약한 before 기대값을 실행하면 실패하는 것이 정상이다. 과거 raw를 후일 실행으로 대체하지 않는다.
- regression/source는 실제 선택했던 RF004 Policy28/Creation15, RF006 Ownership24/Chain13 및 fixture, 당시 두 transaction service를 보존했다. source/hash는 전체 RF021/전체 저장소 snapshot이 아니다. 최초 compile 실패의 당시 잘못된 RF021 integration fixture 원문은 별도 채집하지 않아 console만 보존된다. `diff-check.txt`와 exit-code0은 전체 작업 트리 diff 검사의 당시 결과다.
- before6/after73/회귀80은 서로 다른 클래스의 실행 묶음이다. 취약동작6과 기존 계약80을 새 인가 테스트로 합쳐 표현하지 않는다. RF021 통합 담당자의 실행에서 같은 클래스가 포함되었으면 해당 수치와 중복 합산하지 않는다.
- `artifact-hashes.json`은 README/manifest 자신을 제외한 공유 증빙56파일의 SHA256이며 전 항목 일치를 검증했다. 문서의 이후 독립 리뷰 상태 갱신은 이 실행 artifact 목록을 바꾸지 않는다.

## 현재 미실행·재개 조건

관련 RF004/006 선택 회귀·assemble 및 최종 독립 리뷰를 완료했다. 독립 리뷰는 사용자 확정 정책·운영 변경 범위·before6/after73/regression80·assemble와 artifact56/source 정합을 확인했고 차단 결함을 발견하지 않았다. 전체 test/build, 실DB/Boot HTTP/브라우저/OSIV/프록시·실제 SQL/commit·운영 손상 데이터/피해·성능은 범위 밖 또는 안전 환경 미확보로 미실행이다. 안전 격리 환경/별도 범위 합의 후 실제 오류 dispatch와 외부 소비자·기록 당사자 fixture로 재개한다. mock 성공은 운영 완료가 아니다. 마이페이지 전체 HTTP 통합은 미실행이며 ID 기반 consumer/seller list2건과 공통 DTO 무변경만 검증했다.

기존 DB 고정 ID `TransactionDetailsServiceTest.readTransactionDetailsById`는 인증 없는 직접 호출이라 승인된401 계약에 맞춘 fixture 보완이 필요하지만 이번 지시대로 기존 RF 테스트를 수정하지 않았다. 전체 suite 미실행이며 재개 시 합성 기록 당사자 인증을 명시해야 한다. 목록/채팅·쓰기·주소/상태전이는 기존 BACKLOG 추적 대상이고 자동 착수하지 않는다. 최신 명세/본 README의 잔여를 통합 담당자에게 전달했으며 공통 BACKLOG/로그는 이 담당이 수정하지 않는다.

같은 실DB 클래스의 `updateTransactionStatus()`도75행에서 단건 read를 재호출한다. 이 테스트에는 인증이 있으므로 단독 read의 무인증401과 별개로, 재개 전 인증 주체가 기록 seller/consumer 중 하나와 일치하고 양쪽 당사자 ID가 완전한지 fixture를 확인해야 한다. 현재 product owner만 일치하는 주체는 PUT의 기존 정책을 통과하더라도 이어지는 단건 read는403이다. 해당 fixture의 실제 일치 여부는 실DB 미실행으로 미확인이고 테스트 소스는 변경하지 않았다.
