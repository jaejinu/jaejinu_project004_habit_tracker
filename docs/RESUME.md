# 다음에 이어서 진행할 항목

세션을 중단한 시점(2026-04-13) 기준 정리입니다. 다시 시작할 때 이 파일 먼저 읽으세요.

## 현재 상태 (로컬 빌드 OK)

- `./gradlew test` → **9 tests passing** (2 BadgeControllerDocsTest + 7 CheckInRepositoryIntegrationTest)
- `./gradlew bootJar` → **성공** (REST Docs asciidoctor 결과가 jar의 `static/docs/index.html`에 임베드됨)
- `./gradlew asciidoctor` → `build/docs/asciidoc/index.html` 생성

## 원격 상태 확인이 필요함

- 로컬 커밋은 모두 푸시됨 (`247db65` 기준 + 이 세션에서 추가 커밋 예정).
- 바로 직전 푸시된 CI 실행 결과: https://github.com/jaejinu/jaejinu_project004_habit_tracker/actions
  - `feat: Gradle wrapper, Testcontainers integration tests, GitHub Actions CI` (`4d6f39c`) — 실패 (gradlew exec bit 누락 → 수정 완료, 재푸시됨)
  - `fix: CI gradlew exec bit, @ConfigurationPropertiesScan, add dependabot` (`247db65`) — 다시 시작할 때 Actions 탭에서 녹색 확인 필요
- Dependabot PR 5개가 자동 생성되어 열려 있음 (spring / jjwt / testing / gradle-wrapper). 각 PR의 CI가 녹색이면 머지해도 안전.

## 진행 중이었던 작업: Spring REST Docs 파이프라인

### 이번 세션에서 완료한 부분 (로컬 커밋됨, 아직 push 전)

- `build.gradle`:
  - `org.asciidoctor.jvm.convert` 4.0.3 플러그인
  - `snippetsDir = build/generated-snippets`, `asciidoctor` 태스크 구성
  - `bootJar` 가 `asciidoctor` 에 의존하고 `build/docs/asciidoc` 을 `BOOT-INF/classes/static/docs` 로 임베드
  - `duplicatesStrategy = DuplicatesStrategy.EXCLUDE` 로 중복 엔트리 이슈 회피
- `src/docs/asciidoc/index.adoc`: Badge API 스니펫 레퍼런스 (calendar, calendar-304 두 케이스)
- `src/test/java/com/habit/api/BadgeControllerDocsTest.java`:
  - `RestDocumentationExtension` + `documentationConfiguration` 으로 MockMvc 설정
  - `.andDo(document("badge-calendar", responseHeaders(...)))` 로 스니펫 생성
- `.github/workflows/ci.yml`: `bootJar` 에서 `-x test` 제거, 생성된 HTML 을 `api-reference-html` 아티팩트로 업로드
- `README.md`: `/docs/index.html` 경로 안내
- `.gitignore`: `build/generated-snippets/` 제외

### 다음에 해야 할 작업 (push 포함)

1. **커밋 + 푸시** — 변경 7파일. 권장 메시지:
   ```
   docs: Spring REST Docs asciidoctor pipeline

   - asciidoctor plugin generates build/docs/asciidoc/index.html from
     snapshot tests on every build
   - bootJar embeds the HTML at /docs/index.html so the live API is
     self-documenting
   - BadgeControllerDocsTest now emits named snippets via .andDo(document(...))
   - CI uploads the rendered HTML as the api-reference-html artifact
   ```

2. **CI 결과 확인** — `gh run list --workflow=ci.yml --branch=main --limit 3`. 녹색이면 메모리 업데이트 + CLAUDE.md 에 REST Docs 섹션 추가.

3. **REST Docs 스냅샷 확장** — 현재는 BadgeController 만 커버. 추가 대상:
   - `AuthController` (signup/login — JSON request/response 스니펫)
   - `HabitController` (CRUD 전체)
   - `CheckInController` (POST/GET/PATCH)
   - 이들은 `@WebMvcTest` 기반으로 `MockBean` 으로 서비스 주입하면 쉽게 추가 가능.

4. **asciidoctor 에 `include::` 누락 케이스 많음** — `index.adoc` 이 현재는 Badge 만 커버. 위 테스트 추가 후 adoc 섹션도 추가해야 `/docs/index.html` 이 전체 API 를 보여줌.

## 남아 있는 장기 TODO

- **스크린샷** — `docs/badge-guide.md` 의 `<!-- TODO: insert screenshots -->` 플레이스홀더. 실제 앱을 띄워서 light/dark 테마 뱃지를 캡처해야 함.
- **Dependabot PR 정리** — 5개 자동 PR. 각 CI 녹색 확인 후 머지.
- **app.yml 프로덕션 경고** — `application-prod.yml` 은 `JWT_SECRET` 환경변수에 기본값 없음. 실제 배포 전 시크릿 주입 방식(AWS SecretsManager 등) 결정 필요.
- **Gradle wrapper 권한 잠금** — `gradlew` 의 mode 는 `git update-index --chmod=+x` 로 설정됨. Windows 재클론 시 손실 가능 → `.gitattributes` 로 고정 권장:
  ```
  gradlew text eol=lf
  ```

## 알려진 주의사항 (세션 간 유지)

- `RateLimitFilter` 의 `SecurityContextHolder` principal 가정은 `Long userId`. 변경 시 전 사이트 일괄 수정 (`auth_and_docs.md` 메모리 참조).
- 네이티브 쿼리에서 `LocalDate` 파라미터는 항상 `CAST(:param AS DATE)` 로 감싸야 Postgres JDBC 타입 추론 이슈 없음 (`test_and_ci.md` 메모리 참조).
- `@EnableJpaAuditing` 은 `infra/persistence/JpaConfig` 에만 존재. `@SpringBootApplication` 에 다시 붙이면 `@WebMvcTest` 가 깨짐.

## 빠른 재개 명령

```bash
cd /c/Users/User/Documents/jaejinu_project/jaejinu_project004_habit_tracker
export JAVA_HOME="/c/Users/User/.jdks/temurin-21.0.6"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew test bootJar        # 전체 빌드 + 테스트
./gradlew asciidoctor         # REST Docs HTML 만

git status
gh run list --workflow=ci.yml --branch=main --limit 3
```
