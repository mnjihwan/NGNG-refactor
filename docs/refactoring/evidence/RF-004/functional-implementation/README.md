# RF-004 기능 중심 구현 — 2026-11-06

최신 상태: 기능 중심 최소 구현·최종 오프라인43건·격리 MySQL24건 통과, 최종 독립 리뷰 완료. 최신 기능 범위에서 수정이 필요한 결함은 발견되지 않았다. 최신 사용자 지시의 REST 기능 범위를 위한 새 구현/검증 기록이다. 과거 37건의 임의 상위 트랜잭션·사전 관리 객체·독립 reader 회귀와 그 29통과/8실패 증빙은 당시 결과로 보존한다. 이 디렉터리의 결과로 과거 실패를 소급 대체하지 않는다.

## 구현과 범위

- 수락과 직접 거래 생성의 진입 트랜잭션에 READ_COMMITTED를 지정했다. 요청→상품 ID scalar 조회 뒤 상품 PK scalar `FOR UPDATE`를 얻고, 그 뒤 관리 요청·상품·거래를 조회한다. 같은 상품의 승인된 두 writer가 같은 잠금 규약을 사용한다.
- Product/Request는 관리 엔티티 setter/save로 변경한다. native bulk UPDATE·별도 REQUIRES_NEW committed-ID reader·ID 합집합·개별 요청/거래 native 잠금을 제거했다. 수락과 거래 생성/DTO 매핑은 같은 트랜잭션이다.
- 최초 수락·일치 재수락·충돌·거절 및 직접 생성의 기존 승인 정책과 HTTP 계약을 유지한다. 직접 생성에 상품 sold/요청 수락 변경을 추가하지 않았다. 인증인가·스키마·의존성·전역 OSIV/pool은 변경하지 않았다.
- 실제 controller/TokenFilter 경로에는 사전 Product/Request 로딩이나 controller 상위 트랜잭션이 발견되지 않았다. 임의 active outer RR 트랜잭션은 REQUIRED에 참여하므로 RC가 강제되지 않는다. 사전 stale 관리 객체·임의 caller pending/후속 flush 지원은 이번 보장 범위 밖이다.

## 실행 및 보존

- 변경 전: 기존 Policy26/Creation15 **41건 통과**, 전체 테스트 소스 컴파일 성공. `before/`에 실행 XML 및 당시 테스트 원문을 보존했다. Gradle 캐시 lock 접근이 sandbox에서 차단되어 첫 시도는 테스트 시작 전 실패했고, 기존 캐시 접근 승인 후 오프라인으로 실행했다.
- 변경 후: 갱신한 Policy26/Creation15 **41건 통과**, 전체 테스트 소스 컴파일 성공. `offline/` XML을 보존했다. native collaborator 순서 검사를 관리 엔티티 상태/save 순서와 RC 선언 검사로 바꿨다. 독립 reader 구조 전용 사례는 같은 업무 오류 전파/거래 조회 사례로 갱신했다. 과거 테스트 원문은 `before/`에 남겼다.
- 최종 nullable 기존 거래 buyer/seller 일치 검사 보강: Policy28/Creation15 **43건 통과**, 전체 테스트 소스 컴파일 성공. `offline-final/` XML/로그를 보존했다. buyer/seller가 null인 기존 거래는 일치 재수락으로 인정하지 않아 이전 projection처럼409를 반환하며 저장하지 않는다. 초기41건 XML을 덮어쓰지 않았다.
- 중간 편집에서 Python launcher를 실행하지 못해 테스트 갱신 전 컴파일을 시도했고 제거된 API 참조 42개로 compileTestJava가 실패했다. 이후 Node로 테스트를 갱신해 위 41건을 통과했다. 이 실패는 테스트 실행 결과가 아니며 원시 출력은 대화 tool 결과에만 있다.
- 새 `TransactionAcceptanceFunctionalMySqlTest`는 별도 기능 클래스로 추가했다. opt-in 없는 실행은 **24건 skip**으로 DB 접속 전 차단되었으며 기능 통과가 아니다. XML은 `opt-in-disabled.xml`이다.
- 과거 `TransactionAcceptanceMySqlConcurrencyTest`의 37개 테스트 메서드는 삭제/skip하지 않았다. 제거된 committed-ID reader 메서드의 직접 호출 한 곳을 새 repository entity 조회로 옮겨 컴파일만 유지했다. 그 사례의 reader/outer 기대를 통과했다고 보고하지 않는다. 과거 37건 전체는 새 범위의 성공 판정을 위해 재실행하지 않았다.
- 공유 XML의 hostname만 redacted로 정제했다. 정제 전 원본은 Git 제외 `local/rf004-results/functional-implementation/`에 보존했다. stdout 로그가 별도 보존되지 않은 오프라인 실행은 XML과 대화 tool 출력이 근거이며 후일 재실행을 최초 실행으로 기록하지 않는다.

