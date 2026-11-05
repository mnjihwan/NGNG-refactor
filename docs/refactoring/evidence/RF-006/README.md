# RF-006 증빙 안내

- 2026-11-05 최종 **최소 구현·선택 회귀·독립 리뷰 완료**. 아래 첫 분석 기록은 구현 전 당시 이력이며 실제 실행·최종 판정은 후반 결과에 기록했다. [명세](../../tasks/RF-006.md), [백로그](../../BACKLOG.md), [로그](../../REFACTORING_LOG.md).
- 시작 HEAD `2ef37de4ddb7a40e7b0b2e9c662c61d08b5b939a`, git status --short 출력 없음. 사용자 변경을 덮어쓰지 않는다.
- 수행: AGENTS/README/TEMPLATE/BACKLOG·관련 RF-005 증빙, transaction controller/service/entity/DTO·AuthService·SecurityConfig/TokenFilter·기존 테스트·frontend 호출 읽기와 검색, 공식 Java17 JLS/Long/Objects 문서 확인. 출처와 판단은 명세에 링크했다.
- 관찰: 두 Long 참조 비교와 역할 분기 부재, TokenFilter 비skip, 기존 테스트 고정 DB/필터 off. 같은 값·다른 참조가 거부되는 언어상 경로는 확인했으나 운영 재현·빈도는 미확인이다. 코드 실행 결과로 주장하지 않는다.
- 최소 권고: 기존 두 소유자 조건을 Objects.equals로 비교. 기존 null/null 특성·상품 소유자 기준·ADMIN 비우회·미확인 USER 허용 조건은 유지. 새로운 역할/상태 전이/소유권 정책은 공식 Java 자료로 정당화할 수 없어 별도 결정 대상이다.

## 실행·보존 현황

분석 담당자는 코드/테스트·DB·빌드 실행을 하지 않았다. 실행 XML·console·환경 snapshot은 아직 없다. 읽기/검색 출력은 대화 tool 결과만 있고 별도 원시 파일은 보존하지 않았다. 향후 before/after/실패 실행의 원시·정제 자료와 정확한 명령·건수·skip·환경·소스 hash를 구분해 추가한다. 새 실행을 이전 시점 증빙으로 대체하지 않는다.

## 한계·미실행과 재개 조건

| 범위 | 현재 한계·미실행 이유 | 추가 검증 / 추적 |
|---|---|---|
| 서비스·필터 없는 MVC·실제 Security 체인 | 분석 단계, 아직 테스트 없음 | 명세의 별도 Long 참조/역할 9조합·인증 실패·응답/무변경 fixture와 명시 bean 격리 후 실행. R6-격리 |
| DB·Boot HTTP·브라우저·프록시·OSIV·외부연동 | 합성 대역 계획은 실제 commit/rollback/운영 배치 증명 아님 | 별도 안전 서버·격리 DB fixture·인증·외부연동 차단 확인 후 수행. R6-연동 |
| null-ID·상품 소유자/기록 seller 불일치·역할·전이 | 기존 코드 특성 보존만 계획, 정책 재설계 아님 | 별도 정책/명세 결정 전 변경 없음. R6-정책 |
| 성능/발생빈도 | 측정하지 않음 | 대표 환경과 별도 비교 계획/지시 필요. R6-연동 |

완료 기록에는 실제 결과·독립 리뷰·diff 검증과 남은 항목을 명세/백로그/로그에 함께 반영한다. RF-004 보류 해제·다음 RF 자동 착수를 뜻하지 않는다.

## 2026-11-05 실제 실행 결과 (구현 담당 인계)

현재는 **최소 구현·선택 회귀 완료, 독립 리뷰 완료**다. 위 정적 분석 기록은 당시 이력이며 아래 실행은 이후 별도 실행이다. 운영 diff는 service `Objects` import와 소유자 비교2줄이다.

| 실행 | 실제 결과 | 보존 근거 |
|---|---|---|
| 최초 before | 총25: 서비스/MVC24통과, 체인 초기화1실패, skip0, exit1 | [console](before-initial/console.txt), [체인 XML](before-initial/TEST-com.ngng.api.transaction.status.TransactionStatusSecurityChainTest.xml), 해당 디렉터리 서비스 XML·체인 소스 |
| 보정 before | 37/37통과(24+13), fail/error/skip0, exit0,5tasks executed | [console](before/console.txt), before XML2개·운영/fixture/테스트 snapshot |
| after 선택 회귀 | 196/196통과(24+13+72+46+26+15), fail/error/skip0, exit0,5tasks executed | [console](after/console.txt), after XML6개·운영/fixture/테스트 snapshot |
| assemble | exit0,3executed/2UP-TO-DATE | [console](assemble-console.txt) |
| diff | git diff --check exit0 | 대화 tool 결과. 문서 최종 정리 후 재확인 exit0 및 부모 담당자 독립 리뷰 완료 |

최초 실패는 실제 운영 결함이 아니라 최소 체인 harness의 `${jwt.expiration.access}`/refresh 테스트 property 누락이었다. MapPropertySource로 합성 만료값만 주입해 보정했다. 최초 snapshot은 당시 체인 소스만 보존했으며 당시 운영 소스/fixture/서비스 테스트 전체 snapshot은 별도로 보존하지 않았다. 보정 before에는 이들 전체 snapshot을 보존했다. 시스템 java stub 호출은 완료되지 않아 중단했고 JDK17 직접 경로의 JAVA_HOME으로 진행했다. 최초 Gradle sandbox cache lock 접근 거부는 tool 결과에만 남았으며 기존 캐시 접근 자동승인 후 동일 오프라인 명령을 사용했다. 해결된 환경 거부/중단을 테스트 건수로 세지 않는다.

