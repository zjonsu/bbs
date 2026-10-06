package com.bbs.reader.ui.components

import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text("다시 시도")
            }
        }
    }
}

@Composable
fun HtmlContent(
    html: String,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    enablePinchZoom: Boolean = false
) {
    val bg = if (darkTheme) "#121212" else "#FFFFFF"
    val fg = if (darkTheme) "#ECECEC" else "#1B1B1B"
    val meta = if (enablePinchZoom) {
        """<meta name="viewport" content="width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=5, user-scalable=yes"/>"""
    } else {
        """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>"""
    }
    val wrapped = remember(html, darkTheme, enablePinchZoom) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          $meta
          <style>
            body {
              margin: 0;
              padding: 0;
              font-family: -apple-system, BlinkMacSystemFont, 'Noto Sans KR', sans-serif;
              font-size: 16px;
              line-height: 1.65;
              color: $fg;
              background: $bg;
              word-break: break-word;
            }
            img, video, iframe {
              max-width: 100% !important;
              height: auto !important;
            }
            a { color: #C62828; }
            p { margin: 0.6em 0; }
          </style>
        </head>
        <body>$html</body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                settings.loadsImagesAutomatically = true
                settings.domStorageEnabled = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                if (enablePinchZoom) {
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                } else {
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isNestedScrollingEnabled = true
            }
        },
        update = { webView ->
            val encoded = Base64.encodeToString(wrapped.toByteArray(Charsets.UTF_8), Base64.NO_PADDING)
            webView.loadData(encoded, "text/html; charset=utf-8", "base64")
        }
    )
}
