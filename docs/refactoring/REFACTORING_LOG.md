# 리팩토링 작업 기록

운영 코드 리팩토링은 아직 실행하지 않았다.

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

## RF-001 — 테스트 컴파일 복구 및 사용자 목록 계약 테스트 확보

- 날짜·상태: 2026-11-04, 완료. 통합 HEAD `ab77676ae968354c47408002926119e3f3843c69`에 기존 로컬 환경 미커밋 변경이 있는 상태에서 시작했다. RF-001은 테스트 코드와 이 문서들만 변경했으며 이전 변경을 보존했다.
- 기준선: 변경 전 `compileTestJava`는 UserControllerTest의 2인자 DTO 생성자 호출 1건에서 실패했다. nickname/address 입력을 유지하고 나머지 두 인자를 null로 추가해 컴파일을 복구했다.
- 구현: UserService의 두 사용자·역할·잔액 매핑, page 0/2와 메타데이터, 빈 페이지, 음수 페이지, 포인트 이력 부재 예외 후보를 mock으로 검사했다. standalone MockMvc는 기본 page, page 2의 DTO/JSON 및 잘못된 페이지 바인딩을 검사한다. 정상 조회 시 mock 쓰기 호출도 확인했다.
- 검증: `./gradlew.bat --no-daemon compileTestJava` 성공; `./gradlew.bat --no-daemon test --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest'` 성공. XML 서비스 5건, 컨트롤러 3건, 총 8건, 실패·오류·skip 각 0. 첫 실행의 PageImpl fixture 총 건수 불일치 2건은 fixture 정정 후 통과했다.
- 독립 리뷰: 구현 담당자와 별도로 테스트와 기존 테스트 diff, XML 결과를 확인했다. DTO 필드·순서·페이지·역할과 null 결함 후보의 분리를 확인했고 추가 수정할 사항은 없었다. 사용자 목록 SQL, 실제 DB 쓰기, SecurityFilterChain 및 운영 Jackson 설정은 이 테스트로 증명되지 않는다. 기존 전체 통합 테스트/build는 외부 연결·DDL 위험 때문에 실행하지 않았다.
- 롤백: RF-001의 테스트 파일과 해당 문서 기록만 선택적으로 되돌린다. 기존 로컬 환경 변경은 보존한다. RF-002는 시작하지 않았다.


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

## 이후 작업 기록 양식

## RF-NNN — 제목
- 날짜, 상태, 대상 저장소 및 기준 커밋:
- 문제와 근거, 변경 범위, 선택한 방법 및 이유:
- 유지한 외부 계약과 데이터 정합성, 관련 위험:
- 테스트·빌드 명령과 실제 결과, 미실행 항목과 이유:
- 성능 변경 시 동일 조건의 측정 방법, 전후 결과 및 변동성:
- diff와 독립 리뷰 결과, 조치 및 남은 문제:
- 롤백 참고 사항:
