# 리팩토링 작업 기록

최신 상태(2026-11-06): 합의한 기능 중심 최소 구현·선택 검증·최종 독립 리뷰를 완료했다. 최신 기능 범위에서 수정이 필요한 결함은 발견되지 않았다. 변경 전 오프라인41건, 최종 오프라인43건(Policy28/Creation15), 격리 MySQL 기능24건 통과. 과거37건29통과/8실패는 이전 범용 내부 호출 설계의 실행 이력이며 보존한다. 임의 사전 관리 객체/active outer RR 트랜잭션은 최신 지원 범위 밖이다. 운영 배포·전체 HTTP/브라우저·성능 검증 완료를 뜻하지 않는다. 아래 날짜별 상태는 당시 이력으로 보존한다.


2026-11-05 최신 상태: RF-001 완료, RF-002 최소 구현·오프라인 검증 후 전체 미완료, RF-003 합의된 기능 검증·독립 리뷰 완료, RF-004는 실제 DB 관리 상태 정합성 실패로 사용자 지시 보류·전체 미완료/배포 부적합. 각 날짜의 계획·미완료·유예 기록은 당시 이력이며 최신 한계와 후속 조건은 해당 명세·증빙 및 BACKLOG 잔여 목록을 따른다.

## 2026-11-04 — 백엔드 사전 분석

- 상태: 분석 완료, RF-001 계획 수립, 구현 미착수.
- 대상: NGNG-backend `e0c81ade16b25ec683c24320100069fd0406e682`. 프런트/채팅은 호출자·공유 테이블 매핑 확인에 한정했다. 세 저장소 기준 커밋은 BASELINE에 기록했다.
- 지침: 루트 AGENTS.md와 docs/refactoring/README.md 및 템플릿을 읽고 사용자 요청대로 분석 문서만 작성했다. 새로운 의존성·DB 스키마·인증 정책·애플리케이션 및 테스트 코드는 변경하지 않았다.
- 결과: ARCHITECTURE의 F01~F09에 정적 사실/결함 경로/성능 가설을 분리했다. BACKLOG에 독립 후보와 선행 조건을 정리했다. 첫 작업은 테스트 컴파일 복구·사용자 목록 계약 테스트 확보다.
- 실제 검증: Java 17.0.10 확인, Java 파일/테스트 선언 집계, 세 저장소 HEAD·작업 트리 조회. `gradlew.bat --offline --no-daemon compileTestJava`는 Gradle 8.7 다운로드에서 SocketException Permission denied, exit 1. 컴파일러·테스트 runner에 도달하지 않았다.
- 미실행: 기존 전체 test/build, 서버 기동, 외부 서비스 연결·메일/SMS/S3 호출, SQL 및 부하 측정. 테스트 YAML의 ddl-auto:update·외부 자격 정보 리터럴·메일 호출을 확인하여 격리 전 통합 테스트를 실행하지 않았다. 비밀값은 문서에 복사하지 않았다.
- 주요 위험: 테스트 생성자 불일치, 포인트/거래 경쟁 조건, 토큰/소유권/민감 필드 경계, 캐시 무효화 누락, 사용자별 반복 조회, 빈 결과 예외, 로그 전체 읽기 및 외부 I/O 부분 실패.
- 검토: 문서 근거·범위·상호 링크를 자체 점검했고 끊어진 상대 링크는 없었다. 세 저장소 모두 종료 시 git status --short / diff --stat 출력이 없고 diff --check가 통과했다. 별도 검토자의 독립 리뷰는 미실시다. 루트 docs는 Git 밖에 있으므로 세 저장소 diff에 나타나지 않는다.
- 남은 문제: Wrapper/의존성 실행 환경, 격리 DB fixture·외부 대역, 실제 오류 응답·정책, 운영 DB/ES 동기화/배포 자원 정보 확보. 성능 개선 수치 없음.
- 종료 조건: 분석 문서 작성 후 멈춘다. 커밋·push·배포 및 다음 작업 자동 실행 없음.

## 2026-11-04 — 통합 저장소 기준 계획 독립 검토

- 상태: 읽기 전용 검토 완료. RF-001 구현 및 테스트 실행은 하지 않았다.
- 현재 기준: 통합 저장소 HEAD `ab77676ae968354c47408002926119e3f3843c69`, 검토 시작 시 작업 트리 clean. 세 프로젝트에 별도 Git 저장소가 없고 문서는 통합 저장소에서 추적된다. 이전 분석의 세 커밋은 현재 객체 데이터베이스에서 조회되지 않아 당시 소스와 현재 소스의 동일성은 검증하지 못했다. 위 과거 기록은 당시 상태로 보존한다.
- 분석·계획: README, ARCHITECTURE, BASELINE, RF-001의 저장소 구조 및 기준점 설명을 현 상태와 구분해야 한다. 백엔드의 중첩 `.github/workflows`가 통합 저장소에서 활성 CI임은 확인되지 않았고, RF-002에는 격리 DB·fixture와 실제 SQL 검증 선행 조건을 구체화할 필요가 있다.
- 테스트 계획: 생성자 불일치와 사용자 목록 호출 구조는 현재 코드에서 정적으로 재확인했다. 서로 다른 역할·잔액을 포함한 DTO 전체 필드와 유효한 페이지 fixture를 권장한다. Gradle 8.7 및 일부 의존성 캐시 파일은 존재하지만 실제 실행 가능성·컴파일 결과는 미검증이다. mock 테스트는 실제 DB·보안·운영 직렬화를 증명하지 않는다.
- 독립 리뷰: 계좌 확인의 요청 대상 토큰 발급, 거래 요청의 소유권 검사 부재는 P0 보안 조사 후보, 관리자 수정의 path/body ID 불일치는 P1 정합성 조사 후보다. 이는 정적 코드 경로에 대한 판단이며 실제 HTTP 재현·운영 도달 가능성은 확인하지 않았다.
- 권장 순서: 현재 기준점과 계획 정리, P0 보안 위험 조사 우선 병행, 승인된 범위에서 RF-001 검증 기반 확보, 격리 환경 확보 후 RF-002 착수 여부 판단. CI 정비는 별도 범위로 검토한다. 자동 구현·커밋·배포는 하지 않았다.

## 2026-11-04 — 통합 저장소 문서 및 로컬 실행 준비

- 기준: 통합 HEAD `ab77676ae968354c47408002926119e3f3843c69`; 기존 독립 검토 기록의 미커밋 변경을 보존했다. 로컬 실행을 별도 작업으로 진행했으며 RF-001 구현은 시작하지 않았다.
- 범위: 현재 Git 구조를 문서에 반영했다. 로컬 전용 MySQL·Redis·Elasticsearch·MinIO·Mailpit Compose 구성, 두 서버의 독립 설정, 프런트 환경 예제 및 실행 안내를 추가했다. S3 클라이언트의 선택적 로컬 엔드포인트, 로컬 SMS 발송 차단, 로컬 아바타/이미지 설정을 추가했다. 운영 기본 S3 연결과 SMS 발송 설정은 유지한다.
- 검증: Java 17.0.10, Node 20.10.0 및 Gradle 8.7 확인. 기존 잠금 파일의 npm 패키지 설치 성공. 양 서버 `compileJava` 성공, 프런트 lint 성공(기존 경고 3건), Compose 구문 검증 성공. 백엔드 `compileTestJava`는 기존 DTO 생성자 불일치 1건으로 실패. 프런트 build는 컴파일과 타입 검사를 통과했지만 서버가 없는 상태에서 상품 메타데이터 fetch가 실패해 완료되지 않았다.
- 제약: Docker Desktop은 WSL 2 가상화 설정 미비로 엔진이 시작되지 않아 컨테이너·서버 간 연동을 검증하지 못했다. DB 스키마 승인 전 `ddl-auto: validate`로 고정했으며 로컬 스키마를 생성하지 않았다. 테스트 YAML의 외부 연결·DDL 위험 때문에 기존 전체 통합 테스트는 실행하지 않았다. Cypress 13.10.0 바이너리는 설치됐으나 실행하지 않았다. 나머지 절차는 로컬 개발 안내를 확인한다.
- 영향과 롤백: 운영 프로파일을 변경하지 않는다. 로컬 파일과 이 작업의 코드·문서 변경만 선택적으로 되돌린다. 기존 사용자 변경이나 Docker 볼륨을 일괄 삭제하지 않는다.

## 2026-11-04 — 승인된 격리 로컬 DB 스키마 생성

- 사용자가 로컬 DB 스키마 생성을 승인했다. Docker/WSL은 여전히 사용 불가하여 기존 MySQL80 서비스와 별도로 MySQL 8.0.35 인스턴스를 loopback 포트 13306에 시작했다. 별도 데이터 경로와 개발용 자격 증명을 사용했고 MySQL X 포트는 비활성화했다.
- 빈 `ngng_local`에 백엔드 엔티티의 스키마를 생성하고 채팅 엔티티의 테이블을 추가했다. 채팅 `User.channel`의 `@ColumnDefault("LOCAL")`에서 생성된 SQL이 문자열 따옴표 누락으로 실패하여 로컬 DB에 `DEFAULT 'LOCAL'` 열을 명시적으로 추가한 후 생성 작업을 완료했다. 운영 코드는 변경하지 않았다.
- 결과: 테이블 21개, 외래 키 31개. 별도 JPA 부트스트랩으로 백엔드와 채팅 양쪽 `validate` 성공. 데이터 fixture, 서버 전체 기동 및 기능 검증은 미실행이다. 데이터 없는 스키마를 `local/schema.sql`로 저장했고 새 Docker MySQL 볼륨에서만 초기화되도록 설정했다. 기존 볼륨이나 기존 서비스 데이터는 변경하지 않았다.
- 제약: 생성 SQL은 현재 엔티티에 기반한 개발용 스키마이며 운영 DB와의 동등성은 미검증이다. Docker, Redis, Elasticsearch 및 나머지 로컬 서비스 기동은 WSL 환경 제약으로 남아 있다. 개별 인스턴스의 경로와 시작법은 로컬 개발 안내에 기록한다.

## RF-001 — 테스트 컴파일 복구 및 사용자 목록 계약 테스트 확보