명령은 backend 디렉터리에서 기존 Gradle 캐시만 사용한 `--offline --no-daemon compileTestJava test --rerun-tasks`와 선택 `--tests` 조합이다. 신규 다운로드나 전체 Boot/SpringBootTest 실행은 하지 않았다.

## 격리 MySQL 안전 조건

실행 전 새 runner와 새 테스트·기존 fixture를 독립 리뷰했다. `local/rf004-results/functional-implementation/verify-and-run.ps1`은 이전 증빙을 덮어쓰지 않는 새 경로를 사용한다. 기존 전용 fixture는 무수정 재사용하며 DDL/initializer/외부 서비스 없이 JPA validate만 사용한다. PID·loopback listener·executable·private datadir·account·schema·version·isolation·빈 fixture 테이블을 확인하고, InnoDB·UNIQUE·FK·소유 PK 및 정확 행 cleanup 검사를 유지한다. 추가로 현재 schema trigger/event와 외부 schema inbound FK가 0인지 CLI 읽기로 확인한다.

DB 재기동 사전 가드에서 이미 있는 listener를 발견해 재기동을 중단했다. 새 읽기 전용 preflight에서 PID22580의 기존 전용 MySQL8.0.35·loopback13306·private datadir 일치 및 관련/공통 테이블0, trigger/event/외부 schema inbound FK0을 확인했다. 서비스 시작/스키마 생성은 하지 않았다. 자격값은 프로세스 환경에만 전달하고 공유 자료에 남기지 않는다.

## 한계와 후속 조건

기능 fixture의 정상 호출은 운영 flush 시점을 유지한다. fault/barrier hook을 설치한 검사만 주입 지점을 확정하려고 명시 flush한다. ordered latch 도달은 DB 대기 시작의 직접 관측이 아니다. 연결 ID는 상품 잠금 시점에 채집한 샘플이며 모든 미관측 JDBC 연결 획득이 없었음을 증명하지 않는다. 연결 ID/isolation 관측과 StatementInspector SQL 검사는 이 최소 JPA fixture의 기능 근거이며 운영 OSIV/pool 전체 또는 성능 근거가 아니다.

HTTP standalone MVC는 실제 서버/브라우저·전체 보안 필터·외부 연동·운영 DB를 대체하지 않는다. 다른 상품 writer, 수락 중 신규 요청 삽입, 과거 데이터 복구, 재판매/취소, 임의 사전 관리 객체/outer 트랜잭션, 운영 pool 및 성능은 별도 범위다. 실제 호출 전제가 바뀌면 서비스 경계와 격리수준·사전 관리 상태부터 다시 검토해야 한다.

## 격리 MySQL 실제 결과

