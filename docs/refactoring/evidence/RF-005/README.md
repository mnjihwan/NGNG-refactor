# RF-005 증빙 안내

- 2026-11-05: **승인된 최소 구현·선택 회귀118건·assemble 및 독립 리뷰 완료**. GPT-6 Astra / Medium 독립 리뷰에서 확정 결함을 발견하지 못했고 증빙 22개 해시와 상대 링크를 확인했다. 운영 환경 검증은 아래 한계에 남는다. [명세](../../tasks/RF-005.md), [BACKLOG](../../BACKLOG.md), [로그](../../REFACTORING_LOG.md).
- 기준 HEAD `aa25aaedf783870be1641a90f6c7db7ad08bd021`. 시작부터 RF-004 테스트·문서·public-session-review 증빙의 미커밋 변경이 있었다. RF-004 테스트/명세/증빙 파일은 수정하지 않았으며 BACKLOG/로그는 RF-005 부분만 갱신했다. 시작 byte snapshot은 없어 RF-004 전후 byte 대조는 미확인이다. RF-004 보류·실패·배포 부적합 판정은 그대로다.
- Windows PowerShell, Oracle JDK17.0.10, Gradle wrapper8.7, JJWT0.11.5. [JDK 원시 출력](java-version.txt). 기존 캐시와 의존성만 사용했고 신규 의존성/스키마/운영 설정 변경은 없다.

## 안전 경계와 구현

Get-Content/rg로 지침·작업명세·호출 관계를 확인한 뒤, 실제 verifier/provider와 합성 테스트 키를 사용했다. 직접 필터 테스트는 mock servlet request/response/chain을 사용한다. 체인 테스트는 AnnotationConfigWebApplicationContext + EnableWebMvc/EnableWebSecurity에 **실제 SecurityConfig**, 실제 JWT provider/verifier와 logout handlers, 대역 AuthenticationConfiguration/AuthenticationManager와 probe controller만 명시 등록한다. Boot/NGNG application scan, application.yml, DB·Redis·ES·메일·SMS·S3 bean, HTTP 서버를 만들지 않는다. 테스트용 JWT 만료 property만 명시한다. 외부 서비스·DB 연결·성능 테스트는 실행하지 않았다.

운영 변경은 TokenFilter와 JwtTokenVerifier 2개다. 누락/빈 access 또는 서명·type·필수 claim이 유효하지만 만료된 access만 refresh fallback한다. 정확 Bearer access/raw refresh·서명·type 및 문자열 subject/role(공백만인 값 거부)·숫자 exp를 검사한다. 그 밖의 입력 거부는 빈본문401, chain0, 발급 헤더·cookie 없음이다. 정상 access의 우선순위/권한, 첫 refresh cookie 선택, 같은 요청 계속 처리, refresh 기반 principal/authority와 Authorization 재발급을 유지한다. matcher/권한표·CORS/CSRF·쿠키/발급기·로그인/로그아웃·frontend/chat-server는 변경하지 않았다. role whitelist·DB 재조회·폐기/회전은 추가하지 않았다.

## 실제 명령과 공유 결과

공유 XML·console은 hostname과 Windows 사용자 경로를 비식별화한 사본이며 원시 파일 자체가 아니다. 원본과 원본 해시 목록은 이 작업 환경의 gitignored `.local-runtime/rf005-originals-20261105/RF-005/`에 보존했다. 공유 사본의 해시는 아래 SHA256 목록에 기록하며 이 디렉터리의 Git 줄바꿈 변환을 비활성화해 바이트를 유지한다. 원본은 저장소에 포함되지 않으므로 다른 환경에서 사용할 수 있다는 보장은 없다. 나머지 내용과 테스트 결과는 변경하지 않았다.

아래 명령은 NGNG-backend에서 실행했다. 모든 선택 test에 `--rerun-tasks`를 사용해 5 tasks가 실제 실행됐고 compileTestJava는 전체 테스트 소스(RF-004 미커밋 소스 포함)를 컴파일했다. RF-004/기존 통합 테스트를 실행한 것은 아니다.

```powershell
# before: 각 실행 당시 아래 archived source에 대응
.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.global.security.TokenFilterTest'
.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.global.security.SecurityChainTest'

# after: 최초 실패 후 같은 선택 명령으로 보정 재실행
.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest'
.\gradlew.bat --offline --no-daemon assemble
```

