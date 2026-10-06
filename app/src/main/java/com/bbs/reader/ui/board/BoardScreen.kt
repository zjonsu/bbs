package com.bbs.reader.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbs.reader.data.model.PostSummary
import com.bbs.reader.data.model.UiState
import com.bbs.reader.data.repository.CommunityCatalog
import com.bbs.reader.ui.components.ErrorView
import com.bbs.reader.ui.components.LoadingView
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    viewModel: BoardViewModel,
    boardId: String,
    onBack: () -> Unit,
    onBoardChange: (String) -> Unit = {},
    onPostClick: (PostSummary) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val readIds by viewModel.readIds.collectAsStateWithLifecycle()
    val board = CommunityCatalog.findBoard(boardId)
    val community = board?.let { CommunityCatalog.findCommunity(it.communityId) }
    val boards = community?.boards.orEmpty()
    val selectedIndex = boards.indexOfFirst { it.id == boardId }.coerceAtLeast(0)
    val showTabs = boards.size > 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = community?.name ?: "게시판",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = board?.name.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
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
            if (showTabs) {
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 12.dp,
                    divider = {}
                ) {
                    boards.forEachIndexed { index, info ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { if (info.id != boardId) onBoardChange(info.id) },
                            text = { Text(info.name) }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }

            when (val ui = state) {
                UiState.Loading -> LoadingView()
                is UiState.Error -> ErrorView(ui.message, onRetry = viewModel::refresh)
                is UiState.Success -> {
                    val listState = rememberLazyListState()
                    LaunchedEffect(listState) {
                        snapshotFlow {
                            val info = listState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= info.totalItemsCount - 3
                        }.distinctUntilChanged().collect { nearEnd ->
                            if (nearEnd) viewModel.loadMore()
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(ui.data.posts, key = { "${it.mid}-${it.id}" }) { post ->
                                PostRow(
                                    post = post,
                                    isRead = readIds.contains(post.id),
                                    onClick = { onPostClick(post) }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                )
                            }
                            if (!ui.data.hasMore) {
                                item {
                                    Text(
                                        text = "마지막 페이지입니다",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostRow(
    post: PostSummary,
    isRead: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (isRead) 0.45f else 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (post.commentCount > 0) {
                Text(
                    text = "[${post.commentCount}]",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetaText(post.author, alpha)
            MetaText(post.time, alpha)
            if (post.viewCount > 0) MetaText("조회 ${post.viewCount}", alpha)
            if (post.voteCount > 0) MetaText("추천 ${post.voteCount}", alpha)
        }
    }
}

@Composable
private fun MetaText(text: String, alpha: Float) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * alpha)
    )
}