- `mysql/mysql.xml`: **24건 통과, failure/error/skip0**, Gradle exit0. MySQL8.0.35의 기존 기본 RR 환경에서 이 서비스 트랜잭션이 실제 READ-COMMITTED/write/autocommit=false인지 관측했다. 수락 한 호출의 두 재진입 상품 잠금은 같은 connection ID였고, 첫 요청→상품ID scalar 조회 다음 상품PK FOR UPDATE, 그 다음 eager entity SELECT였음을 SQL 원문으로 보존했다. 잠금 SQL에 eager JOIN이 없음을 검사했다. 이 관측이 모든 미관측 repository 동작이나 운영 pool을 증명하지 않는다.
- 최초/동일재수락, 요청없음, 거절/재거절/거절뒤수락, 동일/서로다른 요청 동시 수락, 직접 최초/중복/동시 생성·수락과 직접 생성 양 순서, 수락먼저 거절/거절먼저 수락, winner rollback뒤 waiter 성공, 상품/거래/형제요청 변경뒤 실패·매핑·rollback-only·beforeCommit 실패의 원자성을 검증했다. 동일재수락은 SQL INSERT/UPDATE/DELETE0과 fixture 상태/updatedAt 불변을 함께 확인했다.
- 이미 sold인 상품과 이미 false인 형제요청은 불필요 UPDATE 없이 수락했다. 사전에 대상 엔티티를 읽지 않은 빈 OSIV EntityManager holder를 bound한 경로도 정상 수락/재수락했다. 이 검사는 실제 Servlet OSIV 필터 자체나 사전 stale entity 지원의 증명이 아니다.
- 공유 seller/category/status의 다른 상품 처리, absent-details 읽기와 insert 사이의 독립성, 양쪽 absent-read 뒤 동시 insert, 의도한 real lock timeout1205·무쓰기·후속 성공을 검증했다. latch/Future로 순서를 조절했으며 DB performance_schema의 직접 잠금 대기 trace나 성능 측정은 아니다. timeout1205는 기대한 테스트 결과이며 suite 실패가 아니다.
- worker를 모두 종료한 뒤 정확 소유행 details26/request56/thumbnail26/product28/user81/category1/status1/transaction_status1을 삭제·commit했고, 새 CLI 연결의 `mysql/postflight.json`에서 관련/공통 테이블0 및 metadata guard0을 확인했다. 기존 transaction_status1이 없어 fixture가 소유행을 생성/정리했다. 기존행 재사용 분기는 이번에도 미실행이며 원본 보존 로직의 실행 증거로 확대하지 않는다.
- `mysql/preflight.json`, `postflight.json`, `mysql-run.log`, `mysql.xml`이 공유 증빙이다. private datadir·account·hostname·개인 경로는 공유본에서 정제했다. 정제 전 XML/로그/환경은 Git 제외 local 경로에 보존했고 삭제하지 않았다. 성능·운영·전체 HTTP 완료를 주장하지 않는다. 최종 독립 리뷰와 범위 밖 잔여 사항은 루트 명세·증빙 README·BACKLOG의 최신 기록을 따른다.
- nullable guard 보강 뒤 최종 소스로 같은24건을 재실행했고 **24통과/failure·error·skip0**, Gradle exit0이다. `mysql-final/` XML/로그/preflight/postflight에 별도 보존했다. 정확 소유행 정리와 새 CLI 관련/공통테이블0을 다시 확인했다. 재실행은 초기24건 결과를 대체하지 않는다. `source/`, `source-hashes.json`은 초기24 실행 소스, `source-final/`, `source-final-hashes.json`은 최종43/24 실행 소스다.


2026-11-06 최종 독립 리뷰: nullable 거래 buyer/seller null 가드를 포함한 운영 변경, 최종 오프라인43/격리MySQL24 XML, 테스트 실행 소스 해시 일치와 정확 소유행 cleanup·독립CLI 잔여0을 확인했고 최신 기능 범위에서 수정이 필요한 결함을 발견하지 않았다. 합의한 최소 기능 범위의 구현·선택 검증·독립 리뷰를 완료한다. nullable 오염 데이터의409는 단위 테스트2건만 검증했으며 실제 MySQL 오염 fixture는 실행하지 않았다. 실제 HTTP/Servlet OSIV·브라우저/전체 보안·운영 writer/신규요청·운영 pool/성능, connection ID 잠금시점 샘플·latch/주입flush 한계 및 임의 outer/stale 관리객체 비지원은 그대로 유지한다. 이 잔여 검증은 BACKLOG의 재개 조건을 따르며 자동 착수하지 않는다.
