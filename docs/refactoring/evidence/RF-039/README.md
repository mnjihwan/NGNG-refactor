# RF-039 증빙 범위와 한계

- 날짜/최신 판정: 2026-11-11, 3단계 **합의한 최소 구현·대역 선택 검증·독립 코드 및 증빙 리뷰 완료**. 차단 결함 없음. 아래 정적 분석 기록은 구현 전 이력이며 최신 실행은 마지막 절에 구분한다.
- 기준 HEAD: `2091fae3b590f0286fe11e8a1354fc27d81e10c6`. 시작 상태 `?? .tmp/`; 사용자 임시 자료 보존.
- [작업 명세](../../tasks/RF-039.md)에 endpoint별 역할·신뢰 주체·타인 변경 부작용, 프런트/관리자 수신, 승인 경계와 대역/통합 검증 계획을 기록했다.

## 수행 범위와 근거

PowerShell Get-Content/rg/rg --files, git status --short, git rev-parse HEAD로 AGENTS/운영문서/재평가 단계3/TEMPLATE/RF007·009·021·022 및 backend controllers/services/entities/repositories/SecurityConfig/TokenFilter/AuthService, frontend sell/my_page/admin API·페이지/Next rewrite, chat main 호출 검색을 읽었다. 실제 HTTP·S3·DB 요청을 보내지 않았다. 없는 backend admin 폴더에 대한 초기 rg 오류 후 backend 전체 main으로 실제 연결을 확인했다.

핵심 근거는 두 upload controller의 외부 저장 선행과 상품 관계 검사 부재, image delete의 productId+URL 검색 뒤 무검사 setter, thumbnail create의 기존행 삭제/교체, ProductService.create의 body.userId 및 refresh의 path ID 신뢰다. `/upload`·`/deleteImage`는 TokenFilter를 거치므로 익명 공개로 판단하지 않으며 `/products/**`와 달리 역할 제한 matcher가 없다. RF007 PUT/DELETE 인가·RF009 refresh cache 동작은 현재 소스로 확인했으나 RF039 실행 증명이 아니다.

원시 탐색은 대화 tool 출력에만 있다. 별도 raw console/XML/source snapshot/해시 manifest를 만들지 않았다. 이 README는 분석 서술 증빙이며 실행 결과를 대체하지 않는다. 테스트/build/lint/tsc·DB·서비스·브라우저·성능 실행은 모두0이고 통과 건수도 없다. 운영 도달성/악용 성공/전체 보안 감사를 주장하지 않는다.

## 추가 검증과 재개 조건

1. 재개 후 계획은 생성 USER/ADMIN 모두 본인만, 기존 ADMIN 상품 관리 UI 보존, 기존 역할 matcher 유지다. `/upload`·`/deleteImage` 별칭에 UNCONFIRMED_USER 일괄403을 추가하지 않고 본인 객체만 허용한다. ADMIN 대리 생성·신규 관리 기능은 근거 없어 제외한다. 이는 기존 역할 검사와 caller 근거를 보존하는 최소 해석이며 공식 권고가 새 업무 정책을 자동 승인한 것이 아니다. 구현은 이번 문서 갱신 범위 밖이다.
2. 합성 JWT+실제 chain/service+repository 및 UploadService 대역으로 before/after owner·ADMIN·제3자·UNCONFIRMED 역할 매트릭스를 검증한다. 두 upload 모두 숫자(Long 범위)·양수·상품 존재·신원/owner 또는 기존 관리자 권한을 파일명/스트림 접근·압축·S3 전 루프 밖에서 확인한다. 빈 배열·미지원 확장자, 숫자아님/overflow/0/음수/없는 상품도 파일 접근·외부 호출·쓰기0을 검증한다. 국소400/404/403과 정상 응답 계약을 구분 기록한다. image delete의 productId+imageURL 실제 소속, thumbnail update의 저장된 product 관계, 직접 호출·null 식별, refresh cache 무변경/정상 afterCommit 및 RF007/009 회귀를 확인한다. Servlet multipart 파싱 이전 차단을 보장한다는 뜻은 아니다.
3. 관리자 PUT 응답 DTO의 ID 또는 검증된 기존 product ID로 후속 upload하고 DTO text 전체를 전달하지 않는다. `/upload`의 hihi와 `/products/upload`의 Long을 각자 유지한다. 이미지 삭제 일부 실패 뒤 PUT/upload0, refresh 실패/유실의 성공 표시0을 대역 검증한다. sell 생성 성공 뒤 업로드 실패·응답유실은 상품 ID를 보존한 부분성공/결과불명으로 구분해 POST 재생성과 자동 재업로드를 막는다. 생성 응답 자체 유실로 ID가 없으면 결과미확정으로 표시하고 자동 POST 재시도하지 않는다. 토큰 갱신·수동 확인/후속 재조회 경계도 점검한다. 기존 index0 버그·주석 처리된 이미지 수정 활성화·다중상품 원자성 재설계는 제외한다.
4. 실DB·OSIV·경합·S3·Redis·HTTP/브라우저는 RF016 안전 identity, 허용 fixture/key·외부 차단과 정확 cleanup 및 별도 실행 지시 후 진행한다. mock 호출0은 실제 DB/클라우드 무쓰기나 rollback 증명이 아니다. S3 중간 실패 보상 RF015, 형식/자원 RF034, 이미지 캐시 RF010/R9, 토큰 최신화 RF040은 별도 추적하며 자동 착수하지 않는다.
5. 성능은 사용자 유예로 미측정이며 별도 비교 계획·지시 전 실행하지 않는다. 신규 의존성·DB 스키마·대규모 구조 변경은 별도 승인이다.

