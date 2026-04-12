# 예시 README — `jaejinu`의 습관 보드

> 이 파일은 **그대로 복사해서 써도 되는 샘플 README** 입니다.
> 토큰 값은 전부 가짜(`abc123def456...` 형태)이니 실제로 발급받은
> 값으로 교체해서 사용하세요.

---

## 렌더링 결과 미리보기

아래부터 `---` 위까지가 실제 README 본문입니다. GitHub에서
다음처럼 보이게 됩니다.

---

# 안녕하세요, jaejinu 입니다 👋

매일 조금씩, 꾸준히.
코딩 · 운동 · 독서 세 가지 습관을 Habit Tracker로 기록하고 있습니다.

## 오늘의 나

최근 365일 체크인 상태 (dark 테마):

![coding calendar](http://localhost:8080/api/v1/badges/abc123def456aaaaaaaaaaaaaaaaaaaaaaaaaaaa/calendar.svg?theme=dark&color=2da44e)

![workout calendar](http://localhost:8080/api/v1/badges/abc123def456bbbbbbbbbbbbbbbbbbbbbbbbbbbb/calendar.svg?theme=dark&color=f97316)

![reading calendar](http://localhost:8080/api/v1/badges/abc123def456cccccccccccccccccccccccccccc/calendar.svg?theme=dark&color=2f81f7)

## 연속 달성

지금 이어오는 중인 streak (한국어 라벨):

![coding streak](http://localhost:8080/api/v1/badges/abc123def456aaaaaaaaaaaaaaaaaaaaaaaaaaaa/streak.svg?lang=ko&theme=dark)
![workout streak](http://localhost:8080/api/v1/badges/abc123def456bbbbbbbbbbbbbbbbbbbbbbbbbbbb/streak.svg?lang=ko&theme=dark)
![reading streak](http://localhost:8080/api/v1/badges/abc123def456cccccccccccccccccccccccccccc/streak.svg?lang=ko&theme=dark)

## 총 달성

지금까지 누적 체크인 횟수:

![coding total](http://localhost:8080/api/v1/badges/abc123def456aaaaaaaaaaaaaaaaaaaaaaaaaaaa/total.svg?lang=ko&theme=dark)
![workout total](http://localhost:8080/api/v1/badges/abc123def456bbbbbbbbbbbbbbbbbbbbbbbbbbbb/total.svg?lang=ko&theme=dark)
![reading total](http://localhost:8080/api/v1/badges/abc123def456cccccccccccccccccccccccccccc/total.svg?lang=ko&theme=dark)

---

> 이 README는 [Habit Tracker](https://github.com/jaejinu/habit-tracker)의
> 공개 뱃지 API로 만들어졌습니다. 직접 꾸미는 법은
> [`docs/badge-guide.md`](../docs/badge-guide.md) 참고.

---

## 원본 Markdown 소스

위 미리보기가 실제로 사용한 Markdown 입니다. 복사해서 본인 토큰으로
3군데만 교체하면 됩니다 (`abc123def456aaaa...`, `abc123def456bbbb...`,
`abc123def456cccc...`).

````markdown
# 안녕하세요, jaejinu 입니다 👋

매일 조금씩, 꾸준히.
코딩 · 운동 · 독서 세 가지 습관을 Habit Tracker로 기록하고 있습니다.

## 오늘의 나

최근 365일 체크인 상태 (dark 테마):

![coding calendar](http://localhost:8080/api/v1/badges/abc123def456aaaaaaaaaaaaaaaaaaaaaaaaaaaa/calendar.svg?theme=dark&color=2da44e)

![workout calendar](http://localhost:8080/api/v1/badges/abc123def456bbbbbbbbbbbbbbbbbbbbbbbbbbbb/calendar.svg?theme=dark&color=f97316)

![reading calendar](http://localhost:8080/api/v1/badges/abc123def456cccccccccccccccccccccccccccc/calendar.svg?theme=dark&color=2f81f7)

## 연속 달성

지금 이어오는 중인 streak (한국어 라벨):

![coding streak](http://localhost:8080/api/v1/badges/abc123def456aaaaaaaaaaaaaaaaaaaaaaaaaaaa/streak.svg?lang=ko&theme=dark)
![workout streak](http://localhost:8080/api/v1/badges/abc123def456bbbbbbbbbbbbbbbbbbbbbbbbbbbb/streak.svg?lang=ko&theme=dark)
![reading streak](http://localhost:8080/api/v1/badges/abc123def456cccccccccccccccccccccccccccc/streak.svg?lang=ko&theme=dark)

## 총 달성

지금까지 누적 체크인 횟수:

![coding total](http://localhost:8080/api/v1/badges/abc123def456aaaaaaaaaaaaaaaaaaaaaaaaaaaa/total.svg?lang=ko&theme=dark)
![workout total](http://localhost:8080/api/v1/badges/abc123def456bbbbbbbbbbbbbbbbbbbbbbbbbbbb/total.svg?lang=ko&theme=dark)
![reading total](http://localhost:8080/api/v1/badges/abc123def456cccccccccccccccccccccccccccc/total.svg?lang=ko&theme=dark)
````

---

## How this was set up

전체 과정은 [`docs/badge-guide.md`](../docs/badge-guide.md)에서 5분 분량의
튜토리얼로 안내하고 있습니다. 요약하면:

1. `POST /api/v1/auth/signup` → 계정 생성.
2. `POST /api/v1/auth/login` → JWT 수령.
3. 세 개의 습관(`코딩`, `운동`, `독서`)을 만들고 공개로 전환.
4. 각 습관에 대해 `POST /api/v1/habits/{habitId}/badges` 를 3번씩
   (`CALENDAR`, `STREAK`, `TOTAL`) 호출해 총 9개의 토큰을 받음.
5. 받은 `url` 9개를 위 Markdown의 `abc123def456...` 위치에 교체.

호스트는 개발 기본값 `http://localhost:8080` 이므로, 운영 배포 시에는
실제 도메인으로 바꿔주세요. 토큰은 24자 이상의 URL-safe Base64
문자열입니다 — 샘플에 쓰인 `abc123def456...` 는 일부러 알아보기 쉬운
가짜 값이므로 그대로 붙여넣으면 404가 납니다.
