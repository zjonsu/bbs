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
        a {
          color: $link;
          text-decoration: underline;
          word-break: break-all;
        }
        p { margin: 0.35em 0; }
        p:empty { display: none; }
        .meta { color: $muted; }
    """.trimIndent()
}

fun AppSettings.isReaderDark(): Boolean = readerBackground.night
