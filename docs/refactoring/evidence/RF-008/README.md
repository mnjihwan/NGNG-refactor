# RF-008 증빙과 검증 한계

최신 상태: 2026-11-09 합의한 최소 구현·격리 기능 검증·최종 독립 리뷰 완료. 아래 분석 당시 설명은 당시 이력이며 하단 실행 결과를 우선한다. 기준 HEAD `24dda6a7715f505e27e7cf237e1e6e0762b9c06a`; 분석 시작 Git 변경 없음.

## 현재 수행 범위

루트 AGENTS.md, 운영 README, BACKLOG, TEMPLATE을 읽고 PointController→PointHistoryService→Repository/AuthService→User의 호출·직렬화 경로, SecurityConfig/TokenFilter, 기존 PointControllerContractTest, 프런트 포인트 API/타입/목록·충전·결제 소비 코드를 정적으로 확인했다. `rg`/Get-Content/Git 읽기 및 [OWASP 공식 API3 예방 지침](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/) 원문 조회를 수행했다. [명세](../../tasks/RF-008.md)에 사실과 선택 근거·추정을 구분했다.

정적 확인: 네 포인트 응답이 User까지 직접 반환하며, 프런트의 확인된 소비에는 중첩 user가 필요하지 않다. 일반 보안 지침에 근거하여 최상위 8개 기존 포인트 속성만 명시한 전용 DTO를 권고한다. User와 PointHistory 전역 직렬화 설정·인증·서비스 계산 변경은 제외한다.

## 원시 자료와 해석

분석 명령 출력은 대화 도구 기록이며 별도 원시 로그 파일로 보존하지 않았다. 이 README와 명세는 서술 기록이다. 실행 테스트 XML·HTTP payload·성능 원시 자료는 아직 없으며 정적 의심 경로를 운영 유출 재현/통과로 표기하지 않는다. 실제 민감 사용자값이나 토큰은 조회·기록하지 않았다. 후속 실행은 그 시점의 새 실행으로 보존하며 분석 시점의 원시 결과를 대체하지 않는다.

## 미실행 및 재개 조건

| 항목 | 이유/범위 한계 | 추가 검증·재개 조건 |
|---|---|---|
| 전후 직렬화 및 실제 체인 회귀 | 문서 분석 담당 범위; 운영 mapper 적용 결과 미확인 | 가상 User/Role fixture, JacksonAutoConfiguration mapper를 MVC converter에 명시 연결(@EnableWebMvc 기본 mapper와 구분), non-null 날짜 JSON tree 전후 비교와 null 키 포함 여부 검증. 실제 SecurityConfig/TokenFilter/AuthService + mock UserRepository로 3역할 × 4응답, 무토큰/불량토큰 × 4응답 및 사용자 ID 전달 확인. 격리 테스트 완전한 클래스명만 명시 선택 |
| 실제 DB·운영 HTTP·브라우저 | 운영 데이터와 분리된 연동·테스트 계정·환경 미확보 | 외부 연동 격리 및 대상 명시 후 4개 API와 프런트 목록/충전/결제 검증; 외부 클라이언트 user 의존 확인 |
| 독립 리뷰 | 계획 리뷰 차단 결함 없음 보고; 검증 계획 보완. 구현 결과 리뷰는 미실행 | 최종 diff와 전후 XML·로그가 확보된 후 reviewer가 경계·계약·누락 검토 |
| 성능 | 사용자 지시로 유예 | 별도 지시와 동일 조건 전후 기준선 마련 후 측정; 현 결과로 효과 주장 금지 |
| 신고 등 다른 User 응답/typeDetail 표시 개인정보 | 합의한 최소 노출 경계 밖 | 독립 명세·계약 확인 후 착수; RF-008으로 자동 확장하지 않음 |

현재 상태는 계획이며 기능 완료가 아니다. 잔여는 BACKLOG의 R8 항목으로 추적한다.

## 구현 후 보존 자료와 실행 결과

