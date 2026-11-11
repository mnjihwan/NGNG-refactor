# RF-035 증빙

- 최신 상태: **동일 PR의 공동 최소 구현·오프라인 선택 검증·독립 리뷰 범위 완료(공동체인6건 최종 대조 포함), 물리DB·운영 개방/배포 미완료 및 차단 유지** (2026-11-11). 앞선 독립 결과는 당시 범위이며 최신 후속 연결은 최하단 절을 따른다.
- 신규/기존 거래를 구분하지 않고 과거 차감 여부가 불명인 거래를 차단하라는 추가 사용자 정책은 RF035만으로 구현되지 않았다. PR·머지가 독립적으로 이뤄져도 공동 출시 조건은 유지한다.

## 초기 분석 단계 이력 — 아래 최신 구현·실행 절이 우선

- 기록일: 2026-11-11. 상태: 정적 분석·계획 작성, 실행 재현/구현 미착수. [작업 명세](../../tasks/RF-035.md)에 사실/가설·호출 경로·선택안·검증 계획을 기록했다.
- 기준: main `82aa7279886385bc0974ae78679ab492447f4135`, 시작 `git status --short`는 `?? .tmp/`. 기존 임시 파일 보존. 다른 담당 RF038와 병행하며 본 RF035 두 문서 외 수정하지 않는다.

## 수행 범위·근거

PowerShell에서 `git rev-parse HEAD`, `git status --short`, `rg --files`, `rg -n`, `Get-Content`로 AGENTS/README/TEMPLATE/BACKLOG/재평가 순서2 및 RF003/004/021/023 기록과 결제·충전·상품·거래 controller/service/repository/entity/DTO, 인증 필터, 프런트 채팅 가격 입력/요청·마이페이지 결제/충전 호출을 열람했다. RF016은 현재 checkout에 파일이 없었으며 별도 초안 PR19 `git show 290248e:docs/refactoring/tasks/RF-016.md`로 A 제한 기반 완료/B DB 미실행·사용자 쓰기 미승인 상태를 확인했다. checkout/merge하지 않았다.

핵심 정적 결과: payment는 body paymentCost를 차감하며 payProductId를 설명에만 사용한다. 충전은 body addCost와 type을 기록한다. point 저장은 초기화/충전/결제 세 곳으로 검색됐다. 거래 요청 price는 편집 가능한 클라이언트 입력이며 판매자가 수락할 때 거래에 복사된다. 직접 거래 생성은 같은 확정 가격 출처로 간주할 수 없고 인가 작업이 사용자 보류 상태다. ProductService 캐시/상품가격 수정, 사용자 잔액 잠금과 상품 수락 잠금의 범위를 구분했다. 실제 위험 악용/운영 손실을 실행 입증한 것은 아니다.

