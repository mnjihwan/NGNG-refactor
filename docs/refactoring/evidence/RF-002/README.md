# RF-002 변경 전후 재현 기록

2026-11-04. 구현 시작 HEAD는 `17adfc75e1315b4ad832a5114f36953837870e9f`다. RF-001이 반영된 현재 HEAD를 사용했고 기존 미커밋 사용자 변경을 보존했다. **사용자 지시로 성능 테스트(SQL 수·실행계획·지연 측정)를 이번에 실행하지 않았다.** 이 기록은 후속 비교를 위한 코드·환경 식별 자료이며 성능 기준선이 아니다.

## 보존 자료

- [before-manifest.json](before-manifest.json): 운영 코드 수정 전 Git status와 관련 파일 28개의 SHA-256. 기존 tracked 변경, untracked 로컬 설정/스키마/문서, 테스트·빌드·Wrapper 설정 및 테스트/운영 application 설정의 내용을 출력하지 않고 해시만 기록했다.
- [before/](before/): 변경 전 운영 대상 3개 파일과 RF-001 테스트 2개 파일의 바이트 원본(`*.java.txt`). 이 5개 파일은 시작 시 HEAD 대비 미커밋 변경이 없었다. 후속 비교에서는 프로젝트를 별도 안전한 위치에 복사한 뒤 이 운영 원본 3개만 대체하여 전후를 분리할 수 있다. 현재 작업 트리에 전체 reset/checkout/clean을 적용하지 않는다.
- `before/*.xml`: 변경 전 실제 재실행한 RF-001 테스트 결과. 서비스 5건과 컨트롤러 3건, 실패/오류/skip 0.
- `after/*.xml`, [after-results.json](after-results.json): 변경 후 선택 테스트 15건 결과와 검증 대상 소스 해시. 서비스 9건, 컨트롤러 3건, 오프라인 repository 경계 3건, 실패/오류/skip 0.

공유하는 XML에서는 로컬 장치명을 제거했으며 테스트 건수·결과·시간은 변경하지 않았다.

설정에는 비밀값이 있을 수 있어 원문과 tracked 전체 diff는 복사하지 않았다. **해시만으로 로컬 환경을 복원할 수 없다.** 기존 설정 파일은 그대로 유지해야 하며 후속 측정 전에 manifest와 일치하는지 확인한다. 변경된 파일이 있으면 안전한 별도 보관본을 확보하거나 새로운 공통 환경을 고정하고 양쪽 구현을 다시 측정해야 한다. 다른 구성에서 얻은 측정치를 이 기준과 동일 조건으로 간주하지 않는다. 현 작업에 새 의존성·설정·DB 스키마 변경은 없다.

## 실제 실행

작업 디렉터리는 `NGNG-backend`다. Java 17.0.10, 기존 Gradle Wrapper 8.7 및 캐시된 기존 의존성을 사용했다. 모든 Gradle 실행은 `--offline`이며 외부 라이브러리를 추가하지 않았다.

1. 첫 sandbox 실행은 `%USERPROFILE%/.gradle/wrapper/.../gradle-8.7-bin.zip.lck` 쓰기 접근 거부로 실패했다. 컴파일·테스트 runner에 도달하지 않았다. 기존 캐시 접근을 허용한 실행으로 진행했다.
2. `./gradlew.bat --offline --no-daemon test --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest'`는 성공했으나 전부 UP-TO-DATE여서 신규 실행으로 세지 않았다.
3. 변경 전 실제 실행:

```powershell
./gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest'
```

`BUILD SUCCESSFUL`, 5 actionable tasks / 5 executed. XML timestamp: controller `2026-11-04T08:04:27`, service `2026-11-04T08:04:29`(XML에는 시간대 표기가 없음). 8건 통과.

4. 변경 후 실제 실행:

```powershell
./gradlew.bat --offline --no-daemon compileTestJava test --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest' --tests 'com.ngng.api.user.point.repository.PointHistoryRepositoryBatchTest'
```

`BUILD SUCCESSFUL`, compileJava/compileTestJava/test executed. XML timestamp: repository `2026-11-04T08:06:34`, controller `2026-11-04T08:06:38`, service `2026-11-04T08:06:39`(XML에는 시간대 표기가 없음). 15건 통과. Gradle 9 호환성 관련 기존 deprecated 기능 경고와 JVM class sharing 경고가 있었고 테스트 실패는 없었다. XML의 runner 시간은 서비스 지연·성능 비교 결과로 사용하지 않는다.

