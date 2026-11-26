# RF-040 분석·구현 검증 증빙

## 2026-11-26 RF-040 단독 후보 검증

기준 커밋 `be5c615baabe921501964dcd0e3fd84fc252942a`의 백엔드를 `.tmp/rf040-standalone-20261126`에 읽기 전용 추출했다. 초기 기준 파일 267개의 SHA-256과 후보를 대조해 RF-040 적용 경로 21개만 추가·변경됐음을 확인했다. 실제 적용 목록, 파일별 기준·후보 SHA-256과 기존 RF-016 구성 및 RF-044 자료 해시는 [후보 매니페스트](after/standalone-final/candidate-files.json)에 있다. 적용 후보의 바이트를 `after/standalone-final/source/`에도 보존했다.

공유 `Rf016IsolationConfiguration.java`는 기준의 저장소 대역과 기존 메일·SMS 구성만으로 컴파일되므로 기준 해시 그대로이며 적용 목록에서 제외한다. 단독 `Rf016IsolatedBootSmokeTest.java`는 기존 가입 계약과 SMS 대역을 가져오고, 인증 전 상태 조회와 인증 후 업무 조회가 순서대로 일어나는지만 추가 검사한다. 공용 작업트리의 회원가입 보완 소스는 유지했다. 단독 후보의 Java 운영·테스트 소스에서 신규 RF-043 타입·증명 필드 참조가 발견되지 않았고, 최종 `compileTestJava`는 전체 테스트 소스 컴파일을 완료했다.

| 실행 | 결과와 자료 |
|---|---|
| 단독 첫 실행 | `after/standalone/`의 21개 클래스 1,221건 중 1,215건 통과, 실패 6건, 오류·건너뜀 0. `<후보>/docs/refactoring/evidence/RF-044/before/`의 기존 비교 JSON을 첫 추출에 포함하지 않아 모두 `ReportResponseSecurityChainTest`에서 `NoSuchFileException`으로 실패했다. |
| 단독 재실행 | 같은 기준 커밋에서 RF-044 `create.json`, `detail.json`, `list.json`, `patch.json`을 추출한 뒤 소스 변경 없이 같은 21개 클래스를 다시 실행했다. `after/standalone-final/`의 **1,221건 통과**, 실패·오류·건너뜀 0, `compileJava`·`compileTestJava`·`bootJar`·`jar`·`assemble` 실행 및 종료 코드 0을 확인했다. RF-044 JSON은 기준에 이미 있으며 적용 목록에는 포함되지 않는다. |

각 콘솔 첫 줄의 정확한 선택 명령을 후보의 `NGNG-backend`에서 실행했다. Windows PowerShell·JDK 17·기존 Gradle 8.7 캐시를 사용했다. 홈 경로·호스트명·Boot 시작 계정을 가린 콘솔과 XML 44개를 두 경로에 공유하고 원본은 `raw/after/{standalone,standalone-final}/`에 보존했다. [원본·공유본 해시와 결과](after/standalone-final/artifacts.json)에는 XML 42개의 호스트명 이외 테스트 속성, 실패·오류·건너뜀 상세와 시간 불변 대조를 기록했다. 결합 실행 1,221건, RF-043 결합 37건과 P2 재실행 407건은 각 당시 결과로 유지한다.

기존 결합 상태의 P2 세 건과 비식별 자료는 별도 담당자가 재검토했다. P2 보완 범위에서 추가 차단 결함이 없었고, 공유 자료 63쌍·산출물 해시 144개와 XML 61개의 테스트 결과 불변이 확인됐다. 이 단독 후보의 소스 분리, 적용 목록, 샘플 검증과 새 산출물도 별도 독립 최종 리뷰를 통과했다. 전체 suite와 실제 MySQL·Redis·메일·SMS·HTTP·성능은 실행하지 않았다. 운영 DB 최신성과 경쟁, 실제 HTTP 오류, 세션 회수·회전과 브라우저·채팅 검증은 아래 승인 환경 및 정책 조건을 따른다.

## 2026-11-26 공유 증빙의 로컬 식별자 정제

