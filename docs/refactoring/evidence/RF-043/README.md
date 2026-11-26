# RF-043 분석·구현 검증 증빙

## 2026-11-26 RF-040 병합 후 독립 후보 검증

원격 `main`과 파일 차이가 0인 RF-040 `f38a127f6c737e23884b9f15fe0fb558e3db86e7`의 추적 트리를 `git archive`로 `.tmp/rf043-standalone-20261126`에 풀었다. 원본 로컬 `main`은 `be5c615baabe921501964dcd0e3fd84fc252942a`다. 추출본에 원본 작업트리의 가입 변경 30개를 복사하고 동일 아카이브를 독립적으로 풀어 파일 바이트를 비교했다. 기존 15개 수정·15개 추가·삭제 0개이며, RF-016의 가입 메일 확인과 fixture 구성 두 파일이 포함된다. 그 밖의 소스와 RF-040 운영 보안 코드는 아카이브와 같다. 후보 2,180개 파일의 전체 SHA-256은 `98a21e6de123d4ef0ea1fc0c87ddeedf6db2f861e93956eb1f402d26e0b3078b`다. 이 값은 정렬한 상대 경로·각 파일 해시의 결합으로 계산했다. [기준 트리·전체 후보 해시·변경 경로별 해시](standalone-20261126/candidate-files.json).

통합 담당의 GitHub 비교에서 기준 `f38a127` 대비 원격 `main`은 커밋 1개 앞·0개 뒤이며 `files=[]`였다. PR #25 병합 직후 보고된 원격 커밋은 `18e04243ca66a2d8e6856904bf417b2db4d4d073`이다. 후보 기준은 로컬에서 확인한 `f38a127` 트리다.

| 실행 | 결과와 보존 위치 |
|---|---|
| 백엔드 첫 시도 | 기본 Gradle 캐시의 배포본 잠금 파일 쓰기가 거부돼 종료 코드 1. 선택 테스트 미시작. [첫 콘솔](standalone-20261126/backend-console.txt) |
| 백엔드 두 번째 시도 | 후보 안에 기존 Gradle 8.7 및 의존성 캐시를 복사한 후 오프라인으로 시도했다. 실행 제약에 의해 Gradle이 이 컴퓨터 안의 자체 프로세스와 연결할 수 없어 종료 코드 1. 선택 테스트 미시작. [두 번째 콘솔](standalone-20261126/backend-local-cache-console.txt) |
| 백엔드 최종 | 동일 후보 및 후보 안의 캐시를 사용했고, 오프라인 실행의 로컬 프로세스 통신을 허용했다. 선택 23개 클래스 **1,261건** 통과, 실패·오류·건너뜀 0. 가입 서비스 35건·격리 Spring MVC 가입 체인 5건·RF-016 격리 12건과 그 외 회귀 1,209건을 포함한다. Gradle은 Java 및 테스트 컴파일, `bootJar`, `jar`, 선택 테스트와 `assemble`의 작업 8개를 실행했다. [최종 콘솔](standalone-20261126/backend-retry-console.txt), [XML 결과](standalone-20261126/xml), [클래스별 집계](standalone-20261126/artifacts.json) |
| 프런트 Node 선택 | 가입 컴포넌트 대역 **51건**, 기존 회귀 9개 파일 **218건**을 별도로 실행해 전부 통과. 실패·취소·건너뜀 0. 기존 설치 패키지는 후보 내부로 복사해 사용했다. [가입 콘솔](standalone-20261126/frontend-signup-console.txt), [기존 회귀 콘솔](standalone-20261126/frontend-regression-console.txt) |
| 프런트 타입 검사·린트 | TypeScript 검사와 캐시 없이 실행한 린트 모두 종료 코드 0. 기존 이미지 경고 3건. [타입 검사](standalone-20261126/tsc-console.txt), [린트](standalone-20261126/lint-console.txt) |

각 콘솔 첫 줄에 실제 명령을 남겼다. 백엔드 실패 두 건은 환경 준비 단계에서 발생했다. 최종 실행의 XML은 이 후보에서 새로 생성했으며, 이전 결합 실행의 37·12·52건 및 이전 프런트 51건은 아래 이력으로 보존한다. 2026-11-26 독립 후보 원시 콘솔 7개 및 XML 23개의 바이트는 Git이 무시하는 `raw/standalone-20261126/`에 보존했다. 공유본에는 프로필 경로·XML 호스트명·Boot 시작 계정·진단용 네트워크 인터페이스 이름을 가렸고, 보안 테스트의 이름 또는 출력에 포함된 합성 JWT 98개도 가렸다. 원시·공유 해시 30쌍과 합성 JWT·호스트를 제외한 XML 테스트 속성, 시각, 소요 시간과 결과 불변을 확인했다. 전체 캐시·빌드 디렉터리는 공유 증빙에 복사하지 않았다. [명령 종료 코드·환경·해시](standalone-20261126/artifacts.json).

서비스 테스트가 사용하는 저장소·발송 대역과 격리 MVC는 실제 Redis·MySQL·HTTP 서버를 만들지 않는다. Lua는 구조를 검사한 범위다. 실 Redis 구문·TTL·서버 시계·원자 소비·복제·복원, 실 MySQL commit/rollback 및 중복·기존 연락처 호환성, 실제 HTTP/브라우저·프록시와 실제 SMTP/SMS, 전체 백엔드 테스트·Next 배포 빌드 및 성능은 미실행이다. 재개하려면 승인된 격리 저장소·합성 fixture·정확한 정리 범위를 확보해 TTL 경계와 소비 경쟁·복원·실제 가입 전환을 확인한다. 실제 발송과 성능에는 별도 실행 범위가 필요하다. `signup.verification.enabled` 기본 비활성은 가입을 차단한다. 운영 개방 전 프런트·백엔드 공동 전환, 구 writer 차단, epoch·키 일치 및 운영 장애 절차 확인이 남아 있다.

## 2026-11-26 독립 리뷰 P2 보완