Windows PowerShell, Oracle JDK17.0.10 ([출력](java-version-direct.txt)), wrapper8.7, 기존 JJWT0.11.5/Mockito/Spring 의존성만 사용했다. 명령은 NGNG-backend에서 실행했다. 각 console은 PowerShell `*> ../docs/refactoring/evidence/RF-006/<경로>/console.txt`로 보존하고 `$LASTEXITCODE`를 확인했다.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
# 최초 및 보정 before: 운영 source 변경 전
.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.transaction.status.*'
# after: 운영 비교만 수정하고 같은 fixture의 결함 기대값을 성공으로 전환
.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.transaction.status.*' --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest' --tests 'com.ngng.api.transaction.service.TransactionCreationContractTest' --tests 'com.ngng.api.transaction.service.TransactionAcceptancePolicyTest'
.\gradlew.bat --offline --no-daemon assemble
```

before의 37건은 기존 동작을 고정한 특성 테스트다. 각 작은7/큰1000에 별도 new Long와 assertNotSame/값 동등성으로 같은 값 다른 객체의 소유자401을 확인했다. 단순 valueOf 또는 동일 엔티티 인스턴스만으로 결함을 가리지 않았다. after에서 동일 fixture의 소유자/구매자가 성공한다. 실제 chain role USER/ADMIN/UNCONFIRMED_USER×상품소유자/consumer/제3자9조합은 앞의6개200/제3자3개401이며 ADMIN 우회가 없다. JWT principal에는 ID가 없고 실제 AuthService가 합성 email로 repository를 조회하는 것을 검사했다. missing/malformed 인증은 저장소 미도달401, missing/expired access와 유효 refresh는 상태변경200/재발급헤더를 검사했다.

서비스/standalone MVC24건은 같은 참조·캐시작은ID·별도참조 소유자, 기록seller만 일치 거부, consumer-ID 단락평가, null/null 기존 허용 및 null/nonnull 거부, 조회순서·상태부재 무변경, DTO/statusId/잘못된JSON·path400/소유권401을 확인한다. 소유자/consumer의 ID와 역할/상태/가격 계약 이외 새로운 정책은 추가하지 않았다. `new Long`은 테스트에서 캐시 영향을 배제하기 위한 JDK17 fixture이며 신규 의존성이 아니다.

전체 테스트 소스 compileTestJava는 수행했지만 기존 SpringBootTest/실DB 테스트는 실행하지 않았다. 실제 SecurityConfig/filter/JWT/controller/service/AuthService를 명시bean으로 연결했으며 Boot/application scan·운영 properties·datasource/외부client/transaction manager는 없다. 필터 없는 MVC는 standaloneSetup이며 인증 검증으로 표현하지 않는다. after의 기존 acceptance/creation41 회귀는 보류된 RF004 실DB 정합성 실패를 해소하거나 재개한 증거가 아니다.

공유 XML/console/source snapshot은 [SHA256SUMS](SHA256SUMS.txt)와 대응한다. 원시 자료는 gitignored `.local-runtime/rf006-raw/`에 보존했고 공유 사본의 workspace/home 경로를 `<workspace>`/`<user-home>`으로 비식별화하고 XML hostname과 합성 JWT를 제거하고 XML·console의 줄 끝 공백을 정리했다. 테스트 건수·실패 정보는 유지하며 SHA256은 정제된 공유본 기준이다. 실제 email/토큰/키는 사용·기록하지 않았다. after 소스 hash는 당시 신규 테스트/운영 snapshot과 대응하며 before는 그 디렉터리의 archived source를 사용해야 재현할 수 있다. 현재 소스로 before 명령을 돌리면 after 실행이므로 과거 증빙의 대체가 아니다. 테스트 경과시간은 성능 지표로 해석하지 않는다.

미실행: 실제 DB dirty checking·commit/rollback·OSIV/Boot HTTP·브라우저/프록시/cookie 후속요청·외부서비스·운영 빈도/성능·전체 test/build. 작은 mock graph의 setter/DTO 성공과 assemble은 운영 데이터 정합성·실제 서버 계약을 입증하지 않는다. 기존 통합 테스트의 외부연결/DDL 위험과 범위 제한으로 실행하지 않았으며 안전 격리 서버/DB·fixture·외부연결 차단 및 별도 지시 후 재개한다(R6-연동). 정책 변경(null ID·상품 owner와 기록 seller 기준·role/전이)은 별도 명세/결정이 필요하다(R6-정책). 부모 담당자의 독립 코드·테스트·증빙 리뷰 완료: 수정 필요 결함 없음(R6-격리). commit/merge/push 없음.

독립 리뷰(2026-11-05): 부모 담당자가 코드·테스트와 before37/after196 XML·최초 property 누락 실패/보정 기록을 독립 검토해 수정 필요 결함이 없음을 보고했다. null/role/단락평가 및 운영 미검증 한계를 확인했다. 합의된 비교 수정·선택 검증·리뷰 완료로 판정한다. 최종 `git diff --check` exit0이며 출력은 대화 tool 결과에 남긴다. 기존 분석/실행 이력과 잔여 R6-연동/정책 상태는 유지한다.
