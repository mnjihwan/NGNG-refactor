# RF-037 분석·설계 증빙

- 일자: 2026-11-11. 기준 HEAD `82aa7279886385bc0974ae78679ab492447f4135`, Windows PowerShell. RF035 구현과 병행하여 분석한 시점의 소스이며 최종 소스 재대조 필요.
- 상태: **2단계 공동 최소 구현·backend942/frontend37 선택 검증 완료, 공동체인6건 포함 독립 최종 리뷰 완료 / 실DB·운영 개방 미완료**. 앞선 분석·미실행 설명은 당시 이력이며 최하단 구현 절이 최신이다. [명세](../../tasks/RF-037.md)의 정책·설계 제안·잔여 조건이 기준이다.
- 허용 변경: `tasks/RF-037.md`, 본 README만. 운영·테스트·DB·설정·공통 BACKLOG/LOG를 수정하지 않았다.

## 수행한 확인과 근거

`git status --short`, `git rev-parse HEAD`, `Get-Content`, `Select-String`, `rg`로 지침·TEMPLATE·BACKLOG·재평가와 RF003/004/006/008/021/023/035/038, PointHistoryService/Repository/entity, TransactionDetailsService/Repository/entity/controller, TransactionRequestService, ProductRepository, BuyProductDetails·my_page API/hook을 읽었다. 시작 untracked `.tmp/`·RF035/038 문서를 보존했다. 초기 RF037 미존재 및 TEMPLATE 상위 경로 오기는 읽기 오류이며 이후 `tasks/TEMPLATE.md`를 읽었다. 일부 긴 탐색 출력은 잘려 중요한 현재 코드·정책을 나누어 재조회했다. 소스 전수 운영 감사나 테스트 실행이라고 표현하지 않는다.

확인된 사실은 두 HTTP 요청/transaction 분리, 결제 뒤 PUT의 Promise 미반환, user→최신 point 잠금, RF004 상품 lock·RC, 상태 PUT의 기존 무제한 변경과 proof 부재다. 운영 장애 빈도/기존 오염 데이터 존재·실제 SQL/스키마·잠금 동작은 추정 또는 미확인이다. RF035 병행 구현의 최종 결과는 이 문서가 인증하지 않는다.

공식 Spring annotation/rollback, MySQL deadlock 문서를 웹 도구로 열람했다. URL·버전 한계와 적용 범위는 명세에 기록했다. 기술 자료는 단일 transaction/proxy·rollback·잠금 규약 설계 근거이며 업무 정책 승인이나 프로젝트 환경 실행 증거가 아니다.

## 보존·해석 한계

- 원시 테스트 XML, 테스트 console, DB 조회 결과, HTTP 캡처, 브라우저 자료는 **없다: 실행하지 않았다**. 읽기 탐색/웹 도구 출력은 대화에만 있으며 별도 raw 파일로 보존하지 않았다.
- 이 명세/README는 서술된 분석 기록이다. RF003/004의 과거 통과 건수를 RF037 성공 건수로 합산하지 않는다. 후일 실행은 당시 환경·커밋·fixture·실패/skip·원시 보존 위치를 별도 기재하고 최초 분석 증거로 소급하지 않는다.
- 문서 diff/공백 검토는 금전 기능·실DB·운영 안전성을 증명하지 않는다. 독립 통합 리뷰 결과는 통합 담당 기록을 따른다.

## 미실행 항목·재개 조건

| 항목 | 미실행 이유 | 추가 검증·재개 조건 |
|---|---|---|
| 결제+상태 실패 주입/회귀 | 현재 분석·명세 전용 | RF037 A안·호환 오류 결정 후 proxy/선택 테스트; RF035/038 회귀 |
| DB atomic commit/동시성/OSIV | DB 금지·격리 fixture 미확정 | RF016 안전 기반·외부차단·소유행 cleanup; 새 연결로 이력/상태 확인, 실제 SQL lock 순서 |
| 응답 유실·중복 재시도 | RF036 계약 미확정 | key/proof/결과보존·TTL 정책 및 필요한 schema 승인 후 동시/순차 재시도 |
| 기존 상태1 고아 차감·status2 무결제·중복/고아 이력 | DB 읽기 조사 미수행, 문자열은 proof 아님 | 읽기 조사 범위·민감정보 보호·분류 정책 합의. 데이터 자동 보정/환불 금지 |
| HTTP/브라우저·구클라이언트·혼합 배포 | 서버/브라우저 미실행 | 공동 릴리스 전환·no-op 또는 거부 계약 결정 후 실제 endpoint/SWR/재조회 오류 검사 |
| 외부 writer·상품 삭제·상태 bulk 경합 | 저장소 정적 검색은 운영 전수 증거 아님 | writer 목록·락 규약 확인, 필요한 별도 범위 승인 |
| 성능 | 사용자 유예 | 별도 지시 전 실행/개선 주장 없음 |