각 콘솔은 PowerShell `*>`로 증빙 경로에 저장하고 `$LASTEXITCODE`를 반환했다. 최초 Gradle wrapper 캐시 `.lck` 쓰기는 sandbox 권한으로 거부됐으며 tool output에만 남아 있다. 동일한 오프라인 명령의 캐시 접근 자동승인 뒤 아래 실행을 진행했다. 이 해결된 거부를 테스트 실패로 세지 않는다. 저장소 `*.log` 제외 규칙 때문에 공유 콘솔 사본은 `.txt`다.

| 실행 | 관측 | 보존 근거 |
|---|---|---|
| 변경 전 직접 필터 | 8/8통과, fail/error/skip0, exit0 | [XML](before/TEST-com.ngng.api.global.security.TokenFilterTest.xml), [console](before/console.txt), [테스트 snapshot](before/TokenFilterTest.java.txt) |
| 최초 체인 harness | 테스트용 @Value 만료 property 누락으로 초기화1실패, exit1. 외부 서비스 생성/연결 없음 | [XML](before/chain-harness-initial-failure.xml), [console](before/chain-harness-initial-failure.txt) |
| 보정 후 변경 전 체인 | 32/32통과, fail/error/skip0, exit0 | [XML](before/TEST-com.ngng.api.global.security.SecurityChainTest.xml), [console](before/chain-console.txt), [테스트 snapshot](before/SecurityChainTest.java.txt) |
| 최초 after | 116건113통과/3실패/error0/skip0, exit1. exp 배열의 JJWT IllegalStateException 2건·MVC OPTIONS handler 호출수 기대값1건 | [직접 XML](after-initial/TEST-com.ngng.api.global.security.TokenFilterTest.xml), [체인 XML](after-initial/TEST-com.ngng.api.global.security.SecurityChainTest.xml), [console](after-initial/console.txt) |
| 최종 after | 직접72 + 실제 체인46 = **118/118통과, fail/error/skip0, exit0** | [직접 XML](after/TEST-com.ngng.api.global.security.TokenFilterTest.xml), [체인 XML](after/TEST-com.ngng.api.global.security.SecurityChainTest.xml), [console](after/console.txt) |
| assemble | exit0, bootJar/jar/assemble 등3 tasks executed, compileJava/processResources2 UP-TO-DATE | [console](assemble-console.txt) |

JJWT는 exp 배열 변환에서 IllegalStateException을 던져 최초 after 2건이 실패했다. raw JWT 파서 안에서만 이를 JwtException으로 변환해 인증 거부로 처리했다. downstream chain은 catch 밖이므로 업무 예외를401로 숨기지 않는다. OPTIONS는 실제200이었으나 MVC 기본 OPTIONS handler가 probe 본문을 호출하지 않으므로 기대 호출수를0으로 수정했다. 최초 실패는 최종 통과로 소급 대체하지 않았다.

before에는 운영 [TokenFilter](before/TokenFilter.java.txt)·[verifier](before/JwtTokenVerifier.java.txt) 원본, after에는 [필터](after/TokenFilter.java.txt)·[verifier](after/JwtTokenVerifier.java.txt)·[직접 테스트](after/TokenFilterTest.java.txt)·[체인 테스트](after/SecurityChainTest.java.txt) snapshot을 보존했다. [SHA256 목록](SHA256SUMS.txt)은 snapshot/XML/console/JDK 출력에 대응한다. 초기 실패 당시 전체 소스 snapshot은 별도 보존하지 않았고 비식별화한 XML·console 사본과 위 보정 서술만 남겼다. 최초 정적 분석의 원시 읽기 출력은 대화 tool 결과뿐이며 이 새 실행이 최초 분석의 소급 원시 증빙은 아니다. 현재 소스로 before 명령을 다시 실행하면 after 결과이므로 before 재현에는 격리 복사본에서 해당 archived baseline 소스를 사용해야 한다. 실제 키·사용자 토큰은 저장하지 않았다.

## 확인한 범위

