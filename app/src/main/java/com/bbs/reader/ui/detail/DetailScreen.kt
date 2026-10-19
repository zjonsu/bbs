package com.bbs.reader.ui.detail

import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bbs.reader.data.local.ReadHistoryStore
import com.bbs.reader.data.local.SettingsStore
import com.bbs.reader.data.model.AppSettings
import com.bbs.reader.data.model.CommentItem
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.PostSummary
import com.bbs.reader.data.model.UiState
import com.bbs.reader.data.repository.BoardRepository
import com.bbs.reader.data.repository.PostBrowseSession
import com.bbs.reader.ui.components.ErrorView
import com.bbs.reader.ui.components.HtmlContent
import com.bbs.reader.ui.components.LoadingView
import com.bbs.reader.ui.components.LoginRequiredView
import com.bbs.reader.ui.reader.isReaderDark
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun DetailScreen(
    mid: String,
    postId: String,
    repository: BoardRepository,
    readHistoryStore: ReadHistoryStore,
    settingsStore: SettingsStore,
    reloadToken: Int = 0,
    onReloadConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onLoginClick: (communityId: String) -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val browsePosts = remember(mid, postId) { PostBrowseSession.snapshot() }
    val startIndex = remember(mid, postId, browsePosts) {
        PostBrowseSession.indexOf(mid, postId)
    }
    val usePager = browsePosts.size > 1 && startIndex >= 0

    if (!usePager) {
        val vm: DetailViewModel = viewModel(
            key = "$mid-$postId",
            factory = DetailViewModel.factory(mid, postId, repository, readHistoryStore)
        )
        LaunchedEffect(reloadToken) {
            if (reloadToken != 0) {
                onReloadConsumed()
                vm.load()
            }
        }
        DetailScaffold(
            viewModel = vm,
            settingsStore = settingsStore,
            onLoginClick = onLoginClick
        )
        return
    }

    val pagerState = rememberPagerState(initialPage = startIndex) { browsePosts.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadToken, pagerState.settledPage) {
        if (reloadToken != 0) {
            onReloadConsumed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { page ->
                val post = browsePosts[page]
                "${post.mid}-${post.id}"
            }
        ) { page ->
            val post = browsePosts[page]
            DetailPage(
                post = post,
                repository = repository,
                readHistoryStore = readHistoryStore,
                settingsStore = settingsStore,
                reloadToken = if (page == pagerState.settledPage) reloadToken else 0,
                onLoginClick = onLoginClick
            )
        }

        // WebView가 제스처를 가로챌 때를 대비한 가장자리 스와이프
        Row(modifier = Modifier.fillMaxSize()) {
            EdgeSwipeZone(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight(),
                onSwipe = { dx ->
                    if (dx > 56f && pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                }
            )
            Spacer(modifier = Modifier.weight(1f))
            EdgeSwipeZone(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight(),
                onSwipe = { dx ->
                    if (dx < -56f && pagerState.currentPage < browsePosts.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                }
            )
        }
    }
}

@Composable
private fun EdgeSwipeZone(
    modifier: Modifier,
    onSwipe: (dx: Float) -> Unit
) {
    var totalDx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { totalDx = 0f },
                onDragEnd = {
                    if (abs(totalDx) > 56f) onSwipe(totalDx)
                    totalDx = 0f
                },
                onDragCancel = { totalDx = 0f },
                onHorizontalDrag = { _, dragAmount -> totalDx += dragAmount }
            )
        }
    )
}

@Composable
private fun DetailPage(
    post: PostSummary,
    repository: BoardRepository,
    readHistoryStore: ReadHistoryStore,
    settingsStore: SettingsStore,
    reloadToken: Int,
    onLoginClick: (communityId: String) -> Unit
) {
    val vm: DetailViewModel = viewModel(
        key = "${post.mid}-${post.id}",
        factory = DetailViewModel.factory(post.mid, post.id, repository, readHistoryStore)
    )
    LaunchedEffect(reloadToken) {
        if (reloadToken != 0) vm.load()
    }
    DetailScaffold(
        viewModel = vm,
        settingsStore = settingsStore,
        onLoginClick = onLoginClick
    )
}

@Composable
private fun DetailScaffold(
    viewModel: DetailViewModel,
    settingsStore: SettingsStore,
    onLoginClick: (communityId: String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

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
