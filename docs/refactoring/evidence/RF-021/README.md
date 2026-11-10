# RF-021 분석·구현·선택 검증 증빙

- 2026-11-10: **요청 생성·수락·거절 인가 진행 범위 승인, 직접 생성 API 변경·정책은 사용자 보류**. 이 담당자는 분석·계획만 작성했으며 구현·검증 결과를 주장하지 않는다. 기준 HEAD `e417eaf7d476a639413414ff8f4423b89cc88e66`, 시작 작업 트리 clean. [명세](../../tasks/RF-021.md)의 범위·불변 조건·실행 계획이 상세 근거다.
- 루트 AGENTS/README/TEMPLATE/BACKLOG/재평가, RF004·006 명세·증빙을 읽고 Git 상태/HEAD/하위 지침을 확인했다.
- rg/파일 읽기로 transaction controller→request/details service→repository, TokenFilter/SecurityConfig/AuthService, frontend 요청 API/hook/UI, chat-server 조회 연관과 기존 RF004 기능 동시성 테스트를 조사했다.
- 확인 사실: 거래 경로는 TokenFilter 인증 대상, JWT principal에 ID 없음, AuthService가 email로 DB user 조회. request 생성·처리 및 직접 생성에 객체 인가 없음. 확정 seller는 product.user이며 request.seller를 그대로 확정 판매자로 쓰지 않음. 운영 도달성/피해는 재현하지 않았다.
- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)와 [API1:2023](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/)을 열람하여 객체·행위별 서버 검사를 계획했다. 직접 생성의 caller 정책은 공식 지침만으로 결정할 수 없어 명세에 미확정으로 남겼다.

## 수행과 보존

소스·설정·테스트 수정, 빌드·테스트 실행, 신규 DB 접속, 운영/프록시/브라우저 확인, 성능 측정, commit/push는 하지 않았다. 이번 변경은 RF021 명세와 이 README뿐이다. 읽기/검색/공식 웹 열람 결과는 대화 tool 출력에만 있고 별도 원시 파일·XML·console·DB snapshot은 생성/보존하지 않았다. 과거 RF004/006 실행 수치를 이번 결과로 재사용하지 않는다.

## 미실행·한계와 재개 조건

| 항목 | 이유와 한계 | 추가 검증 / 추적 |
|---|---|---|
| 실제 체인/서비스 결함 재현·API/무쓰기·회귀 | 현재는 정적 검사; 새 인가 구현/테스트 없음 | 명세의 합성 JWT·명시 실제 bean/저장소 대역 환경으로 변경 전후 실행. R21-격리 |
| 직접 생성 객체 인가 누락·허용 주체 | **사용자 보류**, API·기존 서비스 동작 유지. 외부 호출자 미발견은 안전성 증명 아님 | 사용자 재개 지시·업무 계약/정책 확정 뒤 별도 변경. 요청 인가 합의 범위 완료와 분리하며 거래 생성 전체 안전성 선언 금지. R21-정책 |
| 실제 DB 동시성/commit/rollback·캐시·Servlet OSIV | 신규 DB 접속 금지, 기존 RF004 자료는 현재 실행 증거 아님 | RF016 최소 안전 기반과 격리 target/fixture·소유행 cleanup·worker 인증 격리 확인 후 계획의 경합/실패 주입. R21-연동 |
| 프런트/채팅 HTTP·브라우저·배포 | 파일 조사만 수행 | 안전한 두 서버/프록시·합성 계정으로 실패 UX/재조회·401/403 확인. 채팅 자체 인가는 RF022 |
| owner 변경·신규 요청 경쟁·오염 데이터/가격/역할/상태·성능 | 이번 객체 인가 계획 밖, 정책·환경 근거 부족 | R21-범위 및 RF004/RF035/RF038 등 별도 명세·사용자 지시 전 자동 착수 금지 |

사용자 결정에 따라 수락 서비스에서 owner를 확인한 후 기존 details.create를 호출하며 공유 create/API를 변경·비활성화하지 않는다. 직접 생성에는 객체 인가가 없어 유효 인증 제3자의 임의 거래 생성과 선행 직접 생성으로 정당 수락이409가 되는 잔여 위험이 남는다. RF004 잠금/중복 방지는 인가 보장이 아니다. 직접 생성201/Location/중복409/forSale·요청 불변과 수락↔직접 생성 경합은 기존 동작 보존 회귀이며, 제3자 직접 생성 허용은 보안 통과로 기록하지 않는다. 요청 생성·수락·거절 무쓰기 보장을 직접 생성까지 확대하지 않는다.

