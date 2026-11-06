# RF-007 증빙

최신 상태(2026-11-06): 합의한 최소 구현·before/after 선택 검증 및 backend assemble·독립 리뷰 완료. 아래 분석 당시 미실행 기록은 이력이며 후속 실행을 소급하지 않는다. [명세](../../tasks/RF-007.md)의 결과·한계와 함께 확인한다.

- 기준 HEAD `56d3ec21a72918dd5e6c42c018d12911f13a3345`, 시작 Git 상태 clean. 루트 지침/README/BACKLOG/TEMPLATE, RF006 선행 보안 체인 방식, 상품 controller/service/entity/DTO, SecurityConfig/AuthService/CustomUserDetails, 관리자·my_page frontend 호출을 정적으로 확인했다.
- OWASP API1:2023 및 Authorization Cheat Sheet를 직접 조회했다. 공식 원칙은 객체별 인가 필요성을 뒷받침하고 구체적인 역할표는 프로젝트 흐름을 근거로 추론했다. 관리자 전체 관리 유지·USER 소유자 제한을 권고한다.
- 수행 범위는 읽기·분석 및 문서 작성뿐이다. 소스·설정 수정/테스트/DB/외부 서비스 호출/성능 측정 없음. 웹의 공식 문서 조회는 수행했다. 원시 분석 출력은 대화 도구 결과이며 별도 원시 파일/XML은 보존하지 않았다. Git 기준 소스 조회 가능성과 실행 증빙 보존은 구분한다.
- 역할×소유권 실제 체인, service 무쓰기, cache proxy, frontend 실패 처리, 선택 회귀·assemble 및 독립 리뷰는 아직 미실행이다. 명세의 합성 fixture/명령을 구현 단계에서 수행하고 before/after 소스·XML·console·hash를 보존한다. 최초 실패도 별도로 남기며 후일 재실행으로 대체하지 않는다.
- 실DB/Redis·Boot HTTP·브라우저·운영 writer/동시성은 미검증이다. 안전 격리 환경·fixture·외부 연결 차단과 별도 후속 범위에서 재개한다. mock 필드 변경과 MockMvc 응답은 commit/실제 HTTP 근거가 아니다.
- 성능은 사용자 지시로 별도 유예이며 측정/개선 주장이 없다. 다른 상품 endpoint 인가·삭제 사업조건·캐시 개선은 자동 착수하지 않는다. BACKLOG의 R7-격리/연동/범위/성능에서 추적한다.
- 독립 계획 리뷰 보완: AuthService의 principal 강제 캐스팅 위험에 따라 공통 검사에서 null/unauthenticated/지원하지 않는 principal·내부 user/email 누락을 ADMIN 우회와 AuthService 호출 전에 거부하는 계획을 추가했다. 직접 서비스 호출 회귀와 실제 필터 체인의401/403 회귀를 구분해 명세에 반영했다. 테스트는 아직 미실행이며 R7-격리에서 검증한다.
- 프런트 계획 리뷰 보완: my_page 삭제 호출자가 Promise를 기다리지 않으므로 hook 내부 catch/알림/목록 유지 및 미처리 rejection 방지 검증을 추가했다. 기존 모달 닫힘은 유지한다. 관리자 DELETE 응답 무검사는 별도 후보이며 이번 기능 검증 완료로 기록하지 않는다.

## 보존한 실행 자료

| 위치 | 실제 범위·결과 |
|---|---|
| `before/` | security chain26/26 XML·backend console, frontend11/11 TAP, 실행당시 service/fixture/chain/프런트 source와 SHA256. `RF007_BEFORE=true`로 기존 허용·오류 처리 결함을 특성화한 별도 소스다. 최종 테스트에는 환경에 따른 before 분기가 없다 |
| `before/initial-sandbox-failure.txt` | Gradle cache lock 쓰기 거부, 테스트 미실행. 이후 기존 cache 쓰기 권한으로 offline 실행 성공 |
| `after/initial-test-compile-failure.txt` | 테스트 static import `request()` 충돌 compile 실패. 명시 qualifier로 보정 |
| `after/fixture-failure/`·`after/first-run-counts.json` | 최초 실행293건/59실패. 새 인증 fixture가 `setAuthenticated(true)` 재호출로 service48건 실패. 별개로 잘못 선택한 기존 Boot11건(Details8·Request3)은 datasource UnknownHost/context 초기화 실패. RF007 실패 XML과 suite별 집계만 공유. 앞선 static import compile 실패와도 별개 |
| `.local-runtime/refactoring/RF-007/after-first-run/` (gitignored) | 위 실패 실행의 원본 console/XML 보존. Boot 설정·고정 이메일 등의 공개 위험 때문에 공유 증빙에서 제외. 실제 DB 연결 성공·fixture 쓰기 없음. 실패당시 전체 source snapshot은 없으며 최신 source/hash로 소급 증명하지 않는다 |
| `after/intermediate/` | fixture 보정 후 offline282/282 XML·console. 최종 anonymous custom principal2건은 이 실행 이후 추가 |
| `after/*.xml`, `after/test-counts.json`, `after/backend-console.txt` | 최종 offline284/284 fail/error/skip0. RF00786(service50/cache4/chain32), RF005118, RF00637, RF00443. rerun으로5 task 실제 실행 |
| `after/frontend-tap.txt`, `after/frontend-tap-final.txt` | 최초 after43/43 및 최종45/45. 최종 RF00714+RF00431. 최종은 204/403/network의 unawaited DELETE도 포함 |
| `after/frontend-lint.txt`, `after/frontend-tsc.txt` | lint exit0·기존 img 경고3, tsc exit0은 실행 도구의 `exit_code:0` 반환이 근거다. 빈 tsc 출력 파일만으로 성공을 판정하지 않는다. 리뷰 후 같은 명령을 별도로 재실행해 `frontend-tsc-confirmation.txt`에 출력과 shell exitCode=0을 보존했다 |
| `after/backend-assemble.txt` | assemble exit0, compile/process UP-TO-DATE, resolveMainClassName/bootJar/jar 실행 |
| `after/source/`, `after/source-hashes.json` | 최종 운영5파일·RF007 Java4파일·frontend test source와 SHA256. 소스 보존과 실제 실행 결과는 별개이므로 XML/TAP·console을 함께 참조 |
| `after/java-version.txt`, `after/node-version.txt`, `after/npm-version.txt` | 실제 JAVA_HOME 실행기·Node/npm 버전. Windows PowerShell, 기존 설치 의존성 사용, 추가 설치 없음 |

