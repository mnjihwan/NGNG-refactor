# RF-038 증빙 안내

- 작성: 2026-11-11. 기준 `main@82aa727`, 최초 git status는 기존 `?? .tmp/`만 출력. `.tmp/rf022-frontend` 기존 증빙은 변경·삭제·RF038 결과로 전용하지 않았다.
- 현재 판정: **동일 PR의 공동 최소 구현·오프라인 선택 검증·독립 리뷰 범위 완료(공동체인6건 최종 대조 포함), 물리DB·운영 개방/배포 미완료 및 차단 유지** (2026-11-11). 초기/독립 결과와 현재 공동 결과는 구분하며 아래 최신 공동 통합 절이 우선한다.
- 읽은 범위: 루트 사용자 AGENTS 지침, refactoring README/tasks TEMPLATE/BACKLOG/2026-11-10 재평가, RF003·004·006·021·023, backend 거래 controller/service/DTO/entity/repository와 point payment·보안 체인, frontend my_page enum·구매/판매 모달·API/hook 및 chat/admin 호출, chat-server 거래/status 읽기, ignored local/schema.sql의 거래 DDL, 기존 .tmp 목록.
- 수행 방법: PowerShell Get-Content/Select-Object/Select-String, rg, git status/log/branch 읽기. 상태 setter 호출·필터/역할·현재 owner와 기록 seller 차이·결제와 상태 호출 분리·UI 숫자 정의의 충돌을 확인했다. local DDL에 status seed가 없으므로 실제 DB 상태 이름은 확인되지 않았다. 저장소 검색상 미발견을 외부 호출자 없음으로 일반화하지 않는다.
- 원시 자료: 이번 탐색 출력은 대화 tool 결과에만 남으며 별도 raw 파일·테스트 XML·로그·실행 환경 snapshot은 없다. 명세의 사실은 해당 HEAD 소스로 재확인 가능하나 후일 재확인은 이번 테스트 원본이 아니다. 과거 RF006/003/004의 결과는 각 증빙 문서 범위만 보장하며 RF038 검증을 대신하지 않는다.
- 변경: `tasks/RF-038.md`와 본 README만. 소스·테스트·설정·schema·DB·BACKLOG/LOG·기존 .tmp 변경, 신규 의존성, commit/push 없음.

| 미실행·부족 항목 | 이유/한계 | 추가 검증·재개 조건 / 추적 |
|---|---|---|
| 운영 상태 ID/이름·정책 | frontend 두 정의 상충, local 파일은 DDL | 읽기 자료로 실제 표·판매 형태·취소/신고 결정 확인 / RF038 |
| 서비스/MVC/보안 체인·컴파일 | 이번 분석·명세 범위, 코드/테스트 미수정 | 승인 행렬로 변경 전 특성/후 정책·무쓰기 선택 검증, 실제 실행 XML 보존 / RF038·R6-정책 |
| 결제 전제·원자성·재전송 | 현재 status와 payment가 별도이며 durable 거래별 proof 부재 | RF035/036/037 합의 후 다른 구매자/금액·응답유실·동시/부분실패 확인 |
| MySQL commit/rollback·경합·RR/OSIV | 접속·fixture 생성 미실행; 대역만으로도 증명 불가 | 안전 격리 대상·쓰기/cleanup 범위 확인 후 독립 tx 결과 관찰 / R4·R6-연동 |
| HTTP/브라우저·외부 클라이언트 | 정적 호출자 조사만 실행 | 안전 서버/인증과 소비자 목록 확보, PUT 실패·재시도·캐시/상태 표시 확인 |
| 성능/운영 빈도 | 사용자 유예·목표 밖 | 별도 재개 지시·대표 조건 전 측정/개선 주장 없음 |
| 독립 리뷰 | 문서 초안 작성 단계 | 부모/독립 담당자의 실제 문서 및 결정표 검토; 구현 시 별도 코드·증빙 리뷰 |

위 잔여는 기존 BACKLOG의 RF038/R6-정책 및 연결 후보에 대응하며 이 담당자는 공통 파일을 변경하지 않는다. 정책 대기와 실행 미검증을 통과로 기록하지 않으며 추가 작업을 자동 착수하지 않는다.

## 2026-11-11 사용자 정책 반영 기록

