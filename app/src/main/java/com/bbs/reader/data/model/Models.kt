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
    val listPath: String,
    /** 목록 열람에 사이트 로그인이 필요한 게시판 */
    val requiresLogin: Boolean = false
)

/** 사이트별 WebView 로그인 설정 */
data class SiteAuthInfo(
    val communityId: String,
    val name: String,
    val loginUrl: String,
    val cookieUrls: List<String>,
    val homeUrl: String
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
    val comments: List<CommentItem>,
    /** 본문/댓글 URL → 페이지 제목 (표시용) */
    val linkTitles: Map<String, String> = emptyMap()
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
    data class LoginRequired(
        val communityId: String,
        val siteName: String,
        val message: String = "로그인이 필요한 게시판입니다."
    ) : UiState<Nothing>()
}

class LoginRequiredException(
    val communityId: String,
    val siteName: String,
    message: String = "로그인이 필요한 게시판입니다."
) : Exception(message)