- before mock servlet에서 헤더 누락 NPE·다른 cookie만 있을 때 NoSuchElementException, malformed/exp 부재 예외, raw refresh 헤더 및 access cookie 교차 타입 수용을 관측했다. 임의 위조 JWT 인증 우회·Boot 실제500 재현을 뜻하지 않는다.
- after 직접 필터72건: 정상 access의 쿠키 없음/정상/잘못된 refresh 우선순위, 누락/빈 access 및 만료 access 재발급·type/subject/role/서명, cookies null/빈/다른이름/중복의 첫 값 선택, 접두사·공백·undefined·형식·서명변조/다른키(정상/만료)·교차/누락/unknown type·subject/role/exp 누락·빈값·자료형 오류, 기존 role 값 비확장/비축소, downstream JwtException의 동일 객체 전파와 재발급 헤더 경계를 확인했다. 거부시 빈본문401·chain0·인증없음·새 Authorization/Set-Cookie/WWW-Authenticate 없음이다.
- 실제 체인46건: ADMIN/USER/UNCONFIRMED_USER × admin/chat/상품POST·PUT·DELETE/my_page/confirm 21개 역할표, 공개 skip9개(상품 GET·유사 products prefix·main/join 하위 등), logout204·삭제 cookie, refresh 후 권한부족 빈본문403과 이미 설정된 Authorization 유지, root/unknown/main 유사prefix/일반OPTIONS의 비skip401 및 유효 인증 통과, invalid access+refresh 거부, 누락 access 재발급, 연속 요청 인증 컨텍스트 정리, 기존 CORS preflight를 확인했다. `SecurityConfig` 자체 diff는 없다. probe 경로는 판별용 대역이라 unknown 경로200을 운영404 계약으로 해석하지 않는다.
- git diff --check 통과. 부모의 독립 코드 리뷰 1차에서 확정 결함 지적이 없었고 최종 XML·snapshot 해시 일치를 확인했다. 최종 완료 판정은 부모가 기록한다.

## 한계·미실행·추가 검증 조건

| 항목 | 미실행 이유·현재 한계 | 추가 검증·재개 조건 |
|---|---|---|
| Boot/컨테이너 실제 HTTP 및 async/error dispatch | mock servlet·최소 MVC context만 실행. 실제 오류본문/dispatch/실제 업무 controller/service는 미검증 | R5-연동: 별도 지시와 외부연동 차단 서버·테스트 인증 확보 후 실제 dispatch/업무 경로 확인 |
| 브라우저·프록시·cookie 전송·frontend 재발급 소비 | 클라이언트 정적 영향 조사뿐, E2E/배포 경계 없음 | R5-연동: 안전 배포 환경에서 정상/만료/실패·다음 요청·동시 응답 검사 |
| 정확 exp==현재 시각·clock skew, 특이 alg·nbf/iat 자료형 | 넉넉한 ±1시간 exp·실제 JJWT/HMAC 테스트키만 사용. 모든 JWT 표준/자료형 조합의 검증 아님 | R5-격리: 필요성/범위 합의 후 통제 clock과 추가 합성 fixture로 검증, 성능 작업은 별도 확인 |
| 전체 test/build·실DB·외부서비스 | 기존 통합 테스트는 DB/외부 연결·DDL 의존. 선택 회귀와 assemble만 실행 | R5-격리/연동: 별도 지시와 안전 격리 suite·대역·fixture가 확보된 뒤 관련 묶음 검증 |
| 운영·성능·동시 브라우저 재발급 | 기능 경계 작업이며 실행 요청/안전 환경 없음. task/XML 시간은 성능 효과가 아님 | 별도 사용자 지시와 대표 환경·데이터·비교 계획 선행 |
| 공개 matcher·권한 정책·폐기/회전·역할 최신화·쿠키/CSRF/CORS·타 소유권 | 승인 정책 밖. 공개 matcher/skip 불일치는 유지하고 특성만 기록 | R5-범위: 별도 정책·명세 합의 전 자동 착수 금지. RF-004 보류 해제 아님 |

후일 추가 검증은 이 원시 결과와 구분해 새 실행으로 기록하고 명세·증빙·백로그·로그 상태를 함께 갱신한다. 합의한 인증 입력/타입 수정과 실제 체인의 기존 인가표 회귀를 운영 전체 인증·인가 검증으로 확대하지 않는다.