- 정상 1→2→3→4→5, 구매자 입금/구매확정·기록 seller 발송/배송완료, 역행·건너뛰기 거부를 확정 사항으로 명세에 반영했다. 이전 product.owner 유지 대안은 최신 정책이 아니다.
- RF035 기록 buyer/transaction.price·요청금액 불일치 거부·직접 생성 거래 결제 허용·충전 별도를 반영했다. 수락 proof 필수로 직접 거래를 차단하지 않는다.
- 기존 취소/신고는 API 전 상태→6/7 허용과 UI 취소 status>=2 차단을 구분해 보존한다. 환불·재판매·복구 자동 추가 없음. 6/7→정상 복귀는 정상 전이 정책과 모호성이 있어 거부/유지/통제복귀 대안을 사용자 결정 항목으로 남겼다.
- 결제 1→2 proof 없이 현재 PUT을 유지하는 단계적 배포는 무결제 전이를 허용한다. 결제 endpoint 내부 원자적 변경(우선 검토), 신규 단일 명령, durable proof+복구의 대안을 비교했고 RF037과 결합한 구현/배포 경계 필요성을 기록했다. RF036 재시도·기존 status2 오염 데이터의 한계도 남겼다.
- 수행 범위는 전달받은 사용자 결정 및 기존 소스 분석에 대한 문서 갱신뿐이다. 새 테스트/컴파일/DB 연결·쓰기/HTTP/브라우저/성능 실행 및 소스 수정 없음. 새 실행 raw/XML 없음. 위 정책 반영은 기능 검증 통과가 아니다.

## 2026-11-11 예외 상태 복귀 정책 추가 확정

사용자는 6/7→정상1~5 복귀 거부를 선택했다. 명세의 이전 복귀 결정 대기는 해소되었고 대안 A가 최신 정책이다. 기존 목적지6/7 허용(6→7·7→6 포함)은 유지한다. 마이페이지 취소의 status>=2 차단과 API의 전 상태→6 허용 차이는 기존대로 보존하며 환불/재판매를 추가하지 않는다. 확정 행렬 테스트 계획에 예외 상태 복귀 전부 거부·무쓰기 및 예외 목적지 허용을 추가했다.

결제 성공 증거·RF037 공동 구현/배포 여부는 후속 설계 분석으로 분리되어 여전히 구현 승인·실행 결과가 아니다. 이번에도 task/evidence 문서만 수정했으며 소스·테스트·설정·DB·공통 파일 변경, 실행 테스트 및 새 raw/XML은 없다.

## 2026-11-11 독립 리뷰 보완

최신 사용자 결제 정책(차감+1→2 동일tx, 최초 public PUT1→2 거부, frontend 추가PUT 제거, 결제 재요청409)을 명세에 반영했다. 상태 PUT의 동일 상태는 별도200 무쓰기 계약으로 구체화했으며 기록 당사자 모두 허용·status2 응답은 지급증명 아님을 명시했다. 6/7 목적지 허용은6↔7·동일 상태와 실재 미지 상태8에서의 진입도 포함하고, 불완전한 연관/없는 상태는 그 전제 밖이다. 정상 변경 actor 거부403·비당사자 기존401·불가전이409의 최소안을 기록했다.

공통 잠금은 상품ID 탐색→상품 scalar lock→거래 scalar current lock·재검증→결제만 user/point lock이다. RF038 개발/격리 검증 분리와 공동 배포 필요성을 구분했다. 구버전/진행중tx 배제, 신규거래라도 legacy 상품 과거 고아차감 가능성, 대상 분류·영속 표식 및 운영 상태표/관계 확인 미완료는 배포 차단조건이다. 운영 상태표 확인은 이미 확정된1~5 정책의 재결정이 아니다. 6/7 성공은 환불이 아니며 자동 환불·재판매는 추가하지 않는다.

이 보완은 지정된 두 문서만 수정했다. 실행 테스트·DB/운영 조회·마이그레이션·소스 수정·새 raw/XML 없음. 배포 차단조건이 기록되었다는 사실은 그 조건의 해소 증거가 아니다.

## 2026-11-11 2단계 구현·선택 검증 — 최신 결과

공동 리뷰 계획 승인 뒤 `refactor/payment-integrity-stage2`에서 상태 service/repository 및 RF038 테스트를 구현했다. RF035 repository 수정은 보존했고 포인트 서비스·RF036 gate/config·운영 설정·schema는 변경하지 않았다. 공통 BACKLOG/LOG는 통합 담당 소유다. commit/push·단독 merge/배포 없음.

기록 seller/consumer의 완전한ID, public 최초1→2 거부409, seller2→3/3→4·buyer4→5, 역행/건너뛰기·6/7→정상409, 기존 실재 unknown상태→6/7과6↔7 허용, 동일1~7 양당사자200 무쓰기를 구현했다. 동일2의200은 결제 증거가 아니다. 당사자 외401·당사자의 행위권한403·불완전 current 또는 scalar/entity 불일치409를 구분한다. 존재하지 않는 거래/목표의 기존 repository 예외 및 malformed MVC400을 전역 advice로 바꾸지 않았다.

