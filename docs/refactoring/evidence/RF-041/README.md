# RF-041 증빙: 주소 객체 인가

- 기록일: 2026-11-10. 현재 상태: 구현·격리 선택247건 및 assemble 완료, 독립 최종 리뷰 완료. 아래 최초 정적 분석 내용은 과거 계획 기록이며 마지막 실행 기록이 우선한다.
- [작업 명세](../../tasks/RF-041.md)에 실제 호출 관계, 정책 선택, guard 순서, 회귀 매트릭스 및 남은 검증을 기록했다.
- 기준 HEAD: `e417eaf7d476a639413414ff8f4423b89cc88e66`. 시작 status에 RF-020/021/022 명세·evidence가 untracked였다. ConfirmService는 읽을 당시 기준 커밋 상태였으나 RF-020이 병행 구현될 수 있다. 작업트리의 후속 변경을 기준 커밋의 기존 동작으로 소급 해석하지 않는다.

## 수행한 정적 검증

- `Get-Content`로 AGENTS.md, 운영 README, TEMPLATE, RF-020, 해당 backend/frontend 소스를 읽고 `rg`로 BACKLOG·재평가·호출자·matcher·관리자 경로·테스트를 확인했다. `git status --short`, `git rev-parse HEAD`로 기준 확인. 하위 AGENTS.md 없음.
- 확인 범위: ConfirmController/Service/address DTO·User, SecurityConfig/TokenFilter/CustomUserDetails/AuthService/UserRepository, mypage 주소 UI/SWR/API 및 Next rewrite, AdminUserController/UserService·admin 사용자 편집 UI, 기존 UserControllerTest.
- 공식 [OWASP API1:2023](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/)를 조회했다. 객체별 작업 권한 검사·회귀 테스트 원칙을 본인 주소라는 좁은 객체에 적용한다. OWASP 자체가 NGNG 역할 정책을 확정하지 않는다.
- 확인 결과: 유효 인증 후 요청 id의 주소를 바꾸는 경로에 주체 비교가 없다. 주소는 계좌의 UNCONFIRMED_USER 제한이 없고 관리자 별도 편집 경로가 존재한다. 무인증 공개 취약점으로 단정하지 않는다. 실제 exploit·HTTP 상태/DB commit은 재현하지 않았다.

## 보존과 한계

- 별도 원시 shell/web 파일 없음(대화 출력만 존재). 테스트 XML·빌드 로그·DB dump·운영 자료는 생성/수집하지 않았다. 이 README는 정적 분석 서술 증빙이며 성공한 실행 증빙이 아니다.
- 이 담당자가 변경한 범위는 RF-041 명세/이 README뿐이다. RF-020 같은 서비스 파일의 후속 변경을 수정하거나 되돌리지 않는다.
- 테스트/빌드/DB/브라우저/HTTP 검증은 명시적 이번 범위 제한으로 실행하지 않았다. 신규 의존성·설정·스키마 변경 없음.

| 남은 검증 | 재개 방법·조건 | 상태/추적 |
|---|---|---|
| 실제 Security 체인 및 서비스 before/after | 구현 직전 소스 보존, 합성 JWT/두 사용자 repository 대역으로 명세 매트릭스 실행; XML/로그 및 실행 소스 보존 | RF-041 계획 |
| RF-020 공유 파일 회귀·독립 리뷰 | AuthService 주입·guard 공유 여부 확인. 계좌 role 제한이 주소로 유입되지 않는지 검증 | RF-041 계획, RF-020 협업 |
| JPA 무쓰기·rollback/동시성 | 안전 fixture·DB 범위 확보 후 entity 상태와 DB 결과 비교; mock 결과로 대체 금지 | RF-041 통합 잔여 |
| 브라우저 실패 UI/토큰 fallback | proxy·HTTP 환경에서403·204·storage·SWR 재검증 | RF-041 연동 잔여 |
| 로그/토큰 정책 및 admin 별도 후보 | 해당 범위 합의 후 재개, 이번 작업 자동 확대 금지 | RF-033/RF-040 및 통합 담당 후보 연결 |

후일 재실행은 당시 소스·환경·날짜의 새 증빙이며 이 단계의 미실행을 통과로 소급하지 않는다. 공통 BACKLOG/LOG 연결은 통합 담당자가 수행한다.

