# question

## 2026-10-21 11:28 — 상세 스와이프 인접글 읽음 처리 조사

### 요청
> 글 하나 보고 목록으로 돌아오면 아래 안 읽은 글도 읽음(흐린 제목)으로 보임. 스와이프가 인접 글을 읽음 처리하는 것 같다는 가설. 읽음 저장·목록 스타일·스와이프/프리로드·원인 사슬 조사.

### 처리 결과
- **가설 맞음.** `HorizontalPager(beyondViewportPageCount = 1)`가 인접 페이지를 미리 컴포즈하고, 각 `DetailPage`의 `DetailViewModel.load()` 성공 시 `ReadHistoryStore.markRead`가 호출됨. 스와이프 없이도 현재±1이 읽음 처리됨.
- 저장: DataStore `read_history` / key `read_post_ids` (`ReadHistoryStore`)
- 목록 스타일: `BoardScreen` `PostRow`/`ThumbnailCard`에서 `isRead` → alpha 0.45/0.5
- 핵심 파일: `DetailScreen.kt`, `DetailViewModel.kt`, `ReadHistoryStore.kt`, `PostBrowseSession.kt`
