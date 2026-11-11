# RF-024 증빙: 신고 생성 입력 신뢰

- 일자: 2026-11-11. 기준 HEAD `2091fae3b590f0286fe11e8a1354fc27d81e10c6`, 시작 status `?? .tmp/`. 기존 임시 자료 보존.
- 최신 상태: **합의한 신고자·초기 상태 최소 구현·오프라인 선택 검증·공통 독립 최종 리뷰 완료**. 합의 범위의 P0/P1/P2 차단 결함 없음. 유형별 대상 관계 인가는 별도 잔여이므로 RF024 전체 대상 안전성은 부분 완료다. [작업 명세](../../tasks/RF-024.md)의 마지막 실행 결과 절과 이 문서 마지막 절이 아래 최초 분석 이력보다 최신이다.
- 최초 분석 단계에는 RF024 명세와 이 README만 변경했고 실행 검증이 없었다. 이후 승인된 source/test 변경·선택 검증은 마지막 절에 별도 기록한다. BACKLOG/LOG는 통합 담당 소유로 이 담당자는 수정하지 않았다. 새 의존성/스키마/성능 주장 없음.

## 수행 범위와 근거

`git status --short`, `git rev-parse HEAD`, `rg --files`, `rg -n`, PowerShell `Get-Content`로 루트 지침·refactoring README/TEMPLATE/BACKLOG/재평가 순서3·RF007/008/021/022, 신고 controller/service/DTO/entity/repositories, SecurityConfig/TokenFilter/AuthService, Penalty 처리, 상품 응답 mapper, 프런트 신고·관리자·공개/개인채팅 호출, chat 방/메시지 entity 및 local/schema.sql을 읽었다. 기존 ReportControllerTest는 filters=false·실DB 의존 가능성 때문에 실행하지 않았다.

핵심 확인은 요청 reporterId/userId/isReport/productId/privateChatId가 저장 builder로 복사된다는 사실, 미처리 검색이 null이라는 사실, 생성과 관리자 Penalty 실행의 분리다. 정상 상품 UI는 type1 및 상태 생략, 실패 false를 무시하는 성공 표시를 사용한다. privateChatId는 메시지 PK 이름과 같고 방 PK는 별도지만 신고 연결 의미는 미정이다. 운영 악용·실제 저장·현재 DB 제약·전체 유형 목록·외부 호출자 존재/부재는 입증하지 않았다.

2026-11-11 공식 [OWASP API3](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/) 및 [Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)를 열람해 서버 소유 속성·요청별 인가 권고를 확인했다. 이 자료는 유형별 업무 정책이나 privateChatId 의미의 증거가 아니다.

## 원시 자료와 해석 한계

2026-11-11 재개 후 공통 리뷰를 두 문서에 반영했다. 최소 구현 목표는 principal email→DB caller ID, reporterId 생략/null 시 caller 사용·동일 body 허용·불일치403, isReport 생략/null만 허용·true/false400이다. 바인딩 가능한 동시 위조는 caller 인증→reporter403→상태400 순서이며 @Valid로 서비스 이전에 우선순위를 바꾸지 않는다. 대상 유형 전체/privateChatId 관계는 별도 후속이다. 합의한 부분 범위만 후일 완료 판정하며 이번 문서 수정은 구현 완료가 아니다. @DynamicInsert/실제 DB default 때문에 repository 인자 null이 물리 NULL 저장을 보장하지 않는다.

추가로 frontend `createImages`와 backend `ReportImageController.fileUpload`를 읽었다. helper는 res.json()을 반환하지만 caller는 .ok를 읽고, 실제 controller의 정상 반환은 문자열 `reportImages upload`다. 현재 정상 이미지 응답조차 파싱 오류가 날 수 있는 정적 근거이며 실행 재현은 아니다. 명세에 Response.ok/status 기반 판정, 생성 거부/접수 확인/이미지 실패/생성 또는 이미지 결과 불명 분리, 접수 뒤 전체 재제출 차단 계획을 추가했다. backend는 지원하지 않는 확장자도 건너뛰고200을 반환하므로 이미지 HTTP 성공을 개별 저장 성공으로 확대하지 않는다.

