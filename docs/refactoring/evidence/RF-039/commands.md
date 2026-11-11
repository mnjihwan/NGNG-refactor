# RF039 실행 명령·원시 자료 범위

2026-11-11 Windows PowerShell/Java17/Gradle8.7 기존 cache 사용. wrapper lock 권한은 escalation으로 허용된 뒤 `--offline --no-daemon`으로 의존성 네트워크 접근 없이 실행했다. 아래 test context는 명시 bean만 등록하고 실제 DB/S3/Redis를 만들지 않는다.

## before

NGNG-backend에서:

```powershell
.\gradlew.bat --offline --no-daemon -I ../docs/refactoring/evidence/RF-039/selected-tests.init.gradle test --tests com.ngng.api.product.writer.ProductAdditionalWriterSecurityChainTest --rerun-tasks
```

당시 init은 `sourceSets.test.java.setIncludes(['com/ngng/api/product/writer/**'])`였고 신규 chain1개만 있었다. 테스트 소스 및 XML29건은 before에 보존했다. 이후 init에 authorization/cache include를 추가했지만 최종 정상 sourceSet 실행에는 `-I`를 사용하지 않는다. before29건을 정상 전체 테스트 소스 컴파일로 표현하지 않는다.

## after 선택 및 정상 sourceSet compile

```powershell
.\gradlew.bat --offline --no-daemon test --tests com.ngng.api.product.writer.ProductAdditionalWriterSecurityChainTest --tests com.ngng.api.product.writer.ProductAdditionalWriterServiceTest --tests com.ngng.api.product.authorization.ProductAuthorizationServiceTest --tests com.ngng.api.product.authorization.ProductAuthorizationSecurityChainTest --tests com.ngng.api.product.authorization.ProductAuthorizationCacheTest --tests com.ngng.api.product.cache.ProductCacheInvalidationTest --tests com.ngng.api.product.cache.ProductCacheInvalidatorTest --tests com.ngng.api.product.cache.ProductCacheConcurrencyBoundaryTest --rerun-tasks
.\gradlew.bat --offline --no-daemon assemble
```

이전 첫 after는 `-I`를 사용했으나 RF043 초안 compileJava 실패로 테스트0이었다. 이후 정상 sourceSet compileTestJava 및 위8개 선택 suite를 수행했다. 전체 test 실행과 구분한다. console은 `2>&1 | Tee-Object -FilePath ...; exit $LASTEXITCODE`로 보존했으며 실패/덮어쓴 중간 성공 console의 보존 한계는 README에 명시했다. 최종 suite 직전 중간250 성공 console을 별도 복사하려던 명령은 cwd 상대경로 오류로 실패했고 최종 실행이 그 파일을 덮어썼다. 중간 XML250건은 이미 별도 복사해 보존했다.

## frontend

NGNG-frontend에서:

```powershell
node --test tests/rf039-product-writers.test.cjs tests/rf007-product-authorization.test.cjs
npm run lint
npx tsc --noEmit --incremental false
```

최초에는 RF03935개만 실행해 portal harness2실패를 보존했다. 수정 후49개를 실행했다. tsc 실패 수정 후 성공시 출력이 없으므로 `*> frontend-tsc.txt`로 truncate하고 실제 exitCode를 append했다. lint 경고는 기존 img3건이다. production build와 실제 브라우저는 미실행이다.

## 최종 실행 구분 보완

병렬 RF044 및 RF043 신규 test 초안 compile 실패 뒤 `-I selected-tests.init.gradle`을 붙인 보강291건을 먼저 통과했고 `after/isolated-final`에 보존했다. 이후 담당자 편집 동결 후 위 init 없는 명령으로 정상 sourceSet compileJava·compileTestJava + 같은291건을 다시 통과했다. 최종 after XML/console은 init 없는 실행이다. 정상 sourceSet 전체 테스트 소스 컴파일을 전체 suite 실행으로 해석하지 않는다. 최종 assemble은 compileJava/processResources UP-TO-DATE, 나머지 패키징 task 실행으로 exit0이다.

관련 diff는 원래 CRLF를 보존한 파일이 있어 다음 명령으로 개행과 일반 공백을 구분했다.

```powershell
git -c core.whitespace=cr-at-eol diff --check -- NGNG-backend/src/main/java/com/ngng/api/product NGNG-backend/src/test/java/com/ngng/api/product NGNG-frontend/src/app/sell NGNG-frontend/src/app/admin/products NGNG-frontend/src/app/my_page
```

## 독립 리뷰 후 공유 증빙 비식별화

테스트를 재실행하지 않고 ignored `.local-runtime/rf039-evidence-20261111-raw/`에 기존 증빙85개와 수정 전 task byte를 먼저 보존했다. 그 뒤 공유 실행 산출물40개에서 home 경로18곳(native/escaped/slash/URI)·XML hostname33곳만 치환하고 문서/manifest의 부정확한 판정을 정정했다. paired hash와 XML 결과·timestamp 불변 검증은 redaction-manifest/xml-redaction-verification을 참조한다. 기존 원본 stdout/XML이라는 표현은 이 시점 이후 ignored raw에 적용되고, 공유 디렉터리 파일은 비식별화 파생본이다.