## 검증 범위와 후속 작업

기존 DTO/Page assertion, 기본/숫자 오류 페이지의 standalone MVC 바인딩, 사용자 ID별 매핑(역순 배치·5명·null·음수 잔액), 빈 Page/빈 ID 차단, 단독/혼합 이력 누락의 NPE 경로, 기존 단건/무인자 조회 위임을 확인했다. Hibernate 부트스트랩은 세 엔티티만 등록하고 JDBC 메타데이터 접근·DDL·자동 연결을 비활성화했다. JPQL 의미 분석과 mock 검증이며 실제 MySQL 데이터 결과 검증이 아니다. 컨트롤러 테스트는 변경하지 않았다.

`Get-NetTCPConnection -LocalPort 13306 -State Listen`에 listener가 조회되지 않았고 mysqld CIM 조회는 권한 제약이 있었다. 격리 DB의 실행 상태·대상을 증명하지 못해 DB에 접속하거나 쓰지 않았다. 기존 test YAML을 로드하지 않고 전체 SpringBootTest/build·서버 기동·외부 발송도 하지 않았다.

후속으로 안전한 격리 MySQL에서 최대 ID/시각 역전 fixture, 숨김 사용자/페이지 경계, 단건-배치 결과 동등성, 실제 JSON/Page·무쓰기·이력 부재 오류 응답 및 제어된 동시 append를 확인해야 한다. `AdminUserPageQueryIntegrationTest`와 실제 DB fixture는 미구현이다. 사용자 지시가 해제되고 측정 작업을 진행할 때 동일 환경의 변경 전 기준선과 변경 후 SQL 수·실행계획·지연을 확보한다. 실제 SQL 감소·지연 개선을 주장하지 않으며 RF-002 전체 완료로 처리하지 않는다.

## 2026-11-05 한계 및 후속 추적 보완

`before/*.xml`은 RF-002 변경 전에 RF-001 테스트를 재실행한 결과이며 RF-001 최초 실행 원본이 아니다([RF-001 보존 한계](../RF-001/README.md)). 이 디렉터리의 전후 XML은 오프라인 선택 테스트의 원시 결과다. BUILD SUCCESSFUL 등의 콘솔 출력은 위 서술로 남아 있고 별도 전체 콘솔 로그는 보존하지 않았다.

이후 [RF-003 실제 MySQL 실행](../RF-003/README.md)에서는 작은 fixture에 대한 기존 단건/batch 최신 조회 결과를 교차 확인했다. RF-002 전용 관리자 페이지 실DB 테스트·대표 데이터·동시 읽기·운영 HTTP를 검증한 자료는 아니다. 앞 절의 DB 미접속 사유는 RF-002 구현 당시 결과로 보존한다.

| 추적 | 남은 검증 / 미실행 이유 | 후속 방법 / 재개 조건 |
|---|---|---|
| R2-DB | 실제 관리자 페이지 통합·제어된 동시 append·읽기 무쓰기·HTTP JSON/오류/인가 미검증. 당시 대상 확인 불가, 전용 통합 테스트/fixture 미구현 | 격리 MySQL 대상과 fixture 생성/정리 범위 확인 후 최대 ID/시각 역전·visible·페이지 경계·이력 부재·무쓰기·동시 읽기 확인. HTTP/운영 직렬화는 테스트 인증·안전한 서버 환경 별도 확보 |
| R2-성능 | SQL 수·실행계획·p50/p95/처리량은 사용자 지시로 유예 | 후속 사용자 지시 및 R2-DB 조건 충족 후 명세의 비교 조건을 고정하고 원본/환경 해시를 확인해 전후 측정·독립 리뷰. 환경이 달라졌으면 공통 환경에서 양쪽 모두 새로 측정 |

전체 test/build의 외부 연동 격리는 R1-통합/RF-016으로 연결한다. 최신 상태는 [BACKLOG](../../BACKLOG.md#완료-후-잔여-검증별도-후보)를 따른다. RF-002는 전체 미완료이며 이번 보완에서 테스트·DB 접속·성능 측정을 실행하지 않았다.