| 경로 | 범위·결과·원문 여부 |
|---|---|
| `before/backend-console.txt`, `before/TEST-*.xml`, `before/test-counts.json` | baseline169/169 통과, fail/error/skip0. 신규 실제체인23+기존 계약28/보안118. TokenFilterTest XML 하나는 아래 설명한 토큰 치환본이며 나머지는 원문 |
| `before/source/`, `before/source-hashes.json` | baseline 당시 controller/계약/신규 체인 실제 소스와 SHA. baseline 소스의 SECURE_RESPONSE=false는 당시 고정 기대이며 최종 테스트에는 이 분기 없음 |
| `before/initial-sandbox-failure.txt` | cache lock 쓰기 거부로 테스트 미실행. 이후 승인된 기존 Gradle 캐시 접근으로 offline 실행 |
| `before/initial-compile-failure.txt` | Role wildcard import 충돌 compile 실패; fixture literal 한 줄은 치환. 당시 전체 소스 snapshot 없음 |
| `before/initial-assertion-failure/` | 최초 실행169건 중13실패. valueToTree LongNode와 실제 HTTP readTree IntNode 비교의 테스트 결함. write→read tree 비교로 보정. XML/console/집계 보존; TokenFilterTest XML은 토큰 치환본. source/reconstructed 파일은 보정 직후 소스에서 비교 메서드 두 줄을 역변경한 재구성본이며 당시 원본 snapshot이 아님 |
| `after/backend-console.txt`, `after/TEST-*.xml`, `after/test-counts.json` | 최종204/204, fail/error/skip0. 신규체인24/계약28/포인트34/보안118. TokenFilterTest XML만 토큰 치환본 |
| `after/frontend-tap.txt` | 최종49/49, fail/skip0. 신규4+RF00431/RF00714. 실제 API/목록 컴포넌트 source와 fetch/hook 대역, 실제 React 서버 렌더 |
| `after/initial-frontend-fixture-failure.txt` | 첫49건 중1실패. default hook export 대역에 __esModule 누락. 보정 후49건 통과; 당시 전체 source snapshot 없음 |
| `after/backend-assemble.txt` | assemble exit0, bootJar/jar 실행; 전체 test/build 통과를 뜻하지 않음 |
| `after/source/`, `after/source-hashes.json` | 최종 운영2/Java test2/frontend test1의 실제 소스 및 원본 경로·SHA. 기존 파일/서비스 경계는 기준 Git 소스로 추적하며 소스 보존과 실제 실행 증빙을 구분 |
| `after/*-version.txt` | 실제 JAVA_HOME Java17, Node/npm 버전. Windows PowerShell·기존 의존성, 신규 설치 없음 |
| `after/redaction-manifest.json`, `after/output-safety-scan.json` | 토큰/가상 민감 fixture literal 치환 파일 목록과 원문/공유 SHA, 공유 XML/console 재검사 결과. 소스의 합성 fixture/테스트 전용 서명 key는 의도적으로 보존 |

실사용 사용자·DB·토큰·자격값을 사용하거나 조회하지 않았다. 기존 TokenFilterTest는 실행 중 합성 JWT를 system-out에 남겼다. 공유 증빙에 토큰을 남기지 않도록 위3 XML의 토큰 문자열과 compile log의 합성 fixture literal을 치환했고, 미치환 원문4개는 gitignored `.local-runtime/refactoring/RF-008/unsanitized/`에 보존했다. 공유 XML의 집계/판정은 변하지 않았으며 공유본을 미치환 원문으로 오인하지 않는다. 새 포인트 테스트는 응답/user/token 원문을 assertion·console에 출력하지 않는다. 안전 검사에서 문자열을 표시하지 않고 파일/건수만 집계했다. 후일 재실행은 새 원시 자료이며 당시 실패/분석 증빙을 대체하지 않는다.

## 실제 실행 명령

backend 디렉터리에서 baseline은 아래 첫 네 클래스만 선택했다. 최종은 다음 일곱 클래스를 각각 완전한 이름으로 지정했다. integration 이름/포괄 wildcard/전체 test는 선택하지 않았다.

```powershell
./gradlew.bat --offline --no-daemon test --tests 'com.ngng.api.user.point.controller.PointResponseSecurityChainTest' --tests 'com.ngng.api.user.point.controller.PointControllerContractTest' --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest' --rerun-tasks
./gradlew.bat --offline --no-daemon test --tests 'com.ngng.api.user.point.controller.PointResponseSecurityChainTest' --tests 'com.ngng.api.user.point.controller.PointControllerContractTest' --tests 'com.ngng.api.user.point.service.PointHistoryBehaviorTest' --tests 'com.ngng.api.user.point.service.PointHistoryPolicyTest' --tests 'com.ngng.api.user.point.repository.PointHistoryRepositoryBatchTest' --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest' --rerun-tasks
./gradlew.bat --offline --no-daemon assemble
```