상품ID 비권위 scalar→상품PK scalar lock→거래 current scalar lock→행렬/인가→첫entity load→id/product/party/status/price/address 대조→필요한 setter→기존DTO다. 명시 RC·기존 REQUIRED이며 clear/refresh/REQUIRES_NEW/상위tx거부guard 없음. `findStatusProductId`·`lockStatusTransaction` 인터페이스는 RF037이 같은 잠금 순서로 연결할 수 있도록 명세에 기록했다.

| 단계 | 실제 결과 | 증빙 범위 |
|---|---|---|
| wrapper-denied | zip.lck 접근 거부, test0 | 최초 sandbox console/exit. 캐시 접근 승인 뒤 동일 offline 명령 실행 |
| before | 서비스14+실제체인6=20/20, 실패·오류·skip0 | before XML2개·console/exit 및 source. 무결제/건너뛰기/복귀·owner/seller 기존 불일치 특성 |
| after-initial | 서비스188+실체인159+SQL3=350/350, 실패·오류·skip0 | 최초 구현 검증. compileTestJava 포함 |
| after | 서비스192+실체인160+SQL3=355/355, 실패·오류·skip0 | RF006 Long/JSON/오류 장면 보강 뒤 최종. 최초와 합산 금지 |
| regression·assemble | RF00443+RF02183+RF02373=199/199, 실패·오류·skip0 | 별도 클래스6개. compileTestJava·bootJar/jar·assemble 성공. 최종after와 중복 없음 |

Windows PowerShell·Oracle JDK17.0.10 x86_64·Gradle8.7·Node20.10.0 및 기존 캐시/라이브러리만 사용했다. 정확한 [명령](commands.md), 공유 [implementation/](implementation/), raw/shared와 source의 [SHA256 manifest](redaction-manifest.json), [검증 요약](verification.json)을 따른다. 미정제 raw는 Git 제외 `.local-runtime/rf038-results/`이며 공유 console/XML은 개인경로·hostname·합성JWT literal을 정제했다. 당시 선택 source는 보존하지만 RF035/036을 포함한 전체 작업트리 snapshot은 아니다. 후일 재실행으로 과거 raw를 대체하지 않는다.

초기 RF038 fixture가 기존 RF006 `StatusFixture` 이름을 덮어쓴 실수로 병렬 RF036 compileTestJava가39오류로 실패했다. 기존 fixture를 RF023·RF009 동일 보존본 SHA256 `F818D876B36DA187DC9373A8103B8419A16380290E0653F5CD5C475F2C0E5678`로 복구하고 새 fixture를 `Rf038Fixture`로 분리했다. 이후 RF036178 및 RF038의 전체 테스트 소스 컴파일은 통과했다. 실패 원문은 [RF036 unit-initial 승인실행 console](../RF-036/implementation/unit-initial/console-approved.txt)에 있으며 테스트 실행0의 컴파일 실패를 기능 실패/통과로 바꾸지 않는다.