- 날짜·상태: 2026-11-04, 완료. 통합 HEAD `ab77676ae968354c47408002926119e3f3843c69`에 기존 로컬 환경 미커밋 변경이 있는 상태에서 시작했다. RF-001은 테스트 코드와 이 문서들만 변경했으며 이전 변경을 보존했다.
- 기준선: 변경 전 `compileTestJava`는 UserControllerTest의 2인자 DTO 생성자 호출 1건에서 실패했다. nickname/address 입력을 유지하고 나머지 두 인자를 null로 추가해 컴파일을 복구했다.
- 구현: UserService의 두 사용자·역할·잔액 매핑, page 0/2와 메타데이터, 빈 페이지, 음수 페이지, 포인트 이력 부재 예외 후보를 mock으로 검사했다. standalone MockMvc는 기본 page, page 2의 DTO/JSON 및 잘못된 페이지 바인딩을 검사한다. 정상 조회 시 mock 쓰기 호출도 확인했다.
- 검증: `./gradlew.bat --no-daemon compileTestJava` 성공; `./gradlew.bat --no-daemon test --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest'` 성공. XML 서비스 5건, 컨트롤러 3건, 총 8건, 실패·오류·skip 각 0. 첫 실행의 PageImpl fixture 총 건수 불일치 2건은 fixture 정정 후 통과했다.
- 독립 리뷰: 구현 담당자와 별도로 테스트와 기존 테스트 diff, XML 결과를 확인했다. DTO 필드·순서·페이지·역할과 null 결함 후보의 분리를 확인했고 추가 수정할 사항은 없었다. 사용자 목록 SQL, 실제 DB 쓰기, SecurityFilterChain 및 운영 Jackson 설정은 이 테스트로 증명되지 않는다. 기존 전체 통합 테스트/build는 외부 연결·DDL 위험 때문에 실행하지 않았다.
- 롤백: RF-001의 테스트 파일과 해당 문서 기록만 선택적으로 되돌린다. 기존 로컬 환경 변경은 보존한다. RF-002는 시작하지 않았다.

## 2026-11-04 — RF-001 이후 작업 분석

- 분석 에이전트가 문서와 관련 코드를 읽기 전용으로 검토했다. 통합 HEAD `ab77676ae968354c47408002926119e3f3843c69`, 기존 로컬 환경 및 RF-001 미커밋 변경이 존재한다. 테스트·DB 접속·애플리케이션 수정은 하지 않았다.
- 확인: `ConfirmService.accountConfirm`은 본문 userId로 대상을 선택하고 그 대상의 계좌·역할을 변경한 뒤 토큰을 발급하며, 해당 서비스에서 인증 요청자와 대상의 비교는 발견되지 않았다. 컨트롤러는 refreshToken 쿠키를 요구하고 보안 설정은 UNCONFIRMED_USER 역할을 요구한다. 실제 필터 포함 도달성·운영 영향은 미검증이다.
- 확인: 거래 요청 생성 및 수락/거절 서비스에서 요청자와 구매자·판매자 소유권 비교를 발견하지 못했다. 실제 HTTP 접근·DB 영향은 미검증이다. 동시·반복 수락을 다루는 RF-004와 구별해 RF-021로 분리했다.
- 권장: RF-020 계좌 확인의 본인/타인·역할·쿠키와 토큰/DB 결과를 격리 재현하고 정책을 확정한다. 그다음 RF-021 거래 소유권, RF-016의 안전한 테스트 환경·fixture, SQL 기준선 확보 후 RF-002를 검토한다. 기존 로컬 MySQL 스키마는 fixture·외부 연동 격리의 증거가 아니다. Docker 엔진은 이번 샌드박스 조회에서 연결되지 않았으며 호스트 전체 상태는 재확인이 필요하다.
- 정책 변경·수정 구현은 별도 작업으로 남기고 자동 착수하지 않았다. 분석 과정의 발견을 BACKLOG에 기록하고 오래된 로컬 테스트 설명을 갱신했다.

## 2026-11-04 — RF-002 작업 명세

- 사용자가 다음 작업을 RF-002로 지정했다. 분석·계획 에이전트(GPT-6 Astra / High)가 기준 문서·호출 관계를 확인하고 [RF-002 명세](tasks/RF-002.md)를 템플릿에 따라 작성했다. 통합 HEAD `ab77676ae968354c47408002926119e3f3843c69`이며 기존 로컬 환경·RF-001 미커밋 변경이 존재한다.
- 범위는 관리자 사용자 페이지의 최신 포인트 조회다. 사용자별 최대 이력 ID와 순서·페이지·기존 이력 부재 예외를 보존하고, RF-016 전체가 아닌 필요한 격리 DB fixture와 외부 연동 차단, 실제 SQL·실행계획·지연 측정을 선행 조건으로 명시했다. 동시 갱신 시 읽기 시점 차이는 별도 관찰 대상으로 남긴다.
- 현재 SQL 수·성능·실제 DB 결과는 미측정이다. RF-020/021의 P0 위험은 별도로 남는다. 코드·테스트·설정 수정, 테스트·DB 접속, RF-002 구현은 이번 명세 작업에서 수행하지 않았다.
- 자체 검토: 템플릿 항목, 적용 경로와 기존 기준점, 계약·격리·측정·롤백 경계를 점검했다. 차후 독립 구현 리뷰와 실제 측정은 미실시다.

## 2026-11-04 — RF-002 최소 구현 및 오프라인 기능 검증

- 상태: **진행, 전체 미완료**. 명세 확인 후 사용자 지시로 구현했다. 사용자가 성능 테스트(SQL 수·실행계획·지연 측정 포함)를 추후로 지정하여 선행 성능 기준선과 모든 성능 계측을 유예했다.
- 기준: 현재 HEAD `17adfc75e1315b4ad832a5114f36953837870e9f`, 기존 미커밋 사용자 변경 보존. [변경 전 대상 원본·Git 상태·관련 파일 28개 해시·전후 테스트 XML](evidence/RF-002/README.md)을 남겼다. 설정 원문/비밀값을 복사하지 않았고 해시만으로 환경 복원이 된다고 가정하지 않는다.
- 구현: 허용된 UserService 페이지 메서드, PointHistoryService 읽기 메서드, PointHistoryRepository 쿼리만 변경했다. 페이지 ID 안에서 사용자별 최대 이력 ID를 선택하고 결과 순서를 가정하지 않고 ID로 대응한다. 빈 Page·빈 ID는 포인트 조회에 도달하지 않는다. NPE 결함 후보를 0원/성공으로 바꾸지 않았다. 단건·무인자·쓰기·Controller/DTO·fetch·트랜잭션·인가·스키마·의존성은 유지했다.
- 테스트: 기존 UserServiceTest의 DTO/Page assertion을 유지하고 역순 batch·5명·null/음수 비용·혼합 이력 누락·기존 단건/무인자 경로 회귀를 보강했다. 신규 PointHistoryRepositoryBatchTest는 DB 없이 Hibernate의 JPQL 경로 해석과 empty/위임을 검증한다. 컨트롤러 계약 테스트는 변경하지 않았다.
- 실제 명령/결과: 첫 sandbox Wrapper는 캐시 lock 접근 거부, 캐시 접근 허용 후 첫 선택 test는 UP-TO-DATE. 변경 전 `./gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest'` 실제 실행 성공(8건). 변경 후 `./gradlew.bat --offline --no-daemon compileTestJava test --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest' --tests 'com.ngng.api.user.point.repository.PointHistoryRepositoryBatchTest'` 성공(서비스 9·컨트롤러 3·repository 경계 3, 15건, 실패/오류/skip 각 0). 전후 XML timestamp와 원본은 기준선/증빙에 기록했다.
- DB·성능 제약: loopback 13306 listener 미조회 및 mysqld CIM 조회 권한 제약으로 안전한 DB를 확인하지 못해 접속/쓰기하지 않았다. 실제 MySQL fixture·결과 동등성·무쓰기·동시 append·운영 JSON/오류 응답은 미검증이다. AdminUserPageQueryIntegrationTest/실DB fixture는 미구현. 기존 전체 test/build·외부 발송·서버 기동은 실행하지 않았다. SQL 수·실행계획·지연 측정은 사용자 지시로 실행하지 않았다. SQL 감소나 지연 개선은 주장하지 않는다.
- 독립 리뷰: GPT-6 Astra / High가 구현 diff, 호출 관계, 전후 테스트 XML 및 기존 변경 보존을 검토했고 수정이 필요한 코드 결함은 발견하지 못했다. 명세의 재실행 이력 모순과 결과 파일 링크는 구현 담당자가 정정한 뒤 재확인했다. 기존 단건 쿼리/쓰기/인가/스키마/새 의존성 변경은 발견하지 못했다.
- 남은 검증: 안전한 격리 DB·fixture 확보 후 실제 MySQL 기능·동시 읽기 검증과 사용자 지시에 따른 후속 성능 기준선/전후 비교 및 그 결과의 독립 리뷰가 필요하다. 이 검증 전 RF-002 완료로 표시하지 않는다.
- 롤백: 보존한 변경 전 대상 원본을 참고해 RF-002의 메서드/쿼리/테스트/문서만 선택 복구한다. 전체 reset/clean 금지. DB·스키마 변경이 없어 데이터 롤백은 없다. commit/push는 하지 않았다.

## 2026-11-04 — 로컬 설정의 안전한 Git 공유 준비

- 범위: 기존 로컬 실행 변경을 공유하기 전에 자격값과 개인 PC 경로를 분리했다. 실제 `local/backend.yml`·`local/chat.yml`은 수정하지 않고 Git에서 제외했다. PC별 경로·기동 명령이 있던 개발 안내 원본은 ignored `local/LOCAL_DEVELOPMENT.private.md`에 보존했다. 기존 `.env.local`과 데이터 파일·볼륨도 변경하지 않았다.
- 공유 구성: `local/backend.example.yml`·`local/chat.example.yml`과 빈 필수 값의 루트 `.env.example`을 추가했다. Compose의 MySQL·MinIO 자격값은 필수 환경변수로 전달하며 MinIO 초기화도 동일한 변수를 사용한다. 공유 안내에는 처음 파일 생성, 로컬 자격값 입력, 각 서버 PowerShell 환경 로딩 및 기존 볼륨 자격값 보존을 설명했다. 승인된 로컬 스키마 생성·validate 상태를 LOCAL-SETUP에 반영했다.
- 검증: 임시 검증용 값으로 `docker compose -f compose.local.yml config --quiet` 성공. 빈 `.env.example`은 필수 자격값 누락으로 거부됨을 확인했다. 기존 설치된 YAML parser로 양 예제의 구문·원본 키 구조·자격값 이외 설정 동일성, 환경변수 예제 일치, `ddl-auto: validate`와 SMS 차단을 확인했다. 공유 파일의 개인 절대 경로와 기존 긴 비밀값 재포함 검사는 통과했다. 실제 YAML 2개와 `schema.sql`의 변경 전후 SHA-256이 같고 ignore 규칙·공유 대상 분리가 확인됐다. `git diff --check` 통과(기존 CRLF 변환 안내만 존재).
- 추가 검증 및 독립 리뷰: 백엔드 `./gradlew.bat --offline --no-daemon compileJava` 성공(UP-TO-DATE), 프런트 `npm run lint` 성공(기존 경고 3건). GPT-6 Astra / High 독립 리뷰에서 공유 범위, 설정 예제·환경변수 정합성, 개인 경로와 기존 자격값 노출, 기존 기본 동작을 확인했고 수정이 필요한 문제는 발견하지 못했다. 실제 서버 간 연결을 증명하는 결과는 아니다.
- 한계: Docker 엔진·서버·실제 DB 접속, 스키마 변경, 전체 통합 테스트와 성능 테스트는 실행하지 않았다. Compose 구문 검증이 서비스 간 연동 성공을 의미하지 않는다. 신규 의존성 추가와 commit/push는 하지 않았다. 원본 구성과 개인정보 기록을 되살릴 때는 보존한 ignored 파일을 참고하며 기존 데이터나 볼륨을 삭제하지 않는다.

