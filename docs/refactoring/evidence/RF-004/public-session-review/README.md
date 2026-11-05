# RF-004 재개 검토·회귀 증빙

2026-11-05, HEAD `aa25aaedf783870be1641a90f6c7db7ad08bd021`, 시작 clean. 공개 Session API 접근과 기존 격리 fixture 회귀를 승인받아 재개했으나 pending 변경 보존을 포함한 안전한 snapshot 동기화를 입증하지 못해 운영 구현은 추가하지 않았다. 최신 실제 MySQL은 **37건29통과/8실패**, 전체 미완료·배포 부적합이다. 이전 reader29건 실패는 별도 이력으로 남긴다. 이후 서비스 경계 변경은 읽기 전용으로 검토만 했으며 대안 선택·구현 승인은 없었다. 사용자가 추가 상세 설계·검증을 현재 결과로 보류하도록 결정했으므로 추가 구현·테스트·DB 실행을 중단한다. 검토 이력과 재개 조건은 [메인 증빙](../README.md)의 마지막 절을 따른다.

## 보존 자료와 실행 범위

- 루트 `TEST-*.xml`/`source-and-result.json`: 신규8건 작성 직후 컴파일·offline41통과/DB37skip. 이후 독립 리뷰의 비보호 필드값 assertion 보강 전 결과다.
- `offline-final/TEST-*.xml`/`source-and-result-final.json`: assertion 보강 후 재컴파일·Policy26/Creation15 모두 통과, MySQL37skip. opt-in 부재 skip은 DB 기능 통과가 아니다. 두 오프라인 실행의 Gradle 원시 콘솔 로그는 도구 출력에만 있으며 별도 파일로 보존하지 않았다.
- `mysql.xml`, `mysql-run.log`, `mysql-summary.json`: 실제 MySQL37건29통과/8실패/error0/skip0, Gradle exit1. 테스트 소스는 `TransactionAcceptanceMySqlConcurrencyTest.java.txt`와 최종 source SHA256, fixture는 `Rf004MySqlFixture.java.txt`로 동결했다. `production-hashes.json`의 운영5파일은 기존 실패 당시와 같으며 추가 구현 효과를 검사한 결과가 아니다.
- `preflight.json`, `postflight.json`: 동일 PID22580/loopback13306/executable/private datadir 일치, MySQL8.0.35/RR, 12개 관련·참조 테이블0. 개인 datadir·자격값은 공유하지 않는다. raw XML/로그·전후 JSON과 실행 runner는 Git 제외 `local/rf004-results/public-session-review/`에 보존한다. 공유 XML/로그는 hostname/account/사용자·workspace 경로를 정제했다.

backend에서 실행한 선택 명령:

```powershell
.\gradlew.bat --offline --no-daemon compileTestJava test --rerun-tasks --tests 'com.ngng.api.transaction.service.TransactionAcceptancePolicyTest' --tests 'com.ngng.api.transaction.service.TransactionCreationContractTest' --tests 'com.ngng.api.transaction.integration.TransactionAcceptanceMySqlConcurrencyTest'
```

실DB는 독립 안전 리뷰 후 별도 프로세스에만 opt-in/대상/자격 환경을 주입하고 같은 Gradle 옵션으로 integration 클래스 한 개만 실행했다. runner는 종료 시 환경을 제거한다. 첫 sandbox 실행은 기존 Gradle cache lock 접근 실패였고 기존 캐시 접근 escalation 뒤 offline만 사용했다. 신규 의존성 다운로드·추가는 없다.

## 안전 확인

실행 전 테스트 diff와 변경 없는 fixture를 독립 리뷰했다. 기존 marker/FK/price 정리 identity를 바꾸지 않고 purchaseAt/createdAt만 수정한다. 추가 직접 SQL은 각 테스트가 생성·추적한 product PK1행의 for_sale=false, requestA PK1행의 is_accepted=false뿐이다. 첫 CLI preflight는 batch JSON escaping으로 datadir 비교 실패해 쓰기 전에 중단했다. `--raw` 및 첫 인자 `--no-defaults`로 보강해 재리뷰 후 동일 대상·빈 데이터를 재확인했다. fixture 자체 process/listener/datadir/account/engine/unique/FK 가드도 통과했다. Boot/YAML·외부 발송·SQL initializer·DDL은 실행하지 않았다.

