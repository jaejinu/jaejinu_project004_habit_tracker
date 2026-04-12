# Step 5 — Documentation (Ultra-Plan)

Two complementary documentation surfaces:

1. **OpenAPI + Swagger UI** (live, interactive, generated from annotations).
2. **Markdown guides** under `docs/` (narrative, copy-pasteable examples for README embedding).

## Surface 1 — OpenAPI / Swagger UI

- springdoc-openapi 2.6.0 is already on the classpath.
- A new `com.habit.infra.docs.OpenApiConfig` bean defines:
  - `Info` (title "Habit Tracker API", version "v1", description pointing to `docs/api-reference.md`, contact, license MIT).
  - `Server` list (local `http://localhost:8080`, prod placeholder `https://api.habittracker.io`).
  - `SecurityScheme` "bearerAuth" (type HTTP, scheme "bearer", bearerFormat "JWT").
  - Applies `bearerAuth` globally so endpoints that are actually public (`/api/v1/auth/**`, `/api/v1/public/**`, `/api/v1/badges/**`) are individually marked `@SecurityRequirements({})` to opt out.

- Controllers get these annotations:
  - `@Tag(name = "...")` on the class.
  - `@Operation(summary = "...", description = "...")` on each handler method.
  - `@ApiResponse(responseCode = "...", description = "...", content = ...)` for non-2xx cases that matter.
  - `@Parameter(description = "...", example = "...")` on path/query params that aren't self-evident.
  - `@SecurityRequirements({})` on truly public handlers to opt out of the global `bearerAuth`.

## Surface 2 — Markdown Docs

Create these files (human-written, not generated):

1. `docs/api-reference.md` — concise endpoint reference organized by resource (auth, habits, check-ins, badges, public). For each endpoint: method+path, auth, request body example, response example, error codes.

2. `docs/badge-guide.md` — user-facing "how to embed your streak in a README" guide. Includes:
   - 5-step quick start (signup → login → create habit → make it public → POST badge → copy URL).
   - Copy-paste markdown snippets.
   - Query-parameter cheatsheet (`color`, `theme`, `lang`, `width`).
   - Screenshots placeholder block (leave `![placeholder](...)` lines with TODO comments; screenshots are a later follow-up).

3. `examples/readme-example.md` — a ready-to-fork example README showing badges in action (dark theme + light theme, ko + en).

## Surface 3 — Spring REST Docs (Deferred)

Full Spring REST Docs asciidoctor generation is intentionally deferred. For this step we add:

- One **sample integration test** `BadgeControllerDocsTest` under `src/test/java/com/habit/api/` that uses MockMvc to exercise `/api/v1/badges/{token}/calendar.svg`, asserts the Content-Type / Cache-Control / ETag headers, and includes a comment-stub showing where `document(...)` snippets would be emitted if REST Docs asciidoctor plugin is wired later. The test compiles and runs against Testcontainers Postgres (or profile-based H2 workaround).
- README note that full `docs/api/index.adoc` asciidoc generation is a follow-up once test coverage grows.

Rationale: Spring REST Docs shines when there's >20 endpoints with deep test coverage. We currently have ~10 endpoints and no tests yet. Investing in asciidoctor ceremony before tests exist would be build-config theatre. Swagger UI + Markdown gives us 80% of the readability today; RestDocs is a deliberate "add when tests catch up" follow-up.

## Parallel Plan

- **Agent A**: Add OpenAPI annotations (`@Tag`, `@Operation`, `@ApiResponse`, `@Parameter`, `@SecurityRequirements`) across all controllers: `AuthController`, `BadgeController`, `PublicController`, `SharedBadgeController`.
- **Agent B**: Write the three markdown files: `docs/api-reference.md`, `docs/badge-guide.md`, `examples/readme-example.md`.
- **Main thread**: `OpenApiConfig` bean, `BadgeControllerDocsTest` sample test skeleton, README/CLAUDE/memory updates.

## File Inventory

Create:
- `src/main/java/com/habit/infra/docs/OpenApiConfig.java`
- `docs/api-reference.md`
- `docs/badge-guide.md`
- `examples/readme-example.md`
- `src/test/java/com/habit/api/BadgeControllerDocsTest.java`

Modify:
- `AuthController.java`, `BadgeController.java`, `PublicController.java`, `SharedBadgeController.java` — add springdoc annotations.
- `README.md` — mark step 5 done, link to the new docs.
- `CLAUDE.md` — add "Documentation" section.