## 2026-11-04 — RF-003 분석 및 정책 확인

- 상태·기준: 계획. 사용자 지정 RF-003의 분석 시작 HEAD `237224bd46e31f3d5c789d0699e1f896503b67c8`, 시작 Git 작업 트리 clean. 루트 지침·README·BACKLOG·TEMPLATE·RF-001/RF-002와 호출자를 검토하고 [RF-003 명세](tasks/RF-003.md)를 작성했다.
- 확인 사실: updateCost/payment는 인증 → 최신 ID 이력 조회 → 계산 → append이며 서비스 트랜잭션·사용자 잠금이 없다. payment 잔액 검사는 없고 check만 `>`를 사용한다. 가입 초기화는 별도 가입 트랜잭션 안에 있다. 실제 동시 누락·HTTP 오류·잠금 범위는 미재현이다.
- 사용자 정책: 양수 충전·결제만 허용, 같은 잔액 결제 허용, 부족 시 결제·이력 저장 거부. 중복 결제 방지는 별도 작업. 프런트 결제 실패 후 거래 상태 변경 차단의 최소 수정·검증도 사용자 승인됐다. check는 양수 및 잔액 이상 조건을 적용하며 신규 거부 조건의 HTTP 400·Long 오버플로 무쓰기 거부는 최소안으로 제안하고 실제 오류 계약 기록을 요구했다.
- 제안: 사용자 PK 잠금과 쓰기용 최신 current/locking read를 같은 쓰기 트랜잭션에 두는 최소안. 인증 조회가 먼저 만드는 RR snapshot, eager 연관의 잠금 범위, 다른 사용자 독립성, rollback을 실제 MySQL 검증 완료 조건에 넣었다. 구현 선택과 성공을 아직 확정한 기록은 아니다.
- 이번 변경 범위: 명세·백로그·로그만. 코드/테스트/설정/스키마·의존성·인증/인가 수정과 DB 접속/쓰기·테스트 실행·성능 측정은 하지 않았다. 격리 DB/fixture 안전 조건과 동시성 검증 계획을 기록했으며 실측 효과를 주장하지 않는다.
- 남은 작업: 추가 계약 경계 확정, 최소 구현·기준선/회귀 테스트, 안전한 격리 MySQL 동시성·rollback 검증, diff·독립 리뷰. 성능 테스트 및 범위 밖 변경은 사전 확인 대상이다. 자동 commit/push 없음.

## 2026-11-04 — RF-003 최소 구현 및 오프라인 검증

- 상태·기준: **진행, 전체 미완료**. HEAD `237224bd46e31f3d5c789d0699e1f896503b67c8`의 분석 문서 변경을 보존했다. 명세가 허용한 코드/테스트/프런트 최소 범위만 구현했다.
- 구현: 충전·결제를 쓰기 트랜잭션으로 묶고 사용자 PK native scalar `FOR UPDATE` → 최신 cost native scalar current read → 양수/잔액 검사 → append 순서를 공유한다. scalar는 eager User/Role 조인 및 관리 엔티티의 이전 잔액 재사용을 피하려는 선택이며 실제 MySQL 잠금 범위/효과는 미검증이다. 가입 초기 0원·조회/RF-002 최대 ID·성공 상태 및 PointHistory 응답 구조를 유지했다.
- 금액/오류: 충전·결제는 양수, 결제 동액 성공, 부족·충전 Long overflow는 저장 전 400. check는 양수이며 balance >= amount일 때만 200 true, 0/음수는 200 false, null/누락/바인딩 실패는 400. standalone 부족 응답은 400·빈 본문·content type 없음으로 관찰됐으나 Boot `/error` 및 실제 필터 payload는 미검증이다. 새 advice/공통 DTO/재시도/자동 초기화는 없다.
- 프런트: isPayment의 HTTP/Boolean 검사, paymentProduct의 HTTP 검사 및 onPayment 오류 처리를 적용했다. 실패 시 후속 거래 상태 변경을 호출하지 않으며 결제·거래 전체의 원자성과 멱등성은 별도로 남긴다.
- 실행: 변경 전 backend 19건, 변경 후 **74건(신규 특성4/정책27/MVC28 + 기존15), 실패/오류/skip 0**. frontend API/버튼 handler 오프라인 Node **10건 통과**, 변경 파일 ESLint와 전체 tsc 통과, backend offline `assemble` 성공. 명령과 XML은 [RF-003 증빙](evidence/RF-003/README.md)에 보존했다. Wrapper의 최초 sandbox 캐시 lock 거부 후 같은 명령의 기존 캐시 접근을 허용받아 실행했다.
- 한계: 실제 격리 대상·fixture 쓰기 범위가 확증되지 않아 DB 연결/쓰기/cleanup을 하지 않았다. 실제 MySQL SQL 실행·동시 충전/결제/혼합·부족 경쟁·RR snapshot·다른 사용자·DB rollback 및 실제 HTTP/오류 body는 미검증이다. 실DB 전용 테스트/fixture도 미구현이다. 기록용 transaction manager 프록시의 rollback 호출을 DB rollback 성공으로 세지 않는다. 전체 backend test/build 및 API를 조회하는 frontend 프로덕션 build, 서버 기동·성능 테스트는 실행하지 않았다.
- 자체/독립 검토: diff와 `git diff --check`를 확인했다. 별도 리뷰 담당자가 구현 diff·호출 관계·19/74건 XML과 금액/잠금 순서·프런트 실패 차단을 검토했고 확정 코드 결함은 발견하지 못했다. 안전한 격리 DB 검증 및 그 결과의 후속 독립 리뷰가 남아 전체 미완료다. 실제 Boot 오류 body와 브라우저도 미검증이다. 스키마/의존성/인증/인가·성능 변경, commit/push는 없으며 롤백은 RF-003 diff만 선택 복구한다.

## 2026-11-04 — RF-003 승인 후 격리 MySQL 기능 검증

- 승인/범위: 사용자 지시로 격리 대상·정리 범위를 먼저 확인하고 테스트 전용 user/point_history만 생성·정리했다. 스키마/role/기존 데이터 변경과 성능 테스트는 하지 않았다.
- 안전 확인: listener 13306의 소유 mysqld PID22580·실행 옵션/전용 datadir가 기존 ignored 로컬 기록과 일치했다. 읽기 전용 메타데이터는 ngng_local, ngng@127.0.0.1, MySQL8.0.35, RR, InnoDB 및 기존 PK/FK/인덱스와 user/point/role 각0행을 확인했다. 테스트 내부도 고정 loopback/schema·계정/port/datadir/RR/InnoDB·user/point 빈 상태를 재검사하며 Boot/YAML/외부 연동을 로드하지 않고 JPA validate만 쓴다.
- 안전 리뷰/실행: opt-in 없는 실행은 13건 모두 skip하여 DB 비접속을 확인했다. 독립 안전 리뷰가 확정 오삭제/대상 혼동 결함 없음과 role 부재·잠금 대기 관측 한계를 확인한 뒤 실제 opt-in 13건을 실행했고 **실패/오류/skip0**으로 통과했다. 기존 read/compute/append의 누락(1000+100/+200 뒤 최신1200, 기대1300)을 barrier로 재현했다. 변경 서비스는 동시 충전·결제·혼합·부족, 실제 AuthService 일반 read의 RR snapshot/관리 엔티티 stale, 다른 사용자 진행, flush 후 rollback/rollback-only·잠금해제, 의도한 timeout1205, 정책/재전송/단건-batch 조회 회귀를 검증했다.
- 정리: 실행 UUID marker+정확 user/email/name 및 point_id/user_id/type_detail을 검증해 user15/point38행만 정리했다. worker 종료 전 삭제하지 않고 전체 테이블/볼륨/role 삭제 없이 FK 순서로 수행했다. afterAll과 별도 읽기 전용 CLI 연결에서 user0/point0/role0·잔존 fixture0을 재확인했다. XML 및 재현/정리 로그는 [RF-003 증빙](evidence/RF-003/README.md)에 있다.
- 남은 한계/상태: 공통 role 사례는 role0행이며 role 생성 승인 밖이라 미검증, 다른 사용자 진행은 role=null fixture 결과다. proxy beforeLock 도달/Future 미완료는 DB 내부 대기 직접 관측이 아니다. 실제 Boot 오류 body·보안 필터·브라우저·운영 DB/다른 writer·성능 검증은 수행하지 않았다.
- 독립 리뷰/사용자 결정: 별도 리뷰 담당자가 실DB XML의 13건 통과·15/38행 정리 및 shared-role=false와 미충족 사항을 확인했다. 사용자는 공통 role 검증을 나중으로 지정했다. 추가 role 생성/변경·테스트 없이 종료하며 **공통 role 검증 유예로 RF-003 전체 미완료**를 유지한다. 후속 fixture 변경·검증에는 별도 승인 필요, commit/push 없음.

## 2026-11-05 — RF-003 공통 role 검증 재개 및 완료

- 사용자 승인: 격리 확인 후 테스트 전용 role 1행과 사용자·포인트 이력을 생성하고 그 행만 정리. 스키마 변경·성능 테스트는 제외했다.
- 사전 독립 안전 리뷰와 대상 확인 후 MySQL 8.0.35/RR/InnoDB에서 실DB 테스트 13건을 실행해 실패·오류·skip 없이 통과했다. 새 role의 ID를 두 사용자 DB와 실제 AuthService 결과에서 확인했고 A flush·미커밋 중 B가 정상 커밋하는 것을 검증했다.
- worker 종료 후 테스트 사용자 15행·이력 38행·role 1행만 정리했다. 별도 새 연결에서 user/point/role 및 실행 marker 잔여 0과 기존 role 목록 불변을 확인했다. 증빙은 [RF-003 기록](evidence/RF-003/README.md)에 있다.
- 독립 리뷰: GPT-6 Astra / High가 실제 XML·전후 확인 자료 및 diff를 검토했으며 추가 수정이 필요한 문제는 발견하지 못했다. 공통 role 조건이 충족돼 RF-003의 합의된 기능 검증을 완료했다. 운영 보안 필터·Boot 오류 body·브라우저 E2E·운영 DB/다른 writer·성능은 미검증이다. 멱등성과 결제·거래 상태의 원자성은 별도 작업이다. commit/push는 하지 않았다.

