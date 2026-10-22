# bugfix

## 2026-10-22 11:26 — 본문 HTML 인라인 색상 제거

### 요청
> 게시물 본문을 볼때 스샷과 같이 HTML 색이 적용되어 읽을수가 없는 경우가 있으니 HTML 색을 삭제 해줘

### 처리 결과
- `prepareContentHtml`의 테마 대비 선택 제거 → 인라인 `color`/`background*`/`color`·`bgcolor` 속성 전부 제거
- 리더 CSS 기본 글자색·배경만 사용 (흰 박스+밝은 글씨 등 대비 깨짐 해소)
- 수정: `HtmlContentPrep.kt`, `HtmlContentPrepTest.kt` (단위 테스트 통과)

## 2026-10-21 12:42 — 상세 가로 스와이프 미동작 수정

### 요청
> 세로 스와이프는 괜찮은데 가로가 작동하지 않는다.

### 처리 결과
- 조기 축 잠금·과한 거리 임계(22%) 제거 → UP/플링 시 최종 변위로 판정 (12%/56px, 가로 1.5배)
- HTML 동일 시 WebView 재로드 생략 (터치 CANCEL 방지)
- 실폰 재설치 완료

## 2026-10-21 12:39 — 상세 본문 세로/가로 스와이프 분리 재조정

### 요청
> 게시물 본문 세로 스와이프와 가로 스와이프가 아직 어색하고 잘 동작하지 않으니 조절해 달라.

### 처리 결과
- HorizontalPager `userScrollEnabled=false` + EdgeSwipeZone 오버레이 제거 (가장자리 세로 스크롤 방해 해소)
- WebView OnTouch로 가로가 세로 2배 이상·화면 22% 이상일 때만 글 전환
- `isNestedScrollingEnabled=false`로 Pager와의 제스처 경쟁 완화
- 실폰 재설치·실행 완료

## 2026-10-21 11:32 — 스와이프 프리로드 시 인접글 읽음 표기 수정

### 요청
> 데이터는 미리 로드해도 되지만, 읽음 표기는 사용자가 해당 게시물을 실제로 읽었을 때만 처리해 달라.

### 처리 결과
- `DetailViewModel.load()` 성공 시 `markRead` 호출 제거 (프리로드도 읽음 처리되던 원인)
- `markAsRead()` 분리 후, pager에서는 `settledPage`인 페이지만 (`isActive`), 단일 상세에서는 진입 시 호출
- 수정 파일: `DetailViewModel.kt`, `DetailScreen.kt`
