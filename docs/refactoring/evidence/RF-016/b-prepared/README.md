# RF016 B: 물리 검증 실행 전 준비

2026-11-10, HEAD `82aa7279886385bc0974ae78679ab492447f4135`. **서버 시작·DB 연결·DB 쓰기는 실행하지 않았다.** A 제한 Boot 결과 및 B 순수 guard 단위 결과를 물리 MySQL 통과로 해석하지 않는다. 이 문서와 runner는 실행 준비물이며 환경 사용 승인/독립 리뷰를 대신하지 않는다.

## 준비 범위

- `Rf016MySqlSafetyGate`: opt-in, 고정 JDBC URL, PID/executable/datadir/options/loopback listener 및 DB metadata, exact PK/identity/미추적 참조·불확실 commit 거부의 순수 판정.
- `Rf016MySqlFixture`: host identity 확인 뒤만 DriverManagerDataSource를 만들고, **매 연결 전에** host를 재확인하며 **매 새 연결에서** DB/schema/account/port/datadir/version/RR을 검사한다. 자격값/개인 경로를 로그에 쓰지 않는다. user/point 초기 0, 기존 USER role 정확히 1, role 전체 원본, InnoDB/PK/FK·RESTRICT/NO ACTION, trigger/event/외부 schema 참조를 읽기로 확인한다.
- metadata count=0을 부재 증거로 쓰기 전에 `SHOW GRANTS FOR CURRENT_USER`를 내부 파싱하여 현재 계정의 직접 global SELECT/TRIGGER/EVENT/REPLICATION CLIENT 또는 ALL PRIVILEGES를 요구한다. schema 한정 grant/role 경유 모호성은 거부한다. 이는 기존 권한을 확인하는 gate이며 권한 확대 요청/실행이 아니다. 현 `ngng` 계정의 권한은 미확인이고 이 요건을 충족하지 않으면 쓰기 전 실패해야 한다. 매 연결의 실제 server globals에서 scheduler/persisted globals OFF, replica status/connection configuration/group member 0도 확인한다. 출력/공유 증빙에 grant 원문이나 replication 설정값을 남기지 않는다.
- 명시 JPA 구성은 `PersistenceManagedTypes`로 User/Role/PointHistory 세 엔티티, `JpaRepositoryFactoryBean`으로 user/point 두 repository, 실제 AuthService/PointHistoryService 및 실제 JpaTransactionManager만 등록한다. Boot/application.yml/auto-configuration/component/repository scan, Redis/ES/SMTP/SMS/AWS bean은 없다. B는 합성 SecurityContext이며 A JWT 체인과 별개다.
- Hibernate `hbm2ddl.auto=validate`, scripts action none을 사용한다. **JPA database.action=none을 동시에 주면 Hibernate validate를 덮어쓰므로 그 DB action은 설정하지 않는다.** create/update 및 fallback은 없고 Boot SQL initializer도 없다. 현 schema 적합성은 미검증이며 불일치 시 쓰기 전 실패해야 한다.
- 초기 사용자·포인트는 JDBC transaction으로 만든다. 생성 PK/불변값은 commit 전에 메모리 ledger와 고유 private JSONL journal에 기록하고 `FileChannel.force(true)`를 수행한다. 실제 service repository save 반환 직후 같은 ledger에 기록하고 실제 EntityManager.flush를 수행한다. PK/journal 실패, 예기치 않은 service/commit 실패는 cleanup을 금지한다.
- 정상 service transaction 종료 뒤 새 독립 autocommit JDBC 연결의 connection ID가 실제 service 연결 ID와 다른지 확인하고 같은 관측 연결에서 exact PK/잔액을 검사한다. rollback은 실제 INSERT/flush 뒤 관찰 hook이 의도 예외를 던지고, service transaction 종료 뒤 별도 연결에서 해당 PK 부재·이전 전체 이력/값 불변을 확인하도록 작성했다. 테스트 바깥 transaction을 금지한다.
- cleanup은 identity/FK/trigger/event 재확인 뒤 한 JDBC transaction에서 모든 ledger identity·유입 FK·미추적 user/point를 **첫 DELETE 전에** 검사한다. 의도 rollback PK만 부재를 허용하고 나머지 소유행 누락/변조도 거부한다. point→user 정확 PK+identity 조건으로만 삭제하고 예상 행수/전체0/role불변을 검사한다. commit 후 독립 연결로 재확인한다. role UPDATE/DELETE, 광역 DELETE/TRUNCATE, schema/auto_increment 변경, 알 수 없는 참조의 자동 삭제는 없다.
- snapshot 경쟁 보완: cleanup 시작에 user/point의 전체 PRIMARY 인덱스를 `FOR UPDATE` 현재 읽기로 잠그고 role 전체는 `FOR SHARE`, 유입 FK는 해당 범위를 `FOR UPDATE`로 읽는다. RR 전체 인덱스 범위의 next-key/gap 잠금 근거는 [MySQL Locks Set](https://dev.mysql.com/doc/refman/8.0/en/innodb-locks-set.html)과 [Phantom Rows](https://dev.mysql.com/doc/refman/8.0/en/innodb-next-key-locking.html)다. 이 자료는 현 환경에서의 잠금 검증 결과가 아니다.
- actual run에서는 동일 range-lock helper를 획득한 상태에서 별도 연결의 user/point INSERT를 각각 실행하고 session lock_wait_timeout=1의 1205를 요구하도록 작성했다. worker는 항상 명시 rollback, 잠금 guard transaction도 의도 rollback하고 독립 연결로 기존 user/point/role 불변을 검사한다. 예상 밖 INSERT 성공도 PK/identity를 journal에 기록한 뒤 rollback한다. worker 미종료·PK 손실·rollback/commit 불확실은 광역 복구 없이 중단한다. **두 probe가 해당 run에서 검증되기 전에는 자동 cleanup도 거부**하여 불확실한 잠금 전제로 삭제하지 않는다. 중도 실패 시 소유행이 남을 수 있으며 marker/PK journal로 읽기 복구 검토해야 한다.

## Runner와 실행 gate

[verify-and-run.ps1](verify-and-run.ps1)은 기본 `Inspect`이며 서버/DB 접속을 하지 않는다. 명시 기존 initialized datadir와 `ngng_local` 디렉터리, reviewed executable SHA256을 요구한다. `Start`와 `RunDatabaseTests`에는 다른 opt-in을 요구한다. Start는 mysqld/process/listener가 하나라도 있으면 거부하고, `--no-defaults`·loopback13306·전용 datadir·mysqlx 비활성·persisted globals 로딩/replication 자동 시작/event scheduler 차단으로 새 process만 시작하도록 작성했다. Java host gate도 실제 process args의 `--persisted-globals-load=OFF`/`--skip-slave-start`/`--event-scheduler=OFF`/`--mysqlx=0`를 필수로 검사하므로 안전 상태 미확인인 기존 공유 서버를 수용하지 않는다. 서비스 설치/초기화/스키마 생성/자동 종료 경로는 없다.

기존 executable/datadir는 workspace 밖이다. **Start는 외부 datadir에 쓰는 작업이므로** 구체 대상의 환경 안전성·독립 코드 리뷰·sandbox 권한 판단 전에 실행하지 않는다. RunDatabaseTests는 기존 loopback listener/PID/executable 재확인과 private process 환경의 password를 요구한다. Java의 authoritative ProcessHandle args/datadir 확인이 실패하면 첫 JDBC 연결 전에 거부한다. 원시 server/Gradle/XML/journal은 ignored `local/rf016-results/<고유 실행>`에만 보존하도록 작성했으며, 공유 전 비밀·개인 경로를 확인한다.

추가된 `ReadOnlyPreflight`는 write opt-in과 구별되는 별도 문자열/class다. 매 연결에서 server-side TRANSACTION READ ONLY/@@transaction_read_only를 확인한 뒤 constructor의 SELECT/SHOW만 실행한다. JPA 시작/setup/insert/cleanup은 명시 거부하며 쓰기 test/경쟁 probe를 선택하지 않는다. 해당 읽기 경로는 독립 리뷰를 거쳤지만 실제 연결은 아직0건이다.

## 현재 검증·제약

초기 제한 sandbox의 Get-NetTCPConnection/CIM 오류 및 Get-Process0은 host 부재 증거가 아니었다. private 문서/기존 runner에서 경로만 메모리 추출한 Test-Path 결과 executable/datadir 존재, 둘 다 workspace 밖이며 executable8.0.35.0/SHA256은 before 자료에 기록했다. 최초 Start는 자동 심사에서 외부 datadir 쓰기의 사용자 재승인 없음으로 거부되어 명령이 실행되지 않았다. 이후 정확 target의 시작/read-only만 사용자 명시 승인됐고 새 권한 요청은 허용됐으나, 상승 재검사에서 mysqld2개가 발견돼 **Start 전에 runner가 중단**했다. Inspect 외 DB mode는 실행하지 않았다.

상승 읽기로 MySQL80 Running PID4716(parent1432)→child5544 및 PID5544의 0.0.0.0:3306/33060 listener를 확인했다. 서비스 defaults-file의 단일 datadir는 승인 target과 다르나 runtime executable/commandline은 계속 비가시라 실제 분리를 확정하지 않는다. 기존 process 종료/guard 완화/Start 재시도/DB 연결 없이 보류했다. 관련 비밀 없는 JSON은 상위 디렉터리에 있다. 정확 target 경로는 사용자 승인 검토 요청에 한해 부모에게 전달했으며 자격값은 출력하지 않았다.

`offline-first`는 A12+당시 B pure gate13=25건 fail/error/skip0의 원시 XML/console다. 그 B 실행의 정확 source snapshot은 결과 보존 전에 journal/identity guard를 보완하여 별도 보존되지 않았다. 해당 성공을 이후 source의 증빙으로 대체하지 않고, 현재 source snapshot과 최종 선택 재실행은 `source`/`offline`에서 구분한다. 물리 opt-in test는 어느 실행에도 선택하지 않았다. 최종 결과/해시는 상위 README와 `offline` 자료를 따른다.

최신 결과는 A12+pure gate18=30건 fail/error/skip0 및 fresh assemble0이다. `source`는 B Java5개, A의 최신 snapshot은 `a-local/final`이며 `offline`은 test-only 3개 클래스 결과다. MySQL physical/read-only class는 컴파일만 했고 선택하지 않았다. 상세 중간25/26/28/29 원본 및 snapshot 보존 한계는 상위 README를 따른다.

미검증: 실제 MySQL identity·기동 옵션 작동/ProcessHandle 접근 가능 여부·schema validate·JPA repository bootstrap/SQL·물리 commit/rollback·cleanup, runner mode 실행/timeout/프로세스 종료, 운영·전체 suite·성능. compile은 이러한 런타임 검증을 대신하지 않는다. 정상/실패 test의 실제 data source 연결과 JPA flush, exact cleanup은 안전한 구체 환경과 독립 리뷰 뒤 별도 실행해야 한다. USER role 부재 또는 gate 실패 시 role/schema 생성 fallback 없이 중단한다. commit 불확실/미추적 PK·참조/정리 실패는 journal의 marker+PK를 보존하고 읽기 복구 검토로 전환한다.

추가 실행 차단 조건은 metadata 권한 가시성 미확인, server runtime/안전 args 미확인, range-lock 경쟁 probe 미검증이다. 정확 target의 기존 서버 시작/read-only 범위만 사용자 승인됐으며 fixture 쓰기는 승인 제외다. 어떤 조건도 pure gate의 성공 수치로 대체하지 않는다. 알려진 metadata 권한 한계의 근거는 [MySQL privileges](https://dev.mysql.com/doc/refman/8.0/en/privileges-provided.html)와 [Information Schema](https://downloads.mysql.com/docs/mysql-infoschema-excerpt-8.0-en.pdf)이며, 현재 실제 grant 조회는 하지 않았다.

마감 독립 리뷰는 A12+순수 gate18=30과 fresh assemble 및 **현재 B 준비/보류 판정** 범위를 대조해 차단 결함 없음을 확인했다. B의 실제 identity/preflight/물리 기능 완료 판정이 아니다. 현재 미해결은 기존 process runtime 분리·read-only metadata 검증이다. fixture INSERT/DELETE/경합 probe는 승인 제외다. [독립 기록](../independent-review.md)·상위 README/BACKLOG R16을 따른다. 공유 XML/console의 경로·로컬 사용자 치환은 원본 보존 후 수행했고 [pair manifest](../sharing-manifest.json)에 원본/공유 hash를 구분했다. 코드·테스트·서비스/DB를 마감 중 실행하지 않았다.