- 정적 도구 출력은 대화에 있으며 별도 raw 파일·테스트 XML·실행 로그를 생성/보존하지 않았다. 이 README는 원시 실행 증빙이 아닌 분석 서술이다.
- 테스트 성공/실패/skip 건수 없음. 운영 token/비밀 설정/실사용자 데이터 수집 없음. 로컬 schema 파일의 NULL 기본값·FK는 현재 운영 DB 검사 결과가 아니다.
- 후일 재실행 결과는 날짜·HEAD·환경·fixture·원시 console/XML·source와 함께 별도 절로 남긴다. before snapshot 재생은 최초 실행 원본과 구분한다. 합성 키/JWT도 공유 증빙에서 필요 최소화하고 실제 비밀을 포함하지 않는다.

## 미실행과 추가 검증·재개 조건

| 항목 | 미실행 이유/현재 한계 | 재개 방법 |
|---|---|---|
| 실제 체인 before/after | 이번은 문서 전용 분석 | 구현 지시 후 실제 SecurityConfig/TokenFilter/JWT/AuthService/controller/service+repository 대역. 3역할·본인/생략 caller·위조 reporter+true/false403·본인+true/false400·인증401·refresh·save0·정상201/repository 인자 null 비교. 대상404 정책 신설 없음 |
| 대상 관계 | 유형 ID/필수 조합·privateChatId 의미 미확정 | 업무 표 확정 후 상품 owner/대상 일치 및 메시지/방 참여·교차대상 실패 검증. 임의 상품 전용 제한 도입 금지 |
| 프런트 실패·부분 성공·결과 불명 | helper false 무시 및 res.json()/.ok 불일치, 실제 브라우저 미확인 | 실제 계약의201 JSON reportId·이미지200 plain text Response fixture로 정상 회귀. 생성 거부/5xx/응답 유실/201 파싱·ID 오류, 이미지 거부/응답 유실 분리. reportId 유지·접수 뒤 재제출 차단·자동 재전송0 검증 |
| DB/HTTP/운영/동시성 | DB·서비스 접속 금지, 대역은 commit/기본값/rollback 보장 아님 | RF016 안전 격리 fixture·발송 차단·소유 cleanup·별도 실행 지시 후 NULL 저장·FK·commit/rollback·경합/캐시/브라우저 확인 |
| 응답/이미지/Penalty 정책 | RF044 및 별도 후보 | 담당 범위·정책·실행 조건 합의 후 별도 검증, 자동 착수 금지 |
| 독립 리뷰·성능 | 문서 작성은 독립 리뷰 아님; 성능 유예 | 통합 담당 독립 검토 및 후속 구현 리뷰. 성능은 별도 지시/대표 조건이 있어야 실행 |

BACKLOG RF024 및 정책/통합 잔여, RF044 연계와 범위 밖 후보는 통합 담당자에게 전달한다. 공통 BACKLOG/LOG 갱신을 이 담당자가 완료했다고 주장하지 않는다.

## 2026-11-11 최소 구현·선택 검증 증빙

신고자 email→AuthService DB user ID 확보, reporter 생략/null·동일 허용/타인403, 상태 생략/null만 허용/true와false400을 `ReportServiceImpl.save`에 구현했다. 인증401→reporter403→state400→기존 type/저장 순서이며 ADMIN 대리 예외는 없다. repository 인자 상태는 null이다. 실제 `@DynamicInsert`/default·DB INSERT는 실행하지 않아 물리 NULL 저장은 미검증이다. DTO·entity·전역 security·target 입력 처리는 유지한다.

프런트는 생성 거부, 생성 결과 불명, 접수 확인 뒤 이미지 실패/이미지 결과 불명을 구분한다. 접수 확인 시 reportId를 보존하며 전체 신고 재생성을 하지 않는다. 생성 network/5xx/JSON·ID불명/false200과 부분 성공 뒤 같은 모달의 재제출 및 진행중 연속 클릭을 차단한다.400/401/403 등 명시4xx 거부만 안내 후 의도적 재시도가 가능하다. 이미지 helper는 문자열/JSON 문자열 body를 파싱하지 않고 실제 Response.ok를 판정한다. 이미지200은 개별 파일의 DB/S3 저장 증거가 아니며 서버 멱등성이나 모달 재오픈 중복을 해결한 것이 아니다.

### 실행 환경·결과

