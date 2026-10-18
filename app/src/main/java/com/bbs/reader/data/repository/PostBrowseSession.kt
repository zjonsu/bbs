package com.bbs.reader.data.repository

import com.bbs.reader.data.model.PostSummary

/**
 * 목록 → 상세 진입 시 좌우 스와이프로 이전/다음 글을 열기 위한 세션 캐시.
 */
object PostBrowseSession {
    @Volatile
    private var posts: List<PostSummary> = emptyList()

    fun update(list: List<PostSummary>) {
        posts = list
    }

    fun clear() {
        posts = emptyList()
    }

    fun snapshot(): List<PostSummary> = posts

    fun indexOf(mid: String, postId: String): Int =
        posts.indexOfFirst { it.mid == mid && it.id == postId }
}
