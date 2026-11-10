# RF-009 증빙과 검증 범위

## 2026-11-10 최소 구현·격리 검증 — 최신 결과

기준 HEAD `6438b969e3901d14bdd519c16de26c44d51ba68a`. 구현 담당 시작 시 RF009 계획 문서/BACKLOG 변경만 있었고 이를 보존했다. 작은 `ProductCacheInvalidator`와 ProductService/PenaltyTran/TransactionRequestService만 운영 변경했다. **합의 기능 구현·선택 검증·독립 코드/증빙 리뷰 완료(차단 결함 없음), 문서 정합 최종 검토 완료**다. 전체 캐시 정합성·운영 통합 완료를 뜻하지 않는다.

### 구현·변경된 동작

- 실제 transaction과 synchronization이 모두 활성화되면 `afterCommit`에서 `getProduct`의 해당 Long 상품 ID만 evict한다. 둘 중 하나가 없는 경계는 즉시 처리한다. cache lookup/evict의 RuntimeException은 상품 ID·캐시명을 로그로 남기고 차단하며 뒤 callback은 계속 실행된다. 중복 evict는 허용한다.
- PUT의 `@CachePut`을 제거하고 반환 DTO 생성 뒤 같은 협력자를 호출한다. sale/refresh/delete 성공, private 제재 writer, 최초 거래 수락 성공에 연결했다. 인가·삭제 OR 사업조건·거래 RC/lock/원자성 경계는 유지했다. 거부/없는 ID/반복 수락·거절/rollback·commit 실패에는 evict하지 않는다.
- **의도된 차이**: PUT은 반환 DTO를 캐시에 put하지 않고 무효화한다. 기존 annotation의 Redis put 오류 전파 경로도 제거되어, writer eviction 실패는 로그 후 기존 쓰기 응답을 유지한다. 읽기 get/put 오류는 계속 caller로 전파된다. 기존 Redis 미의존 writer에 새 HTTP 실패를 만들지 않으려는 선택이며 자동 복구는 없다. 변경 전 Redis 장애 HTTP를 실행한 근거는 없고, 기존 CachePut/default handler 소스와 공식 API 분석이 그 경로의 근거다.

### 실행·원시 근거

환경은 [Gradle/JVM/OS](gradle-environment.txt)의 Gradle8.7/Oracle JVM17.0.10/Windows11, Boot3.2.4이다. [실제 dependencyInsight](spring-tx-dependency.txt)로 Spring tx6.1.5를 확인했다. 모두 기존 의존성의 `--offline` 실행이며 신규 의존성·실DB/Redis 접속·서버 시작·성능 측정은 하지 않았다. 전체 `test/build`는 외부 Boot 설정 위험 때문에 실행하지 않았다. 최종 선택 test 실행에서 전체 테스트 소스의 `compileTestJava`는 성공했다.

| 단계 | 실행 결과 | 보존 근거·해석 |
|---|---|---|
| before | 11/11, fail/error/skip0 | [before](before/): 새 실제 proxy 특성7 + 당시 RF007 cache4 XML, 당시 테스트/fixture 소스3개. PUT returned DTO put, sale/refresh/delete/private penalty/accept의 warm stale 유지. console은 대화 tool 출력만 있고 별도 원문 파일 없음 |
| 최초 after compile | compile 실패2건, 테스트 미실행 | [console](after-initial-console.txt): bean 삽입 문자열 오류와 조건부 assertThrows generic 추론 오류. 테스트 코드만 수정. 당시 전체 소스 snapshot은 보존하지 않았으며 현재 소스로 소급 대체하지 않음 |
| 첫 after 기능 | 63/63, fail/error/skip0 | [XML](after-functional/), [console](after-functional-console.txt): RF00959 + RF007 cache4. 이후 추가 사례로 확장했으므로 당시 전체 소스 snapshot 없음 |
| 추가 선택 회귀 | 405건 중404통과/1실패, error/skip0 | [XML](after-regression-failed/), [console](after-regression-console.txt): 신규 direct no-tx 수락 성공 가정이 기존 TransactionDetailsService.findCurrentDetails의 MANDATORY tx에 거부됨. 운영 정책을 완화하지 않고 기존 경계 거부·무evict를 기대하는 테스트로 수정. 당시 전체 소스 snapshot 없음 |
| 수정 retry | 392/392, fail/error/skip0 | [XML](after-regression-retry/), [console](after-regression-retry-console.txt): RF006 security selector의 패키지 오타로 해당13건 미실행. 이 통과를14클래스 전체로 기록하지 않음 |
| 최종 선택 회귀 | **14클래스405/405, fail/error/skip0** | [XML](after-final/), [console](after-final-console.txt), [실행 소스](after-source/), [SHA256](source-manifest.csv). 정확한 selector로 RF00613건도 포함해 전체 재실행 |
| assemble | exit0 | [console](assemble-console.txt): compile/process UP-TO-DATE, resolveMainClassName/bootJar/jar 실행. 전체 build/test·운영 기동 성공이 아님 |

