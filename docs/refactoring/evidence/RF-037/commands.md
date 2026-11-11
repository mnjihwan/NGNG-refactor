# RF037 2026-11-11 실행 명령

backend 명령은 NGNG-backend에서 Windows PowerShell·JAVA_HOME=C:\Program Files\Java\jdk-17로 실행했다. 다른 Gradle 실행과 직렬화했다. 각 phase의 stdout/stderr·LASTEXITCODE 및 XML은 .local-runtime/rf037-results 아래 보존한다. 최초 wrapper/compile 실패 뒤 남은 예전 XML은 preexisting-stale-xml-not-this-run으로 분리했고 공유/새 실행 집계에서 제외했다.

## initial / initial-compile / after-initial

동일 명령이다. initial은 sandbox wrapper cache lock 거부로0건, initial-compile은 helper 인자누락 compile 오류로0건, after-initial은388건/4실패다. 후속 phase가 앞 결과를 덮어쓰지 않는다.

~~~powershell
.\gradlew.bat test --offline --no-daemon --rerun-tasks --tests 'com.ngng.api.user.point.payment.*' --tests 'com.ngng.api.user.point.service.*' --tests 'com.ngng.api.user.point.controller.*' --tests 'com.ngng.api.user.point.repository.PointHistoryRepositoryBatchTest'
~~~

## backend final

~~~powershell
.\gradlew.bat test --offline --no-daemon --rerun-tasks --tests 'com.ngng.api.user.point.payment.*' --tests 'com.ngng.api.user.point.service.*' --tests 'com.ngng.api.user.point.controller.*' --tests 'com.ngng.api.user.point.repository.PointHistoryRepositoryBatchTest' --tests 'com.ngng.api.transaction.status.*' --tests 'com.ngng.api.transaction.service.TransactionAcceptancePolicyTest' --tests 'com.ngng.api.transaction.service.TransactionCreationContractTest' --tests 'com.ngng.api.transaction.authorization.TransactionRequestAuthorizationServiceTest' --tests 'com.ngng.api.transaction.authorization.TransactionRequestAuthorizationChainTest' --tests 'com.ngng.api.transaction.read.TransactionReadOwnershipTest' --tests 'com.ngng.api.transaction.read.TransactionReadSecurityChainTest' assemble
~~~

942/942·errors/failures/skips0. compileJava/compileTestJava·bootJar/jar·assemble 성공이며 물리 DB/전체 test/build가 아니다.

## frontend

NGNG-frontend에서 다음 명령을 실행했다. 초기22/1실패→after22통과, final25통과·final-reviewed26통과·review-final37통과는 같은 사례의 반복/보강으로 합산하지 않는다. 마지막 세 phase에서 tsc/lint도 실행했다. 최종 review-final37·tsc/lint exit0이 현재 source 기준이다.

~~~powershell
node --test tests/rf035-payment-trust.test.cjs
.\node_modules\.bin\tsc.cmd --noEmit --incremental false
npm run lint
~~~

## 증빙 정제/검토

루트에서 실행했다. 테스트를 재실행하지 않는다. 최종 source snapshot이 이미 있으면 동일 hash가 아닐 때 실패하며 덮어쓰지 않는다. raw console/XML·source를 복사하고 개인경로/hostname/합성 JWT literal을 정제해 source/raw/shared hash 및 XML 총계를 verification.json에 기록한다.

~~~powershell
node docs/refactoring/evidence/RF-037/prepare-evidence.cjs
git diff --check
~~~

최종 snapshot은 선택30개 source이며 전체 작업트리 snapshot이나 실패 시점 source 전부가 아니다. git diff --check는 공백 오류0·기존 LF→CRLF 경고만 관찰했다. 후일 재실행은 이 원시 결과의 소급 대체가 아니다.


## 후속 공동 체인6건

~~~powershell
.\gradlew.bat test --offline --no-daemon --rerun-tasks --tests 'com.ngng.api.user.point.payment.PaymentAndStatusChainTest'
~~~

joint-chain 최초는 helper status(long)와 static status() 이름 충돌 compile7오류·테스트0이다. 남아 있던 기존942 XML은 stale로 분리했다. helper 이름 putStatus로 수정한 joint-chain-final은6/6·오류/실패/skip0 및 compileJava/compileTestJava 성공이다. 기존942/assemble 실행을 대체하지 않으며 겹치지 않는 새6건의 직렬 요청 순서 검증이다. 추가 source는 joint-chain-source, 기존30개는 final-source로 보존·hash를 구분한다.