- Windows PowerShell/Java17.0.10·기존 Gradle8.7 cache, Node20.10.0/npm10.2.3 및 기존 TypeScript/React. 새 라이브러리/다운로드 없이 offline Gradle 및 설치된 frontend tools 사용. implementation 재개 HEAD는 `2091fae3b590f0286fe11e8a1354fc27d81e10c6`, status는 기존 `.tmp/`와 RF024/039/040/043/044 계획 명세·evidence untracked뿐이었다. 이후 Git 명령/commit/push 없음.
- 실제 SecurityConfig/TokenFilter/JWT provider/verifier/AuthService/controller/service와 repository/cache mocks를 명시 등록했다. Boot scan·실 DB/DataSource/SMTP/SMS/S3/Redis/ES connector·HTTP 서버·브라우저는 실행하지 않았다. 실제 S3/upload bean은 미등록으로 외부 업로드 무호출을 별도 계측하지 않았다. 저장/캐시0은 등록된 repository/cache 대역 상호작용의 검증이다. OS 방화벽 또는 운영 연결 차단 자체의 검증은 아니다.

| 검증 | 결과·보존 artifact | 범위/해석 |
|---|---|---|
| backend before | **2/2**, 실패/오류/skip0. `before/console.txt`, `before/test.xml`, 당시 `ReportCreationSecurityChainTest.java.txt`/`ReportServiceImpl.java.txt` | signed caller가 타인 reporterId와 true/false를 save에 복사, 익명401. 무관 RF016 compile 오류 때문에 **before에만** `selected-tests.init.gradle`로 test sourceSet 제한. 정상 전체 compile 통과가 아님 |
| frontend before | **3/3**. `before/frontend/{console.txt,test.cjs.txt,api.ts.txt,ReportModal.tsx.txt}` |403 false 성공표시/닫기, 이미지200 plain text의 JSON 파싱 실패와 JSON문자열 `.ok` undefined를 당시 실제source+React/fetch 대역으로 재현 |
| backend final | **210/210**, 실패/오류/skip0, assemble exit0. `after/console.txt`, `after/xml/TEST-*.xml` | **정상 전체 compileTestJava sourceSet**, init 미사용. RF024 전용35, 기존 SecurityChain46/TokenFilter72/ProductCacheInvalidation57=175. RF016 담당이 별도 복구한 기존 호환 오류 이후 실행. 타담당의 선택281은 이210의 RF024 증빙으로 합산하지 않음 |
| frontend final | **22/22**, `after/frontend/console.txt`; lint/tsc exit0, `lint.txt`/`tsc.txt`, 기존img warning3 | 실제 API/modal handler를 React hooks/fetch 및 메모리 Response/FormData로 평가.201·기존200 정상/400/401/403/network/500/false200/불명JSON·ID, 이미지200 plain text/JSON문자열·거부/불명, 진행중 중복/거부후 의도적 재시도. 브라우저 렌더/wire 아님 |
| 초기 실패 | `before/wrapper-lock-failure.txt`, `unrelated-compile-failure.txt`, `init-ineffective-failure.txt` | wrapper .lck sandbox 거부(테스트0), 기존 RF016 생성자7/3불일치(compile 실패·테스트0), 첫 init 설정 미적용 같은 실패. 최초 경로 오타는 도구 출력에만 있음. 스스로 만든 빈 nested docs 디렉터리만 제거했고 사용자 파일을 제거하지 않음 |

정상 sourceSet 최종 명령은 [명세](../../tasks/RF-024.md)의 실행 표 아래 그대로 보존했다. 전용35건은3역할에 걸친 정상 body18/위조12/state거부18 요청을 role별 한 case 안에서 검사하며 요청 수를35와 중복 합산하지 않는다. 실제 토큰 principal의 ID=null·email→DB ID 사용, direct entry의 잘못된 principal7종·위조 principal ID무시, unresolved DB caller401·DB장애전파, 위조+state 우선순위, save/type/image/penalty/product/cache0 및 기존 ADMIN PATCH·Penalty 회귀가 포함된다. Penalty는 actual controller/transaction service와 대역으로 실행했으며 이 체인 fixture에 transaction manager/JPA는 없어 물리 transaction 검증이 아니다. 기존 cache 회귀는 recording transaction 대역이다.

