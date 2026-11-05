# RF-004 분석·검증 증빙

**현재 상태(2026-11-05): 사용자 지시로 보류, 전체 미완료·현재 변경은 배포 부적합.** backend 오프라인41건·frontend31건 통과. 최초 실DB20건은17통과/3실패로 다른 상품 잠금 충돌을 발견했고, 승인된 별도 reader/정확 PK 갱신 수정 뒤 실DB29건은27통과/2실패로 관리 Product/Request의 stale 값이 후속 flush에서 복원되는 결함을 재현했다. 두 실패 실행을 성공 증빙으로 대체하지 않는다. 사용자는 Hibernate 내부 API를 사용하는 추가 구현·검증을 진행하지 않고 현재 결과를 기록해 보류하도록 결정했다. 이후 운영코드/테스트 수정·테스트 재실행·DB 접속/쓰기는 중단했다.

## 최초 분석 단계 이력

2026-11-05. 기준 HEAD `63435d19dfd9fb0ac3488db5e9668b0f85bf0cc6`, 분석 시작 작업 트리 clean. 아래는 [작업 명세](../../tasks/RF-004.md)의 정적 분석·계획 작성 당시 기록이다. 최초 초안은 정책 답변 대기였으며 이후 사용자 승인(동일 요청 재수락의 구매자/가격 일치 시 무쓰기200, 직접 생성 최소 보호·검증, frontend 실패 처리·검증)과 위임받은 충돌409 결정을 반영했다. 이 단계에서는 실제 DB 대상/fixture 범위가 추가 확인 조건이었다.

## 수행한 확인

- 루트 지침, refactoring README/BACKLOG/TEMPLATE와 RF-001/002/003 관련 명세·증빙을 읽었다.
- `git status --short`는 출력 없음, `git rev-parse HEAD`는 위 SHA였다.
- `rg`와 파일 읽기로 backend controller→request service→product/details service→repository/엔티티, 직접 POST 생성 경로, frontend API/hook/모달과 chat-server 결과 조회 경로를 확인했다. 정확 경로·행과 사실/가설은 명세에 기록했다.
- ignored 로컬 `local/schema.sql` 파일에는 transaction_details.product_id UNIQUE가 있음을 확인했다. 현재 DB 접속 결과가 아니며 운영 스키마 증거로 쓰지 않는다. 파일을 변경·재실행하거나 자격값·개인 datadir 원문을 증빙으로 복사하지 않았다.

이 단계의 근거는 기준 HEAD의 소스와 명세의 서술 기록이다. 별도 원시 명령 로그·테스트 XML·DB 전후 상태 자료는 생성/보존하지 않았다. 아직 테스트를 실행하지 않아 원시 테스트 증빙이 없는 것이며, 통과 결과가 소실된 것으로 기록하지 않는다.

## 한계와 미실행 항목

| 항목 | 미실행 이유와 해석 한계 | 추가 검증 / 재개 조건 |
|---|---|---|
| 반복/동시 수락 응답·거래 건수·rollback | 정책/범위를 반영했으나 아직 분석 단계. 코드에 검사 부재가 있다는 사실과 실제 장애 재현은 다름 | 승인 범위의 오프라인 특성·회귀, 안전한 격리 MySQL 동시성·실제 flush/rollback 검증 |
| 현재 DB·fixture 안전성 | 접속/쓰기/정리하지 않음. RF-003 승인 행 범위를 상품/거래에 확장하지 않음 | listener/datadir·계정/버전/isolation/engine/제약·기존 데이터 재확인, 정확 fixture/cleanup 범위 합의, 실행 전 안전 리뷰 |
| status ID1 | create가 하드코딩하나 현재 DB 존재/의미 미확인 | read-only 확인 뒤 기존행 불변 재사용 또는 부재 시 테스트 전용 ID1 생성·정리의 범위 합의. 기존행 변경 금지 |
| frontend/HTTP/채팅 연동 | 실제 서버·필터·브라우저 미실행. frontend helper/hook 오류 처리 위험은 정적 분석 | 승인된 최소 오류처리와 오프라인 대역 검증, 별도 안전 환경 확보 시 실제 HTTP/브라우저/재조회 검증 |
| 운영 writer·성능 | 수락 외 쓰기 경로·신규 요청 생성과 재판매 정책은 범위 밖, 성능 사전 확인 필요 | 별도 정책·범위 합의 및 사용자 후속 지시 후 검증. 기능 테스트 시간으로 성능 효과를 주장하지 않음 |