종료 시 `git status --short`와 ConfirmService diff를 추가 확인했다. RF-020의 AuthService 필드 및 계좌 전용 guard가 작업트리에 추가되었고 주소 메서드는 변경되지 않았다. confirmation 테스트/RF-023 문서도 새 untracked로 관찰됐다. 시작 HEAD 분석과 후속 작업트리 변화를 구별하며 해당 파일은 수정하지 않았다. 당시 `git diff --check`에 오류 출력 없음(기존 tracked diff 대상; 실행 테스트 증거 아님).


## 2026-11-10 1A 승인 후 실행

실제 기준 HEAD `2adafc839f3e24dc61dd7a7a0be52661e0e21687`(RF020 병합), Windows PowerShell·Oracle JDK17.0.10·Gradle8.7 offline. 다른 담당자의 RF042/backend·RF022/chat 변경과 untracked 문서는 보존했다. Gradle backend 실행은 다른 담당 종료 확인 후 직렬 수행했다. 외부 서비스/DB 접속은 없다. 처음 계획과 이번 구현·실행 결과를 구별한다.

| 단계 | 실제 결과 | 보존 위치·근거 |
|---|---|---|
| 변경 전 실제 chain9 + 직접 service1 |10건 통과, fail/error/skip0. 유효 USER/UNCONFIRMED_USER/ADMIN/OTHER는 본인·타인 주소204, 무인증 HTTP401, 무인증 직접 서비스는 주소 변경 | `before/`의 당시 ConfirmService·테스트2개·XML2개, `before-console.txt` |
| 변경 후 주소 unit27 + 실제 chain50 |77건 통과. 역할별 본인204·타인/미존재 타인403·null caller/ID·무대상조회/무변경·principal 방어·값비교·DB 장애 전파·기존 null/empty/공백 의미·MVC400·JWT401·fallback·별도 관리자 편집 회귀 | `after/` 소스·XML, `after-source/`, `after-console.txt` |
| RF020·공통 선택 회귀 |계좌 service22+chain30, TokenFilter72+Security46=170건 통과. 주소 포함247건 fail/error/skip0 | `after/` XML6개, `results-summary.json` |
| 패키징 |`--offline assemble` exit0 | `assemble-console.txt` |

생산 변경은 `ConfirmService.addressConfirm`의 호출과 전용 guard뿐이다. 기존 AuthService 필드를 재사용하고 RF020의 계좌 역할 guard·생성자 주입은 보존했다. address 메서드 이전 prefix를 before snapshot과 줄바꿈 정규화 비교해 동일함을 확인했다. 주소는 새 role allowlist 없이 인증 principal email→실제 AuthService→DB caller nonnull ID와 요청 ID를 비교한다. target findById 이전 거부라서 타인 존재 여부에 관계없이403, caller DB 장애는 원래 예외가 전파된다. 기존 자기 조회 실패 예외·주소 대입·204 빈 body를 유지한다.

fallback10조합은 missing/expired access의 본인204·타인403 및4종 역할 표본을 포함한다. 발급된 access의 서명·subject·기존 role·type을 파싱하고 Set-Cookie/새 refresh 발급 없음까지 검증했다. 역할4×access2×대상2의16조합 전체는 아니므로 전체 조합 통과라고 표현하지 않는다. 유효 access가 다른/잘못된 refresh 쿠키보다 우선함도4건 확인했다. 별도 실제 AdminUserController/UserService는 ADMIN 타인 주소200·나머지3역할403 회귀4건을 포함한다.

## 실행 명령 및 실패 이력

명령은 NGNG-backend에서 실행했다. 각 실행 `2>&1 | Tee-Object -FilePath ../docs/refactoring/evidence/RF-041/<단계>-console.txt; exit $LASTEXITCODE`로 stdout/stderr/exit를 보존했다. 기존 Gradle cache 접근은 sandbox escalation 승인 후 수행했고 의존성 조회는 `--offline`으로 차단했다.

```powershell
.\gradlew.bat --offline test --tests 'com.ngng.api.user.user.address.*' --rerun-tasks
.\gradlew.bat --offline test --tests 'com.ngng.api.user.user.address.*' --tests 'com.ngng.api.user.user.confirmation.*' --tests 'com.ngng.api.global.security.TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest' --rerun-tasks
.\gradlew.bat --offline assemble
```