모든 worker를 중단한 후 marker+PK+연관 소유권으로 details33/request80/thumbnail38/product40/user117/category1/status1/transaction_status1만 삭제·commit했다. XML의 cleanup 확인과 새 별도 CLI postflight12테이블0을 함께 보존했다. transaction_status1은 이번에도 부재하여 소유행을 생성/정리했으며 기존행 재사용 분기는 미실행이다.

## 실제 실패와 검증 한계

| 사례 | 이번 실행에서 확인한 결과 | 확인되지 않은 부분 |
|---|---|---|
| 기존 managed Product 후속 dirty flush | caller purchaseAt 저장 뒤 for_sale=true 복원 | 운영 HTTP 발생 여부·빈도 |
| 기존 managed Request 후속 dirty flush | selected와 sibling의 createdAt 저장, 두 is_accepted 모두 null 복원 | 운영 HTTP 발생 여부·빈도 |
| 신규 pending-first/후속 변경 | pending 및 후속 purchaseAt/createdAt 정확 값 assertion 통과 후 for_sale=true로 실패 | pending 변경 유실을 실증한 결과가 아님; 뒤의 요청 상태 assertion은 도달하지 않음 |
| 신규 Product affected0 | 상품 UPDATE affected=0, ORM entityUpdateCount 불변 통과 후 getter가 true라 실패. 요청 UPDATE·거래 INSERT는 발생 | 실패 뒤 purchaseAt 변경/검사는 미실행 |
| 신규 이미 거절된 Request | native writer 호출0/ORM update0/DB state 불변 통과 후 getter가 null이라 실패 | 실패 뒤 createdAt 변경/검사는 미실행 |
| 신규 원래 read-only 객체 | read-only flags 보존 통과, Product/selected/sibling의 보호필드 getter 모두 stale | assertion 실패로 outer tx rollback, 정상 commit 후 상태는 이 사례로 증명하지 않음 |
| 신규 stale RR 동일 반복 | native writer 호출0/ORM update0 통과, 세 보호필드 getter stale | caller Future 실패 뒤 외부 state 비교는 미실행 |
| 신규 stale RR + caller pending | 동일 재수락에409가 나와 outer caller tx rollback | SQL 순서 직접 trace·커밋된 stale overwrite는 미증명; pending 값 최종 검사 미도달 |

신규 proxy 및 예외 read-only flag 복구2건은 통과했다. 총 신규8건 중6실패/2통과이며 기존29건은27통과/2실패다. 무쓰기의 ORM update count는 테스트 전용 Hibernate 공개 Statistics로 관찰했고 native writer 호출/affected는 테스트 repository wrapper에서 기록했다. 기능상 불필요 쓰기를 구분하는 용도이며 성능 지표가 아니다.

## 설계 결론과 재개 조건

공개 `Session.setReadOnly` 문서는 unmodified object를 대상으로 하고 read-only snapshot을 유지하지 않는다. 기존 로컬 Hibernate6.4.4.Final sources.jar의 `AbstractEntityEntry.setReadOnly`를 읽어 true에서 loadedState=null, false에서 현재 전체 속성값을 deepCopy함을 확인했다. 이 내부 소스는 근거로 읽었을 뿐 운영/테스트에서 SPI를 직접 호출하지 않았다. true→false만 적용하면 비보호 pending 값도 새 snapshot에 들어갈 수 있어 유실 위험이 있다. 선행 flush·단순 setter 또한 stale 보호필드 쓰기/반복 불필요 쓰기를 함께 해결했다고 볼 수 없다. 안전한 공개 API 설계의 존재 자체를 부정하는 결론은 아니다.

추가 운영 구현 없이 테스트·증빙만 남겼다. pending 보존·보호필드 stale flush 방지·affected1/0/무쓰기·read-only/proxy/예외 복구를 함께 만족할 설계를 합의·독립 리뷰한 뒤 같은 회귀와 cleanup을 재검증해야 한다. 운영 pool/HTTP/브라우저/다른 writer/성능은 이번에도 미검증이다. 스키마·의존성·인가·pool 설정·성능 작업이나 추가 후보는 자동으로 시작하지 않는다. 명세·메인 증빙 README·BACKLOG·REFACTORING_LOG에 상태를 맞췄으며 commit/push하지 않았다.
