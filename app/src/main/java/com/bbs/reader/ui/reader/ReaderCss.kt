package com.bbs.reader.ui.reader

import com.bbs.reader.data.model.AppSettings
import com.bbs.reader.data.model.FontOption

/** 상세 WebView용 CSS (ebook ReaderSettings 공식을 웹에 맞게 변환) */
fun AppSettings.toReaderCss(): String {
    val family = fontFamily.coerceWeight(fontWeight).let { fontFamily.cssFamilyName() }
    val asset = fontFamily.assetFileName(fontWeight)
    val fontFace = if (asset != null) {
        """
        @font-face {
          font-family: '${fontFamily.cssFamilyName()}';
          src: url('fonts/$asset');
          font-weight: normal;
          font-style: normal;
        }
        """.trimIndent()
    } else {
        ""
    }
    val sizePx = effectiveFontSizeSp
    val letter = effectiveLetterSpacingEm
    val link = if (readerBackground.night) "#EF5350" else "#C62828"
    val muted = if (readerBackground.night) "#9E9E9E" else "#757575"
    val stack = when (fontFamily) {
        FontOption.ORIGINAL ->
            "-apple-system, BlinkMacSystemFont, 'Noto Sans KR', sans-serif"
        else ->
            "'$family', -apple-system, BlinkMacSystemFont, 'Noto Sans KR', sans-serif"
    }

    return """
        $fontFace
        body {
          margin: 0;
          padding: ${marginVerticalDp}px ${marginHorizontalDp}px;
          font-family: $stack;
          font-size: ${sizePx}px;
          font-weight: ${fontWeight.css};
          line-height: $lineSpacingVertical;
          letter-spacing: ${letter}em;
          color: $fontColorHex;
          background: ${readerBackground.hex};
          word-break: break-word;
        }
        img, video, iframe {
          max-width: 100% !important;
          height: auto !important;
        }
        img {
          display: block;
          margin: 0.4em 0;
        }
        img.emoji {
          display: inline-block;
          width: 1.35em;
          height: 1.35em;
          margin: 0 0.1em;
          vertical-align: -0.2em;
          object-fit: contain;
        }
        video {
          display: block;
          width: 100%;
          max-height: 80vh;
          margin: 0.5em 0;
          background: #000;
        }
        .video-embed {
          position: relative;
          width: 100%;
          aspect-ratio: 16 / 9;
          margin: 0.5em 0;
          overflow: hidden;
          background: #000;
        }
        .video-embed iframe {
          position: absolute;
          inset: 0;
          width: 100% !important;
          height: 100% !important;
          max-width: none !important;
          border: 0;
        }
        a, a.ext-link {
          color: $link !important;
          text-decoration: underline !important;
          word-break: break-word;
        }
        p { margin: 0.35em 0; }
        p:empty { display: none; }
        .meta { color: $muted; }
    """.trimIndent()
}

fun AppSettings.isReaderDark(): Boolean = readerBackground.night