최초 기본 sandbox wrapper 실행은 사용자 Gradle cache lock 쓰기 거부로 테스트 실행 전에 종료됐다. 기존 Gradle 캐시 접근을 위한 escalation 승인 뒤 위 오프라인 선택 실행을 수행했다. 이 초기 lock 출력은 대화 tool에만 있으며 별도 원문 파일은 없다. `python` launcher 접근 실패로 파일 변경은 없었고 PowerShell/.NET으로 계속했다.

실제 명령은 [commands.md](commands.md)에 기록했다. 최종 집계는 RF00969(Invalidation57/Invalidator9/Concurrency3), RF00786(service50/cache4/chain32), RF00443(policy28/creation15), RF00637(ownership24/chain13), RF00852(contract28/chain24), 공통 보안118(TokenFilter72/chain46)이다. 변경한 기존 MySQL 테스트2개는 생성자 인자에 mock invalidator를 추가해 컴파일 호환만 유지했으며 실행/skip 판정을 하지 않았다.

### 검증한 보장과 대역 한계

- 실제 Spring cache/transaction proxy와 `AbstractPlatformTransactionManager`의 synchronization lifecycle을 사용했다. cache/repository/트랜잭션 resource는 대역이다. commit 직전 warm key 유지/commit 후 대상 key evict·타 key 유지/다음 read 재조립, 6 writer outer rollback-only·commit 실패·evict 실패, 중복 callback, outer runtime rollback/참여 실패에 따른 global rollback-only를 검사했다. 대역 entity 상태를 DB rollback 성공으로 해석하지 않는다.
- 인가 거부/삭제 사업조건 거부·없는 상품의 기존 NPE/NoSuchElement 경계·제재 missing 단계·중간 writer 실패·수락 반복/거절/404/409가 무evict임을 검사했다. 제재는 이미 forSale=false여도 report DTO 변경으로 무효화한다. 상품 ID77과 request501/report901을 달리해 잘못된 key를 구분한다.
- helper의 no-tx/synchronization-only/actual-tx-without-sync는 즉시 처리한다. direct ProductService 4 writer와 private penalty 연결도 즉시 evict한다. direct no-tx 수락은 기존 TransactionDetailsService.findCurrentDetails의 MANDATORY 경계에서 실패하며 캐시를 evict하지 않는다. RF004의 직접 수락 no-tx 지원을 새로 추가하지 않았다.
- 캐시 lookup/evict/null cache 및 no-tx 오류에 대해 로그의 상품 ID·캐시명/throwable, 쓰기 응답 보존·fake commit 완료·뒤 callback 실행을 구분했다. service 6 writer의 evict 오류도 정상 반환한다. read get/put 오류 전파를 별도 검사했다. Redis 실제 네트워크 장애·HTTP 응답을 실행한 검증은 아니다.
- **advisor 관측은 격리 `@EnableCaching`/`@EnableTransactionManagement` context에 한정**된다. 해당 구성에서 ProductService CacheInterceptor 바깥→TransactionInterceptor 안쪽, Penalty/수락은 transaction advisor를 관측했다. 실제 운영 Boot 전체 advisor 순서·Servlet OSIV/HTTP/error dispatch는 미검증이다.
- 두 독립 Spring context가 같은 대역 cache/repository를 공유한다. latch로 miss 후 옛 snapshot을 읽은1/2 reader를 정지하고 다른 context의 writer commit+evict 뒤 release하여 stale put 재삽입을 결정적으로 재현했다. 이 테스트의 통과는 **잔여 결함 재현 성공**이며 해결이 아니다. 실제 Redis·다중 프로세스·운영 격리 수준 검증이 아니다.
- 역순 writer는 fake commit에서 독립 snapshot을 공개한다. A 시작→B commit/evict→중간 reader가 B cache→A commit/evict→다음 read A 순서를 검사했다. JPA flush/락·실DB의 writer 순서를 증명하지 않는다. 늦은 reader가 A eviction 뒤 put하는 경쟁은 여전히 남는다.

