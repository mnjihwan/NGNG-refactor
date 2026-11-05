# RF-003 검증 증빙

2026-11-04. 기준 HEAD `237224bd46e31f3d5c789d0699e1f896503b67c8`. 분석 문서의 미커밋 변경을 보존했다. 설정·자격값을 이 디렉터리에 복사하지 않았다.

- `before/`: 구현 전 정상 충전/결제·가입 0원·check 무쓰기 특성 테스트 4건과 기존 회귀 15건의 XML, 당시 특성 테스트 원문. 합계 19, 실패/오류/skip 0.
- `after/`: 구현 후 backend XML 6개. 특성 4, 정책 27, point MVC 28, UserService 9, AdminUserController 3, batch repository 3. 합계 74, 실패/오류/skip 0.
- `frontend-payment.test.cjs`: 실제 API/컴포넌트 TypeScript를 기존 TypeScript로 CommonJS 변환해 실행한다. API의 fetch/토큰 처리와 hook/effect만 대역으로 두고 실제 결제 버튼 handler를 호출한다. 성공/취소/check false, check HTTP·네트워크·비Boolean·JSON 오류, payment HTTP·네트워크·JSON 오류 10건 통과. 브라우저/E2E/실제 HTTP 결과는 아니다.
- `mysql-opt-in-disabled/`: 명시적 opt-in 없이 실행한 신규 실DB 테스트 XML. 13건 모두 skip이며 DB 기능 통과로 세지 않는다.
- `mysql/`: 확인된 격리 MySQL의 실제 기능 테스트 XML. 13건 통과, 실패/오류/skip0이며 구 알고리즘 누락 재현·의도한 timeout1205 및 user15/point38행 정리 확인 로그를 포함한다.

## 실제 명령

backend 디렉터리에서 변경 전(19건):

```powershell
.\gradlew.bat --offline --no-daemon compileTestJava test --rerun-tasks --tests 'com.ngng.api.user.point.service.PointHistoryBehaviorTest' --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest' --tests 'com.ngng.api.user.point.repository.PointHistoryRepositoryBatchTest'
```

backend 디렉터리에서 변경 후(74건):

```powershell
.\gradlew.bat --offline --no-daemon compileTestJava test --rerun-tasks --tests 'com.ngng.api.user.point.service.*' --tests 'com.ngng.api.user.point.controller.*' --tests 'com.ngng.api.user.user.service.UserServiceTest' --tests 'com.ngng.api.user.user.controller.AdminUserControllerContractTest' --tests 'com.ngng.api.user.point.repository.PointHistoryRepositoryBatchTest'
.\gradlew.bat --offline --no-daemon assemble
```

첫 Wrapper는 sandbox에서 기존 사용자 Gradle 캐시 `.lck` 접근이 거부됐다. 동일한 선택 테스트·패키징 명령에 캐시 접근이 허용된 뒤 각각 성공했다. `assemble`은 `test/build` 전체를 실행하지 않고 bootJar/jar 패키징을 확인했다.

루트에서 프런트 검증(10건):

```powershell
node --test docs/refactoring/evidence/RF-003/frontend-payment.test.cjs
```

frontend 디렉터리에서 다음 검사가 모두 exit 0:

```powershell
npm run lint -- --file src/app/my_page/_api/api.ts --file src/app/my_page/_compoenets/modal/BuyProductDetails.tsx
node node_modules/typescript/bin/tsc --noEmit --incremental false
```

## 해석 한계와 남은 검증

위 오프라인 명령들은 DB/Redis/ES/S3/SMTP/SMS를 연결하거나 Spring Boot 전체를 기동하지 않는다. repository batch 테스트의 기존 Hibernate 검사도 JDBC 연결을 금지한 설정이다. 단위 프록시 테스트는 기록용 transaction manager를 사용해 advice 활성/commit 실패 전파/rollback 호출을 검증하며 DB에 행이 남는지는 검사하지 않는다. standalone MVC의 오류 본문은 Boot `/error`의 실제 응답과 다를 수 있다. 아래 추가 승인된 실MySQL 실행은 별도다.