통합 담당이 전달한 **공통 독립 최종 리뷰**는 합의된 reporter/초기 상태 범위의 source 및 before2/final210·frontend22·assemble/lint/tsc, raw34개/source14개 정합을 확인했고 P0/P1/P2 차단 결함 없음으로 판정했다. 대화로 전달된 독립 검토 결과이며 추가 원시 실행 로그가 새로 생성됐다고 주장하지 않는다. 유형별 target/privateChatId 관계, 실제 DB NULL, 이미지 직접 인가, 모달 재오픈 멱등성, RF044 응답 및 기존 실DB fixture는 아래 잔여로 남는다.

### 원시 보존·치환·재현

[ARTIFACT_SHA256.tsv](ARTIFACT_SHA256.tsv)에34개 artifact의 원본 SHA256·공유 SHA256·변환·보존 여부가 있다. 원본은 새 전용 `.tmp/rf024-implementation-20261111-originals/`에 보존했고 이전 사용자 `.tmp` 자료를 열거나 수정하지 않았다. 공유본은 home을 `<USER_HOME>`, XML hostname을 `REDACTED_HOST`, 존재하는 compact JWT header 패턴을 `REDACTED_COMPACT_JWT`로 바꾼 뒤 hash를 기록했다. 합성 source key/email은 재현을 위해 남겼으며 운영 비밀/개인정보를 사용·수집하지 않았다. `after/source/`에는 실제 검증 source와 관련 경계 snapshots를 경로별 보존했다. **최종 원본·공유hash34개 불일치0, XML5개 strict parse 성공, home/비치환hostname/실제 compactJWT header 패턴 재검색0**이다. 치환 점검에서 XML attribute의 angle-bracket 마커와 넓은 패턴의 Java package 오탐을 발견해 보존 원본에서 안전한 마커/정확한 header 패턴으로 다시 생성했다. 이 점검은 저장소 전체 비밀 감사가 아니다. 원본/치환본 bytes와 후일 rerun 결과를 구분하며 현재 성공으로 이전 실패/문서전용 시점을 소급 통과시키지 않는다.

### 잔여·재개 조건

| 상태·소유 | 한계/미실행 이유 | 추가 검증·재개 조건 |
|---|---|---|
| RF024-대상 정책/인가: 별도 후속 | 유형표·privateChatId 메시지/방 의미·필수조합·신뢰 조회 경계 미정. target 기존처리 유지 | 유형/자기·중복신고/owner·참여 정책 확정 뒤 독립 명세/교차대상 테스트. 이번 최소 보호를 전체 대상 안전성 해결로 기록하지 않음 |
| RF024-DB/HTTP/브라우저: 미검증 | 물리NULL/default/FK/commit·rollback·경합·OSIV·error dispatch/실HTTP/Next rewrite·브라우저·외부소비자는 실행금지/안전환경 미확보 | RF016 identity/소유fixture·발송차단/cleanup 및 별도 실행지시 후 실제 전후행·물리NULL·응답/UI를 확인 |
| 범위밖/RF044·후속후보 | 개인정보 응답 축소는 RF044. 이미지 인가/파일별저장·부분보상, Penalty 입력, 기존오염행·서버멱등성/재개중복 미해결 | 후보별 정책·명세·실행범위 합의 뒤 별도 진행. 기존 행 자동수정 없음 |
| 리뷰/성능·전체검증 | 합의 최소 범위 공통 독립 최종 리뷰 완료·P0/P1/P2 차단 없음. 전체 backend test/build·frontend production build·성능/운영서비스 미실행 | 새로운 변경은 동일 경계 회귀·리뷰 필요. 전체/성능은 안전환경/대표조건·별도지시 필요 |

기존 실DB 의존 `ReportControllerTest`의 filters=false·무인증·false201 fixture는 강화된401/400 계약과 맞지 않아 실행/수정하지 않았다. 안전 환경·합성인증·소유fixture·상태생략으로 이관 후 재개하며 이 테스트가 현재 통과한다고 주장하지 않는다. 외부 false 입력 소비자 확인/고지도 잔여다.14개 final source snapshot은 현재 작업 파일 SHA256과 전부 일치함을 별도 대조했다.

BACKLOG/LOG 통합 담당에게 위 상태를 전달했으며 이 문서 갱신은 해당 공통 추적 갱신을 대신하지 않는다. 새로운 의존성·schema·실서비스 접속·성능 주장·자동 commit/push 없음.
