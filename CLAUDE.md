# bbs — 요청 기록 진입점

이 프로젝트의 작업 맥락은 `requests/`에 누적한다. 매 요청마다:

1. **읽기** — 이 파일 → `requests/index.md` → 관련 분류 md
2. **처리** — 요청 수행
3. **기록** — 분류 md 맨 위에 항목 추가
4. **인덱스 갱신** — `requests/index.md` + `~/.claude/request-log/INDEX.md`

## 분류

| 분류 | 파일 | 설명 |
| --- | --- | --- |
| bugfix | [requests/bugfix.md](requests/bugfix.md) | 기존 동작 결함 수정 |
| docs | [requests/docs.md](requests/docs.md) | 확정 결정·문서화 |
| etc | [requests/etc.md](requests/etc.md) | 설치·기타 |
| feature | [requests/feature.md](requests/feature.md) | 새 기능·구현 |
| question | [requests/question.md](requests/question.md) | 코드 변경 없는 질의·조사 |
| system-setup | [requests/system-setup.md](requests/system-setup.md) | 환경·기록 시스템 |

## 프로젝트 요약

- Android 커뮤니티 눈팅 앱 (BbsReader) — 읽기 전용
- 목록 읽음 표시, 상세 좌우 스와이프(HorizontalPager)

## 확정 결정 (원복 금지)

| 항목 | 상태 | 규칙 |
| --- | --- | --- |
| 상세 세로/가로 스와이프 | **확정 (2026-10-21)** — 사용자가 적합 확인 | [`.cursor/rules/detail-swipe-gestures.mdc`](.cursor/rules/detail-swipe-gestures.mdc) |

요약: 상세는 Pager 드래그 OFF + WebView 터치로만 가로 전환. EdgeSwipeZone·조기 축 잠금·불필요 HTML 재로드 금지.
