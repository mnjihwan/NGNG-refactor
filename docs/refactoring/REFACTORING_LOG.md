# 리팩토링 작업 기록

2026-11-05 최신 상태: RF-001 완료, RF-002 최소 구현·오프라인 검증 후 전체 미완료, RF-003 합의된 기능 검증·독립 리뷰 완료. 각 날짜의 계획·미완료·유예 기록은 당시 이력이며 최신 한계와 후속 조건은 해당 명세·증빙 및 BACKLOG 잔여 목록을 따른다.

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