공통 BACKLOG/LOG는 병렬 통합 담당자가 RF039의 정책/기능 미실행·통합 미검증·범위/성능 잔여를 연결한다. 향후 원시 테스트 결과·환경·source hash·실패/skip·독립 리뷰를 별도 보존하고 이번 분석 시점의 미실행 기록은 유지한다.

공식 OWASP API1:2023 자료는 통합 담당자가 2026-11-11 직접 확인한 근거를 전달받아 명세에 연결했다. RF039 담당자가 별도 웹 열람한 것으로 기록하지 않는다. 초기 사용자 일시중지 지시에 따라 문서 정리 후 중지했으며, 이후 재개 지시로 공통 독립 계획 리뷰를 위 두 문서에 반영했다. 과거 마지막 상태 확인에서 RF024 병렬 문서도 보였으며 수정하지 않았다. 이번 재개도 문서만 변경했고 운영 코드·테스트·DB·서비스 접속은 하지 않았다. 선행 인가와 저장 서비스 재검사 사이 ownership race, 허용 후 S3 부분 실패/보상, 이미지 캐시 및 물리 통합은 그대로 미검증·별도 범위다.

## 구현 재개 후 실제 증빙 (2026-11-11)

### 기준·실행 범위

실제 구현 시작 HEAD `3a101a0f5eee7bf2b2582fcaadba7e97b41982cf`, 초기 RF024 source/test/docs·타 RF 계획 문서·사용자 `.tmp/` 변경을 보존했다. 다른 담당자의 통합으로 수행 중 HEAD가 `b06c05e2b90bdbd7acb9c7d5314e59ada200c6ce`로 이동했으며 RF039 담당은 Git 변경/commit/push를 하지 않았다. 전체 분석·변경점은 [명세의 구현 기록](../../tasks/RF-039.md)에 있다. RF024의 product API/ReportModal은 수정하지 않았다.

실제 SecurityConfig/TokenFilter/JWT/컨트롤러/서비스/AuthService를 명시 bean context로 구성하고 repository/UserRepository·UploadService를 대역으로 공급했다. Boot auto-config·실DB·Redis·S3·외부 네트워크는 시작하지 않았다. UploadService 호출0은 그 뒤 변환·압축·S3 경로로 진입하지 않았다는 대역 근거이며 servlet의 multipart 파싱 이전 차단을 보장하지 않는다.

### 보존 자료와 실행 이력