현재 가입 코드와 RF-040을 결합한 상태에서 만료 사전 검사와 이메일 입력 수정 경계를 보완했다. 운영 변경은 `RedisSignupStore.java`와 `thirdPage.tsx` 두 파일이다. 메모리 저장 대역, 가입 검증 서비스 테스트, 실제 프런트 컴포넌트 테스트를 함께 적응했다.

| 실행 | 결과와 보존 위치 |
|---|---|
| RF-040 변경 직후 결합 | 가입 서비스 32·MVC 5로 **37건**, 별도 RF-016 환경 격리 1·Boot smoke 11로 **12건** 통과. 실패·오류·건너뜀 0. 이 실행은 이번 P2 변경 전의 별도 기록이다. 원시 콘솔·XML은 [RF-040 증빙](../RF-040/README.md#2026-11-26-a단계-구현-결과)에 있다. |
| P2 후 백엔드 | `review-fixes/backend-console.txt`, `review-fixes/xml/`의 선택 4개 클래스 **52건** 통과. 가입 서비스 35·실제 MVC 가입 체인 5·RF-016 격리 12이며 실패·오류·건너뜀 0이다. `compileJava`, `compileTestJava`, `bootJar`, `jar`, `assemble`이 실행됐다. |
| P2 후 프런트 | `review-fixes/frontend-console.txt`의 컴포넌트 대역 **51건** 통과, 실패·건너뜀 0. `review-fixes/tsc-console.txt` 및 `lint-console.txt`는 종료 코드 0이며, 기존 이미지 경고 3건이 남았다. |

각 콘솔 첫 줄에 정확한 명령을 보존했다. Windows에서 백엔드 선택 테스트와 `assemble` 실행을 마친 후 기존 프런트 의존성으로 Node 테스트·타입 검사·린트를 순차 실행했다. 로컬 원본은 `raw/review-fixes/`, 홈 경로·XML 호스트명·Boot 시작 로그 사용자명을 가린 공유본은 `review-fixes/`에 보존했다. 원본과 공유본 8쌍의 해시, XML의 호스트명 이외 테스트 속성 및 결과 불변 확인, 수정 소스 5개 해시와 환경은 [이번 실행 자료](review-fixes/artifacts.json)에 있다. 기존 2026-11-11 기록과 이전 매니페스트는 당시 결과로 유지했다.

- 코드나 proof의 저장 만료가 `min(now + 5분, attempt 종료)`를 초과하면 Redis script의 공통 사전 검사에서 `UNAVAILABLE`로 반환하도록 했다. 메모리 대역은 pending/발송 완료 단계의 만료 손상, 두 채널 proof 및 verified 단계에 남은 code의 손상을 각각 주입해 503과 quota·증명·DB 무쓰기를 확인했다. 만료를 복구하면 두 proof를 소비할 수 있었다. 정상 전환에서는 5분 상한을 그대로 수용하며, 검증 성공으로 생성된 proof가 이미 지난 code 만료 후에도 attempt 종료 전까지 유효했다. 만료 상한은 현재 시각에 대한 검사이며 원 발급 시각 이력은 유지하지 않는다.
- 이메일은 최초 발급 요청 전, 첫 예약 무쓰기 429, 발급 성공 후 대상 수정, 최초 503·응답유실·잘못된 성공 응답 뒤 같은 대상의 재시도와 다른 대상 편집을 확인했다. 예약 여부가 불명하면 해당 채널의 증명과 challenge를 지우되 가입 시도와 전화 증명을 유지한다. 이메일 변경 시 해당 시도와 두 증명을 폐기하고 다음 인증 클릭에서 전화 재인증으로 이동한다. 정상 예약 또는 불명 결과의 대상을 기억한 상태에서 받은 429는 기존 추적과 남은 증명을 유지한다.

Lua는 문장 순서와 검사 구조만 확인했다. 실제 Redis parser·TTL·시계·원자 소비·다중 인스턴스와 복원, 실 DB 트랜잭션·실 HTTP/브라우저·발송, 전체 suite 및 성능은 미실행이다. Next 배포 빌드는 실행 범위에서 제외했다. 재개 시 승인된 격리 Redis/DB와 정확한 fixture·정리 범위에서 5분 경계·소비 경쟁·장애를 재검증한 뒤 운영 전환을 검토한다. 기본 비활성으로 가입이 중단되며, 증명 없는 구클라이언트는 차단된다. 활성화는 공동 배포, 구 writer 차단, epoch·키 운영 및 실제 브라우저·발송 검증 조건을 따른다.

아래는 2026-11-11 분석 및 이전 검증 이력이다.

- 기록: 2026-11-11, HEAD `2091fae3b590f0286fe11e8a1354fc27d81e10c6`.
- 범위: [명세](../../tasks/RF-043.md)의 정적 호출관계·신뢰경계·저장/원자성 대안·정책 결정·검증 계획만 작성. 기능 구현 및 실행 검증 미착수. 최초 `.tmp/`, RF024/039/040 문서 미추적 변경을 보존했다.
- 수행: `git status --short`, `git rev-parse HEAD`, `rg --files`, `rg -n`, PowerShell Get-Content로 AGENTS/README/BACKLOG/재평가/TEMPLATE/RF005/RF040 및 가입·발급 DTO/controller/service, User/LoginFilter/SecurityConfig/TokenFilter, Redis config/build.gradle, 프런트 가입 화면/rewrite, RF016·기존 UserControllerTest를 읽었다. Windows wildcard 인수로 `rg .../sign* .../join*` 탐색은 os error123이었고 실제 join 경로로 재탐색했다. 실패 탐색을 검사 통과로 기록하지 않는다.
- 정적 근거: 이메일·SMS code 응답 노출, 브라우저 문자열 비교, final JoinRequest proof 부재와 JoinService 중복검사→저장→초기포인트, 가입 서비스의 code/proof 저장·TTL·원자 소비 부재. 파일/메서드/행과 정책 구분은 명세 표에 있다. 실제 운영에서 공격·발송·가입을 실행하지 않았다.
- 기존 RF016 `newEmailRunsRealServiceButOnlyMemorySenderReceivesIt`는 응답 authNumber와 메모리 메일 내용 일치를 단언한다. 이번에 재실행하지 않았으며 과거 RF016 결과를 RF043 통과 수치로 합산하지 않는다. SMS는 RF016에서 서비스 대역으로만 확인한다. 기존 UserControllerTest의 발송·가입 테스트는 안전 실행 증빙이 아니며 실제 연락처가 있어 이번 실행에서 제외했다.
- 공식 조회: [OWASP 토큰/코드 지침](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html), [Redis transactions](https://redis.io/docs/latest/manual/transactions/), [Redis Lua](https://redis.io/docs/latest/develop/programmability/eval-intro/). 지침을 가입 증명에 적용한 설계 추론과 프로젝트에서 직접 확인한 사실을 구분했다. 외부 문서 원문 사본은 보존하지 않았으며 후일 변경될 수 있다.
- 원시 증빙: 탐색 출력과 웹 조회 결과는 대화 도구 결과에만 존재한다. 별도 console/XML/source snapshot/hash manifest·운영 자료는 생성하지 않았다. 이 README는 분석 기록이며 테스트 raw를 대체하지 않는다. 후일 실행은 당시 HEAD/환경/명령·fixture·raw/hash를 별도 보존하며 이번 결과로 소급하지 않는다.

## 미실행·한계와 재개

테스트/빌드0, DB·Redis/서비스 연결0, 실제 메일/SMS0, schema·운영설정/키 변경0, 성능0. 최초에는 정책 미결이었고 아래 후속 결정으로 해소됐으나 이번 담당 범위는 두 문서 수정이며 안전 실행 환경도 미확보여서 실행하지 않았다. 정확 운영 schema/제약·gateway 제한·외부 전처리·공급자 실제 수신은 미확인이다. 정적 코드만으로 운영 우회 성공 또는 전체 인프라에 방어가 없다고 단정하지 않는다.

## 2026-11-11 사용자 결정·공통 리뷰 반영

이메일+휴대폰 모두 서버 검증, 기존 Redis 선소비 뒤 DB 실패/불명 재인증, code5분·확인5회·동일대상10분3회를 명세 현재 계약으로 반영했다. 기존 Redis 사용과 위 수치는 승인됐으며 신규 production schema/의존성·실발송 승인은 아니다. 최초 대안 표는 이력으로 명시했고 DB ledger를 현재 권고로 읽지 않는다.

추가 문서 설계: 서버 CSPRNG attemptSecret(공개 challenge ID와 분리), proof digest·code HMAC·대상 canonical 결합, rolling 발송 예약/실패 포함, resend로 확인횟수 초기화 금지, verify 성공응답 유실 때 같은 code로 proof 추가발급 금지/재발급, Redis Lua의 두 proof 일괄소비·부분소비 금지, 소비 timeout 시 DB쓰기0·failclosed, DB commit/응답유실 재인증, restore/failover 이후 외부 epoch 갱신 전 개방차단이다. 개인정보 최소 새 API, 기존 열거409/400 잔여, 비밀 메모리/no-store·로그금지, 프런트 성공상태/오류/공동 전환도 구체화했다.

위 내용은 **설계 검토 결과**이며 실행 증빙이 아니다. 코드5분과 별개인 proof최대5분·attempt30분, 세부 canonical/API·오류 계약은 사용자 위임에 따른 최소 설계 선택으로 사용자 수치 승인과 구분했다. 실제 topology·키 공급/epoch 관리와 과거 raw 연락처 중복 호환은 실행 전 확인한다. 메일 service+memory sender, SMS sender adapter 대역, 기존 RF016 SMS service 전체 mock, 실제 공급자 sandbox는 서로 다른 검증 범위다.

후속 작업 시작 당시 다른 담당 RF024/RF016 소스·테스트 변경이 있음을 git status로 확인하고 수정하지 않았다. 이 보완도 RF043 두 문서만 변경하며 별도 raw/XML/hash를 새로 만들지 않았다. 독립 리뷰 및 실행은 후속 범위다.

### 공통 계획 리뷰 필수 보완 반영

명세의 현재 계약에 다음을 추가했다. 이는 문서 검토/설계 보완이며 새로운 실행 결과가 아니다.

- HMAC키·환경 namespace·독립 epoch 누락/오류 및 disabled flag에 issue/verify/JoinService 자체 failclosed. 직접 서비스/관리자 무증명 우회 없음.
- canonical 이전에 ASCII 단일 이메일 mailbox의 dot-atom/DNS domain 제약과 다중주소·표시이름·CRLF·Unicode 원문/모호 domain 거부, 정상 ASCII punycode label 허용. 전화는 기존 UI의 ASCII10·11자리 또는 3-3-4/3-4-4 hyphen 형식을 허용하고 010 전용 prefix 제한을 두지 않는다. 공급자 실제 지원과 인용 local-part 등 이메일 제한의 기존 API 호환은 미검증이다. 발송/quota/검증/저장 공통 canonical, 기존 raw 계정/로그인 호환 미검증·일괄 데이터 정규화 금지.
- consumed terminal·join/resend/verify/늦은 완료 상태복원 금지, 잘못된 secret의 quota/generation/횟수 무쓰기, 동시 verify 단일 proof.
- domain-separated length-prefix HMAC 전체digest와 환경/epoch/목적/채널 분리, challenge NX collision 처리, 동일시각 quota unique reservation member. token digest는 code HMAC과 구분.
- HMAC키/epoch 변경에 따른 quota 초기화 한계를 명시하고 구writer 차단/drain·최소600초 발급중단·새 epoch 동기화 후 재개 절차를 계획했다. restore replay·키 유출 시 짧은 code/대상 대입·Redis 메타데이터 노출은 미해결 운영 검증 대상이다.

추가 검증 항목은 해당 입력/설정 거부 시 무쓰기, terminal 경쟁 두 순서, 동일시각4발급/3예약, collision·HMAC 길이모호성, code5분 정각·epoch 전환/quota 대기를 포함한다. 실제 Redis/failover·epoch/writer drain·기존 계정 호환은 실행하지 않았다. 기존 발급409/가입400 열거 잔여를 별도로 유지하며 실발송은 금지다. 수정 범위는 여전히 두 문서이며 RF024/RF016 코드에 손대지 않았다.

| 추적 | 추가 검증 및 재개 조건 |
|---|---|
| RF043-정책/로컬 | 사용자 확정 계약으로 service/필터+대역·Clock 검증: 5분 정각/5회 경계·rolling10분3회·다른 attempt 파괴0·같은 code 추가 proof0·재전송/역순·동시 소비·DB/발송 실패. 구현/실행 증빙은 아직 없음 |
| RF043-저장 | 기존 Redis 사용 승인·schema 금지. 전용 namespace/실행범위 및 키/epoch 운영 조건 확인 후 실제 Lua 두 proof 일괄소비·TTL·소비timeout DB쓰기0·restore replay 차단 확인. Redis-DB 분산 rollback을 주장하지 않으며 대역 통과는 물리 보장 아님 |
| RF043-통합/릴리스 | RF016 전용 신원/fixture/key·외부발송 차단·정확 cleanup 및 별도 실행 허용 후 실제 HTTP·브라우저·다중 인스턴스·복원/응답유실·공동배포/구writer 차단 검증. 공급자 sandbox/수신자 승인이 없는 실제 발송 금지 |
| RF032/RF033/RF040 | 중복가입/UNIQUE·PII 로그·로그인 토큰 정책은 별도 명세 범위로 추적하며 자동 착수하지 않음 |
| RF043-성능 | 미측정·개선 주장 없음. 별도 지시와 대표 동일조건 비교 계획 후만 재개 |

독립 문서 리뷰 및 BACKLOG/LOG 통합 반영은 통합 담당 후속 범위다. 기존 Redis 사용 승인은 사용자 결정으로 확보됐지만 이 두 문서 작성은 기능 완료나 신규 schema/의존성·운영 발송 승인을 뜻하지 않는다.

## 2026-11-11 구현·격리 실행 이력

최초 두 문서 분석만 수행한 위 이력과 구분한다. 사용자 확정 정책과 통합 담당 지시에 따라 RF043 backend/signup frontend·선택 테스트를 구현했다. 신규 dependency/schema·운영 설정/키/epoch·실제 DB/Redis/발송·성능·commit/push는0이다. 현재 판정은 **격리 로컬 기능 통과, 독립 최종 리뷰 대기, 물리/운영 개방 차단**이다. 전체 suite나 배포 승인으로 해석하지 않는다.

### 현재 계약과 실제 구현

`POST /join/auth/attempt`는201 JSON `{attemptId,attemptSecret,expiresAt}`를 준다. 이어지는 기존 발급/신규 verify/최종 join은 **JSON body의 attemptSecret**을 유일한 비밀 전달원으로 사용한다. 계획 초기에 남아 있던 X-Signup-Secret header 표현은 사용자 지정 요구가 아니었으므로 frontend와 일치한 body 계약으로 명세에서 정리했다. header와 body 중 하나를 임의 fallback하는 경계는 없다. HTTPS/TLS·프록시/APM/요청 body 미수집·no-store는 운영 전제이며 실제 proxy/APM/TLS 검사 결과가 아니다. 기존 CORS/CSRF 정책을 이번에 바꾸거나 body secret만으로 CSRF/남용 방어 전체를 해결했다고 하지 않는다.

code는 발급 응답에서 제거해 발송 내용에만 두고, attemptSecret/proof는 각 최초 성공 JSON에만 전달하며 URL·로그에 넣지 않는다. request/proof/예약 toString은 redacted, signup name/email 로그는 제거했다. 프런트는 secret/proof를 메모리 state에만 보관하고 최종201 확인 시만 login으로 이동한다. 최종 실패/불명에는 두 proof와 attempt를 버리고 login 또는 재인증을 안내하며 같은 증명 자동 반복을 차단한다.

`SignupCrypto`는 별도 Base64 HMAC키 최소32byte와 allowlist 환경(local/development/test/staging/production)·안전문자 epoch16~64자·enabled를 매 요청 gate로 확인한다. 설정 property는 `signup.verification.enabled/environment/epoch/hmac-key`이며 실제 값을 추가하지 않았다. enabled 기본 false는 가입 중단이고 기존 무증명 JoinService로 fallback하지 않는다. 외부 복원 밖에서 epoch를 통제하고 과거 epoch를 재사용하지 않는 운영 보장은 별개다.

Lua는 Redis TIME과 명시 만료를 확인하며, 동일 slot `{signup}`에서 발급 예약/quota/generation, verify와 두 proof 단일 consume를 수행한다. attempt HSETNX·challenge SET NX 예약과 고유 quota member를 사용하고 충돌3회 실패는503이다. secret의64자리 digest 비교는 mismatch byte에서 조기 반환하지 않는다. Redis에 raw 연락처·secret·proof·code를 저장하지 않으며 target/code는 domain-separated 길이명시 HMAC-SHA-256 전체digest, 고엔트로피 secret/proof는 domain-separated SHA-256 전체digest다. 키 이름·시각/횟수·접근패턴 메타데이터와 HMAC키 유출 시 짧은 code/대상 대입 한계는 남는다.

### 실행 환경·명령과 보존

Java17.0.10, Gradle8.7, 기존 npm/node·기존 캐시만 사용했다. backend Gradle은 RF039/044 담당과 직렬 조율했다. 최초 wrapper-cache lock sandbox 거부를 `before/backend-console.txt`에 보존하고, 이후 필요한 offline 선택명령만 자동심사 상승으로 캐시 lock 접근을 허용받았다. 외부 서비스 접근/네트워크 dependency 설치 승인이 아니며 실제 접속0이다.

변경 전 실행은 현재 작업트리를 되돌리지 않았다. `git show HEAD:<path>` 읽기 전용으로 HEAD `2091fae3b590f0286fe11e8a1354fc27d81e10c6`의 RF043 backend10/frontend3 source를 `before/source`에 복사했다. `before/baseline.init.gradle`은 본래 signup10개 source와 신규 signup 협력자만 컴파일에서 제외하고 보존 원본10개를 대체하며, 별도 build/test source와 빈 resources를 사용한다. 비RF043 공통 소스는 실행 당시 작업트리였으므로 저장소 전체 HEAD 통합 기준선이라고 표현하지 않는다. Gradle/원본 code에는 외부 config를 로딩하는 테스트가 없다. baseline build artifact는 raw XML 보존 뒤 **본 작업 소유의 정확한 evidence build 경로를 확인하고 제거**했으며 다른 .tmp나 기존 결과는 삭제하지 않았다.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
# cwd NGNG-backend, 독립 보존 원본 sourceSet
./gradlew.bat test --offline --no-daemon --rerun-tasks -I ../docs/refactoring/evidence/RF-043/before/baseline.init.gradle
# cwd NGNG-backend, init script 없는 정상 sourceSet
./gradlew.bat test --offline --no-daemon --rerun-tasks --tests com.ngng.api.user.signup.SignupVerificationTest --tests com.ngng.api.user.signup.SignupSecurityChainTest --tests com.ngng.api.isolation.Rf016EnvironmentIsolationTest --tests com.ngng.api.isolation.Rf016IsolatedBootSmokeTest
./gradlew.bat assemble --offline --no-daemon --rerun-tasks
# cwd repository, 보존 원본/수정 후 실컴포넌트 hook+fetch 대역
$env:RF043_BEFORE = '1'; node --test NGNG-frontend/tests/rf043-signup.test.cjs
Remove-Item Env:RF043_BEFORE
node --test NGNG-frontend/tests/rf043-signup.test.cjs
# cwd NGNG-frontend
npm.cmd run lint -- --no-cache
node node_modules/typescript/bin/tsc --noEmit --incremental false
```

| 실행 | 실제 결과와 파일 | 해석 한계 |
|---|---|---|
| before backend |2통과·fail/error/skip0, `before/backend-retry-console.txt`, `before/xml` | 실제 원본 EmailAuthService→memory mail code와 HTTP응답에 주는 code 동일, 원본 JoinService에5필드만 넣어 User/Point 쓰기 재현. SMS SDK·실DB·실HTTP 실행 아님 |
| before frontend |2통과, `frontend/before-console.txt`, 보존 원본3파일 | SMS response code를 로컬 비교하여 통과·verify요청0, 최종400이어도 login 이동. 실제 브라우저 아님 |
| initial after |43중42통과·fixture NPE1, `after/initial-console.txt`, `initial-xml`, `initial-source/SignupVerificationTest.java` | Mockito save 재stub이 기존 answer에 null을 넣은 test fixture 오류. doThrow로 고친 뒤43통과; 최초 실패를 삭제하거나 통과로 소급하지 않음 |
| first success after |43통과, `after/first-success-console.txt`, `first-success-xml`, `first-success-source` | 현재29개 service test 중 추가3개 이전26개 test source. production source는 후속 Lua preflight 보완 전이며 `before-preflight-source`의 signup 구현과 동일 |
| preflight 보완 전 정상 sourceSet |**46통과·failure/error/skip0**, `after/final-console.txt`, `final-xml`, `test-summary.json`, `before-preflight-source` | service29+실제 MVC/Security/JWT/controller/service chain5+RF016 A12. resources/전체test compile은 정상 sourceSet, 실행은 명시4클래스만. 전체 Boot/전체 suite 통과 아님 |
| assemble |초회와 최종 exit0·5task 실실행, `after/assemble-console.txt`, `assemble-final-console.txt` | 패키징 성공, 앱 startup·운영 연결/통합 증거 아님 |
| frontend after |**47통과**, `frontend/after-console.txt`, `after/source` | 기존 Second/ThirdPage 실컴포넌트를 TypeScript transpile 후 hook/fetch 대역 실행. DOM·브라우저·실HTTP/실제 수신 미검증 |
| lint/tsc |둘 다 exit0, `frontend/lint-final-console.txt`, `tsc-final-console.txt` | 기존 img 경고3건 그대로. Next full build/배포 통합 미실행 |

최종 source snapshot35개는 본 작업 main/test/frontend 및 실행 경계의 실제 SecurityConfig/TokenFilter/JWT/PointHistoryService를 포함한다. baseline source13개, initial/first-success/final test source를 구별했다. source/raw console/XML의 SHA-256은 `sha256-manifest.json`에 둔다. 기존 test application.yml·운영 자격값·Gradle 전체 build artifact·mail/code/proof 원문을 별도 원시 자료로 저장하지 않았다. 로그 점검은 signup source에 비밀 log 호출이 없고 보존 console/XML에 생성된43자 token/6자리 PIN 출력이 없는지 확인한 범위이며, 실제 proxy/APM/운영 수집 부재를 입증하지 않는다.

### 테스트 범위와 한계

service 대역 검증은 단일 mailbox/ASCII10·11자리/punycode·거부입력, domain-separated full digest와 길이모호성, 설정누락·disabled·잘못된 secret/대상/증명 무쓰기, pending/5분 정각·299999ms·proof/attempt30분, 비교1~5·다섯번째 성공/실패·resend 누적/채널 독립, rolling600초 정확경계·실패예약·새 attempt 우회, ID/challenge collision, quota 거부의 이전 proof 보존, verify replay 불가, 양쪽 전체소비·terminal 및 join↔resend 두 직렬순서·늦은 발송완료, 모델 동시verify/join 단일승자·모델 동일시각4발급3sender, Redis consume불명/실adapter null·예외의503/DB경로0, DB save/point 실패·중복 뒤 복구없는 소비를 포함한다.

메일은 실제 EmailAuthService와 memory JavaMailSender로 canonical 수신자/발송본문 code/응답 code 없음 및 provider accepted 뒤 결과불명 예외를 검사한다. SMS는 실제 PhoneNumberAuthService와 sender adapter 대역으로 enabled=false/형식거부·접수성공·실패/quota를 검사하며 Nurigo SDK 생성/호출0이다. 실제 RedisSignupStore의 null/예외 mapping은 mock StringRedisTemplate으로 실행했으나 **Lua 자체는 실행하지 않았다**. MemorySignupStore의 synchronized 동작과 동시 테스트는 기대 프로토콜/서비스 호출 경계만 검증하며 실제 Redis parser/TIME/TTL/cluster/복제·원자성을 증명하지 않는다.

실제 MVC/Security/JWT chain은 명시 `AnnotationConfigWebApplicationContext`만 생성하고 system property/environment를 제거하며 Boot/scan/외부 auto-config를 사용하지 않는다. repository·로그인 manager·메일/SMS·Redis store는 대역이고 transaction advice/실DB/RedisTemplate/SDK client/실HTTP서버는 없다. RF016 A12는 기존 poison config 차단·bean inventory·실 Security/JWT·memory mail·close assertion을 보존했다. 새 SignupVerification 협력자는 그 RF016 mail smoke에만 mock으로 추가했고, SMS mock의 미생성 검사는 삭제된 SDK 필드 대신 sender null+adapter bean0으로 적응했다. 따라서 RF016 smoke 자체를 새 서버 증명 로직 검증으로 집계하지 않는다. RF043의31→34건(후속 preflight3개 포함 최종37건)이 그 별도 service/chain 범위다.

### 운영 차단·미실행과 재개

1. 실제 Redis Lua·TTL/시계·자료형/인수 실패·same-slot topology·multi-instance 동시 issue/verify/join/resend·복제 lag·failover/backup restore는 미검증이다. 외부 epoch 통제·구writer drain/차단·최소600초 발급중단·새키/epoch 전 backend 동기화·과거 epoch 재사용 금지도 운영 미확인이다. 허용된 격리 Redis와 정확 key/cleanup·장애 주입 범위를 확인한 뒤만 실제 script/경쟁/복구를 검증한다. 이 gate 전에 가입 개방 금지다.
2. 실DB UNIQUE·동시중복·기존 raw 계정/하이픈 번호/이메일 local case 및 narrowed mailbox 입력 호환·실제 User+Point commit/rollback·Redis선소비 뒤 DB결과불명·commit 뒤 HTTP응답유실은 미검증이다. repository 대역 예외는 실제 transaction commit 실패 재현이 아니다. 별도 허용 identity/fixture·독립 commit/cleanup 검증 뒤 재개한다. RF032 및 RF040 계정세대 한계는 별도 추적이다.
3. 실HTTP/브라우저·TLS/CORS/프록시 no-store/APM 요청 body 비수집·모든 ingress 및 구클라이언트/무증명 writer drain·공동배포/차단 rollback은 미검증이다. 현재 API body의 attemptSecret/proof/password가 수집되지 않는 운영 확인과 실제 공동전환이 필요하다. 기존 발급409/가입400 열거경계는 유지했으며 남용 방어 완료 아님.
4. 실제 SMTP/SMS supplier sandbox/단말 수신·IP/서비스 전체 남용제한·부하/단일slot 집중·성능은 미실행이며 실발송 금지다. 별도 범위/환경/실수신자 승인 및 성능 재개 지시가 필요하다. 후속 자동 착수하지 않는다.

공통 BACKLOG/LOG 통합 반영과 독립 최종 리뷰는 통합 담당 후속이다. 위 잔여의 상태/근거/재개조건은 task 명세에도 같게 남겼으며 raw 보존이 운영 안전 통과나 후일 재실행을 대신하지 않는다.

### 공통 최종 리뷰의 프런트 P2 두 건 반영·재검증

공통 독립 리뷰는 서버 proof 우회는 발견하지 않았고, 프런트의 resend 결과불명 상태와 응답형식 검사를 보완하도록 지적했다. 기존의 로컬 통과를 그 결함이 없는 것으로 해석하지 않는다. 두 수정 후의 최종 범위는 다음과 같다.

- 서버가 재전송을 예약하면 provider503/complete실패/HTTP응답유실 때 이미 기존 proof가 폐기됐을 수 있다. 이제 `signupPost`의 상태가 확인된429만 기존 proof/challenge를 보존하고, 그 외 오류/결과불명/잘못된 응답은 **해당 채널 proof/challenge만 폐기**한다. 반대 채널 proof와 같은 attempt는 유지하여 정상 재발급/검증 또는 명시 재시작이 가능하다. 기존 상태로 완료 표시·가입 자동 재시도를 하지 않는다.
- attemptId/attemptSecret/challengeId/proof 각각은 endpoint별 정확한43자리 base64url **문자열**, expiresAt은 positive safe integer로 검사한다. truthy 숫자/boolean·짧은 token·잘못된 alphabet·배열/빠진 필드·0/음수/소수/unsafe/string 만료·잘못된 JSON은 성공으로 처리하지 않는다.
- 실제 Second/ThirdPage hook/fetch 대역에 old proof+resend503/network/malformed200/429·반대 채널 불변, malformed attempt/각 채널 proof 회귀25개를 더해 **최종47개 failure/skip0**이다. lint(no-cache)·tsc(noEmit/incremental=false)도 수정 후 exit0이다. backend source는 이 리뷰 수정에서 바뀌지 않아 최종46 및 assemble 결과를 그대로 구분한다.

프런트 중간22개 통과의 원시 console과 당시 api/두 컴포넌트/test runner는 `frontend/first-success-console.txt`, `frontend/first-success-source`에 따로 보존했다. 그 이전20개 반복 실행의 각각의 원시 사본은 별도 보존하지 않았다. before frontend 실행 당시 전체 runner의 별도 contemporaneous 사본은 남기지 않았으나 보존 원본 컴포넌트3개와 before2개 결과는 보존했고, snapshot runner의 before 선택분기2개는 이후 변경하지 않았다. 이것은 raw runner 보존 한계이며 현재47결과로 before 결과를 소급 대체하지 않는다. `after/source`는 이 최종47개를 실행한 source로 갱신했다. `raw-secret-audit.json`은 출력 후보를 가린 문맥 점검 범위를 기록하며 운영 로그/APM 미수집 증거가 아니다.

### Lua 입력·상태 preflight 후속 보완 및 최신49건

Java 호출자가 생성한 정상 인수에만 의존하면 Lua 중간의 잘못된 인수/손상 상태 오류가 일부 quota 예약 뒤 발생할 여지가 있어 후속 보완했다. **Redis script 오류가 이전 명령을 rollback한다는 가정을 사용하지 않는다.** 모든 script가 쓰기 전에 key/ARGV 개수, 안전 환경·epoch/type/channel 및 full digest로 된 동일 namespace의 key 구조, 중복 issue key, 채널·43자 token/64자 hex digest 형태를 검사한다. finite integer attempt 만료/남은 최대30분, root state=open, 채널 state별 target/generation/checks/code/proof 및 만료 필드의 필수/금지 조합, quota 최대3개·member token/score 형태를 확인한 뒤만 기존 전환을 실행한다. 자료 누락/알 수 없는 state·NaN/잘못된 count·key alias 등은 무쓰기 failclosed이며, consumed terminal은 변경하지 않는다.

최신 선택은 **49건(service32+실제 chain5+RF016A12), failure/error/skip0**이다. `after/preflight-final-console.txt`, `preflight-final-xml`, `preflight-test-summary.json`이 원본이며, 정상 sourceSet compile 및 최종 `assemble-preflight-final-console.txt`의5task 실실행을 구분한다. `after/source`35개를 이 최종49 실행 소스로 갱신했다. 이전46개와 그 때 source는 `before-preflight-console.txt`, `before-preflight-xml`, `before-preflight-source`, `assemble-before-preflight-console.txt`로 별도 보존했다.

추가3개는 행동대역의 malformed operation 인수/키·namespace/alias 무쓰기, root/채널 자료손상·비정수 TTL/카운터 무쓰기, production Lua 텍스트의 preflight guard가 mutation보다 먼저 배치됐는지 **정적 구조**를 확인한다. 실제 Redis/Lua parser·실제 script 오류/부분쓰기/물리 원자성을 실행한 테스트가 아니다. Lua interpreter/parser도 기존 환경에서 찾지 못했고 dependency를 설치하지 않았다. 따라서 static/behavioral 통과를 실제 Lua 구문·원자 실행 보장으로 확대하지 않고, 앞서 적은 실제 Redis 검증·운영 개방 차단을 그대로 유지한다.

공유 sourceSet 조율 중 통합 담당은 RF039 컴파일이 RF043의 아직 편집 중이던 JoinRequest 필드4개 미정의, 이어 신규 테스트의 Role import 모호성1건으로 중단됐음을 보고했다. DTO 편집 완료와 명시적 Role import로 수정하고 backend 편집 동결·Gradle 직렬화를 지켰다. 이는 본 절의 RF043 직접 선택 실행 결과와 구분하며, 그 중간 컴파일 raw/partial source 사본은 RF043에 별도 보존하지 않은 한계가 있다. 이후 RF043 정상 sourceSet49 및 RF039/RF044의 별도 결과로 과거 실패를 소급 대체하지 않는다.

### 최종 증빙 P2 정제: 원본 bytes와 공유본 분리

이전의 비밀43자/6자리 후보 점검은 **로컬 home 경로·실제 hostname 정제 검사까지 포함한 판정이 아니었다**. 공통 최종 리뷰에서 `before/backend-console.txt`, `after/initial-console.txt`의 로컬 home 경로/URI와 before/after XML의 실제 hostname이 남은 사실을 확인했다. 이전 서술의 console/XML '원본'은 당시 공유 사본에 관한 표현이며, 이 조치 후 공유본과 보존 원본을 아래와 같이 구분한다.

1. 수정 전에 console/XML39개 전체의 **기존 bytes**를 이 작업 전용 `raw/` 아래 상대경로 그대로 복사했다. 이전 manifest/README/audit/summary도 `raw/prior-metadata/`에 보존했다. `raw/`는 이 RF043 evidence의 `.gitignore`로 제외하며 실제 `git check-ignore --no-index`로 확인했다. 공유본에서 개인정보를 제거하기 전에 원본을 보존했고 원본을 덮어쓰지 않았다.
2. `redact-evidence.cjs`는 공유 console/XML에서 home 경로의 native/slash/escaped/URI 표기와 실제 XML hostname만 `[USER_HOME]`, `[HOST]`로 치환했다. **39개 중23개 공유본 변경**이며 source snapshot·코드·테스트 결과/시각을 고치지 않았다. 원본/공유본 각각의 byte 수·SHA-256을 `redaction-pairs.json`에 나란히 기록한다. 원본 경로나 hostname 값은 그 공유 manifest에 넣지 않았다.
3. `verify-redacted-evidence.ps1`은39개 원본 hash가 정제 전 manifest와 같은지, 원본/공유 paired hash가 실제 bytes와 같은지 확인했다. **XML21개**의 testsuite/testcase 속성(hostname 제외), failure/error/skipped 상태 수, timestamp/time 등은 원본과 모두 동일했다. 결과는 `redaction-verification.json`이며 integrity 검사일 뿐 애플리케이션 테스트 재실행이 아니다.
4. `redaction-scan.json`은 ignored raw를 제외한 공유 evidence를 재탐색해 home/실제 hostname/완성형 JWT 후보0을 기록한다. 이전 `raw-secret-audit.json`도 원래 범위와 이번 정제를 구분하도록 보완했다. 이 검색은 특정 패턴/확인된 실제 hostname 범위이며 운영 로그/APM 전체나 다른 작업의 개인정보 정제를 입증하지 않는다. `sha256-manifest.json`은 **공유본만** 대상으로 다시 계산하고 ignored raw는 paired hash로 연결한다.

### 독립 후속 리뷰에서 확인한 시작 로그 사용자명

Spring Boot 시작 로그의 `started by` 뒤 OS 사용자명이 `after/{before-preflight-xml,final-xml,first-success-xml,initial-xml,preflight-final-xml}/TEST-com.ngng.api.isolation.Rf016IsolatedBootSmokeTest.xml` 5개에 남아 있었다. 앞선 공유본에서 이 필드만 `[OS_USER]`로 치환했다. 5개는 이미 호스트를 가린 파일에 포함되어 전체 공유 console/XML 39개 중 원본과 다른 파일은 23개다. 기존 로컬 원본 39개의 SHA-256은 최초 원본 manifest와 일치했고 39개 모두 Git 무시를 확인했다.

앞선 공유본과 이번 공유본을 바이트 단위로 비교해 다른 필드나 파일이 바뀌지 않았음을 확인했다. `redaction-pairs.json`의 원본·공유 SHA-256과 크기를 확인했고, `redaction-verification.json`은 XML 21개의 테스트 이름·결과·실패/오류/건너뜀 수·타임스탬프·소요 시간과 실행 소스 사본 35개의 불변을 기록한다. Spring Boot 시작 로그 사용자명 5개가 모두 가려졌는지도 별도로 검사했다. `sha256-manifest.json`에는 공유 파일의 갱신된 해시를 기록했다.

`redaction-scan.json`의 명세 포함 공유 텍스트 144개에서 홈 경로, 확인된 호스트명·OS 사용자명, 가리지 않은 시작 로그 사용자명, 완성형 JWT, 예약 도메인 이외 이메일 및 URL 인증정보 후보는 0건이었다. 이메일 후보는 테스트용 예약 도메인에서만 발견됐고 한국 휴대전화 형식 및 IPv4 후보는 소스 사본의 합성 테스트 입력이었다. 로그나 XML에서 이 연락처 후보는 발견되지 않았다. 패턴 밖의 식별자와 마스킹 이전 원본의 별도 복사본·운영 로그는 검사 범위 밖이다. Git 무시는 로컬 원본의 접근 권한을 제어하지 않는다.

보존 원본과 초기 manifest에 접근할 수 있는 원래 Windows 프로필에서 당시 39개 쌍의 디렉터리 구조를 별도 복사해 `redact-evidence.cjs`와 `verify-redacted-evidence.ps1`로 다시 점검할 수 있다. 뒤에 추가된 실행은 각각의 매니페스트에 독립 검증 결과를 둔다. 이 후속 조치는 저장 자료를 검증했으며 제품 테스트·assemble·외부 서비스 검증을 새로 실행하지 않았다.

### 당시 source와 병행 RF040 현재 작업트리 차이

`source-drift-review.json`의 읽기 전용 비교에서 당시 `after/source`35개 중 작업트리와 다른 것은 `SecurityConfig.java`, `TokenFilter.java` **2개**였다. 이는 RF04349건을 실행한 뒤 병행 RF040이 보완 중인 소스와의 차이다. 당시 snapshot을 보존했으며 나머지33개는 비교 시점에 hash가 같았다. RF04349건은 **당시 SecurityConfig/TokenFilter와 결합한 결과**다. 이후 RF043+RF040의 정상 가입/공개 skip/거부 경로와 RF016 격리 smoke를 실행한 결과는 위 2026-11-26 절에 따로 기록했다. 당시 증빙 정제 작업은 코드 변경·제품 테스트/assemble 재실행·공통 BACKLOG/LOG 수정 없이 끝냈다.

## 2026-11-26 독립 후보 최종 리뷰와 병합 보류

공통 독립 리뷰에서 RF-040 병합 기준 독립 후보와 증빙을 검토했으며 신규 결함은 발견하지 못했다. 이는 선택된 코드와 증빙 범위의 리뷰이며 실 Redis/DB, 배포 및 운영 검증의 대체가 아니다. 원격 main은 `18e04243`이며 후보 기준 `f38a127`과 GitHub 파일 비교 결과 차이가 없다. 후보 30개 파일의 SHA-256을 후보 목록과 재대조해 모두 일치했다.

기본 `signup.verification.enabled=false`는 가입 발급·검증·최종 등록을 중단한다. 실 Redis Lua/TTL/원자성·복구, 실 MySQL 정합성 및 기존 연락처 호환, 구 writer 차단과 프런트·백엔드 공동 배포/롤백은 미검증 P1 병합 차단 조건이다. 승인된 격리 환경·fixture·정리 범위 및 공동 전환 절차를 확보하고 해당 검증을 수행한 뒤 재평가한다. 실제 발송·성능·브라우저/프록시·전체 테스트와 배포 빌드도 미실행이며 별도 범위가 필요하다. 검증 전 병합·운영 개방을 하지 않는다. 기존 로컬 ignored raw는 보존하며 공유 증빙과 후일 재실행을 구분한다.
## 2026-11-26 PR 독립 리뷰: 소스 정규화 예외 제거

PR 독립 리뷰 P2에서 RF-043 소스·테스트 30개에 추가된 `-text` 예외가 기존 저장소 줄바꿈 정책을 우회한다는 지적을 받았다. 예외 30개를 제거하고 기존 Git 정규화를 적용했으며 공유 증빙의 `-text` 규칙은 원본과 공유 증빙의 바이트 보존을 위해 유지한다. 검증 후보 30개 원본 SHA-256, 기존 Git blob SHA-1, 정규화 후 blob SHA-1·SHA-256을 [개별 비교 기록](standalone-20261126/normalization-review.json)에 남겼다. 30개 모두 원본 SHA-256이 후보 목록과 일치하고 CRLF를 LF로 통일한 내용이 일치했으며, 10개 blob 바이트가 달라졌다. 코드의 실질 내용은 바뀌지 않았다.

기존 선택 테스트·assemble·프런트 검사는 정규화 전 독립 후보에서 실행했다. 정규화 후 blob 바이트로 테스트를 재실행하지 않았으며 위 내용 비교가 실행 검증을 대체하지 않는다. 실 Redis/DB·공동 배포 등 P1 병합 보류와 기존 미실행·재개 조건은 그대로다. ignored raw는 복사하거나 변경하지 않았다.