실행 계획·fixture 소유권·FK 순서 cleanup·worker 종료·사전/사후 확인·완료 조건은 명세에 있다. 후일 수행하면 명령/환경/marker와 XML·로그·전후 확인자료를 별도 디렉터리에 보존하고 실제 실행/skip/서술을 구분한다. 이 문서는 검증 완료나 DB 쓰기 승인을 뜻하지 않는다. [BACKLOG](../../BACKLOG.md)의 R4 추적 항목과 명세·로그를 함께 갱신하며 추가 테스트·별도 후보는 자동 착수하지 않는다.

## 구현 후 실제 실행과 보존 자료

- `before/`: 변경 전 request 특성5건 통과 XML와 당시 테스트 원문. 반복수락이 create를 재호출하고 수락을 거절로 덮어쓸 수 있다는 mock 특성을 보존한다. 실제 중복 DB 행 생성/DB rollback 증거가 아니다. 직접 create/MVC의 별도 변경 전 실행은 확보하지 못했다.
- `after-initial/`: 최초 구현·리뷰 보정 후 오프라인38건 통과 XML. null requestId 기존 IAE 경계와 VM timestamp source를 보존했다. 이후 실DB 실패가 발견된 당시 버전이며 최종 DB 통과가 아니다.
- `after/`: reader/PK 수정 후 backend41건(Policy26/Creation15), fail/error/skip0. 정상/반복/충돌·오류 전파·입력 경계, 기존 DTO/ID/Location, 기록용 transaction manager advice와 own/committed ID 합치기 및 PK 조회/갱신을 검사한다. 전체 compileTestJava도 성공했다. 최종 실행 로그는 tool output이며 원시 XML을 보존했다.
- `frontend/`: 실제 TypeScript를 기존 TypeScript5.4.5/Node20.10.0/npm10.2.3/SWR2.2.5로 실행한31건 TAP·명령/환경 JSON, 변경파일 lint 경고0·tsc 성공 로그. 최초 baseline2실패/29skip와 중간 SWR1실패/30skip 로그는 tool output만 있어 보존하지 못했다. 보존한 자료는 최종 소스의 별도 재실행이며 최초 원본으로 소급하지 않는다.
- `mysql/opt-in-disabled-*.xml`: 초기18/20건 skip, `opt-in-reader-disabled.xml`27skip, `opt-in-reader-baseline-disabled.xml`29skip. opt-in 미설정 시 접속 전 차단한 결과이며 기능 통과가 아니다.
- `mysql/mysql-first.xml`, `mysql-first-run.log`: process commandLine 조회 불가로 DB 연결 전 가드 초기화1실패, fixture0. 읽기 전용 CIM process fallback을 추가·독립 재리뷰한 뒤 재개했다.
- `mysql/mysql-second.xml`, `mysql-second-run.log`, `mysql-second-summary.json`: 최초 실제 MySQL20건,17통과/3실패. 다른 상품 사이 request PK lock1205, 부재 details 조회 후 insert1205, 양쪽 부재 조회 후 insert1213을 확인했다. 의도한 lock-timeout 검사의1205 성공 사례와 이3실패를 구분한다.
- `mysql/mysql-reader-baseline.xml`, `mysql-reader-baseline-run.log`: 승인된 reader/PK 수정 후29건,27통과/2실패. 기존 다른 상품 독립성3개, RR 최신성, 별도 reader 실제 연결/READ_COMMITTED/readOnly, 상위tx own rows·재호출·rollback은 통과했다. 관리 Product 수정 뒤 forSale=true 복원, 관리 선택 Request 수정 뒤 isAccepted=null 복원을 실제 확인했다. sibling 검사는 selected 실패 지점에서 중단돼 그 실제 후속 결과를 추정하지 않는다.
- `mysql/reader-baseline-source/*.java.txt`, `reader-baseline-source-hashes.json`: reader29건 실행 당시 운영5개/테스트2개 원문과 SHA256. 이후 수정 결과와 실패 당시 소스를 구분한다.
- 29건 실행 뒤 현재 testclass의 assertAccepted를 soft assertion·요청 정확2행 검사로 보강했으나 이 **최신 assertion 변경은 컴파일/테스트 미실행**이다. 보존한29건 XML은 `reader-baseline-source`의 실행 당시 소스를 검사한 결과이며 현재 테스트 파일의 실행 결과로 소급하지 않는다. 운영5개와 fixture helper는 실행 당시 소스와 같다.
- `mysql/preflight*.json`, `postflight*.json`: loopback13306·전용 mysqld/datadir 일치·DBngng_local·MySQL8.0.35/RR·빈 시작 상태와 별도 새 연결의 정리 후0을 기록한다. private datadir·password는 복사하지 않았고 공유본 hostname/account/개인·workspace 경로를 정제했다. **정제 전 원본은 `local/rf004-results/`에 보존**한다. 이 경로는 원래 untracked였으며 이번 `.gitignore` 추가로 제외했고 원본을 삭제하지 않았다. 정제한 RF004 `.log`는 `.gitignore`에서 예외 허용했다.