### 비식별화·재실행과 남은 검증

기존 TokenFilterTest의 fixture가 출력한 **합성 JWT**49개씩이 실패/retry/final XML3개에 있었다. 공유본에서 `[REDACTED_SYNTHETIC_JWT]`로 치환했고, 원문은 기존 Git 제외 경로 `.local-runtime/rf009-raw/{phase}/`에 보존했다(`git check-ignore` 확인). [치환 manifest](redaction-manifest.csv)에 각 원문/공유본 SHA256·건수·원문 위치를 기록했다. 기존 broken/null/이미 REDACTED fixture는 그대로다. 실제 자격값·실사용자 데이터는 사용하지 않았다. 공유 XML의 test 결과/stack/집계는 유지했고 비치환 XML은 원시 그대로다. 보존된 이번 실행과 후일 재실행은 구분한다. 수정 전 전체 source snapshot이 없는 중간 실패를 최종 source로 소급 증명하지 않는다.

미실행: 실DB JPA flush/commit/rollback·RF004 락/격리 수준, 실제 Redis serializer/key-prefix/TTL/네트워크·failover, 프로세스 종료/다중 인스턴스·운영 Boot/HTTP/OSIV·브라우저/프런트·성능. 이유는 이번 사용 승인·identity/namespace·소유 fixture·외부 차단이 확인되지 않았거나 범위 밖/사용자 유예이기 때문이다. 기존 RF004 MySQL24건은 RF009/Redis 검증을 대신하지 않는다.

추가 검증은 [명세](../../tasks/RF-009.md)의 별도 승인·전용 identity·격리 MySQL/Redis namespace/정확 key·소유행 cleanup·독립 잔여 확인 조건 뒤 opt-in으로 수행한다. 강한 최신성/복구가 필요하면 캐시 우회·분산 generation/fencing/outbox의 별도 설계/비용·계약 검토가 필요하며 자동 착수하지 않는다. TTL은 늦은 stale put부터 재시작하므로 DB 쓰기부터30분 이내 최신성 보장이 아니다. [BACKLOG R9](../../BACKLOG.md)에서 일관성·통합·범위·성능을 추적한다. 독립 담당자가 코드·테스트·before11/final405·assemble·실패 이력·비식별/해시·23 snapshot을 확인했고 차단 결함은 없었다. 독립 문서 정합 최종 검토도 완료했고 합의 기능 한정 차단 결함은 없었으며 commit/push·배포는 하지 않았다.

독립 문서 리뷰에서 보존된 실패 XML의 `TransactionDetailsService.findCurrentDetails` → `TransactionRequestService.update:66` stack과 실제 `findCurrentDetails`의 `@Transactional(MANDATORY)`를 대조했다. 초기 실패 원인을 `details.create`로 설명했던 문구를 이 경계로 정정했다. `create` 자체는 READ_COMMITTED이며 이번 실패는 호출 전 조회 단계에서 발생했다. 나머지 수치·범위·근거가 정합하고 합의 기능 한정 차단 결함이 없음을 확인했다. 이번 보완은 문서만 변경했으므로 테스트를 재실행하지 않았으며 before11/final405와 각 원시 실패 이력을 유지한다.

## 분석·계획 당시 기록 (아래는 실행 전 이력)

2026-11-10 분석·계획 단계. 기준 HEAD `6438b969e3901d14bdd519c16de26c44d51ba68a`, 시작 Git clean. [명세](../../tasks/RF-009.md)의 사실·공식 근거·설계 후보·완료 조건을 참조한다.

## 수행한 확인

- `git status --short`, `git rev-parse HEAD`, `rg --files -g AGENTS.md`: 기준과 적용 지침 확인.
- `Get-Content` 및 `rg`로 ProductService/Controller/Product.from, RedisCacheConfig, PenaltyTran/Controller, TransactionRequestService, RF007 cache 테스트·RF004 최신 기능 결과·LOCAL_DEVELOPMENT를 읽었다.
- `Cacheable/CachePut/CacheEvict`, 판매/표시/끌올 setters·호출자를 main에서 검색했다. updateForSale의 main 호출자는 발견되지 않았다. 운영 외부 writer 부재의 증명은 아니다.
- Spring 공식 Redis 캐시·캐시 annotation·TransactionAwareCacheDecorator·TransactionSynchronization·SimpleCacheErrorHandler를 조회했다. 최신 문서 버전과 프로젝트 Boot3.2.4 차이를 명세에 표시했으며 프로젝트 실제 의존성 교차 확인은 후속 검증이다.