오프라인 실행 당시에는 안전한 대상/fixture 권한이 확증되지 않아 DB 연결·쓰기·cleanup을 하지 않았다. 이후 사용자 승인 및 격리/정리 범위 확인 후 아래 실제 MySQL 기능 검증을 추가했다. 전체 서버 HTTP/보안 필터·Boot 오류 body, 브라우저 E2E·운영 DB/다른 writer·성능은 계속 미검증이다.

프런트 프로덕션 build는 상품 정적 생성이 실행 중 API를 조회하는 경계여서 이번에는 실행하지 않았다. 기존 전체 backend `test/build`도 외부 연결/DDL 설정을 가진 통합 테스트를 실행하므로 제외했다. 패키지/설정/스키마 변경, 신규 의존성 설치와 commit/push는 없다.

## 승인 후 실제 MySQL 기능 검증

사용자는 확인된 격리 대상에서 테스트 전용 사용자·포인트 이력만 생성하고 해당 행만 정리하는 기능 검증을 승인했다. role 생성·스키마/성능 변경은 승인하지 않았다. 실제 실행 전 listener 소유 mysqld PID22580의 전용 datadir/옵션을 기존 ignored 기록과 대조했고, DB가 `127.0.0.1:13306/ngng_local`, 계정 `ngng@127.0.0.1`, MySQL8.0.35, `REPEATABLE-READ`, user/point_history/user_role InnoDB이고 시작 데이터 각0행임을 확인했다. 기존 PK/FK/인덱스도 읽기 전용 확인했다. 개인 datadir·자격값은 이 문서에 복사하지 않는다.

`PointHistoryMySqlConcurrencyTest`는 기존 Boot/YAML 없이 최소 JPA/repository/실제 AuthService 및 서비스 Spring 프록시를 쓴다. 테스트 내부도 대상·datadir/RR/계정/port·엔진과 user/point 빈 상태를 재확인하고 `hibernate.hbm2ddl.auto=validate`를 사용한다. Redis/ES/S3/SMTP/SMS, SQL initializer/DDL은 없다. session 한정 `innodb_lock_wait_timeout=5`는 의도한 실패의 한계 시간이며 성능 측정이 아니다.

실제 명령(backend 디렉터리):

```powershell
# 사전 확인한 로컬 credential과 datadir를 RF003_DB_PASSWORD / RF003_EXPECTED_DATADIR에
# 현재 프로세스 한정으로 전달하고, 명령 종료 후 기존 환경값으로 복구했다.
$env:RF003_MYSQL_TEST = 'approved-local-fixture-only'
.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.user.point.integration.PointHistoryMySqlConcurrencyTest'
```

실행 marker `rf003_98454d80100640b89f6bd722767df4c1`. **13건, 실패/오류/skip0**. 실제 native SQL 실행·동시 충전/결제/혼합·부족 경쟁·RR 선행 이메일 read 및 기존 관리 이력 뒤 최신 잔액·다른 사용자 진행·실제 flush 후 rollback/rollback-only·다음 요청 잠금 획득·의도한 timeout1205·금액 경계/재전송·기존 단건/batch 결과를 검증했다. 구 알고리즘은 두 read를 완료한 후 save하도록 조율해 before1000 두 건 뒤 최신1200(기대1300)을 재현했다.

worker 종료 후 실행별 marker·user ID/email/name을 확인하고 해당 point의 point_id/user_id/type_detail로 한 행씩, 이후 해당 user_id/email/name만 삭제했다. 기존 role/전체 테이블/볼륨을 삭제하지 않았다. XML afterAll 로그의 정리는 user15/point38행이며 role 행 불변·user0/point0을 확인했다. 별도 새 mysql CLI 읽기 전용 연결도 다음을 재확인했다:

