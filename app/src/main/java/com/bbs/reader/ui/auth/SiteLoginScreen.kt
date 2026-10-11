package com.bbs.reader.ui.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bbs.reader.data.local.SiteSessionStore
import com.bbs.reader.data.repository.CommunityCatalog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SiteLoginScreen(
    communityId: String,
    siteSessionStore: SiteSessionStore,
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val auth = remember(communityId) { CommunityCatalog.findSiteAuth(communityId) }
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("사이트에 로그인한 뒤 아래 완료를 눌러주세요.") }
    var saving by remember { mutableStateOf(false) }

    if (auth == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("로그인") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                    }
                )
            }
        ) { padding ->
            Text(
                text = "이 사이트는 로그인을 지원하지 않습니다.",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
        }
        return
    }

    DisposableEffect(communityId) {
        siteSessionStore.injectIntoWebView(communityId)
        onDispose { }
    }

    fun completeLogin() {
        if (saving) return
        saving = true
        scope.launch {
            val ok = siteSessionStore.saveFromWebView(communityId)
            saving = false
            if (ok) {
                statusMessage = "로그인 정보를 저장했습니다."
                onLoginSuccess()
            } else {
                statusMessage = "쿠키를 찾지 못했습니다. 로그인 후 다시 완료를 눌러주세요."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${auth.name} 로그인",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    TextButton(onClick = { completeLogin() }, enabled = !saving) {
                        Text("완료")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (progress in 0f..0.99f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = false
                        }
                        loadUrl(auth.loginUrl)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Button(
                onClick = { completeLogin() },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(if (saving) "저장 중…" else "로그인 완료")
            }
        }
    }
}