독립 리뷰의 `after/signup-compatibility/` 2개, `after/verified/` 21개, `after/review-fixes/` 3개 XML과 verified Boot 로그의 지적을 확인했다. 전체 공유본을 재탐색해 이전 실행에도 같은 정보가 있음을 확인했다. 공유 XML 61개의 호스트명은 `[HOST]`, continuation·verified Boot 로그의 로컬 OS 사용자명 2개는 `[OS_USER]`, 이 두 XML과 별도 콘솔 2개에서 찾은 홈 경로는 `[USER_HOME]`으로 치환했다. 공유 파일 63개에서 이 식별자만 변경했다.

변경 전 정확한 원본 바이트 63개와 이전 해시 목록을 RF-040의 `raw/`에 상대경로 그대로 보존했다. 64개 모두 이 디렉터리의 `.gitignore`가 적용되는 것을 확인했다. `REDACTION_PAIRS.tsv`에 원본과 공유본의 경로·SHA-256·크기를 기록했고, `ARTIFACT_SHA256.tsv`에는 공유 산출물의 해시를 갱신했다. 원본의 해시는 변경 전 목록과 일치하며 나머지 기존 산출물 78개의 바이트도 그대로다.

`REDACTION_VERIFICATION.json`은 XML 61개의 테스트 이름, 실패·오류·건너뜀 상세와 실행 타임스탬프·소요 시간의 원본 대조 및 63개 공유본의 치환 범위 검증 결과다. 명세를 포함한 공유 텍스트 149개에서는 확인된 홈 경로·OS 사용자명·호스트명 후보가 0건이었다. 확인한 형식 밖의 식별자, 별도 원본 복사본, 운영 로그는 검색 범위 밖이며 Git 무시는 로컬 원본의 접근 권한을 제어하지 않는다.

기존 원본이 보관된 원래 Windows 프로필에서 저장소 루트의 `pwsh -NoProfile -File docs/refactoring/evidence/RF-040/verify-redacted-evidence.ps1`로 저장 자료를 다시 확인할 수 있다. 이 작업에서 제품 테스트·빌드·외부 서비스 검증을 새로 실행하지 않았다. 아래 실행 결과는 당시의 기록이다.

## 2026-11-26 독립 리뷰 P2 보완

기존 운영 소스와 RF-043 작업을 보존하고 리뷰에서 지적한 테스트 클래스 세 개를 수정했다. `after/review-fixes-console.txt`의 첫 줄에 실행 명령을 보존했다. `after/review-fixes/`의 공유 XML에서 `TokenStateSecurityChainTest` 306건, `PaymentTrustSecurityChainTest` 51건, `ConfirmAddressSecurityChainTest` 50건으로 **총 407건 통과**, 실패·오류·skip 각 0과 종료 코드 0을 확인했다. 기존 오프라인 Gradle 환경에서 테스트를 다시 컴파일했다. 이번 선택 실행은 세 클래스 범위이며 앞선 1,221건과 합산하지 않는다. 수정한 소스 3개는 `after/review-fixes/source/`에 보존했고 이전 캡처와 원시 기록을 유지했다.

| 지적 | 회귀의 근거 |
|---|---|
| 결제 업무에서 사용자 재조회 실패 | 첫 번째 인증 조회에는 활성 사용자를 반환하고 두 번째 업무 조회에서 사용자 없음, ID 없음, 저장소 예외를 각각 발생시킨다. 정확히 두 번 사용자 조회 후 업무 401 또는 MVC 예외의 동일 원인을 확인한다. 상품·거래·잔액 잠금과 포인트 이력 접근·쓰기가 발생하지 않는다. |
| 주소 역할 행렬 | 본인·타인·refresh의 각 행에서 합성 DB 역할을 실제로 `ADMIN`/`USER`/`UNCONFIRMED_USER`로 설정한다. 별도 입력 행에서 비등록 토큰 역할 `OTHER`와 현재 DB의 `USER`를 교차한다. 본인만 204, 타인은 403과 상태 보존, 이전의 계좌·역할 보존 및 refresh로 발급된 access의 DB 역할을 확인한다. |
| 로그아웃 전후 복사 토큰 | 실제 provider로 합성 access와 refresh를 발급해 로그아웃 전에 보호 경로에 보낸 뒤, 같은 값을 로그아웃 요청과 그 뒤 보호 경로에 다시 보낸다. 204·`Max-Age=0` 쿠키와 로그아웃의 저장소 조회·발급 0, 동일 access 성공 및 동일 refresh의 access 발급을 확인한다. 앞선 테스트는 로그아웃 뒤에 새 합성 토큰을 생성했으므로 동일 토큰 재사용의 근거는 이번 새 실행에만 있다. |