후일 실행 시 before/after/실패를 분리하고 정확 HEAD·명령·환경·fixture·실행/UP-TO-DATE/skip·원시/정제 XML 및 로그/소스 hash를 기록한다. DB 없는 MockMvc 성공을 물리 commit/운영 검증으로 확대하지 않는다. 독립 리뷰·diff 검토 결과와 남은 항목은 명세/이 README/부모 담당자의 BACKLOG·로그에 함께 반영한다. 원시 분석 출력 미보존과 후일 재실행을 구분한다.

## 2026-11-10 실제 구현·검증

요청 생성·수락·거절의 최소 인가 구현·선택 검증·부모 독립 리뷰를 완료했다. 합의 요청 경로의 차단 결함은 없으며 직접 생성 API 변경·정책 보류와 물리 통합 미검증은 유지한다. 위 초기 분석의 실행0 기록은 당시 이력이며 아래 새 실행을 소급 적용하지 않는다. 실제 시작 HEAD `6bc7a56bab1a94c1f79f0c96970258fd89c08150`; 병행 RF022 소스 변경/RF021~023 초안이 있던 작업 트리였다. 명세의 실제 결과 절이 구현/역할·오류 경계·소유 파일·한계의 상세 근거다. 공통 BACKLOG/로그는 부모 담당자에게 인계했으며 직접 수정하지 않았다.

| 실행·자료 | 실제 결과 |
|---|---|
| before/ |실제 chain10/10, failure/error/skip0. 타인 허용7건은 결함 특성 재현이며 보안 통과가 아님. 나머지 정상1/인증거부2. 당시 테스트/TransactionRequestService source2개·XML·console 보존 |
| compile-failure/ |병행 RF023 before의 RF021 MySQL factory auth 인자2곳 누락 컴파일 실패 로그 복사. 테스트 미실행, 별도 건수 없음 |
| after-initial/ |9클래스217 중187통과/30실패, error/skip0. nested stubbing28·기존 mock answer 재stub null2의 fixture 오류. XML9·console·관련 테스트 source3개 보존 |
| after/ |9클래스232/232, failure/error/skip0. chain60/service23/acceptance28/creation15/ownership24/status-chain13/cache57/invalidator9/concurrency3. Gradle exit0,5tasks executed |
| assemble.txt |offline assemble exit0 |
| frontend-initial.txt |58중34통과/24실패. 단순 return mutate에서 오류 뒤 SWR revalidation1 발생. 실패를 통과로 대체하지 않음 |
| frontend.txt·lint.txt·tsc.txt |API 성공을 먼저 await하도록 보완 후58/58(신규27+RF00431), lint0. tsc는 담당 실행 tool 결과 exit0이나 보존 로그가 비어 있어 독립 리뷰에서 파일만으로 exit0 확인 불가 |

환경: Windows PowerShell, Oracle Java17.0.10(java-version.txt), Gradle8.7(wrapper properties), Node20.10.0(node-version.txt), 기존 설치 의존성. 정확 명령과 출력 보존 방식은 [commands](commands.md). Gradle wrapper 최초 lock 접근 거부 원문 파일은 재시도에 덮어써져 대화 출력만 남았다. Python launcher 접근불가/Node 편집script 문법오류도 대화에만 있고 테스트에 포함하지 않는다. 캐시 접근 자동승인은 허용됐으며 승인 거절은 없었다.

TransactionRequestService에 email→AuthService DB user ID 검증을 국소 추가했다. invalid service principal/DB user·ID 부재401, 객체 불일치/null ID403. create는 RC tx/실구매자→상품 scalar lock seller→entity owner→duplicate 순서, server buyer/product/seller로 저장한다. 없는상품 create404는 새로 명시한 경계이며 기존404 보존이라고 주장하지 않는다. update는 기존 scalar productId→상품 lock 순서를 지키고 projection owner 검사 뒤 request entity를 읽으며 entity owner도 검사한다. 인가가 no-op·409·details 조회·setter/save 앞이다. 제3자는 duplicate -1/state/details에 도달하지 않고 저장·형제요청/상품 상태·cache 호출이 변하지 않는다. Mock 상태와 interaction만으로 실제 DB 무쓰기를 보장하지 않는다.

실제 SecurityConfig/TokenFilter/JWT/controller/서비스/AuthService와 저장소/cache 대역의 chain60은 role3의 owner/buyer/기록seller/제3자, principal ID 없음과 DB email 조회, 별도 Long 값 비교, 생성/처리·no-op·기존 직접거래 충돌 앞403, 정상 fallback access 재발급·403, 정상200/201/Location·-1/404/409를 검사했다. service23은 비정상 principal7·DB identity null·body의 buyer/seller ID null·불일치·lock/entity owner 불일치·조회순서·RC 쓰기 advice·save 실패 rollback advice를 검사했다. AuthService User 연관에는 product/request 역방향 연관이 없어 인증 user 조회가 이들을 사전 로딩하지 않음도 정적으로 확인했다. 실제 SQL 관측은 아니다.

RF00443/RF00637/RF00969 회귀로 정상 반복·거절뒤수락/409·직접 생성·동일 RC/lock/entity 순서·후커밋 캐시/롤백 advice를 보호했다. recording transaction manager는 실제 Spring advice/synchronization을 사용하지만 JPA/물리 DB resource는 아니다. fallback 헤더·MockMvc 검증은 실제 browser cookie/HTTP 서버·OSIV 검증과 구분한다. 로그인 manager는 미사용 대역이며 Boot scan/운영 properties/DB/Redis/외부 client가 없다. offline 의존성 해소를 사용했고 새 dependency/schema를 추가하지 않았다.

직접 `/transaction`과 details.create는 사용자 보류로 변경하지 않았다. 인증 제3자 role3의 직접201·Location/seller=product.user/status1·상품/요청 불변은 기존 위험의 회귀이며 보안 통과가 아니다. 제3자가 선행 직접 생성하면 정당 owner 수락은409가 될 수 있다. 내부 수락은 기존 details.create를 사용하고 findCurrentDetails MANDATORY가 유지된다. 요청 인가 완료를 거래 생성 전체 안전성으로 확대하지 않는다.

공유 파일 `_api.ts` helper 검사와 `PrivateChat.tsx` 생성 handler await/catch의 두 hunk는 RF022 frontend 담당자가 적용했다. RF021 hook은 await API 성공 후 mutate 반환이며 이 외 RF022 채팅 변경의 출처는 RF022다. 실제 TS API/hook/component handler·installed SWR를 double DOM/React와 실행해 생성 실패12종의 실패전파·성공cache/revalidation/모달닫힘 방지와 성공/중복·응답대기를 검증했다. 실브라우저/채팅서버 재조회와 동일하지 않다.

## 보존·한계·추가 검증

- source-final21개와 source-final-hashes.json에 최종 실행 관련 source SHA256을 보존했다. RF023 read 및 RF022 frontend 병행 변경도 snapshot에 포함되므로 RF021 source diff 출처와 구분한다. before 의존소스 전체 snapshot은 없고 당시 request service/test만 있다. 최초 실패 source는 관련3개만 있어 전체 당시 소스를 새 final로 소급 대체하지 않는다.
- 원시 XML/console/TAP·환경 출력30개는 Git 제외 `.local-runtime/rf021-raw`에 보존했다. 공유본은 hostname/개인 경로와 행끝공백을 정리하고 redaction-manifest.json에 raw/shared SHA256을 구분했다. 로그의 JWT 문자열은0개였다. 합성 key/email만 사용했고 실제 자격값/실사용자 자료는 사용하지 않았다. prepare-evidence.cjs가 정제 절차다. 새 실행은 새 경로로 보존해야 하며 원시 자료가 없는 초기 도구 실패는 그 한계를 유지한다.
- R21-격리: 부모가 최종 독립 코드/증빙·diff를 검토해 합의 요청 경로의 차단 결함이 없음을 확인했다. before10/final232/frontend58 및 보존 source/hash 정합을 확인했으며 결과를 이 README와 명세에 반영했다. tsc 빈 로그만으로 독립 exit0 확인은 불가하고 담당 실행 tool 결과와 구분한다. 이 담당자는 시작의 read-only Git 상태 확인 이후 Git 명령·commit/push를 하지 않았다.
- R21-정책: 직접 생성 인가·동의/허용 주체는 사용자 보류. 사용자 재개와 제품 계약 확정 후 별도 수행한다.
- R21-연동: 실제 SQL/상품 lock·DB commit/rollback·동시성·Servlet OSIV/Boot HTTP·브라우저·소비자/운영 도달성은 미검증. 이번 실DB 금지와 RF016 안전 identity/fixture·외부차단 미확인으로 미실행했다. RF004 과거 MySQL24는 이번 성공이 아니다. 기존 opt-in2개는 ctor 컴파일만 유지했으며 worker 인증 context가 없으므로 재개 전 worker별 owner principal/email→DB user·finally clear를 추가해야 한다. 후속 승인·안전 target/fixture·소유행 cleanup·새 연결의 상태/SQL 관찰로 검증한다.
- R21-범위: 상품 owner 변경 등 다른 writer, 가격·자기상품구매/판매조건·오염 데이터·탈퇴/토큰폐기/role·상태전이는 별도 정책/명세다. 새 생성 상품lock이 모든 writer/중복경합을 해결한다는 주장은 없다.
- 전체 backend test/build·frontend production build는 운영 설정/외부 fetch 위험 및 선택 범위 때문에 미실행이다. 성능은 사용자 유예로 미측정이며 개선 주장이 없다. 독립 검토를 거쳐 합의 요청 경로만 완료 판정했으며 직접 API 보류·실DB worker 인증 설정 미실행을 포함한 후속 항목은 자동 착수하지 않는다.
