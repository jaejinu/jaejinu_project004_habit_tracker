# 뱃지 가이드 — GitHub README에 내 습관을 자랑하기

> Habit Tracker 뱃지를 내 저장소 README에 붙이는 방법.
> 개발자가 아니어도 5분이면 됩니다.

---

## 무엇인가요?

Habit Tracker는 **SVG 뱃지**를 발급해주는 공개 API입니다.
습관을 하나 만들고 공개(public)로 전환하면, 전용 토큰 URL이 생기고
그 URL은 GitHub/블로그 어디든 이미지처럼 붙여넣을 수 있습니다.

- 잔디 캘린더 — 최근 365일 체크인 현황
- 연속 달성(streak) — 며칠째 이어오는 중인지
- 총 달성(total) — 지금까지 누적 체크인 횟수

모두 그냥 이미지 한 줄. JavaScript도, iframe도 필요 없습니다.

---

## 빠른 시작 (5분)

> 아래 예시의 호스트는 개발 기본값 `http://localhost:8080` 입니다.
> 운영 환경을 사용 중이라면 도메인만 바꿔주세요.

### 1) 회원가입

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jaejinu@example.com",
    "nickname": "jaejinu",
    "password": "pa$$w0rd123"
  }'
```

응답:

```json
{
  "userId": 1,
  "publicId": "4b9e3c0f-5e7a-4e0f-b3b7-0a8e6c5b9d22",
  "email": "jaejinu@example.com",
  "nickname": "jaejinu"
}
```

`publicId`는 공개 프로필 URL에 쓰이는 UUID 입니다. 잘 저장해두세요.

### 2) 로그인 후 JWT 받기

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jaejinu@example.com",
    "password": "pa$$w0rd123"
  }'
```

응답:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

`accessToken` 값을 복사해 두세요. 1시간 유효합니다.

```bash
export TOKEN="eyJhbGciOiJIUzI1NiJ9..."
```

### 3) 습관 만들기 + 공개로 전환

> **⚠️ 솔직한 안내**: 현재 `/api/v1/habits` CRUD 컨트롤러는 아직
> 만들어지지 않았습니다. `HabitService`는 구현되어 있으므로 REST
> 엔드포인트는 곧 붙을 예정입니다. 지금은 다음 둘 중 하나를
> 선택하세요.

- 개발 편의상 시드 데이터를 넣거나
- DB에 직접 접속해 습관을 만들고 `is_public = true`로 업데이트합니다.

```sql
-- 예시: 방금 만든 습관을 공개로 전환
UPDATE habit SET is_public = true WHERE id = 10 AND user_id = 1;
```

(REST 컨트롤러가 추가된 후에는 `PATCH /api/v1/habits/{id}`로 토글할
수 있게 됩니다. 계획된 스펙은 [`api-reference.md`](./api-reference.md)의
"Habits" 절 참고.)

### 4) 뱃지 토큰 발급

습관 ID가 예를 들어 `10`이라면:

```bash
curl -X POST http://localhost:8080/api/v1/habits/10/badges \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"CALENDAR"}'
```

응답:

```json
{
  "token": "aB3kL9mN2pQ5rS7tU0vW-xYzA1bC2dE3fG4hI5jK6l",
  "url": "/api/v1/badges/aB3kL9mN2pQ5rS7tU0vW-xYzA1bC2dE3fG4hI5jK6l/calendar.svg"
}
```

`type`을 바꿔서 3종류 모두 발급할 수 있습니다.

| type | 나오는 뱃지 | 엔드포인트 |
|---|---|---|
| `CALENDAR` | 잔디 달력 | `/.../calendar.svg` |
| `STREAK` | 연속 달성 pill | `/.../streak.svg` |
| `TOTAL` | 총 달성 pill | `/.../total.svg` |

### 5) README에 붙여넣기

`url`에 호스트만 앞에 붙여서 Markdown 이미지 문법으로 씁니다.

```markdown
![habit calendar](http://localhost:8080/api/v1/badges/aB3kL9mN2pQ5rS7tU0vW-xYzA1bC2dE3fG4hI5jK6l/calendar.svg)
```

끝. GitHub가 알아서 이미지를 렌더해줍니다.

---

## 뱃지 URL 커스터마이징

모든 뱃지 URL은 쿼리 파라미터로 모양을 바꿀 수 있습니다.