실서비스나 전체 suite, 패키징은 테스트만 수정한 이 재실행의 범위에서 제외했다. 운영 DB에서 인증과 업무 조회 사이 상태 변화, 실제 오류 응답, 세션 영구 회수 및 채팅·브라우저 경계는 아래에 기록된 별도 환경과 정책을 확보한 뒤 재개한다. 검토자가 같은 합성 경계를 확인할 때는 보존한 명령과 새 소스 캡처를 사용한다.

## 2026-11-26 A단계 구현 결과

기준 HEAD `be5c615`의 작업트리에 공존하던 RF-040, RF-043과 `.tmp/`를 보존했다. RF-040 운영 코드는 `SecurityConfig`, `TokenFilter` 두 파일에 한정된다. 공용 RF-016 smoke의 사용자·포인트 경로는 인증 전 조회와 기존 업무 조회를 순서대로 확인하며, RF-043의 회원가입 대역과 메일·SMS 단언을 유지했다. 거래 조회·신고는 합성 계정의 DB 역할을 실제로 바꾸어 기존 역할별 업무 인가를 회귀했고, 첫 조회 성공 후 두 번째 조회에서 발생하는 저장소 예외도 계속 검증한다.

| 보존 파일 | 실제 실행 및 결과 |
|---|---|
| `before/backend-console.txt`, `before/TEST-*.xml`, `before/source/` | 변경 전 합성 특성 테스트 6건 통과. 비활성·nullable·누락 사용자 수용, 토큰의 옛 역할 유지 및 비활성 refresh의 발급이 당시의 관찰값이다. 초기 Gradle wrapper 잠금 파일 접근 거부는 `before/sandbox-lock-failure.txt`에 남겼다. |
| `after/core-console.txt`, `after/core/`, `after/identity-chains-console.txt`, `after/identity-chains/` | 앞선 작업의 핵심 RF-005·RF-040 424건 및 RF-020·041·042 체인 137건 각각 통과. |
| `after/business-chains-first-console.txt`, `after/business-first/` | 앞선 작업의 업무 체인 560건 중 3건 실패. 상품의 잘못된 경로/본문 400이 유지됐으며 Mockito가 새 인증 조회 1회를 예상하지 않았다. XML과 당시 관련 소스 스냅샷을 보존했다. |
| `after/continuation-cli-first-console.txt` | 재개 첫 CLI 요청에서 `assemble` 뒤에 `--tests`를 지정해 Gradle 인자 해석 단계에서 실패했다. 테스트는 시작하지 않았다. 명령 순서를 수정했다. |
| `after/continuation-console.txt`, `after/continuation/` | 재개 후 18개 클래스 1,133건 통과. 기존 핵심 424건, 계정 체인 137건, 업무 체인 560건, RF-016 12건을 포함한다. 컴파일 및 `bootJar`·`jar`·`assemble` 모두 실행되어 성공했다. |
| `after/verified-console.txt`, `after/verified/TEST-*.xml` | 독립 리뷰 전 21개 클래스 **1,221건 통과**. 1,133건을 재실행하고 계정 서비스 직접 테스트 88건을 추가로 실행했다. 보완한 테스트 소스를 다시 컴파일했고 `assemble`은 직전의 정상 생성물을 확인해 최신 상태였다. 실패·오류·skip 0. |
| `after/signup-compatibility-console.txt`, `after/signup-compatibility/` | 동일 운영 코드로 RF-043 결합 회귀 **37건 통과**. 실제 명시적 MVC 회원가입 체인 5건과 회원가입 검증 서비스 32건에서 메일·SMS·저장소 대역을 사용했다. 실패·오류·skip 0. 이후 거래 조회/신고 테스트만 추가 수정했다. |
| `after/verified/source/`, `after/verification-environment.txt`, `ARTIFACT_SHA256.tsv` | 리뷰 전 RF-040 관련 운영·테스트 소스 및 공용 RF-016 구성의 정확한 작업트리 바이트 22개, 실행 환경, 보존 자료 SHA-256. 변경을 시작한 당시 자료와 재개 후 실행 자료를 별도 경로로 유지했다. |