## 결과와 해석 한계

현재 상세 캐시30분 TTL/비transactionAware, PUT DTO put, 그 밖의 대상 writer 무효화 부재는 분석 시점 소스 사실이다. warm stale·동시 miss·커밋 후 Redis 오류의 실제 실행 결과는 아직 없다. 직접 TransactionSynchronization.afterCommit의 예외 전파는 공식 API 계약이며 NGNG HTTP 실제 응답/발생빈도는 미측정이다. TransactionalEventListener(AFTER_COMMIT)는 afterCompletion(COMMITTED) 단계이므로 이를 직접 callback과 혼동하지 않는다. TransactionPhase 공식 API를 추가 확인했다. 단순 eviction만으로 분산 강한 최신성을 보장하지 않는 경쟁 순서를 명세에 기록했다.

독립 계획 리뷰를 반영해 작은 협력자의 실제 tx+synchronization 커밋 후 evict/무tx 즉시 fallback, RuntimeException 차단·상품 ID/캐시명 로그, 뒤 callback 계속 실행·반복 evict 허용을 선택했다. 기존 Redis 미의존 writer 응답을 유지하기 위한 프로젝트 판단이며 PUT의 기존 CachePut·Redis 오류 전파 동작은 변경된다. 인가거부·사업조건거부·수락 no-op는 제외하고 private 제재/수락에 명시 연결한다. 공식 근거는 타이밍·예외 경계이며 장애 정책 자체의 외부 권고로 제시하지 않는다.

최종 검증에는 두 독립 Spring context가 공유 대역 cache/backend를 사용하는 결정적 동시 miss stale put 재현이 포함된다. 이 결과는 잔여 위험의 재현이지 해결·실제 Redis 검증이 아니다. 정상 커밋 후 무효화의 합의 기능은 검증 후 한정 완료 가능하며 강한 최신성·실패 복구·실DB/Redis는 별도로 미해결/미검증을 유지한다.

원시 읽기 출력은 대화 tool 결과에만 있고 별도 저장 로그·XML·SQL/Redis trace 없음. 새 테스트 실행·DB/Redis 연결·fixture 작성·운영 소스/설정 변경을 하지 않았다. RF004/007 기존 자료는 재실행이나 RF009 통합 증빙으로 대체하지 않는다.

## 미실행과 재개 조건

| 항목 | 미실행 사유 | 추가 방법·재개 조건 |
|---|---|---|
| before/after·회귀·최종 리뷰 | 계획 리뷰·실패 정책 선택 완료, 아직 실행 전 | 실제 proxy/tx lifecycle·대역 기반 선택 suite, 원시 XML/console·실행 소스/hash 보존 |
| 동시 miss/다중 context·Redis 오류 | 아직 구현·fixture 없음 | 결정적 latch·get/put/evict 오류를 분리, 보장과 재현된 잔여를 별도 기록 |
| MySQL commit/rollback·Redis serialization/TTL/네트워크 | 이번 사용 승인 및 identity 미확인 | 명세의 전용 환경 preflight·opt-in fixture·정확 소유행/key cleanup 후 독립 확인. 기존 MySQL 승인 이력을 새 Redis 승인으로 전용하지 않음 |
| 실제 HTTP/OSIV·브라우저·운영 다중 인스턴스 | 안전한 환경/계정·배포 자료 없음 | 후속 지시·격리 서버·외부 연결 차단·호출자 확인 |
| RF010 연관 DTO/이미지/프런트·검색 캐시 | RF009 대상 writer 밖 | 별도 명세·승인, 자동 착수 금지 |
| 성능 | 사용자 유예 | 별도 비교 계획과 후속 지시 |

현재 판정은 분석·계획 완료, 구현·기능 검증 미완료다. [BACKLOG](../../BACKLOG.md)의 R9 항목으로 추적하고 후속 결과는 당시 원시 증빙과 분리하여 추가한다.

공유 준비 시 전체 staging의 `git diff --cached --check`에서 원시 XML/콘솔 로그의 행 끝 공백이 검출됐다. 실행 출력 보존을 위해 수정하지 않았으며 기존 작업 트리만 검사한 `git diff --check` 결과와 구분한다.
