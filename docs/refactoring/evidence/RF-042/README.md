# RF-042 증빙: 탈퇴 본인 객체 인가

- 기록일: 2026-11-10. 합의 객체 인가 범위의 최소 구현·선택 격리 검증·독립 최종 리뷰 완료. [작업 명세](../../tasks/RF-042.md)가 정책·범위 기준이다.
- 기준 HEAD: `e417eaf7d476a639413414ff8f4423b89cc88e66`. 시작 작업트리 ConfirmService 수정, confirmation 테스트 및 RF020/021/022/023/041 문서·증빙 untracked를 보존했다. 최초 계획 두 문서만 작성한 이력 후, 별도 구현 위임과 독립 계획 리뷰 착수 가능 판정을 전달받아 DropOutService·전용 테스트 두 파일을 변경했다. 공통 BACKLOG/LOG는 통합 담당자가 기록한다.
- 환경: Windows PowerShell, Java `17.0.10+11-LTS-240`, 기존 Gradle wrapper8.7/cache, offline/no-daemon. 신규 의존성·DB schema·실DB·Redis·SMTP/SMS·브라우저·운영 서버를 사용하지 않았다.

## 원시 자료·보존과 실행

| 단계 | 결과 | 보존 |
|---|---|---|
| 기본 sandbox wrapper | wrapper zip.lck 접근 거부, 테스트0 | `before/wrapper-access-denied.txt` |
| before harness 첫 컴파일 | Role wildcard import 충돌, 테스트0. 명시 import로 보정 | `before/compile-failure.txt`; 실패 당시 전체 소스 snapshot은 미보존, 로그에 충돌 위치/원인 보존 |
| before actual chain | 5/5, fail/error/skip0 | `before/console.txt`, `before/xml/`, `before/source/DropOutService.java`·`DropOutSecurityChainTest.java` |
| after 첫 선택 실행 | 214건 중1실패, error/skip0. fixture 재stubbing에서 이전 answer가 SecurityContext 없는 상태로 실행되어 NPE. 운영 guard 결함 아님 | `after/initial-failure/console.txt`, `xml/`, 보정 전 `DropOutSecurityChainTest.java` |
| 잘못된 Gradle 작업 옵션 위치 | `test assemble --tests`가 assemble 옵션으로 해석되어 실행0 | `after/invalid-task-order.txt` |
| 최종 after+assemble | 214/214, fail/error/skip0; compileTestJava·assemble 성공 | `after/console.txt`, `after/xml/`, `after/source/`17개 소스 |

- `SHA256SUMS.txt`는 README·manifest 자체를 제외한 보존 파일의 SHA256이다. snapshot은 실행에 사용한 소스이며 다른 RF 소스를 되돌리는 용도가 아니다. 최종 source와 현재 원본 hash가 일치함을 확인했다.
- 원본 XML은 gitignored `NGNG-backend/build/rf042-raw/{before-xml,initial-failure-xml,after-xml}`에 별도 보존했다. 공유 XML은 기존 TokenFilterTest의 parameterized display name에 포함된 합성 JWT compact 문자열만 `[SYNTHETIC_JWT_REDACTED]`로 치환했다. assertion·건수·실패 stack·test identity는 유지한다. console과 전용 소스는 합성 fixture뿐이며 운영 토큰/개인자료를 사용하지 않았다.
- 최초 정적 분석은 지침·재평가·service/controller/entity/AuthService/TokenFilter/SecurityConfig/관리자·frontend 호출 조사 및 [OWASP API1:2023 BOLA](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/) 확인이다. 당시 shell/web 원시 출력은 대화에만 있고 별도 raw는 없다. 그 당시 테스트·DB 미실행 기록을 이번 결과로 소급 대체하지 않는다.

backend 디렉터리에서 실행한 성공 명령:

```powershell
.\gradlew.bat test --offline --no-daemon --tests '*DropOutSecurityChainTest'
.\gradlew.bat --offline --no-daemon test --tests '*DropOutServiceTest' --tests '*DropOutSecurityChainTest' --tests '*TokenFilterTest' --tests 'com.ngng.api.global.security.SecurityChainTest' assemble
```

기본 sandbox wrapper lock 접근 거부 뒤 기존 cache lock 쓰기만 필요한 `require_escalated` auto-review 승인으로 실행했다. 외부 네트워크/서비스를 사용하는 승인이나 새 의존성 추가는 아니다. 첫 명령은 구현 전 특성화 소스에서만 실행했다. 현재 보호된 소스로 첫 명령을 재실행하면 최종57건이며 과거 before5건을 재현한 결과가 아니다. before를 다시 재현하려면 보존 source를 별도 안전 checkout에 복원해 실행하고 새로운 날짜의 결과로 구분한다. 공유 작업트리에서 운영 guard를 제거하며 재현하지 않는다.

## 범위별 실제 근거

| suite | 건수 | 검증 범위 |
|---|---:|---|
| before DropOutSecurityChainTest | 5 | 세 역할 타인 visible 변경3, 동일JWT 본인 재활성화1, unknown nonblank role 본인1 |
| after DropOutServiceTest | 39 | 역할4×visible2 본인, 역할3×존재/미존재 타인×visible2, 잘못된 principal8, 미해석/null identity5, 다른 인스턴스 Long 값 동등성·위조 principal ID 배제·반복·DB 오류 전파·본인 target 소실 예외·nullable visible NPE |
| after DropOutSecurityChainTest | 57 | 본인8, 타인/미존재/null18, 반복1, missing/expired access→refresh12, irrelevant refresh2, 인증 거부8, DB caller 미해석2, MVC body400 3, 별도 관리자 DELETE3 |
| 기존 TokenFilterTest | 72 | 기존 토큰 파싱·fallback/인증 정책 회귀 |
| 기존 SecurityChainTest | 46 | 기존 role/matcher/skip/401/403 정책 회귀 |