공식 자료를 웹으로 열람했다: [OWASP Business Logic Security](https://cheatsheetseries.owasp.org/cheatsheets/Business_Logic_Security_Cheat_Sheet.html), [Transaction Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html), [API1:2023](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/). 서버 금액·객체 권한·실행 전 검증의 일반 근거이며 무료 포인트/협상가/직접 거래 사업 정책을 결정하지 않는다.

## 후속 사용자 정책과 남는 위험

기록 buyer만 결제, 서버 거래가 기준·클라이언트 금액 불일치/거래 없음 거부, **직접 POST 생성 거래 결제 허용**, 외부 충전 인증 별도 분리·기존 양수 충전 유지가 확정됐다. 명세의 후속 확정 절이 초기 선택안보다 우선한다. 수락 요청을 일반 필수 조건으로 구현하면 안 된다. transaction_details에 출처/수락 FK가 없어 수락 요청 부재를 직접 생성의 확실한 증거로 취급할 수 없다.

직접 생성은 RF021 인가 보류여서 buyer/price 조작 가능 경로가 남으며 거래 저장값 조회만으로 판매자 승인·정당한 가격을 입증하지 못한다. 양수 충전은 입금 증명 없이 유지된다. 금액 오염·거래 선점/방해·임의 적립·RF036 반복 차감/RF037 상태 불일치 가능성이 남고 실제 피해는 미재현이다. 이 범위 제한을 전체 금전 안전 완료로 표현하지 않는다.

## 공통 계획 리뷰 반영

사용자 허용 범위에 따라 accepted request/seller-owner/forSale/상태1 검사를 RF035의 추가 전제로 넣지 않는다. 동일 서비스 transaction에서 기존 상품 PK scalar lock→거래 ID/consumer/price native scalar current FOR UPDATE 목록→0/복수·buyer/price 검증→기존 사용자/최신 포인트 locks→이력 저장으로 고정했다. JPA eager graph/이전 snapshot·캐시 재사용을 피한다. RF004/038 writer 순서 및 물리 교착/rollback은 미검증이다.

`rg -n 'ExceptionHandler|ControllerAdvice' NGNG-backend/src/main`으로 관리자 두 handler 외 points 공통 handler 부재를 확인했고 기존 ResponseStatusException을 유지하기로 했다. 명세에 입력400/인증401/상품·거래부재404/타인403/복수·불완전·저장가격오류·금액불일치409/잔액부족 기존400을 고정했다. UI의 !res.ok throw 및 실패 후 PUT 미호출을 새 오류별로 검증할 계획이다. 이 조사/설계는 실제 오류 직렬화나 SQL 실행 증거가 아니다.

## 보존과 한계

- 탐색 출력은 대화 tool 결과만 남고 별도 원시 파일은 없다. 처음 잘못된 repository 경로 및 main RF016 파일 읽기 실패, PowerShell brace 경로 파싱 실패는 뒤의 올바른 경로 열람으로 정정했다. 일부 넓은 문서 출력이 잘렸으므로 핵심 현황은 좁은 검색과 실제 소스로 확인했다.
- 테스트/빌드/DB/서비스/브라우저/HTTP/성능 실행 없음, 따라서 XML/console 실행 증빙 없음. source snapshot/hash를 별도 보존하지 않았으며 HEAD+경로를 후일 재탐색 기준으로 제공한다. 후일 실행은 새 결과로 기록하고 이번 미검증을 소급 통과로 바꾸지 않는다.
- 정적 문서 점검은 코드/정책의 기능 통과가 아니다. 기존 RF003/004 DB 수치·RF016 A 수치를 RF035 결과로 합산하지 않는다. 운영 gateway/외부 writer/실제 DB 제약·기존 오염 데이터·외부 소비자는 확인되지 않았다.

## 남은 검증·재개 조건

| 항목 | 미실행 이유 | 추가 방법/재개 조건 | 추적 |
|---|---|---|---|
| 확정 오류·잠금 구현/검증 | 공통 계획 리뷰 반영 완료, 실행 미착수 | 명세의 오류별 무쓰기·UI 후속차단·잠금 순서 대역 검증. 직접 생성 인가 자동 재개 금지 | RF035/RF021 |
| 변조액·상품·타인거래·충전 재현/회귀 | 분석 전용 | 합성 인증+repository 대역 context, before/after 분리, 무쓰기와 전체 응답 검사 | RF035 |
| 경쟁·commit/rollback | DB 접속 금지, RF016 B 물리 미검증 | 환경 gate·허용 범위 확보 후 두 연결/barrier·소유 fixture·정리 | RF035/RF016 |
| 실제 HTTP/프런트·실패 복구 | 구현/환경 미착수 | 오류 계약 확정 후 브라우저와 비정상 응답·응답 유실 검증 | RF035/RF037/RF045 |
| 재전송·상태 원자성 | 독립 범위 | RF035/038 결정 뒤 RF036/037 각각 명세 | RF036/RF037 |
| 독립 리뷰/최종 diff | 작성 단계 | 통합 담당의 독립 문서 검토, diff 범위/링크 확인 | RF035 검토 대기 |
| 성능/정산·환불 | 범위 밖·사용자 성능 유예 | 별도 지시/업무 계약 | 자동 착수 금지 |

공통 BACKLOG/로그는 통합 담당이 위 잔여와 연결한다. 본 문서는 구현 승인 또는 운영 결제 안전 완료 선언이 아니다.

## 2026-11-11 최소 구현·격리 실행

사용자 승인 후 운영 수정은 `PointHistoryService`, `PaymentPointRequestDto`, `TransactionDetailsRepository` 세 파일과 필요한 선택 테스트/fixture·본 증빙이다. 새 schema/dependency, 직접 생성 인가/상태 정책·충전 외부 증명·멱등성/원자성 기능은 변경하지 않았다. RF038 분석 문서와 시작 .tmp를 보존했다. 공통 BACKLOG/LOG 상태 연결은 통합 담당이 수행한다.

입력/국소 인증과 실제 AuthService email→DB ID 검증 후 **기존 상품 PK scalar FOR UPDATE→거래 ID/consumer/price native scalar FOR UPDATE 목록→0/복수/ID·buyer·금액→기존 user/latest point locks→서버 저장가로 append**를 동일 payment write advice에서 수행한다. 유일 거래의 non-null buyer만 결제하고 body가 양수 저장가와 정확히 같아야 한다. seller/현재 owner·visible·forSale·수락 요청·상태 조건은 추가하지 않았다. 상세 [구현 결과/오류 순서/한계](../../tasks/RF-035.md#2026-11-11-승인-후-최소-구현격리-검증-결과), [실제 명령](commands.md).

| 보존 경로 | 실제 결과/자료 |
|---|---|
| [before](implementation/before/) | 89/89, failure/error/skip0. 취약 특성6(실제 chain에서 nullable/0/-1/42/999999 product와 body1 차감5, 외부증명 없는 양수 충전1)+기존83. 당시 before 전용 Java·운영4개 및 기존 테스트 source/hash 포함 |
| [wrapper-denied](implementation/wrapper-denied/) | sandbox Gradle cache .zip.lck 접근 거부, exit1·테스트 미시작. 이후 허용된 동일 명시 선택 명령으로 실행 |
| [after-initial](implementation/after-initial/) | backend172 중167통과/5 fixture실패, failure 외 error/skip0. frontend19 중14통과/5 기대문구 fixture실패. XML7·console/TAP·당시 관련 테스트 source/hash 보존. 실패 이유는 아래 |
| [query-fixture-failure](implementation/query-fixture-failure/) | backend179 중178통과/1 query helper fixture실패, error/skip0. XML8·console·당시 helper source/hash 보존 |
| [after](implementation/after/) | **179/179, failure/error/skip0, exit0**. 서비스42+실제chain48+SQL/매핑 선언3, 기존포인트86. XML8·console·선택 source/hash. 프런트 **19/19, skip0, exit0**, TAP·실행 TS/handler/test source/hash |
| [regression](implementation/regression/) | **317/317, failure/error/skip0, exit0; compileTestJava·bootJar/jar·assemble 성공**. RF004 acceptance28+creation15, RF021 service23+chain60, RF023 service40+chain33, 기존 TokenFilter72+SecurityChain46. XML8·console·선택 source/hash·jar SHA256 |
| [환경·기준](implementation/baseline.json) | main82aa727, 당시 구현 시작 untracked 목록. Java는 [JDK release](implementation/java-release.txt), Node [버전](implementation/node-version.txt), wrapper [버전](implementation/gradle-version.txt). 개인경로 정제 |

before는 잔액1000의 실제 chain으로 상품·거래 조회 없는 기존 차감을 확인한 것이며 계획상의 잔액10000/특정 취소·타인 DB행을 전부 준비해 재현한 것은 아니다. 변경 후에는 서비스8000/잔액10000 및 chain 역할·거래 구조/금액별 거부를 검증했다. 실DB/운영 피해를 재현했다고 표현하지 않는다. before89와 after179/회귀317을 더해 고유 건수로 집계하지 않는다. 최종 after179와 별도 회귀317은 클래스 중복이 없다. 전체 suite의 통과가 아니다.

서비스42는 실제 AuthService와 저장소 대역을 사용하고 새 오류 순서·무point 접근/append·Long 값 비교·잠금 호출 순서·동일 Spring write advice·save 실패 rollback advice를 확인했다. 실제 chain48은 SecurityConfig/TokenFilter/JWT verifier/provider/AuthService/controller/service/Boot Jackson을 실제로 사용하고 repository만 대역으로 두었다. 로그인 AuthenticationConfiguration/Manager는 이 요청들에서 미사용 대역이다. USER/ADMIN/UNCONFIRMED_USER와 buyer/owner/제3자, 인증 이상/DB identity 부재·저장소 장애, 400/401/403/404/409, 8필드/동액/부족, refresh fallback·헤더를 검사한다. 결제 거부는 point 잠금/append 전 멈추고 상태·상품 save/delete·요청 및 상태 collaborator를 호출하지 않는다. 실제 SQL/무쓰기/commit/rollback 관측을 뜻하지 않는다.

실제 직접 POST→저장된 scalar→결제6건은 상품 가격 변경·seller null/visible=false/forSale=true·상태1~5/null이 새 전제가 아님을 확인한다. 저장가1도 결제되는1건은 직접 생성 인가/가격 확정 보류의 잔존 위험을 재현하는 것이며 안전성 통과로 이름 붙이지 않는다. accepted request→payment까지 같은 fixture의 연속 HTTP는 미실행이며 RF004 가격 복사와 RF035 scalar 비교를 각각 검사했다. SQL3건은 mapped column/native current FOR UPDATE 선언·목록/alias·BigInteger→Long/null Spring projection 변환 검사로 한정한다. 실제 MySQL driver/query/락/인덱스/current-read 보장은 별도다.

프런트는 실제 API와 BuyProductDetails handler를 TypeScript transpile 후 hook/fetch 대역으로 실행한다. 새 HTTP400/401/403/404/409별 기존 access header 처리·같은 generic 알림·오류 body를 성공 파싱하지 않음·후속 상태 PUT0을 확인했다. 실제 브라우저/lifecycle/SWR/서버 HTTP 결과가 아니다. 운영 프런트 변경이 없어 lint/tsc/production build는 추가 실행하지 않았다.

### 실패 보존·정제

- after-initial backend5 실패는 기존 잔액부족 fixture 저장가200/요청101 불일치1과 Mockito `when` 재stub이 기존 answer를 실행한4(DB auth principal assertion3, 트랜잭션 안 save assertion1)이다. fixture의 가격을 요청101로 맞추고 doReturn/doThrow로 재stub했다. query1 실패는 Product.productId에 @Column이 없다는 기존 암묵 매핑을 helper가 처리하지 못한 것으로 helper만 수정했다. 운영3파일은 최초 after 구현부터 최종 실행까지 변경하지 않았다.
- frontend5 실패는 기대 generic 알림을 잘못 적은 테스트 오류였으며 실제 기존 `결제를 완료하지 못했습니다. 포인트와 거래 상태를 확인해 주세요.`로 기대만 고쳤다. 최초 실패 source/TAP를 보존했다. 실패를 통과로 소급 치환하지 않는다.
- 첫 생성 시 Python WindowsApps launcher 실행 실패와 잘못된 상대 출력경로 오류는 도구 대화에만 있고 별도 raw가 없다. 테스트는 시작되지 않았다. java metadata shim 무출력은 중단 후 JAVA_HOME의 release 파일 읽기로 확인했으며 빈 java-version.txt는 당시 수집 실패를 뜻한다.
- 미정제 raw는 Git 제외 `.local-runtime/rf035-results/`에, 정제 공유본125개는 `implementation/`에 보존했다. [redaction manifest](redaction-manifest.json)는 파일별 raw/shared SHA256을 구분한다. [정제 스크립트](prepare-evidence.cjs)는 원시 자료를 수정하거나 재실행하지 않는다. hostname/개인경로, 기존 보안 테스트의 parameter 이름에 들어간 합성 JWT49개와 행끝공백을 정제했다. 합성 key/email fixture는 남기고 실자격값/사용자자료는 사용하지 않았다.
- 최초 정제의 angle marker가 XML attribute를 깨뜨렸으므로 marker를 각괄호로 고친 뒤 공유 XML36개를 모두 다시 파싱했다. 기능 테스트 재실행과 구분하며 raw/XML 판정 수치는 변하지 않았다. 최종 [정적 검증](verification.json)에서 실제 개인경로0·JWT literal0·소스/문서 후행공백0, source hash55개 및 raw/shared manifest125개 일치(불일치0)를 확인했다. 정제 스크립트 자체의 경로 정규식4개는 개인경로가 아니다.
- after/source-hashes와 regression/source-hashes는 실행 당시 선택 source만 보존하며 저장소 전체 snapshot을 뜻하지 않는다. before 전용 Java는 source에서 제거하고 before/source에 보존했다. 실패 단계의 source 보존 범위는 각 hash 목록에 한정하며 누락 source를 나중 snapshot으로 소급 대체하지 않는다.

### 미실행·재개 조건과 운영 적용 차단

| 잔여·별도 후보 | 현재 근거·이유 | 재개 조건 |
|---|---|---|
| 물리 SQL/잠금/경합·commit/rollback·다른 writer | 이번 DB 금지·RF016 B unresolved/쓰기 미승인. InOrder/advice·정적 SQL 검사는 물리 증명이 아님 | 격리 identity/소유 fixture·cleanup 및 명시 범위 확보 후 두 연결/barrier·INSERT 뒤 실패·SQL/commit/timeout 관측. RF003 MySQL fixture는 생성자만 맞췄고 실제 상품/거래 repository 및 fixture 재설계 필요 |
| Boot HTTP/error dispatch·Servlet OSIV·브라우저·외부 소비자 | 격리 MockMvc/handler만 실행 | 안전 서비스/인증/실제 오류 dispatch·소비자 목록을 마련한 별도 검증 |
| 신규/기존 모두 과거 차감 불명인 거래 차단, 거래당1회 차감409/조회·결제/상태 원자성 | 추가 사용자 정책. RF035는 이 gate나 RF036/037을 구현하지 않아 중복/과거 재결제·상태 불일치 가능 | RF036/037 별도 구현·격리 검증 + RF038 상태 계약/공통 잠금 순서 대조까지 **공동 출시 조건 충족 전 RF035 단독 운영 적용/배포 차단**. PR·머지와 무관 |
| 직접 생성 인가/가격 확정·외부 충전 증명 | RF021 직접 생성 사용자 보류, 양수 충전 유지 승인 | 별도 재개/업무 계약. 필요 schema/dependency는 별도 승인. 이번 범위에서 자동 재개하지 않음 |
| 전체 suite·성능/정산·환불 | 외부 연결 기존 통합/SSG 제외, 성능 범위 밖 | 별도 지시·안전 환경/대표 비교 또는 업무 요구 |

독립 코드/증빙 리뷰는 대기 중이며 이 담당자가 스스로 완료로 기록하지 않는다. 공통 BACKLOG/LOG는 통합 담당이 잔여·배포 차단 조건을 연결한다. RF036/037/038 구현·완료 또는 운영 결제 안전 완료로 확대하지 않는다.


## 2026-11-11 후속 공동 통합·현재 완료 범위

**동일 PR의 공동 최소 구현·오프라인 선택 검증·독립 리뷰 범위 완료(공동체인6건 최종 대조 포함), 물리DB·운영 개방/배포 미완료 및 차단 유지**. 브랜치는 `refactor/payment-integrity-stage2`이고 RF035/036/037/038·프런트를 같은 PR/릴리스 경계로 다룬다. 이 문서의 초기 분석·독립 구현 당시 “대기/미연결/미구현”은 당시 이력이며 아래 현재 결과를 소급 의미하지 않는다. 기존 RF035 원시 XML/console·실행 당시 source/hash·중간 실패를 덮어쓰거나 최신 source로 재생성하지 않았다.

기록 buyer·거래 price 권위/요청 금액 일치·직접 생성도 같은 조건·기존 양수 충전/8필드 응답을 유지한다. 후속 공동 통합은 기록 seller/product/status scalar 완전성과 검증 gate·단일 차감/상태2 commit을 추가했다. RF035 당시 seller/status를 요구하지 않았던 direct-create 특성은 그때의 실행 이력이며 최신 결제 허용 조건은 완전한 기록 tuple·status1·검증 증거다. RF021 직접 생성 인가·임의가격 오염 위험은 별도 미해결이다.

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