| 자료 | 실제 결과·해석 |
|---|---|
| `before/ProductAdditionalWriterSecurityChainTest.java.txt`, `before/TEST-*.xml`, `before/backend-console.txt` | 실제 chain29건 fail/error/skip0. USER 타인/ADMIN 대리 생성, refresh/upload/delete 및 UNCONFIRMED 별칭 타인 변경의 변경 전 특성. 서비스와 프런트 최초 소스는 아래 별도 설명 |
| `before/sandbox-lock-failure.txt` | 첫 sandbox wrapper lock 접근 거부. 테스트 미실행, 기존 Gradle cache 접근 escalation 후 offline 재실행 성공 |
| `after/concurrent-rf043-compile-failure.txt` | 병렬 RF043 초안 JoinRequest accessor4오류로 compileJava 실패, RF039 테스트0. 해당 담당자가 수정했고 RF039 담당은 그 파일 미수정 |
| `after/initial-test-compile-failure.txt` | RF039 신규 service test의 IOException 선언2오류. 선언 수정 후 재실행 |
| `after/initial-contract-regression/TEST-*.xml`, `after/initial-contract-regression-failure.txt` | 정상 sourceSet250건 중249통과/1실패. RF009 기존 REFRESH 미존재 NPE 기대가 새 국소404와 충돌. REFRESH 기대만404로 명시 수정 |
| `after/initial-250-pass/TEST-*.xml`, `test-summary.csv` | 정상 전체 sourceSet compileTestJava 및 선택250건 fail/error/skip0. 이 중간 성공 console은 최종 재실행 시 덮어썼고 별도 보존하지 못했다. XML과 당시 tool 결과로만 뒷받침하며 console 원본 보존을 주장하지 않는다 |
| `after/concurrent-rf044-test-compile-failure.txt` | 보강 테스트 재실행 중 병렬 RF044 ReportResponseSecurityChainTest 초안5오류로 정상 compileTestJava 실패. 테스트 실행0, RF039 담당이 해당 초안 수정하지 않음 |
| `after/frontend-initial-harness-failure.txt` | 최초 RF03935건 중33통과/2실패. refresh portal 닫힘 fixture에서 handler를 못 찾은 harness 오류, 열린 portal 실제 handler fixture로 수정 |
| `after/frontend-tap.txt` | 실제 TypeScript API/component handler + fetch/React/navigation 대역. RF03935+RF00714=49건 fail/skip0. sell 생성/이미지 실패·유실/ID 유지·POST 재생성0, admin DTO ID 및 hihi/삭제 실패 뒤 PUT0, refresh 거짓성공0. 브라우저 렌더/네트워크 증거 아님 |
| `after/initial-tsc-failure.txt`, `after/frontend-tsc.txt` | 초기 discriminator union narrowing1오류 후 rejected/unknown 개별 discriminator로 수정. 재실행 exit0. 성공은 stdout 없음이므로 파일에 실제 exitCode=0을 명시 |
| `after/frontend-lint.txt` | lint exit0, 기존 img warning3. production build/외부 폰트 fetch/브라우저 미실행 |
| `after/backend-assemble.txt` | assemble exit0(최종 보강 뒤 결과는 최종 확인 절 참조). 패키징만 검증하며 전체 테스트·서버 기동 근거가 아님 |

`before/source/`의 Java/기존 cache test는 immutable 착수 커밋을 **구현 후 git show로 추출한 기준 소스**이며 실행 당시에 byte snapshot한 것으로 표현하지 않는다. git show 출력의 UTF-8/개행 표현으로 저장했으며 원본 blob byte hash와 동일하다고 주장하지 않는다. 프런트 before6개는 수정 직전 실제 작업트리 파일을 byte copy했다. after/source는 최종 수정 source/test를 byte copy하고 source-manifest에 SHA256을 남긴다. 사용자 임시 원본/다른 RF 증빙은 옮기거나 삭제하지 않았다.

실제 실행 명령은 `commands.md`, 환경은 `environment.txt`, 보강 후 최종 class별 건수는 `after/test-summary.csv`를 참조한다. before XML을 after 결과로 대체하지 않았으며 skip/mock/pass 범위를 전체 운영 검증으로 확대하지 않는다. 인가용 key/email/ID/이미지 URL은 테스트용 합성 값이며 운영 .env/yaml·토큰·계정 비밀번호를 증빙으로 복사하지 않았다. 다만 최초 점검은 실패로그 home 경로와 XML hostname을 놓쳤다. 독립 리뷰 후 아래 증빙 보완 절에서 원본 byte를 ignored 전용 디렉터리에 보존하고 공유본을 비식별화했으며, 최초 `none required` 판정은 철회했다.

### 추가 검증·한계 및 재개 조건