## 실제 명령

backend 디렉터리, 오프라인 변경 전5건:

```powershell
.\gradlew.bat --offline --no-daemon compileTestJava test --rerun-tasks --tests 'com.ngng.api.transaction.service.TransactionAcceptanceBehaviorTest'
```

당시 테스트 원문을 before에 보존한 뒤 승인 정책에 맞게 Policy/Creation 테스트로 대체했다. 변경 후 최종 오프라인41건:

```powershell
.\gradlew.bat --offline --no-daemon compileTestJava test --rerun-tasks --tests 'com.ngng.api.transaction.service.TransactionAcceptancePolicyTest' --tests 'com.ngng.api.transaction.service.TransactionCreationContractTest'
```

기존 사용자 Gradle 캐시 lock 접근이 sandbox에서 차단돼 첫 명령이 실패했고 승인된 캐시 접근 후 위 선택 명령이 성공했다. 외부 의존성 다운로드/신규 의존성 추가는 하지 않았다. 초기 오프라인 중간3실패는 대역 fixture 수정 후 해소됐으나 해당 최초 XML은 미보존, 보존 성공 XML은 후일 실행이다. `git diff --check`도 성공했다. Gradle 동시 실행으로 초기35건 XML 복사 전에 결과 경로가 지워져 별도 재실행했으며 그 재실행 XML을 보존했다.

실DB는 opt-in이 없는 같은 선택 명령으로 skip을 확인한 뒤, 검토한 대상/연결/fixture opt-in 환경을 **프로세스에만** 주입하여 다음 한 클래스만 실행했다. 자격값은 명령·공유 로그·Git 문서에 쓰지 않는다.

```powershell
.\gradlew.bat --offline --no-daemon compileTestJava test --rerun-tasks --tests 'com.ngng.api.transaction.integration.TransactionAcceptanceMySqlConcurrencyTest'
```

fixture는 user/category/product status/product/thumbnail/request/details 및 부재했던 transaction_status1을 생성했다. Boot 전체설정·Redis/ES/S3/SMTP/SMS·SQL initializer 없이 최소 JPA/실제 service proxy와 `hbm2ddl.auto=validate`를 사용했다. 스키마를 바꾸지 않았다. fixture marker+생성PK+연관 소유권을 확인하여 worker 종료 후 자식부터 정확 행을 삭제·commit했고 새 별도 CLI 연결에서 관련 모든 테이블0을 확인했다. 최초20건 정리 상세는 details17/request48/thumbnail22/product24/user69/category1/status1/transaction_status1이다. reader29건 정리는 해당 XML/새 postflight를 따른다. 두 실행 모두 시작부터 transaction_status1이 없었으므로 테스트 소유행만 만들고 정리했으며 기존행 재사용 분기는 실행하지 않았다.

프런트는 frontend 디렉터리에서 `node --test tests/rf004-transaction-request.test.cjs`, `npx --offline next lint --file src/app/chat/_api.ts --file src/app/chat/_hooks/usePrivateChatMessgae.ts --file 'src/app/chat/[roomId]/PrivateChat.tsx'`, `npx --offline tsc --noEmit --incremental false`를 실행했다. 정확 인자·exit는 frontend의 실행 JSON을 따른다. production build는 실행하지 않았다.