최종 실행과 결합 회귀의 파일별 명령은 각 콘솔 첫 줄에 보존했다. `NGNG-backend`에서 기존 Gradle 8.7 캐시로 `gradlew.bat --offline --no-daemon --console=plain test --tests <완전한 클래스명> ... assemble`을 실행했다. 재개 후 사용자 캐시를 분리한 사전 확인은 Gradle 로컬 프로세스에 연결하지 못했으며 그 확인 출력은 대화에만 있다. 기존 캐시 및 로컬 테스트 프로세스 접근이 가능한 오프라인 실행으로 재시도했다. 재현할 때는 정확히 보존한 선택 목록을 사용한다. before 특성 검사는 현재 작업트리를 덮어쓰지 않고 별도 격리 복사본과 당시 소스로 재현한다.

| 회귀 경계 | 결과 해석 |
|---|---|
| access 우선·refresh fallback·공개 경로 | 유효 access는 refresh를 검사하지 않는다. 누락·빈 값·유효 서명/claim의 만료에서만 fallback한다. 무효 credential, 공개 skip, CORS preflight, logout은 인증 사용자 조회 0이다. 합성 JWT의 만료 사례는 테스트 시각과 넉넉한 간격을 사용하며 운영 만료·clock skew 경계 검증은 남아 있다. |
| 현재 사용자 상태·권한 | 3개 실제 DB 역할과 JWT 역할(기존 비어 있지 않은 미등록 claim 포함)을 교차했다. 미등록 DB 역할, `visible=false/null`, 누락/ID 없음은 빈 401로 발급과 업무를 중단한다. 유효 access의 상태 거부 뒤 refresh 추가 검증·발급은 발생하지 않는다. 정상 refresh가 endpoint 인가에서 403을 받아도 기존처럼 새 access 헤더가 남는다. |
| 오류 경계·업무 계약 | 인증 조회의 DB/중복 예외는 JWT catch를 통과해 원인 그대로 전파되며, 신고·거래의 인증 후 업무 조회 예외는 MVC의 `ServletException`에서 원인을 유지한다. 유효 credential 뒤의 입력 400/권한 403은 인증 조회가 1회, 업무 조회가 필요한 정상 흐름은 추가로 기존 조회를 수행한다. RF-042 동일 JWT 반복은 204→401, 서비스 직접 호출의 기존 toggle 동작도 별도 통과했다. |
| 알려진 잔여 동작 | 이 실행은 재활성화와 이메일 재사용을 특성화했다. 로그아웃 전후 같은 복사 토큰의 사용은 위 독립 리뷰 보완 실행에서 검증했다. |

실 MySQL/Redis, 메일·SMS 송신, 운영 HTTP, 전체 suite 및 성능 집계는 실행 범위에서 제외했다. MockMvc·명시적 Spring 컨텍스트와 RF-016 제한된 Boot 모드에서는 실제 오류 페이지, DB 현재 읽기·복제 지연·영속성 캐시, 권한 변경과 진행 중 업무 경쟁을 검증할 수 없다. RF-016 승인된 격리 환경과 실행 범위 확보 후 물리 DB와 HTTP를 확인한다. 회수·회전·계정 세대, 채팅 기존 연결·다중 인스턴스·브라우저는 정책과 테스트 환경을 합의한 뒤 각각 재개한다. 지연·부하·SQL 횟수는 별도 성능 지시와 측정 계획을 따른다. 필터 우선순위·실패 구분·기존 비교 단언과 증빙 재현성은 독립 리뷰 범위에 포함됐다.

아래 11월 11일 항목은 당시 계획과 분석 기록이다. 최신 결과와 잔여 범위는 위 내용 및 [작업 명세](../../tasks/RF-040.md)의 최신 절을 따른다.

## 2026-11-11 A단계 구현 재개 — 진행 기록

후속 위임으로 backend 현재 계정·role 검증과 필요한 기존 chain fixture 적응을 진행한다. 원래 분석 전용 기록은 당시 이력으로 보존한다. 최초 재개 HEAD는 `b06c05e`, source snapshot 시 `ebfc6f2da4b98ea8237ddbbf8a306b55baae5110`이다. 작업 중 다른 RF 변경/부모 Git 작업이 병행되며 이 담당자의 Git mutation은 없다.

`before/source/`에 관련 운영2개와 RF005/신규 before 및 기존 chain/harness 총19개 소스를 보존했다. `implementation-start-status.txt`와 `implementation-head.txt`는 snapshot 시점 상태다. 신규 before 실제-chain6건은 작성만 했으며 실행하지 않았다. 기존 backend build slot 및 RF043 보완/RF044 병합을 기다려 운영/공통 tests 변경·Gradle 실행을 보류했다. 따라서 아직 before/after 통과·빌드 성공 증빙은 없다.