| 파라미터 | 값 | 적용 뱃지 | 비고 |
|---|---|---|---|
| `color` | 6자리 hex (예: `2da44e`), `#` 없이 | `calendar.svg` | GitHub 잔디색 기본 |
| `theme` | `light` \| `dark` | 전체 | 기본 `light` |
| `lang` | `ko` \| `en` | `streak.svg`, `total.svg` | 기본 `en` |
| `width` | 정수 ≥ 100 | `streak.svg`, `total.svg` | 텍스트가 길면 자동 확장 |

### 조합 예시

**1. 다크 테마 + 한국어 streak**

```markdown
![streak](http://localhost:8080/api/v1/badges/TOKEN/streak.svg?theme=dark&lang=ko)
```

**2. 주황색 잔디 + 다크 배경**

```markdown
![calendar](http://localhost:8080/api/v1/badges/TOKEN/calendar.svg?color=f97316&theme=dark)
```

**3. 최소 너비 200px로 영문 total**

```markdown
![total](http://localhost:8080/api/v1/badges/TOKEN/total.svg?lang=en&width=200)
```

**4. 라이트 테마 + 기본값 (가장 단순)**

```markdown
![calendar](http://localhost:8080/api/v1/badges/TOKEN/calendar.svg)
```

알 수 없는 파라미터는 조용히 무시됩니다. `color`에 잘못된 값을 주면
원래 습관 색으로 대체합니다.

---

## 캐싱

뱃지는 **1시간 동안 서버 측에 캐시**됩니다.

- Redis 캐시: `max-age=3600` (1시간)
- HTTP 헤더: `Cache-Control: public, max-age=3600, s-maxage=3600, stale-while-revalidate=600`
- `ETag` 지원: 변경이 없으면 `304 Not Modified` 반환

**GitHub README 사용자 관점**에서 이는 다음을 의미합니다.

- 방금 체크인했다고 뱃지가 **즉시** 업데이트되지는 않습니다.
- 최대 1시간까지 지연될 수 있습니다. (GitHub의 이미지 프록시
  `camo.githubusercontent.com` 가 추가로 캐싱하기도 합니다.)
- 급하게 최신 상태를 보고 싶다면 쿼리 파라미터를 살짝 바꿔
  새 URL을 만드는 방법(예: `?v=2`)이 있지만 권장하진 않습니다.

---

## 문제 해결 (FAQ)

**Q. `404 Not Found`가 나와요.**

원인은 둘 중 하나입니다.

1. 토큰이 잘못 복사됐거나 존재하지 않는 토큰입니다.
   `POST /api/v1/habits/{habitId}/badges` 응답의 `token` 값을
   다시 확인하세요.
2. 습관을 다시 **비공개**로 바꿨습니다. `is_public = false` 상태의
   습관은 뱃지를 서빙하지 않습니다 (`BADGE_403` → 상위에서 404 처리).

**Q. SVG가 깨져 보이거나 옛날 상태로 남아 있어요.**

- 브라우저 / GitHub의 캐시 때문입니다. 브라우저라면 강제 새로고침
  (`Ctrl+Shift+R` / `Cmd+Shift+R`)을 시도하세요.
- GitHub README는 camo 프록시가 이미지를 재캐시하기까지 수 분 정도
  더 걸릴 수 있습니다.

**Q. 체크인을 했는데 잔디가 안 채워져요.**

- 서버 캐시 TTL 1시간을 기다리면 자동 갱신됩니다.
- 로컬 개발 환경이라면 Redis에서 `badge:svg:*` 키를 삭제해
  즉시 다음 요청 때 새로 생성되도록 할 수 있습니다.

**Q. `429 Too Many Requests`가 떠요.**

- 뱃지 SVG 경로 자체는 **rate-limit 면제**지만, 토큰을 발급하는
  `POST /api/v1/habits/{id}/badges`나 기타 API는 시간당 제한이
  있습니다 (익명 60회, 인증 1000회). `Retry-After` 헤더의
  초 값만큼 대기하세요.

---

## 스크린샷

<!-- TODO: insert screenshots of light+dark badges -->
<!-- TODO: add calendar badge screenshot (light) -->
<!-- TODO: add calendar badge screenshot (dark) -->
<!-- TODO: add streak + total badge screenshots side-by-side -->

스크린샷은 곧 추가될 예정입니다.

---

## 다음 단계

- 완성된 README 샘플: [`../examples/readme-example.md`](../examples/readme-example.md)
- 전체 엔드포인트 레퍼런스: [`./api-reference.md`](./api-reference.md)
- 인터랙티브 문서(실행 가능): `http://localhost:8080/swagger-ui.html`
