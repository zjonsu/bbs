package com.bbs.reader.ui.components

import android.content.Intent
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bbs.reader.data.model.AppSettings
import com.bbs.reader.ui.reader.toReaderCss
import kotlin.math.abs

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
fun LoginRequiredView(
    siteName: String,
    message: String,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = siteName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Button(onClick = onLogin, modifier = Modifier.padding(top = 8.dp)) {
                Text("로그인")
            }
            Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                Text("다시 시도")
            }
        }
    }
}

/**
 * @param onHorizontalSwipe 가로가 세로보다 명확히 클 때만 호출 (dx: +오른쪽 / -왼쪽).
 *        null이면 제스처 감지를 붙이지 않는다.
 *
 * [확정 2026-10-21] 상세 세로/가로 분리 계약. 감지 로직·임계·nestedScroll 원복 금지
 * → .cursor/rules/detail-swipe-gestures.mdc
 */
@Composable
fun HtmlContent(
    html: String,
    appSettings: AppSettings,
    modifier: Modifier = Modifier,
    enablePinchZoom: Boolean = false,
    onHorizontalSwipe: ((dx: Float) -> Unit)? = null
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

    val swipeHolder = remember { HorizontalSwipeHolder() }
    SideEffect { swipeHolder.onSwipe = onHorizontalSwipe }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        // iframe(유튜브 등) 내부 로드는 WebView가 처리
                        if (!request.isForMainFrame) return false
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
                // 유튜브/비메오 임베드 재생에 필요. 본문 script는 prepareContentHtml 에서 제거됨
                settings.javaScriptEnabled = true
                settings.loadsImagesAutomatically = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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
                // Pager와 제스처 경쟁을 줄이기 위해 nested scroll 끄고, 가로는 OnTouch로만 판정
                isNestedScrollingEnabled = false
                attachHorizontalSwipeDetector(this, swipeHolder)
            }
        },
        update = { webView ->
            swipeHolder.onSwipe = onHorizontalSwipe
            // 같은 HTML이면 재로드하지 않음 (터치 중 CANCEL 방지)
            if (webView.tag != wrapped) {
                webView.tag = wrapped
                webView.loadDataWithBaseURL(
                    "file:///android_asset/",
                    wrapped,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }
    )
}

private class HorizontalSwipeHolder {
    @Volatile
    var onSwipe: ((Float) -> Unit)? = null
}

private fun attachHorizontalSwipeDetector(webView: WebView, holder: HorizontalSwipeHolder) {
    var startX = 0f
    var startY = 0f
    var pointerCount = 0
    var navigated = false

    fun tryNavigate(dx: Float, dy: Float): Boolean {
        val cb = holder.onSwipe ?: return false
        if (navigated || pointerCount > 1) return false
        val minDistance = (webView.width.coerceAtLeast(1) * 0.12f).coerceAtLeast(56f)
        if (abs(dx) > abs(dy) * 1.5f && abs(dx) >= minDistance) {
            navigated = true
            cb.invoke(dx)
            return true
        }
        return false
    }

    val flingDetector = GestureDetector(
        webView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                if (abs(velocityX) <= abs(velocityY) * 1.5f || abs(velocityX) <= 600f) {
                    return false
                }
                return tryNavigate(e2.rawX - e1.rawX, e2.rawY - e1.rawY)
            }
        }
    )

    webView.setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerCount = 1
                navigated = false
                startX = event.rawX
                startY = event.rawY
                v.parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> pointerCount = event.pointerCount
            MotionEvent.ACTION_POINTER_UP -> pointerCount = event.pointerCount - 1
            MotionEvent.ACTION_UP -> {
                // 플링 미감지 시 최종 변위로 판정
                if (!navigated) {
                    tryNavigate(event.rawX - startX, event.rawY - startY)
                }
                pointerCount = 0
            }
            MotionEvent.ACTION_CANCEL -> pointerCount = 0
        }
        flingDetector.onTouchEvent(event)
        false
    }
}
