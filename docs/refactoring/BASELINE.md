# 검증 및 성능 기준선

기록일: 2026-11-04 (Asia/Seoul). **정적 집계와 빌드 시도 결과만 확보했으며 애플리케이션 성능 기준선은 없다.** 소스·테스트·설정을 수정하지 않았다.

## 1. 기준 상태

RF-001 구현 후 `NGNG-backend`에서 `./gradlew.bat --no-daemon compileTestJava` 성공. 기존 테스트 입력 생성자에 null 두 인자를 추가한 것 외 운영 코드 변경 없이 선택 테스트만 `./gradlew.bat --no-daemon test --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest'`로 실행했다. 결과 XML에서 서비스 5건·컨트롤러 3건, 총 8건 실패/오류/skip 0건을 확인했다. 기존 로컬 환경 작업의 미커밋 운영 코드 변경이 시작 전부터 있었으므로 HEAD만의 테스트 결과는 아니다. 실제 DB, 보안 필터, 전체 통합 테스트 및 build 성공을 뜻하지 않는다.

| 저장소 | HEAD | 분석 시작 작업 트리 |
|---|---|---|
| NGNG-backend | `e0c81ade16b25ec683c24320100069fd0406e682` | git status --short 출력 없음 |
| NGNG-chat-server | `2f54696baeca557f39f92ccbe08eec12a5450ecd` | 출력 없음 |
| NGNG-frontend | `a498d23b604883c42d868b14c2ce3516a6ecbcea` | 출력 없음 |

루트는 Git 저장소가 아니다. 최초 Git 조회는 소유권 차이로 거부되었으며, 각 명령에 해당 저장소 절대경로의 `-c safe.directory=...`만 지정해 읽었다. 전역 Git 설정은 변경하지 않았다. 문서는 루트 docs/refactoring에만 작성했다.

분석 종료 시 세 저장소의 `git status --short`, `git diff --stat`는 모두 출력이 없었고 `git diff --check`는 통과했다. 문서 상대 링크 검사에서도 끊어진 링크가 없었다. 운영·테스트 소스 변경 없음과 문서 구조를 확인한 것이며 애플리케이션 검증 성공을 뜻하지 않는다.

환경: Windows / PowerShell, JAVA_HOME은 `C:\Program Files\Java\jdk-17`. `java -version` 실제 출력은 Java 17.0.10, HotSpot 64-Bit Server VM이다. Wrapper 지정 버전은 Gradle 8.7이다. 캐시 디렉터리 이름이 존재하는 것을 설치 완료로 간주하지 않는다. Docker 실행 파일은 발견했지만 daemon·컨테이너·DB 가용성은 검증하지 않았다.

## 2. 실제 확인·측정한 값

| 항목 | 값 / 관찰 결과 | 방법과 해석 |
|---|---|---|
| 운영 Java 파일 | 165개 | rg --files로 src/main/java의 *.java 집계; 복잡도/커버리지 아님 |
| 테스트 Java 파일 | 9개 | 같은 방식으로 src/test/java 집계 |
| @Test 선언 | 52개 | 각 파일에서 `^\s*@Test\b` 집계. 실행 테스트 수 아님; 빈 테스트 포함 |
| SpringBootTest 파일 | 9개 | 파일별 애너테이션 검색 |
| 필터 비활성화 파일 | 7개 | addFilters = false 검색; 나머지 2개는 서비스 테스트 |
| Java 버전 명령 | 성공 | java -version → 17.0.10 |
| 컴파일 준비 시도 | 실패, exit code 1 | 아래 실행 기록. Java 컴파일 미도달 |
| 애플리케이션 테스트 실행 | 없음 | 통과 0개라는 테스트 결과가 아니라 runner 미실행 |

### 실제 실행 기록

작업 디렉터리: NGNG-backend.

```powershell
.\gradlew.bat --offline --no-daemon compileTestJava
```

관찰한 핵심 출력:

```text
Downloading https://services.gradle.org/distributions/gradle-8.7-bin.zip
Exception in thread "main" java.net.SocketException: Permission denied: no further information
... org.gradle.wrapper.Install.forceFetch(...)
... org.gradle.wrapper.GradleWrapperMain.main(...)
```

`--offline`은 Gradle의 의존성 해석 옵션이며 Wrapper 배포본 다운로드를 막지 못했다. 네트워크 권한 제약으로 실패했으므로 프로젝트 컴파일 실패의 증거가 아니다. 다운로드 권한 확대, 의존성 설치, 서비스 기동은 수행하지 않았다. 동일 Wrapper 제약을 반복할 test/build는 실행하지 않았다.

UserControllerTest:232의 2인자 생성자와 UserUpdateRequestDTO:7-16의 0/4인자 생성자 불일치는 **정적 확인한 컴파일 장애 후보**다. 컴파일러의 오류 개수·다른 오류·해결 시간은 미측정이다.

## 3. 기존 테스트

경로 기준: `NGNG-backend/src/test/java/com/ngng/api/`.