- 첫 준비 명령은 잘못된 cwd로 중첩 작업 디렉터리/테스트 파일을 만들고 원본 경로를 찾지 못했다. 해당 생성 경로가 workspace/backend 내부임을 확인한 뒤 그 담당 생성 디렉터리만 삭제하고 올바른 경로로 다시 준비했다. 그때의 wrapper 실행 및 별도 wrapper version probe는 sandbox의 기존 Gradle lock 파일 접근 거부로 test가 시작되지 않았다. 처음 실패 출력은 대화에만 있으며 `before-console.txt`는 성공한 기준선 실행으로 덮어써 원시 실패 파일은 없다. 실패를10건 테스트 통과로 계산하지 않는다.
- 실제 before/after test·assemble의 compile/test 실패는 없다. 별도 sandbox `java -version` 환경 probe가 출력 없이 대기해 중단했고 JDK release 파일로 환경을 확인했다. 테스트/assemble 결과와 무관하다. CIM process 조회도 접근 불가였으며 원시 실패 파일은 없다.
- TokenFilter XML 공유본 치환 시 angle-bracket placeholder로 임시 XML 파싱 오류가 있었으나 보존 원문에서 XML 안전 문자열 `SYNTHETIC_JWT`로 다시 생성했다. 최종8개 XML 파싱·집계로 before10/after247과 fail/error/skip0을 확인했다. 이 자료 가공 실패는 테스트 실행 실패가 아니다.

## 보존·검토 및 해석의 한계

- `source-manifest.csv`: 실제 실행 후 관련 생산·테스트17개 snapshot과 SHA256. `before/`는 당시 생산1개·기준선 테스트2개, `after/`는 후속 생산1개·주소 테스트2개 및 결과XML이다. `raw-manifest.csv`는 최종 RF041 증빙 파일 SHA256이며 manifest 자체 제외. 원시XML6개 중 TokenFilter1개는 합성 JWT49회만 공유본에서 치환했다. 원문은 Git 제외 `.local-runtime/rf041-raw/after/`에 두고 `redaction-manifest.csv`에서 원문/공유본 SHA256을 구분한다. 나머지7개 XML은 원문 그대로다.
- `before-console.txt`, `after-console.txt`, `assemble-console.txt`는 성공 실행 원시 로그다. 초기 준비/캐시 접근/CIM/환경 probe 실패는 별도 원시 보존이 없다는 한계가 있다. 후일 재실행은 그 날짜·환경의 새 증빙이며 과거 원시 공백 또는 미실행을 소급 통과로 대체하지 않는다.
- 실제 JWT·필터·MVC·controller·서비스/AuthService 및 관리자 UserService를 사용했지만 repository/token 관찰 대역이며 JPA 트랜잭션 manager/Boot component scan/실제 외부서비스를 올리지 않았다. entity 상태와 repository 호출 없음은 격리 무변경 근거이며 실제 DB commit/rollback 무쓰기를 증명하지 않는다. 전체 Boot test/build 및 물리 HTTP·proxy·브라우저·cookie storage·SWR 오류 UI·동시성·성능도 미실행이다.
- 실DB·외부서비스 금지 및 합의한 대역 범위가 미실행 사유다. RF016 안전 fixture/발송 차단·승인된 환경 이후 DB 전후상태·rollback/삭제경합 및 실제HTTP/browser의204/403·fallback 수락·실패 표시를 별도 검증한다. 성능은 사용자 별도 지시·동일 조건 기준선 후 재개한다. RF033 주소 로그·RF040 토큰 폐기·관리자 ID/프런트 실패 UI 정책은 별도 후보로 자동 착수하지 않는다.
- 공유 BACKLOG/LOG는 통합 담당자 소유다. 독립 최종 리뷰에서 본인 주소 인가 범위의 차단 결함 없음, before10/final247/assemble 및 source17개·raw39개 hash 일치를 확인했다. 합의한 구현·격리 검증·독립 리뷰 완료이며 운영 배포 완료를 뜻하지 않는다. commit/push 없음. 신규 의존성·스키마 변경 없음.

