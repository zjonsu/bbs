package com.bbs.reader.ui.board

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bbs.reader.data.local.BoardPrefsStore
import com.bbs.reader.data.local.HomePrefsStore
import com.bbs.reader.data.local.ReadHistoryStore
import com.bbs.reader.data.local.SettingsStore
import com.bbs.reader.data.model.AppSettings
import com.bbs.reader.data.model.BoardListStyle
import com.bbs.reader.data.model.PostSummary
import com.bbs.reader.data.model.UiState
import com.bbs.reader.data.repository.BoardRepository
import com.bbs.reader.data.repository.CommunityCatalog
import com.bbs.reader.ui.components.ErrorView
import com.bbs.reader.ui.components.LoadingView
import com.bbs.reader.ui.components.LoginRequiredView
import com.bbs.reader.ui.gestures.AxisLockNestedScrollConnection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    boardId: String,
    repository: BoardRepository,
    readHistoryStore: ReadHistoryStore,
    settingsStore: SettingsStore,
    boardPrefsStore: BoardPrefsStore,
    homePrefsStore: HomePrefsStore,
    loginRefreshToken: Int = 0,
    onLoginRefreshConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onBoardChange: (String) -> Unit = {},
    onPostClick: (PostSummary) -> Unit,
    onLoginClick: (communityId: String) -> Unit = {}
) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val disabledBoardIds by homePrefsStore.disabledBoardIds
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    val routeBoard = CommunityCatalog.findBoard(boardId)
    val community = routeBoard?.let { CommunityCatalog.findCommunity(it.communityId) }
    val boards = community?.boards.orEmpty().filter { it.id !in disabledBoardIds }
    val selectedIndex = boards.indexOfFirst { it.id == boardId }.coerceAtLeast(0)
    val showPager = boards.size > 1

    val pagerState = rememberPagerState(initialPage = selectedIndex) {
        boards.size.coerceAtLeast(1)
    }

    // 현재 게시판이 비활성화되면 첫 활성 게시판으로 이동
    LaunchedEffect(boardId, disabledBoardIds, boards) {
        if (boards.isNotEmpty() && boardId !in boards.map { it.id }) {
            onBoardChange(boards.first().id)
        }
    }

    // route boardId ↔ pager 동기화
    LaunchedEffect(boardId, boards) {
        val idx = boards.indexOfFirst { it.id == boardId }
        if (idx >= 0 && pagerState.currentPage != idx) {
            pagerState.scrollToPage(idx)
        }
    }

    LaunchedEffect(pagerState, boards) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val id = boards.getOrNull(page)?.id ?: return@collect
                if (id != boardId) onBoardChange(id)
            }
    }

    var refreshNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(loginRefreshToken) {
        if (loginRefreshToken != 0) {
            onLoginRefreshConsumed()
            refreshNonce++
        }
    }

    val activeBoardId = boards.getOrNull(pagerState.currentPage)?.id ?: boardId
    val activeBoard = CommunityCatalog.findBoard(activeBoardId)
    val hideRead by boardPrefsStore.hideReadFor(activeBoardId)
        .collectAsStateWithLifecycle(initialValue = false)

    // route 교체(remount) 후에도 목록 상태가 유지되도록 Activity 스코프 VM 사용
    val activity = LocalContext.current as ComponentActivity
    val activeVm: BoardViewModel = viewModel(
        viewModelStoreOwner = activity,
        key = "board-$activeBoardId",
        factory = BoardViewModel.factory(activeBoardId, repository, readHistoryStore)
    )

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(48.dp)
                        .padding(end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    val titleText = buildString {
                        append(community?.name ?: "게시판")
                        val boardName = activeBoard?.name.orEmpty()
                        if (boardName.isNotBlank()) append("[$boardName]")
                    }
                    Text(
                        text = titleText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                boardPrefsStore.setHideRead(activeBoardId, !hideRead)
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (hideRead) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (hideRead) "읽은 글 숨김 해제" else "읽은 글 숨기기",
                            tint = if (hideRead) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    val siteAuth = community?.let { CommunityCatalog.findSiteAuth(it.id) }
                    if (siteAuth != null) {
                        TextButton(
                            onClick = { onLoginClick(siteAuth.communityId) },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("로그인", fontSize = 13.sp)
                        }
                    }
                    IconButton(
                        onClick = activeVm::refresh,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "새로고침",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showPager) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, boards.lastIndex),
                    edgePadding = 12.dp,
                    divider = {}
                ) {
                    boards.forEachIndexed { index, info ->
                        Tab(
                            selected = index == pagerState.currentPage,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = {
                                Text(
                                    text = info.name,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }

            if (boards.isEmpty()) {
                ErrorView("표시할 게시판이 없습니다.", onRetry = onBack)
            } else if (showPager) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { page -> boards[page].id }
                ) { page ->
                    val pageBoard = boards[page]
                    BoardPage(
                        boardId = pageBoard.id,
                        repository = repository,
                        readHistoryStore = readHistoryStore,
                        boardPrefsStore = boardPrefsStore,
                        fontSizeSp = settings.baseFontSizeSp,
                        refreshNonce = if (page == pagerState.settledPage) refreshNonce else 0,
                        onPostClick = onPostClick,
                        onLoginClick = onLoginClick
                    )
                }
            } else {
                BoardPage(
                    boardId = boards.first().id,
                    repository = repository,
                    readHistoryStore = readHistoryStore,
                    boardPrefsStore = boardPrefsStore,
                    fontSizeSp = settings.baseFontSizeSp,
                    refreshNonce = refreshNonce,
                    onPostClick = onPostClick,
                    onLoginClick = onLoginClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardPage(
    boardId: String,
    repository: BoardRepository,
    readHistoryStore: ReadHistoryStore,
    boardPrefsStore: BoardPrefsStore,
    fontSizeSp: Float,
    refreshNonce: Int,
    onPostClick: (PostSummary) -> Unit,
    onLoginClick: (communityId: String) -> Unit
) {
    val activity = LocalContext.current as ComponentActivity
    val vm: BoardViewModel = viewModel(
        viewModelStoreOwner = activity,
        key = "board-$boardId",
        factory = BoardViewModel.factory(boardId, repository, readHistoryStore)
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val readIds by vm.readIds.collectAsStateWithLifecycle()
    val hideRead by boardPrefsStore.hideReadFor(boardId)
        .collectAsStateWithLifecycle(initialValue = false)
    val board = CommunityCatalog.findBoard(boardId)
    val axisLock = remember { AxisLockNestedScrollConnection() }

    LaunchedEffect(refreshNonce) {
        if (refreshNonce != 0) vm.refresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(axisLock)
    ) {
        when (val ui = state) {
            UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(ui.message, onRetry = vm::refresh)
            is UiState.LoginRequired -> LoginRequiredView(
                siteName = ui.siteName,
                message = ui.message,
                onLogin = { onLoginClick(ui.communityId) },
                onRetry = vm::refresh
            )
            is UiState.Success -> {
                val visiblePosts = if (hideRead) {
                    ui.data.posts.filterNot { readIds.contains(it.id) }
                } else {
                    ui.data.posts
                }
                val isThumbGrid = board?.listStyle == BoardListStyle.THUMBNAIL_GRID

                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isThumbGrid) {
                        ThumbnailGrid(
                            posts = visiblePosts,
                            readIds = readIds,
                            hasMore = ui.data.hasMore,
                            onLoadMore = vm::loadMore,
                            onPostClick = onPostClick
                        )
                    } else {
                        PostList(
                            posts = visiblePosts,
                            readIds = readIds,
                            fontSizeSp = fontSizeSp,
                            hasMore = ui.data.hasMore,
                            onLoadMore = vm::loadMore,
                            onPostClick = onPostClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostList(
    posts: List<PostSummary>,
    readIds: Set<String>,
    fontSizeSp: Float,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onPostClick: (PostSummary) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(posts, key = { "${it.mid}-${it.id}" }) { post ->
            PostRow(
                post = post,
                isRead = readIds.contains(post.id),
                fontSizeSp = fontSizeSp,
                onClick = { onPostClick(post) }
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        }
        if (!hasMore) {
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

@Composable
private fun ThumbnailGrid(
    posts: List<PostSummary>,
    readIds: Set<String>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onPostClick: (PostSummary) -> Unit
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(posts, key = { "${it.mid}-${it.id}" }) { post ->
            ThumbnailCard(
                post = post,
                isRead = readIds.contains(post.id),
                onClick = { onPostClick(post) }
            )
        }
        if (!hasMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "마지막 페이지입니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun ThumbnailCard(
    post: PostSummary,
    isRead: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (isRead) 0.5f else 1f
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            if (!post.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = post.thumbnailUrl,
                    contentDescription = post.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(36.dp)
                )
            }
            if (post.commentCount > 0) {
                Text(
                    text = "${post.commentCount}",
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = post.title,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
        Row(
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!post.boardLabel.isNullOrBlank()) {
                Text(
                    text = post.boardLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f * alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            if (post.time.isNotBlank()) {
                Text(
                    text = post.time,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f * alpha),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PostRow(
    post: PostSummary,
    isRead: Boolean,
    fontSizeSp: Float,
    onClick: () -> Unit
) {
    val alpha = if (isRead) 0.45f else 1f
    val metaColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * alpha)
    val titleSize = fontSizeSp.coerceIn(12f, 28f).sp
    val commentSize = (fontSizeSp * 0.85f).coerceIn(11f, 24f).sp
    val metaSize = (fontSizeSp * 0.72f).coerceIn(10f, 20f).sp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = post.title,
                fontSize = titleSize,
                lineHeight = (fontSizeSp * 1.35f).sp,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (post.commentCount > 0) {
                Text(
                    text = "[${post.commentCount}]",
                    fontSize = commentSize,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (post.author.isNotBlank()) {
                Text(
                    text = post.author,
                    fontSize = metaSize,
                    color = metaColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            MetaText(post.time, metaColor, metaSize)
            if (post.viewCount > 0) MetaText("조회 ${post.viewCount}", metaColor, metaSize)
            if (post.voteCount > 0) MetaText("추천 ${post.voteCount}", metaColor, metaSize)
        }
    }
}

@Composable
private fun MetaText(text: String, color: Color, fontSize: TextUnit) {
    if (text.isBlank()) return
    Text(
        text = text,
        fontSize = fontSize,
        color = color,
        maxLines = 1,
        softWrap = false
    )
}

