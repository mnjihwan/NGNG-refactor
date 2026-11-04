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


## 이후 작업 기록 양식

## RF-NNN — 제목
- 날짜, 상태, 대상 저장소 및 기준 커밋:
- 문제와 근거, 변경 범위, 선택한 방법 및 이유:
- 유지한 외부 계약과 데이터 정합성, 관련 위험:
- 테스트·빌드 명령과 실제 결과, 미실행 항목과 이유:
- 성능 변경 시 동일 조건의 측정 방법, 전후 결과 및 변동성:
- diff와 독립 리뷰 결과, 조치 및 남은 문제:
- 롤백 참고 사항:
