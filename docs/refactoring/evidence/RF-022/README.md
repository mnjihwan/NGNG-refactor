# RF-022 증빙 및 미실행 검증

- 기록일: 2026-11-10. 기준 HEAD `e417eaf7d476a639413414ff8f4423b89cc88e66`, 최초 작업 트리 clean.
- 최신 판정: 승인된 최소 서버·프런트 구현/격리 검증 및 독립 리뷰 완료. 서버28건/assemble, 프런트 전용42건·관련 묶음91건/lint/tsc 확인. 초기 분석 이력과 실제 실행을 구분하며 실DB/운영 인가 완료가 아니다.
- 공유 증빙: `raw/`의 사용자 홈 경로·XML host metadata를 치환했다. [ARTIFACT_SHA256.tsv](ARTIFACT_SHA256.tsv)에 원본/공유본 해시·변환·원본 보존 여부를 구분했다. 아래 “원본” 표기는 이 변환 고지를 함께 읽는다.
- 상세 근거·공식 자료·정책 선택·단계별 검증은 [명세](../../tasks/RF-022.md)를 따른다.

## 실제 수행 범위

Windows PowerShell에서 루트 AGENTS, 운영 README, TEMPLATE, BACKLOG/재평가, RF005/004를 읽고 `git status --short`, `git rev-parse HEAD`, `rg --files`, `rg -n`, `Get-Content`로 chat 설정·controller·service·entity·DTO·repository·build/test/Dockerfile, backend JWT/AuthService, frontend chat/product API·STOMP 세 연결부·Next rewrite 및 로컬 설정 예제를 정적 확인했다. 토큰/비밀번호 등 실제 비밀 설정은 읽거나 저장하지 않았다. Spring/OWASP 공식 페이지를 직접 열람했다. 최신 문서와 Boot3.2.4의 버전 차이는 명세에 표시했다.

관찰: chat Security/JWT 의존성 및 인증/인가 코드를 발견하지 못함, path/body ID를 사용한 저장·구독·발행 경계, GET의 읽음 쓰기, PUT/DELETE의 실제 메시지 ID 의미, backend sub=email, 프런트 CONNECT 토큰 미전송, 운영 proxy 미확인. 이는 정적 경로와 검색 결과이며 운영 요청이 성공했다는 증거가 아니다.

초기 파일 탐색에서 frontend 경로를 src 없이 지정해 경로 없음이 발생했고 src 경로로 바로잡아 확인했다. AuthService도 실제 user/user/service 경로를 재탐색했다. 긴 문서 결합 출력 일부는 잘렸으므로 재평가와 RF022 관련 BACKLOG는 별도 rg로 확인했다. 검색 오류를 테스트 실패/보안 결과로 세지 않는다.

## 원시 증빙 구분

탐색 명령·출력과 웹 열람은 대화 도구 결과에만 남아 있다. 별도 원시 콘솔/XML/소스 snapshot/네트워크 캡처/해시 manifest는 보존하지 않았다. 이 README는 분석 요약이며 실행 원본이 아니다. 후일 실행은 그 시점 커밋/환경/명령/fixture와 새 증빙으로 기록하고 이번 분석을 소급 통과로 바꾸지 않는다.

## 미실행·한계·재개 조건

| 항목 | 상태와 이유 | 추가 검증/재개 조건 |
|---|---|---|
| 실제 REST 필터/CONNECT/SEND/SUBSCRIBE | 미실행, 이번 문서 전용 범위 | 핵심 정책/키 공유/의존성 승인 반영 후 명시 Spring context+대역. 직접 controller 호출과 실제 channel/broker 테스트 구분 |
| 인증 검증 라이브러리 및 키 공유 | 사용자 승인 완료, 구현/설정 주입 미실행 | 기존 backend와 같은 JJWT 계약+필요 Spring Security 의존성으로 합성 키 검증 가능. 운영 공급·회전·alg·email→ID 유일성은 배포 잔여. 원격 introspection/JWKS는 도입 안 함 |
| 무쓰기·무발행·읽음 불변 | 정적 위험 후보만 확인 | 실패 단계별 repository save0/read-pointer 불변/outbound0, 정상 목적지 2개 및 타인 수신0 검증 |
| 구독 수명·만료·logout·권한 철회 | 미검증 | exp 종료·재연결·토큰 갱신·기존 구독 해제 및 정책 변경 시 철회 방식 확정 |
| 공개 익명/비공개 참여 및 세부 추론 | 핵심 사용자 결정 완료, 세부 최소안 명세에 기록 | 토큰 없는 CONNECT+공개 SUBSCRIBE 허용, 잘못된 토큰 거부, 비공개 인증 참여자만. 역할 추가 제한/관리자 감청 예외 없음. 테스트로 고정 |
| 실DB/Boot HTTP/WebSocket·프록시·WSS/origin·브라우저 | 미접속/미실행, 배포 자료 없음 | RF016 안전 fixture·전용 신원·발송 차단·소유 cleanup·별도 지시와 실제 배포 경로 확인 |
| 전체 test/build/성능 | 미실행, Boot 전체 테스트는 외부 환경 의존 가능 | 전용 선택 suite로 시작, 패키징과 실제 실행 구분. 성능은 별도 지시 후 |

RF025~028/030/040 및 RF021/023은 별도 후보이며 이번 계획이 자동 착수 승인이 아니다. BACKLOG RF022와 재평가 1A에 연결하며 세부 RF022-신뢰/정책/격리/연동 상태는 통합 담당자에게 전달한다. 공통 BACKLOG/LOG는 이 담당자가 수정하지 않았다.


## 2026-11-10 승인 반영 기록

사용자가 기존 backend HMAC 키 공유 검증과 필요한 security/JWT 의존성 추가, 공개 채팅 익명 조회·구독 유지, 비공개 채팅 인증 참여자 한정을 승인했다. 이전 미결정 상태는 이력이며 최신 상태는 승인 반영·구현/실행 전이다. 명세 최신 절에 익명 CONNECT와 잘못된 토큰 거절의 구분, 역할 추가 제한 없는 참여자 검사, buyer 본인·상품 seller 검증, REST 오류와 STOMP ERROR 구분 및 실채널 테스트를 구체화했다. 세부 사항은 사용자 개별 지시가 아닌 최소 구현 추론임을 명시했다.

HMAC 공유의 발급 능력 확대는 승인된 구조의 잔여 위험이며 다시 승인 대기시키지 않는다. 운영 비밀 공급/회전·gateway/WSS/origin·실DB/email 유일성은 미검증이다. 합성 키/대역 기반 실제 inbound channel→handler/broker→outbound capture 검증은 이 운영 자료 없이도 준비할 수 있다. CONNECT principal 유지·익명 공개 수신·인증 참여자 수신·타인 차단·broker 직접 SEND·만료 후 기존 구독 수신 중단과 저장/읽음 무쓰기·업무 무발행을 검사하도록 계획했다.

이번 후속 작업도 두 문서만 갱신했으며 코드·설정·라이브러리 설치·test/build·DB·서비스 접속은 하지 않았다. 승인 사실과 계획은 실행 증빙이 아니다. 추가 원시 테스트/XML/로그는 없고 사용자 결정 전달 및 문서 검토는 대화에만 남아 있다. 기존 정적 근거와 향후 실행 결과를 분리 보존한다.

## 2026-11-10 chat 구현·실행 증빙

사용자 승인 후 최소 Security/JJWT dependency와 REST·CONNECT/SEND/SUBSCRIBE·세션별 expiry·최종 transport 전송 guard를 구현했다. 상세 파일/정책/검증 범위·한계는 [명세의 chat 구현 결과](../../tasks/RF-022.md#2026-11-10-승인-범위-구현격리-검증--chat-서버)를 따른다. 기존 “분석만/실행 전” 서술은 당시 이력이다.

- 기준선은 source 변경 전 `ChatBoundaryBaselineTest` 1건이며 실제 channel/simple broker에서 무인증 private 구독과 broker 직접 SEND 수신을 확인했다. 당시 source/XML/console을 `raw/before-test.java.txt`, `before-test.xml`, `before-console.txt`로 보존했다. HTTP/security 필터나 실제 StompSubProtocolHandler를 거친 변경 전 기준선은 실행하지 않았다.
- 최종 `./gradlew.bat --offline --no-daemon test --rerun-tasks --tests 'com.ngng.chat.security.*Test' assemble`: **28건, 실패0/skip0**, compileJava/compileTestJava·bootJar/jar/assemble 성공. `raw/after-suite-console.txt`와 `raw/TEST-com.ngng.chat.security.{ChatTokenVerifierTest,ChatRestSecurityTest,ChatStompSecurityTest,ChatSessionExpiryTest}.xml`은 이 최종 전체 선택 실행의 원본이다.
- JWT6건은 backend 호환 HMAC3종·exact Bearer/access·필수 claim·서명/만료·email 단일 매핑·비대칭/unknown alg·잘못된 exp/nbf·repository 오류 경계를 검사했다. JJWT object exp의 실제 IllegalStateException을 재현했고 parse 영역만 인증실패로 바꿨다. MockMvc8건은 실제 SecurityFilterChain+controller+기존 service와 repository대역으로 공개 익명/본인/참여자/타인·소유 수정/삭제·읽음 정상/거부 무쓰기·정상404/잘못된JSON400/인증실패401을 검사했다.
- handler12건은 실제 WebSocket mapping의 decorated handler→StompSubProtocolHandler→clientInboundChannel→controller/simple broker→clientOutboundChannel→가짜 WebSocketSession을 거친다. CONNECT 이후 principal 유지/handshake·native 주체 위조 거부, 공개 익명 수신·쓰기 인증, 저장방/path/상대/작성자 검증, private 참여·own alarms·직접 broker SEND, 정상 `.body`와 정확한 상대 알림, generic ERROR·종료/서비스 실패 무발행, 다른 exp의 같은 사용자 세션·broker 구독 정리·outbound queue를 통과한 뒤 clock 이동한 최종 write 차단을 검사했다. 전용 expiry2건은 실제 production scheduler의 idle 종료와 broker DISCONNECT 실패 중에도 transport를 닫는 경로다.
- 명시 context에는 DataSource/JPA/AMQP/외부 connector bean이 없고 모든 repository는 Mockito다. endpoint URI는 가짜 세션의 문자열이며 실제 listener/브라우저/네트워크 ws·DB·서비스에 연결하지 않았다. Gradle은 offline이고 기존 cache만 사용했다. OS outbound firewall 설정을 검증한 것은 아니다.
- 중간24건 중 2실패는 `raw/after-intermediate-24-console.txt`, `after-intermediate-24-handler.xml`로 보존했다. 잘못된 heartbeat fixture(NULL 추가)는 수정했다. 공격3 ERROR 대기 timeout1건은 원인 미확정으로 기록한다. 이후 handler 선택 재실행과 최종28건 및 같은 handler12건 추가 rerun이 성공했으나 당시 실패를 통과로 고치지 않는다. 추가12건 원본은 `raw/after-handler-repeat-console.txt`, `after-handler-repeat.xml`이며 최종28건 XML과 구분된다. 장기 안정성·부하 검증은 아니다.
- `raw/after-first-console.txt`는 초기 compile 실패, `after-handler-console.txt`는 초기 handler6건 성공, `after-handler-refine-console.txt`는 후속 handler 선택 실행이다. 일부 초기 경로/Clock import/Mockito 오류 및19건 중2실패의 원본은 별도 보존하지 못했으며 최종 결과의 증빙으로 대체하지 않는다. 실행 전 최초 cache-lock 권한 거부는 대화에 남고 승인된 offline cache 접근으로 재개했다.
- `raw/dependencies-runtime.txt`는 실제 resolved Framework6.1.5/Security6.2.3/JJWT0.11.5 report다. 새 Security/JJWT 라이브러리 다운로드나 DB schema 변경은 없었다. 선택 suite를 추적하기 위해 chat `.gitignore`는 security 테스트 java만 허용했으며 기존 ignored Boot 통합 테스트는 그대로 제외했다. `git diff --check -- NGNG-chat-server` 성공. chat 담당은 commit/push를 수행하지 않았다. 병렬 작업으로 검증 후 HEAD는 `6bc7a56bab1a94c1f79f0c96970258fd89c08150`였고 최초 기준 HEAD와 구분한다.

운영 secret/key 공급·회전·키 길이/alg·email 유일성/오염행, WSS/origin allowlist·gateway/direct-port·실DB commit/read-pointer·실제 브라우저와 전체 통합/build·성능은 미검증이다. default origin `*`는 기존 계약이며 `chat.allowed-origin-patterns` 운영값 주입 확인이 필요하다. HMAC 공유가 chat에 발급 능력도 준다는 승인된 위험을 유지한다. 실제 wire/브라우저는 RF016의 안전 fixture·발송 차단·소유 cleanup·별도 지시 후 확인한다. 즉시 분산 토큰 폐기·방 원자성/읽음 동시성·이미지/개인정보·장기 안정성은 별도 후보와 재개 조건으로 통합 담당자가 BACKLOG/LOG에 추적한다. 프런트 구현/실행 결과와 최종 독립 리뷰는 이 chat 결과를 근거 없이 확장하지 않고 별도 통합 기록한다.

## 2026-11-10 프런트·독립 리뷰 최종 통합

최신 판정은 **승인된 최소 구현·격리 검증·독립 리뷰 완료**다. 실DB/운영 배포·실브라우저·성능 완료가 아니다. 아래 결과는 frontend 담당의 handoff/source/기존 console과 통합 담당의 독립 리뷰 전달을 대조해 기록했으며 문서 통합 시 테스트를 재실행하지 않았다. 앞서 남긴 프런트/리뷰 대기는 당시 이력이다.

### 프런트 구현·검증

세 실제 호출부는 `beforeConnect`에서 최신 **sessionStorage** access를 읽고 공개 익명은 Authorization을 생략한다. 비공개/알림은 자격증명 필수, ERROR 후 익명 fallback/재시도 없음, 네트워크 재연결은 최신 access를 사용한다. effect cleanup이 실제 생성 client를 force deactivate하고 identity 변경/logout/늦은 callback을 막는다. `.body`·서버 echo를 유지하고 HTTP/JSON 실패는 오류 UI로, 알림 실패는 무알림으로 처리한다. login/logout 및 기존 backend 응답 token 갱신은 같은 창 연결을 갱신한다. BroadcastChannel은 credential/profile/chat 정보가 없는 invalidation signal만 보낸다. 미지원/차단된 브라우저의 다른 팝업 즉시 logout 전파는 보장되지 않아 서버 exp까지 남을 수 있다.

| 증빙 | 확인 결과·한계 |
|---|---|
| `raw/frontend/before-console.txt`, `before-public.tsx`, `before-private.tsx`, `before-alarm.tsx` | 변경 전 component+React/STOMP 대역3/3. CONNECT 인증과 실제 생성 client cleanup 부재를 확인. 브라우저/wire 아님 |
| `before-snapshot-replay-console.txt`, `rf022-chat-auth-test-source.cjs`, `source-sha256.txt` | 보존 source를 현재 harness로 후일 replay3/3. 최초 결과와 별개. source hash는 아래 전체 artifact manifest와 구분 |
| `after-console.txt` | RF022 전용42/42 exit0. 설치된 실제 `@stomp/stompjs`7.0.0 Client+메모리 socket의 CONNECT 직렬화·토큰 변경 두 번째 CONNECT·강제 종료·dispose 취소 포함. 나머지는 React/STOMP/fetch/Notification/BroadcastChannel 대역 |
| `all-tests-console.txt` | RF022/RF004/RF007/RF008 명시4파일91/91 exit0.42건 포함, RF021 신규 suite 제외. source 파일명·재실행 명령은 작업 명세 최종 통합 표 참고 |
| `lint-console.txt`, `tsc-console.txt` | lint0, 기존 img 경고3건; `tsc --noEmit --incremental false`0. production build/브라우저 미실행 |
| `after-first-console.txt`, `all-tests-first-console.txt`, `tsc-first-console.txt` | 당시11/11·60/60 성공과 중간 tsc duplicate property 실패를 최종42/91과 구분. duplicate는 수정됐으며 hook 최종 변경 없음. 담당이 보고한 Windows wildcard/Copy-Item 오류의 전체 원본은 보존되지 않아 성공으로 세지 않음 |
| `HANDOFF.txt` | 담당 범위·실행·한계 전달 자료. 원시 실행 로그 자체는 아니며 위 console/source로 교차 확인 |

환경은 Windows PowerShell/Node20.10.0/npm10.2.3이다. 신규 frontend package/dependency는 없다. `chat/_api.ts`와 `chat/[roomId]/PrivateChat.tsx`는 RF021과 공유하지만 RF022 소유는 채팅 메시지/목록 GET non-ok와 error 표시다. 거래 요청 create/update·모달 async/await 및 성공/실패 처리는 **RF021 소유**이며 대신 적용한 hunk도 RF022 결과로 귀속하지 않는다. 다른 RF의 신규 테스트 결과를 이번42/91 결과에 합산하지 않는다.

chat에는 refresh endpoint를 만들지 않았다. 기존 backend 보호 요청의 기존 refresh/header 소비가 access를 바꾼 경우 또는 재로그인 후 재연결한다. backend 요청 없이 만료되면 ERROR/닫힘과 로그인 안내가 남을 수 있다. 임의 업무 요청을 refresh 목적으로 추가하지 않았다. 실제 브라우저 만료·갱신/두 창 전파·in-flight HTTP refresh 이후 logout/roleToken cookie·분산 즉시 폐기는 미검증 또는 RF030/040의 별도 정책이다.

### 독립 리뷰

통합 담당이 전달한 별도 독립 리뷰에서 이전 P2 2건 수정, 서버28/28·handler12 재실행·assemble, 프런트42/42·묶음91/91·lint/tsc를 확인했고 **추가 차단 결함 없음**으로 판정했다. JWT temporal claim 예외/형식 회귀를 포함한다. 이 리뷰 전달은 대화 근거이며 별도 reviewer raw test 실행을 생성했다고 표시하지 않는다. 서버 단발 ERROR timeout1건은 원인 미확정으로 유지하고 후속 통과가 그 실패를 지우지 않도록 원본 실패 증빙을 보존한다.

### 공유본 변환·원본 보존 점검

[ARTIFACT_SHA256.tsv](ARTIFACT_SHA256.tsv)는33개 artifact마다 원본/공유본 SHA256, 변환, 원본 bytes 보존 여부를 기록한다. 사용자 홈 경로는 `<USER_HOME>`, XML hostname은 `REDACTED_HOST`로 치환했다. frontend 원본은 공유본 밖 `.tmp/rf022-frontend`에 남고 `source-sha256.txt` 공유본의 path만 치환됐다. 서버의 변환된 일부 과거 log/XML은 치환 전 bytes를 별도 보존하지 않아 **원본 hash만** 남는다. 치환 없는 artifact는 bytes 동일하다. 문서화된 실행 결과·개인정보 치환 공유본·미보존 원본을 구분하며 SHA256만으로 원본 재현/복구가 가능하다고 주장하지 않는다.

공유본의 XML 파싱·33개 shared hash 일치, compact signed JWT/email/home 경로 pattern 재검색과 선택 source/console 검토를 수행했다. 해당 pattern 잔존0이며 실제 key/token/사용자 개인정보를 수집·발견하지 않았다. fixture literal과 의도된 합성 source는 실제 사용자 데이터가 아니다. 이 점검은 전체 저장소 secret 감사가 아니다. source snapshot의 원래 SHA256 값은 유지하고 경로 치환 전후 파일 hash를 별도 기록했다.

### 완료 후 남은 검증

- **원인 미확정 timeout/안정성**: 중간 attack3 ERROR 대기 timeout1건과 후속 성공을 모두 유지한다. 부하/장기 안정성·원인 분석은 대표 환경·별도 실행 지시 후 재개한다.
- **신뢰/배포**: **HMAC 공유로 chat도 서명 생성 능력을 갖는다.** 사용자 승인된 위험이지만 실제 key 공급·회전·폐기·접근권한/alg/길이·email 유일성은 미검증이다. production default origin은 `*`이며 allowlist 주입/gateway/WSS/direct port·헤더 정제도 미검증이다. 배포 자료와 안전한 전용 환경을 확보해야 한다.
- **실DB/실브라우저**: server network upgrade/진짜 WebSocket·HTTP/DB/AMQP persistence·read-pointer/rollback/브라우저·팝업 BroadcastChannel 지원 matrix·만료→재발급→재CONNECT는 미실행이다. RF016 안전 fixture·발송 차단·소유 cleanup·별도 실행 지시 후 확인한다. OS firewall 자체도 검증하지 않았다.
- **빌드/성능/별도 후보**: server assemble는 패키징이며 전체 Boot/test/build를 대신하지 않는다. frontend npm build와 실제 성능/부하를 실행하지 않았다. 원자성/읽음 동시성/이미지·개인정보/즉시 토큰 폐기 등 RF025~028/023/030/040은 별도 승인·명세로 추적하며 자동 착수하지 않는다.

이번 통합은 task/evidence와 공유 raw 정리에 한정했다. 프런트/서버 source·타 RF·BACKLOG/LOG 변경이나 Git 명령·commit/push·추가 서비스/DB 접속은 수행하지 않았다. 공통 추적의 최종 반영은 통합 담당자 소유다.