- refresh 실제 cache/tx proxy 대역은 성공 afterCommit 대상77 key만 무효화·rollback/commit 실패 무효화0·인가거부 timestamp/cache 무변경을 선택 검증했다. 실제 DB rollback·Redis 장애 복구·분산 최신성의 증거는 아니다.
- owner/제3자/ADMIN/UNCONFIRMED·무인증/잘못된 access/refresh·ID 없는 JWT principal→DB email caller 조회, service 직접 호출의 malformed principal/null owner·ID·실제 image/thumbnail 상품 관계 및 두 upload 빈 배열/미지원 파일 선행검사와 malformed/overflow/0/음수/미존재 파일접근0을 대역 검증한다. 최종 보강 실행 완료 전 추가 건수를 통과로 세지 않는다.
- 인가 preflight와 S3 후 저장 재검사 사이 owner 변경·삭제 경합, S3 중간 실패의 보상 RF015, 형식/자원 RF034, 이미지 캐시 RF010/R9, role 최신화 RF040은 남는다. 허용 이미지 저장 후 thumbnail 업로드 실패 시 이미지 save가 먼저 수행됨을 재현했다. 이를 원자성/보상 해결로 표현하지 않는다.
- sell 요청 기억은 마운트 동안만 유지된다. unknown은 수동 판매 목록, 알려진 ID는 상품 링크/관리 목록 재조회로 확인한다. 리로드 멱등성·다중상품 원자성/이동 경쟁·admin PUT 뒤 upload 부분성공 재개·refresh index0 버그는 범위 밖이다.
- 실DB/HTTP/S3/Redis/JPA/OSIV/브라우저는 실행하지 않았다. RF016의 승인된 identity/fixture와 외부 차단·cleanup 또는 S3 전용 버킷/key 확보 후 별도 명세/승인으로 검증한다. 성능은 사용자 유예로 미측정이다. 신규 의존성·스키마·전역 인증/role/refresh fallback 변경 없음.
- RF007 guard/비즈니스 조건과 RF009 invalidator는 유지했다. RF021/RF022 소유 파일은 변경하지 않았고 해당 별도 suite를 이번 RF039 회귀로 실행하지 않았다. 공통 BACKLOG/LOG 통합과 독립 최종 판정은 부모에게 전달한다.

### 보강 후 최종 확인

- **정상 전체 sourceSet compileJava·compileTestJava 실행 + 선택8클래스291건(fail/error/skip0)**을 최종 확인했다. `after/backend-console.txt`, 최종 XML8개, `after/test-summary.csv`는 해당 실행의 공유본이다. 원본 byte의 전용 보존 위치와 비식별화 해시는 아래 증빙 보완 절을 참조한다. 신규 RF039 chain87/direct48=135건, RF00786, RF00970이다. 전체 프로젝트 test 실행은 아니다.
- 먼저 보강 격리291건을 `after/isolated-final/`에 보존했고, 병렬 RF044/RF043 초안이 수정되고 편집 동결된 뒤 init 없이 같은291건을 정상 sourceSet에서 재실행했다. 후자의 최종 compile은 성공했다. 정상 sourceSet 재개 중 RF043 신규 test의 Role ambiguous1오류도 `after/concurrent-rf043-new-test-compile-failure.txt`에 보존했다.
- 최종 assemble exit0, compileJava/processResources UP-TO-DATE, resolveMainClassName/bootJar/jar 실행. frontend 최종49pass0fail/skip0, lint exit0·기존 img warning3, tsc exit0. 최종 source16개 byte snapshot/SHA256은 `source-manifest.csv`에 있다.
- 최초 `redaction-manifest.csv`의 `none required`와 home 경로·hostname을 포함한 공유본은 부정확했다. 독립 리뷰 후 원본 byte 보존·공유본 비식별화와 paired SHA256으로 교체했다. 합성 test key/계정은 보존하며, 실제 비밀 및 개인정보 전체 감사를 주장하지 않는다.
- 원래 CRLF 파일의 개행을 보존하며 국소 diff를 확인했다. `git -c core.whitespace=cr-at-eol diff --check -- <RF039 source>` 성공. 일반 diff check의 CRLF 줄 trailing-whitespace 결과와 LF 전체변환 시도는 source 변경량을 최소화해 원래 CRLF로 복구했다. source 내용은 동일하며 체크 설정 경계를 숨기지 않는다.
- 독립 리뷰는 대기 중이다. 추가 대역 기능 검증의 남은 필수 항목은 없고 앞에 명시한 실통합·owner 경합·S3 보상·이미지 캐시·성능·화면 리로드 멱등성은 별도 재개 조건을 유지한다.