| 파일 | @Test 수 | 내용과 한계 |
|---|---:|---|
| user/UserControllerTest | 12 | CRUD/가입/확인/메일. 고정 사용자, 생성자 불일치, 실제 메일 발송 경로 |
| product/controller/ProductControllerTest | 7 | CRUD/끌올/조회. 상태 코드 중심, 업로드는 빈 본문 |
| transaction/controller/TransactionControllerTest | 8 | 거래/요청 REST. 고정 ID, 응답·정합성 검증 부족 |
| transaction/service/TransactionDetailsServiceTest | 8 | 생성·상태·조회. 데이터 개수 가정, 일부 assertion 주석 처리 |
| transaction/service/TransactionRequestServiceTest | 3 | 생성/수정/목록. 고정 데이터, 동시성·반복 수락 검증 없음 |
| point/PointControllerTest | 3 | 조회/이력/충전. 결제·경계값·동시성 검증 없음 |
| report/ReportControllerTest | 5 | 신고 CRUD. 고정 ID, 상태 코드 중심 |
| penalty/PenaltyControllerTest | 3 | 조회/생성. 고정 기준 데이터 의존 |
| search/SearchControllerTest | 3 | main/category/search. category URL이 현재 controller와 불일치 |

여러 테스트가 Transactional과 수동 transactionManager.rollback을 함께 쓴다. 이 rollback은 SMTP·S3·Redis나 DDL을 되돌린다는 보장이 아니다. 테스트 YAML은 MySQL 및 외부 연동 리터럴과 `ddl-auto: update`를 포함한다. Redis 필수 속성은 해당 YAML에서 발견하지 못했다. 분리된 환경·fixture·발송 대역 확인 전 기존 전체 통합 테스트를 실행하지 않는다.

## 4. 미측정 항목

| 항목 | 현재 상태 / 필요한 조건 |
|---|---|
| main/test 컴파일, test/build 성공 | 미확인. Wrapper와 기존 의존성 사용 가능한 환경 필요 |
| 통과/실패/skip·커버리지 | 미측정. XML/HTML 결과·커버리지 계측 없음 |
| context 시작, 실제 API 상태/JSON | 미측정. 격리된 설정·서비스 또는 test double 필요 |
| MySQL/Redis/ES 버전, 테이블 건수·인덱스·제약·OSIV | 미확인. 운영 환경에 연결하지 않음 |
| 사용자 목록 SQL 수·시간·실행계획 | 미측정. 동일 MySQL fixture와 SQL 통계 필요 |
| 상품 SQL 수·응답/캐시 크기·히트율 | 미측정. 관계 데이터와 Redis hit/miss 분리 필요 |
| p50/p95·처리량·오류율·CPU·heap/GC | 모두 미측정. 부하 시나리오·측정 환경 미구성 |
| 포인트 동시 갱신/중복 결제/동시 수락 | 미재현. 별도 연결·트랜잭션과 동시 실행 barrier 필요 |
| 캐시 변경 반영/롤백/TTL·ES 반영 지연 | 미측정. 변경 주체·동기화 경로 확인 필요 |
| 로그 조회·이미지 처리·S3 비용 | 미측정. 파일 크기·해상도별 분리 측정 필요 |
| 프런트 lint/build/Cypress, 채팅 test/build | 미실행. 이번 백엔드 분석 검증 범위 밖 |

TTL 30분, 사용자 페이지 5개, 검색 20개, 메인 60개, 로그 10개는 **설정값**이다. N명당 최신 포인트 repository 호출 N회는 **코드상 구조**다. 실제 SQL 수·캐시 유효시간·성능 개선율과 혼동하지 않는다.

## 5. 이후 실행 절차

1. Java 17, 지정 Wrapper와 기존 의존성이 준비된 환경에서 `./gradlew.bat --version`, `./gradlew.bat --no-daemon compileTestJava` 실행. 오류 원문은 비밀값 없이 보관한다. 신규 의존성은 추가하지 않는다.
2. RF-001에서 컴파일 문제를 고친 뒤 외부 연결 없는 신규 테스트만 정확한 --tests 필터로 실행한다. 필터를 써도 전체 테스트 소스의 컴파일은 필요하다. 단위 테스트 통과를 통합 테스트 통과로 보고하지 않는다.
3. 통합 검증은 별도 작업으로 전용 MySQL/Redis/ES, 기준 데이터, 더미 JWT 키, SMTP/SMS/S3 대역을 구성한다. 운영 자격 정보·DDL 갱신 설정을 그대로 쓰지 않는다. H2/Testcontainers를 자동 추가하지 않는다.
4. 격리 후 `./gradlew.bat test`, `./gradlew.bat build`를 실행한다. 인증은 필터 포함, 정합성은 commit/rollback·동시 트랜잭션을 포함한다. 결과 파일 timestamp와 기준 커밋을 기록한다.

### RF-002 성능 측정 계획 — 미실행

