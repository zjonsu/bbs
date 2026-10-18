package com.bbs.reader.data.repository

import com.bbs.reader.data.model.BoardInfo
import com.bbs.reader.data.model.BoardListStyle
import com.bbs.reader.data.model.Community
import com.bbs.reader.data.model.SiteAuthInfo

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

    const val SLRCLUB_ID = "slrclub"
    const val SLRCLUB_HOT = "slrclub_hot"
    const val SLRCLUB_WORK_GALLERY = "slrclub_work_gallery"
    const val SLRCLUB_THEME_GALLERY = "slrclub_theme_gallery"
    const val SLRCLUB_STUDY_GALLERY = "slrclub_study_gallery"
    const val SLRCLUB_PRESS_GALLERY = "slrclub_press_gallery"
    const val SLRCLUB_TODAY_PICTURES = "slrclub_today_pictures"
    const val SLRCLUB_AD_FREE = "slrclub_ad_free"

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
        ),
        Community(
            id = SLRCLUB_ID,
            name = "SLR클럽",
            description = "인기글 / 갤러리 / 성인게시판",
            boards = listOf(
                BoardInfo(
                    id = SLRCLUB_HOT,
                    communityId = SLRCLUB_ID,
                    name = "인기글",
                    listPath = "https://m.slrclub.com/l/hot_article"
                ),
                BoardInfo(
                    id = SLRCLUB_WORK_GALLERY,
                    communityId = SLRCLUB_ID,
                    name = "작품갤러리",
                    listPath = "https://m.slrclub.com/l/work_gallery",
                    listStyle = BoardListStyle.THUMBNAIL_GRID
                ),
                BoardInfo(
                    id = SLRCLUB_THEME_GALLERY,
                    communityId = SLRCLUB_ID,
                    name = "주제갤러리",
                    listPath = "https://m.slrclub.com/l/theme_gallery",
                    listStyle = BoardListStyle.THUMBNAIL_GRID
                ),
                BoardInfo(
                    id = SLRCLUB_STUDY_GALLERY,
                    communityId = SLRCLUB_ID,
                    name = "습작갤러리",
                    listPath = "https://m.slrclub.com/l/study_gallery",
                    listStyle = BoardListStyle.THUMBNAIL_GRID
                ),
                BoardInfo(
                    id = SLRCLUB_PRESS_GALLERY,
                    communityId = SLRCLUB_ID,
                    name = "시민기자단",
                    listPath = "https://m.slrclub.com/l/press_gallery",
                    listStyle = BoardListStyle.THUMBNAIL_GRID
                ),
                BoardInfo(
                    id = SLRCLUB_TODAY_PICTURES,
                    communityId = SLRCLUB_ID,
                    name = "오늘의사진",
                    listPath = "https://m.slrclub.com/l/today_pictures",
                    requiresLogin = true,
                    listStyle = BoardListStyle.THUMBNAIL_GRID
                ),
                BoardInfo(
                    id = SLRCLUB_AD_FREE,
                    communityId = SLRCLUB_ID,
                    name = "성인게시판",
                    listPath = "https://m.slrclub.com/l/ad_free",
                    requiresLogin = true
                )
            )
        )
    )

    val siteAuthList: List<SiteAuthInfo> = listOf(
        SiteAuthInfo(
            communityId = SLRCLUB_ID,
            name = "SLR클럽",
            loginUrl = "https://m.slrclub.com/?type=login",
            cookieUrls = listOf(
                "https://m.slrclub.com",
                "https://www.slrclub.com",
                "https://secure.slrclub.com",
                "https://slrclub.com"
            ),
            homeUrl = "https://m.slrclub.com/"
        )
    )

    fun findCommunity(id: String): Community? = communities.find { it.id == id }

    fun findBoard(boardId: String): BoardInfo? =
        communities.flatMap { it.boards }.find { it.id == boardId }

    fun findSiteAuth(communityId: String): SiteAuthInfo? =
        siteAuthList.find { it.communityId == communityId }

    fun isAagagMid(mid: String): Boolean = mid == "issue" || mid == "deal" || mid == "mirror"

    fun isDamoangMid(mid: String): Boolean = mid.startsWith("damoang:")

    fun isHumorunivMid(mid: String): Boolean = mid.startsWith("humoruniv:")

    fun isSlrclubMid(mid: String): Boolean = mid.startsWith("slrclub:")
}