frontend 디렉터리:

```powershell
node --test tests/rf008-point-response.test.cjs tests/rf007-product-authorization.test.cjs tests/rf004-transaction-request.test.cjs
```

실제 Boot JacksonAutoConfiguration mapper를 명시 MVC converter에 연결하고 RequestMappingHandlerAdapter의 유일 Jackson mapper와 동일 인스턴스임을 검증했다. @EnableWebMvc 기본 mapper를 Boot mapper라고 표기하지 않는다. non-null 나노초 Timestamp fixture를 기존 엔티티 write→read tree와 응답 tree로 비교하여 user 제거만 허용했다. nullable8필드의 키와 null을 함께 검증하며 별도 user getter 예외 spy에서 DTO 매핑·직렬화가 연관에 접근하지 않음을 확인했다. 역할3×응답4, 무토큰/불량토큰×응답4, AuthService의 실제 principal email 재조회·ID 전달, 충전/전체잔액 결제 계산·정확한 문구·잠금→조회→저장 순서, 읽기 무쓰기·null200/빈 목록·순서를 확인했다. 기존 계약 테스트가 validation/금액/오류/check의 범위를 추가로 검증한다.

명시 bean context에는 실제 SecurityConfig/TokenFilter/JWT/AuthService/PointHistoryService/controller와 Boot Jackson만 두고 repository 대역을 사용했다. DB/트랜잭션 manager/Boot scan/외부 발송을 시작하지 않았다. 이는 실제 DB commit/rollback/락 또는 운영 Boot 전체 실행을 증명하지 않는다. RepositoryBatch3건은 기존 offline HQL/위임 검증이며 실제 DB 실행이 아니다.

SecurityConfig 생성용 로그인 AuthenticationConfiguration/AuthenticationManager도 mock이다. 이 포인트 경로는 해당 로그인 manager를 사용하지 않으므로 로그인 인증 성공을 검증했다고 표기하지 않는다. 독립 리뷰 지적 후 테스트 Javadoc을 이 사실에 맞게 수정했다. 실행 후 변경은 설명2줄뿐이며 동작 변경 없이 테스트를 재실행하지 않았다. `after/executed-source/PointResponseSecurityChainTest.java`와 `after/executed-source-hashes.json`은204건 실행 당시 설명을, `after/source/`와 `source-hashes.json`은 보정 후 최종 소스를 보존한다. baseline snapshot의 종전 'only repositories' 설명도 이 한계를 적용해서 읽는다.

2026-11-09 최종 독립 리뷰 완료: 별도 담당자가 운영 DTO/controller 경계와 user 제거 외 계약 유지, 실제 mapper/체인·169/204/49 결과, 문서·증빙·해시 정합 및 실패/치환 이력·한계를 확인했고 합의 범위의 차단 결함을 발견하지 않았다. 합의한 최소 구현·격리 검증·독립 리뷰를 완료한다. 실DB·Boot 전체 HTTP/error dispatch·Servlet OSIV·브라우저/React lifecycle·외부 소비자 계약/운영 설정·다른 writer는 미검증이다. frontend 운영 변경이 없어 lint/tsc는 추가 실행하지 않았으며 production build는 기존 SSG 외부 backend fetch가 있어 외부 비연결 선택 범위에서 미실행이다. 전체 backend test/build도 실행하지 않았다. 후속 검증은 안전한 격리 서비스/fixture/인증·외부 소비자 목록과 사용자 지정 후속 범위에서 새 증빙으로 실행한다. 외부 소비자 user 의존과 릴리스 고지는 R8-통합, 다른 API User/typeDetail 정책은 R8-범위밖, 성능은 R8-성능에서 추적하며 자동 착수하지 않는다. 성능은 사용자 지시로 유예했으며 테스트 시간/응답 축소를 성능 개선 근거로 사용하지 않는다.