대상은 `/admin/users?page=0`. 동일 MySQL 버전·DDL·인덱스·JVM·CPU 조건과 fixture(visible 사용자 12명, 숨김 3명, 사용자별 이력 0/1/10건 사례)를 기록한다. 포인트 이력 부재의 기존 실패는 별도 시나리오로 격리하며 임의로 0원으로 바꾸지 않는다.

- SQL: fixture 저장 후 flush/clear·통계 reset, 요청 1회. 반환 0/1/5명 페이지에서 point-history SELECT와 전체 SELECT를 구분한다. L1 캐시·count 생략·role fetch를 기록한다.
- 지연 제안: 전후 동일 조건, 워밍업 20회, 동시성 1로 100회씩 3회 반복. p50/p95·오류율·SQL 수 기록. 이는 계획값이며 실측값이 아니다. 부하 도구는 기존 가용성 확인 후 결정한다.
- 완료 판단: JSON·페이지 메타데이터·순서 동일, 데이터 쓰기 없음, N회 최신 이력 조회 제거를 실제 SQL로 확인. 지연 개선은 반복 결과가 뒷받침할 때만 주장한다. SQL 감소에도 실행계획·지연이 악화하면 완료로 간주하지 않는다.
- 운영 대표 규모·고동시성 검증은 실제 분포 확보 후 계획한다. 소규모 fixture를 운영 처리량으로 일반화하지 않는다.

## RF-002 구현 기준과 기능 검증 — 2026-11-04

- 구현 시작 통합 HEAD: `17adfc75e1315b4ad832a5114f36953837870e9f`(RF-001 반영). 명세의 옛 HEAD `ab77676`와 구분한다. 기존 사용자 미커밋 변경을 보존했다. [변경 전 원본·Git 상태·관련 설정 해시 및 XML](evidence/RF-002/README.md)을 보존했으며 HEAD만으로 미커밋 환경을 재현할 수 없다. 설정 원문·비밀값은 복사하지 않았다. 해시는 식별 자료이므로 후속 비교에는 일치하는 기존 파일이나 안전한 보관본이 필요하다.
- 사용자 지시로 **SQL 수·실행계획·지연 등 성능 테스트 및 선행 성능 기준선을 유예**했다. 구현 전후 DB·SQL·서비스 지연을 측정하지 않았다. 아래는 오프라인 기능 테스트 결과이며 서비스 성능 수치가 아니다.
- 환경: Java 17.0.10, Gradle Wrapper 8.7, 기존 의존성 캐시. 최초 sandbox Wrapper 실행은 캐시 `.lck` 접근 거부로 실패했다. 기존 캐시 접근을 허용한 후 `--offline`으로 실행했다. 첫 성공은 UP-TO-DATE여서 기준선 실행으로 세지 않고 `--rerun-tasks`로 실제 재실행했다.
- 변경 전: `./gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest'` 성공. 서비스 5건·컨트롤러 3건, 총 8건, 실패/오류/skip 각 0. XML timestamp service `2026-11-04T08:04:29`, controller `2026-11-04T08:04:27`(XML 시간대 미표기).
- 변경 후: `./gradlew.bat --offline --no-daemon compileTestJava test --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest' --tests 'com.ngng.api.user.point.repository.PointHistoryRepositoryBatchTest'` 성공. 전체 테스트 소스 컴파일 후 선택 15건(서비스 9·컨트롤러 3·오프라인 repository 경계 3), 실패/오류/skip 각 0. XML timestamp repository `2026-11-04T08:06:34`, controller `2026-11-04T08:06:38`, service `2026-11-04T08:06:39`(시간대 미표기).
- 범위: 기존 DTO/Page assertion을 유지하고 역순 batch·5명 매핑·null 의미·빈 조회 차단·단독/혼합 이력 누락 예외·단건 호출 유지 검증을 보강했다. repository 테스트는 세 엔티티의 Hibernate JPQL 경로 해석 및 mock 위임만 검증했다. JDBC 메타데이터 접근·DDL·자동 연결을 끄고 기존 application/test YAML을 로드하지 않았다.
- 실제 DB 검증 제약: loopback 13306 listener 미조회, mysqld CIM 조회 권한 제한으로 격리 DB 대상/실행 상태를 증명하지 못했다. DB 접속·쓰기·fixture 생성은 하지 않았다. 실MySQL의 최대 ID 선택 결과·단건/배치 동등성·숨김/페이지 경계·무쓰기·동시 append·실제 오류 응답은 미검증이다. `AdminUserPageQueryIntegrationTest`와 실제 DB fixture는 미구현이다. 전체 기존 통합 테스트/build·실SecurityFilterChain·운영 ObjectMapper 검증도 하지 않았다.
- 판정: 최소 구현과 오프라인 기능 검증 완료, 실제 DB 기능·동시 읽기·성능 검증은 후속 작업. repository 위임 수를 실제 SQL 수로 해석하지 않는다. 성능 효과는 미측정이며 RF-002 전체는 미완료다.
