package com.bbs.reader.data.model

data class Community(
    val id: String,
    val name: String,
    val description: String,
    val boards: List<BoardInfo>
)

data class BoardInfo(
    val id: String,
    val communityId: String,
    val name: String,
    val listPath: String
)

data class PostSummary(
    val id: String,
    val boardId: String,
    val title: String,
    val boardLabel: String?,
    val author: String,
    val time: String,
    val commentCount: Int,
    val viewCount: Int,
    val voteCount: Int,
    val url: String,
    val mid: String
)

data class PostDetail(
    val id: String,
    val title: String,
    val author: String,
    val time: String,
    val viewCount: Int,
    val voteCount: Int,
    val commentCount: Int,
    val contentHtml: String,
    val url: String,
    val mid: String,
    val comments: List<CommentItem>
)

data class CommentItem(
    val id: String,
    val author: String,
    val time: String,
    val contentHtml: String,
    val depth: Int,
    val isReply: Boolean,
    val replyTo: String?
)

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
