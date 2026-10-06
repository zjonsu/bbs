package com.bbs.reader.data.repository

import com.bbs.reader.data.model.BoardInfo
import com.bbs.reader.data.model.Community

object CommunityCatalog {
    const val DDANZI_ID = "ddanzi"
    const val DDANZI_HOT = "ddanzi_hot"

    const val AAGAG_ID = "aagag"
    const val AAGAG_ISSUE = "aagag_issue"

    const val DAMOANG_ID = "damoang"
    const val DAMOANG_EMPATHY = "damoang_empathy"
    const val DAMOANG_EXPLORE = "damoang_explore"
    const val DAMOANG_GROUP_EMPATHY = "damoang_group_empathy"

    const val HUMORUNIV_ID = "humoruniv"
    const val HUMORUNIV_PDS_BEST = "humoruniv_pds_best"
    const val HUMORUNIV_SSUDA_BEST = "humoruniv_ssuda_best"

    val communities: List<Community> = listOf(
        Community(
            id = DDANZI_ID,
            name = "딴지일보",
            description = "HOT 눈팅",
            boards = listOf(
                BoardInfo(
                    id = DDANZI_HOT,
                    communityId = DDANZI_ID,
                    name = "HOT",
                    listPath = "https://www.ddanzi.com/hot_all"
                )
            )
        ),
        Community(
            id = AAGAG_ID,
            name = "AAGAG",
            description = "이슈 모음 눈팅",
            boards = listOf(
                BoardInfo(
                    id = AAGAG_ISSUE,
                    communityId = AAGAG_ID,
                    name = "ISSUE",
                    listPath = "https://aagag.com/issue/"
                )
            )
        ),
        Community(
            id = DAMOANG_ID,
            name = "다모앙",
            description = "공감글 / 모아보기 / 소모임 공감글",
            boards = listOf(
                BoardInfo(
                    id = DAMOANG_EMPATHY,
                    communityId = DAMOANG_ID,
                    name = "공감글",
                    listPath = "https://damoang.net/empathy"
                ),
                BoardInfo(
                    id = DAMOANG_EXPLORE,
                    communityId = DAMOANG_ID,
                    name = "모아보기",
                    listPath = "https://damoang.net/explore"
                ),
                BoardInfo(
                    id = DAMOANG_GROUP_EMPATHY,
                    communityId = DAMOANG_ID,
                    name = "소모임 공감글",
                    listPath = "https://damoang.net/"
                )
            )
        ),
        Community(
            id = HUMORUNIV_ID,
            name = "웃긴대학",
            description = "웃긴자료 / 웃수다 베스트",
            boards = listOf(
                BoardInfo(
                    id = HUMORUNIV_PDS_BEST,
                    communityId = HUMORUNIV_ID,
                    name = "웃긴자료 베스트",
                    listPath = "https://m.humoruniv.com/board/list.html?table=pds"
                ),
                BoardInfo(
                    id = HUMORUNIV_SSUDA_BEST,
                    communityId = HUMORUNIV_ID,
                    name = "웃수다 베스트",
                    listPath = "https://m.humoruniv.com/main.html"
                )
            )
        )
    )

    fun findCommunity(id: String): Community? = communities.find { it.id == id }

    fun findBoard(boardId: String): BoardInfo? =
        communities.flatMap { it.boards }.find { it.id == boardId }

    fun isAagagMid(mid: String): Boolean = mid == "issue" || mid == "deal" || mid == "mirror"

    fun isDamoangMid(mid: String): Boolean = mid.startsWith("damoang:")

    fun isHumorunivMid(mid: String): Boolean = mid.startsWith("humoruniv:")
}