| 대상 | 실행 후 행 수 |
|---|---:|
| user | 0 |
| point_history | 0 |
| user_role | 0 |
| rf003 marker의 잔존 사용자 | 0 |

주의: 기존 role0행이고 role 생성은 승인 밖이라 **같은 role을 가진 두 사용자**는 검증하지 않았다. 다른 사용자 진행은 role=null fixture 결과다. RR 테스트의 proxy beforeLock hook 및 Future 미완료는 DB 내부 잠금 대기를 직접 관측한 자료가 아니며, 선행 snapshot 후 A의 commit을 반영한 B의 beforeCost/최종 체인으로만 해석한다. 실제 보안 필터/Boot 오류 body·브라우저·운영 writer 보장과 성능도 미검증이다.

별도 독립 리뷰가 실DB XML의 13건 통과와 user15/point38행 정리, shared-role=false의 미충족을 확인했다. 사용자는 공통 role 검증을 나중으로 유예했다. 추가 role 생성/변경·DB 테스트 없이 이번 실행을 종료하며, 이 조건이 남아 **RF-003 전체 미완료**다. 후속 role fixture 및 검증은 별도 사용자 승인을 받아야 한다.

## 2026-11-05 추가 승인 후 공통 role 기능 검증

위 유예 기록은 이전 실행의 결과로 보존한다. 사용자는 후속 작업으로 **현재 격리 MySQL을 재확인하고 테스트 전용 role 1행과 사용자·포인트 이력을 생성한 뒤 이번 실행 행만 정리**하는 검증을 승인했다. 스키마 변경·성능 테스트·신규 의존성과 추가 기능은 포함하지 않는다.

- `mysql-shared-role/opt-in-disabled.xml`: 새 opt-in 없이 컴파일·선택 테스트 실행. 13건 모두 skip, 실패/오류0. DB 기능 통과로 세지 않는다.
- `mysql-shared-role/preflight.json`: 실행 직전 로컬 listener PID22580, mysqld 실행 파일·`--no-defaults`·loopback 바인딩·전용 datadir가 ignored 로컬 기록과 일치하는지 확인한 결과. 새 CLI 연결에서 DB/계정/port/MySQL8.0.35/RR/InnoDB·user0/point0/role0을 확인했다. datadir 원문·자격값은 기록하지 않는다.
- `mysql-shared-role/TEST-com.ngng.api.user.point.integration.PointHistoryMySqlConcurrencyTest.xml`: **실제 MySQL 13건 통과, 실패/오류/skip0**. 생성 role ID, 정확 marker, 공통 role 검증 및 정리 로그 포함.
- `mysql-shared-role/postflight.json`: 테스트 종료 후 별도 새 CLI 연결에서 user0/point_history0/user_role0, `rf003_` marker의 사용자·포인트·role 각0, 기존 role 목록 `[]` 동일성을 재확인했다.

현재 테스트는 과거의 `MIN(role_id)` 재사용을 제거했다. 기존 role 전체 목록을 먼저 보존하고, 최소 JPA context 및 트랜잭션 초기화가 완료된 뒤 실행 UUID marker를 `role_type`으로 하는 **전용 role 1행만** prepared statement로 생성한다. generated key를 commit 전부터 추적하고 fixture 사용자에게 이 ID를 명시한다. 새 opt-in은 `approved-local-shared-role-fixture-only`이며 이전 user/point 전용 opt-in으로는 role을 생성하지 않는다. 외부 연동 빈·Boot/YAML·DDL·기존 role 변경은 없다.

공통 role 테스트는 서로 다른 두 사용자의 실제 `user.role_id`가 생성 ID와 같은지, 실제 AuthService의 이메일 조회가 반환한 role ID/type이 전용 ID/marker와 같은지 확인한다. A가 사용자 잠금과 flush된 append를 미커밋 상태로 유지하는 동안 B의 충전이 커밋되어 1200원임을 확인했고, A Future 미완료 및 별도 조회의 A=1000/B=1200을 확인한 뒤 A의 commit을 허용했다. 최종 A=1100/B=1200의 이력 체인을 확인했다. DB 내부 잠금 테이블을 직접 관측한 결과나 성능 측정으로 해석하지 않는다.