BACKLOG의 RF036/037/038, R3-거래원자성 및 R3/R4 연동 잔여와 연결한다. 공통 문서 갱신은 통합 담당에게 전달하며 다음 작업을 자동 시작하지 않는다.

## 2026-11-11 추가 정책·cutover 분석

사용자의 **거래당1회 차감·재요청409/상태조회·과거 차감 후 상태1 가능 거래는 확인 전 차단** 결정을 명세에 반영했다. 기존 200 응답 재현/key TTL 대안을 필수 요구로 취급하지 않는다. 생성일/수정일/IDENTITY id 필드를 엔티티에서 다시 확인했으며, 새 transaction이 기존 상품에 생성되는 경우에도 RF035 이전의 거래 없는 차감 가능성을 배제하지 못함을 기록했다.

현재 schema에 결제 protocol/version·거래 연결 proof가 없다는 정적 근거, createdAt 시계/정밀도·구 writer 혼재, ID 할당과 commit 순서·복원/수동 입력, 기존 데이터 미확인 한계를 구분했다. 실제 DB metadata/clock/auto_increment/trigger·운영 기록·배포 시각·in-flight transaction은 조회하지 않았다. 실제 장애나 오염 데이터 존재를 확인한 것이 아니다.

추가 미실행: old status1+차감/불명·old status2+무차감·새 거래+legacy 상품, cutover 경계의 진행중 transaction, timestamp 동률·ID gap, 표식 null/unknown·복원·구 writer 재유입, 한 거래 동시 결제→한 차감/다른409. 분석 전용 범위 때문에 실행하지 않았다. 재개는 영속 분류 근거·cutover 설계 확정과 필요한 schema/운영 변경 별도 승인, 안전한 격리 fixture 확보 후다. **증거 없이 legacy 차단 해제하지 않으며 기존행 일괄 safe UPDATE·날짜/ID 상수 도입을 실행하지 않았다.**

이 추가 기록도 서술 분석이며 원시 실행 로그/XML·DB 결과는 없다. 문서 외 수정 없음. RF037 기존행 보호/전환 잔여는 통합 담당에게 전달했다.

## 추가 확정: 모든 거래의 검증 근거와 무스키마 후보

신규 여부와 무관하게 이전 차감 위험을 판별하지 못하면 차단하고, 검증 근거가 확보된 거래만 허용한다는 정책을 반영했다. 배포별 서버 전용 불변 allowlist 후보를 명세에 구체화했으며 실제 목록/항목·환경 설정은 생성하지 않았다. 정확한 transaction/product/consumer/seller/Long price와 환경·데이터 세대·검증 세대 결합, 검증자/증거 출처, 기본 전부 차단, 구 writer 배제와 항목 수/수명·폐기 한계를 기록했다.

현재 schema의 timestamp/tuple 비교는 변경 후 복구(ABA)나 동일 snapshot 복원을 완전히 식별하지 못한다. DB와 함께 복원되지 않는 관리 경계의 세대 갱신·이전 manifest 폐기·재검증 운영 통제가 필요하며, 보장할 수 없으면 결제를 계속 차단한다. 이 요구를 환경변수 하나나 파일 hash로 이미 구현/검증했다고 표현하지 않는다. 운영 등록·해제·DDL·기존 데이터 수동수정은 별도 승인 경계이고 증거 없는 자동 허용은 금지다.

추가 테스트/운영 확인은 전부 미실행이다: manifest 누락/오류·환경/세대/tuple 불일치·만료·중복, 관계/금액 변경·ABA, 복원/구 manifest 재사용, 다중 인스턴스·폐기 경합, 단일 차감과409. 이유는 분석 전용 범위이며 실제 신뢰 가능한 검증 자료/복원 통제·운영 승인도 아직 없다. 재개 시 구현된 계약과 실제 환경의 증거를 분리해 보존하고 불명 거래의 차단 유지부터 확인한다. 원시 실행 자료 없음; RF037 두 문서만 변경했다.

