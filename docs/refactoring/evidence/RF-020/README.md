# RF-020 증빙: 계좌 확인 객체 인가

- 기록일: 2026-11-10. 최신 상태: 합의된 최소 구현·격리 검증(before14/after317, assemble0)·독립 최종 리뷰 완료, 범위 내 차단 결함 없음. 아래 계획 시점 미실행 설명은 이력이며 마지막 실행 절을 우선한다. 실제 DB는 계속 미실행이다.
- [작업 명세](../../tasks/RF-020.md), [백로그](../../BACKLOG.md), [재평가](../../BACKLOG_REASSESSMENT_2026-11-10.md).
- 기준 HEAD `e417eaf7d476a639413414ff8f4423b89cc88e66`, 분석 시작 Git 상태 clean. 루트 AGENTS/README/TEMPLATE 및 RF-007 선례 준수. 이후 다른 RF의 병렬 문서 변경은 이 작업 변경이 아니다.

## 수행 범위와 근거

`git status --short`, `git rev-parse HEAD`, `rg --files -g AGENTS.md`, `rg -n`, `Get-Content`로 ConfirmController/ConfirmService, DTO/User/repositories/AuthService, SecurityConfig/TokenFilter/JwtTokenProvider/CustomUserDetails/response wrapper, 기존 UserControllerTest와 ProductAuthorizationSecurityChainTest, frontend ConfirmAccount/useMypageSWR/api/auth-header 및 Next rewrite를 읽었다. `git diff --check`로 작성 문서 공백 오류를 확인한다. 테스트 실행 명령은 실행하지 않았다.

확인된 경계: 실제 필터는 계좌 경로 인증을 요구하고 UNCONFIRMED_USER 역할로 제한한다. service는 body ID의 계좌·role을 바꾸고 그 대상 email/role의 access/refresh를 헤더로 전파한다. controller 성공 body는 비어 있다. principal에는 DB ID가 없으므로 email→DB user 해석이 필요하다. frontend는 응답 Authorization을 저장한다. refresh fallback은 거부 전 호출자 access를 발급할 수 있으므로 대상 발급과 구분해야 한다.

공식 근거는 [OWASP API1:2023](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/)이며 2026-11-10 웹 조회했다. 서버 객체별 작업 권한 검사 및 테스트 권고를 좁은 본인 계좌 등록 정책에 적용한다. 관리자 우회/실제 운영 피해를 이 권고로 추정 확정하지 않는다.

## 보존과 해석의 한계

- 원시 shell/web 출력은 대화에만 있으며 별도 로그·웹 snapshot을 보존하지 않았다. 영구 근거는 기준 commit의 소스와 위 분석 명세다.
- XML/실행 로그/DB snapshot/브라우저 캡처는 생성하지 않았다. 성공 테스트 수는 없다. 정적 호출 확인은 실제 취약점 재현, 전체 감사, 실DB 무쓰기, commit/rollback, 쿠키 수락, 운영 악용 확인이 아니다.
- UserControllerTest의 addFilters=false 및 고정 DB 조건은 이번 인가 기준선을 대체하지 않는다. 기존 RF-007 실행 결과도 RF-020 실행 결과가 아니다.
- 현재 명세의 예상 204/400/401/403 및 token assertions는 계획이다. 미래 실행은 날짜·환경·fixture·실제 실행/skip/UP-TO-DATE·실패·정리 결과를 별도 기록하고 이번 미검증을 소급 통과로 바꾸지 않는다.

## 미실행 항목과 재개 조건

| 항목 | 미실행 사유 | 방법·재개 조건 | 추적 |
|---|---|---|---|
| 변경 전/후 실제 JWT/filter/controller/service 격리 재현 | 분석·계획만 위임, 테스트 금지 | 명세 확정 후 명시 bean context/합성 JWT/두 사용자 대역, before/after 소스·XML·로그 보존 | RF-020 |
| 본인 성공·타인/누락 ID 거부·역할·cookie·fallback·직접 호출 회귀 | 테스트 코드 미작성 | 명세 표에 따라 토큰 subject/type/role, 204 빈 body 및 무상태변경 검사 | RF-020 |
| JPA commit/rollback·동시 등록·HTTP | DB 접속 금지 및 격리 미구축 | RF-016 최소 안전 기반 후 소유 fixture/key 정리 포함 검증; 중복 동시성은 별도 범위 | RF-020 통합 잔여 |
| 브라우저·proxy·refresh 수락·SWR 실패 UI | 환경 미실행 | 정상/403/fallback의 헤더·storage·화면 관찰, 필요 변경은 좁은 범위 합의 | RF-020 연동 잔여 |
| 독립 리뷰 | 계획 작성 단계 | 통합 담당/독립 담당 문서·정책 검토, 구현 뒤 코드 및 raw 검토 | RF-020 |
| 토큰 폐기/역할 최신화 및 로그 PII | 객체 인가 범위 밖 | 각 정책/환경·수정 범위 확정 후 별도 작업 | RF-040/RF-033 |

