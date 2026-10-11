package com.bbs.reader.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bbs.reader.data.local.ReadHistoryStore
import com.bbs.reader.data.model.LoginRequiredException
import com.bbs.reader.data.model.PostSummary
import com.bbs.reader.data.model.UiState
import com.bbs.reader.data.repository.BoardRepository
import com.bbs.reader.data.repository.CommunityCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BoardUiModel(
    val boardName: String,
    val communityName: String,
    val posts: List<PostSummary>,
    val page: Int,
    val hasMore: Boolean
)

class BoardViewModel(
    private val boardId: String,
    private val repository: BoardRepository,
    readHistoryStore: ReadHistoryStore
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<BoardUiModel>>(UiState.Loading)
    val state: StateFlow<UiState<BoardUiModel>> = _state.asStateFlow()

    val readIds: StateFlow<Set<String>> = readHistoryStore.readIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var currentPage = 1
    private val loaded = mutableListOf<PostSummary>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            currentPage = 1
            loaded.clear()
            runCatching { repository.fetchPosts(boardId, currentPage) }
                .onSuccess { posts ->
                    loaded.addAll(posts)
                    _state.value = UiState.Success(buildModel(hasMore = posts.isNotEmpty()))
                }
                .onFailure { e ->
                    _state.value = when (e) {
                        is LoginRequiredException -> UiState.LoginRequired(
                            communityId = e.communityId,
                            siteName = e.siteName,
                            message = e.message ?: "로그인이 필요한 게시판입니다."
                        )
                        else -> UiState.Error(e.message ?: "목록을 불러오지 못했습니다.")
                    }
                }
        }
    }

    fun loadMore() {
        val current = (_state.value as? UiState.Success)?.data ?: return
        if (!current.hasMore) return
        viewModelScope.launch {
            val next = currentPage + 1
            runCatching { repository.fetchPosts(boardId, next) }
                .onSuccess { posts ->
                    if (posts.isEmpty()) {
                        _state.value = UiState.Success(current.copy(hasMore = false))
                    } else {
                        currentPage = next
                        loaded.addAll(posts)
                        _state.value = UiState.Success(buildModel(hasMore = true))
                    }
                }
        }
    }

    private fun buildModel(hasMore: Boolean): BoardUiModel {
        val board = CommunityCatalog.findBoard(boardId)
        val community = board?.let { CommunityCatalog.findCommunity(it.communityId) }
        return BoardUiModel(
            boardName = board?.name ?: boardId,
            communityName = community?.name.orEmpty(),
            posts = loaded.toList(),
            page = currentPage,
            hasMore = hasMore
        )
    }

    companion object {
        fun factory(
            boardId: String,
            repository: BoardRepository,
            readHistoryStore: ReadHistoryStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BoardViewModel(boardId, repository, readHistoryStore) as T
            }
        }
    }
}