## 공동 리뷰 계약 반영

명세 최하단을 최신 기준으로 고정했다. 상품 scalar lock→거래 scalar current lock→buyer/price/status1 및 seller 포함 manifest・만료 판정→user/point lock→append→상태 조건부 UPDATE affected1→단일 commit을 기록했다. stale managed entity/auto-flush·OSIV·bulk update 뒤 재flush 위험은 제거·검증 대상으로 명시했고 해결됐다고 주장하지 않는다.

public 최초 PUT1→2는 거부, 구클라이언트 PUT2→2는 기록 당사자200 무쓰기 DTO이며 결제 proof가 아니라는 공동 계약을 반영했다. 신규 프런트의 추가 PUT 제거·포인트/거래 재조회와 결제409/네트워크·응답 유실/check 부족 시 거래 선조회, 조회 실패의 자동재결제·보상 금지를 기록했다. 이전 결제 key/proof・200재현 대안은 이력이다.

검증 계획은 INSERT flush 후 상태실패/물리 commit실패의 rollback과 commit 성공 뒤 응답유실・재요청409를 분리한다. 물리 결과 불명인 commit 단절을 rollback으로 기록하지 않는다. mock≠실DB 원칙, 현재 tuple/seller・잠금 후 만료·구클라이언트 무쓰기・재조회 실패 테스트, 공동 gate/old writer drain・증거/운영 승인 미충족 시 개방 금지를 추가했다.

이 보완은 문서 리뷰 반영이며 신규 테스트・DB・HTTP・브라우저 실행이나 운영 설정 변경은 없다. 원시 결과는 없고 이전 테스트 결과를 새 검증으로 재사용하지 않는다. 재개 조건은 명세의 공동 계약과 검증/운영 승인 충족이다.


## 2026-11-11 2단계 구현 증빙·최신 판정

사용자 승인에 따라 `refactor/payment-integrity-stage2`에서 RF035·RF036·RF038의 기존 미커밋 산출물을 보존하며 통합했다. 최초 README·BACKLOG·2026-11-10 재평가·RF035/036/037/038 명세와 증빙, 기존 호출자를 읽었다. 이 작업은 운영/실DB 실행·schema·새 의존성·자동 commit/push·단독 merge/배포를 승인하거나 수행한 것이 아니다. 공통 BACKLOG/LOG는 통합 담당 범위다.

### 구현 경계와 계약

- `PointHistoryService.payment`는 기존 controller의 단일 REQUIRED write transaction을 유지하고 READ_COMMITTED를 명시한다. 상품 PK scalar lock → 해당 상품의 거래 current scalar lock → 유일성/완전한 product·buyer·seller·price/status1 및 인증 구매자/요청 금액 검사 → RF036 실제 gate → user/latest point lock → 잔액 검사 → 같은 tuple의 gate 시각 재검사 → 포인트 save+명시 flush → 전용 native 조건부 상태1→2 UPDATE 순서다. 모든 쓰기는 같은 transaction 안이며 affected count가 정확히1이 아니면409 unchecked 예외로 rollback advice를 거친다. 재요청은 현재 상태가1이 아니므로409이며 과거200을 재현하지 않는다.
- RF038 담당자가 기존 PaymentTransaction projection을 id/buyer/price의3개에서 product/seller/status를 더한6개로 확장했다. RF037은 transaction entity를 조회하거나 setter를 호출하지 않는다. 새 `PaymentSettlementRepository.markPaid`는 id/product/buyer/seller/price/status1 전체 조건으로 status2와 updated_at=CURRENT_TIMESTAMP(6)를 쓴다. 전역 clear/refresh·독립 commit/REQUIRES_NEW·자동 retry·환불은 추가하지 않았다.
- gate는 production 기본 deny이며 신규 거래도 승인된 전체 tuple 증거가 없으면 user/point 잠금과 차감 전에409다. direct-create도 동일 조건이 충족되면 가능하며 accepted request를 요구하지 않는다. 상품 현재 owner/가격/visible/forSale은 결제 권위로 추가하지 않았다. 양수 충전·가격 불일치409·다른 buyer403·없는 상품/거래404·잔액 부족400·기존8필드 point DTO·양수 addCost/기존 설명문은 유지한다.
- 프런트는 결제200 뒤 별도 PUT2를 제거했다. check 부족/오류, payment409/다른4xx, 응답 유실·JSON 실패·5xx 모두 거래/포인트를 강제 재조회한다. 409는 성공 증명이 아니고 네트워크/5xx는 결제 결과 미확정으로 안내한다. 조회 실패는 별도로 표시하고 자동 재결제/보상하지 않는다. pending ref와 disabled로 중복 클릭을 막는다. getFetchMyPage는 no-store·HTTP 성공 검사·기존 access-header 처리를 거치며 hook이 새 point/buyList를 함께 대체한다.
- 독립 리뷰 P2에 따라 BuyHistory의 index 선택을 거래ID 매칭으로 바꿨다. 강제 재조회로 순서가 바뀌어도 같은 거래를 유지하고 선택 거래가 목록에서 빠지면 portal을 즉시 그리지 않고 effect로 닫아 안내한다. 판매자 상태 UI·취소/신고·구매확정 호출 정책은 변경하지 않았다.

