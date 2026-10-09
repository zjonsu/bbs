package com.bbs.reader.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
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
import com.bbs.reader.data.model.AppSettings
import com.bbs.reader.ui.reader.toReaderCss

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
    appSettings: AppSettings,
    modifier: Modifier = Modifier,
    enablePinchZoom: Boolean = false
) {
    val css = remember(appSettings) { appSettings.toReaderCss() }
    val meta = if (enablePinchZoom) {
        """<meta name="viewport" content="width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=5, user-scalable=yes"/>"""
    } else {
        """<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>"""
    }
    val wrapped = remember(html, css, enablePinchZoom) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          $meta
          <style>
            $css
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
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        return openExternalUrl(view, request.url?.toString())
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                        return openExternalUrl(view, url)
                    }

                    private fun openExternalUrl(view: WebView, url: String?): Boolean {
                        if (url.isNullOrBlank()) return true
                        // 초기 loadData / about:blank 등은 WebView 내부 처리
                        if (url.startsWith("data:", ignoreCase = true) ||
                            url.startsWith("about:", ignoreCase = true) ||
                            url.startsWith("file:", ignoreCase = true)
                        ) {
                            return false
                        }
                        if (url.startsWith("http://", ignoreCase = true) ||
                            url.startsWith("https://", ignoreCase = true)
                        ) {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                view.context.startActivity(intent)
                            }
                            return true
                        }
                        return true
                    }
                }
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
            // assets 폰트(@font-face) 로드를 위해 baseURL 사용
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                wrapped,
                "text/html",
                "utf-8",
                null
            )
        }
    )
}
