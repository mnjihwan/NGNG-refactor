# Windows 로컬 개발 환경

이 구성은 통합 저장소의 세 프로젝트를 운영 자격 증명 없이 실행하기 위한 별도 개발 환경이다. 기준 도구는 Windows, Java 17, Node.js 20, Gradle Wrapper 8.7, Docker Desktop의 Linux 컨테이너 엔진이다. 기존 `package-lock.json`의 패키지는 `NGNG-frontend`에서 `npm ci`로 설치한다.

## 설정 준비

저장소 루트에서 필요한 파일만 처음 한 번 복사한다. 기존 파일은 보존한다.

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
if (!(Test-Path local/backend.yml)) { Copy-Item local/backend.example.yml local/backend.yml }
if (!(Test-Path local/chat.yml)) { Copy-Item local/chat.example.yml local/chat.yml }
if (!(Test-Path NGNG-frontend/.env.local)) { Copy-Item NGNG-frontend/.env.local.example NGNG-frontend/.env.local }
```

`.env`의 빈 값을 새 로컬 전용 값으로 채운다. MySQL 비밀번호, MySQL root 비밀번호, MinIO 사용자·비밀번호, JWT 키를 각각 설정한다. MinIO 사용자 이름은 3자 이상, 비밀번호는 8자 이상이어야 한다. 예제의 MySQL 사용자 이름과 데이터베이스는 `ngng`와 `ngng_local`이다. 운영 자격 증명을 사용하지 않는다. 아래 명령은 무작위 64바이트를 Base64URL 문자열로 만들어 준다. 각 비밀번호와 JWT 키에 서로 다른 값을 생성하여 입력할 수 있다.

```powershell
$ngngRandomBytes = New-Object byte[] 64
$ngngRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $ngngRng.GetBytes($ngngRandomBytes) } finally { $ngngRng.Dispose() }
[Convert]::ToBase64String($ngngRandomBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
```

아래 환경 로딩 명령과 Compose가 같은 값을 읽도록 `.env`는 `NGNG_이름=값` 형식으로 작성하며, 값에는 영문·숫자·`_`·`-`만 사용하고 따옴표를 넣지 않는다. JWT 키는 최소 32바이트의 무작위 값을 Base64URL로 인코딩해야 한다. 위 명령은 이 조건을 충족한다. `.env`, 실제 `local/backend.yml`·`local/chat.yml`, 프런트 `.env.local`은 Git에서 제외된다. 예제에는 자격값을 저장하지 않는다.

이미 실행 중인 로컬 환경은 **기존 자격값과 설정을 유지**한다. 기존 YAML의 값을 예제로 덮어쓰지 않으며 `.env`에는 해당 로컬 MySQL/MinIO와 일치하는 값을 입력한다. `.env`의 값을 바꿔도 기존 MySQL 볼륨의 사용자·비밀번호가 갱신되지는 않는다. 기존 YAML을 환경변수 방식으로 바꾸려면 보존한 설정과 비교해 별도로 수정한다. 기존 데이터 경로와 볼륨을 삭제하지 않는다.

## 선행 조건과 검증 상태

- Docker Desktop의 Linux 컨테이너 엔진과 WSL 2 가상화가 동작해야 한다. 최초 준비 PC에서는 가상화 설정 문제로 Docker 엔진을 기동하지 못했으므로 Compose 서비스 간 연동은 미검증이다. 필요한 Windows 기능·BIOS/UEFI 설정과 재부팅은 각 PC에서 확인한다.
- 사용자 승인에 따라 격리 로컬 MySQL에 스키마를 생성하고, 데이터 없이 `local/schema.sql`로 내보냈다. 두 서버의 로컬 설정은 `ddl-auto: validate`이며 애플리케이션 실행 시 스키마를 변경하지 않는다. 이 SQL은 현재 엔티티에서 생성한 개발용 기준이며 운영 스키마의 복제본이나 마이그레이션 이력이 아니다.
- 최초 준비 시 별도 MySQL 8.0.35 인스턴스의 21개 테이블에 대해 두 서버의 Hibernate validate가 통과했다. fixture와 서버 전체 기능은 검증하지 않았다. PC별 데이터 경로·실행 명령은 공유 문서에 포함하지 않으며 기존 준비 PC의 기록은 Git에서 제외되는 `local/LOCAL_DEVELOPMENT.private.md`에 보존한다.
- 별도 MySQL 인스턴스와 Docker MySQL은 포트 13306을 공유하므로 동시에 실행할 수 없다. Docker는 별도 데이터 볼륨을 사용하며 기존 독립 인스턴스 데이터를 자동 이전하지 않는다. 기존 데이터의 보존 여부를 확인한 뒤 전환한다.
- [RF-001](refactoring/tasks/RF-001.md)의 선택 테스트와 [RF-002](refactoring/tasks/RF-002.md)의 오프라인 테스트 결과는 해당 기록을 참고한다. 기존 통합 테스트는 외부 자원과 `ddl-auto: update`를 포함하므로 격리 및 검토 전 전체 테스트를 실행하지 않는다.
- SMS는 로컬 설정에서 실패 응답으로 차단되고 이메일은 Mailpit으로 전달된다. 실제 문자 인증이나 운영 연동을 검증한 구성은 아니다.

## 인프라 시작

Docker 엔진을 사용할 수 있을 때 저장소 루트에서 실행한다. Compose는 루트 `.env`를 읽고 필수 자격값이 없으면 실패한다. 최초 실행은 이미지 다운로드가 필요하다. MySQL 데이터 볼륨이 새로 생성될 때 `local/schema.sql`을 한 번 적용한다. 기존 볼륨에는 자동 적용되지 않는다.

```powershell
docker compose -f compose.local.yml config --quiet
docker compose -f compose.local.yml up -d
docker compose -f compose.local.yml ps
```

MySQL은 `127.0.0.1:13306`의 `ngng_local`, Redis는 `16379`, Elasticsearch는 `19200`, MinIO API는 `9000`, MinIO 콘솔은 `9001`, Mailpit SMTP는 `1025`, 웹 화면은 `http://localhost:8025`다. 서비스 포트는 loopback에만 노출된다. 데이터는 Docker named volume에 보존된다. MinIO 초기화 컨테이너는 로컬 버킷을 생성하고 읽기를 허용한다. `docker compose -f compose.local.yml down`은 컨테이너를 중지하고 데이터 볼륨을 보존한다.

Elasticsearch는 별도 인덱싱 경로가 검증되지 않아 빈 인덱스의 검색 결과가 DB와 같다고 가정하면 안 된다. 기존 데이터와 이미지도 자동 복사하지 않는다. MinIO는 로컬 이미지 업로드용이며 초기화 완료를 확인해야 한다.

## 애플리케이션 실행

Spring Boot는 Compose의 `.env`를 자동으로 읽지 않는다. 환경변수를 참조하는 예제로 설정했다면 **각 서버를 실행할 PowerShell 창마다 저장소 루트에서** 아래 명령으로 `.env`를 현재 프로세스에 로딩한다. 값은 화면에 출력하지 않는다.

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*(#|$)') { return }
    if ($_ -notmatch '^NGNG_[A-Z_]+=[A-Za-z0-9_-]+$') {
        throw '.env의 형식 또는 빈 값을 확인하세요.'
    }
    $ngngPair = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($ngngPair[0], $ngngPair[1], 'Process')
}
```

반드시 아래처럼 `spring.config.location`을 지정해 각 프로젝트의 기존 로컬 또는 배포 설정 대신 준비한 설정만 사용한다. 백엔드에는 MySQL 외에 Redis·Elasticsearch·MinIO·Mailpit 준비가 필요하다. 두 서버는 별도 창에서 실행한다.

```powershell
cd NGNG-backend
.\gradlew.bat bootRun --args="--spring.config.location=file:../local/backend.yml"
```

```powershell
cd NGNG-chat-server
.\gradlew.bat bootRun --args="--spring.config.location=file:../local/chat.yml"
```

프런트엔드는 별도 창에서 실행한다.

```powershell
cd NGNG-frontend
npm run dev
```

프런트엔드는 `http://localhost:3000`, 백엔드는 `http://localhost:8080`, 채팅은 `ws://localhost:8081/chat-server`다. 서버 측 상품 메타데이터 생성은 실행 중인 백엔드와 상품 데이터에 의존하므로 백엔드가 없으면 프런트 프로덕션 빌드가 실패할 수 있다. Kakao 주소 서비스 및 기존 외부 이미지 등 일부 화면 기능은 인터넷에 의존할 수 있다.