- actual chain은 실제 SecurityConfig가 만든 TokenFilter/LoginFilter 및 실제 JwtTokenVerifier/JwtTokenProvider, DropOutController/DropOutService/AuthService다. 필터를 끄거나 service를 mock하지 않았다. JWT principal userId가 null임을 repository answer에서 확인하고 실제 email→DB caller 해석 뒤 대상조회 순서를 검증했다.
- 별도 관리자 DELETE 회귀에는 실제 AdminUserController/UserService를 연결했다. UserRepository·관리자 경로에서 사용하지 않는 PointHistoryService/TransactionDetailsService/ProductService/JoinService와 로그인 AuthenticationConfiguration/manager는 대역이다. component scanning·Boot 자동부팅·data source·transaction manager·네트워크가 없다. 로그인 manager는 이 요청에서 사용하지 않는다.
- 본인204 빈 body와 visible 토글만 유지하며 USER/UNCONFIRMED_USER/ADMIN/unknown role의 새 제한을 두지 않았다. 타인·null·주체 해석 실패403 전 target `findById`·`changeVisible` 미도달, caller/target 전체 user 필드 snapshot 불변과 save 미호출을 함께 확인했다. DB 일반 예외를403으로 변환하지 않는다.
- 유효 access의 refresh 없는 요청과 잘못되거나 타인 주체인 refresh cookie는 기존 access 기준으로 처리하고 새 Authorization/Set-Cookie가 없다. missing/expired access의 유효 refresh는 본인204/타인403 모두 caller 원래 email/role/type=access의 fallback Authorization만 생성한다. 새 refresh cookie/대상 토큰은 없다.
- 동일 유효JWT 반복의 true→false→true 및 visible=false 본인 호출을 유지했다. 이는 RF040 서버 토큰 폐기/상태 최신화 미해결과 구분한다. 별도 관리자 DELETE는 ADMIN만200 ID/false 대입/save와 재호출 false 유지, 다른 역할403이며 dropout ADMIN 타인 우회가 아니다.
- 진행거래/잔액의 합성 객체를 두고 사용자 역할/주소/계좌와 관련 fixture 상태·업무 대역 무호출을 확인했다. DropOutService에 원래 그런 의존성이 없으므로 별도 사업조건을 추가하지 않았다는 제한된 근거다. 실제 DB 거래/잔액 정합성·정산 안전성을 검증한 것이 아니다.

## 한계·미실행·재개 조건

| 항목 | 이번 상태·이유 | 추가 방법·재개 조건 |
|---|---|---|
| JPA dirty checking/물리 commit·rollback/SQL·동시 토글/행 삭제 경합 | 실DB 금지, repository 대역 및 tx proxy 없는 services. snapshot/target 조회·mutation 미호출을 물리 DB 무쓰기 증거로 확대하지 않음 | RF016 안전 identity/소유행 fixture/정리·별도 실행 지시 후 실DB 검증. 본인 target 미존재는 unit 예외 경계만 확인 |
| 실제 Boot HTTP/error dispatch/OSIV·브라우저/proxy·외부 소비자 | MockMvc 명시 MVC/security context만 실행. frontend 탈퇴 직접 호출 미발견은 외부 소비자 부재 증명 아님 | 안전 HTTP 서버/인증 fixture/외부 차단·소비자 목록 확보 후 별도 실행 |
| 전체 backend test/build | 기존 외부연동 Boot suite 안전 fixture 미확보, 선택 test+assemble만 수행 | 안전 환경 후 관련 묶음부터 새 결과로 실행 |
| 반복 멱등화/재활성화·nullable visible·거래/잔액·동시 토글 정책 | 기존 동작 유지, nullable 본인 NPE는 unit 특성화; 사업 정책 미승인 | 별도 정책/범위 합의 뒤 독립 작업 |
| 탈퇴 후 access/refresh 폐기·visible/role 최신화 | 현재 같은 토큰 반복 재활성화 유지, 객체 인가와 별도 RF040 | 토큰 수명 정책 및 폐기/회전/후속요청 검증 |
| 성능 | 사용자 지시 유예, 측정·개선 주장 없음 | 별도 지시와 대표 데이터/환경의 비교 계획 |
| 독립 최종 리뷰 | 완료, 합의 범위 차단 결함 없음 | 별도 담당자가 guard/role/ADMIN 별도 API/토글/fallback/무쓰기·실제chain 및 before5/final214/assemble, source17개·manifest35개 일치, 실패 이력·한계를 확인했다. 새로운 소스/환경 변경 시 관련 범위를 다시 검증한다 |

공통 BACKLOG/REFACTORING_LOG의 최신 상태·잔여 검증 연결은 통합 담당자에게 인계한다. 잔여 기록은 새 정책/DB/성능/범위 밖 작업 착수 승인이 아니다.


공유 로그의 로컬 사용자 경로는 `[LOCAL_PATH_REDACTED]`로 치환했다. 해당 실패 로그의 원문은 별도 보존하지 않았으며 치환본 해시를 manifest에 기록한다.
증빙 디렉터리의 `.gitattributes`로 바이트 변환을 방지하며 manifest는 해당 설정도 포함한다.