### 테스트 적응·변경 전후 대응

`implementation/before/`에 RF037 진입 당시 PointHistoryService, point 테스트10개, 기존 frontend 버튼 테스트 및 BuyHistory를 보존했다. 이것은 before 실행 성공 증거가 아니다. API/hook/버튼의 진입 시점 전체 source snapshot은 없고 초기 도구 읽기 기록만 있다. RF035/036/038의 기존 성공 결과를 후행 소스로 소급 변경하지 않았다.

| 기존 검증/기대 | 승인된 이번 대응 |
|---|---|
| RF003 양수/동액/부족·잔액 lock/advice/기록 문자열 | 실제 합성 manifest gate와 새 repository double 주입, 결제 flush 검증 추가. 가격/사용자 변형 unit은 호출 전에 해당 immutable 합성 승인 snapshot을 새로 만든다. 충전에는 gate를 적용하지 않음 |
| 같은 거래 반복 결제600 잔액 기대 | 첫800 잔액 뒤 current status2를 읽는 두 번째 요청409·save1회로 변경. 취약 동작을 통과시키려고 정책을 완화하지 않음 |
| RF008 USER/ADMIN/UNCONFIRMED_USER·8필드/민감정보 차단·header·오류 | 실제 Security/JWT/AuthService/MVC 체인 유지, 결제만 flush 검증·합성 tuple 승인 추가 |
| RF035 buyer/price/없는 거래/다건/SQL3scalar | 기존 부정 검증 유지·새 scalar 조건을 fixture에 제공, SQL6scalar 및 실제 Long projection 유지. 다른 buyer 테스트는 price를 읽으면 AssertionError라403 branch 선행을 계속 입증 |
| direct-create의 seller/status가 없어도 결제 가능 특성 | seller 완전·현재status1·합성 승인tuple 때만 성공, status0/null·2~5는409/무차감. direct-create API의 미인가/임의가격 생성 위험 자체는 보류한 채 관찰 |
| RF003 opt-in MySQL 과거 fixture | 생성자 compile만 적응. 이 fixture는 상품/거래/schema가 없어 gate denyAll·repository 대역을 유지하며 실행하지 않았다. 과거13건 결과를 이번 공동 원자성 증거로 사용하지 않음 |
| 프런트 성공 뒤 PUT2·generic 실패 안내 | PUT0·재조회1, 요청/Authorization 불변, check/409/네트워크/5xx/조회실패·중복 클릭·목록 reorder/remove/empty를 실제 TS handler/hook/component 대역으로 검증 |

### 실제 실행·범위·보존

Windows PowerShell·Oracle JDK17.0.10 x86_64·Gradle8.7·Node20.10.0, 기존 캐시만 사용했다. 외부 SDK/DB/Redis/ES/운영 계정/실제 ledger/승인 manifest 없음. 합성 임시 JSON은 실제 parser/gate로 읽고 삭제했으며 운영 allowlist를 생성하지 않았다. 명령은 [commands.md](commands.md), 원문/정제본·최종 source hash는 [verification.json](verification.json)이다.

