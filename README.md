# 눈팅 (BbsReader)

볼라고 스타일의 커뮤니티 눈팅용 안드로이드 앱입니다.  
읽기 전용이며, 글/댓글 작성은 지원하지 않습니다.

## 현재 지원

- **딴지일보**
  - HOT (`/hot_all`)
- **AAGAG** ([aagag.com](https://aagag.com/))
  - ISSUE (`/issue/`)
- **다모앙** ([damoang.net](https://damoang.net/))
  - 공감글 (`/empathy`)
  - 모아보기 (`/explore`)
  - 소모임 공감글 (홈 위젯)
- **웃긴대학** ([m.humoruniv.com](https://m.humoruniv.com/main.html))
  - 웃긴자료 베스트 (`table=pds`)
  - 웃수다 베스트 (메인 위젯)
- **SLR클럽** ([m.slrclub.com](https://m.slrclub.com/))
  - 인기글
  - 갤러리(작품/주제/습작/시민기자단/오늘의사진) — 썸네일 그리드
  - 성인게시판(로그인 필요)
- 게시글 목록 → 상세(본문 + 댓글)
- 상세에서 좌/우 스와이프로 이전·다음 글
- 읽은 글 흐리게 표시
- 원문 브라우저 열기 / 공유
- 다크 모드(시스템 설정 연동)

## 구조

커뮤니티를 하나씩 추가할 수 있도록 `CommunityCatalog` + 사이트별 파서로 분리했습니다.  
다음 커뮤니티는 카탈로그와 파서만 추가하면 됩니다.

## 빌드

Android Studio에서 이 폴더를 열고 Run 하거나:

```bash
./gradlew :app:assembleDebug
```

생성된 APK: `app/build/outputs/apk/debug/app-debug.apk`

## 주의

- 각 사이트의 공식 앱이 아닙니다.
- 콘텐츠 권리와 책임은 원 작성자 및 해당 사이트에 있습니다.
- 사이트 HTML 구조 변경 시 파서 수정이 필요할 수 있습니다.
