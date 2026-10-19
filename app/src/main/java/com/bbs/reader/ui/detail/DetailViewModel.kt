package com.bbs.reader.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bbs.reader.data.local.ReadHistoryStore
import com.bbs.reader.data.model.LoginRequiredException
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.UiState
import com.bbs.reader.data.remote.LinkTitleFetcher
import com.bbs.reader.data.repository.BoardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val mid: String,
    private val postId: String,
    private val repository: BoardRepository,
    private val readHistoryStore: ReadHistoryStore
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<PostDetail>>(UiState.Loading)
    val state: StateFlow<UiState<PostDetail>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching { repository.fetchPostDetail(mid, postId) }
                .onSuccess { detail ->
                    readHistoryStore.markRead(postId)
                    _state.value = UiState.Success(detail)
                    enrichLinkTitles(detail)
                }
                .onFailure { e ->
                    _state.value = when (e) {
                        is LoginRequiredException -> UiState.LoginRequired(
                            communityId = e.communityId,
                            siteName = e.siteName,
                            message = e.message ?: "로그인이 필요한 게시판입니다."
                        )
                        else -> UiState.Error(e.message ?: "글을 불러오지 못했습니다.")
                    }
                }
        }
    }

    private suspend fun enrichLinkTitles(detail: PostDetail) {
        val urls = LinkTitleFetcher.collectUrls(detail)
        if (urls.isEmpty()) return
        val titles = runCatching { LinkTitleFetcher.fetchTitles(urls) }.getOrDefault(emptyMap())
        if (titles.isEmpty()) return
        val current = _state.value
        if (current is UiState.Success && current.data.id == detail.id) {
            _state.value = UiState.Success(detail.copy(linkTitles = titles))
        }
    }

    companion object {
        fun factory(
            mid: String,
            postId: String,
            repository: BoardRepository,
            readHistoryStore: ReadHistoryStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DetailViewModel(mid, postId, repository, readHistoryStore) as T
            }
        }
    }
}
