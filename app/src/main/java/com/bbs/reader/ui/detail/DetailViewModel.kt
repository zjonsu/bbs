package com.bbs.reader.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bbs.reader.data.local.ReadHistoryStore
import com.bbs.reader.data.model.PostDetail
import com.bbs.reader.data.model.UiState
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
                }
                .onFailure { e ->
                    _state.value = UiState.Error(e.message ?: "글을 불러오지 못했습니다.")
                }
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
