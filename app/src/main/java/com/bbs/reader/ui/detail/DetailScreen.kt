package com.bbs.reader.ui.detail

import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbs.reader.data.local.SettingsStore
import com.bbs.reader.data.model.AppSettings
import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.UiState
import com.bbs.reader.ui.components.ErrorView
import com.bbs.reader.ui.components.HtmlContent
import com.bbs.reader.ui.components.LoadingView
import com.bbs.reader.ui.components.LoginRequiredView
import com.bbs.reader.ui.reader.isReaderDark

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onLoginClick: (communityId: String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    BackHandler(onBack = onBack)

    Scaffold { padding ->
        when (val ui = state) {
            UiState.Loading -> LoadingView(Modifier.padding(padding))
            is UiState.Error -> ErrorView(
                message = ui.message,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding)
            )
            is UiState.LoginRequired -> LoginRequiredView(
                siteName = ui.siteName,
                message = ui.message,
                onLogin = { onLoginClick(ui.communityId) },
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding)
            )
            is UiState.Success -> {
                val pageHtml = remember(ui.data, settings) {
                    buildDetailPageHtml(ui.data, settings)
                }
                HtmlContent(
                    html = pageHtml,
                    appSettings = settings,
                    enablePinchZoom = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

private fun buildDetailPageHtml(detail: PostDetail, settings: AppSettings): String {
    val darkTheme = settings.isReaderDark()
    val mutedClass = "meta"
    val border = if (darkTheme) "#333333" else "#E0E0E0"
    val primary = if (darkTheme) "#EF5350" else "#C62828"
    val authorHtml = detail.author.takeIf { it.isNotBlank() }?.let {
        """<span style="display:inline-block;max-width:42%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;vertical-align:bottom;">${escapeHtml(it)}</span>"""
    }
    val metaParts = listOfNotNull(
        authorHtml,
        detail.time.takeIf { it.isNotBlank() }?.let { escapeHtml(it) },
        detail.viewCount.takeIf { it > 0 }?.let { "조회 $it" },
        detail.voteCount.takeIf { it > 0 }?.let { "추천 $it" }
    ).joinToString(" · ")
    val contentHtml = prepareContentHtml(
        detail.contentHtml,
        darkTheme,
        detail.url,
        detail.linkTitles
    )
    val commentCount = detail.comments.size.coerceAtLeast(detail.commentCount)
    val commentsHtml = if (detail.comments.isEmpty()) {
        """<p class="$mutedClass" style="margin:0;">표시할 댓글이 없습니다.</p>"""
    } else {
        detail.comments.joinToString("\n") {
            commentRowHtml(it, mutedClass, border, primary, darkTheme, detail.url, detail.linkTitles)
        }
    }

    val titleSize = (settings.effectiveFontSizeSp * 1.05f).coerceAtLeast(16f)
    val metaSize = (settings.effectiveFontSizeSp * 0.8f).coerceAtLeast(11f)

    return """
        <div style="padding:4px 0 8px;">
          <h1 style="font-size:${titleSize}px;line-height:1.35;margin:0 0 8px;font-weight:700;">
            ${escapeHtml(detail.title)}
          </h1>
          <div class="$mutedClass" style="font-size:${metaSize}px;margin-bottom:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">$metaParts</div>
        </div>
        <hr style="border:none;border-top:1px solid $border;margin:0;"/>
        <div style="padding:8px 0 16px;">
          $contentHtml
        </div>
        <hr style="border:none;border-top:1px solid $border;margin:0;"/>
        <div style="padding:14px 0 28px;">
          <h2 style="font-size:${settings.effectiveFontSizeSp}px;margin:0 0 12px;font-weight:600;">댓글 $commentCount</h2>
          $commentsHtml
        </div>
    """.trimIndent()
}

private fun commentRowHtml(
    comment: CommentItem,
    mutedClass: String,
    border: String,
    primary: String,
    darkTheme: Boolean,
    baseUrl: String,
    linkTitles: Map<String, String>
): String {
    val indent = (comment.depth.coerceAtMost(4) * 12)
    val author = escapeHtml(comment.author.ifBlank { "익명" })
    val time = escapeHtml(comment.time)
    val reply = comment.replyTo?.takeIf { it.isNotBlank() }?.let {
        """<div style="color:$primary;font-size:0.8em;margin:4px 0;">@${escapeHtml(it)}</div>"""
    }.orEmpty()
    val body = prepareContentHtml(comment.contentHtml, darkTheme, baseUrl, linkTitles)
    return """
        <div style="padding:12px 0 12px ${indent}px;border-bottom:1px solid $border;">
          <div style="font-size:0.95em;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">$author
            <span class="$mutedClass" style="font-weight:400;font-size:0.8em;margin-left:8px;">$time</span>
          </div>
          $reply
          <div style="margin-top:4px;">$body</div>
        </div>
    """.trimIndent()
}

private fun escapeHtml(text: String): String {
    return Html.escapeHtml(text).toString()
}
