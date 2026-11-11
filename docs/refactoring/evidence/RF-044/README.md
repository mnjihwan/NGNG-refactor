# RF-044 증빙: 신고 응답 최소화

**최신: 2026-11-11 합의 최소 구현·선택 검증·공통 최종 독립 리뷰 완료. 합의 응답 축소 범위의 P0/P1/P2 차단 결함 없음.** 구현 HEAD `b06c05e2b90bdbd7acb9c7d5314e59ada200c6ce`(RF024 PR #22 병합). before70통과/2skip·after335/335 및 assemble·frontend28/28. 아래 분석 당시 문구는 당시 이력이며 마지막 실행 절을 우선한다.

- 일자: 2026-11-11. 기준 HEAD `2091fae3b590f0286fe11e8a1354fc27d81e10c6`.
- 시작 Git 상태: 기존 `.tmp/` 및 병행 RF024/039/040 명세·증빙의 untracked 파일. 기존 자료는 수정·삭제하지 않았다.
- 상태: **정적 분석·계획만 작성, 구현·실행 검증 전**. 허용/실제 변경은 [명세](../../tasks/RF-044.md)와 이 README뿐. 테스트·빌드·DB/서비스 접속 없음, 신규 의존성·스키마 변경 없음, 성능 유예.

## 수행 범위·근거

Git status/HEAD, `rg --files`, `rg -n`, PowerShell Get-Content로 루트 AGENTS/운영 README/BACKLOG/재평가/TEMPLATE/RF008/RF024 및 신고 controller/service/DTO/entity/repository, User/Role, SecurityConfig/TokenFilter, PenaltyTran, 상품 동명 신고 DTO·매핑, frontend 생성 helper·관리자 목록/상세·상품/채팅 소비, 기존 ReportControllerTest를 읽었다. 잘못 추정한 frontend `src/type.d.ts` 검색은 경로 없음으로 끝났으며 실제 `product/_types/type.ts`와 각 페이지 선언을 별도로 확인했다. 이를 검증 성공으로 기록하지 않는다.

POST 생성 및 ADMIN 목록/단건/PATCH 네 응답의 User getter 경로를 확인했다. 생성 builder의 scalar ID와 읽기 연관 null 가능성, 관리자 UI가 사용하는 userId/name/nickname, DELETE의 User 없는 응답 및 상품용 별개 UserDTO의 id 계약을 구분했다. 실제 민감정보 출력/운영 유출·DB 로딩 성공은 확인하지 않았다.

2026-11-11 [OWASP API3 공식 원문](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/)의 속성 선택·최소 응답·스키마 검증 권고를 열람했다. 생성의 reporter/user 제거 및 관리자 3필드 요약 DTO는 현재 소비 근거에 대한 제안이며 OWASP가 NGNG 업무 필드를 직접 정한 것이 아니다. RF024 입력 변경과 ReportServiceImpl.save/공동 테스트의 충돌을 명세에 명시했다.

## 원시 자료·해석 한계

- 이 두 문서는 분석 서술이다. 검색/열람 출력은 대화 기록에 있으며 별도 raw·test XML·실행 로그·HTTP payload 파일을 보존하지 않았다. 테스트 성공/실패/skip 집계 없음.
- 운영 사용자·실제 JWT·비밀 설정·DB를 조회하지 않았다. non-null 합성 fixture 계획은 실제 생성응답에 연관이 채워진다는 증거가 아니다.
- RF008 실행 증거는 포인트 한정이며 이번 신고에 전용할 수 없다. 필터 비활성·고정 DB 자료의 기존 ReportControllerTest도 읽기만 했고 현재 안전성 근거로 쓰지 않았다.
- 후일 실행은 별도 날짜/HEAD/환경·정확한 선택명령·fixture/source/hash/원시 console/XML 및 필요 시 치환본/원본 구분을 기록한다. 재실행 결과로 당시 원시 자료의 부재를 덮지 않는다.

## 미실행·추가 검증·재개 조건

| 항목 | 현재 한계/미실행 이유 | 재개 조건과 방법 |
|---|---|---|
| Boot mapper/MVC before/after | 분석 전용 | 구현 지시 후 JacksonAutoConfiguration mapper를 실제 converter에 연결; 네 응답·정확키·null·날짜/숫자/Page/이미지와 합성 민감 필드 부재, 생성 User 미접근·관리자3 getter만 확인 |
| 실제 filter/JWT/서비스 대역 | 실행 금지 | 실제 SecurityConfig/TokenFilter/JWT + controller/service/repository 대역, POST3역할·ADMIN3응답 성공, 신고자/대상/제3자 비ADMIN403·무/불량토큰401·refresh 및 거부 저장소0. RF024와 입력/응답 공동 기대 확정 |
| 실제 JPA/OSIV/HTTP | DB·서비스 접속 금지 | RF016 안전 identity·소유 fixture·외부 차단·정확 cleanup 허용 후 생성직후/null·재조회·proxy·lazy 이미지/세션 경계를 실제 응답으로 비교. 대역을 물리 검증으로 간주하지 않음 |
| 프런트·외부 소비자 | 내부 정적 소비만 확인 | 생성 reportId→upload, 관리자 표시·제재 userId·Page/이미지 격리 검증 및 후속 브라우저; 외부/구클라이언트 조사·릴리스 고지 |
| 상품 공개 신고정보/이미지·업무 정책 | 별개 DTO 및 자유 본문/이미지 | 별도 개인정보/익명성·관리자 요구 합의. 이번 축소를 모든 개인정보 제거로 확대하지 않음 |
| 독립 리뷰·성능 | 구현 전·사용자 유예 | 통합 담당 문서 검토, 구현 후 diff/원시 근거 독립 리뷰. 성능은 별도 지시/대표 비교조건 후에만 수행 |

BACKLOG RF044/R8-범위밖 및 RF024/RF040 연계로 통합 담당자에게 잔여를 전달한다. 공통 BACKLOG/LOG를 이 담당자가 갱신한 것은 아니며 후속 작업 자동 착수를 뜻하지 않는다.

## 2026-11-11 실제 구현·선택 검증

변경은 생성 reporter/user 제거와 관리자 신고용3필드 요약 DTO 및 mapper5개 운영 파일, RF044 backend/frontend 테스트, 이 명세·증빙이다. frontend 운영/상품용 동명 DTO/User/Jackson/보안·엔티티·repository/스키마는 그대로다. RF024 저장 guard·null 미처리·대상 입력을 유지했다. 범위·설계와 잔여 상세는 [최신 명세](../../tasks/RF-044.md)의 마지막 절을 따른다. 구현 시작에 병행 RF039/RF043 소스와 RF039/040/043/044 문서 및 기존 `.tmp/`가 있었고 건드리지 않았다. Gradle 공유 실행은 통합 담당의 순서 배정으로 직렬화했다.

| 보존 경로 | 실제 범위·결과 / 원시 여부 |
|---|---|
| `before/backend-console.txt`, `before/test.xml` | **72건 중70통과·2skip**, failures/errors0. RF044 전용 sourceSet에서 실제 Boot mapper·MVC·security/JWT/AuthService/신고 service·repository 대역 실행. skip2는 변경후 생성/User getter 제약검사이며 통과가 아님. 전체 test 컴파일/실DB/운영 노출 재현 아님 |
| `before/source/` | 실행 당시 운영4파일·RF044 baseline test·변하지 않은 frontend 소비5파일. baseline test에만 before 분기와 payload 저장이 있고 최종 test에는 해당 모드/쓰기/skip 없음 |
| `before/create.json`, `list.json`, `detail.json`, `patch.json` | 로딩된 non-null 합성 User/Role을 반환한 네 실제 MVC 응답 tree. 합성 민감 literal을 `[SYNTHETIC-REDACTED]`로 치환하여 **캡처 시점부터 공유 가능한 JSON**으로 저장. 미치환 payload는 메모리에만 있었고 파일 원본은 없음. mapper에서 본 모든 User13키/민감필드 non-null을 before test가 확인했으며 실제 JPA 생성직후 결과라는 뜻이 아님 |
| `after/backend-console.txt`, `after/xml/TEST-*.xml`7개 | **335/335**, failures/errors/skips0, assemble exit0. RF04472 + RF02435 + 공통 security118/cache58 + RF00852. init 없이 원래 전체 compileTestJava sourceSet 사용; 실행 test는 완전한 클래스7개로만 선택. cache58에는 병행 RF039 추가1건 포함 |
| `after/source/`12개 | 운영5·RF044 backend/frontend test2·변하지 않은 frontend 소비5파일. hash를 현재 파일과 대조. 경계의 미변경 Java source는 기준 Git HEAD로 추적 |
| `frontend-first.txt`, `frontend-after.txt`, `frontend-final.txt` | 초안 RF0445+RF02422=27 중26pass1fail(VM realm TypeError prototype assertion). error name으로 보정27/27, 생성8키→업로드 사례 추가 후 RF0446+RF02422=**28/28**. 첫 실패 source 전체 원본 없음; 최종 test/소비 source 보존 |
| `concurrent-first-draft-compile-failure.txt` | RF039 `after/concurrent-rf044-test-compile-failure.txt`를 복사한 출처 있는 공유 로그. RF044 초안 package typo/Role ambiguity/없는 UserDTO.builder 등5 compile 오류, 테스트0. RF044 별도 실행 원본으로 오인하지 않음. 당시 미완성 source snapshot 없음 |
| `before/powershell-property-argument-failure.txt` | 미인용 `-Drf044.before=true`가 PowerShell에서 분리되어 Task `.before=true` not found, 테스트0. 이후 속성 전체 인용으로 보정 |
| `environment.txt`, `preparation-history.txt`, `verification-history.txt` | 실제 JDK release/Node/npm/Gradle wrapper와 prepare·증빙 보완의 성공/실패·자료 보존 한계. PATH java 버전 조회 hang은 해당 실행 세션만 종료했으며 타 Java 중단 없음 |
| `ARTIFACT_SHA256.tsv`, `artifact-check.json`, `preserve-artifacts.cjs` | 최초 캡처본/공유본 hash·치환·출처와 별도 source/안전 점검. helper는 저장자료 정제만 수행하며 테스트 재실행/누락 원본 생성 아님 |

before는 실제 JacksonAutoConfiguration ObjectMapper를 MappingJackson2HttpMessageConverter에 연결하고 handler adapter의 유일 mapper와 동일 인스턴스인지 확인했다. 생성 loaded fixture와 repository echo(null 연관)를 구분했다. after는 before JSON에서 생성의 reporter/user 제거 또는 관리자 양쪽 userId/name/nickname 투영만 허용한 tree와 실제 응답을 동일 비교한다. 날짜는 동일 mapper의 write→read Timestamp JSON으로 비교하므로 LongNode/IntNode 표현 차이를 유출 또는 계약 변화로 오인하지 않는다. 정확8/10/12키·ReportType2키·이미지5키·상세 visible=null과 나머지 값/null·metadata/순서·true PATCH·DELETE 두키·없는 ID NPE를 함께 확인했다.

역할/자격 검증은 POST3역할 성공, 관리자 세 응답에서 신고자/대상/제3자 USER·UNCONFIRMED_USER의18개403 및 저장소0, 네 응답의32개 missing/malformed access·불량/만료/잘못된type refresh401·정상refresh/만료access+refresh 성공·새 access header를 포함한다. RF02435건이 인증→reporter403→state400/저장0·DB identity·principal 예외 및 관리자 제재 흐름을 별도로 회귀한다. 최종 생성 getter 예외 spy와 관리자 허용3 getter 외 모든 User 메서드 예외 대역으로 응답 매핑/직렬화의 접근 경계를 확인했다. PenaltyServiceImpl의 공유 DTO는 기존 두 ID/나머지 null, 상품용 DTO의 `id`는 별도 확인했다.

프런트6건은 실제 API/page/ProductInfo source를 기존 TypeScript로 transpile하고 fetch/React hooks를 대체하여 실행했다. React 서버 렌더가 이름/닉네임·날짜·유형·이미지·처리상태를 표시하고 summary userId가 제재 payload 양쪽 ID로 소비되는지 확인했다. 생성8키 응답의 reportId→FormData 업로드와 공개 상품 reporter.id도 유지했다. null 이름은 렌더되나 null 연관은 기존 두 관리자 페이지에서 TypeError라는 제한을 특성화했다. 실제 브라우저/hook lifecycle·물리 업로드 검증이 아니며 UI 대응 기능을 임의 추가하지 않았다.

환경은 Windows PowerShell·Java17.0.10(release 파일)/기존 Gradle8.7 cache·Node20.10.0/npm10.2.3와 기존 의존성이다. Gradle offline으로 실행하고 새 라이브러리를 설치하지 않았다. 명시 Spring context에는 실제 SecurityConfig/TokenFilter/JWT/AuthService/controller/service/Boot Jackson 및 repository 대역을 등록했다. 로그인 AuthenticationConfiguration/AuthenticationManager도 SecurityConfig 생성용 mock이며 로그인 provider를 검증하지 않았다. component scan/DataSource/트랜잭션 manager/외부 service bean·실제 HTTP 서버를 시작하지 않았다. 외부 대역0은 OS 네트워크 차단 증거가 아니다.

## 실행한 정확한 명령

backend 디렉터리에서 before는 다음 명령을 실행했다. init은 RF044 클래스만 compile하도록 sourceSet을 제한하고 JVM 속성을 test에 전달했다. 최종 after source는 before 모드가 없으므로 과거 source snapshot을 현재 파일로 덮어써서 재현하지 말고 별도 격리 복사본에서만 baseline 재실행을 검토한다.

```powershell
./gradlew.bat --offline --no-daemon -I ../docs/refactoring/evidence/RF-044/selected-tests.init.gradle '-Drf044.before=true' test --tests 'com.ngng.api.report.response.ReportResponseSecurityChainTest' --rerun-tasks
./gradlew.bat --offline --no-daemon test --tests 'com.ngng.api.report.response.ReportResponseSecurityChainTest' --tests 'com.ngng.api.report.creation.ReportCreationSecurityChainTest' --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest' --tests 'com.ngng.api.product.cache.ProductCacheInvalidationTest' --tests 'com.ngng.api.user.point.controller.PointResponseSecurityChainTest' --tests 'com.ngng.api.user.point.controller.PointControllerContractTest' assemble --rerun-tasks
```

frontend 디렉터리에서 최종 실행:

```powershell
node --test tests/rf044-report-response.test.cjs tests/rf024-report-creation.test.cjs
```

실제 명령의 stdout/stderr는 위 console/TAP에 보존했다. 전체 backend test/build·기존 실DB ReportControllerTest는 실행하지 않았다. frontend 운영 변경이 없어 lint/tsc를 추가 실행하지 않았고 production build는 외부 backend fetch 범위 때문에 미실행이다. assemble 성공을 전체 build/test 또는 운영 패키지 실행으로 확대하지 않는다.

## 원본·공유본·점검

최초 캡처본은 gitignored `.local-runtime/refactoring/RF-044/20261111-originals/`에 새 경로로 보존했다. 기존 사용자 `.tmp/`를 열거나 수정·삭제하지 않았다. manifest의 `preserved`/`provenance`/양쪽 hash는 byte 보존과 출처를 구분한다. before JSON의 미치환 tree 파일은 원래 생성하지 않았고 RF039 compile 복사본은 해당 출처 공유본이므로 모든 항목을 독립 미치환 원본이라고 부르지 않는다. source의 합성 fixture/email/테스트 서명키는 재현 근거로 남기고 운영 자격/사용자 자료는 수집하지 않았다.

공유 console/XML에서 home/hostname/compact JWT를 치환했고 XML 판정 집계는 유지했다. `artifact-check.json`에 공유/캡처 hash 불일치0·after source snapshot12개 불일치0·비치환home/JWT/hostname 파일0을 기록했다. Node helper의 XML attribute 집계와 별개로 PowerShell `[xml]` strict parse가8개 성공했다. 출력 검사 범위는 RF044 artifacts이며 저장소 전체 비밀 감사가 아니다. 후일 실행은 새 결과이며 이 원본 또는 최초 분석의 원시 기록 부재를 소급 대체하지 않는다.

## 현재 미검증·추가 방법·재개 조건

| 상태·소유 | 한계/미실행 이유 | 재개 조건과 추가 검증 |
|---|---|---|
| 공통 최종 독립 리뷰: 완료 | 통합 담당이 합의 응답 축소 범위의 P0/P1/P2 차단 결함 없음으로 전달 | before70+2skip/after335/frontend28/assemble·artifact45/source12와 key/null/관리 UI/RF024 보존 확인. 실제 DB/HTTP/OSIV·운영 전체 검증 판정은 아님 |
| RF044 DB/HTTP/브라우저: 미검증 | 실제 JPA 생성/재조회·proxy/lazy 이미지/OSIV·세션·Servlet dispatch·Next rewrite·브라우저 미실행 | RF016 안전 identity/소유fixture·발송차단·정확 cleanup·별도 지시 후 실제 응답/행/UI 비교 |
| 외부/구버전 소비자·관리자 추가 업무·릴리스: 미검증 | 내부 검색/fixture로 운영 소비자 부재를 입증하지 못함, 승인된 필드 제거는 호환 영향 가능 | 소비자·업무 담당 확인, 제거 속성 고지 및 실제 클라이언트 검증을 운영 조건으로 수행 |
| null 연관 UI: 기존 제한 유지 | 두 페이지의 기존 TypeError 재현. null을 가상 사용자로 바꾸면 의미가 바뀜 | 실제 null 데이터/업무 표시 정책을 합의한 별도 UI 후보에서 수정·브라우저 확인 |
| 상품 공개 신고·자유 본문/이미지 개인정보: 범위밖 | 다른 DTO 및 비구조화 입력은 네 응답 요약과 별개 | 공개/익명성·허용필드 정책 후 독립 후보 재개. 모든 개인정보 제거 완료로 확대하지 않음 |
| RF024 대상·이미지 인가/Penalty, RF040 role/폐기: 별도 소유 | 기존 target·인가/수명 정책 변경 없음 | 각 명세/정책/안전 fixture로 별도 검증; 자동 착수 없음 |
| 전체·운영·성능: 미실행/유예 | 안전 통합 환경 미확보, 성능 사용자 유예 | 별도 실행 범위/대표 조건·지시 후 확인. 시간·호출수·응답 축소만으로 성능 개선 주장 없음 |

공통 BACKLOG/LOG 갱신은 통합 담당에게 위 상태·잔여를 전달했으며 이 담당자가 수정하지 않았다. Git commit/push/새 의존성·스키마 변경·실DB/외부 서비스 접속은 수행하지 않았다. 실제 소스는 최종 회귀 후 공동 리뷰를 위해 동결했다.

2026-11-11 공통 최종 독립 리뷰 결과를 통합 담당으로부터 전달받아 명세와 이 README에 반영했다. 이 기록은 독립 검토 결과의 전달 근거이며 새 실행·원시 리뷰 로그가 있다는 주장이 아니다. 실DB/HTTP/OSIV·외부 사용자의 제거 필드 의존·본문/이미지·기존 null 연관 UI 및 성능 미검증은 유지한다. `verification-history.txt`의 리뷰 대기는 당시 실행 시점 상태로 보존했으며 위 최신 판정이 우선한다. 상태 반영에서 source/test·실행 artifact·hash를 변경하거나 테스트를 재실행하지 않았다.