| 단계 | 실제 결과 |
|---|---|
| wrapper initial | 기존 사용자 Gradle cache zip.lck sandbox 거부, 테스트0. 기존 캐시 실행 허용 후 재시도 |
| initial-compile | compileTestJava helper 인자누락1오류, 테스트0. 이전 실행199건 XML이 build 폴더에 남아 자동 복사됐으나 stale로 이름을 바꿔 raw에서 격리하고 공유/집계에서 제외 |
| after-initial |388건 중384통과/4실패, errors/skips0. 부족잔액101 fixture가 gate200에만 등록되어409가 난1건, payment 추가flush가 기존 no-more-interaction 검증에 걸린3건. 승인 fixture와 flush 검증을 고쳤고 잔액400·8필드 보안 assertion은 유지 |
| backend final | **942/942, failures/errors/skips0**. point388(Atomicity28, trust service42/chain51/SQL3, gate/config178, 기존 point86) + RF038355 + RF00443 + RF02183 + RF02373. compileJava/compileTestJava·bootJar/jar·assemble 성공 |
| frontend initial → after |22건21통과/옛 PUT2 assertion1실패 →22/22. 새 hook/API 검증 후25/25, 5xx 미확정 보완 후26/26 |
| frontend review-final | **37/37, failures/skips0**, tsc --noEmit --incremental false exit0, lint exit0. 기존 무관 img warning3개 유지. 500/502/503/504×조회성공/실패8건, 목록 reorder/remove/empty3건 포함 |

반복 실행을 합산하지 않는다. initial 및 initial-compile의 stale199 XML은 새 테스트 결과가 아니며 각 실패는0건 실행이다. 작은 편집 보조 스크립트에서 Python launcher 접근거부/작업경로·문자열 오류도 있었고 실제 검증 실패/성공과 구분한다. 실패 단계의 정확한 전체 source snapshot은 없으며 console/XML만 보존했다. 최종 backend 실행 후 source 동결 및 frontend review-final 실행 후의 선택30개 source를 보존·hash로 대조했으며 전체 작업트리 snapshot이 아니다. raw는 Git 제외 `.local-runtime/rf037-results/`, 개인경로/hostname/합성 JWT literal 정제 공유본은 본 evidence implementation이다. 후일 재실행은 새 증빙이다.

### 해석 한계·미실행·재개 조건

1. Atomicity28의 save/flush/조건부UPDATE/commit 오류와 성공1회→409는 repository 대역+Spring advice 증거다. 실제 물리 commit/rollback·동시 row lock·FK/updated_at 정밀도·MySQL affected count를 실행한 것이 아니다. 실제 chain51도 MockMvc/필터/인증/컨트롤러/서비스가 진짜이고 JDBC·Boot 오류 dispatch/네트워크·응답 postcommit 전달은 대역이다. 프런트37은 transpile한 TS handler/hook/component와 React element/hook 대역이며 브라우저/진짜 React effect scheduling/SWR 네트워크/실HTTP 검증이 아니다.
2. payment는 target transaction entity를 로드하지 않지만 native query와 명시 flush는 caller의 미리 관리/dirty 상태를 flush할 수 있고 native UPDATE는 관리 transaction entity를 동기화하지 않는다. 임의 outer RC/RR·사전 로딩/dirty entity·OSIV/다른 writer에 대한 안전을 입증하지 않았다. REQUIRED의 RC 선언이 outer 격리수준을 바꾸지 않는다. clear/refresh/독립 transaction으로 임의 해결하지 않았으며, 이 미검증 경로는 운영 안전 완료의 차단조건이다.
3. 실제 DB는 이번 미승인·안전한 물리 격리/fixture 미확보로 실행하지 않았다. 재개 시 독립 환경 identity·소유 fixture·외부차단·cleanup 승인 뒤 debit INSERT flush 후 UPDATE 실패·FK/flush/commit 실패·새 연결에서 두 쓰기0/1건·동시동일거래/같은buyer다른거래/충전/취소6·신고7 양방향/barrier·outer/OSIV/current-read를 확인한다. 실제HTTP/브라우저·프록시 응답유실은 안전 실행 기반에서 별도로 확인한다.
4. gate 현재tuple 비교는 ABA/ID재사용/복원epoch·과거 원장 완전성·독립 증거/승인 진위·구 writer/drain을 증명하지 않는다. 신규 포함 미검증 거래는 차단한다. 운영 전원장 조사/승인등록·실제 시계신뢰/manifest 경로통제·복원epoch 독립 trust·동일릴리스 frontend/backend 전환과 구요청 drain·롤백시 쓰기재개 조건은 미실행 배포 차단이다. RF021 직접 생성의 비인증/임의가격 위험도 해결하지 않았다.
5. 전체 backend test/build·frontend production build·실제 브라우저·운영 소비자/구클라이언트·성능은 미실행이다. 선택 assemble과 tsc/lint를 전체 실행/운영/성능으로 확대하지 않는다. 성능은 사용자 유예이며 개선 주장 없음. 잔여는 공통 BACKLOG RF036/037/038·R3/R4/R6 연동 및 RF021에 통합 담당자가 상태/근거/재개 조건을 연결하고 자동 착수하지 않는다.