본 작업 파일은 명세와 이 README뿐이다. 공통 BACKLOG/REFACTORING_LOG 상태·잔여 연결은 통합 담당자가 갱신한다. 신규 라이브러리/스키마/역할 확대/commit/push는 없다.

## 2026-11-10 1A 승인 후 실제 실행

Windows11·Oracle Java17.0.10·Gradle8.7, 기존 Gradle cache에 대해 sandbox escalation 승인 후 `--offline`으로 실행했다. 새 의존성/네트워크 해석 없이 명시 context의 실제 SecurityConfig/TokenFilter/JWT/controller/ConfirmService/AuthService를 사용한다. UserRepository/UserRoleRepository 및 사용되지 않는 로그인 AuthenticationConfiguration/AuthenticationManager만 대역이다. provider는 실제 JWT provider의 spy이며 합성 전용 key만 사용한다. 외부 DB/Redis/SMTP/SMS/Boot scan/서버 기동은 없다. 신규 direct service22건은 실제 AuthService와 repository/token 대역이다.

| 실행 | 실제 결과 | 보존 근거 |
|---|---|---|
| before | 계좌 실제 chain14건 통과, fail/error/skip0 | `before/` 운영 소스·당시 테스트·원시 XML, `before-console.txt` |
| after 최초 | 170건 중 fixture1실패, 나머지169통과 | `after-functional-failed/` 원시 XML4개·당시 chain 소스, `after-functional-failed-console.txt` |
| after 기능+인증 | 신규52 + 기존118 =170건 통과, fail/error/skip0 | `after-functional/` 원시 XML4개, `after-functional-console.txt` |
| after 최종 선택 회귀 | 신규52 + 기존265 =317건 통과, fail/error/skip0 | `after-final/` 원시 XML10개, `after-final-console.txt`, `test-summary.csv` |
| assemble | exit0, 패키징3개 실행·컴파일/리소스2개 up-to-date | `assemble-console.txt` |

before14는 본인·타인 성공2 + USER/ADMIN×본인/타인 거부4 + 필수 cookie1 + 잘못된 인증5 + 본인 계좌 기등록/중복400 두 건이다. 타인 성공은 그 대상 상태 USER 및 JWT subject/type/role·refresh 속성을 확인한 실제 격리 재현이다. after는 같은 기본 경계를 보존하며 불일치를403으로 바꾸고 추가 malformed caller/null/refresh 경계를 넓혔다. before/after 테스트 전체가 완전히 동일한52건이라는 뜻이 아니다.

after52의 확인 범위:

- 실제 chain30건: 본인204 빈 body·bank/number/USER 및 본인 subject/USER access+refresh·쿠키 path/domain/HttpOnly/Secure, 타인/미존재/null/missing caller/ID null7종403, role4·필수 cookie1·인증5·본인400 두 건, access 없음/만료×본인/타인 fallback4건, fallback 본인400 두 건, 유효 access와 타인/형식오류 존재 refresh×본인/타인4건.
- direct22건: authentication 없음/unauthenticated/anonymous×2/null principal/string/null user/null·blank email/USER/ADMIN/authority mismatch12종403, 타인/미존재/null 요청/missing caller/ID null/both-null6종403, 캐시 밖 동값의 별도 Long 객체 통과 및 기존 호출 순서1건, 내부 principal role null+명시 authentication authority1건, 정상 일치 ID target 누락의 기존 예외1건, DB 조회 실패를403으로 삼키지 않는1건.
- 유효 access 거부는 caller 조회 외 UserRepository/role/token 무호출 및 caller/target 상태 불변을 함께 확인한다. fallback 거부는 필터가 발급한 caller/UNCONFIRMED_USER access만 허용하며 새 refresh 없음·계좌 service 발급 무도달을 확인한다. JWT principal에는 DB ID가 없음을 fixture Answer에서 확인한다. `save` 미호출만으로 dirty checking의 무쓰기를 추론하지 않는다.
- 최종 기존265건: TokenFilter72+SecurityChain46+상품 인가86(service50/chain32/cache4)+거래 소유권37(unit24/chain13)+포인트 응답 실제 chain24. 기존 Boot/고정 DB `UserControllerTest`는 실행하지 않았다.