정리는 worker가 완전히 종료된 뒤에만 수행한다. 이번 실행의 사용자 ID/email/name/role_id와 marker를 대조하고, 이력을 `point_id+user_id+type_detail`로 한 행씩 삭제한 뒤 사용자를 `user_id+email+name+role_id`로 삭제한다. 모든 fixture와 user/point 잔존0을 확인한 뒤 전용 role의 **ID+정확 role_type** 및 해당 role을 참조하는 사용자0을 확인하고 그 1행만 삭제한다. 기존 role 목록과 결과가 일치해야 성공이다. `@BeforeAll` 실패 시에도 worker 종료와 context close를 거치며, 추적한 role 삭제의 동일 안전 조건을 적용한다. 불명확한 commit·cleanup 실패 시 삭제 범위를 넓히지 않고 실패를 남긴다.

실행 marker는 `rf003_5fadb59b957d4779b98684221cd17097`, role ID는 `1`, role marker는 `rf003_5fadb59b957d4779b98684221cd17097_role`이다. 공통 role 시나리오의 사용자 ID는 21/22였다. XML 정리 로그는 **user15/point38/role1** 삭제 및 기존 role 행 불변을 확인한다. 구 알고리즘의 동시 충전은 이번에도 before1000 두 건 후 최신1100(정상 기대1300)으로 누락을 재현했다. 의도한 lock timeout1205 검증도 성공했다. 이 실행은 새 marker로 수행했으며 이전 `mysql/` 결과를 덮어쓰지 않았다.

backend 디렉터리의 실행 명령:

```powershell
# DB 미접속 opt-in 가드/컴파일 확인. 기존 환경의 opt-in이 없는 상태로 실행했다.
.\gradlew.bat --offline --no-daemon compileTestJava test --rerun-tasks --tests 'com.ngng.api.user.point.integration.PointHistoryMySqlConcurrencyTest'

# 별도 확인한 로컬 credential/datadir를 현재 프로세스 환경으로만 전달했다.
$env:RF003_MYSQL_TEST = 'approved-local-shared-role-fixture-only'
.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.user.point.integration.PointHistoryMySqlConcurrencyTest'
```

기존 Gradle 캐시 lock 접근을 위해 위 Gradle 명령과 로컬 process/CLI 확인은 승인된 격상 실행을 사용했다. 환경값은 종료 후 기존 값으로 복구했다. 최초 읽기 전용 사전 확인의 CLI JSON escaping 불일치는 가드가 차단했고, `--raw` 조회로 datadir 일치를 확인한 뒤 독립 안전 리뷰를 통과하고 실제 테스트를 실행했다. 이 과정에서 추가 DB 생성·기동·스키마/설정 변경·전체 통합 테스트·성능 측정·commit/push는 하지 않았다.

기존 role=null 조건으로 남았던 **같은 role을 가진 두 사용자의 진행 독립성**은 위 결정적 시나리오로 추가 확인됐다. 실제 HTTP 보안 필터·Boot 오류 body/content type·브라우저 E2E·운영 스키마/다른 writer 보장·성능은 이번에도 미검증이다. 오프라인 backend74건/frontend10건은 앞선 실행 증빙을 보존하며 이번 테스트 변경만으로 반복하지 않았다. 결과 XML·사전/사후 확인에 대한 최종 독립 리뷰와 RF-003 상태 결정은 작업 명세·로그에 기록한다.

공유용 XML의 hostname 속성은 장치 식별정보 보호를 위해 redacted로 치환했다. 원본 증빙은 Git에서 제외된 로컬 백업에 보존했다. 테스트 결과와 로그는 변경하지 않았다.