## 2026-11-05 — 검증 한계·추가 검증 기록의 소급 보완

- 범위: AGENTS·운영 README·작업 템플릿, RF-001/002/003 명세·증빙 README 및 BACKLOG·이 로그만 수정했다. RF-001 증빙 README를 신설하고 기존 완료 범위와 미검증을 구분했다. 완료한 작업도 누락 발견 시 명세·증빙에 범위·한계·미실행 사유·후속 방법/재개 조건을 기록하도록 지속 지침을 추가했다.
- 증빙 보존 한계: RF-001 최초 8건/컴파일 원시 자료는 별도 보존되지 않았으며 RF-002 before XML은 후일 재실행이다. RF-003 frontend10건·ESLint·tsc·assemble은 성공 서술과 명령은 있으나 각 원시 실행 로그는 별도 보존되지 않았다. 기존 XML·JSON·실행 기록은 수정하지 않았다.
- 상태 정합성: RF-002는 이후 RF-003 작은 MySQL fixture의 단건/batch 결과 교차 확인만 추가됐다. 관리자 페이지 실DB 통합·동시 읽기 및 사용자 유예 성능 조건은 남아 전체 미완료다. RF-003 공통 role 기능 검증 완료와 운영 HTTP/브라우저/다른 writer/성능 한계를 분리했다.
- 후속 추적: [BACKLOG 잔여 목록](BACKLOG.md#완료-후-잔여-검증별도-후보)에 근거·상태·재개 조건을 연결했다. RF-003 멱등성과 결제 성공 후 거래 상태 변경 실패의 원자성은 별도 정책·기능 후보이며 담당·일정·구현 승인을 정하지 않았다.
- 검증: 기존 명세·로그·보존 자료와 수정 문서의 diff 및 상대 링크·추적 항목 대응을 확인하는 문서 검토만 수행했다. 애플리케이션/테스트/설정·스키마/의존성은 변경하지 않았고 테스트·DB 접속·성능 측정·commit/push는 하지 않았다. 최초 원시 자료 공백과 운영 검증 미완료 상태는 이 보완으로 해소되지 않는다.

## 2026-11-05 — RF-004 분석 및 정책 반영

- 상태·기준: 계획, 기능 전체 미완료. HEAD `63435d19dfd9fb0ac3488db5e9668b0f85bf0cc6`, 분석 시작 clean. 루트 지침·README/BACKLOG/TEMPLATE, 관련 RF 명세·증빙과 backend/frontend/chat-server 호출자를 읽고 [RF-004 명세](tasks/RF-004.md)·[증빙 README](evidence/RF-004/README.md)를 작성했다.
- 확인 사실: request.update는 이미 Transactional이지만 상태 검사·명시적 잠금 없이 거래를 생성한다. 직접 POST `/transaction`이 같은 DetailsService.create를 호출해 수락만 보호하면 우회 경로가 남는다. local/schema.sql에는 details.product_id UNIQUE가 있으나 현재 실DB·운영 제약은 미확인이다. 중복 저장·제약 오류·교착은 실제 재현하지 않았다.
- 사용자 결정: 최초 초안은 정책 답변 대기였고 이후 동일 수락 재요청은 구매자/가격 일치 시 무쓰기 기존200, 직접 생성 최소 보호·검증 및 frontend의 실패 시 성공 처리 방지 최소 수정·검증을 승인받았다. 충돌 코드는 에이전트 판단에 위임돼409를 선택했다. 이미 확정된 같은 상품의 거래/요청 상태 충돌을 표현하며 기존 미존재404·최초 정상200/201은 유지한다. 인증인가·스키마·추가 작업 승인은 없다.
- 계획: 상품 PK를 공통 잠금 기준으로 삼고 잠금 뒤 request/details 최신 상태를 검사한다. RR snapshot·관리 엔티티 stale·eager 연관 잠금 범위, 다른 상품 독립성, 실제 flush/rollback 및 직접 생성 경합을 검증한다. frontend API res.ok 검사·hook Promise 전파·화면 실패 처리의 최소 범위를 명시했다.
- DB 안전 조건: 현재 대상/제약/기존 행 재확인과 RF-004 전용 fixture 범위 합의 선행. 상품 연관 fixture 및 hardcode transaction_status ID1의 존재/의미·소유권을 확인하고 기존행 수정/삭제 금지. 실행 marker+PK+연관 소유권으로 worker 종료 뒤 이번 행만 FK 순서로 정리하고 새 연결에서 잔여0·기존행 불변을 확인한다. RF-003 user/point/role 승인을 확대하지 않는다.
- 수행/한계: 소스·기존 테스트·로컬 DDL 파일 읽기와 git 상태/HEAD 확인만 했다. 코드/테스트 수정·test/build·서버/브라우저·DB 접속/쓰기·cleanup·성능 테스트는 하지 않았다. 원시 테스트 XML/실행 로그가 아직 없으며 통과로 기록하지 않는다. 별도 원시 분석 명령 로그는 보존하지 않았고 근거 경로·HEAD·서술을 명세에 남겼다.
- 다음 단계: 승인된 최소 구현·오프라인 검증으로 진행 가능. 실DB는 대상·fixture 범위 확인 및 사전 안전 리뷰 후 수행하며 결과의 독립 리뷰가 필요하다. 운영/HTTP/브라우저·다른 writer·성능 한계와 범위 밖 후보는 BACKLOG R4 항목에서 추적한다. 후속 후보 자동 착수·commit/push는 없다. 롤백은 RF-004 diff만 선택 복구한다.

## 2026-11-05 — RF-004 구현·실DB 결함 확인 및 사용자 보류

- 상태: **사용자 지시 보류, 전체 미완료·현재 변경 배포 부적합**. 기준 HEAD `63435d19dfd9fb0ac3488db5e9668b0f85bf0cc6`와 초기 문서 변경을 보존했다. 자동 commit/push·배포·코드 롤백은 하지 않았다.
- 승인 확장: 기존200/201·ID/DTO/Location과 미존재/입력 실패 경계를 보존하며 같은 요청 일치 재수락 무쓰기200·충돌409·직접생성 최소보호·frontend실패처리를 구현했다. 별도 격리fixture 및 부재 transaction_status1 생성/정리는 사용자 승인·사전 안전 리뷰 후 실행했다. 첫 DB 실패 설명 후 쓰기tx 유지+짧은 별도 RC readOnly reader/추가연결 설계·검증도 승인받았다. 스키마/의존성/인가·pool설정·성능 변경 승인은 없다.
- 구현: 상품 PK scalar lock, 잠금 후 최신 scalar 검사, conditional native UPDATE와 VM timestamp bind. 초기 부재 details FOR UPDATE/sibling product_id 범위 UPDATE에서 다른상품 gap/next-key 충돌이 재현돼, own-visible+별도 committed ID union 및 details 정확PK current read/각 요청 정확PK update로 수정했다. 쓰기와 DTO매핑·rollback은 기존 write tx에 남겼다. frontend3파일은 status/숫자검사·Promise전파·실패표시/성공재조회 최소변경. chat-server는 수정하지 않았다.
- 기준선·오프라인: 변경 전 request mock5건 통과 및 원문/XML 보존. 반복 create 재호출·수락→거절덮어쓰기는 당시 결함 특성이고 실제중복DB행 증거는 아니다. 직접create/MVC 변경 전 별도실행 미확보. 초기리뷰보정38건 XML은 after-initial, reader수정41건(Policy26/Creation15)fail/error/skip0 XML은 after 보존, 전체compileTestJava 성공. frontend31건/lint경고0/tsc 통과 TAP·로그·환경 보존. 명령은 [증빙 README](evidence/RF-004/README.md)를 따른다.
- 실행 이력: opt-in 비활성18/20/27/29건은 skip이며 통과로 세지 않는다. 최초 실제시도는 Java process commandLine 조회 불가로 연결 전 guard초기화1실패·fixture0. read-only CIM fallback을 독립재리뷰한 뒤 재개했다. 최초 실제MySQL8.0.35/RR/InnoDB20건은17통과/3실패로 다른상품 request PK lock1205, details insert1205/1213을 확인했다. reader수정29건은27통과/2실패, gap독립성3개·별도reader실제connection/RC/readOnly·상위tx own rows/재호출/rollback은 통과했다.
- **남은 실제 결함**: 승인된 service proxy를 상위tx에서 사용하여 미리관리된 Product/Request를 수락 후 unrelated field수정/flush하면 전체컬럼 dirty UPDATE가 native 변경을 덮어쓴다. 별도 새읽기에서 거래가 생성돼도 상품forSale=true·선택요청isAccepted=null이 남음을 재현했다. sibling은 selected assertion 실패에서 중단돼 실제값 미증빙이며 운영HTTP재현/발생빈도도 미검증. 이는 범위밖후보가 아니라 완료차단·배포보류 사유다.
- 정리·증빙: 각실행 worker종료→marker+생성PK+연관소유권 기반 자식부터 정확행 삭제commit→새CLI 관련count0을 확인했다. status1은 시작부재라 테스트소유1행만 생성/정리, 기존행재사용분기 미실행. 원본은 `local/rf004-results/`에 보존, 이경로는 초기에는 untracked였고 이번 `.gitignore`로 제외했다. 정제 공유본XML/log/전후JSON·29실행당시운영5/테스트2소스와SHA256은 evidence/mysql에 보존했다. hostname/account/개인경로를 정제했고 datadir/password원문은 공유하지 않는다.
- 실행후차이:29건 뒤 testclass assertAccepted의 soft assertion·정확2행검사 보강은 **미컴파일·미실행**. 기존29XML은 당시보존소스에만 해당한다. 초기 Mockito3실패·frontendbaseline/중간실패 원시로그 일부는 미보존이며 후일성공을 최초증빙으로 소급하지 않는다.
- 검토대안/제약: 실제변경1행만 무락refresh하는 방식은 affected0/반복무쓰기·오래된RR 상태까지 해결하지 못한다. 단순setter/clear/detach/잠금refresh는 불필요쓰기·caller변경유실·eager/gap위험, 전역DynamicUpdate는 모든writerSQL영향/관리getterstale가 남는다. HibernateEntityEntry.loadedState와보호필드동시정렬 helper는 내부SPI/버전·auto-flush·유지보수부담이 있다. 사용자는 내부API추가설계/구현/검증을 하지말고 현재결과기록후보류하도록 명시했다. 어떤대안도 구현/검증하지 않았다. 추가reader의poolstarvation도 미검증·pool설정변경없음이다.
- 중단·재개조건: 보류지시 이후 운영/테스트수정·재실행·DB접속/쓰기를 중단하고 문서/증빙보존만 마무리한다. 재개를 명시지시받고 설계를 합의한 뒤 affected1/0·반복무쓰기·관리객체/outerrollback·gap/reader·cleanup을 재검증하고 독립리뷰해야 완료판정가능하다. HTTP/브라우저·외부writer·성능/운영pool은 미검증으로 [BACKLOG](BACKLOG.md)의 R4-기능/연결/연동/운영/성능에서 추적한다. 추가후보 자동착수없음.

## 2026-11-05 — RF-004 공개 Session API 재개 검토·회귀 확대

- 기준/승인: HEAD `aa25aaedf783870be1641a90f6c7db7ad08bd021`, 시작 clean. 사용자는 내부 SPI 직접 사용 없이 Hibernate6.4 공개 Session API 기반 동기화 접근과 격리 DB 회귀를 승인했고 안전성이 불확실하면 구현 중단하도록 했다. 기존 전용 fixture·소유행 정리 승인 범위에서만 실행했다.
- 구현 차단: 공개 Session.setReadOnly 문서와 기존6.4.4.Final sources.jar를 확인했다. true에서 snapshot 제거, false에서 현재 전체값 deepCopy로 pending 비보호 값도 새 기준선에 포함될 수 있다. 선행 flush/단순 setter 역시 보호필드 stale 쓰기·무쓰기 요구를 함께 입증하지 못했다. 안전한 설계 미입증으로 운영 추가 구현 없이 회귀만 남겼다. 공개 API의 다른 설계 가능성 자체를 부정한 것은 아니다.
- 변경/오프라인: MySQL 기존29건+신규8건(비보호 pending/후속dirty, affected0·무쓰기·read-only·proxy·예외·stale RR auto-flush). 테스트 관찰용 native affected count/Hibernate 공개 ORM update count만 추가했다. 독립 리뷰로 exact pending/후속 DB 필드 assertion을 보강한 뒤 compileTestJava와 offline41건 통과/opt-in-disabled37skip을 재확인했다. 성능 측정은 없다.
- 안전/실행: 독립 테스트·runner 리뷰를 거쳤다. 첫 read-only preflight는 CLI batch JSON path escape로 fail-closed(쓰기0), --raw와 첫 --no-defaults 보강 후 동일PID/loopback/datadir/executable·8.0.35/RR·12테이블0 확인. fixture InnoDB/unique/FK 가드 통과 뒤 실37건29통과/8실패/error0/skip0, Gradle exit1이다.
- 결과 한계: 기존2실패 재현, 최신 soft assertion으로 selected/sibling 둘다 null 복원 확인. 신규6실패는 Product affected0 getter true, 이미거절 Request getter null, read-only3필드 stale, RR반복3필드 stale, pending-first 필드 보존 성공 뒤 sale=true, stale+pending 반복409/rollback이다. Product affected0는 상품 UPDATE affected=0·ORM update=0 assertion을 통과했으나 요청 UPDATE·거래 INSERT는 발생했다. 이미거절/반복은 native writer0·ORM update0 assertion이 getter 실패 전에 통과했다. pending 변경 유실 및 커밋된 stale overwrite는 이번 결과로 증명하지 않는다. SQL 순서 직접 trace·실패 뒤 assertion은 미실행이다. 신규 proxy/예외 flag복구2건은 통과했다.
- 정리/보존: worker종료→정확 소유행 details33/request80/thumbnail38/product40/user117/category1/status1/transaction_status1 삭제commit→새CLI 12테이블0 확인. status1 기존행 재사용은 이번에도 미실행. raw XML/로그·전후JSON은 gitignored local/rf004-results/public-session-review, 공유본은 [새 증빙](evidence/RF-004/public-session-review/README.md)의 XML/로그/summary/소스·해시와 비식별 JSON. 보강 전/후 offline XML을 구분하고 원시 offline 콘솔은 tool output만 보존한다.
- 현재 판정/후속: 전체 미완료·배포 부적합. [명세](tasks/RF-004.md)·[증빙](evidence/RF-004/README.md)·[BACKLOG R4](BACKLOG.md)에 재개·설계차단·미도달 assertion/기존 운영 한계를 맞췄다. 안전한 설계 합의·독립 리뷰 후 동일 회귀와 cleanup을 다시 검증해야 한다. 운영/추가 테스트는 여기서 중단하고 추가 작업은 사용자 확인. 스키마/의존성/인가/pool/성능 변경·commit/push는 없다.
## 2026-11-05 — RF-004 서비스 경계 대안 검토·현재 결과 보류

- 사용자는 서비스 경계 변경의 검토를 선택했고 읽기 전용 분석 결과만 기록했다. main request.update는 TransactionController 한 곳, details.create는 직접 controller 및 수락 service가 호출한다. 외부 소비자와 실제 OSIV/사전 bound EntityManager는 미확인이다.
- active transaction 거절+transaction worker 내 수락/내부 create 원자성 보존이 추천 검토안이지만, 기존 상위 tx 계약 변경 합의 및 사전 bound EM/OSIV 문제 해결이 필수다. 자체 EntityManager 소유 대안은 변경량·lifecycle 위험이 늘어난다. 단순 REQUIRES_NEW는 outer rollback 원자성·자기 잠금·stale overwrite·추가 연결 수 위험으로 비권고다. 검토일 뿐 대안 선택·구현 승인이 아니다.
- 사용자는 최상위 작업 제한을 포함한 추가 상세 설계·검증을 현재 결과로 보류하도록 결정했다. 이후 문서만 갱신했으며 운영/테스트 변경·DB/테스트 실행은 없다. 최신37건29통과/8실패와 cleanup 증빙을 유지한다. 전체 미완료·배포 부적합이며 운영 pool/실DB·OSIV 검증 한계도 남는다.
- 재개는 사용자 명시 지시·경계/호출자 계약·원자성·사전 EM/OSIV 처리 결정과 독립 리뷰 후 가능하다. 현재 결과는 해결 설계나 완료 증빙이 아니다. 명세·증빙 README·BACKLOG R4-경계에 근거·한계·보류 조건을 맞췄다. 호출관계 검색 원시 로그는 별도 파일 없이 경로/메서드 서술로 남겼다. commit/push하지 않았다.
## 2026-11-05 — RF-005 인증 필터 정적 분석·계획

- 상태: **정적 분석·계획 및 세 정책 승인 완료, 구현·검증 미착수**. HEAD `aa25aaedf783870be1641a90f6c7db7ad08bd021`, 시작부터 RF-004 테스트·문서·증빙 미커밋 변경 존재. 해당 변경/보류 이력을 보존하고 [RF-005 명세](tasks/RF-005.md)·[증빙 README](evidence/RF-005/README.md) 신설과 BACKLOG RF-005/R5 부분만 작성했다.
- 확인 사실: Authorization null 역참조·다른 cookie만 있을 때 Optional.get, 만료 외 파싱 예외 전파, OR 검증으로 type 필수성이 없는 경로 및 skip/인가 matcher 차이. 정상 서명 교차 타입 수용은 정적 경로이며 HTTP 재현은 없다. 임의 위조 인증 우회·실제500/401/403을 확정하지 않는다.
- 호출/계획: 로그인·계좌확인 발급 및 refresh 재발급 헤더, frontend get/setAccessToken·HTTP/STOMP 경계를 조사했다. 기존 controller의 addFilters=false/standalone 결과는 인증 증빙이 아니다. 기존 의존성의 실제 verifier/provider·테스트 키·mock servlet 및 최소 SecurityConfig context를 단계적으로 사용하고 업무 service를 대역으로 격리하는 계획이다.
- 정책 제안: 누락/빈/정상서명 만료 access만 refresh fallback, 나머지 malformed/type 등 거부; Bearer access/raw refresh와 type/필수 claim 검증; 인증 거부 빈본문401 및 기존 인가403 유지 검증. 최초 권고안 작성 후 사용자가 세 항목을 각각 **승인**했다. 승인 자체는 구현·검증 결과가 아니다. 공개 matcher·쿠키·폐기/역할 최신화 정책은 자동 포함하지 않는다.
- 수행/한계: 파일 읽기·검색·git 상태/HEAD 확인·계획 문서 작성만 수행. 코드/설정/테스트 변경, 테스트/build·실서비스·DB·브라우저·성능 실행 없음. 원시 분석 출력은 대화 tool 결과뿐이며 별도 로그/XML은 없다. 후일 테스트는 새 실행으로 보존한다. BACKLOG R5-정책/격리/연동/범위에 재개 조건과 미검증을 기록했다. commit/push하지 않았다.

## 2026-11-05 — RF-005 승인 정책 최소 구현·격리 검증

- 기준·보존: HEAD `aa25aaedf783870be1641a90f6c7db7ad08bd021`. 기존 RF-004 테스트/명세/증빙은 수정하지 않았고 이 로그·BACKLOG는 RF-005 부분만 갱신했다. 시작 byte snapshot이 없어 RF-004 전후 byte 대조는 미확인이다. RF-004 보류/배포 부적합 상태는 유지한다. commit/push 없음.
- 구현: TokenFilter/JwtTokenVerifier2개와 신규 테스트2개만 수정/추가했다. 누락/빈/서명·type·claim 유효 만료 access만 refresh fallback, Bearer access/raw refresh·signature/type/subject/role/exp 검증, 인증 거부 빈본문401을 반영했다. 정상 우선순위·권한/principal·첫 cookie·재발급 헤더/chain1 유지, downstream chain은 JWT catch 밖이다. 기존 verifier API·SecurityConfig/matcher/인가표·발급·로그인/로그아웃·프런트/채팅·의존성/스키마 무변경. 성능 개선 주장은 없다.
- 기준선·실패 이력: 실제 JWT/mock servlet 직접8건·실제 SecurityConfig 최소 체인32건 통과. 누락NPE·cookie Optional 예외·raw/교차 type 수용·exp부재 예외를 관측했다. 최초 chain harness 만료property 누락 초기화1실패 후 보완했다. 최초after116건113통과/3실패: exp배열의 JJWT IllegalStateException2개는 raw 파서 내부에서 JwtException변환, OPTIONS 기본MVC handler 호출수 기대값1개는0으로 보정했다. 실패XML·로그를 보존하며 최종결과로 대체하지 않는다.
- 최종 명령/결과: NGNG-backend에서 `.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest'` **118건통과(72+46), fail/error/skip0, exit0**. 전체 compileTestJava 포함5tasks 실제실행. `.\gradlew.bat --offline --no-daemon assemble` exit0,3executed/2UP-TO-DATE. 선택suite/패키징이며 전체test/build/서비스기동이 아니다. git diff --check 통과.
- 격리/검증범위: Boot/application scan·실서비스설정/키·repository/외부client 없음. 합성키·명시bean·대역AuthenticationManager/probe endpoint 사용. 정상/실패/필수claim/만료·서명 및 실제 역할표21건·skip9건·인가403·재발급후403헤더·CORS/OPTIONS·연속요청컨텍스트·downstream예외 전파 확인. before/after·초기실패 XML/console 원본은 gitignored 로컬에 보존하고 비식별 공유 사본·소스snapshot·SHA256·환경은 [증빙](evidence/RF-005/README.md)에 보존했다.
- 판정/한계: GPT-6 Astra / Medium 독립 리뷰에서 코드와 당시 원본 자료 22개 해시·링크를 검토해 확정 결함이 없었고 합의 범위 완료로 판정했다. 운영Boot/컨테이너HTTP·async/error dispatch·브라우저/cookie/프록시·실제업무/DB·정확 exp==현재·특이alg/nbf/iat·전체suite·성능은 미검증이다. [명세](tasks/RF-005.md)/증빙/백로그R5에 범위·근거·후속조건을 맞췄다. 추가검증·별도정책·RF004재개는 자동착수하지 않는다.

## 2026-11-05 — RF-006 Long 소유자 비교 분석·계획

- 상태: 정적 분석·계획 완료, 구현·실행 검증 대기. 기준 HEAD `2ef37de4ddb7a40e7b0b2e9c662c61d08b5b939a`, 분석 시작 clean. [명세](tasks/RF-006.md)·[증빙](evidence/RF-006/README.md) 신설 및 BACKLOG RF-006/R6만 변경했다.
- 근거/결정: User ID 두 Long의 !=는 참조 비교다. Oracle JLS17/Long/Objects 공식 자료를 직접 확인하고, 값 동등 인스턴스를 상호교환하도록 권고하는 근거와 사용자 위임에 따라 두 비교만 Objects.equals로 수정하도록 계획했다. 역할·판매자 주체·401/DTO/트랜잭션·null/null 특성은 유지한다. 공식 자료를 새로운 업무 인가 정책의 승인 근거로 확대하지 않는다.
- 호출/검증 설계: PUT controller→details service→AuthService email 재조회→상품 소유자/consumer 비교→상태 조회·setter→DTO. TokenFilter 비skip이므로 유효 인증이 필요하지만 서비스 role 제한/ADMIN 우회는 없다. 실제 AuthService와 대역 repository, 합성 JWT/명시 bean 체인에 역할3×소유권3조합 및 누락/실패·refresh를 계획했다. 별도 Long 객체를 명시 확인하고 작은 ID 캐시·동일 인스턴스만으로 성공하는 테스트를 피한다.
- 수행/한계: 읽기/검색·git 상태와 HEAD·공식 문서 확인만 수행. 코드/테스트 변경·실행·DB 연결·브라우저·성능 측정 없음. 원시 읽기 출력은 대화 tool 결과뿐, XML·실행 로그 없음. 후일 실행은 새 자료로 보존한다. R6-격리/연동/정책에 미실행 사유·재개 조건 기록. RF-004 보류 유지, 다른 RF 착수 없음. 분석 담당자는 commit/merge/push를 하지 않았다.

## 이후 작업 기록 양식

## RF-NNN — 제목
- 날짜, 상태, 대상 저장소 및 기준 커밋:
- 문제와 근거, 변경 범위, 선택한 방법 및 이유:
- 유지한 외부 계약과 데이터 정합성, 관련 위험:
- 테스트·빌드 명령과 실제 결과, 미실행 항목과 이유:
- 명세·증빙의 검증 범위/한계, 원시 자료 보존 여부와 추가 검증·재개 조건, BACKLOG 추적 링크:
- 성능 변경 시 동일 조건의 측정 방법, 전후 결과 및 변동성:
- diff와 독립 리뷰 결과, 조치 및 남은 문제:
- 롤백 참고 사항:

## 2026-11-05 — RF-006 최소 구현·선택 검증 (독립 리뷰 완료)

- 기준 HEAD 2ef37de4ddb7a40e7b0b2e9c662c61d08b5b939a. 분석자가 작성한 RF006 문서 인계 후 같은 작업만 갱신했다. 운영 변경은 TransactionDetailsService Objects import와 비교2줄, 신규 fixture/서비스·standalone MVC/실제 security chain 테스트3파일이다. 역할/상품user·consumer 기준/ADMIN 비우회/&& 단락평가/null 특성/상태 조회·setter/DTO/트랜잭션을 유지한다. 새 의존성·스키마·설정·다른 저장소 수정 없음.
- [명세](tasks/RF-006.md)·[증빙](evidence/RF-006/README.md)의 실제 명령/자료 참고. 최초 before 서비스24통과·체인 초기화1실패(property 누락) 자료 보존 후 합성 MapPropertySource 보정. 보정 before37/37로 서로 별도 Long 동일값 소유자401을 재현하고 after196/196(신규37+RF005118+거래41)통과했다. fail/error/skip0,5tasks 실제 실행으로 전체 test소스 compileTestJava도 성공. assemble exit0,3executed/2UP-TO-DATE. git diff --check 통과, 독립 리뷰 완료. 운영 빈도·성능 개선 주장은 없다.
- Windows PowerShell/Oracle JDK17.0.10/Gradle8.7 기존 offline cache. 명시 SecurityConfig/JWT/filter/controller/service/실제AuthService와 mock repositories로 role3×소유권3, 인증미도달·refresh발급/status200 및 서비스/MVC null/단락평가·DTO/400/401/무변경을 확인했다. Boot/외부연결·transaction manager·실DB 없음. 원시 XML/console은 gitignored .local-runtime/rf006-raw, 공유 비식별 XML/console·before/after snapshot·SHA256 보존. 최초 실패 당시 전체 source snapshot은 미보존이며 chain source만 보존했다. JAVA_HOME stub 중단·sandbox cache lock 거부 후 자동승인 이력은 tool 결과이며 테스트 실패에 합산하지 않는다.
- 구현·합의 선택 검증 및 독립 리뷰 완료. R6-격리/연동/정책에 반영했다. 실제 DB dirty checking/commit/rollback·OSIV/Boot HTTP·브라우저·프록시·외부연동/전체suite·성능은 미실행이며 대역/MockMvc/assemble 성공을 운영 보장으로 해석하지 않는다. 별도 지시와 안전 격리환경/fixture/외부연결 차단 후 추가 검증, 정책 변경은 별도 결정. RF004 보류·실DB 실패 상태를 해소/재개하지 않았고 다른 RF 자동 착수 없음. rollback은 해당 비교/import·신규 테스트/문서 변경만 검토하며 전체 reset 금지. 구현 담당자 commit/merge/push 없음.

- RF-006 최종 독립 리뷰: 부모 담당자가 운영 코드·테스트 및 before37/after196 XML·초기 harness 실패와 보정을 확인해 수정 필요 결함이 없다고 보고했다. null/역할/단락평가 유지 및 실제 DB/Boot HTTP·브라우저·성능 미검증 구분 확인. 합의된 최소 변경·선택 검증·독립 리뷰 범위 완료이며 R6 잔여 항목은 유지한다.

## 2026-11-06 — RF-004 보류 작업 재검토

- 기준: `main` HEAD `73e89b9afe1bee7112b45fb48ceba3bc179e9d0f`, 시작 작업 트리 clean. 분석·독립 리뷰는 GPT-6 Astra / Medium이 읽기 전용으로 수행했다.
- 확인: RF-004 미완료 구현은 PR #5로 main에 이미 포함됐다. 이전 37건 중 8건 실패는 당시 실행 결과이며 현재 HEAD 재실행이나 실제 배포·운영 HTTP 발생의 증거는 아니다. 이후 RF-006의 소유자 ID 비교 외 RF-004 수락·생성 경로 변경은 발견하지 못했다.
- 대안/판정: 상위 트랜잭션 참여 제한과 작업 전용 영속성 컨텍스트는 우선 조사 후보이나 기존 합동 rollback 계약 및 OSIV/사전 EntityManager 처리 결정을 필요로 한다. 공개 read-only 토글·단순 refresh로 caller pending과 무쓰기 의미가 보존된다고 입증하지 못했다. 별도 reader의 추가 연결과 운영 pool 위험도 남는다. [명세](tasks/RF-004.md)·[증빙](evidence/RF-004/README.md)에 공식 근거와 재개 조건을 기록했다.
- 한계: 이번 재검토는 코드·보존 증빙·공식 문서의 읽기만 수행했다. 운영 코드·설정 변경, DB 접속, 테스트·성능 측정과 배포 확인은 하지 않았다. RF-004는 전체 미완료·배포 부적합이며 다른 RF는 자동 시작하지 않는다.

## 2026-11-06 — RF-004 재설계 분석 재개

- HEAD `73e89b9afe1bee7112b45fb48ceba3bc179e9d0f`, 직전4문서 미커밋 기록 보존. 최근 재설계·구현·테스트 요청에 따라 대안·공식 근거·검증 계획을 명세/증빙/R4에 추가했다.
- 기존 상위tx 참여·own rows·rollback·pending/후속수정 및 무쓰기 유지가 우선이다. 공식 refresh/readonly 문서와 EntityEntry SPI 문서는 각각의 동작을 설명하지만 임의 pending/오래된 RR 상태에서 안전한 동기화를 입증하거나 SPI 사용을 권장하지 않는다. fresh EM/최상위 작업은 계약 축소·OSIV 사전 holder 처리가 필요하다. 아직 대안을 선택하거나 구현하지 않았다.
- 분석 담당 수행은 소스/기존 증빙/공식 문서 읽기뿐이다. 소스·테스트 변경, 실행·DB 접속·성능 측정·commit/merge/push 없음. 원시 읽기 결과는 tool 대화에만 있으며 새 XML/실행 로그 없음. 상세 선택 뒤 독립 리뷰와 기존41/37 회귀·SQL 순서/affected0/pending/직접생성/예외/OSIV/gap/reader/정리를 실제 검증해야 한다. 전체 미완료·배포 부적합이며 과거8실패를 통과로 바꾸지 않는다.

## 2026-11-06 — RF-004 공개 Interceptor·flush 제어 대안 분석

- 사용자 후속 지시인 대안 적절성 확인 후 구현 조건에 따라 공개 Interceptor/flush 제어·DynamicUpdate/refresh 복원 후보를 검토했다. 공식 문서와 로컬6.4.4.Final 소스에서 기존 Session의 final interceptor, 생성 시 적용, factory/명시/session-scoped 선택 우선순위와 미합성, dirty 판정 뒤 onFlushDirty 호출을 확인했다.
- 안전한 최소안 미확정: 타입별 dirty 의미·pending/무쓰기, 전역 등록과 기존 interceptor 보존, OSIV/후속tx·reader 분리 lifecycle 검증이 필요하다. 명세·증빙·R4에 구체 제약과 추가 회귀를 기록했다. 내부 SPI/상위tx 계약 축소가 필수라고 단정하지 않았고 자동 추진하지 않았다.
- 소스/공식 문서 읽기와 기록만 수행. 코드·테스트 수정/실행·DB접속·Git 병합 없음. 새 XML/SQL trace/DB 로그 없이 원시 조회는 대화 tool 결과에만 있다. 기존 변경/실패 증빙 보존, 전체 미완료·배포 부적합 상태 유지. 후속 설계 적절성 판단과 실제 회귀·cleanup이 필요하다.

## 2026-11-06 — RF004 기능 중심 재설계

- 최신 사용자 지시로 기존 RF004 구조/과도한 상위tx·관리객체 지원 기대를 새 요구에서 제외했다. 기존4개 문서 미커밋 변경·과거 실패를 보존하고 명세·증빙·R4를 갱신했다. 기준 HEAD `73e89b9afe1bee7112b45fb48ceba3bc179e9d0f`.
- 추천: update/create 단일 READ_COMMITTED tx, request→product scalar ID 확인 및 상품 PK scalar lock 뒤 엔티티 조회, JPA setters/save로 상태 변경. 별도 reader/native bulk 상태 갱신/수동 OSIV holder 처리·SPI 제거 방향. TokenFilter는 claims 기반 새 User/Role만 구성하고 controller는 service 직행이므로 OSIV 전역 변경 없이 현 HTTP 경로를 지원한다. 임의 outer RR/사전 target managed 객체는 비지원 한계로 명시했다.
- 공식 Spring 트랜잭션/JpaTransactionManager 및 MySQL RC/locking 문서를 근거로 정책·잠금/롤백/DB FK·독립성·연결 위험과 새 검증 기준을 명세에 기록했다. 메커니즘 문서가 구현 안전성 증명을 대신하지 않는다.
- 이번 수행은 읽기/기록뿐이며 운영 코드·테스트 변경/실행·DB접속·commit/merge/push 없음. 원시 조회는 대화 tool 출력이고 새 XML/SQL 로그 없음. 과거37건8실패 유지, 새 구현/선택 검증·독립 리뷰·소유 fixture 정리 전 전체 미완료·배포 부적합.


## 2026-11-06 — RF004 기능 중심 최소 구현·선택 검증

최신 상태(2026-11-06): 합의한 기능 중심 최소 구현·선택 검증·최종 독립 리뷰를 완료했다. 최신 기능 범위에서 수정이 필요한 결함은 발견되지 않았다. 변경 전 오프라인41건, 최종 오프라인43건(Policy28/Creation15), 격리 MySQL 기능24건 통과. 과거37건29통과/8실패는 이전 범용 내부 호출 설계의 실행 이력이며 보존한다. 임의 사전 관리 객체/active outer RR 트랜잭션은 최신 지원 범위 밖이다. 운영 배포·전체 HTTP/브라우저·성능 검증 완료를 뜻하지 않는다.

- 범위: READ_COMMITTED/service tx·상품PK scalar lock·관리entity setter/save, nativebulk/별도reader 제거. HTTP/승인 반복·충돌·직접생성 정책 유지. 기존 nullable 거래buyer/seller는409 불일치 검사에 포함. 인증인가/스키마/의존성/전역OSIV/pool 변경없음.
- 근거: [기능 증빙](evidence/RF-004/functional-implementation/README.md)에 before41·초기after41·최종43 XML, opt-in24skip·실DB24pass XML/로그·pre/postflight·소스해시 보존. 과거37 메서드 유지·삭제/skip없고 제거API 참조1개만 컴파일호환 변경; 임의outer/stale관리객체 기대를 새 범위 통과로 기록하지 않음.
- 안전/정리: 독립 fixture/runner 리뷰와 PID/datadir/account/schema/FK/UNIQUE/trigger/event/외부schema inboundFK/빈테이블 확인 후 실행. 정확 소유행만 worker종료뒤 삭제·commit, 새CLI 관련/공통테이블0. existingstatus1 없어 소유행생성/정리, 기존행재사용은 미실행.
- 한계/재개: 정상 기능은 무강제flush, fault/barrier는 명시flush. ordered latch는 직접DB대기관측이 아니고 connectionID는락시점샘플. 실제ServletOSIV/HTTP/브라우저/전체보안·운영writer/새요청삽입·재판매/취소·운영pool/성능은 미검증·별도범위. 새호출자/outer/stale상태 도입 전 경계검토, BACKLOG잔여추적. 최종독립리뷰완료·범위내수정필요결함없음이며 commit/push·배포·자동다음작업없음.


2026-11-06 최종 독립 리뷰: nullable 거래 buyer/seller null 가드를 포함한 운영 변경, 최종 오프라인43/격리MySQL24 XML, 테스트 실행 소스 해시 일치와 정확 소유행 cleanup·독립CLI 잔여0을 확인했고 최신 기능 범위에서 수정이 필요한 결함을 발견하지 않았다. 합의한 최소 기능 범위의 구현·선택 검증·독립 리뷰를 완료한다. nullable 오염 데이터의409는 단위 테스트2건만 검증했으며 실제 MySQL 오염 fixture는 실행하지 않았다. 실제 HTTP/Servlet OSIV·브라우저/전체 보안·운영 writer/신규요청·운영 pool/성능, connection ID 잠금시점 샘플·latch/주입flush 한계 및 임의 outer/stale 관리객체 비지원은 그대로 유지한다. 이 잔여 검증은 BACKLOG의 재개 조건을 따르며 자동 착수하지 않는다.

## 2026-11-06 — RF007 상품 수정·삭제 객체 인가 최소 구현·선택 검증

HEAD `56d3ec21a72918dd5e6c42c018d12911f13a3345`에서 기존 RF007 문서 변경을 보존했다. ProductService의 상품 조회 직후 작은 공통 guard로 인증/principal·익명 토큰을 확인하고 USER non-null 소유자 ID 값 비교·ADMIN 기존 타인 관리 허용을 구현했다. 새 거부403은 태그/사업조건·상태/save/캐시보다 앞이다. 전역 security/설정·DTO·엔티티/repository·의존성·스키마는 변경하지 않았다.

프런트 네 파일에서 PUT 실패 이동 방지·알림과 DELETE200만 성공·204/401/403/500/network 실패 목록 보존·hook catch를 구현했다. 기존 DELETE204 목록 제거 부수 결함도 승인 범위대로 보정했고 모달 닫힘/관리자 UI/기존 경로는 유지했다.

- before 실제 chain26/26·프런트 특성11/11, 최종 backend284/284(RF00786/RF005118/RF00637/RF00443)·frontend45/45(RF00714/RF00431), fail/error/skip0. lint0(기존 img 경고3)·tsc0·backend assemble0. XML/console/source/hash·TAP·환경 버전은 [증빙](evidence/RF-007/README.md)에 보존했다. assemble compile/process UP-TO-DATE·bootJar/jar 실행을 전체 build/test 성공으로 확대하지 않는다.
- 초기 sandbox cache lock 실패, 테스트 static import compile 실패와 인증 fixture setter 오류를 보존·보정했다. 기존 Boot2클래스/11건을 단위로 오인 선택하여 datasource UnknownHost/context 초기화 실패가 발생했다. 연결 성공·fixture 쓰기는 없었고 두 클래스는 기존 소스/skip 그대로 유지하며 offline 선택에서 제외했다. 해당 원본은 gitignored 로컬에 보존하고 공개 증빙은 RF007 실패 XML·집계로 제한했다. 최초 실패당시 전체 source snapshot은 없음을 명세/증빙에 기록했다.
- 실제 service/filter chain·cache proxy·SWR cache 대역 검증이며 DB dirty checking/rollback·실Redis·Boot HTTP/error dispatch/Servlet OSIV·브라우저·프록시/운영 writer·경합은 미검증이다. frontend production build는 기존 SSG의 BACKEND_URL/고정 상품·사용자 fetch로 미실행이다. 안전 격리 서버/fixture·외부 차단·승인 범위 확보 후 별도 검증한다. 성능은 사용자 유예·미측정, 범위 밖 인가·DELETE OR/일반 캐시·관리자 DELETE 실패 UI는 후보로 유지한다.
- 최소 구현·선택 검증·최종 독립 리뷰를 완료했다. 별도 담당자가 backend284/frontend45·manifest·운영 변경·실패 이력/한계를 확인했고 합의 범위의 수정 필요 기능 결함은 없었다. 최초 Boot11건(Details8/Request3)·fixture48건·compile 실패를 분리 기록하고 tsc exit 근거·후속 확인 실행, 증빙 README 상대 링크0을 보완했다. BACKLOG R7-격리/연동/범위/성능에 상태·재개 조건을 반영했다. commit/push·배포·자동 후속 작업 없음.

## 2026-11-09 — RF008 포인트 응답 DTO 최소 구현·격리 검증

기준 HEAD `24dda6a7715f505e27e7cf237e1e6e0762b9c06a`, 기존 RF008 계획 문서 변경을 보존했다. OWASP API3와 사용자 위임에 따른 의도된 보안 계약 축소로 네 응답의 user 키를 제거하고 기존8개 Long/String/Timestamp 포인트 속성만 전용 DTO에 매핑했다. Controller와 신규 DTO만 운영 변경했으며 서비스 계산/저장/엔티티/repository·인증/전역 mapper·프런트 운영 코드·의존성/스키마는 변경하지 않았다.

- 변경 전169/169(신규chain23·기존146), 최종 backend204/204(chain24·기존 계약28·포인트34·보안118), frontend49/49(신규4·기존45) fail/error/skip0. backend assemble exit0. 실제 Boot Jackson mapper를 MVC converter에 명시 연결하고 mapper identity·엔티티의 user만 제거한 날짜/값/null tree·8키·순서/빈 목록/null200·DTO user 미접근을 검증했다. 실제 TokenFilter/Security/JWT/AuthService/서비스+repo 대역에서 역할3×응답4, 인증거부8·email→DB ID 전달·충전/전체잔액 결제 및 잠금→조회→저장 순서를 확인했다. 로그인 manager도 대역이며 포인트 경로에서 미사용·로그인 인증 검증 아님을 구분했다.
- 기존 의존성으로 완전한 테스트 클래스 이름7개만 offline 지정했다. DB/발송·전체 integration은 실행하지 않았다. XML/console/TAP/집계/source/hash·환경 버전 및 실제 명령은 [증빙](evidence/RF-008/README.md)에 보존했다. frontend는 실제 API와 목록 컴포넌트 소스를 가상 fetch/hook·React 서버 렌더로 검증하며 브라우저 동작 전체가 아니다.
- 최초 sandbox wrapper lock 거부(미실행), Role import 충돌 compile 실패, 숫자 JSON tree 노드 차이13실패/169, frontend default-export 대역1실패/49를 별도 보존·보정했다. 최초 assertion 실패 source는 두 줄을 역변경한 재구성본이며 당시 snapshot이 아님을 명시했다. 기존 TokenFilterTest가 합성 JWT를 XML에 출력하여 공유본3개와 compile log fixture literal을 치환했고 원문은 gitignored 로컬/치환 manifest로 구분 보존했다. 실제 사용자·자격값을 사용/공개하지 않았다.
- 최종 독립 리뷰 완료: 별도 담당자가 DTO/controller의 네 응답 경계·user 제거 외 계약 유지, 실제 mapper/체인·169/204/49 결과, 문서/증빙/해시 정합 및 실패/치환 이력·한계를 확인했고 합의 범위의 차단 결함을 발견하지 않았다. 합의한 최소 구현·격리 검증·독립 리뷰를 완료한다. 리뷰 중 지적된 'only repositories are doubles' Javadoc 설명은 로그인 manager 대역까지 정확하게 보정했다.204건 실행 후 변경은 설명2줄뿐이어서 재실행하지 않았고 실행 당시/최종 소스를 별도 hash로 구분했다. 실DB commit/rollback·락/SQL/Boot 전체 HTTP/error dispatch/OSIV·브라우저·외부 소비자 user 의존/릴리스 고지는 R8-통합에서 미검증으로 추적한다. frontend production build는 기존 SSG 외부 fetch 때문에 외부 비연결 범위에서 미실행, runtime TS 변경이 없어 lint/tsc 추가 실행도 생략했다. 다른 User 응답/typeDetail 표시는 별도 후보, 성능은 사용자 지시 유예·미측정이다. 후속 범위·격리 환경/fixture/인증/소비자 목록 확보 시 별도 증빙으로 재개하며 자동 착수/commit/push/배포하지 않는다.

## 2026-11-10 — RF009 상품 상세 캐시 commit 후 무효화

기준 HEAD `6438b969e3901d14bdd519c16de26c44d51ba68a`의 RF009 계획 문서/BACKLOG 변경을 보존하고, 작은 ProductCacheInvalidator와 ProductService/PenaltyTran/TransactionRequestService만 운영 변경했다. actual tx+sync에서는 afterCommit 해당 상품 key만 evict하고 그 외는 즉시 처리한다. RuntimeException을 상품ID/캐시명과 함께 로그 후 차단하여 기존 쓰기 응답·다른 callback 실행을 보존한다. PUT returned DTO CachePut→eviction과 기존 Redis put 오류 전파 제거는 의도된 차이다. RF004 RC/상품 lock/MANDATORY·단일 tx, RF007 인가·삭제 OR·API 응답/DTO·의존성/스키마를 유지했다.

- before 실제 proxy7+기존 cache4=11/11, 최종14클래스405/405(RF00969/RF00786/RF00443/RF00637/RF00852/공통보안118), fail/error/skip0; --offline assemble exit0. 실제 synchronization lifecycle·대역 resource로6 writer commit 전후 key/타상품·다음 read/rollback-only·commit 실패·거부/no-op/오류/중복·뒤 callback·no-tx 경계를 검증했다. direct no-tx 수락은 기존 TransactionDetailsService.findCurrentDetails의 MANDATORY 거부·무evict를 유지한다. 격리 context의 CacheInterceptor→TransactionInterceptor 관측이며 운영 Boot advisor 순서 증명은 아니다.
- 두 독립 context 공유 대역에서1/2 reader miss→옛snapshot→다른 context commit/evict→늦은 stale put을 결정적으로 재현했다. 성공 테스트는 잔여 경쟁 재현이지 강한 최신성 해결이 아니다. 역순 writer는 fake commit snapshot 공개 순서만 검증하며 JPA/실DB 격리/락을 입증하지 않는다. DB 성공/evict 실패 시 stale 잔존·프로세스 종료/Redis 복구·다중 프로세스 문제는 미해결이다.
- 초기 sandbox Gradle lock 거부·신규 test compile2오류·405중 direct no-tx 가정1실패·RF006 selector 오타 retry392를 구분 보존하고 최종405를 정확 selector로 다시 실행했다. 초기 lock/launcher 출력은 대화 tool에만 있고 중간 실패의 전체 source snapshot은 없다. before source3개/최종 source23개·XML/console/hash·환경/명령은 [증빙](evidence/RF-009/README.md)에 보존한다. 기존 MySQL2클래스는 mock invalidator 생성자 인자로 컴파일 호환만 수정했으며 실행하지 않았다.
- 기존 TokenFilterTest 출력의 합성 JWT49개×XML3개를 공유본에서 치환하고 원문은 Git 제외 .local-runtime/rf009-raw에 보존했다. manifest에 원문/공유 SHA256·건수·위치를 기록했고 실제 자격값/실사용자 데이터를 사용하지 않았다. 원문 보존/공유 치환/후일 재실행을 구분한다.
- 독립 담당자가 코드/테스트·before11/final405·assemble·실패 이력·비식별/hash·23 snapshot을 검토했고 합의 기능의 차단 결함은 없었다. 독립 문서 정합 최종 검토도 완료했고 합의 기능 한정 차단 결함은 없었다. 실DB/JPA flush/rollback·Redis serialization/key/TTL/네트워크·운영Boot/HTTP/OSIV·브라우저/프런트·성능은 미검증/범위밖/사용자 유예다. 전용 환경 사용 승인·identity/namespace·소유 fixture·외부 차단·정확 cleanup/독립 잔여 확인 후 별도 opt-in으로 재개하며 BACKLOG R9에서 추적한다. commit/push·배포·후속 자동 착수는 하지 않았다.

RF009 독립 문서 최종 리뷰 완료: 보존된 실패 XML stack의 TransactionDetailsService.findCurrentDetails → TransactionRequestService.update:66과 실제 findCurrentDetails의 @Transactional(MANDATORY)를 대조해, 초기 direct no-tx 실패 원인을 details.create라고 적었던 설명을 정정했다. create의 READ_COMMITTED 호출 전에 조회 경계에서 실패했다. 나머지 수치·범위·근거는 정합하며 합의 기능 한정 차단 결함이 없었다. 네 문서의 최신 상태를 검토 완료로 맞췄고 과거 분석/실패 이력·원시 XML/소스/해시는 유지했다. 문서만 보완했으므로 테스트를 재실행하지 않았다.

## 2026-11-10 — RF020 계좌 확인 요청자 인가

기준 HEAD `e417eaf7d476a639413414ff8f4423b89cc88e66`에서 계좌 확인 전에 인증된 미확인 사용자와 요청 ID의 값 일치를 확인한다. 본인 정상 처리와 기존 계좌 중복·역할·쿠키 경계를 유지하며 타인 요청은 대상 조회 및 계좌용 토큰 발급 전에 403으로 거부한다. 유효 access의 거부 응답에 새 Authorization은 없고, refresh fallback의 거부 응답에는 필터가 발급한 기존 caller/UNCONFIRMED_USER access만 허용하며 새 refresh와 대상 토큰은 없다.

변경 전 14건과 최종 317건 통과, assemble 성공. 최종 317건에는 별도로 실행한 170건이 포함되므로 합산하지 않는다. 실제 security chain·JWT와 repository 대역을 포함한 격리 검증이며 원시 결과·실패 이력·명령과 한계는 [증빙](evidence/RF-020/README.md)에 기록했다. 독립 최종 리뷰에서 합의 범위의 차단 결함이 없었다. 실제 DB/HTTP/브라우저·동시성·성능은 미검증이며 후속 조건과 범위 밖 후보는 BACKLOG의 R20 항목에서 추적한다. 전체 backend test/build는 실행하지 않았고 assemble은 이를 대체하지 않는다.

## 2026-11-10 — RF042 탈퇴 본인 객체 인가

인증된 주체를 이메일로 DB에서 해석해 요청 ID와 비교하고 타인·null·주체 해석 실패를 대상 조회 전에 403으로 거부한다. 기존 본인 visible 토글과 역할·refresh fallback을 유지한다. before 5/5, 최종 선택 214/214 및 assemble 성공, 독립 리뷰에서 합의 범위의 차단 결함 없음. 초기 실패와 원시 자료·공유 치환·명령은 [증빙](evidence/RF-042/README.md)에 기록했다. 실제 DB/HTTP/브라우저·동시성·성능 및 전체 backend test/build는 미검증이며 반복 탈퇴 재활성화와 JWT 폐기 부재(RF040)는 미해결이다. 후속 조건은 BACKLOG R42에서 추적한다.

## 2026-11-10 — RF041 주소 본인 객체 인가

인증 주체를 이메일로 해석해 요청 ID와 값 비교하고 타인·null·주체 해석 실패를 대상 조회 전 403으로 거부한다. 본인 주소의 기존 역할·204 응답과 별도 관리자 편집 경로, RF020 계좌 guard를 유지한다. before 10건, 최종 선택 247건 및 assemble 성공, 독립 리뷰에서 합의 범위의 차단 결함 없음. [증빙](evidence/RF-041/README.md)에 실행 명령·원시 자료·치환 및 한계를 기록했다. 실DB/HTTP/브라우저·SWR·동시성·성능과 전체 backend test/build는 미검증이며 후속 조건은 BACKLOG R41에서 추적한다.

## 2026-11-10 RF022 채팅 인증·인가 경계

승인된 최소 서버·프런트 구현과 격리 검증, 독립 리뷰를 완료했다. 서버 28건/assemble, 프런트 전용 42건과 이를 포함한 관련 묶음 91건, lint/tsc가 통과했다. 중간 STOMP ERROR timeout 1건은 원인 미확정이며 후속 통과로 소급 변경하지 않는다. 실제 DB·wire·브라우저·운영 구성·성능은 검증하지 않았다. HMAC 공유의 서명 능력 확대, 기본 origin `*`, BroadcastChannel 미지원 팝업 잔여는 [백로그](BACKLOG.md)에서 추적한다. 검증 환경·원시 증빙 보존과 재개 조건은 [명세](tasks/RF-022.md)와 [증빙](evidence/RF-022/README.md)을 따른다. 공유 프런트 두 파일의 RF021 거래 요청 처리 hunk와 해당 hook·전용 테스트는 독립 리뷰를 거쳐 선행 반영되지만 RF021 전체 완료를 의미하지 않는다. 두 전용 suite를 함께 재실행해 69건(27+42) 통과했으며 대역 기반이고 실제 브라우저/HTTP 검증은 아니다.


## 2026-11-10 RF021 거래 요청 객체 인가

인증 구매자와 요청 buyer, 실제 상품 소유자와 seller를 확인하고 수락·거절은 잠긴 상품의 현재 소유자만 허용한다. 변경 전 10건, 최종 선택 backend 232건 및 frontend 58건, lint·tsc·assemble 통과와 독립 리뷰를 완료했다. 프런트 관련 변경은 RF022 PR #16에 선행 반영됐다. 직접 생성 인가는 사용자 보류이며 제3자 선행 생성 시 정당한 수락이 409로 충돌할 위험이 남는다. 실DB/HTTP/OSIV/브라우저·기존 MySQL worker 인증 fixture·성능은 미검증이다. 원시 자료 보존과 검증 범위·재개 조건은 [명세](tasks/RF-021.md), [증빙](evidence/RF-021/README.md), [백로그](BACKLOG.md)를 따른다.
