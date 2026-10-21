# feature

## 2026-10-21 12:23 — 게시판 좌우 스와이프 + 제스처 축 잠금

### 요청
> 게시판 좌우/상하 스와이프 민감도가 원복된 것 같고, 영역이 겹쳐 어색하며, 게시판 리스트 간 좌우 스와이프도 빠졌다. 계획대로 구현.

### 처리 결과
- `BoardScreen`에 `HorizontalPager`로 같은 커뮤니티 게시판 간 좌우 스와이프 추가 (탭·route 동기화)
- `AxisLockNestedScrollConnection`으로 세로 스크롤 vs 가로 전환 분리
- `AppNav`는 repository/readHistory 전달, 로그인 후 refresh 토큰 처리
- 상세 `EdgeSwipeZone`도 가로 우세일 때만 전환
- SM-S938N에 installDebug·실행 완료