## 2026-11-05 증빙 보존 한계 및 후속 검증 정리

backend 19/74건과 MySQL 각 실행의 XML, 공통 role 실행의 preflight/postflight JSON은 보존 자료다. frontend 10건·변경 파일 ESLint·전체 TypeScript 검사·backend assemble의 성공은 위 명령/결과 서술로 기록돼 있으나 **각각의 원시 실행 로그는 별도 보존하지 않았다**. 프런트 테스트 스크립트는 실행 방법을 제공하며 그 자체가 통과 로그는 아니다. backend XML로 프런트·정적 검사·패키징 성공을 증명하지 않는다. 최초 역할 없는 DB 실행의 별도 CLI 확인은 서술 기록이며 공통 role 실행에는 별도 JSON이 있다.

완료 판정은 [명세](../../tasks/RF-003.md)의 합의된 기능 범위다. 아래 항목은 남아 있으며 최신 추적 상태는 [BACKLOG](../../BACKLOG.md#완료-후-잔여-검증별도-후보)를 따른다.

| 추적 | 보존 증빙의 한계 / 미실행 이유 | 추가 검증 방법 / 재개 조건 |
|---|---|---|
| R3-HTTP | standalone MVC·최소 JPA는 실제 보안 필터·Boot `/error` body/content type을 검증하지 않음 | 외부 연동 격리 서버·테스트 인증 확보 후 정상/금액·바인딩·인증 거부 및 저장·커밋·잠금 실패의 상태·본문·헤더·DB 상태 확인. 정책 변경은 별도 합의 |
| R3-브라우저 | fetch/hook 대역의 Node 10건은 브라우저/E2E 아님. production build는 정적 생성의 실제 API 조회 경계 때문에 제외 | 안전한 API·인증·데이터 확보 후 성공/취소·각 실패·재시도 UI와 실제 요청 순서/차단 확인. build 대상 API 확인 후 실행 |
| R3-운영 | 격리 MySQL 8.0.35/RR/InnoDB, 작은 fixture·2 worker의 결정적 결과만 확인. 운영 스키마/다른 writer 미검증, hook/Future는 DB 잠금 대기의 직접 관측 아님 | 운영 구성·쓰기 주체·잠금 규약을 조사하고 대표 격리 환경에서 충돌/교착·실패 복구 확인. DB 대기 직접 관측은 별도 권한·방법 필요 |
| R3-성능 | 승인 범위는 기능 검증이며 SQL 수·실행계획·지연/처리량 미측정. timeout은 의도적 실패 검증 | 후속 사용자 지시 후 대표 환경·데이터·동시성을 정해 경합·지연·처리량·오류율 측정. 테스트 시간으로 대체하지 않음 |
| R3-멱등성 | 범위 밖. 충분한 잔액의 동일 payment 재전송은 각각 별도 차감됨을 확인 | 중복 식별·키 수명/범위·재시도 응답 정책 합의와 별도 명세 후 동시 중복/응답 유실/재전송 검증. 스키마 변경은 별도 승인 |
| R3-거래원자성 | 결제 실패 뒤 후속 호출 차단만 확인. 결제 성공 뒤 상태 변경 실패의 원자성은 해결하지 않음 | 결제/거래 경계와 복구·보상·재시도 정책 합의 후 별도 명세에서 결제 커밋 뒤 상태 변경 실패·응답 유실 주입 및 잔액/상태 확인 |

전체 backend test/build는 기존 외부 연결·DDL·고정 fixture 때문에 제외했다(R1-통합/RF-016). 과거 잔액 복구는 범위 밖이다. 원시 로그 공백은 당시 자료를 확보할 때 출처를 밝혀 보완하고 새 실행은 별도 날짜·환경으로 남긴다. 이번 문서 보완에서 원시 결과·XML을 수정하거나 테스트·DB 접속·성능 측정을 실행하지 않았다. 별도 후보의 일정·담당·구현 승인은 정하지 않았다.
