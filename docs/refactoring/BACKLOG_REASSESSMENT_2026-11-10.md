# RF009 이후 백로그 재평가 — 2026-11-10

기준 HEAD `83494a8`(RF009 PR #11 merge), 분석 시작 작업 트리 clean. 사용자가 신규 후보 조사와 기존 미진행·완료 후 잔여의 우선순위 재산정을 요청하여 작성한 연구 산출물이다. 소스·설정 변경, 테스트 실행, DB 접속, 운영/프록시 확인은 하지 않았다. 정적 탐색은 전수 보안 감사나 운영 결함 재현이 아니다. 도구 탐색 출력은 대화에만 있으며 별도 raw 파일은 보존하지 않았다. 기존 실행 수치는 각 RF 명세/증빙의 과거 결과이며 이번 재실행 결과가 아니다.

기존 [BACKLOG](BACKLOG.md)의 이력과 각 `tasks/RF-NNN.md`, `evidence/RF-NNN/README.md`가 상세 근거다. 신규 번호는 기존 마지막 RF021 뒤부터 부여했으며 번호가 실행 순서나 우선순위는 아니다. 아래 후보 등록은 구현·정책·환경 사용 승인이 아니며 자동 착수하지 않는다. 신규 의존성·스키마·인가/응답/업무 정책 변경은 별도 합의가 필요하다.

## 판정과 우선순위 기준

- **확인 사실:** 현재 코드의 분기·호출·저장·응답 필드 또는 보존된 과거 테스트 결과. 정적 호출 경로만으로 운영 악용·발생 빈도를 확정하지 않는다.
- **가설/미검증:** 실제 필터/프록시 도달성, 운영 데이터·제약·배포 상태, HTTP/브라우저 결과, 장애 빈도와 성능 효과. 재현 전에는 이를 통과 또는 사고로 기록하지 않는다.
- **P0:** 타인 권한/민감정보·금전·거래 정합성에 직접 영향을 줄 수 있는 구체적 경로의 우선 조사·정책 결정. 모든 P0가 운영 사고 확정이라는 뜻은 아니다.
- **P1:** 반영된 변경의 물리 통합 검증, 실행/데이터 안정성 및 안전한 검증 기반.
- **P2:** 국소 기능 계약·유지보수 정리. 실제 영향 확인 시 상향 가능.
- **P3:** 사용자 유예 성능 작업 및 낮은 긴급도의 증빙 보완. 유예 해제 전 실행하지 않는다.

위험 등급과 실행 의존성은 별개다. RF016 최소 기반은 P1이지만 실제 DB/HTTP 실행 전에 필요하다. 모든 RF016 완료를 대역 기반 P0 재현의 선행조건으로 만들지 않는다. 운영 노출·실제 피해 확인 시 우선순위를 다시 판단한다.

## 신규 및 기존 잔여에서 승격한 후보

경로 약어: `B`=`NGNG-backend/src/main/java/com/ngng/api`, `C`=`NGNG-chat-server/src/main/java/com/ngng/chat`, `F`=`NGNG-frontend/src`. 행은 위 기준 HEAD의 위치다. 아래 표의 테스트는 **계획**이며 이번에는 실행하지 않았다. 공통 재개 조건은 담당 범위/정책 합의, 안전한 격리 환경·합성 인증·외부 발송 차단, 소유 fixture/key의 정확한 정리와 독립 확인이다. 기존 스키마 제약은 확인 없이 있다고/없다고 단정하지 않는다.

| ID / 위험 | 확인 사실·코드 근거 | 영향 가설·미확인 | 경계·선행조건 및 독립 검증/승인 |
|---|---|---|---|
| RF022 / P0 | 채팅 REST/STOMP 인증·방 참여·목적지. `C/publicChat/controller/PublicChatController.java:34,55,60,76,81`에서 body/path ID와 목적지를 사용. `C/WebSocketConfig.java:15,21` broker/전체 origin 설정. chat main/build 검색에서 Principal·SecurityFilterChain·ChannelInterceptor·security starter를 발견하지 못함 | 타인 방 조회/발신·구독/알림 목적지 위조 후보. 외부 프록시 인증·노출 여부 미확인; 백엔드 TokenFilter를 별도 chat 서버에 적용된 것으로 가정하지 않음 | REST와 CONNECT/SEND/SUBSCRIBE 각각 역할·참여자·body/path 불일치 검증. 프록시 자료 확보. RF021은 거래 요청 인가, 이 항목은 채팅 경계. 인증/구독 정책 수정 승인 필요 |
| RF023 / P0 | 거래 단건 `B/transaction/service/TransactionDetailsService.java:39` ID 조회 후 DTO 반환, `controller/TransactionController.java:24` 위임. `dto/ReadTransactionDetailsDTO.java:25` 거래 address, `:114~121` 당사자 이름/전화 포함 | 유효 인증된 제3자의 개인정보 조회 후보. User 전체/비밀번호 응답이라고 표현하지 않음 | RF021의 쓰기와 분리. 실제 TokenFilter 포함 당사자/제3자/관리자/미인증 응답 재현, 읽기 정책 합의 |
| RF024 / P0 | `B/report/report/service/ReportServiceImpl.java:87,97,99` 요청 reporterId/isReport를 저장 | 신고자 위조·처리상태 신뢰 후보. isReport 저장이 즉시 Penalty 제재 실행이라는 뜻은 아님 | 생성 주체·초기상태 정책 확정, 실제 체인 및 상태 무쓰기 검증. RF044 응답 축소와 분리 |
| RF025 / P1 | `C/privateChat/service/PrivateChatRoomService.java:32,34,40,45` 방/두 읽음행 별도 save, `:52` 조회와 생성 별도. `PrivateChatMessageService.java:31,33,43` 메시지 저장 뒤 방/후속 처리 | 부분 저장·중복 방·메시지 재전송/전송 실패 후보; 운영 유일 제약·보장 미확인 | RF022 참여 정책 선행, 단계별 실패/동시 생성/응답 유실 재현. 방 생성 원자성과 메시지 전달·재전송은 별도 하위 시나리오. 유일 제약/멱등키 스키마는 별도 승인 |
| RF026 / P1 | `C/privateChat/service/PrivateChatMessageReadService.java:14` 조회 후 메시지 포인터 save. `PrivateChatRoomService.java:66~85` 읽음행 없으면 접근 위험, 읽은 메시지를 recent로 사용, 미읽음 포인터 null이면 count0. `PrivateChatMessageService.java:127~139` GET 과정 읽음 갱신 | 동시 읽음 회귀·최근 메시지/미읽음 수 불일치 후보 | RF022 읽기 권한, RF025 초기행 원자성과 연결. 빈 방/누락행/역순 동시읽기/신규메시지 검증, 읽음 정책 합의. 성능 최적화와 분리 |
| RF027 / P1 | `F/app/chat/[roomId]/_components/PrivateChatting.tsx:59` 및 `F/app/product/[id]/_components/Chatting.tsx:51` blob URL 전송. `F/app/chat/_api.ts:34~43` isImage, `C/privateChat/dto/request/CreatePrivateChatMessageRequestDTO.java:14` contentType | 다른 브라우저/재접속에서 이미지 재현 불가 및 형식 누락 후보 | 두 브라우저/재접속·공개/비공개 채팅 저장→조회→렌더 계약 검증. 영구 업로드 정책/저장 수명 합의, RF015 업로드 실패 보상과 별개 |
| RF028 / P2 | `F/app/chat/[roomId]/_components/PrivateChatting.tsx:137~165`, `F/app/product/[id]/_components/Chatting.tsx:129~150` client 생성/activate·cleanup이 state client 참조. `F/providers/stompAlarmSubscribe.tsx:34~66`는 render 중 Client 생성·activate하고 cleanup이 없으며, `:14`에서 존재 여부의 안전한 검사 없이 Notification 전역을 참조 | stale closure·중복 연결/구독·unmount/로그아웃 후 이전 사용자 알림 수신, Notification 미지원 환경의 예외 후보. 실제 재현 미실행 | mount/unmount·방/사용자 전환·로그아웃·StrictMode·재접속 때 연결/구독 개수·이전 목적지 해제 검증. Notification 미지원/권한 거부 환경 회귀 포함. RF022 인증 수명·RF030 로그아웃·RF029 알림 mutate 캐시 키와 연결 |
| RF029 / P1 | `F/app/chat/_hooks/usePrivateChatRoom.ts:13`, `usePrivateChatMessgae.ts:82` 사용자/방 값 없는 고정 SWR 키 | 사용자/방 전환 시 잘못된 캐시 데이터 표시 후보; 실제 캐시 provider 수명 미검증 | A→B 계정/방 전환·mutate·로그아웃·느린 응답 순서 검증. RF010 backend DTO 캐시와 별개, RF030과 로그아웃 회귀 공유 |
| RF030 / P1 | `F/providers/UserContext.tsx:42~44` user만 제거, accessToken 삭제 주석. `F/app/my_page/_compoenets/MyInfo.tsx:25~27` 서버 logout 호출 경로와 다른 logout 호출자 존재 | 로그아웃 뒤 클라이언트 인증 상태 잔존 후보 | 모든 logout 진입·실패/다중탭/새로고침·후속요청 검증. 서버 폐기 RF040과 구분, RF029 캐시 정리와 연결 |
| RF031 / P2 | `C/privateChat/service/PrivateChatMessageService.java:146` Long ==. 방 목록 `PrivateChatRoomService.java:94`는 Objects.equals | 같은 값 다른 참조의 거래요청 누락 후보 | RF006 backend 비교 수정과 다른 코드. 서로 다른 boxed Long·작은/큰 값·null 계약 검증 후 최소 변경 |
| RF032 / P1 | `B/user/user/service/JoinService.java:27,34` exists 검사 후 save. 엔티티 `User.java:28,33` email/phone 필드 | 동시 중복 가입·오류 계약 후보, 운영 UNIQUE 제약 여부 미확인 | 실제 제약 읽기 확인과 동시 이메일/전화 가입, rollback/초기 포인트 검증. 제약 추가/기존 중복 복구는 별도 스키마·정책 승인. RF043 인증 증명과 분리 |
| RF033 / P1 | `B/user/user/controller/ConfirmController.java:30,35,47,52` 계좌번호/주소 로그 인자 포함 | 로그 수집/열람 경계의 개인정보 노출 가능. 실제 보관/접근권한 미확인 | 합성 PII로 성공/실패 로그 검사, 마스킹/필요필드·보관 정책 합의. RF020/RF041 인가와 별개 |
| RF034 / P1 | `B/product/product/controller/ProductController.java:78` 파일명 분기, `service/UploadService.java:27,40` ImageIO와 입력 stream | 잘못된 형식·과대 이미지·자원 수명/실패응답 후보; 실제 누수/고갈 미측정 | 크기/픽셀/확장자-MIME 불일치/빈·손상 입력과 stream 정리 검증. RF015 S3/DB 보상, RF018 watermark 배포자원과 분리. 허용 형식·상한 정책 승인 |
| RF035 / P0 | `B/user/point/service/PointHistoryService.java:110~127` 양수/잔액 검증 뒤 body paymentCost 차감, payProductId는 설명에 사용 | 실제 상품·거래·가격과 결제 금액 결합 부재 후보. RF003 양수/잠금 완료와 별개 | 상품/거래 기준 가격·구매자·확정 상태·충전 신뢰 경계까지 조사하고 범위 확정. 위조 금액/상품/타인거래 재현. 금액 정책 변경 별도 승인 |
| RF036 / P0 | R3-멱등성 이관. `evidence/RF-003/README.md` 동일 payment 재전송 별도 차감 확인 | 중복 차감 위험은 격리 fixture에서 확인; 운영 빈도 미측정 | 키 주체/범위/수명·재시도 응답 합의 후 동시 중복/응답 유실. 스키마 필요 시 승인. RF035 가격 검증 및 RF037 다단계 실패와 별개 |
| RF037 / P0 | R3-거래원자성 이관. `F/app/my_page/_compoenets/modal/BuyProductDetails.tsx:35~40` payment 성공 후 상태 호출, RF003 증빙은 결제 실패 시 후속 차단만 확인 | 결제 커밋 후 거래 상태 실패 불일치 미해결 | RF035/038 계약과 복구·보상 정책 먼저 합의, 두 단계/응답 유실 장애 주입. 단순 frontend 순서 변경으로 원자성 주장 금지, 구조/스키마 변경 별도 승인 |
| RF038 / P0 | R6-정책의 상태전이 이관. `B/transaction/service/TransactionDetailsService.java:120~143` 소유자 검사 뒤 요청 statusId 조회/설정 | 상태 역행·건너뛰기·결제 미충족 전이 후보; 정상 상태 머신 미확정 | 당사자/관리자별 전이·결제 전제 합의, 동시/반복/역순 전이 재현. RF006 값 비교 완료·RF004 수락 원자성·RF021 요청 인가와 분리 |
| RF039 / P0 | R7-범위 추가 상품 writer 인가 이관. `B/product/product/controller/ProductController.java:41,64,71`, `productImage/controller/ProductImageController.java:28` 생성/끌올/업로드 경로. RF007 PUT/DELETE guard 범위 밖 | 생성 userId·타인 이미지/썸네일/끌올 변경 후보 | writer별 실제 principal/상품 소유권·ADMIN 계약·무쓰기/캐시 검증. RF007 완료를 전체 writer 인가로 확대하지 않음. RF015/034 업로드 처리와 분리 |
| RF040 / P0 | R5-범위 폐기/역할 최신화 이관. `B/global/security/filter/TokenFilter.java:38~52` claims로 인증/재발급, `jwt/util/JwtTokenProvider.java:39,54` role claim, `handler/CustomLogoutHandler.java:27` 쿠키 만료 | 회수된 역할/탈퇴/복사 토큰의 사용 가능성 후보, 서버 운영 정책 미확정 | 로그아웃/탈퇴/강등·refresh 재사용·만료 후 후속요청 검증. RF005 파싱 정책 완료와 구분. 저장소/토큰 회전·CSRF/CORS 정책 별도 합의; RF030은 UI 수명 |
| RF041 / P0 | RF020 계좌와 분리한 주소 객체 인가. `B/user/user/service/ConfirmService.java:71~75` 요청 id의 주소 변경 | 유효 인증된 타인 주소 변경 후보 | 본인/타인·역할·빈값/거부 무쓰기 실제 체인 검증. RF020 대상 토큰 발급과 독립 명세 |
| RF042 / P0 | `B/user/user/service/DropOutService.java:19~23` 요청 id 조회 후 visible 변경 | 유효 인증된 타인 탈퇴 후보; 인증 없이 공개라고 단정하지 않음 | 자기/타인·관리자/반복·기존 거래 정책 합의 후 체인/DB 검증. RF040 탈퇴 뒤 토큰 효력과 연결하되 소유권 검사와 별개 |
| RF043 / P0 | `B/user/user/controller/JoinController.java:25~27`, `service/JoinService.java:25~44` 요청→중복검사→저장, `controller/JoinAuthController.java:45` 이메일 인증 별도 경로 | 최종 가입에서 이메일/전화 인증 증명 결합 누락 후보; 다른 외부 인증/프로세스 미확인 | 이메일/전화 발송 대역, 미인증/다른 대상/만료/재사용/재전송/정상 가입 검증. 인증 proof 정책 승인. RF032 중복 경쟁과 분리 |
| RF044 / P0 | R8-범위밖 신고 응답 이관. `B/report/report/service/ReportServiceImpl.java:52,72,111,138` reporter 및 관련 User 엔티티를 응답 DTO에 매핑 | 민감 필드 직렬화/열람 범위 후보. 생성 직후 연관은 null일 수 있어 실제 응답 노출을 확정하지 않음 | 관리자/신고자/제3자별 네 응답 mapper·실제 로딩/HTTP 재현 후 최소 응답 정책 합의. RF008은 포인트 네 API만 완료. RF024 신고 생성 입력과 분리 |
| RF045 / P1 | `F/app/my_page/_compoenets/modal/PointModalContent.tsx:17~33,46,51` 버튼 disabled 상태를 두지만 chargingPoint에 진행중 guard가 없고 Enter도 호출 | 충전 진행중 Enter 반복 제출·실패 후 복구 UI 후보, 실제 브라우저 미검증 | 느린 응답/실패/Enter·클릭 조합에서 요청수·상태 복원 검증. RF036 서버 결제 멱등성과 별도이며 UI guard로 서버 중복 안전성 주장 금지 |

백엔드의 `SecurityConfig.anyRequest().permitAll()`만으로 거래/주소/탈퇴를 비인증 공개라고 판정하지 않는다. `TokenFilter.java:68` skip 규칙과 필터 순서를 함께 적용해 유효 인증 이후 객체 인가를 우선 재현한다. 신고 입력 isReport 저장과 별도 Penalty 수행도 구분한다.

RF020 계좌 경로는 유효 UNCONFIRMED_USER 인증·요구 refresh 쿠키·대상 계좌 미등록·미중복 계좌 등 조건을 포함해 검증한다(`ConfirmController.java:24`, `ConfirmService.java:35~65`, `SecurityConfig.java:109`). 대상 토큰의 실제 반환 위치는 controller/response wrapper와 헤더 소비까지 확인하며 무조건적인 계정 탈취로 표현하지 않는다. RF021 수락 거래의 seller는 최신 구현에서 상품 owner 기준이며 요청 sellerId를 그대로 확정 판매자로 쓴다고 주장하지 않는다. RF043에서는 `EmailAuthService.java:45` 인증번호를 응답 DTO로 반환하는 경로도 확인했으나 최종 가입 proof·발송/UI 전체 계약의 실제 검증은 남아 있다.

## 기존 RF001~009: 현재 완료 범위와 후속 소유권

| ID / 현재 판정 | 기능·통합 잔여 / 위험 | 순서·재개 조건 및 중복 제거 |
|---|---|---|
| RF001 합의 선택 검증 완료 | R1-통합/HTTP P1, 최초 raw 공백 P3 | RF016 안전 기반 후 R2 및 공통 HTTP 검증. 당시 자료 확보만 최초 증빙 보완; RF002 재실행은 최초 원본 아님 |
| RF002 전체 미완료 | R2-DB 관리자 Page·role·최대ID/시각 역전·빈이력·동시append·무쓰기/HTTP P1; R2-성능 P3 유예 | 최소 사용자/포인트 fixture 후 기능 검증. RF003 작은 batch 교차결과는 Page 통합 아님. SQL 수·실행계획·지연은 사용자 재개 지시 전 실행 금지 |
| RF003 합의 기능 완료 | 실제HTTP/브라우저·다른 writer/교착/운영 구성 P1; 과거 원시 frontend/assemble 로그 공백 P3 | RF016 후 공통 HTTP/금전 흐름. 멱등성→RF036, 원자성→RF037, 금액 신뢰→RF035. 과거 잔액복구는 필요·정책 확인 전 보류. 공통 role 기능 검증은 이미 완료 |
| RF004 최신 합의 기능 완료 | 실제 Servlet OSIV/HTTP·기존 status1 재사용/nullable 오염 실DB·일반 상품/신규 request writer/재판매·취소 경합 P1; 운영 pool 검증 P1, 성능 P3 | 최신 43 offline/24 MySQL과 독립 리뷰. RF021 인가 및 RF038 정책과 별도. 새 호출자가 outer RR/stale target을 요구하면 경계 재검토. 과거37 중8실패는 이전 설계 이력; 최신 완료 차단으로 재계상 금지 |
| RF005 최소 정책·격리 검증 완료 | 실제 Boot/dispatch·쿠키·프록시 재발급 소비 P1, exp 경계/clock skew·특이 claim 조합 P2 | 공통 HTTP 후 통제 clock 검증. 폐기/role→RF040. skip/permitAll·CSRF/CORS 정책은 R5-범위 잔여, 운영 정책 자료 후 별도 결정 |
| RF006 최소 비교 수정 완료 | 실제 DB commit/rollback·HTTP/OSIV P1; null/null·상품 owner와 거래 seller 주체 차이 P2 | 공통 거래 통합으로 검증, 상태전이→RF038. 정책 변경은 별도 합의. RF031은 별도 chat 코드 |
| RF007 최소 PUT/DELETE 인가 완료 | 실DB/Redis/Boot/브라우저 P1; 삭제 OR 사업조건·관리자 DELETE 응답 미검사 UI P2 | 공통 상품 통합 후 정책/실패 UI 검증. 다른 writer 인가→RF039; 캐시는 RF009/010, 거래 경합은 R4와 연결 |
| RF008 최소 포인트 DTO 완료 | 실DB/HTTP/외부 소비자 user 의존·릴리스 고지 P1; typeDetail 이름 표시 정책 P2 | 안전 통합 및 소비자 목록 확인, 네 응답 계약 검증. 신고 User 직렬화→RF044. 응답 축소 성능 P3 유예 |
| RF009 정상 커밋 후 무효화 완료 | R9-통합 실DB/JPA/Redis key·serializer·TTL·네트워크/Boot advisor P1. R9-일관성 늦은 stale put·장애/프로세스종료/failover 복구 P1 | 1/2 reader stale put은 대역에서 재현된 미해결 경쟁. strong freshness 필요성·계약 판단 후 별도 설계. TTL은 DB변경부터30분 보장 아님. RF010은 다른 writer/DTO 범위. 과거 RF00424건은 RF009 실제 Redis 통합 결과 아님 |

RF004의 이전 REQUIRES_NEW reader는 최신 구현에서 제거됐다. 과거 추가연결/pool starvation 위험을 현재 코드의 동일 결함으로 서술하지 않는다. 운영 pool/OSIV 미검증은 유지한다. RF009는 MySQL opt-in 두 클래스 constructor mock 호환만 수정하고 재실행하지 않았으므로 최신 HEAD 전체 물리 검증을 통과했다고 주장하지 않는다. 전체 backend test/build·frontend production build는 과거 선택 검증과 별개이며 안전 서비스/SSG fixture가 준비된 후 관련 묶음부터 실행한다.

## 실행 순서와 병렬 가능 범위

아래는 승인 요청을 위한 권고 순서다. 높은 위험 조사와 환경 준비를 병렬로 할 수 있지만 실제 실행 승인/정책 확정은 각각 필요하다. 한 작업 완료 후 다음 작업 자동 착수 금지 원칙을 유지한다.

| 순서 | 위험 / 대상(기존 미진행 전부 포함) | 실행 의존성과 완료 근거 |
|---|---|---|
| 1A | P0 RF020 계좌, RF021 거래 요청, RF022 채팅 인증·인가, RF023 상세 개인정보, RF041 주소, RF042 탈퇴 | 독립 경계별 실제 chain/채널+대역 재현·정책 확정. 운영 proxy 자료 확보는 채팅 노출 판단 선행. RF020과 주소/탈퇴를 같은 수정으로 묶지 않음 |
| 1B 병렬 | P1 RF016 최소 격리 기반 / R1-통합 | 사용자/포인트/인증부터, 이어 거래·상품·Redis·채팅. 전용 identity·DDL 금지·발송 차단·소유행/key cleanup. 전 영역 fixture를 먼저 완성할 필요 없음 |
| 2 | P0 RF035 금액신뢰→RF038 상태전이 계약→RF036 멱등성·RF037 원자성 | 금전/상태의 권위 있는 기준 결정 후 동시/부분 실패 재현. RF036/037는 정책 결정 뒤 별도 구현 가능하나 동일 결제 경계 리뷰 공유 |
| 3 | P0 RF024 신고생성, RF039 추가 상품인가, RF040 토큰수명, RF043 가입인증, RF044 신고응답 | 1A 인증/주체와 1B 최소 기반 재사용. 응답·권한·인증 정책 합의, 대역 검증 뒤 필요한 물리 통합 |
| 4 | P1 R1-HTTP/R2-DB/R3-HTTP·브라우저·운영/R4-연동·운영/R5~R8-연동·통합 | 공통 Boot/HTTP·인증/DB·브라우저 fixture로 묶되 각 RF 성공/실패·무쓰기 assertions와 한계 별도 기록. RF002 기능 잔여를 performance 유예와 혼동해 방치하지 않음 |
| 5A | P1 R9-통합·일관성, RF010 DTO writer 소유범위, RF017 검색 동기화 | Redis와 ES 보장은 별개. RF010/RF017 writer 인벤토리는 병렬 조사 가능. 강한 최신성 설계·인덱스 필터 정책은 별도 합의, 이벤트 인프라 자동 도입 금지 |
| 5B 병렬 | P1 RF015 S3/DB 실패, RF018 watermark 패키징, RF034 업로드형식/자원, RF033 로그 개인정보, RF032 중복가입 | S3 대역/이미지 fixture·배포 mount 자료·로그 정책·실DB 제약 확인. RF018 `CompressService.java:37,57` 상대 이미지 경로와 Docker 최종 stage JAR만 COPY는 후보 근거이며 운영 mount 부재는 미확인 |
| 6 | P1 RF025 방/메시지 원자성, RF026 읽음, RF027 이미지 계약, RF029 SWR, RF030 프런트로그아웃, RF045 충전 UI 중복제출 | RF022 참여 정책과 RF040/로그아웃 계약 연결. RF025→026 초기행 의존, RF027·029·030·045는 독립 frontend/계약 검증 병렬 가능 |
| 7 | P2 RF011 빈목록 계약→RF012 목록매핑 중복, RF013 로그 공통화, RF019 Exception 상속, RF028 STOMP수명, RF031 Long 비교 | RF011 `ProductService.java:59,98` null 반환은 재현/204·200 정책 결정 후. RF019 `TransactionDetailsService.java:30` extends Exception은 거래 정책 변경 안정 뒤. RF013 임시 파일 경계 테스트는 다른 영역과 병렬 가능 |
| 8 | P2 R5 특이시각/claim·skip/쿠키 정책 잔여, R6 null/주체, R7 삭제OR/관리자UI, R8 이름표시 | 위 표의 통합과 구분한 정책/추가 경계 검증. 영향 재현 시 위험도 상향 가능, blanket 보안 정책 변경 금지 |
| 유예 | P3 RF014 로그 비용측정, RF002 성능, R3~R9 성능, 전체 성능 후보 | 사용자 별도 재개 지시와 대표 전후 기준선 후. RF014는 RF013 뒤. 기능 테스트 경과시간/호출수 감소를 성능 향상으로 보고하지 않음 |
| 자료 확보 시 | P3 RF001 최초 raw·RF003 frontend/정적검사/assemble 원시로그 공백 | 당시 자료와 출처만 소급 보완. 새 실행은 별도 날짜·환경 결과, 과거 성공의 원시증거로 대체 금지 |

RF011/012/013/019는 보안·정합성 위험보다 우선 착수할 근거가 없다. RF017 visible/forSale 검색 정책은 기능 조사라서 성능 유예에 묶지 않는다. R9 강한 최신성의 실제 영향이 판매/숨김 보안 판단에 직결된다고 확인되면 P0로 상향하되 현재 운영 영향을 확정하지 않는다.

## 문서 검증 및 남은 한계

이번 변경은 BACKLOG와 본 분석문서만 대상으로 한다. 기존 RF004 실패/보류/재설계 및 RF003 role 유예 이력은 삭제하지 않는다. 신규 번호·기존 Rn 소유권·전 항목 순서 포함 여부, 상대 링크와 diff를 정적으로 확인한다. 별도 독립 문서 리뷰는 통합 담당 단계에서 수행하며 이 문서 작성 자체를 독립 리뷰 완료로 기록하지 않는다. 실제 테스트/보안 악용/DB·Redis·S3·SMTP/SMS·운영 프록시/브라우저·성능은 이번 미실행이며, 재개 시 해당 작업 명세와 증빙 README·BACKLOG에 범위·환경·결과·한계·미실행 사유·정리 결과를 함께 남긴다.

최종 독립 문서 리뷰 완료: 별도 담당자가 읽기 전용으로 신규·기존 전체 번호, 우선순위, 근거, 링크와 범위를 확인했다. 지적된 RF023 DTO 근거 행과 RF028 알림 provider 수명·Notification 환경 누락 2건을 정정했고, 정정 확인 후 문서 범위의 차단사항 없음으로 판정했다. 이 리뷰는 테스트·DB 접속·운영 검증을 실행하지 않았으며 후보의 기능 검증 또는 구현 승인을 뜻하지 않는다.