잘못 선택한 Boot11건은 `TransactionRequestServiceTest`/`TransactionDetailsServiceTest`의 기존 `@SpringBootTest` 초기화 실패다. 전체 integration 실패를 신규 서비스 결함이나 통과로 계산하지 않는다. 실제 DB 격리·fixture 승인 없이 이 둘을 다시 실행하지 않았다. 기존 테스트는 그대로 두고 offline 선택 명령만 보정했다. 원시 실패는 보존하며 최종 성공으로 덮어쓰지 않았다.

## 실행 명령

backend 디렉터리, before 환경만 `RF007_BEFORE=true` 후 실행·해제:

```powershell
./gradlew.bat --offline --no-daemon test --tests 'com.ngng.api.product.authorization.*' --rerun-tasks
```

최종 backend 선택(실DB 필요 클래스 제외)·패키징:

```powershell
./gradlew.bat --offline --no-daemon test --tests 'com.ngng.api.product.authorization.*' --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest' --tests 'com.ngng.api.transaction.status.*' --tests 'com.ngng.api.transaction.service.TransactionCreationContractTest' --tests 'com.ngng.api.transaction.service.TransactionAcceptancePolicyTest' --rerun-tasks
./gradlew.bat --offline --no-daemon assemble
```

frontend 디렉터리:

```powershell
# before 소스에서는 RF007_BEFORE=true로 첫 파일만 실행했고 이후 환경을 해제했다.
node --test tests/rf007-product-authorization.test.cjs tests/rf004-transaction-request.test.cjs
npm run lint
./node_modules/.bin/tsc.cmd --noEmit --incremental false
```

## 검증 내용과 한계

실제 SecurityConfig/TokenFilter/JWT/controller/service/AuthService로 역할×소유권·401/403·refresh fallback/갱신 header·200 DTO/ID·204·400을 확인했다. repository/TagService·무관 controller 의존성 대역과 명시 bean context만 사용하며 Boot scan/DB/트랜잭션 manager는 만들지 않는다. JWT principal에 DB ID가 없는 상태에서 email 재조회를 확인한다. 직접 서비스50건은 null/잘못된 인증/익명 토큰·ADMIN guard와 ID 비교·무쓰기·기존 수정/삭제 OR·없는 상품 예외 및 filter 없는 MVC403을 확인한다. 캐시4건은 실제 Spring cache proxy·in-memory manager에서 성공 PUT 갱신·거부 PUT/DELETE 보존·기존 성공 DELETE 미갱신을 확인한다. 객체403 body는 선택 MockMvc에서 빈 문자열이었다.

프런트는 실제 API·수정 컴포넌트 handler·삭제 hook을 transpile해 fetch/React hook 대역과 설치된 SWR mutate/cache로 검사한다. 실패403/401/500·network에서 이동/목록 제거 방지·알림, DELETE204도 실패 처리, Promise를 기다리지 않는 삭제 호출의 rejection 소비를 확인했다. 브라우저/실제 React 렌더 lifecycle/실HTTP·프록시·모달 동작 전체를 검증한 것은 아니다. 모달 닫힘·관리자 UI/기존 경로는 변경하지 않았다.

실DB dirty checking·commit/rollback/거래경합·실Redis 직렬화/실패·Boot HTTP/error dispatch/Servlet OSIV·브라우저·운영 writer/전체 보안은 미검증이다. frontend production build는 기존 SSG가 BACKEND_URL/고정 상품·사용자를 fetch하므로 현재 외부 비연결 선택 범위에서 미실행이다. 전체 backend test/build도 통과로 주장하지 않는다. 안전한 격리 backend/Redis/정적 fixture·테스트 인증·외부 차단과 승인 범위를 확인한 후 별도 실행으로 재개한다. 성능은 사용자 지시로 유예하며 측정/개선 주장이 없다.

다른 상품 endpoint 인가·DELETE OR 정책·일반 캐시/거래경합·관리자 DELETE 실패 UI는 별도 후보다. BACKLOG R7-격리(합의 범위 완료)/연동/범위/성능에서 현재 상태·재개 조건을 추적한다. 새 실행은 당시 source와 XML/TAP/환경을 새 경로에 보존하며 이 before/실패 결과를 대체하지 않는다.

2026-11-06 최종 독립 리뷰: 별도 담당자가 운영 코드/guard/무쓰기·캐시/프런트와 최종 backend284/frontend45·manifest·실패 이력·한계를 확인했고 합의 범위의 수정 필요 기능 결함을 발견하지 않았다. 실패 원인 분리·tsc exit 근거와 후속 확인 실행을 명세/증빙에 보완했다. 최종 운영/테스트 source hash 불일치0, 이 README의 깨진 상대 링크0, `git diff --check` exit0을 확인했다. 합의된 최소 구현·선택 검증·독립 리뷰 완료이며 실DB·실HTTP·브라우저·성능 완료는 아니다.