### 독립 리뷰 후 개인정보 비식별화 보완

초기 `none required` 판정은 실패로그 home 경로 및 XML hostname을 놓쳤으므로 철회한다. 코드 리뷰는 차단 결함 없음으로 전달받았으며 이번 보완은 문서·증빙만이다. 운영/테스트 소스나 Git 상태를 수정하거나 테스트/Gradle을 재실행하지 않았다.

- 먼저 기존 RF039 증빙85개와 수정 전 task 문서를 byte copy해 ignored `.local-runtime/rf039-evidence-20261111-raw/`에 보존했다. `git check-ignore`와 raw hash 검증으로 확인했다. 이 디렉터리는 로컬 전용이고 Git에 추가하지 않았다. 중간250 성공 console을 이미 덮어쓴 과거 한계는 복구했다고 주장하지 않는다.
- 그 뒤 공유본40개에서 native/escaped/slash/URI home 표현18곳과 XML hostname33곳을 `[USER_HOME]`/`REDACTED_HOST`로 치환했다. 개인정보 원래 값은 공유 manifest에 기록하지 않는다. 첫 시도는 PowerShell 읽기전용 HOME 변수명 충돌로 home 치환이 실패해 중간 산출물을 버리고 보존 원본에서 다시 생성했다. raw byte는 변경하지 않았다.
- `raw-manifest.csv`에 원본86개 위치·byte 수·SHA256, `redaction-manifest.csv`에 원본/현재 공유본 paired SHA256·치환 수·변경 종류를 남긴다. 문서·기존 manifest의 정정은 실행 산출물 치환과 구분한다. manifest 자신을 자기 hash에 넣는 순환은 제외하며 최종 공유 파일 hash는 `artifact-hashes.csv`에 자기 파일을 제외해 남긴다. 새로 생성한 audit/manifest에는 이전 raw 쌍이 없으며, 기존 raw 실행 증거로 표현하지 않는다.
- `xml-redaction-verification.csv`는 XML33개에서 hostname 외 모든 suite 속성(결과 건수·timestamp·time), 모든 testcase 속성과 failure/error/skipped 분류·type이 보존됨을 확인한다. 최종291건 및 before29·실패/중간 실행의 timestamp나 결과를 재작성하지 않았다.
- `source-manifest.csv`는 source16의 현재 SHA256·비식별화 전 raw snapshot SHA256·현재 shared snapshot SHA256을 재계산했다. 모두 일치하고 코드/test 변경0이다. `git-manifest.json`은 HEAD 및 해당 source16 상태를 read-only로 캡처한 자료이며 커밋/인덱스 변경은 하지 않았다.
- 개인 home(역슬래시·escaped·slash·URI), 원래 hostname, JWT 형태, private key/AWS key 후보를 공유본과 task에서 재탐색한 수치 및 raw/paired/source 일치 검증은 `redaction-verification.json`에 있다. 합성 key/계정은 유지한다. 이 범위 점검은 저장소 전체 비밀 감사가 아니다.

생성 owner의 구현은 서버 caller ID와 body.userId를 비교한 뒤 기존 Product 생성자로 같은 ID를 저장하는 방식이다. 서버 User 객체를 직접 owner에 할당하는 구현으로 설명하지 않는다. 기존 통합·성능·owner 경합·S3 보상 등 잔여 판정은 바꾸지 않는다.

### 최신 독립 리뷰 완료 판정

2026-11-11 공통 독립 리뷰가 코드 차단 결함 없음 및 P2 증빙 정제 완료를 최종 확인했다. paired85행·공유 artifact89개·source16개·raw86개와 XML33개 결과/시간 불변을 독립 확인해 합의 범위의 RF039를 완료로 판정했다. 이번 상태·문서 hash 갱신에 코드/테스트 수정이나 재실행은 없으며 기존 물리 통합·경합·보상·성능 등 잔여 조건은 그대로다.