### 실패 이력과 원시 보존

최초 wrapper lock 접근 거부는 test 미기동이며 shell 출력은 대화에만 있다. 같은 호출의 잘못된 상대 경로 보존은 실패하고 backend 아래 빈 `docs/.../before`만 생성되어 내용을 확인한 후 빈 디렉터리 단위로 정리했다. source/evidence 유실은 없다. 그 뒤 실제 baseline compile의 Role wildcard 충돌2오류 및 checked exception1오류를 순서대로 수정했다. console은 `before-compile-failed-console.txt`, `before-compile-second-failed-console.txt`로 보존하고 해당 compile 실패 소스는 별도 보존하지 않았다.

최초 after fixture1실패는 `when(users.findUserByEmail(...)).thenReturn(empty)`가 기존 Answer를 재설정 중 실행하여 인증 없는 test setup에서 NPE를 낸 것이다. 운영 guard 실패가 아니다. `doReturn`으로 fixture를 수정한170/317건을 별도 새 실행으로 보존한다. 실패 XML/console을 성공으로 덮어쓰지 않는다.

원시 XML은 그대로 보존하며 테스트가 사용하는 synthetic email/key/bank/account뿐이다. 기존 TokenFilterTest의 parameterized testcase 이름에는 합성 전용 key로 만든 JWT 원문이 포함된다. 실제 사용자/운영 자격값·운영 토큰은 없으며 비식별화로 원시를 치환하지 않았다. XML/console의 SHA256은 `raw-manifest.csv`에 있다. `after-source/`에는 최종 수정3개를 포함한 관련 소스 총11개를 상대 경로 구조로 복사하며 SHA256을 `source-manifest.csv`에 기록한다. 소스 snapshot은 테스트 증빙의 해석용이며 빌드/운영 배포 artifact가 아니다. 향후 재실행은 현재 결과와 별개다.

### 실행 한계와 재개 조건

repository 대역 상태/호출 관찰은 JPA commit/rollback·물리 SQL 무쓰기/락/경쟁 검증이 아니다. 실제 HTTP/error dispatch/OSIV/전체 Boot context, Next rewrite·브라우저 쿠키 수락·SWR403 UI는 미검증이다. RF016 안전 격리·소유 fixture·외부 차단 및 별도 후속 지시 후 재개한다. frontend production build/전체 backend test/build는 고정 데이터/외부 연동 위험으로 실행하지 않았고 assemble은 이를 대체하지 않는다.

토큰 폐기/최신 DB role/탈퇴·은행 실소유 인증·중복 계좌 동시성/스키마·배포 cookie/CORS·계좌 로그 PII·주소/탈퇴 인가는 범위 밖이며 RF040/RF033/RF041/RF042 및 별도 정책으로 추적한다. 성능은 미측정·유예이고 실행 시간으로 개선을 주장하지 않는다. 독립 계획 리뷰를 반영했으며 최종 리뷰에서 guard·fallback·상태/호출·diff, 명세/증빙/hash 및317건 정합을 확인했고 합의 범위 내 차단 결함은 없었다. 리뷰 문구 정정은 snapshot 수정3개를 포함한 총11개라는 수량 설명뿐이며 운영/테스트 소스를 바꾸지 않았다. 합성 JWT 원시의 Git 공유 여부는 Git 담당자에게 별도 점검을 전달한다. 통합 담당자가 BACKLOG R20-통합/R20-연동/R20-범위/R20-성능을 갱신한다. 자동 후속 착수·commit/push는 없다.

### 공유 증빙 경로 치환 (2026-11-10)

두 compile 실패 console의 로컬 사용자 홈 절대경로만 중립 `<WORKSPACE>`로 치환했다 (각 2회·1회). 실행 원문은 Git 제외 `.local-runtime/rf020-raw/`에 보존하며 공유본과 원본 SHA256·치환 수는 `redaction-manifest.csv`에 기록한다. `raw-manifest.csv`의 해당 두 해시는 공유본 기준이다. 나머지 XML/console은 원문 그대로이며 합성 JWT는 fixture의 테스트 전용 키·주체다. 원본 보존은 현 로컬 환경에 한정되고 후일 재실행은 별도 결과다.