## 현재 한계·추가 검증/재개 조건

| 항목 | 현재 근거·미실행 이유 | 재개 조건/추적 |
|---|---|---|
| 관리 상태 정합성 | reader29건 중2실패로 실제 결함. affected1 뒤 일반 refresh만으로 affected0/무쓰기·선행RR stale 상태를 모두 해결했다고 볼 수 없음 | 사용자 보류. 재개를 명시 지시하면 설계/범위 재확인, 수정 diff 독립 리뷰, affected1/0·동일 반복·caller 후속dirtyflush 회귀 후 DB 재실행. R4-기능 |
| 추가 reader 연결/pool starvation | 기존 쓰기tx/잠금을 보유한 채 두 번째 연결을 잠시 사용. 실제 reader RC/readOnly·서로 다른 connection은 기능 검사했으나 pool 고갈은 미검증 | 위험을 설명한 사용자 승인 범위. pool 설정 변경 없음. 별도 사용자 지시 후 실제 pool/동시성 조건 검증. R4-연결 |
| 운영 writer/삭제·새 요청/재판매 | own/committed ID를 합쳐 정확PK를 잠그지만 외부 삭제로 사라진 과거 snapshot PK는 부재 gap 가능. 승인 경로는 해당 삭제/관계 변경을 하지 않음 | 실제 writer/규약·운영 제약 확인 후 별도 범위 합의. R4-운영 |
| 불일치·두 순서·기존 status1 | 구매자/가격/판매자 불일치·accepted인데 details부재는 오프라인 검사. 기존 DB에서는 reject→accept 순차, accept→reject 실제동시. 시작 status1부재라 재사용분기 미실행 | 필요한 별도 실DB 사례/fixture를 합의 범위에서 추가하고 실제 결과로 갱신. 미실행을 통과로 추정하지 않음. R4-기능 |
| 실제 HTTP/브라우저 | standalone MVC·Node fetch/hook/SWR 대역. Boot error body/보안 필터·브라우저/live chat/production build 미실행 | 안전한 서버·인증·데이터·외부 차단 및 별도 확인. R4-연동 |
| 성능/대기 관측 | 테스트 시간은 기능 실행 기록. SQL지연/처리량·운영 pool 한계 미측정, latch/Future는 performance_schema 잠금 직접 관측과 다름 | 후속 사용자 지시 후 별도 계획. R4-성능 |

스키마/의존성/인증인가·pool 설정·캐시·상품 재판매/취소·과거 데이터 복구는 바꾸지 않았다. 분석 시작 clean 이후 다른 담당의 동시 문서/프런트 변경을 보존했고 commit/push는 하지 않았다. 완료 판정은 이 실패를 해결하고 합의 범위의 실제 DB·cleanup·독립 리뷰를 충족한 뒤에만 한다.

검토한 유지보수 대안은 실행하지 않았다. 실제 native UPDATE1행 뒤 caller pending flush를 마치고 그 객체만 무락refresh하는 방식은 own-write 가시성으로 일부 경로를 다룰 수 있으나 affected0/반복 무쓰기·오래된 RR snapshot까지 일반화할 수 없다. 단순 setter는 불필요 dirty write, clear/detach는 caller의 후속 변경 유실, 잠금refresh는 eager/gap 잠금 재도입 위험이 있다. `@DynamicUpdate`는 엔티티의 모든 writer SQL에 영향을 주고 관리 getter의 stale 값은 남는다. Hibernate EntityEntry.loadedState와 보호필드를 함께 맞추는 국소 helper는 불필요 UPDATE 없이 동기화할 가능성이 있으나 내부 SPI/버전 의존·auto-flush 순서·유지보수 부담이 있어 사용자 결정대로 **미구현·미검증**이다. 별도 reader의 추가 연결 위험도 이 대안으로 해소되지 않는다.

공유 전 before/after-initial/after XML 5개의 hostname도 redacted로 치환했다. 정제 전 원본은 Git 제외 경로 local/rf004-results/share-original-xml/에 보존했으며 결과 값은 변경하지 않았다.