주요 기대 변경: 정상 unskipped 인증의 repo 조회 +1(업무 조회는 유지), invalid/skip/preflight/logout 조회0, valid token의 MVC400/역할403에서는 인증 조회1. caller 없음/ID null은 이전 일부 객체 guard403에서 선행 인증401로 바뀌며 RF042 반복은 첫204 뒤401이다. claim의 unknown nonblank role와 DB unknown role를 구분한다. visible=null인 기존 행은 활성으로 간주하지 않는다. DB 단건성/연결 예외를401로 삼키지 않으며 필터 발생 예외와 업무 발생 ServletException wrapper를 구분해 회귀한다.

후속 source/XML/console/hash/기대 대응표와 실제 결과는 실행 후 별도로 기록한다. email-only sub의 계정 세대 혼동·재활성화 시 옛 토큰 부활, logout server ledger·기존 chat WS 회수는 이번 A단계로 해결되지 않는다. 실DB/replica/current-read·진행 요청 경쟁·운영 HTTP/error dispatch/성능·전체 test/build는 미검증이며 기존 README의 별도 재개 조건을 유지한다.

- 기록일: 2026-11-11. [명세](../../tasks/RF-040.md).
- 기준 HEAD `2091fae3b590f0286fe11e8a1354fc27d81e10c6`, 최초 작업트리 `?? .tmp/`. 기존 .tmp 보존.
- 수행 범위: AGENTS/운영 README/BACKLOG/재평가 단계3/TEMPLATE/RF005·020·022·041·042와 backend JWT/로그인/logout/계좌확인/탈퇴/사용자, chat REST/STOMP 인증/세션/전송, frontend logout/저장/재발급 호출 관계의 정적 읽기. `rg`, `Get-Content`, `git status --short`, `git rev-parse HEAD` 및 공식 문서 열람.
- 이번 변경은 RF040 명세·본 README뿐이다. 소스/config/tests·공통 BACKLOG/LOG는 담당하지 않는다. 테스트·빌드·DB/Redis/외부서비스 접속·성능·commit/push 없음.

## 재개 후 공통 리뷰 보완

통합 담당 공통 리뷰를 두 문서에 반영했다. 새 코드/테스트/서비스 실행은 없으며 A안 검증은 **계획**이다. 이전 정적 관찰·보존 이력은 그대로 유지한다.

- A안은 유효 credential을 먼저 검증한 다음 email→현재 DB 존재·ID·visible=true·기존 유효 role를 확인한다. access authority와 refresh 재발급 모두 DB role를 사용하며 유효 access의 상태 거부는 refresh fallback으로 우회하지 않는다. DB 연결/단건성/쿼리 오류를 credential401로 숨기지 않는다.
- 우선 한계: email-only sub는 삭제·email 변경/재가입 뒤 동일 email 신규 계정에 옛 토큰을 결합할 수 있다. RF043 email 증명과 연계하되 발급 계정 세대 결합을 별도 추적한다. visible=null 거부와 별개로 false→true 재활성화 시 옛 토큰은 부활할 수 있다. logout 회수·기존 WS idle/outbound 수신은 A안으로 해결되지 않는다.
- 별도 보류: session ledger/새 schema·전체기기 logout·chat 기존 연결 확장·발급 subject 전환. 사용자 사전 위임은 통합 담당이 전달한 근거 있는 최소 보안 판단으로 기록하며 공개 지침을 승인 자체로 취급하지 않는다. 이번에는 구현하지 않았다.
- 추가 계획: 실제 RF005 체인에서 세 role×endpoint의 기존 인가, 현재 DB role와 token role 교차, access 상태 거부+유효 refresh 우회 금지, 무효 credential의 DB조회0, active/false/null·계정 없음/ID/role 오류, DB 장애·downstream 예외, 정상 refresh·skip/OPTIONS·logout 경계를 검증한다. email 재사용/재활성화·기존 WS는 미해결 특성으로 기록한다.
- 성능/DB부하·replica freshness/current-read/commit 경쟁은 미측정·미증명. 대역 성공으로 운영 즉시 최신성을 주장하지 않는다.