### 공동 체인 순서 검증 추가·독립 리뷰 결과

공통 독립 리뷰에서 정상 top-level 로컬 경로의 새 P0/P1은 발견되지 않았다. 지적된5xx 결과 미확정 분기·BuyHistory 거래ID 선택·공유 console의 slash형 file URI 개인경로 P2를 수정했다. 마지막 경로 정제는 source/테스트 실행이 아니라 공유본 생성 수정이며 raw/공유 hash를 다시 계산했다. 테스트 수·실행 시각은 정제 전후 동일하다. 앞선942/37·assemble/tsc/lint와 source/hash 대조는 통과했고 운영 차단조건은 그대로다.

추가 승인된 `PaymentAndStatusChainTest`는 기존 실제 Security/JWT/AuthService/PointController/TransactionController/두 service를 하나의 명시 MVC context에서 실행한다. 거래·상품·포인트·조건부UPDATE·status repository는 대역이고 managed entity 상태를 메모리로 이어준다. payment→취소6/신고7 뒤 payment409는 차감1회·잔액200·환불0, 취소6/신고7→payment409는 차감0·사용자잔액 lock0을 각각 확인했다. 최초 publicPUT2가 차감/상태2를 생성하지 않는지, payment 성공 뒤 legacyPUT2의200 no-op와 payment 재요청409의 구분도 확인했다. 동시에 달린 race가 아닌 **두 직렬 순서의6건**이며 실제 row lock/물리 rollback·원장 조회·경합 증거가 아니다.

- 최초 joint-chain은 보조 메서드 `status(long)`가 Spring static `status()`를 가린 compileTestJava7오류·테스트0건이다. raw console/exit를 보존했고 남아 있던 이전942 XML은 stale로 분리했다. 새 source의 정확한 실패시점 snapshot은 없다.
- `putStatus(long)`로 이름을 수정한 joint-chain-final은 **6/6·failures/errors/skips0, compileJava/compileTestJava 성공**이다. 기존942 선택 실행·assemble을 다시 돌린 결과가 아니며 클래스 중복이 없어942와 별도6으로 보고한다. 제품 source/기존 테스트/프런트 source는 변경하지 않았다.
- 최종 증빙 snapshot은 기존30개와 추가 joint-chain test1개의31개다. 기존30개 snapshot은 byte-for-byte 일치 검사를 거쳐 덮어쓰지 않았고 새 test는 joint-chain-source에 별도 보존했다. raw/shared paired hash와 최종6 XML은 verification.json에서 구분한다. 공유본에서 개인 사용자경로/JWT literal·미정제hostname을 확인하며 전체 원본을 공개하지 않는다.

최종 구현 판정은 **승인한 로컬 최소 기능·선택검증 및 독립 리뷰 범위 충족, 추가6건 최종 독립 대조 완료 / 실DB·운영 결제 개방·배포는 미완료 및 차단 유지**다. 이 완료는 같은 PR의 RF035/036/037/038·프런트 공동 경계에 의존하며 개별 부분 배포 승인이 아니다. 물리DB/OSIV·dirty/outer·전체 writer/drain·운영 상태사전/원장/독립복원세대·복원/ABA/ID재사용·RF021 직접생성 가격오염을 별도 승인된 증거로 해소하기 전 gate 기본폐쇄를 유지한다. 공통 BACKLOG/LOG와 다른 RF 후속 통합 상태는 통합 담당자가942/37 및 별도6 근거에 연결하고 기존 실행 source/hash를 소급 대체하지 않는다.


최종 독립 후속 리뷰는 joint-chain6/6·이전 compile7오류/0test·staleXML 분리, 선택source31개/raw-shared106쌍 hash일치·XML 수/시각/정제일치, 직렬요청/대역과 실제DB 미검증의 구분을 확인했고 새로운 차단사항을 발견하지 않았다. 로컬 합의범위의 구현·선택검증·독립 리뷰를 완료한다. 운영 개방/배포 차단과 잔여 검증 재개 조건은 위와 같이 유지한다.