기존 RF006 서비스24/체인13 원본과 fixture를 before/source에 보존했다. product.owner 인가·1→4 허용·null/null 허용·ID 단락평가 기대는 사용자 확정 seller/행렬·완전한ID 계약으로 의도적으로 바꿨다. 작은 캐시ID와 양당사자 별도Long값·3역할/ADMIN 비우회·없는거래/상태·정확 DTO중첩·malformed400·missing/malformed 토큰·refresh 장면은 새 계약으로 보강했다. 구체 대응표는 [명세의 RF006 비교표](../../tasks/RF-038.md#기존-rf006-테스트와-정책-차이)를 따른다.

서비스192에는 상태9×목표8×두당사자144행렬, 불완전·stale 주요값11종·인가/오류/Long·lock순서·advice를 포함한다. 실제체인160에는 역할3×전이12×주체4=144행렬 및 인증/refresh/binding·불완전/응답/오류 보강이 포함된다. SQL3은 query 선언/파라미터/projection 변환을 확인하며 MySQL driver·SQL실행·잠금 범위 증명이 아니다. 동일 상태의 setter/save/bulk 미호출·in-memory updatedAt 불변은 실제 DB 무쓰기 관측이 아니다.

fake transaction manager의 outer RC/RR×clean/stale4건은 REQUIRED가 outer 격리수준을 변경하지 않음과 scalar/entity 불일치409만 확인한다. 진짜 persistence context·dirty flush·RR snapshot은 없다. 사전 dirty entity는 scalar 대조 전에 auto-flush될 수 있어 arbitrary outer transaction/OSIV 안전을 보장하지 않는다. clear/refresh로 caller pending 상태를 잃게 하지 않는다. setter 실패 rollback advice·commit 실패 전파도 실제 DB rollback/commit 증거가 아니다.

| 잔여·미실행 | 이유·한계 | 추가 검증·재개 조건 |
|---|---|---|
| RF037 연결·결제차감+상태2 원자성·취소/신고 경합·재요청409 | 이 담당 범위는 상태PUT; point/gate 미수정 | RF037과 동일 lock 순서/단일tx 통합 후 대역 실패·경합 계약 및 독립 리뷰. 공동 출시 전 단독 merge/배포 금지 |
| 실DB current-read/dirty flush/물리 commit·rollback·교착·OSIV | RF016B·DB 쓰기 승인/안전 fixture 없음 | 명시 격리 identity·소유 fixture/cleanup·외부차단 범위 승인 후 물리 검증; 기존 MySQL 이력 대용 금지 |
| 실제 HTTP/Boot error dispatch·브라우저·프록시·외부소비자 | 명시 Spring MVC context와 대역만 실행 | 안전 서버/인증·호출자 확보 후 상태응답/오류·구프런트 호환 검증 |
| 과거 결제/고아차감·status표 호환·구버전 drain/통제 | 운영자료 조회·복구·전환 미실행/미승인 | RF036/037 공통 배포 차단조건 해소 증거 전 결제 개방 금지. 신규도 자동안전 판정 금지 |
| 전체 backend test/build·frontend production build/lint·성능 | 선택 범위/외부연결·성능 유예 | 별도 범위 합의/안전 환경·대표 측정 전 실행·개선 주장 없음 |

잔여는 기존 BACKLOG RF038/R6-연동 및 RF036/037 공동 출시 범위에 연결해 통합 담당자가 추적한다. 미검증을 통과로 쓰지 않으며 이 결과만으로 결제·운영 완료를 선언하지 않는다.

### 독립 리뷰·후속 projection 구분

공통 독립 리뷰가 승인 상태 전이 범위에서 차단 결함 없음, before20/after355/회귀199·hash 일치, fake tx/outer·실DB 한계 및 RF035/036/037 공동 출고 차단을 확인했다. 당시 XML14개를 파싱했고 raw/shared24개·선택source33개 SHA 불일치0, 해당 source/docs의 후행공백·공유본 개인경로/JWT literal0을 확인했다. Git 작업은 하지 않았다.

그 뒤 RF037 연결 요청으로 같은 repository `lockPaymentTransactions`에 기존 transactionId/consumerId/price 외에 productId/sellerId/statusId scalar·Long getter3개를 추가했다. 실제 @JoinColumn product_id/seller_id/status_id와 대조하고 RF037 담당에게 인터페이스를 전달했다. 기존 목록/상품 선잠금/current FOR UPDATE는 보존한다. 이 추가 변경은 포인트 서비스의 완전한 당사자·상태/상품 검증을 위한 것이다.

**after/source는355·회귀199 실행 당시 확장 전 source로 유지**한다. 후속 source는 [integration-projection/source](implementation/integration-projection/source/)에 별도 보존했으며 source snapshot 수는34가 됐다. RF038은 후속 projection에 대해 테스트를 재실행하지 않았다. 기존 PaymentLockQueryContractTest의 정확SELECT/정확3getter 기대를6getter에 맞춰 보강하고 fixture를 연결하는 일은 RF037 담당에게 인계했다. 이후 공동 통합 검증/실패는 RF037·통합 증빙으로 별도 기록하며 이 후속 미검증을 기존355건 성공으로 소급하지 않는다.


## 2026-11-11 후속 공동 통합·현재 완료 범위

**동일 PR의 공동 최소 구현·오프라인 선택 검증·독립 리뷰 범위 완료(공동체인6건 최종 대조 포함), 물리DB·운영 개방/배포 미완료 및 차단 유지**. 브랜치는 `refactor/payment-integrity-stage2`이고 RF035/036/037/038·프런트를 같은 PR/릴리스 경계로 다룬다. 이 문서의 초기 분석·독립 구현 당시 “대기/미연결/미구현”은 당시 이력이며 아래 현재 결과를 소급 의미하지 않는다. 기존 RF038 원시 XML/console·실행 당시 source/hash·중간 실패를 덮어쓰거나 최신 source로 재생성하지 않았다.

public 최초1→2는409이고 payment가 차감과 함께 처리한다. 기록seller의2→3/3→4, buyer의4→5, 동일1~7 무쓰기200 및 기존6/7 예외 목적지·6/7→정상 복귀거부를 유지한다. RF037 연결용 PaymentTransaction6scalar projection은 후속 통합SQL/서비스/체인 테스트에서 검증했다. 동일2 PUT200은 결제증거/재결제200이 아니며 결제 재요청은409다. 같은 MVC context의 payment↔6/7 두 직렬 순서는 별도6건에서 추가 확인했다.

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
