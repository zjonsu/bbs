package com.bbs.reader.ui.detail

import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.UiState
import com.bbs.reader.ui.components.ErrorView
import com.bbs.reader.ui.components.HtmlContent
import com.bbs.reader.ui.components.LoadingView

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    BackHandler(onBack = onBack)

    Scaffold { padding ->
        when (val ui = state) {
            UiState.Loading -> LoadingView(Modifier.padding(padding))
            is UiState.Error -> ErrorView(
                message = ui.message,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding)
            )
            is UiState.Success -> {
                val pageHtml = remember(ui.data, dark) {
                    buildDetailPageHtml(ui.data, dark)
                }
                HtmlContent(
                    html = pageHtml,
                    darkTheme = dark,
                    enablePinchZoom = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

private fun buildDetailPageHtml(detail: PostDetail, darkTheme: Boolean): String {
    val muted = if (darkTheme) "#9E9E9E" else "#757575"
    val border = if (darkTheme) "#333333" else "#E0E0E0"
    val primary = "#C62828"
    val meta = listOfNotNull(
        detail.author.takeIf { it.isNotBlank() },
        detail.time.takeIf { it.isNotBlank() },
        detail.viewCount.takeIf { it > 0 }?.let { "조회 $it" },
        detail.voteCount.takeIf { it > 0 }?.let { "추천 $it" }
    ).joinToString(" · ")
    val commentCount = detail.comments.size.coerceAtLeast(detail.commentCount)
    val commentsHtml = if (detail.comments.isEmpty()) {
        """<p style="color:$muted;margin:0;">표시할 댓글이 없습니다.</p>"""
    } else {
        detail.comments.joinToString("\n") { commentRowHtml(it, muted, border, primary) }
    }

    return """
        <div style="padding:12px 16px 8px;">
          <h1 style="font-size:1.35rem;line-height:1.35;margin:0 0 10px;font-weight:700;">
            ${escapeHtml(detail.title)}
          </h1>
          <div style="color:$muted;font-size:0.85rem;margin-bottom:12px;">$meta</div>
        </div>
        <hr style="border:none;border-top:1px solid $border;margin:0;"/>
        <div style="padding:8px 12px 16px;">
          ${detail.contentHtml}
        </div>
        <hr style="border:none;border-top:1px solid $border;margin:0;"/>
        <div style="padding:14px 16px 28px;">
          <h2 style="font-size:1.05rem;margin:0 0 12px;font-weight:600;">댓글 $commentCount</h2>
          $commentsHtml
        </div>
    """.trimIndent()
}

private fun commentRowHtml(
    comment: CommentItem,
    muted: String,
    border: String,
    primary: String
): String {
    val indent = (comment.depth.coerceAtMost(4) * 12)
    val author = escapeHtml(comment.author.ifBlank { "익명" })
    val time = escapeHtml(comment.time)
    val reply = comment.replyTo?.takeIf { it.isNotBlank() }?.let {
        """<div style="color:$primary;font-size:0.8rem;margin:4px 0;">@${escapeHtml(it)}</div>"""
    }.orEmpty()
    return """
        <div style="padding:12px 0 12px ${indent}px;border-bottom:1px solid $border;">
          <div style="font-size:0.9rem;font-weight:600;">$author
            <span style="color:$muted;font-weight:400;font-size:0.75rem;margin-left:8px;">$time</span>
          </div>
          $reply
          <div style="margin-top:4px;">${comment.contentHtml}</div>
        </div>
    """.trimIndent()
}

private fun escapeHtml(text: String): String {
    return Html.escapeHtml(text).toString()
}