재개 후 [OWASP Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)과 [OWASP REST Security](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html#jwt)를 추가 열람했다. 매 요청 권한 확인·거부와 JWT/현재 상태 불일치가 A안 권고의 근거다. 원문 응답은 도구 대화에만 있고 새 raw/source/hash/XML은 생성하지 않았다. 공통 리뷰 반영은 독립 실행 검증이나 전체 계획 최종 승인으로 계산하지 않는다.

## 확인된 근거와 한계

backend TokenFilter는 claim role로 인증하고 refresh도 claim 기반으로 재발급한다. logout은 cookie 만료만 반환하며 회수 상태 기록·조회가 없다. DropOut의 visible toggle 및 계좌확인의 role 변경이 기존 JWT를 직접 폐기하지 않는다. chat은 email→ID의 정확1개 조회를 하지만 visible/현재 DB role를 확인하지 않으며, 이미 열린 세션은 exp/참여자·목적지 검사와 transport expiry로 관리된다. 따라서 backend 상태 조회만 수정하거나 frontend 연결을 닫는 것으로 모든 토큰·다중 인스턴스 실시간 회수를 보장할 수 없다.

RF022 이후 UserContext는 accessToken도 삭제한다. 과거 재평가의 삭제 주석 설명을 현행 사실로 재사용하지 않았다. MyInfo의 로컬 삭제 선행/잘못된 최상위 Authentication fetch 옵션·응답 미대기, AdminMenu 로컬 logout, roleToken/늦은 refresh 재저장의 경계가 남는다. 실제 브라우저 결과와 운영 악용 여부는 이번에 재현하지 않았다.

원시 `rg`/파일 읽기/Git/웹 응답은 도구 대화에만 있으며 별도 raw 로그·source snapshot·hash manifest·XML은 생성하지 않았다. 이 README는 정적 관찰의 서술 증빙이다. 기존 RF005/022 등의 실행 건수는 해당 작업의 이력이며 RF040 실행 결과로 합산하지 않는다. 후일 검증은 새 기준 HEAD/환경/합성 fixture/명령/exit code·XML·콘솔·source hash를 보존하고 이번 정적 기록을 소급 대체하지 않는다.

## 공식 참고

2026-11-11 [RFC9700 §4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14), [RFC7009 §2](https://www.rfc-editor.org/rfc/rfc7009.html#section-2), [OWASP JWT](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html), [Spring Security logout](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)를 열람했다. 회전/회수·식별자/전파·LogoutFilter/CSRF 설계 근거이며 저장소 코드나 운영이 그 규약을 이미 만족한다는 증거가 아니다. Spring 페이지 현재판은 프로젝트 버전 실행을 대체하지 않는다. 원문 snapshot은 별도 보존하지 않았다.

## 미실행·추가 검증·재개 조건

| 추적 | 미실행 이유·현재 한계 | 후속 방법/재개 조건 |
|---|---|---|
| RF040-상태 | 분석 요청만, 변경 전 재사용·탈퇴/강등 실행 재현 없음 | 구현 착수 범위 후 실제 parser·합성 키/clock·repository 대역과 RF005/020/041/042 회귀 |
| RF040-회수 | 세션/전체기기·재활성화·저장소/장애/동시 refresh 정책 미결 | 정책 및 필요한 schema/store 승인 후 공유 원자 대역 경쟁·실패 및 ledger 물리 CAS/복구 |
| RF040-실시간 | 기존 chat 연결·outbound/idle·다중 인스턴스 폐기 미검증 | RF022 경계 재사용, 회수 지연/이벤트 유실 정책 확정 후 두 context 및 실제 wire |
| RF040-통합 | 서비스 접근/실행 미요청, 운영값 미조회 | RF016 안전 신원·소유 fixture/key·외부차단·정확 cleanup 및 실행 범위 확보 후 DB/HTTP/browser/key·혼합버전 |
| R5-정책/RF030/RF029 | cookie/CSRF/CORS·UI 캐시·팝업·늦은 응답 별도 범위 | 공동 계약 후 명시 logout과 로컬 정리를 구분해 실제 브라우저 회귀 |
| RF040-성능 | 사용자 유예 | 별도 재개 지시와 대표 동일 조건 비교 계획, 이번 개선 주장 없음 |

계획 문서 독립 리뷰와 BACKLOG/LOG 통합은 루트 담당 후속 범위다. 분석 완료가 기능/운영 완료 또는 후속 자동 착수 승인은 아니다.
